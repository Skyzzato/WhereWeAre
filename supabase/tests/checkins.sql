begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Sender"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Receiver"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Stranger"}');
insert into public.location_shares(owner_id,viewer_id,enabled,shared_precision) values
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',false,500);
select pg_temp.assert_true(not has_table_privilege('authenticated','private.app_events','select'),'raw snapshots private');
select pg_temp.assert_true(not has_table_privilege('authenticated','private.event_recipients','select'),'delivery payloads private');
select pg_temp.assert_true(not has_function_privilege('anon','public.event_inbox()','execute'),'anonymous event inbox denied');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.create_checkin('30000000-0000-0000-0000-000000000001','here',46.123456,11.123456,18,now(),array['00000000-0000-0000-0000-000000000002']::uuid[]);
select public.create_checkin('30000000-0000-0000-0000-000000000001','here',46.1,11.1,18,now(),array['00000000-0000-0000-0000-000000000002']::uuid[]);
select pg_temp.assert_true((select not is_sharing from public.sharing_status where user_id=auth.uid()),'snapshot never starts continuous sharing');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'snapshot never writes tracking table');
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'event creation idempotent');
select pg_temp.assert_true((public.event_inbox()->0->'payload'->>'latitude')::float8=46.123456,'sender sees original voluntarily sent snapshot');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((public.event_inbox()->0->'payload'->>'precision_m')::int=500,'recipient gets configured snapshot precision even when continuous sharing off');
select pg_temp.assert_true((public.event_inbox()->0->'payload'->>'latitude')::float8<>46.123456,'raw snapshot not returned to approximate recipient');
select pg_temp.assert_true(public.app_metadata()->'events'=public.event_inbox(),'metadata uses authorized event inbox');
do $$ begin
 begin perform public.remove_event('30000000-0000-0000-0000-000000000001');raise exception 'receiver deleted sender event';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.event_inbox()='[]','stranger cannot access event');
do $$ begin
 begin perform public.create_checkin('30000000-0000-0000-0000-000000000002','here',46,11,10,now(),array['00000000-0000-0000-0000-000000000001']::uuid[]);raise exception 'unrelated recipient accepted';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_shared_precision('person','00000000-0000-0000-0000-000000000002',0);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((public.event_inbox()->0->'payload'->>'precision_m')::int=500,'later grant cannot upgrade old snapshot to raw');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_shared_precision('person','00000000-0000-0000-0000-000000000002',1000);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((public.event_inbox()->0->'payload'->>'precision_m')::int=1000,'privacy downgrade coarsens past snapshot reads too');
reset role;
select pg_temp.assert_true((select count(*)=1 from private.push_outbox where kind='checkin'),'one notification per recipient/event');
insert into public.groups(id,name,emoji,invite_code,creator_id) values
 ('20000000-0000-0000-0000-000000000001','Group','G','ZZZ-YYY','00000000-0000-0000-0000-000000000001');
insert into public.group_members(group_id,user_id,sharing_enabled,shared_precision) values
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',false,1000),
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',false,null);
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.create_checkin('30000000-0000-0000-0000-000000000003','arrived',46,11,10,now(),'{}',array['20000000-0000-0000-0000-000000000001']::uuid[]);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select (e->'payload'->>'precision_m')::int=1000 from jsonb_array_elements(public.event_inbox()) e where e->>'id'='30000000-0000-0000-0000-000000000003'),'selected group override applies without accidental precise default');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.remove_event('30000000-0000-0000-0000-000000000001');
select public.create_checkin('30000000-0000-0000-0000-000000000001','here',46,11,10,now(),array['00000000-0000-0000-0000-000000000002']::uuid[]);
select pg_temp.assert_true(not exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'id'='30000000-0000-0000-0000-000000000001'),'late retry cannot resurrect removed snapshot');
select public.create_meeting('40000000-0000-0000-0000-000000000001',46,11,false,array['00000000-0000-0000-0000-000000000002']::uuid[]);
select public.create_checkin('30000000-0000-0000-0000-000000000004','okay',46,11,10,now(),'{}','{}','40000000-0000-0000-0000-000000000001');
do $$ begin
 begin perform public.create_checkin('30000000-0000-0000-0000-000000000005','here',46,11,10,now()-interval '5 minutes',array['00000000-0000-0000-0000-000000000002']::uuid[]);raise exception 'old fix accepted';
 exception when raise_exception then if sqlerrm<>'invalid_form' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'id'='30000000-0000-0000-0000-000000000004'),'meeting participants receive snapshot');
reset role;
update private.app_events set expires_at=now()-interval '1 second';
set local role authenticated;
select pg_temp.assert_true(public.event_inbox()='[]','expired events disappear for sender');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.event_inbox()='[]','expired events disappear for recipient');
reset role;
rollback;
