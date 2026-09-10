\set ON_ERROR_STOP on
begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; raise notice 'PASS: %',description; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Alice"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Bruno"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Carla"}');
select pg_temp.assert_true((select bool_and(visibility_seconds=86400) from public.profiles),'default 24 hours');
set local role anon;
select pg_temp.assert_true((public.app_bootstrap()->>'minimum_supported_version_code')::int=2,'anonymous bootstrap minimal versionCode 2');
reset role;
select pg_temp.assert_true(not has_table_privilege('anon','private.app_bootstrap','select'),'bootstrap table inaccessible');
select pg_temp.assert_true(not has_table_privilege('anon','public.groups','select'),'anonymous groups denied');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select set_config('test.group',public.create_group('Montagna 🏔️','🏔️')::text,true);
select set_config('test.code',(select invite_code from public.groups where id=current_setting('test.group')::uuid),true);
select pg_temp.assert_true((select length(invite_code)=24 from public.groups),'group code 96-bit');
select pg_temp.assert_true((select created_at=now() from public.groups),'server created_at');
do $$ begin
 begin perform public.create_group(repeat('a',25),'📍'); raise exception 'long name accepted'; exception when check_violation then null; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=0 from public.groups),'groups not enumerable');
select public.join_group(current_setting('test.code'));
select public.join_group(current_setting('test.code'));
select pg_temp.assert_true((select count(*)=2 from public.group_members),'join retry no duplicate');
select pg_temp.assert_true((select count(*)=2 from public.contact_profiles()),'common profiles only');
select public.set_sharing(true,'10000000-0000-0000-0000-000000000002',0);
select public.publish_location('10000000-0000-0000-0000-000000000002',41,12,2000,now());
select public.set_visibility(1800);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select accuracy=2000 from public.latest_locations),'group authorized poor accuracy fix retained');
reset role;
update public.latest_locations set recorded_at=now()-interval '31 minutes';
set local role authenticated;
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'owner timeout enforced in RLS');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'own expired location retained');
select public.set_visibility(86400);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'changed timeout applies');
reset role;
insert into public.location_shares(owner_id,viewer_id) values
 ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002'),
 ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001');
set local role authenticated;
select public.remove_connection('00000000-0000-0000-0000-000000000002');
select pg_temp.assert_true((select count(*)=0 from public.location_shares),'reciprocal direct removal');
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'group path survives direct removal');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_sharing(false,null);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'master OFF overrides group');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000003',2);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'new sharing session does not expose old fix');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.publish_location('10000000-0000-0000-0000-000000000003',41,12,10,now());
insert into storage.objects(bucket_id,name) values('avatars','00000000-0000-0000-0000-000000000002/10000000-0000-0000-0000-000000000001.webp');
select public.set_avatar('00000000-0000-0000-0000-000000000002/10000000-0000-0000-0000-000000000001.webp');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=1 from storage.objects),'group avatar readable');
select set_config('test.revision',(select revision from public.account_events where user_id=auth.uid())::text,true);
select public.remove_group_member(current_setting('test.group')::uuid,'00000000-0000-0000-0000-000000000002');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'last authorization path removed');
select pg_temp.assert_true((select count(*)=0 from storage.objects),'avatar revoked with last path');
select pg_temp.assert_true((select revision>current_setting('test.revision')::bigint from public.account_events where user_id=auth.uid()),'private invalidation survives member removal');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true((select count(*)=0 from storage.objects),'third party avatar denied');
do $$ begin
 begin perform public.delete_group(current_setting('test.group')::uuid); raise exception 'unauthorized group deletion';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
 begin insert into storage.objects(bucket_id,name) values('avatars','00000000-0000-0000-0000-000000000002/20000000-0000-0000-0000-000000000001.webp'); raise exception 'foreign avatar upload';
 exception when insufficient_privilege then null; end;
end $$;
reset role;
update public.latest_locations set recorded_at=now()-interval '1 year';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=1 from public.latest_locations),'owner reads year-old fix');
reset role;
delete from auth.users where id='00000000-0000-0000-0000-000000000001';
select pg_temp.assert_true((select count(*)=0 from public.groups),'creator deletion cascades groups');
select pg_temp.assert_true((select count(*)=0 from public.group_members),'group memberships cascade');
rollback;
