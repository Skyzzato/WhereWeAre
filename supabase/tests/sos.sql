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
update public.profiles set shared_precision=1000;
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.send_sos('80000000-0000-0000-0000-000000000001','help',array['00000000-0000-0000-0000-000000000002']::uuid[],array['20000000-0000-0000-0000-000000000001']::uuid[],46,11,150,now());
select public.send_sos('80000000-0000-0000-0000-000000000001','help','{}','{}');
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'retry creates no duplicate SOS');
select pg_temp.assert_true(jsonb_array_length(public.sos_status('80000000-0000-0000-0000-000000000001')->'recipients')=1,'person selected directly and through group is deduplicated');
select pg_temp.assert_true((public.sos_status('80000000-0000-0000-0000-000000000001')->'recipients'->0->>'push_accepted')::boolean=false,'server registration does not claim delivery');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((public.event_inbox()->0->'payload'->>'latitude')::float8=46,'explicit SOS uses best fix despite default approximation');
select public.respond_sos('80000000-0000-0000-0000-000000000001','can_help');
do $$ begin
 begin perform public.close_sos('80000000-0000-0000-0000-000000000001','okay');raise exception 'recipient closed SOS';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
 begin perform 1 from private.sos_state;raise exception 'raw SOS read allowed';exception when insufficient_privilege then null;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.event_inbox()='[]','stranger cannot discover SOS');
do $$ begin
 begin perform public.respond_sos('80000000-0000-0000-0000-000000000001','can_help');raise exception 'stranger responded';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(public.sos_status('80000000-0000-0000-0000-000000000001')->'recipients'->0->>'response'='can_help','sender sees authorized response');
reset role;
select pg_temp.assert_true((select count(*)=1 from private.push_outbox where kind='sos'),'one original SOS notification for overlapping recipients');
set local role authenticated;
select public.close_sos('80000000-0000-0000-0000-000000000001','accidental');
select public.close_sos('80000000-0000-0000-0000-000000000001','accidental');
select public.send_sos('80000000-0000-0000-0000-000000000001','help','{}','{}');
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1 and public.event_inbox()->0->>'kind'='sos_closed','closed retry cannot resurrect precise SOS');
do $$ begin
 begin perform public.send_sos('80000000-0000-0000-0000-000000000002','help','{}',array['20000000-0000-0000-0000-000000000001']::uuid[]);raise exception 'cooldown bypassed';
 exception when raise_exception then if sqlerrm<>'sos_cooldown' then raise;end if;end;
end $$;
reset role;
select pg_temp.assert_true((select payload='{}' from private.app_events where id='80000000-0000-0000-0000-000000000001'),'closure removes exact payload');
select pg_temp.assert_true((select count(*)=1 from private.push_outbox where kind='sos_closed'),'closure notice deduplicated');
select pg_temp.assert_true(not has_function_privilege('authenticated','public.record_push_acceptance(uuid)','execute'),'clients cannot forge push acceptance');
-- No location is a supported emergency report, not a validation failure.
update private.app_events set created_at=now()-interval '10 minutes' where kind='sos';
set local role authenticated;
select public.send_sos('80000000-0000-0000-0000-000000000002','lost','{}',array['20000000-0000-0000-0000-000000000001']::uuid[]);
select pg_temp.assert_true(exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'kind'='sos' and e->'payload'->'latitude'='null'),'SOS can be registered without GPS');
reset role;
update public.groups set expires_at=statement_timestamp()-interval '1 second';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(not exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'kind'='sos'),'group-only SOS access ends when temporary group expires');
reset role;
rollback;
