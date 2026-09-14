begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; raise notice 'PASS: %',description; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Admin"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Member"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Invitee"}'),
 ('00000000-0000-0000-0000-000000000004','{"display_name":"Outsider"}');
-- Relationship in the opposite direction: invitee cannot read admin GPS through it.
insert into public.location_shares(owner_id,viewer_id) values
 ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select set_config('test.group',public.create_group('Original','📍')::text,true);
select set_config('test.code',(select invite_code from public.groups where id=current_setting('test.group')::uuid),true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,5,now());
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.join_group(current_setting('test.code'));
select set_config('test.join',(select id::text from public.group_requests where status='pending'),true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.respond_group_request(current_setting('test.join')::uuid,true);
reset role;
select set_config('test.revisions',(select jsonb_object_agg(user_id,revision)::text from public.account_events),true);
set local role authenticated;
select public.invite_group_member(current_setting('test.group')::uuid,'00000000-0000-0000-0000-000000000003');
select set_config('test.invite',(select id::text from public.group_requests where status='pending'),true);
select public.invite_group_member(current_setting('test.group')::uuid,'00000000-0000-0000-0000-000000000003');
select pg_temp.assert_true((select count(*)=1 from public.group_requests where status='pending'),'repeated invite has a single pending row');
select public.edit_group(current_setting('test.group')::uuid,'  Mountain team  ','🏔️');
select pg_temp.assert_true((select name='Mountain team' and emoji='🏔️' and invite_code=current_setting('test.code') from public.groups),'name and emoji update atomically without changing code');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true((select count(*)=1 from public.group_requests where kind='invite' and status='pending'),'existing member sees pending invite in member list');
select pg_temp.assert_true((select (r->>'group_name')='Mountain team' and r->>'group_emoji'='🏔️' and not (r->>'can_cancel')::boolean from jsonb_array_elements(public.group_inbox()) r where r->>'id'=current_setting('test.invite')),'member receives edited identity without cancellation permission');
do $$ begin
 begin perform public.cancel_group_invitation(current_setting('test.invite')::uuid); raise exception 'unauthorized cancellation'; exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
 begin perform public.edit_group(current_setting('test.group')::uuid,'Bad','X'); raise exception 'unauthorized edit'; exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true((select (r->>'can_respond')::boolean and r->>'group_name'='Mountain team' from jsonb_array_elements(public.group_inbox()) r where r->>'id'=current_setting('test.invite')),'recipient can respond and sees latest group identity');
select pg_temp.assert_true((select count(*)=0 from public.latest_locations),'pending invitation grants no GPS access');
select pg_temp.assert_true((select count(*)=0 from public.group_members),'pending invitation grants no membership');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000004',true);
select pg_temp.assert_true(jsonb_array_length(public.group_inbox())=0 and (select count(*)=0 from public.group_requests),'outsider cannot enumerate invitations');
reset role;
select pg_temp.assert_true((select bool_and(revision>coalesce((current_setting('test.revisions')::jsonb->>user_id::text)::bigint,0)) from public.account_events where user_id<>'00000000-0000-0000-0000-000000000004'),'invite and edit notify admin, member and recipient');
select set_config('test.revisions',(select jsonb_object_agg(user_id,revision)::text from public.account_events),true);
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.cancel_group_invitation(current_setting('test.invite')::uuid);
select public.cancel_group_invitation(current_setting('test.invite')::uuid);
reset role;
select pg_temp.assert_true((select count(*)=0 from public.group_requests where id=current_setting('test.invite')::uuid),'cancellation really deletes pending row and is idempotent');
select pg_temp.assert_true((select bool_and(revision>(current_setting('test.revisions')::jsonb->>user_id::text)::bigint) from public.account_events where user_id<>'00000000-0000-0000-0000-000000000004'),'cancellation invalidates all affected clients');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(jsonb_array_length(public.group_inbox())=0,'cancelled invitation disappears from recipient inbox');
do $$ begin
 begin perform public.respond_group_request(current_setting('test.invite')::uuid,true); raise exception 'cancelled request accepted'; exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
end $$;
select pg_temp.assert_true((select count(*)=0 from public.group_members),'late acceptance cannot resurrect cancelled invitation');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.invite_group_member(current_setting('test.group')::uuid,'00000000-0000-0000-0000-000000000003');
select set_config('test.invite',(select id::text from public.group_requests where status='pending'),true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select public.respond_group_request(current_setting('test.invite')::uuid,false);
select pg_temp.assert_true((select count(*)=0 from public.group_requests where status='pending'),'rejection removes pending status');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.invite_group_member(current_setting('test.group')::uuid,'00000000-0000-0000-0000-000000000003');
select set_config('test.invite',(select id::text from public.group_requests where status='pending'),true);
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select public.respond_group_request(current_setting('test.invite')::uuid,true);
select pg_temp.assert_true((select count(*)=3 from public.group_members),'accepted invite becomes one actual member');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.cancel_group_invitation(current_setting('test.invite')::uuid);
select pg_temp.assert_true((select count(*)=3 from public.group_members),'late cancellation never removes accepted member');
select public.rename_group(current_setting('test.group')::uuid,'Legacy works');
select pg_temp.assert_true((select name='Legacy works' and emoji='🏔️' from public.groups),'legacy v0.3 rename RPC remains compatible');
reset role;
select pg_temp.assert_true(not has_function_privilege('anon','public.edit_group(uuid,text,text)','execute') and not has_function_privilege('anon','public.cancel_group_invitation(uuid)','execute'),'anonymous clients cannot mutate groups');
select pg_temp.assert_true(not has_table_privilege('authenticated','public.group_requests','delete'),'clients cannot bypass cancellation RPC');
select pg_temp.assert_true((select latest_version_code=6 and latest_version_name='0.31' and minimum_supported_version_code=4 from private.app_bootstrap),'v0.31 advertised while v0.3 remains supported');
rollback;
