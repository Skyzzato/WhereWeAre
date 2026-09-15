begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Owner"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Member"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Invitee"}');
insert into public.groups(id,name,emoji,invite_code,creator_id,expires_at) values
 ('20000000-0000-0000-0000-000000000001','Temporary','T','ZZZ-YYY','00000000-0000-0000-0000-000000000001',now()+interval '1 hour');
insert into public.group_members(group_id,user_id) values
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001'),
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002');
insert into public.group_requests(id,group_id,user_id,inviter_id,kind) values
 ('40000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','invite');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,10,now());
select public.create_checkin('30000000-0000-0000-0000-000000000001','here',46,11,10,now(),'{}',array['20000000-0000-0000-0000-000000000001']::uuid[]);
select public.edit_group_timed('20000000-0000-0000-0000-000000000001','Temporary','T',now()+interval '2 hours');
select pg_temp.assert_true(public.app_metadata()->>'temporary_groups_available'='true','temporary groups capability');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.is_group_member('20000000-0000-0000-0000-000000000001'),'active group membership');
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'active group permits precise location');
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'active group permits snapshot');
do $$ begin
 begin perform public.edit_group_timed('20000000-0000-0000-0000-000000000001','Hijacked','T',null);raise exception 'member changed expiry';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
reset role;
-- No cleanup: group and both membership rows remain physically present.
update public.groups set expires_at=statement_timestamp()-interval '1 second' where id='20000000-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.assert_true(not public.is_group_member('20000000-0000-0000-0000-000000000001'),'expired group denies membership without cleanup');
select pg_temp.assert_true((select count(*)=0 from public.group_members),'expired membership rows cannot be read');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'expired group denies raw coordinates');
select pg_temp.assert_true(public.visible_locations()='[]','expired group denies approximate RPC too');
select pg_temp.assert_true(public.event_inbox()='[]','group-only snapshot access expires with group');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.group_inbox()='[]','expired invitations hidden');
select pg_temp.assert_true((select count(*)=0 from public.group_requests),'expired invitations RLS denied');
do $$ begin
 begin perform public.respond_group_request('40000000-0000-0000-0000-000000000001',true);raise exception 'expired invitation accepted';
 exception when raise_exception then if sqlerrm<>'group_expired' then raise;end if;end;
 begin perform public.join_group('ZZZ-YYY');raise exception 'expired code joined';
 exception when raise_exception then if sqlerrm<>'group_expired' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(public.location_audience()='[]','expired group no longer contributes to audience');
do $$ begin
 begin perform public.edit_group_timed('20000000-0000-0000-0000-000000000001','Revive','T',null);raise exception 'expired group revived';
 exception when raise_exception then if sqlerrm<>'group_expired' then raise;end if;end;
end $$;
select pg_temp.assert_true(public.create_group_timed('Permanent','P',null) is not null,'permanent creation remains available');
select pg_temp.assert_true(public.create_group_timed('Next outing','T',now()+interval '1 day') is not null,'future temporary group creation');
reset role;
insert into public.location_shares(owner_id,viewer_id,enabled,shared_precision) values
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',true,500);
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((public.visible_locations()->0->>'precision_m')::int=500,'independent personal grant survives group expiry');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'expired exact group cannot override approximate personal grant');
reset role;
rollback;
