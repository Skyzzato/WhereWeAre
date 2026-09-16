-- Run in the verified WhereWeAre development project via SQL Editor.
-- All fixtures, outbox entries and notifications are rolled back; no push can be sent.
begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description;end if;end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('f0420000-0000-0000-0000-000000000001','{"display_name":"v042 transactional sender"}'),
 ('f0420000-0000-0000-0000-000000000002','{"display_name":"v042 transactional volunteer"}');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0420000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.nearby_sos_status()->>'opted_in'='false','default off');
select public.set_nearby_sos_consent(true);
select public.refresh_nearby_sos(0,0,10,now());
select set_config('request.jwt.claim.sub','f0420000-0000-0000-0000-000000000001',true);
select public.send_sos_v042('f0420000-0000-0000-0000-000000000010','help','{}','{}',0,0,10,now(),true);
select public.send_sos_v042('f0420000-0000-0000-0000-000000000010','help','{}','{}',0,0,10,now(),true);
select pg_temp.assert_true(public.sos_status('f0420000-0000-0000-0000-000000000010')->'recipients'='[]','no candidate enumeration');
select set_config('request.jwt.claim.sub','f0420000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(not(public.event_inbox()->0->'payload' ? 'latitude'),'anonymous invitation');
select public.respond_sos('f0420000-0000-0000-0000-000000000010','can_help');
select pg_temp.assert_true(public.event_inbox()->0->'payload'->>'latitude'='0','acceptance unlocks location');
select public.set_nearby_sos_consent(false);
select pg_temp.assert_true(public.event_inbox()='[]','revocation removes access');
reset role;
select pg_temp.assert_true((select count(*)=1 from private.nearby_invitations where event_id='f0420000-0000-0000-0000-000000000010'),'retry deduplicated');
select pg_temp.assert_true(not exists(select 1 from private.push_outbox where event_id='f0420000-0000-0000-0000-000000000010'),'revocation cancels queued notification');
rollback;
select 'v0.42 transactional remote smoke passed; all fixtures rolled back' as result;
