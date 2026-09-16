-- Isolated fixtures only, no nearby search, no tokens/devices, entire transaction rolled back.
begin;
create or replace function pg_temp.assert_true(value boolean, description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description;end if;end $$;
insert into auth.users(id,raw_user_meta_data) values
('f0440000-0000-0000-0000-000000000001','{"display_name":"v044 test A"}'),
('f0440000-0000-0000-0000-000000000002','{"display_name":"v044 test B"}'),
('f0440000-0000-0000-0000-000000000003','{"display_name":"v044 test outsider"}');
insert into public.groups(id,name,emoji,invite_code,creator_id,expires_at) values
('f0440000-0000-0000-0000-000000000010','v044 test','T','V44-XYZ','f0440000-0000-0000-0000-000000000001',now()+interval '1 hour');
insert into public.group_members(group_id,user_id) values
('f0440000-0000-0000-0000-000000000010','f0440000-0000-0000-0000-000000000001'),
('f0440000-0000-0000-0000-000000000010','f0440000-0000-0000-0000-000000000002');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0440000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((public.app_metadata()->>'sos_available')::boolean,'SOS metadata available');
select public.send_sos('f0440000-0000-0000-0000-000000000020','help',array['f0440000-0000-0000-0000-000000000002']::uuid[],array['f0440000-0000-0000-0000-000000000010']::uuid[]);
select public.send_sos('f0440000-0000-0000-0000-000000000020','help','{}','{}');
select pg_temp.assert_true(public.sos_registered('f0440000-0000-0000-0000-000000000020'),'registration confirmed');
select pg_temp.assert_true(jsonb_array_length(public.sos_status('f0440000-0000-0000-0000-000000000020')->'recipients')=1,'person and group deduplicated');
select set_config('request.jwt.claim.sub','f0440000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'recipient inbox');
select public.respond_sos('f0440000-0000-0000-0000-000000000020','can_help');
select set_config('request.jwt.claim.sub','f0440000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.event_inbox()='[]','outsider isolated');
select pg_temp.assert_true(not public.sos_registered('f0440000-0000-0000-0000-000000000020'),'outsider cannot enumerate registration');
select set_config('request.jwt.claim.sub','f0440000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(public.sos_status('f0440000-0000-0000-0000-000000000020')->'recipients'->0->>'response'='can_help','sender receives response');
select public.close_sos('f0440000-0000-0000-0000-000000000020','okay');
select pg_temp.assert_true(public.sos_registered('f0440000-0000-0000-0000-000000000020'),'closed tombstone recoverable');
select public.save_place_v043('f0440000-0000-0000-0000-000000000030',0,'Test place',46,11,100,'');
select public.save_place_v043('f0440000-0000-0000-0000-000000000030',0,'Test place',46,11,100,'T');
select pg_temp.assert_true(public.places_rules()->'places'->0->>'emoji'='T','place icon restored');
select public.set_nearby_sos_consent(true);
select public.end_nearby_availability();
select pg_temp.assert_true((public.nearby_sos_status()->>'opted_in')::boolean and not (public.nearby_sos_status()->>'available')::boolean,'persistent consent separate from availability');
reset role;
select pg_temp.assert_true(not has_function_privilege('anon','public.sos_registered(uuid)','execute'),'anonymous recovery denied');
select pg_temp.assert_true(not has_function_privilege('authenticated','public.record_push_acceptance(uuid)','execute'),'clients cannot forge delivery');
select 'PASS: metadata, SOS person/group, retry, response, closure, outsider, places, consent, grants; SQL roles only' as result;
rollback;
