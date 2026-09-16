begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description;end if;end $$;
insert into auth.users(id,raw_user_meta_data) values
('f0430000-0000-0000-0000-000000000001','{"display_name":"v043 owner"}'),
('f0430000-0000-0000-0000-000000000002','{"display_name":"v043 other"}');
set local role authenticated;
select set_config('request.jwt.claim.sub','f0430000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((public.nearby_sos_status()->>'opted_in')::boolean=false,'new accounts stay off');
select public.set_nearby_sos_consent(true);
select public.refresh_nearby_sos(46,11,10,now());
reset role;
update private.nearby_volunteers set available_until=now()-interval '1 day',acquired_at=now()-interval '1 day'
where user_id='f0430000-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.assert_true((public.nearby_sos_status()->>'opted_in')::boolean and not (public.nearby_sos_status()->>'available')::boolean,'stale availability does not revoke consent');
select public.refresh_nearby_sos(46,11,10,now());
select public.end_nearby_availability();
select pg_temp.assert_true((public.nearby_sos_status()->>'opted_in')::boolean and not (public.nearby_sos_status()->>'available')::boolean,'logout stops availability only');
select public.set_nearby_sos_consent(false);
select pg_temp.assert_true(not (public.nearby_sos_status()->>'opted_in')::boolean,'revocation persists');
select public.save_place_v043('f0430000-0000-0000-0000-000000000010',0,'Casa',46,11,100,'🏠');
do $$ begin
 begin perform public.save_place_v043('f0430000-0000-0000-0000-000000000011',1,'  CASA  ',46,11,100,'');raise exception 'duplicate accepted';
 exception when raise_exception then if sqlerrm<>'place_duplicate' then raise;end if;end;
 for i in 1..8 loop perform public.save_place_v043(gen_random_uuid(),i,'Luogo '||i,46,11,100,'📍');end loop;
end $$;
select pg_temp.assert_true(jsonb_array_length(public.places_rules()->'places')=9,'more than four places');
select public.save_place_v043('f0430000-0000-0000-0000-000000000010',0,'casa',46,11,200,'');
select pg_temp.assert_true((select x->>'emoji'='' from jsonb_array_elements(public.places_rules()->'places') x where x->>'id'='f0430000-0000-0000-0000-000000000010'),'icon removed');
select public.save_place_v043('f0430000-0000-0000-0000-000000000010',0,'casa',46,11,200,'🌲');
select pg_temp.assert_true((select x->>'emoji'='🌲' from jsonb_array_elements(public.places_rules()->'places') x where x->>'id'='f0430000-0000-0000-0000-000000000010'),'icon chosen again');
select pg_temp.assert_true(public.places_rules()->'rules'='[]','creation does not arm automation');
select public.save_place_rule('f0430000-0000-0000-0000-000000000020','f0430000-0000-0000-0000-000000000010','enter','f0430000-0000-0000-0000-000000000001',array['f0430000-0000-0000-0000-000000000002']::uuid[],false);
select public.save_place_rule('f0430000-0000-0000-0000-000000000020','f0430000-0000-0000-0000-000000000010','exit','f0430000-0000-0000-0000-000000000001',array['f0430000-0000-0000-0000-000000000002']::uuid[],false);
select public.remove_place('f0430000-0000-0000-0000-000000000010');
select pg_temp.assert_true(public.places_rules()->'rules'='[]','delete removes dependent rules atomically');
select pg_temp.assert_true(not public.sos_registered('f0430000-0000-0000-0000-000000000030'),'missing SOS is not confirmed');
select public.send_sos_v042('f0430000-0000-0000-0000-000000000030','help','{}','{}',null,null,null,null,true);
select pg_temp.assert_true(public.sos_registered('f0430000-0000-0000-0000-000000000030'),'committed SOS is recoverable');
select public.close_sos('f0430000-0000-0000-0000-000000000030','okay');
select pg_temp.assert_true(public.sos_registered('f0430000-0000-0000-0000-000000000030'),'closed tombstone remains recoverable');
select set_config('request.jwt.claim.sub','f0430000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(not public.sos_registered('f0430000-0000-0000-0000-000000000030'),'other account cannot enumerate SOS');
select public.save_place_v043('f0430000-0000-0000-0000-000000000011',0,'Casa',46,11,100,'');
select pg_temp.assert_true(jsonb_array_length(public.places_rules()->'places')=1,'names are scoped per owner');
reset role;
select pg_temp.assert_true(not has_function_privilege('anon','public.sos_registered(uuid)','execute'),'anonymous recovery denied');
rollback;
