-- Transactional disposable fixtures, suitable for local PostgreSQL and the verified project's SQL Editor.
begin;
create or replace function pg_temp.assert_true(v boolean,description text) returns void language plpgsql as $$
begin if v is distinct from true then raise exception 'FAIL: %',description;end if;end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('f0490000-0000-0000-0000-000000000001','{"display_name":"v049 Creator"}'),
 ('f0490000-0000-0000-0000-000000000002','{"display_name":"v049 Direct"}'),
 ('f0490000-0000-0000-0000-000000000003','{"display_name":"v049 Group"}'),
 ('f0490000-0000-0000-0000-000000000004','{"display_name":"v049 Stranger"}');
insert into public.location_shares(owner_id,viewer_id,enabled) values('f0490000-0000-0000-0000-000000000001','f0490000-0000-0000-0000-000000000002',true);
insert into public.groups(id,name,invite_code,creator_id) values('f0490000-0000-0000-0000-000000000010','v049 test','T49-QA1','f0490000-0000-0000-0000-000000000001');
insert into public.group_members(group_id,user_id) values
 ('f0490000-0000-0000-0000-000000000010','f0490000-0000-0000-0000-000000000001'),
 ('f0490000-0000-0000-0000-000000000010','f0490000-0000-0000-0000-000000000003');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000001',true);
select public.create_meeting('f0490000-0000-0000-0000-000000000020',46,11,false,array['f0490000-0000-0000-0000-000000000002']::uuid[],array['f0490000-0000-0000-0000-000000000010']::uuid[]);
select pg_temp.assert_true((select expires_at=created_at+interval '1 hour' from public.meeting_points where id='f0490000-0000-0000-0000-000000000020'),'server assigns exactly one hour');
reset role;
update public.meeting_points set expires_at=expires_at+interval '1 day',created_at=created_at+interval '1 day',latitude=45 where id='f0490000-0000-0000-0000-000000000020';
select pg_temp.assert_true((select expires_at=created_at+interval '1 hour' and created_at<clock_timestamp() from public.meeting_points where id='f0490000-0000-0000-0000-000000000020'),'edits cannot extend original lifetime');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000004',true);
select pg_temp.assert_true(not public.can_read_meeting('f0490000-0000-0000-0000-000000000020'),'stranger cannot read');
do $$ begin
 begin perform public.remove_meeting_v049('f0490000-0000-0000-0000-000000000020');raise exception 'stranger deleted';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
 begin update public.meeting_points set active=false where id='f0490000-0000-0000-0000-000000000020';raise exception 'direct table write allowed';
 exception when insufficient_privilege then null;end;
end $$;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.can_read_meeting('f0490000-0000-0000-0000-000000000020'),'direct recipient reads');
reset role;
update public.location_shares set enabled=false where owner_id='f0490000-0000-0000-0000-000000000001' and viewer_id='f0490000-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.assert_true(not public.can_read_meeting('f0490000-0000-0000-0000-000000000020'),'revoked recipient cannot read');
do $$ begin begin perform public.remove_meeting_v049('f0490000-0000-0000-0000-000000000020');raise exception 'revoked recipient deleted';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;end $$;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.remove_meeting_v049('f0490000-0000-0000-0000-000000000020')='removed','authorized group member deletes');
select pg_temp.assert_true(public.remove_meeting_v049('f0490000-0000-0000-0000-000000000020')='gone','retry is idempotent');
reset role;
select pg_temp.assert_true((select count(*)=1 from private.app_events where kind='meeting_removed' and payload->>'meeting_id'='f0490000-0000-0000-0000-000000000020'),'one durable event');
select pg_temp.assert_true((select count(*)=1 from private.push_outbox where kind='meeting_removed' and recipient='f0490000-0000-0000-0000-000000000001'),'one creator push, no self push');
select pg_temp.assert_true((select bool_and(not (payload ?| array['latitude','longitude'])) from private.app_events where kind='meeting_removed'),'no coordinates in deletion audit');
delete from public.group_members where group_id='f0490000-0000-0000-0000-000000000010' and user_id='f0490000-0000-0000-0000-000000000003';
set local role authenticated;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'kind'='meeting_removed' and e->>'sender_name'='v049 Group'),'creator recovers event after actor leaves group');
select public.dismiss_event((select (e->>'id')::uuid from jsonb_array_elements(public.event_inbox()) e where e->>'kind'='meeting_removed' limit 1));
select pg_temp.assert_true(not exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'kind'='meeting_removed'),'dismissal preserved');
select public.create_meeting('f0490000-0000-0000-0000-000000000021',46,11,false,'{}',array['f0490000-0000-0000-0000-000000000010']::uuid[]);
reset role;
update public.meeting_points set expires_at=clock_timestamp() where id='f0490000-0000-0000-0000-000000000021';
set local role authenticated;
select pg_temp.assert_true(not public.can_read_meeting('f0490000-0000-0000-0000-000000000021'),'deadline invalidates read before cleanup');
select pg_temp.assert_true(public.remove_meeting_v049('f0490000-0000-0000-0000-000000000021')='gone','expiry is not attributed to a person');
reset role;
select private.cleanup_meetings();
select pg_temp.assert_true(not exists(select 1 from public.meeting_points where id='f0490000-0000-0000-0000-000000000021'),'cleanup removes expired point');

-- Test activation only inside this rolled-back transaction. Does not change the live minimum.
update private.app_bootstrap set latest_version_code=18,latest_version_name='0.49',minimum_supported_version_code=18 where singleton;
set local role authenticated;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000001',true);
select set_config('request.jwt.claims','{"session_id":"f0490000-0000-0000-0000-000000000080"}',true);
select set_config('request.headers','{"x-whereweare-version":"17"}',true);
select set_config('request.path','/rpc/app_metadata',true);
do $$ begin begin perform public.check_client_version();raise exception 'legacy admitted';exception when sqlstate 'PT426' then null;end;end $$;
select set_config('request.path','/rpc/app_bootstrap',true);
select public.check_client_version();
select pg_temp.assert_true((public.app_bootstrap()->>'minimum_supported_version_code')::int=18,'legacy bootstrap remains reachable');
select public.set_sharing(false);
select set_config('request.path','/rpc/app_metadata',true);
select set_config('request.headers','{"x-whereweare-version":"18"}',true);
select public.check_client_version();
select public.set_sharing(true,'f0490000-0000-0000-0000-000000000081',(select revision from public.sharing_status where user_id=auth.uid()));
select pg_temp.assert_true(exists(select 1 from public.sharing_status where user_id=auth.uid()),'read-write request registers compatible Realtime session');
select pg_temp.assert_true(public.app_metadata()->'profile'->>'id'=auth.uid()::text,'authenticated app metadata loads');
select pg_temp.assert_true(jsonb_typeof(public.visible_locations())='array','authenticated location snapshot loads');
select pg_temp.assert_true(public.publish_location_v049('f0490000-0000-0000-0000-000000000081',46,11,10,clock_timestamp()-interval '1 second',1000),'new client ACK');
select pg_temp.assert_true(not public.publish_location_v049('f0490000-0000-0000-0000-000000000081',45,10,10,clock_timestamp()-interval '10 seconds',10000),'out of order fix rejected without false ACK');
select set_config('request.headers','{}',true);
do $$ begin begin perform public.publish_location('f0490000-0000-0000-0000-000000000081',46,11,10,clock_timestamp());raise exception 'legacy active service admitted';exception when sqlstate 'PT426' then null;end;end $$;
select public.set_sharing(false);
reset role;
rollback;
