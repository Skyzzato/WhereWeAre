begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; raise notice 'PASS: %',description; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Admin"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Member"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Outsider"}');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select set_config('test.group',public.create_group('Rocket team','🚀')::text,true);
select set_config('test.code',(select invite_code from public.groups where id=current_setting('test.group')::uuid),true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,5,now());
select public.set_group_sharing(current_setting('test.group')::uuid,false);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.join_group(current_setting('test.code'));
select set_config('test.join',(select id::text from public.group_requests where status='pending'),true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.respond_group_request(current_setting('test.join')::uuid,true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.save_group_person('00000000-0000-0000-0000-000000000001');
select public.save_group_person('00000000-0000-0000-0000-000000000001');
select pg_temp.assert_true((select count(*)=1 from public.saved_people),'saving twice yields one association');
select pg_temp.assert_true((select count(*)=0 from public.location_shares),'saving creates no GPS grants');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'saving does not bypass disabled group GPS');
select pg_temp.assert_true(jsonb_array_length(public.app_metadata()->'saved_people')=1,'metadata includes saved IDs');
do $$ begin
 begin perform public.save_group_person('00000000-0000-0000-0000-000000000002'); raise exception 'self accepted'; exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
 begin perform public.save_group_person('00000000-0000-0000-0000-000000000003'); raise exception 'outsider accepted'; exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select pg_temp.assert_true((select count(*)=0 from public.saved_people),'address book association is private to its owner');
select public.create_meeting_styled('20000000-0000-0000-0000-000000000001',46,11,false,'{}',array[current_setting('test.group')::uuid],50);
select public.create_meeting_styled('20000000-0000-0000-0000-000000000001',46,11,false,'{}','{}',31);
select pg_temp.assert_true((select flare_style_id=50 from public.meeting_points where id='20000000-0000-0000-0000-000000000001'),'style 50 preserved on conflicting retry');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((public.meeting_inbox()->0->>'flare_style_id')::int=50,'recipient receives creator rocket style');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.remove_group_member(current_setting('test.group')::uuid,'00000000-0000-0000-0000-000000000002');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=1 from public.saved_people),'saved contact survives leaving group');
select pg_temp.assert_true((select not common_group and not can_view from public.contact_profiles_v03() where user_id='00000000-0000-0000-0000-000000000001'),'saved profile survives with GPS denied');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'saved contact after group exit grants no location');
select public.request_saved_person('00000000-0000-0000-0000-000000000001');
select pg_temp.assert_true((select count(*)=1 from public.share_requests where status='pending'),'GPS access still requires an explicit consent request');
select pg_temp.assert_true((select count(*)=0 from public.location_shares),'pending consent still creates no grant');
reset role;
select set_config('test.revision',(select revision::text from public.account_events where user_id='00000000-0000-0000-0000-000000000002'),true);
update public.profiles set display_name='Updated admin' where id='00000000-0000-0000-0000-000000000001';
select pg_temp.assert_true((select revision>current_setting('test.revision')::bigint from public.account_events where user_id='00000000-0000-0000-0000-000000000002'),'profile changes invalidate saved contact metadata');
select pg_temp.assert_true(not has_table_privilege('authenticated','public.saved_people','insert'),'direct insert cannot bypass common membership');
select pg_temp.assert_true(not has_function_privilege('anon','public.save_group_person(uuid)','execute'),'anonymous cannot save contacts');
select pg_temp.assert_true((select latest_version_code=8 and latest_version_name='0.33' and minimum_supported_version_code=4 from private.app_bootstrap),'v0.33 advertised with backwards compatibility');
select pg_temp.assert_true((select count(*)=2 from pg_publication_tables where pubname='supabase_realtime' and tablename in ('account_events','latest_locations')),'metadata invalidation and positions published to realtime');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.remove_connection('00000000-0000-0000-0000-000000000001');
select pg_temp.assert_true((select count(*)=0 from public.saved_people),'remove connection removes saved association');
rollback;
