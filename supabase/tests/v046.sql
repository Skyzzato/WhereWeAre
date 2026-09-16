-- Synthetic accounts only. Rollback prevents any real notification or lasting fixture.
begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description;end if;end $$;
insert into auth.users(id,raw_user_meta_data) values
('f0460000-0000-0000-0000-000000000001','{"display_name":"v046 sender"}'),
('f0460000-0000-0000-0000-000000000002','{"display_name":"v046 recipient"}'),
('f0460000-0000-0000-0000-000000000003','{"display_name":"v046 outsider"}');
insert into public.groups(id,name,emoji,invite_code,creator_id) values
('f0460000-0000-0000-0000-000000000010','v046 test','T','V46-XYZ','f0460000-0000-0000-0000-000000000001');
insert into public.group_members(group_id,user_id) values
('f0460000-0000-0000-0000-000000000010','f0460000-0000-0000-0000-000000000001'),
('f0460000-0000-0000-0000-000000000010','f0460000-0000-0000-0000-000000000002');
select pg_temp.assert_true(not (select sos_quota_enabled from private.nearby_config),'quota disabled on server');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0460000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.nearby_sos_status()->>'consent_initialized'='false','new preference distinguishable from explicit OFF');
select public.set_nearby_sos_consent(false);
select pg_temp.assert_true(public.nearby_sos_status()->>'consent_initialized'='true' and public.nearby_sos_status()->>'opted_in'='false','OFF persists');
select public.set_nearby_sos_consent(true);
select pg_temp.assert_true(public.nearby_sos_status()->>'available'='false','consent alone does not claim reception');
select public.refresh_nearby_sos(46.001,11.001,10,now());
reset role;
update private.nearby_volunteers set available_until=now()-interval '1 hour' where user_id='f0460000-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.assert_true(public.nearby_sos_status()->>'available'='true','manual lease expiry irrelevant with fresh real fix');
select set_config('request.jwt.claim.sub','f0460000-0000-0000-0000-000000000001',true);
select public.send_sos_v042('f0460000-0000-0000-0000-000000000020','help','{}','{}',46,11,10,now(),true);
select public.send_sos_v042('f0460000-0000-0000-0000-000000000020','help','{}','{}',46,11,10,now(),true);
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'idempotent retry');
select set_config('request.jwt.claim.sub','f0460000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'nearby reception without manual lease');
select public.dismiss_event('f0460000-0000-0000-0000-000000000020');
select public.dismiss_event('f0460000-0000-0000-0000-000000000020');
select pg_temp.assert_true(public.event_inbox()='[]' and public.app_metadata()->'events'='[]','dismissal survives metadata reload');
reset role;
select pg_temp.assert_true((select closed_at is null from private.sos_state where event_id='f0460000-0000-0000-0000-000000000020'),'dismissal never closes SOS');
select pg_temp.assert_true(not public.push_job_authorized((select id from private.push_outbox where event_id='f0460000-0000-0000-0000-000000000020')),'dismissal suppresses pending push');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0460000-0000-0000-0000-000000000003',true);
do $$ begin
 begin perform public.dismiss_event('f0460000-0000-0000-0000-000000000020');raise exception 'outsider dismissed';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','f0460000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'other account unaffected');
select public.dismiss_event('f0460000-0000-0000-0000-000000000020');
select pg_temp.assert_true(public.event_inbox()='[]' and public.sos_registered('f0460000-0000-0000-0000-000000000020'),'sender dismissal preserves registration');
select pg_temp.assert_true(public.own_active_sos()->>'id'='f0460000-0000-0000-0000-000000000020','hidden SOS still accessible for closure');
select public.close_sos('f0460000-0000-0000-0000-000000000020','okay');
-- More than the old daily quota, with no sleeps. One active SOS rule stays intact.
do $$ declare i int;eid uuid;begin
 for i in 21..28 loop
 eid:=('f0460000-0000-0000-0000-'||lpad(i::text,12,'0'))::uuid;
 perform public.send_sos(eid,'help','{}',array['f0460000-0000-0000-0000-000000000010']::uuid[]);
 perform public.close_sos(eid,'okay');
 end loop;
end $$;
reset role;
select pg_temp.assert_true((select count(*)=9 from private.sos_state s join private.app_events e on e.id=s.event_id where e.sender_id='f0460000-0000-0000-0000-000000000001'),'quota OFF retains counters');
update private.nearby_config set sos_quota_enabled=true;
set local role authenticated;
do $$ begin
 begin perform public.send_sos('f0460000-0000-0000-0000-000000000029','help','{}',array['f0460000-0000-0000-0000-000000000010']::uuid[]);raise exception 'quota not enforced when enabled';
 exception when raise_exception then if sqlerrm<>'sos_cooldown' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','f0460000-0000-0000-0000-000000000002',true);
select public.set_nearby_sos_consent(false);
select pg_temp.assert_true(public.nearby_sos_status()->>'opted_in'='false' and public.nearby_sos_status()->>'available'='false','revocation effective');
reset role;
select pg_temp.assert_true(not has_table_privilege('authenticated','private.event_dismissals','DELETE'),'no global delete privilege');
select pg_temp.assert_true(not has_function_privilege('anon','public.dismiss_event(uuid)','EXECUTE'),'anonymous denied');
select 'PASS v046: personal deletion, idempotency, quota toggle, persistent consent, freshness, authorization' as result;
rollback;
