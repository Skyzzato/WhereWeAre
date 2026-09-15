begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Owner"}'),
 ('00000000-0000-0000-0000-000000000002','{"display_name":"Member"}'),
 ('00000000-0000-0000-0000-000000000003','{"display_name":"Invitee"}');
insert into public.groups(id,name,emoji,invite_code,creator_id,expires_at) values
 ('20000000-0000-0000-0000-000000000001','Temporary','T','ZZZ-YYY','00000000-0000-0000-0000-000000000001',now()+interval '1 hour');
insert into public.group_members(group_id,user_id) values
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001'),
 ('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.save_place('60000000-0000-0000-0000-000000000001',0,'Home',46,11,100);
select public.save_place_rule('70000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000001','enter','00000000-0000-0000-0000-000000000001',array['00000000-0000-0000-0000-000000000002']::uuid[],true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.publish_location('10000000-0000-0000-0000-000000000001',47,11,10,now()-interval '90 seconds');
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,10,now()-interval '60 seconds');
select pg_temp.assert_true(public.event_inbox()='[]','one inside fix does not notify');
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,10,now()-interval '20 seconds');
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'confirmed entry creates one event');
select public.publish_location('10000000-0000-0000-0000-000000000001',46,11,10,now());
select pg_temp.assert_true(jsonb_array_length(public.event_inbox())=1,'remaining inside does not duplicate');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select pg_temp.assert_true(public.places_rules()->'places'='[]','recipient cannot read place coordinates');
select pg_temp.assert_true(not ((public.event_inbox()->0->'payload') ? 'latitude'),'place notification contains no coordinates');
select pg_temp.assert_true(public.event_inbox()->0->'payload'->>'place_name'='Home','recipient receives selected label only');
do $$ begin
 begin perform public.save_place('60000000-0000-0000-0000-000000000001',0,'Steal',1,1,100);raise exception 'stole place';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise;end if;end;
 begin perform 1 from private.saved_places;raise exception 'raw place read allowed';exception when insufficient_privilege then null;end;
end $$;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000003',true);
select pg_temp.assert_true(public.event_inbox()='[]','unselected recipient receives no notice');
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
-- Watcher uses only precision the subject currently shares with the watcher.
select public.save_place_rule('70000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000001','arrival','00000000-0000-0000-0000-000000000002','{}',true);
reset role;
update public.group_members set shared_precision=500 where user_id='00000000-0000-0000-0000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000002',true);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000002',0);
select public.publish_location('10000000-0000-0000-0000-000000000002',47,11,10,now()-interval '90 seconds');
select public.publish_location('10000000-0000-0000-0000-000000000002',46,11,10,now()-interval '60 seconds');
select public.publish_location('10000000-0000-0000-0000-000000000002',46,11,10,now()-interval '20 seconds');
reset role;
select pg_temp.assert_true((select count(*)=1 from private.app_events),'approximate area cannot reveal precise arrival at home');
update public.group_members set shared_precision=0 where user_id='00000000-0000-0000-0000-000000000002';
-- A new session establishes a baseline instead of reporting an off-session transition.
set local role authenticated;
select public.set_sharing(true,'10000000-0000-0000-0000-000000000003',1);
select public.publish_location('10000000-0000-0000-0000-000000000003',46,11,10,now());
reset role;
select pg_temp.assert_true((select count(*)=1 from private.app_events),'new session does not fabricate arrival');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
select public.remove_place('60000000-0000-0000-0000-000000000001');
select pg_temp.assert_true(public.places_rules()->'rules'='[]','delete cascades to rules');
reset role;
rollback;
