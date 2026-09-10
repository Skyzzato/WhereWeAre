begin;
-- Incremental migration; the owner's last fix is retained until account deletion.
alter table public.profiles add column avatar_path text,
 add column deleting boolean not null default false,
 add column avatar_updated_at timestamptz,
 add column visibility_seconds integer not null default 86400
 check (visibility_seconds in (600,1800,3600,7200,14400,43200,86400));
alter table public.share_requests add column sender_hidden boolean not null default false,
 add column receiver_hidden boolean not null default false;
alter table public.latest_locations add column share_session uuid;
update public.latest_locations l set share_session=s.session_id from public.sharing_status s where s.user_id=l.user_id;

create table public.groups (
 id uuid primary key default gen_random_uuid(),
 name text not null check(char_length(btrim(name)) between 1 and 24),
 emoji text not null default '📍' check(char_length(emoji) between 1 and 12),
 invite_code text not null unique default encode(extensions.gen_random_bytes(12),'hex'),
 creator_id uuid not null references public.profiles(id) on delete cascade,
 created_at timestamptz not null default now()
);
create table public.group_members (
 group_id uuid not null references public.groups(id) on delete cascade,
 user_id uuid not null references public.profiles(id) on delete cascade,
 joined_at timestamptz not null default now(), primary key(group_id,user_id)
);
create index group_members_user on public.group_members(user_id,group_id);
-- Payload-free per-user invalidation survives deletion/revocation of its cause.
create table public.account_events (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 revision bigint not null default 0
);
insert into public.account_events(user_id) select id from public.profiles;
create function private.notify_users(ids uuid[]) returns void language sql security definer set search_path='' as $$
 insert into public.account_events(user_id,revision)
 select distinct p.id,1 from public.profiles p where p.id=any(ids)
 on conflict(user_id) do update set revision=account_events.revision+1;
$$;
create function private.common_group(a uuid,b uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.group_members x join public.group_members y using(group_id) where x.user_id=a and y.user_id=b);
$$;
create function private.related(a uuid,b uuid) returns boolean language sql stable security definer set search_path='' as $$
 select a=b or private.common_group(a,b) or exists(select 1 from public.location_shares s where
 (s.owner_id=a and s.viewer_id=b) or (s.owner_id=b and s.viewer_id=a));
$$;
create function private.changed() returns trigger language plpgsql security definer set search_path='' as $$
declare ids uuid[]; gid uuid; uid uuid;
begin
 if tg_table_name='group_members' then
  gid:=case when tg_op='DELETE' then old.group_id else new.group_id end;
  uid:=case when tg_op='DELETE' then old.user_id else new.user_id end;
  select array_agg(user_id)||array[uid] into ids from public.group_members where group_id=gid;
 elsif tg_table_name='location_shares' then
  if tg_op='DELETE' then ids:=array[old.owner_id,old.viewer_id]; else ids:=array[new.owner_id,new.viewer_id]; end if;
 else
  uid:=case when tg_op='DELETE' then old.id else new.id end;
  select array_agg(p.id) into ids from public.profiles p where private.related(uid,p.id);
 end if;
 perform private.notify_users(ids);
 if tg_op='DELETE' then return old; else return new; end if;
end $$;
create trigger members_notify before insert or delete on public.group_members for each row execute function private.changed();
create trigger connections_notify before insert or update or delete on public.location_shares for each row execute function private.changed();
create trigger profile_notify after insert or update on public.profiles for each row execute function private.changed();

create function public.is_group_member(gid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.group_members where group_id=gid and user_id=auth.uid());
$$;
create function public.can_read_profile(target uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and private.related(auth.uid(),target);
$$;
create function public.can_read_location(target uuid,fix_at timestamptz,fix_session uuid) returns boolean language sql stable security definer set search_path='' as $$
 select target=auth.uid() or (auth.uid() is not null and exists(
 select 1 from public.sharing_status s join public.profiles p on p.id=s.user_id
 where s.user_id=target and s.is_sharing and s.session_id=fix_session
 and fix_at>=now()-make_interval(secs=>p.visibility_seconds)
 and (private.common_group(target,auth.uid()) or exists(select 1 from public.location_shares d
 where d.owner_id=target and d.viewer_id=auth.uid() and d.enabled))));
$$;
drop policy location_read on public.latest_locations;
create policy location_read on public.latest_locations for select to authenticated
 using(public.can_read_location(user_id,recorded_at,share_session));
drop policy status_read on public.sharing_status;
create policy status_read on public.sharing_status for select to authenticated using(public.can_read_profile(user_id));
drop policy request_read on public.share_requests;
create policy request_read on public.share_requests for select to authenticated using(
 (sender_id=auth.uid() and not sender_hidden) or (receiver_id=auth.uid() and not receiver_hidden));
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.account_events enable row level security;
revoke all on public.groups,public.group_members,public.account_events from public,anon,authenticated;
grant select on public.groups,public.group_members,public.account_events to authenticated;
create policy groups_read on public.groups for select to authenticated using(public.is_group_member(id));
create policy members_read on public.group_members for select to authenticated using(public.is_group_member(group_id));
create policy events_read on public.account_events for select to authenticated using(user_id=auth.uid());

create or replace function public.publish_location(session uuid, lat double precision, lon double precision,
 acc double precision, fix_at timestamptz, velocity double precision default null, heading double precision default null)
 returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); status public.sharing_status; received timestamptz:=clock_timestamp();
begin
 select * into status from public.sharing_status where user_id=caller for update;
 if not status.is_sharing or status.session_id is distinct from session then raise exception 'sharing_stopped'; end if;
 if fix_at is null or fix_at>received+interval '30 seconds' or fix_at<received-interval '2 hours' then raise exception 'invalid_fix_time'; end if;
 insert into public.latest_locations(user_id,latitude,longitude,accuracy,speed,bearing,recorded_at,device_recorded_at,server_received_at,updated_at,share_session)
 values(caller,lat,lon,acc,velocity,heading,least(fix_at,received),fix_at,received,received,session)
 on conflict(user_id) do update set latitude=excluded.latitude,longitude=excluded.longitude,accuracy=excluded.accuracy,
 speed=excluded.speed,bearing=excluded.bearing,recorded_at=excluded.recorded_at,device_recorded_at=excluded.device_recorded_at,
 server_received_at=excluded.server_received_at,updated_at=excluded.updated_at,share_session=excluded.share_session
 where excluded.recorded_at>latest_locations.recorded_at;
end $$;
create function public.set_visibility(seconds integer) returns void language plpgsql security definer set search_path='' as $$
begin update public.profiles set visibility_seconds=seconds where id=private.require_user(); end $$;
create function public.remove_connection(other_user_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform private.pair_lock(caller,other_user_id);
 delete from public.location_shares where (owner_id=caller and viewer_id=other_user_id) or (owner_id=other_user_id and viewer_id=caller);
end $$;
create function public.dismiss_request(request_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.share_requests;
begin
 select * into r from public.share_requests where id=request_id;
 if r.id is null or caller not in(r.sender_id,r.receiver_id) then raise exception 'not_authorized'; end if;
 perform private.pair_lock(r.sender_id,r.receiver_id);
 select * into r from public.share_requests where id=request_id for update;
 update public.share_requests set
 status=case when r.status<>'pending' then r.status when caller=r.sender_id then 'cancelled'::public.request_status else 'rejected'::public.request_status end,
 responded_at=case when r.status='pending' then now() else responded_at end,
 sender_hidden=sender_hidden or caller=sender_id,receiver_hidden=receiver_hidden or caller=receiver_id where id=request_id;
end $$;

create function public.create_group(group_name text,group_emoji text default '📍') returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); gid uuid;
begin
 insert into public.groups(name,emoji,creator_id) values(btrim(group_name),group_emoji,caller) returning id into gid;
 insert into public.group_members values(gid,caller,now()); return gid;
end $$;
create function public.join_group(code text) returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); gid uuid; attempts integer;
begin
 -- Share the persistent per-account lookup budget; failure must not roll it back.
 perform private.lookup('invalid');
 select l.attempts into attempts from private.lookup_limits l where l.user_id=caller;
 if attempts>60 then return null; end if;
 select id into gid from public.groups where invite_code=lower(regexp_replace(code,'[\s-]','','g')) for update;
 if gid is null then return null; end if;
 insert into public.group_members values(gid,caller,now()) on conflict do nothing; return gid;
end $$;
create function public.remove_group_member(gid uuid,member uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); creator uuid;
begin
 select creator_id into creator from public.groups where id=gid for update;
 if creator is null or (caller<>member and caller<>creator) then raise exception 'not_authorized'; end if;
 if member=creator then raise exception 'creator_must_delete_group'; end if;
 delete from public.group_members where group_id=gid and user_id=member;
end $$;
create function public.delete_group(gid uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); creator uuid;
begin
 select creator_id into creator from public.groups where id=gid for update;
 if creator is distinct from caller then raise exception 'not_authorized'; end if;
 perform private.notify_users(array(select user_id from public.group_members where group_id=gid));
 delete from public.groups where id=gid;
end $$;

-- Profiles stay owner-only. This RPC exposes only bounded, authorized metadata.
create function public.contact_profiles() returns table(user_id uuid,display_name text,avatar_path text,avatar_updated_at timestamptz,visibility_seconds integer,common_group boolean)
 language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return query select p.id,p.display_name,p.avatar_path,p.avatar_updated_at,p.visibility_seconds,private.common_group(caller,p.id)
 from public.profiles p where private.related(caller,p.id); end $$;
create or replace function public.contact_names() returns table(user_id uuid,display_name text)
 language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return query select p.id,p.display_name from public.profiles p where private.related(caller,p.id) or exists(
 select 1 from public.share_requests r where (r.sender_id=caller and not r.sender_hidden and r.receiver_id=p.id)
 or (r.receiver_id=caller and not r.receiver_hidden and r.sender_id=p.id)); end $$;

-- Private Storage: each version has a new UUID filename. No public/signed URLs.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 values('avatars','avatars',false,1048576,array['image/webp','image/jpeg','image/png']);
create function public.owns_avatar_path(path text) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.profiles where id=auth.uid() and not deleting)
 and path ~ ('^'||auth.uid()::text||'/[0-9a-f-]{36}\.webp$');
$$;
-- Serialize Storage writes with the deletion tombstone. A queued upload must
-- recheck the tombstone after the lock; a deleted JWT cannot upload orphan files.
create function private.avatar_write_guard() returns trigger language plpgsql security definer set search_path='' as $$
declare available boolean;
begin
 if new.bucket_id='avatars' then
  select not deleting into available from public.profiles where id=auth.uid() for share;
  if available is distinct from true then raise exception 'not_authorized'; end if;
 end if;
 return new;
end $$;
create trigger avatars_guard before insert or update on storage.objects for each row execute function private.avatar_write_guard();
create or replace function private.require_user() returns uuid language plpgsql stable set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'not_authenticated'; end if;
 if not exists(select 1 from public.profiles where id=auth.uid() and not deleting) then raise exception 'not_authorized'; end if;
 return auth.uid();
end $$;
create policy avatar_read on storage.objects for select to authenticated using(bucket_id='avatars' and
 (public.owns_avatar_path(name) or exists(select 1 from public.contact_profiles() p where p.avatar_path=name)));
create policy avatar_insert on storage.objects for insert to authenticated with check(bucket_id='avatars' and public.owns_avatar_path(name));
create policy avatar_update on storage.objects for update to authenticated using(bucket_id='avatars' and public.owns_avatar_path(name)) with check(bucket_id='avatars' and public.owns_avatar_path(name));
create policy avatar_delete on storage.objects for delete to authenticated using(bucket_id='avatars' and public.owns_avatar_path(name));
create function public.set_avatar(path text) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if path is not null and (not public.owns_avatar_path(path) or not exists(select 1 from storage.objects where bucket_id='avatars' and name=path)) then raise exception 'not_authorized'; end if;
 update public.profiles set avatar_path=path,avatar_updated_at=clock_timestamp() where id=caller;
end $$;

create table private.app_bootstrap (
 singleton boolean primary key default true check(singleton),
 latest_version_code integer not null,latest_version_name text not null,
 minimum_supported_version_code integer not null check(minimum_supported_version_code>0),
 maintenance_mode boolean not null default false,maintenance_message text,
 check(latest_version_code>=minimum_supported_version_code)
);
alter table private.app_bootstrap enable row level security;
insert into private.app_bootstrap values(true,2,'0.2',2,false,null);
create function public.app_bootstrap() returns jsonb language sql stable security definer set search_path='' as $$
 select jsonb_build_object('latest_version_code',latest_version_code,'latest_version_name',latest_version_name,
 'minimum_supported_version_code',minimum_supported_version_code,'maintenance_mode',maintenance_mode,
 'maintenance_message',maintenance_message) from private.app_bootstrap where singleton;
$$;
revoke all on private.app_bootstrap from public,anon,authenticated;
revoke execute on all functions in schema private from public,anon,authenticated;
do $$ declare f regprocedure; begin
 for f in select p.oid::regprocedure from pg_proc p join pg_namespace n on p.pronamespace=n.oid where n.nspname='public'
 and p.proname in ('is_group_member','can_read_profile','can_read_location','set_visibility','remove_connection','dismiss_request',
 'create_group','join_group','remove_group_member','delete_group','contact_profiles','owns_avatar_path','set_avatar','app_bootstrap') loop
 execute format('revoke all on function %s from public,anon,authenticated',f);
 execute format('grant execute on function %s to authenticated',f);
 end loop;
end $$;
grant execute on function public.app_bootstrap() to anon;
alter publication supabase_realtime add table public.account_events;
-- Stop ONLY this application's expiry job; do not touch other scheduled jobs.
do $$ begin
 if exists(select 1 from pg_namespace where nspname='cron') then
  perform cron.unschedule(jobid) from cron.job where jobname='whereweare-expiry';
 end if;
end $$;
commit;
