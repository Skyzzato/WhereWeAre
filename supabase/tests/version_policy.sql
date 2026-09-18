begin;
create or replace function pg_temp.assert_true(value boolean, description text)
returns void language plpgsql as $$
begin
  if value is distinct from true then raise exception 'FAIL: %', description; end if;
end $$;
select pg_temp.assert_true((public.app_bootstrap()->>'latest_version_code')::int = 17, 'latest code 17');
select pg_temp.assert_true(public.app_bootstrap()->>'latest_version_name' = '0.48', 'latest name 0.48');
update private.app_bootstrap set minimum_supported_version_code = 17 where singleton = true;
set local role anon;
select pg_temp.assert_true((public.app_bootstrap()->>'minimum_supported_version_code')::int = 17, 'anonymous startup sees minimum');
reset role;
set local role authenticated;
select pg_temp.assert_true((public.app_bootstrap()->>'minimum_supported_version_code')::int = 17, 'authenticated startup sees minimum');
reset role;
rollback;
