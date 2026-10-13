begin;
-- Prepared enforcement: dormant until the existing bootstrap minimum reaches 18.
-- A version header is a compatibility signal, NOT APK attestation.
create table private.client_sessions(session_id uuid primary key,user_id uuid not null references public.profiles(id) on delete cascade,
 version_code integer not null,seen_at timestamptz not null);
alter table private.client_sessions enable row level security;
revoke all on private.client_sessions from public,anon,authenticated;
create function private.request_version() returns integer language plpgsql stable set search_path='' as $$
declare value text:=coalesce(nullif(current_setting('request.headers',true),''),'{}')::jsonb->>'x-whereweare-version';
begin return case when value ~ '^[0-9]{1,8}$' then value::integer else 0 end;end $$;
create function private.assert_client_version() returns void language plpgsql stable security definer set search_path='' as $$
declare minimum integer;
begin
 select minimum_supported_version_code into minimum from private.app_bootstrap where singleton;
 if minimum>=18 and private.request_version()<minimum then raise sqlstate 'PT426' using message='update_required';end if;
end $$;
create function public.check_client_version() returns void language plpgsql security definer set search_path='' as $$
declare path text:=current_setting('request.path',true);sid uuid:=nullif(auth.jwt()->>'session_id','')::uuid;caller uuid:=auth.uid();
begin
 if caller is null then return;end if;
 -- Bootstrap and revocation stay reachable from installed legacy binaries.
 if path in ('/rpc/app_bootstrap','/rpc/server_time','/rpc/set_sharing','/rpc/unregister_device','/rpc/end_nearby_availability') then return;end if;
 perform private.assert_client_version();
 if sid is not null and private.request_version()>0 then
  insert into private.client_sessions values(sid,caller,private.request_version(),clock_timestamp())
  on conflict(session_id) do update set version_code=excluded.version_code,seen_at=excluded.seen_at where client_sessions.user_id=excluded.user_id;
 end if;
end $$;
create function private.compatible_session() returns boolean language sql stable security definer set search_path='' as $$
 select (select minimum_supported_version_code<18 from private.app_bootstrap where singleton) or exists(
 select 1 from private.client_sessions s cross join private.app_bootstrap b where s.session_id=nullif(auth.jwt()->>'session_id','')::uuid
 and s.user_id=auth.uid() and s.version_code>=b.minimum_supported_version_code and s.seen_at>statement_timestamp()-interval '15 minutes');
$$;
-- Restrictive SELECT policies also apply to Realtime authorization, without disclosing coordinates.
do $$ declare t text;begin
 foreach t in array array['latest_locations','account_events','sharing_status','location_shares','share_requests'] loop
  execute format('create policy compatible_client on public.%I as restrictive for select to authenticated using(private.compatible_session())',t);
 end loop;
 if exists(select 1 from pg_roles where rolname='authenticator') then
  alter role authenticator set pgrst.db_pre_request='public.check_client_version';
 end if;
end $$;
alter function public.set_sharing(boolean,uuid,bigint) rename to set_sharing_before_v049;
alter function public.set_sharing_before_v049(boolean,uuid,bigint) set schema private;
create function public.set_sharing(active boolean,session uuid default null,expected_revision bigint default null) returns void
language plpgsql security definer set search_path='' as $$
begin if active then perform private.assert_client_version();end if;perform private.set_sharing_before_v049(active,session,expected_revision);end $$;
-- Also enforce inside the write RPC: old services cannot bypass UI through an existing session.
alter function public.publish_location(uuid,float8,float8,float8,timestamptz,float8,float8) rename to publish_before_v049;
alter function public.publish_before_v049(uuid,float8,float8,float8,timestamptz,float8,float8) set schema private;
create function public.publish_location(session uuid,lat float8,lon float8,acc float8,fix_at timestamptz,velocity float8 default null,heading float8 default null)
returns void language plpgsql security definer set search_path='' as $$
begin perform private.assert_client_version();perform private.publish_before_v049(session,lat,lon,acc,fix_at,velocity,heading);end $$;
create function public.publish_location_v049(session uuid,lat float8,lon float8,acc float8,fix_at timestamptz,fix_age_ms bigint,velocity float8 default null,heading float8 default null)
returns boolean language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform private.assert_client_version();
 if fix_age_ms is null or fix_age_ms not between 0 and 30000 or fix_at<clock_timestamp()-interval '45 seconds' then raise exception 'invalid_fix_time';end if;
 perform private.publish_before_v049(session,lat,lon,acc,fix_at,velocity,heading);
 return exists(select 1 from public.latest_locations where user_id=caller and share_session=session and device_recorded_at=fix_at);
end $$;
alter table private.app_bootstrap add column update_url text not null default 'https://github.com/Skyzzato/WhereWeAre/releases/tag/v0.48';
revoke all on function private.request_version(),private.assert_client_version(),private.set_sharing_before_v049(boolean,uuid,bigint),private.publish_before_v049(uuid,float8,float8,float8,timestamptz,float8,float8) from public,anon,authenticated,service_role;
revoke all on function private.compatible_session() from public,anon;
grant execute on function private.compatible_session() to authenticated;
revoke all on function public.check_client_version(),public.set_sharing(boolean,uuid,bigint),public.publish_location(uuid,float8,float8,float8,timestamptz,float8,float8),public.publish_location_v049(uuid,float8,float8,float8,timestamptz,bigint,float8,float8) from public,anon;
grant execute on function public.check_client_version() to anon,authenticated;
grant execute on function public.set_sharing(boolean,uuid,bigint),public.publish_location(uuid,float8,float8,float8,timestamptz,float8,float8),public.publish_location_v049(uuid,float8,float8,float8,timestamptz,bigint,float8,float8) to authenticated;
notify pgrst,'reload schema';
notify pgrst,'reload config';
commit;
