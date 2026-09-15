begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Owner"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Viewer"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Stranger"}');
insert into public.location_shares(owner_id,viewer_id,enabled) values
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(not has_function_privilege('anon','public.update_device_status(uuid,integer,boolean)','execute'),'anonymous RPC denied');
select pg_temp.assert_true(not has_table_privilege('authenticated','public.latest_locations','update'),'direct device state mutation denied');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select pg_temp.assert_true(not public.update_device_status('10000000-0000-0000-0000-000000000001',64,true),'no fabricated location row');
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,18,now());
select pg_temp.assert_true(public.update_device_status('10000000-0000-0000-0000-000000000001',64,true),'owner writes active session');
select pg_temp.assert_true((select battery_level=64 and location_enabled and device_status_at>=now() from public.latest_locations where user_id=auth.uid()),'metadata has a server timestamp');
do $$ begin
 begin perform public.update_device_status('10000000-0000-0000-0000-000000000001',101,true);raise exception 'invalid battery accepted';
 exception when raise_exception then if sqlerrm<>'invalid_battery' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select battery_level=64 from public.latest_locations),'authorized viewer sees metadata');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'stranger cannot read battery or location state');
do $$ begin
 begin perform public.update_device_status('10000000-0000-0000-0000-000000000001',1,false);raise exception 'stranger changed metadata';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_location_share('00000000-0000-0000-0000-000000000002',false);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'revocation also revokes device metadata');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_sharing(false,null);
do $$ begin
 begin perform public.update_device_status('10000000-0000-0000-0000-000000000001',64,false);raise exception 'stopped session wrote metadata';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise;end if;end;
end $$;
select public.set_sharing(true,'10000000-0000-0000-0000-000000000002',2);
select public.publish_location('10000000-0000-0000-0000-000000000002',46,11,18,clock_timestamp());
select pg_temp.assert_true((select battery_level is null and device_status_at is null from public.latest_locations where user_id=auth.uid()),'replacement session cannot inherit old device state');
reset role;
rollback;
