begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Owner"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Viewer"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Stranger"}');
insert into public.location_shares(owner_id,viewer_id,enabled) values
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(not has_function_privilege('anon','public.visible_locations()','execute'),'anonymous reads denied');
select pg_temp.assert_true(not has_table_privilege('authenticated','private.location_grid','select'),'private origin inaccessible');
select pg_temp.assert_true(not has_column_privilege('authenticated','public.profiles','shared_precision','update'),'no direct default mutation');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.publish_location('10000000-0000-0000-0000-000000000001',46.123456,11.123456,18,now());
select public.set_shared_precision('default',null,500);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'owner retains raw access');
select pg_temp.assert_true((public.visible_locations()->0->>'latitude')::float8=46.123456,'owner RPC exact');
select pg_temp.assert_true((public.location_audience()->0->>'precision_m')::int=500,'audience follows default');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'approximate viewer cannot read raw through table or realtime RLS');
select pg_temp.assert_true(not public.can_read_precise_location('00000000-0000-0000-0000-000000000001',now(),'10000000-0000-0000-0000-000000000001'),'raw policy also rejects explicit access');
select pg_temp.assert_true((public.visible_locations()->0->>'precision_m')::int=500,'server returns approximate radius');
select pg_temp.assert_true((public.visible_locations()->0->>'latitude')::float8<>46.123456,'RPC does not return raw latitude');
select pg_temp.assert_true(public.visible_locations()=public.visible_locations(),'repeated samples stable');
select pg_temp.assert_true(public.visible_locations()->0->>'speed' is null and public.visible_locations()->0->>'bearing' is null,'motion details suppressed');
do $$ begin
 begin perform public.set_shared_precision('person','00000000-0000-0000-0000-000000000001',0);raise exception 'viewer changed grant';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.visible_locations()='[]','stranger RPC denied');
select pg_temp.assert_true(public.location_audience()='[]','stranger cannot inspect another owner');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_shared_precision('person','00000000-0000-0000-0000-000000000002',250);
select pg_temp.assert_true((public.location_audience()->0->>'precision_m')::int=250,'person overrides default');
reset role;
insert into public.groups(id,name,emoji,invite_code,creator_id) values
 ('20000000-0000-0000-0000-000000000001','Family','F','ZZZ-YYY','00000000-0000-0000-0000-000000000001');
insert into public.group_members(group_id,user_id,shared_precision) values
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',0),
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',null);
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(jsonb_array_length(public.location_audience())=1,'overlapping grants deduplicate recipient');
select pg_temp.assert_true(jsonb_array_length(public.location_audience()->0->'sources')=2,'both grant sources visible');
select pg_temp.assert_true((public.location_audience()->0->>'precision_m')::int=0,'explicit group precision wins');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select latitude=46.123456 from public.latest_locations),'explicit exact recipient can read raw');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_group_sharing('20000000-0000-0000-0000-000000000001',false);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'revocation immediately removes raw grant');
select pg_temp.assert_true((public.visible_locations()->0->>'precision_m')::int=250,'revocation falls back to remaining grant');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.set_shared_precision('person','00000000-0000-0000-0000-000000000002',null);
select pg_temp.assert_true((public.location_audience()->0->>'precision_m')::int=500,'null override restores default');
select public.set_sharing(false,null);
select pg_temp.assert_true(public.location_audience()='[]','stopped sharing has no active audience');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.visible_locations()='[]','stop revokes approximate data too');
reset role;
create temporary table prior_area as select a.* from private.approximate_location('00000000-0000-0000-0000-000000000001',46.123456,11.123456,500) a;
update public.sharing_status set is_sharing=true where user_id='00000000-0000-0000-0000-000000000001';
update public.latest_locations set recorded_at=clock_timestamp() where user_id='00000000-0000-0000-0000-000000000001';
select pg_temp.assert_true((select a.latitude=p.latitude and a.longitude=p.longitude from private.approximate_location('00000000-0000-0000-0000-000000000001',46.123456,11.123456,500) a cross join prior_area p),'position updates and sharing restart preserve the same grid');
update public.profiles set visibility_seconds=600 where id='00000000-0000-0000-0000-000000000001';
update public.latest_locations set recorded_at=now()-interval '11 minutes' where user_id='00000000-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.assert_true(public.visible_locations()='[]','expired positions also disappear from approximate RPC');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true(public.location_audience()='[]','expired owner location has no current audience');
do $$ begin
 begin perform public.set_shared_precision('default',null,-1);raise exception 'invalid radius accepted';
 exception when raise_exception then if sqlerrm<>'invalid_precision' then raise;end if;end;
end $$;
reset role;
-- Great-circle bound at equator, poles and date line for all privacy levels.
do $$ declare lat float8; lon float8; r int; a record; distance float8;
begin
 foreach lat in array array[-90.,-89.999,0.,46.123456,89.999,90.] loop
  foreach lon in array array[-180.,-11.123456,0.,179.999] loop
   foreach r in array array[250,500,1000] loop
    select * into a from private.approximate_location('00000000-0000-0000-0000-000000000001',lat,lon,r);
    distance:=6371000*acos(least(1.,greatest(-1.,sin(radians(lat))*sin(radians(a.latitude))+cos(radians(lat))*cos(radians(a.latitude))*cos(radians(lon-a.longitude)))));
    perform pg_temp.assert_true(distance<=r,'approximation center within declared radius');
   end loop;
  end loop;
 end loop;
end $$;
rollback;
