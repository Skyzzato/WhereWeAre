-- Regression for PostgREST GET/HEAD and STABLE RPC transactions.
-- Requires minimum_supported_version_code=18; no persistent fixtures or writes.
begin read only;
set local role authenticated;
select set_config('request.jwt.claim.sub','f0490000-0000-0000-0000-000000000091',true);
select set_config('request.jwt.claims','{"sub":"f0490000-0000-0000-0000-000000000091","session_id":"f0490000-0000-0000-0000-000000000092"}',true);
select set_config('request.headers','{"x-whereweare-version":"18"}',true);
select set_config('request.path','/share_requests',true);
select set_config('request.method','GET',true);
select public.check_client_version();
select count(*) from public.share_requests;
select set_config('request.method','HEAD',true);
select public.check_client_version();
-- The transaction mode, rather than HTTP method alone, determines writability.
select set_config('request.method','POST',true);
select public.check_client_version();
do $$ begin
  if (public.app_bootstrap()->>'minimum_supported_version_code')::int <> 18 then
    raise exception 'FAIL: regression requires live minimum 18';
  end if;
  perform set_config('request.headers','{"x-whereweare-version":"17"}',true);
  begin
    perform public.check_client_version();
    raise exception 'FAIL: legacy read admitted';
  exception when sqlstate 'PT426' then null;
  end;
  perform set_config('request.headers','{}',true);
  begin
    perform public.check_client_version();
    raise exception 'FAIL: missing version admitted';
  exception when sqlstate 'PT426' then null;
  end;
  perform set_config('request.path','/rpc/app_bootstrap',true);
  perform public.check_client_version();
  perform public.app_bootstrap();
end $$;
reset role;
do $$ begin
  if exists(select 1 from private.client_sessions
    where session_id='f0490000-0000-0000-0000-000000000092') then
    raise exception 'FAIL: read-only request persisted a session';
  end if;
end $$;
select 'PASS: compatible GET/HEAD/STABLE, legacy rejection, bootstrap, no writes' as result;
rollback;
