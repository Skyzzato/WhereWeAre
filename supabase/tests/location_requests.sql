begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Requester"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Receiver"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Stranger"}');
insert into public.location_shares(owner_id,viewer_id,enabled,shared_precision) values
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',false,1000),
 ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001',false,500);
select pg_temp.assert_true(not has_function_privilege('anon','public.request_location(uuid)','execute'),'anonymous cannot request');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(public.request_location('00000000-0000-0000-0000-000000000002')=public.request_location('00000000-0000-0000-0000-000000000002'),'retries reuse request identifier');
select pg_temp.assert_true((select count(*)=1 from public.share_requests where purpose='location'),'one pending request');
select pg_temp.assert_true(public.app_metadata()->'requests'='[]','legacy inbox excludes location requests');
select pg_temp.assert_true(public.app_metadata()->>'location_requests_available'='true','capability exposed');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.location_request_inbox()='[]','stranger cannot read inbox');
do $$ begin
 begin perform public.request_location('00000000-0000-0000-0000-000000000002');raise exception 'stranger request accepted';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(jsonb_array_length(public.location_request_inbox())=1,'recipient sees one request');
do $$ declare rid uuid:=(public.location_request_inbox()->0->>'id')::uuid;
begin
 begin perform public.respond_to_share_request(rid,true);raise exception 'legacy acceptance allowed';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
 perform pg_temp.assert_true(public.respond_location_request(rid,true),'recipient accepts');
 perform pg_temp.assert_true(public.respond_location_request(rid,true),'retry is idempotent');
end $$;
select pg_temp.assert_true((select enabled and shared_precision=500 from public.location_shares where owner_id=auth.uid()),'own precision preserved on consent');
select pg_temp.assert_true((select not enabled and shared_precision=1000 from public.location_shares where viewer_id=auth.uid()),'no reciprocal grant');
select pg_temp.assert_true((select not is_sharing from public.sharing_status where user_id=auth.uid()),'server does not silently start tracking');
select pg_temp.assert_true(public.location_request_inbox()='[]','accepted request removed from inbox');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$ begin
 begin perform public.request_location('00000000-0000-0000-0000-000000000002');raise exception 'duplicate accepted';
 exception when raise_exception then if sqlerrm<>'request_cooldown' then raise;end if;end;
end $$;
reset role;
select pg_temp.assert_true((select count(*)=1 from private.push_outbox where kind='location_request'),'single durable notification per request');
set local role service_role;
select pg_temp.assert_true((public.claim_push_batch()->0->>'request_id') is not null,'dispatcher claims typed request');
select pg_temp.assert_true(public.claim_push_batch()='[]','leased notification not duplicated');
reset role;
update public.share_requests set created_at=now()-interval '11 minutes' where purpose='location';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.request_location('00000000-0000-0000-0000-000000000002');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(not public.respond_location_request((public.location_request_inbox()->0->>'id')::uuid,false),'rejection returns false');
select pg_temp.assert_true(public.location_request_inbox()='[]','rejection clears inbox');
reset role;
rollback;
