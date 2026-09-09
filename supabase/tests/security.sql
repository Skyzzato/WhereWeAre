-- Run as postgres on a disposable DB after migration. All test data rolls back.
\set ON_ERROR_STOP on
begin;
create function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; raise notice 'PASS: %',description; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Alice"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Bruno"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Carla"}');
select pg_temp.assert_true((select count(*)=3 from public.profiles),'signup profiles');
select pg_temp.assert_true((select count(*)=3 from public.sharing_status where not is_sharing),'initial sharing OFF');
select set_config('test.b_code',(select invite_code from public.profiles where display_name='Bruno'),true);
select set_config('test.a_code',(select invite_code from public.profiles where display_name='Alice'),true);
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.profiles),'profiles not enumerable');
select pg_temp.assert_true((select count(*)=0 from public.lookup_user_by_invite_code('%')),'no wildcard lookup');
select pg_temp.assert_true((select count(*)=0 from public.lookup_user_by_invite_code(current_setting('test.a_code'))),'no self lookup');
select pg_temp.assert_true((select display_name='Bruno' from public.lookup_user_by_invite_code(lower(current_setting('test.b_code')))),'exact lookup normalized');
select set_config('test.request',public.send_share_request(current_setting('test.b_code'))::text,true);
select pg_temp.assert_true(public.send_share_request(current_setting('test.b_code'))::text=current_setting('test.request'),'send retry idempotent');
do $$ begin
 begin perform public.respond_to_share_request(current_setting('test.request')::uuid,true); raise exception 'sender accepted request';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.send_share_request(current_setting('test.a_code'))::text=current_setting('test.request'),'opposite request coalesced');
select public.respond_to_share_request(current_setting('test.request')::uuid,true);
select pg_temp.assert_true((select count(*)=2 from public.location_shares where enabled),'accept grants both directions');
select public.set_sharing(true,'10000000-0000-0000-0000-000000000002',0);
select public.publish_location('10000000-0000-0000-0000-000000000002',41,12,15,now());
select public.publish_location('10000000-0000-0000-0000-000000000002',42,13,10,now());
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'one row per user even on retries');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'authorized viewer can read');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'unrelated user denied');
select pg_temp.assert_true((select count(*)=0 from public.share_requests),'unrelated requests denied');
do $$ begin
 begin update public.latest_locations set recorded_at=now()+interval '1 year'; raise exception 'direct mutation allowed';
 exception when insufficient_privilege then null; end;
 begin perform public.cancel_share_request(current_setting('test.request')::uuid); raise exception 'third party cancelled';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_location_share('00000000-0000-0000-0000-000000000001',false);
select public.respond_to_share_request(current_setting('test.request')::uuid,true);
select pg_temp.assert_true((select not enabled from public.location_shares where owner_id=auth.uid()),'accept retry does not undo revocation');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'revocation immediately hides location');
select pg_temp.assert_true((select enabled from public.location_shares where owner_id=auth.uid()),'reverse permission unaffected');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_location_share('00000000-0000-0000-0000-000000000001',true);
select public.set_sharing(false,null);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'stop immediately hides recent location');
select pg_temp.assert_true((select count(*)=1 from public.sharing_status where user_id='00000000-0000-0000-0000-000000000002' and not is_sharing),'stop remains readable for realtime invalidation');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
do $$ begin
 begin perform public.publish_location('10000000-0000-0000-0000-000000000002',41,12,15,now()); raise exception 'late upload allowed';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise; end if; end;
end $$;
select public.set_sharing(true,'10000000-0000-0000-0000-000000000003',2);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000003',2);
select pg_temp.assert_true((select revision=3 from public.sharing_status where user_id=auth.uid()),'start retry idempotent');
do $$ begin
 begin perform public.publish_location('10000000-0000-0000-0000-000000000002',41,12,15,now()); raise exception 'old session allowed';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise; end if; end;
 begin perform public.publish_location('10000000-0000-0000-0000-000000000003',41,12,15,now()+interval '1 day'); raise exception 'future time allowed';
 exception when raise_exception then if sqlerrm<>'invalid_fix_time' then raise; end if; end;
end $$;
reset role;
update public.latest_locations set recorded_at=now()-interval '2 hours 1 second';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'expiry enforced without cleanup');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'owner can read own expired row');
reset role;
update public.latest_locations set recorded_at=now()-interval '2 hours';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'exact two hour boundary inclusive');
reset role;
select pg_temp.assert_true(not has_function_privilege('anon','public.lookup_user_by_invite_code(text)','execute'),'anonymous RPC denied');
select pg_temp.assert_true(not has_function_privilege('authenticated','private.lookup(text)','execute'),'private helper inaccessible');
select pg_temp.assert_true(not has_table_privilege('authenticated','public.latest_locations','update'),'direct timestamp changes denied');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_sharing(false,null);
do $$ begin
 begin perform public.set_sharing(true,'10000000-0000-0000-0000-000000000004',3); raise exception 'delayed start allowed';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise; end if; end;
end $$;
select pg_temp.assert_true((select not is_sharing from public.sharing_status where user_id=auth.uid()),'delayed start cannot undo stop');
reset role;
rollback;
