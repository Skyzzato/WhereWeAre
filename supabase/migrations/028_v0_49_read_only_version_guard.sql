-- Correct the pre-request hook from 025 without weakening version enforcement.
-- PostgREST uses read-only transactions for GET/HEAD and STABLE RPCs.
begin;

create or replace function public.check_client_version() returns void
language plpgsql security definer set search_path = '' as $$
declare
  path text := current_setting('request.path', true);
  sid uuid := nullif(auth.jwt()->>'session_id', '')::uuid;
  caller uuid := auth.uid();
begin
  if caller is null then return; end if;
  -- Preserve access to bootstrap and revocation for installed legacy binaries.
  if path in (
    '/rpc/app_bootstrap', '/rpc/server_time', '/rpc/set_sharing',
    '/rpc/unregister_device', '/rpc/end_nearby_availability'
  ) then return; end if;

  -- Always check compatibility, including in read-only transactions.
  perform private.assert_client_version();

  -- app_metadata and position publications renew the Realtime session through
  -- read-write RPCs. A GET/HEAD must never attempt this database write.
  if current_setting('transaction_read_only') = 'off'
     and sid is not null and private.request_version() > 0 then
    insert into private.client_sessions as existing
      (session_id, user_id, version_code, seen_at)
    values (sid, caller, private.request_version(), clock_timestamp())
    on conflict (session_id) do update
      set version_code = excluded.version_code, seen_at = excluded.seen_at
      where existing.user_id = excluded.user_id;
  end if;
end;
$$;

notify pgrst, 'reload schema';
commit;
