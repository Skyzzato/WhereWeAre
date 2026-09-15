begin;
create or replace function pg_temp.assert_true(value boolean,description text) returns void language plpgsql as $$
begin if value is distinct from true then raise exception 'FAIL: %',description; end if; end $$;
insert into auth.users(id,raw_user_meta_data) values
 ('00000000-0000-0000-0000-000000000001','{"display_name":"Recovery"}');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
-- The client persists revision 0 before start; losing the response must be recoverable.
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
select pg_temp.assert_true((select is_sharing and revision=1 and session_id='10000000-0000-0000-0000-000000000001'::uuid
 from public.sharing_status where user_id=auth.uid()),'lost ACK retry keeps the same session and revision');
select public.set_sharing(false,null);
do $$ begin
 begin
  perform public.set_sharing(true,'10000000-0000-0000-0000-000000000001',0);
  raise exception 'recovery undid remote stop';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise; end if; end;
end $$;
-- A second device may explicitly start; an old session may not publish or stop it.
select public.set_sharing(true,'10000000-0000-0000-0000-000000000002',2);
select public.set_sharing(false,'10000000-0000-0000-0000-000000000001');
select pg_temp.assert_true((select is_sharing and session_id='10000000-0000-0000-0000-000000000002'::uuid
 from public.sharing_status where user_id=auth.uid()),'scoped old stop preserves replacement session');
do $$ begin
 begin
  perform public.publish_location('10000000-0000-0000-0000-000000000001',46,11,10,now());
  raise exception 'old publisher accepted';
 exception when raise_exception then if sqlerrm<>'sharing_stopped' then raise; end if; end;
end $$;
reset role;
rollback;
