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
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.create_meeting('50000000-0000-0000-0000-000000000001',46,11,false,'{}',array['20000000-0000-0000-0000-000000000001']::uuid[]);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,10,now());
select pg_temp.assert_true((public.meeting_inbox()->0->>'active')::boolean,'missing participant cannot complete');
reset role;
select pg_temp.assert_true(private.flare_participant('50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000003')->>'state'='unavailable','unrelated viewer receives no derived location');
update public.latest_locations set recorded_at=now()-interval '5 minutes';
select pg_temp.assert_true(private.flare_participant('50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')->>'state'='stale','old position never arrived');
update public.group_members set shared_precision=500 where user_id='00000000-0000-0000-0000-000000000001';
update public.latest_locations set recorded_at=now();
select pg_temp.assert_true(private.flare_participant('50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')->>'state'='uncertain','approximate area cannot imply precise arrival');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000002',0);
select public.publish_location('10000000-0000-0000-0000-000000000002',46,11,10,now());
select pg_temp.assert_true((public.meeting_inbox()->0->>'active')::boolean,'global completion cannot leak precise arrival');
select public.create_checkin('30000000-0000-0000-0000-000000000002','arrived',46,11,10,now(),'{}','{}','50000000-0000-0000-0000-000000000001');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.create_checkin('30000000-0000-0000-0000-000000000001','arrived',46,11,10,now(),'{}','{}','50000000-0000-0000-0000-000000000001');
select pg_temp.assert_true(not (public.meeting_inbox()->0->>'active')::boolean,'explicit arrivals complete automatically');
select pg_temp.assert_true(public.meeting_inbox()->0->>'completed_at' is not null,'completion persisted');
reset role;
select pg_temp.assert_true((select count(*)=2 from private.push_outbox where meeting_id='50000000-0000-0000-0000-000000000001'),'created and concluded notifications deduplicated');
update public.group_members set shared_precision=0;
update public.latest_locations set latitude=47 where user_id='00000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.create_meeting('50000000-0000-0000-0000-000000000002',46,11,false,'{}',array['20000000-0000-0000-0000-000000000001']::uuid[]);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.publish_location('10000000-0000-0000-0000-000000000002',46,11,10,clock_timestamp());
reset role;
select pg_temp.assert_true((select completed_at is not null and not active from public.meeting_points where id='50000000-0000-0000-0000-000000000002'),'fresh positions complete on publication without an inbox poll');
rollback;
