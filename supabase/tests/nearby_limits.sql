begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description;end if;end $$;
insert into auth.users(id,raw_user_meta_data)
 select ('00000000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid,jsonb_build_object('display_name','Test '||i) from generate_series(1,30) i;
insert into private.nearby_volunteers(user_id,opted_in,latitude,longitude,accuracy,acquired_at,received_at,available_until)
 select id,true,46.001,11.001,10,now(),now(),now()+interval '15 minutes' from public.profiles;
update private.nearby_volunteers set opted_in=false where user_id='00000000-0000-0000-0000-000000000002';
update private.nearby_volunteers set acquired_at=now()-interval '16 minutes' where user_id='00000000-0000-0000-0000-000000000003';
update private.nearby_volunteers set available_until=now()-interval '1 second' where user_id='00000000-0000-0000-0000-000000000004';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.send_sos_v042('80000000-0000-0000-0000-000000000001','help','{}','{}',46,11,10,now(),true);
select pg_temp.assert_true(public.app_metadata()->>'nearby_sos_available'='true','metadata capability enabled');
reset role;
select pg_temp.assert_true((select count(*)=20 from private.nearby_invitations),'maximum twenty candidates');
select pg_temp.assert_true(not exists(select 1 from private.nearby_invitations where user_id in
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004')),'self, revoked, stale and unavailable excluded');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000005',true);
select public.respond_sos('80000000-0000-0000-0000-000000000001','can_help');
select public.respond_sos('80000000-0000-0000-0000-000000000001','cannot_help');
select pg_temp.assert_true(public.event_inbox()='[]','withdrawal removes access');
do $$ begin
 begin perform public.respond_sos('80000000-0000-0000-0000-000000000001','can_help');raise exception 'withdrawal resurrected';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.close_sos('80000000-0000-0000-0000-000000000001','okay');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000006',true);
select pg_temp.assert_true(public.event_inbox()='[]','closure removes pending grants');
reset role;
update private.app_events set created_at=now()-interval '10 minutes';
update private.nearby_config set recipient_hourly_limit=1;
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.send_sos_v042('80000000-0000-0000-0000-000000000002','help','{}','{}',46,11,10,now(),true);
reset role;
select pg_temp.assert_true(not exists(select 1 from private.nearby_invitations a join private.nearby_invitations b using(user_id) where a.event_id<>b.event_id),'recipient hourly rate enforced');
update private.app_events set expires_at=now()-interval '1 second' where id='80000000-0000-0000-0000-000000000002';
select pg_temp.assert_true(not exists(select 1 from private.nearby_invitations where event_id='80000000-0000-0000-0000-000000000002' and private.nearby_access(event_id,user_id)),'expiry ends access');
update private.app_events set created_at=now()-interval '10 minutes';
set local role authenticated;
select public.send_sos_v042('80000000-0000-0000-0000-000000000003','help','{}','{}',46,11,10,now()-interval '2 hours',true);
select pg_temp.assert_true(public.sos_status('80000000-0000-0000-0000-000000000003')->>'nearby_state'='fresh_fix_required','old sender fix does not search nearby');
reset role;
select pg_temp.assert_true(not exists(select 1 from private.nearby_invitations where event_id='80000000-0000-0000-0000-000000000003'),'old fix never selects candidates');
select pg_temp.assert_true(not has_function_privilege('authenticated','public.push_job_authorized(uuid)','execute'),'client cannot probe jobs');
select pg_temp.assert_true(not has_function_privilege('anon','public.send_sos_v042(uuid,text,uuid[],uuid[],float8,float8,float8,timestamptz,boolean)','execute'),'anonymous send denied');
rollback;
