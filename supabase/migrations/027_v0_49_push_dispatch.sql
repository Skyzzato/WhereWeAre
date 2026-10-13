-- Server-only follow-up after the immutable v0.49 APK was published.
-- Prerequisite: matching PUSH_DISPATCH_SECRET in the Edge Function environment
-- and whereweare_push_dispatch in Vault; FIREBASE_SERVICE_ACCOUNT configured.
-- No credential value belongs in this migration or in the cron command.
begin;

create extension if not exists pg_net with schema extensions;

create or replace function private.dispatch_push_v049() returns bigint
language plpgsql security definer set search_path = '' as $$
declare dispatch_key text;
begin
  if not exists (
    select 1 from private.push_outbox
    where sent_at is null and attempts < 8
      and (leased_until is null or leased_until < clock_timestamp())
  ) then
    return null;
  end if;

  select decrypted_secret into dispatch_key
  from vault.decrypted_secrets where name = 'whereweare_push_dispatch';
  if dispatch_key is null then
    raise exception 'push_dispatch_not_configured';
  end if;

  return net.http_post(
    url := 'https://vqvouzpsgbuaddcyitzg.supabase.co/functions/v1/send-meeting-push',
    headers := jsonb_build_object(
      'Content-Type', 'application/json', 'Authorization', 'Bearer ' || dispatch_key
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
end;
$$;

revoke all on function private.dispatch_push_v049()
  from public, anon, authenticated, service_role;

select cron.schedule(
  'whereweare-push-dispatch', '* * * * *',
  'select private.dispatch_push_v049()'
);

commit;
