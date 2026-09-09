begin;
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
create extension if not exists pgcrypto with schema extensions;
create type public.request_status as enum ('pending','accepted','rejected','cancelled');

create table public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 display_name text not null check (length(btrim(display_name)) between 1 and 80),
 invite_code text not null unique check (invite_code ~ '^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{4}$'),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.share_requests (
 id uuid primary key default gen_random_uuid(),
 sender_id uuid not null references public.profiles(id) on delete cascade,
 receiver_id uuid not null references public.profiles(id) on delete cascade,
 status public.request_status not null default 'pending',
 created_at timestamptz not null default now(), responded_at timestamptz,
 check(sender_id <> receiver_id)
);
create unique index one_pending_pair on public.share_requests
 (least(sender_id,receiver_id),greatest(sender_id,receiver_id)) where status='pending';
create index requests_sender on public.share_requests(sender_id);
create index requests_receiver on public.share_requests(receiver_id);
create index requests_status on public.share_requests(status);
create table public.location_shares (
 owner_id uuid not null references public.profiles(id) on delete cascade,
 viewer_id uuid not null references public.profiles(id) on delete cascade,
 enabled boolean not null default true,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 primary key(owner_id,viewer_id), check(owner_id <> viewer_id)
);
create index shares_viewer on public.location_shares(viewer_id);
create table public.sharing_status (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 is_sharing boolean not null default false,
 session_id uuid,
 revision bigint not null default 0,
 updated_at timestamptz not null default now()
);
create table public.latest_locations (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 latitude double precision not null check(latitude between -90 and 90),
 longitude double precision not null check(longitude between -180 and 180),
 accuracy double precision not null check(accuracy >= 0 and accuracy < 'Infinity'::float8),
 speed double precision check(speed >= 0 and speed < 'Infinity'::float8),
 bearing double precision check(bearing >= 0 and bearing < 360),
 recorded_at timestamptz not null,
 device_recorded_at timestamptz not null,
 server_received_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index locations_expiry on public.latest_locations(recorded_at);
create table private.lookup_limits (
 user_id uuid primary key references auth.users(id) on delete cascade,
 window_start timestamptz not null, attempts integer not null
);
alter table private.lookup_limits enable row level security;

create function private.touch() returns trigger language plpgsql set search_path='' as $$
begin new.updated_at := clock_timestamp(); return new; end $$;
create trigger profiles_touch before update on public.profiles for each row execute function private.touch();
create trigger shares_touch before update on public.location_shares for each row execute function private.touch();
create trigger status_touch before update on public.sharing_status for each row execute function private.touch();

create function private.invite_code() returns text language plpgsql set search_path='' as $$
declare chars constant text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ'; code text := ''; bytes bytea;
begin
 -- Rejection sampling avoids modulo bias. Eight independent symbols, ~39 bits.
 while length(code)<8 loop
  bytes := extensions.gen_random_bytes(1);
  if get_byte(bytes,0)<248 then code := code || substr(chars,(get_byte(bytes,0)%31)+1,1); end if;
 end loop;
 return substr(code,1,4)||'-'||substr(code,5,4);
end $$;
create function private.create_profile(uid uuid, name text) returns void language plpgsql set search_path='' as $$
begin
 if exists(select 1 from public.profiles where id=uid) then return; end if;
 loop
  begin
   insert into public.profiles(id,display_name,invite_code)
    values(uid,left(coalesce(nullif(btrim(name),''),'Utente'),80),private.invite_code());
   exit;
  exception when unique_violation then
   if exists(select 1 from public.profiles where id=uid) then exit; end if;
  end;
 end loop;
 insert into public.sharing_status(user_id) values(uid) on conflict do nothing;
end $$;
create function private.on_signup() returns trigger language plpgsql security definer set search_path='' as $$
begin perform private.create_profile(new.id,new.raw_user_meta_data->>'display_name'); return new; end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function private.on_signup();
select private.create_profile(id,raw_user_meta_data->>'display_name') from auth.users;

create function private.require_user() returns uuid language plpgsql stable set search_path='' as $$
begin if auth.uid() is null then raise exception 'not_authenticated'; end if; return auth.uid(); end $$;
create function private.pair_lock(a uuid,b uuid) returns void language sql set search_path='' as $$
 select pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(least(a,b)::text||greatest(a,b)::text,0));
$$;
create function private.lookup(code text) returns uuid language plpgsql set search_path='' as $$
declare caller uuid := private.require_user(); normalized text; found uuid; count_used integer;
begin
 insert into private.lookup_limits values(caller,clock_timestamp(),1)
 on conflict(user_id) do update set
 attempts=case when lookup_limits.window_start < clock_timestamp()-interval '1 hour' then 1 else lookup_limits.attempts+1 end,
 window_start=case when lookup_limits.window_start < clock_timestamp()-interval '1 hour' then clock_timestamp() else lookup_limits.window_start end
 returning attempts into count_used;
 -- Return no result instead of raising: raising would roll back the rate counter.
 if count_used>60 then return null; end if;
 normalized := upper(regexp_replace(code,'[\s-]','','g'));
 if normalized !~ '^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$' then return null; end if;
 select id into found from public.profiles where invite_code=substr(normalized,1,4)||'-'||substr(normalized,5,4) and id<>caller;
 return found;
end $$;
create function public.lookup_user_by_invite_code(code text)
 returns table(user_id uuid,display_name text,invite_code text)
 language plpgsql security definer set search_path='' as $$
declare target uuid;
begin perform private.require_user(); target:=private.lookup(code);
 return query select p.id,p.display_name,p.invite_code from public.profiles p where p.id=target;
end $$;
create function public.send_share_request(code text) returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); target uuid; request uuid;
begin
 target:=private.lookup(code); if target is null then return null; end if;
 perform private.pair_lock(caller,target);
 select id into request from public.share_requests where status='pending' and
 least(sender_id,receiver_id)=least(caller,target) and greatest(sender_id,receiver_id)=greatest(caller,target);
 if request is not null then return request; end if;
 if exists(select 1 from public.location_shares where owner_id=caller and viewer_id=target) then raise exception 'already_connected'; end if;
 insert into public.share_requests(sender_id,receiver_id) values(caller,target) returning id into request;
 return request;
end $$;
create function public.respond_to_share_request(request_id uuid, accept boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.share_requests;
begin
 select * into r from public.share_requests where id=request_id;
 if r.id is null or r.receiver_id<>caller then raise exception 'not_authorized'; end if;
 perform private.pair_lock(r.sender_id,r.receiver_id);
 select * into r from public.share_requests where id=request_id for update;
 if r.status<>'pending' then return; end if;
 update public.share_requests set status=case when accept then 'accepted'::public.request_status else 'rejected'::public.request_status end,responded_at=now() where id=request_id;
 if accept then
  insert into public.location_shares(owner_id,viewer_id,enabled) values(r.sender_id,r.receiver_id,true),(r.receiver_id,r.sender_id,true)
  on conflict(owner_id,viewer_id) do update set enabled=true;
 end if;
end $$;
create function public.cancel_share_request(request_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.share_requests;
begin
 select * into r from public.share_requests where id=request_id;
 if r.id is null or r.sender_id<>caller then raise exception 'not_authorized'; end if;
 perform private.pair_lock(r.sender_id,r.receiver_id);
 update public.share_requests set status='cancelled',responded_at=now() where id=request_id and status='pending';
end $$;
create function public.set_location_share(viewer uuid, enabled boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform private.pair_lock(caller,viewer);
 update public.location_shares s set enabled=set_location_share.enabled where s.owner_id=caller and s.viewer_id=viewer;
 if not found then raise exception 'not_authorized'; end if;
end $$;
create function public.set_sharing(active boolean, session uuid, expected_revision bigint default null) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if active and session is null then raise exception 'invalid_session'; end if;
 -- Status row lock serializes uploads with stop. An old stop cannot stop a new session.
 if active then
  update public.sharing_status set is_sharing=true,session_id=session,revision=revision+1
   where user_id=caller and revision=expected_revision;
  if not found and not exists(select 1 from public.sharing_status where user_id=caller and is_sharing and session_id=session) then
   raise exception 'sharing_stopped';
  end if;
 else
  update public.sharing_status set is_sharing=false,revision=revision+1 where user_id=caller and (session is null or session_id=session);
 end if;
end $$;
create function public.publish_location(session uuid, lat double precision, lon double precision,
 acc double precision, fix_at timestamptz, velocity double precision default null, heading double precision default null)
 returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); status public.sharing_status; received timestamptz:=clock_timestamp();
begin
 select * into status from public.sharing_status where user_id=caller for update;
 if not status.is_sharing or status.session_id is distinct from session then raise exception 'sharing_stopped'; end if;
 if fix_at is null or fix_at>received+interval '30 seconds' or fix_at<received-interval '2 hours' then raise exception 'invalid_fix_time'; end if;
 insert into public.latest_locations(user_id,latitude,longitude,accuracy,speed,bearing,recorded_at,device_recorded_at,server_received_at,updated_at)
 values(caller,lat,lon,acc,velocity,heading,least(fix_at,received),fix_at,received,received)
 on conflict(user_id) do update set latitude=excluded.latitude,longitude=excluded.longitude,accuracy=excluded.accuracy,
 speed=excluded.speed,bearing=excluded.bearing,recorded_at=excluded.recorded_at,device_recorded_at=excluded.device_recorded_at,
 server_received_at=excluded.server_received_at,updated_at=excluded.updated_at
 where excluded.recorded_at>latest_locations.recorded_at;
end $$;
-- Restricted contact names, without exposing invite codes or an enumerable profiles endpoint.
create function public.contact_names() returns table(user_id uuid,display_name text)
 language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return query select p.id,p.display_name from public.profiles p where p.id=caller or exists(
 select 1 from public.location_shares s where (s.owner_id=caller and s.viewer_id=p.id) or (s.viewer_id=caller and s.owner_id=p.id)
) or exists(select 1 from public.share_requests r where (r.sender_id=caller and r.receiver_id=p.id) or (r.receiver_id=caller and r.sender_id=p.id)); end $$;
create function public.server_time() returns timestamptz language sql stable set search_path='' as $$ select now() $$;

alter table public.profiles enable row level security;
alter table public.share_requests enable row level security;
alter table public.location_shares enable row level security;
alter table public.sharing_status enable row level security;
alter table public.latest_locations enable row level security;
revoke all on public.profiles,public.share_requests,public.location_shares,public.sharing_status,public.latest_locations from anon,authenticated;
grant select on public.profiles,public.share_requests,public.location_shares,public.sharing_status,public.latest_locations to authenticated;
grant update(display_name) on public.profiles to authenticated;
create policy profile_read on public.profiles for select to authenticated using(id=(select auth.uid()));
create policy profile_update on public.profiles for update to authenticated using(id=(select auth.uid())) with check(id=(select auth.uid()));
create policy request_read on public.share_requests for select to authenticated using(sender_id=(select auth.uid()) or receiver_id=(select auth.uid()));
create policy share_read on public.location_shares for select to authenticated using(owner_id=(select auth.uid()) or viewer_id=(select auth.uid()));
-- Keep status observable after stop/revocation so Realtime can deliver invalidations.
create policy status_read on public.sharing_status for select to authenticated using(user_id=(select auth.uid()) or exists(
 select 1 from public.location_shares s where s.owner_id=user_id and s.viewer_id=(select auth.uid())
));
create policy location_read on public.latest_locations for select to authenticated using(
 user_id=(select auth.uid()) or (
 recorded_at>=now()-interval '2 hours' and
 exists(select 1 from public.location_shares s where s.owner_id=user_id and s.viewer_id=(select auth.uid()) and s.enabled) and
 exists(select 1 from public.sharing_status st where st.user_id=latest_locations.user_id and st.is_sharing)
));
-- All mutations except display_name pass through checked RPCs; no direct timestamp forgery.
revoke execute on all functions in schema private from public,anon,authenticated;
revoke execute on function public.lookup_user_by_invite_code(text),public.send_share_request(text),public.respond_to_share_request(uuid,boolean),public.cancel_share_request(uuid),public.set_location_share(uuid,boolean),public.set_sharing(boolean,uuid,bigint),public.publish_location(uuid,double precision,double precision,double precision,timestamptz,double precision,double precision),public.contact_names(),public.server_time() from public,anon;
grant execute on function public.lookup_user_by_invite_code(text),public.send_share_request(text),public.respond_to_share_request(uuid,boolean),public.cancel_share_request(uuid),public.set_location_share(uuid,boolean),public.set_sharing(boolean,uuid,bigint),public.publish_location(uuid,double precision,double precision,double precision,timestamptz,double precision,double precision),public.contact_names(),public.server_time() to authenticated;
do $$ declare t text; begin
 if not exists(select 1 from pg_publication where pubname='supabase_realtime') then create publication supabase_realtime; end if;
 foreach t in array array['latest_locations','location_shares','sharing_status','share_requests'] loop
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=t) then
   execute format('alter publication supabase_realtime add table public.%I',t);
  end if;
 end loop;
end $$;
commit;
-- Cron is optional, and never a prerequisite for RLS expiry.
do $$ begin
 if exists(select 1 from pg_available_extensions where name='pg_cron') then
  create extension if not exists pg_cron;
  perform cron.schedule('whereweare-expiry','*/15 * * * *',$job$delete from public.latest_locations where recorded_at < now()-interval '2 hours';$job$);
 else raise notice 'pg_cron unavailable: configure external cleanup (README).'; end if;
exception when others then raise notice 'Configure cleanup manually: %',sqlerrm;
end $$;
