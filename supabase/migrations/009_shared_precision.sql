begin;
alter table public.profiles add column if not exists shared_precision int not null default 0 check(shared_precision in (0,250,500,1000));
alter table public.location_shares add column if not exists shared_precision int check(shared_precision in (0,250,500,1000));
alter table public.group_members add column if not exists shared_precision int check(shared_precision in (0,250,500,1000));

-- Persistent owner-specific origin. Nested 250/500/1000 grids prevent combining
-- different precision grants into independent, randomly shifted samples.
create table if not exists private.location_grid (
 user_id uuid primary key references public.profiles(id) on delete cascade,
 x double precision not null, y double precision not null, z double precision not null
);
revoke all on private.location_grid from public,anon,authenticated;
create or replace function private.ensure_location_grid() returns trigger language plpgsql security definer set search_path='' as $$
begin
 insert into private.location_grid values(new.id,random()*1000,random()*1000,random()*1000) on conflict do nothing;
 return new;
end $$;
revoke all on function private.ensure_location_grid() from public,anon,authenticated;
drop trigger if exists location_grid_created on public.profiles;
create trigger location_grid_created after insert on public.profiles for each row execute function private.ensure_location_grid();
insert into private.location_grid select id,random()*1000,random()*1000,random()*1000 from public.profiles on conflict do nothing;

create or replace function private.precision_sources(owner_id uuid,viewer_id uuid)
returns table(kind text,source_id uuid,name text,precision_m int) language sql stable security definer set search_path='' as $$
 select 'person',s.viewer_id,p.display_name,coalesce(s.shared_precision,o.shared_precision)
 from public.location_shares s join public.profiles o on o.id=s.owner_id join public.profiles p on p.id=s.viewer_id
 where s.owner_id=precision_sources.owner_id and s.viewer_id=precision_sources.viewer_id and s.enabled and not p.deleting and not o.deleting
 union all
 select 'group',g.id,g.name,coalesce(a.shared_precision,o.shared_precision)
 from public.group_members a join public.group_members b using(group_id)
 join public.groups g on g.id=a.group_id join public.profiles o on o.id=a.user_id join public.profiles p on p.id=b.user_id
 where a.user_id=owner_id and b.user_id=viewer_id and a.sharing_enabled and not o.deleting and not p.deleting;
$$;
create or replace function private.effective_precision(owner_id uuid,viewer_id uuid) returns int
language sql stable security definer set search_path='' as $$
 select case when owner_id=viewer_id then 0 else (select min(precision_m) from private.precision_sources(owner_id,viewer_id)) end;
$$;
revoke all on function private.precision_sources(uuid,uuid),private.effective_precision(uuid,uuid) from public,anon,authenticated;

create or replace function public.can_read_precise_location(target uuid,fix_at timestamptz,fix_session uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select public.can_read_location(target,fix_at,fix_session) and private.effective_precision(target,auth.uid())=0;
$$;
revoke all on function public.can_read_precise_location(uuid,timestamptz,uuid) from public,anon;
grant execute on function public.can_read_precise_location(uuid,timestamptz,uuid) to authenticated;
drop policy if exists location_read on public.latest_locations;
create policy location_read on public.latest_locations for select to authenticated
 using(public.can_read_precise_location(user_id,recorded_at,share_session));

create or replace function private.approximate_location(owner_id uuid,lat double precision,lon double precision,radius int)
returns table(latitude double precision,longitude double precision) language sql stable security definer set search_path='' as $$
 with scale as (select radius*.9/sqrt(3.0) side),
 quantized as (select
  (floor((6371000*cos(radians(lat))*cos(radians(lon))+g.x)/s.side)+.5)*s.side-g.x x,
  (floor((6371000*cos(radians(lat))*sin(radians(lon))+g.y)/s.side)+.5)*s.side-g.y y,
  (floor((6371000*sin(radians(lat))+g.z)/s.side)+.5)*s.side-g.z z
 from private.location_grid g cross join scale s where g.user_id=owner_id)
 select degrees(atan2(z,sqrt(x*x+y*y))),degrees(atan2(y,x)) from quantized;
$$;
revoke all on function private.approximate_location(uuid,double precision,double precision,int) from public,anon,authenticated;

create or replace function public.visible_locations() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 return coalesce((select jsonb_agg(jsonb_build_object(
  'user_id',l.user_id,'latitude',case when p.m=0 then l.latitude else a.latitude end,
  'longitude',case when p.m=0 then l.longitude else a.longitude end,
  'accuracy',case when p.m=0 then l.accuracy else p.m end,
  'speed',case when p.m=0 then l.speed else null end,'bearing',case when p.m=0 then l.bearing else null end,
  'recorded_at',l.recorded_at,'battery_level',l.battery_level,'location_enabled',l.location_enabled,
  'device_status_at',l.device_status_at,'precision_m',p.m))
 from public.latest_locations l
 cross join lateral (select private.effective_precision(l.user_id,caller) m) p
 left join lateral private.approximate_location(l.user_id,l.latitude,l.longitude,nullif(p.m,0)) a on p.m>0
 where p.m is not null and public.can_read_location(l.user_id,l.recorded_at,l.share_session)),'[]');
end $$;
create or replace function public.location_audience() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 return coalesce((select jsonb_agg(jsonb_build_object('user_id',p.id,'name',p.display_name,'precision_m',s.m,'sources',s.sources))
 from public.profiles p
 cross join lateral (select min(precision_m) m,jsonb_agg(to_jsonb(r)) sources from private.precision_sources(caller,p.id) r) s
 where p.id<>caller and s.m is not null and exists(select 1 from public.latest_locations l
 join public.sharing_status st on st.user_id=l.user_id join public.profiles o on o.id=l.user_id
 where l.user_id=caller and st.is_sharing and st.session_id=l.share_session and not o.deleting
 and l.recorded_at>=now()-make_interval(secs=>o.visibility_seconds))),'[]');
end $$;
create or replace function public.set_shared_precision(scope text,target uuid,value int) returns void
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if value is not null and value not in (0,250,500,1000) then raise exception 'invalid_precision'; end if;
 if scope='default' and target is null and value is not null then
  update public.profiles set shared_precision=value where id=caller;
 elsif scope='person' then
  update public.location_shares set shared_precision=value where owner_id=caller and viewer_id=target;
  if not found then raise exception 'not_authorized'; end if;
 elsif scope='group' then
  update public.group_members set shared_precision=value where user_id=caller and group_id=target;
  if not found then raise exception 'not_authorized'; end if;
 else raise exception 'invalid_precision_scope'; end if;
 perform private.notify_users(array(select p.id from public.profiles p where private.related(caller,p.id)));
end $$;
revoke all on function public.visible_locations(),public.location_audience(),public.set_shared_precision(text,uuid,int) from public,anon;
grant execute on function public.visible_locations(),public.location_audience(),public.set_shared_precision(text,uuid,int) to authenticated;

-- Notify recipients without publishing raw coordinates to approximate viewers.
create or replace function private.location_changed() returns trigger language plpgsql security definer set search_path='' as $$
begin
 perform private.notify_users(array(select candidates.viewer from (
  select viewer_id viewer from public.location_shares where owner_id=new.user_id and enabled
  union select b.user_id from public.group_members a join public.group_members b using(group_id)
   where a.user_id=new.user_id and a.sharing_enabled
 ) candidates where candidates.viewer<>new.user_id and private.effective_precision(new.user_id,candidates.viewer)>0));
 return new;
end $$;
revoke all on function private.location_changed() from public,anon,authenticated;
drop trigger if exists location_precision_notify on public.latest_locations;
create trigger location_precision_notify after insert or update on public.latest_locations for each row execute function private.location_changed();

create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return jsonb_build_object(
 'profile',(select to_jsonb(p) from public.profiles p where id=caller),
 'names',coalesce((select jsonb_agg(n) from public.contact_names() n),'[]'),
 'requests',coalesce((select jsonb_agg(r) from public.share_requests r where (sender_id=caller and not sender_hidden) or (receiver_id=caller and not receiver_hidden)),'[]'),
 'shares',coalesce((select jsonb_agg(s) from public.location_shares s where owner_id=caller or viewer_id=caller),'[]'),
 'statuses',coalesce((select jsonb_agg(s) from public.sharing_status s where private.related(caller,s.user_id)),'[]'),
 'contacts',coalesce((select jsonb_agg(c) from public.contact_profiles_v03() c),'[]'),
 'groups',coalesce((select jsonb_agg(g) from public.groups g where public.is_group_member(g.id)),'[]'),
 'members',coalesce((select jsonb_agg(m) from public.group_members m where public.is_group_member(m.group_id)),'[]'),
 'saved_people',coalesce((select jsonb_agg(person_id) from public.saved_people where user_id=caller),'[]'),
 'shared_precision',true,'group_requests',public.group_inbox(),'meetings',public.meeting_inbox(),'server_time',clock_timestamp()); end $$;
update private.app_bootstrap set features=features||'{"shared_precision":true}'::jsonb;
commit;
