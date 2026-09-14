begin;
-- Incremental v0.3. Existing codes and memberships remain valid.
create table private.code_policy(singleton boolean primary key default true check(singleton), symbols text not null, code_length int not null check(code_length between 6 and 12));
insert into private.code_policy values(true,'23456789ABCDEFGHJKMNPQRSTUVWXYZ',6);
alter table private.code_policy enable row level security;
create or replace function private.invite_code() returns text language plpgsql set search_path='' as $$
declare alphabet text; n int; result text:=''; b int;
begin
 select symbols,code_length into alphabet,n from private.code_policy where singleton;
 while length(result)<n loop
  b:=get_byte(extensions.gen_random_bytes(1),0);
  if b<256-(256%length(alphabet)) then result:=result||substr(alphabet,(b%length(alphabet))+1,1); end if;
 end loop;
 return substr(result,1,n/2)||'-'||substr(result,n/2+1);
end $$;
alter table public.profiles drop constraint profiles_invite_code_check;
alter table public.profiles add constraint profiles_invite_code_check check(invite_code ~ '^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{3,6}-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{3,6}$');
create function private.code_key(code text) returns text language sql immutable set search_path='' as $$ select upper(regexp_replace(code,'[\s-]','','g')); $$;
create unique index profiles_code_key on public.profiles(private.code_key(invite_code));
create unique index groups_code_key on public.groups(private.code_key(invite_code));
create function private.consume_lookup() returns boolean language plpgsql set search_path='' as $$
declare used int; caller uuid:=private.require_user();
begin
 insert into private.lookup_limits values(caller,clock_timestamp(),1) on conflict(user_id) do update set
 attempts=case when lookup_limits.window_start<clock_timestamp()-interval '1 hour' then 1 else lookup_limits.attempts+1 end,
 window_start=case when lookup_limits.window_start<clock_timestamp()-interval '1 hour' then clock_timestamp() else lookup_limits.window_start end returning attempts into used;
 return used<=60;
end $$;
create or replace function private.lookup(code text) returns uuid language plpgsql set search_path='' as $$
declare target uuid; caller uuid:=private.require_user();
begin
 if not private.consume_lookup() then return null; end if;
 select id into target from public.profiles where private.code_key(invite_code)=private.code_key(code) and id<>caller and not deleting;
 return target;
end $$;
alter table public.profiles add column update_interval_seconds int not null default 60 check(update_interval_seconds in(5,30,60,300,600,1800,3600));
alter table public.group_members add column sharing_enabled boolean not null default true;
create function public.set_update_interval(seconds int) returns void language plpgsql security definer set search_path='' as $$
begin update public.profiles set update_interval_seconds=seconds where id=private.require_user(); end $$;
create function private.group_visible(owner_id uuid,viewer_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.group_members a join public.group_members b using(group_id) where a.user_id=owner_id and b.user_id=viewer_id and a.sharing_enabled);
$$;
create or replace function public.can_read_location(target uuid,fix_at timestamptz,fix_session uuid) returns boolean language sql stable security definer set search_path='' as $$
 select target=auth.uid() or (auth.uid() is not null and exists(select 1 from public.sharing_status s join public.profiles p on p.id=s.user_id
 where s.user_id=target and s.is_sharing and s.session_id=fix_session and not p.deleting and fix_at>=now()-make_interval(secs=>p.visibility_seconds)
 and (private.group_visible(target,auth.uid()) or exists(select 1 from public.location_shares d where d.owner_id=target and d.viewer_id=auth.uid() and d.enabled))));
$$;
create function public.contact_profiles_v03() returns table(user_id uuid,display_name text,avatar_path text,visibility_seconds int,common_group boolean,can_view boolean,update_interval_seconds int)
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return query select p.id,p.display_name,p.avatar_path,p.visibility_seconds,private.common_group(caller,p.id),
 (p.id=caller or private.group_visible(p.id,caller) or exists(select 1 from public.location_shares s where s.owner_id=p.id and s.viewer_id=caller and s.enabled)),p.update_interval_seconds
 from public.profiles p where private.related(caller,p.id) and not p.deleting; end $$;
create function public.set_group_sharing(gid uuid,enabled boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 update public.group_members set sharing_enabled=enabled where group_id=gid and user_id=caller;
 if not found then raise exception 'not_authorized'; end if;
 perform private.notify_users(array(select user_id from public.group_members where group_id=gid));
end $$;
create function public.rename_group(gid uuid,group_name text) returns void language plpgsql security definer set search_path='' as $$
begin
 update public.groups set name=btrim(group_name) where id=gid and creator_id=private.require_user();
 if not found then raise exception 'not_authorized'; end if;
 perform private.notify_users(array(select user_id from public.group_members where group_id=gid));
end $$;
create or replace function public.create_group(group_name text,group_emoji text default '📍') returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); gid uuid;
begin
 loop begin
 insert into public.groups(name,emoji,creator_id,invite_code) values(btrim(group_name),group_emoji,caller,private.invite_code()) returning id into gid; exit;
 exception when unique_violation then null; end; end loop;
 insert into public.group_members(group_id,user_id) values(gid,caller); return gid;
end $$;

create table public.group_requests(
 id uuid primary key default gen_random_uuid(),group_id uuid not null references public.groups(id) on delete cascade,
 user_id uuid not null references public.profiles(id) on delete cascade,inviter_id uuid references public.profiles(id) on delete cascade,
 kind text not null check(kind in('join','invite')),status text not null default 'pending' check(status in('pending','accepted','rejected','cancelled')),
 created_at timestamptz not null default now(),responded_at timestamptz
);
create unique index group_request_pending on public.group_requests(group_id,user_id) where status='pending';
alter table public.group_requests enable row level security;
grant select on public.group_requests to authenticated;
create policy group_request_read on public.group_requests for select to authenticated using(user_id=auth.uid() or exists(select 1 from public.groups g where g.id=group_id and g.creator_id=auth.uid()));
create function private.request_group(gid uuid,target uuid,inviter uuid) returns uuid language plpgsql set search_path='' as $$
declare result uuid;
begin
 perform 1 from public.groups where id=gid for update;
 if exists(select 1 from public.group_members where group_id=gid and user_id=target) then return null; end if;
 select id into result from public.group_requests where group_id=gid and user_id=target and status='pending';
 if result is null then
 insert into public.group_requests(group_id,user_id,inviter_id,kind) values(gid,target,inviter,case when inviter is null then 'join' else 'invite' end) returning id into result;
 end if;
 perform private.notify_users(array[target,(select creator_id from public.groups where id=gid)]); return result;
end $$;
create or replace function public.join_group(code text) returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); gid uuid;
begin
 if not private.consume_lookup() then return null; end if;
 select id into gid from public.groups where private.code_key(invite_code)=private.code_key(code);
 if gid is null then return null; end if;
 perform private.request_group(gid,caller,null); return gid;
end $$;
create function public.invite_group_member(gid uuid,person uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if not exists(select 1 from public.groups where id=gid and creator_id=caller) or person=caller or not private.related(caller,person) then raise exception 'not_authorized'; end if;
 perform private.request_group(gid,person,caller);
end $$;
create function public.respond_group_request(request_id uuid,accept boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.group_requests; owner_id uuid;
begin
 select creator_id into owner_id from public.groups where id=(select group_id from public.group_requests where id=request_id) for update;
 select * into r from public.group_requests where id=request_id for update;
 if r.id is null or (r.kind='join' and caller is distinct from owner_id) or (r.kind='invite' and caller<>r.user_id) then raise exception 'not_authorized'; end if;
 if r.status<>'pending' then return; end if;
 update public.group_requests set status=case when accept then 'accepted' else 'rejected' end,responded_at=now() where id=request_id;
 if accept then insert into public.group_members(group_id,user_id) values(r.group_id,r.user_id) on conflict do nothing; end if;
 perform private.notify_users(array[r.user_id,owner_id]);
end $$;
create function public.group_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(jsonb_build_object('id',r.id,'group_id',r.group_id,'group_name',g.name,'user_id',r.user_id,'name',p.display_name,'kind',r.kind,'status',r.status,'can_respond',case when r.kind='invite' then caller=r.user_id else caller=g.creator_id end))
 from public.group_requests r join public.groups g on g.id=r.group_id join public.profiles p on p.id=r.user_id where r.user_id=caller or g.creator_id=caller),'[]'); end $$;

create table public.meeting_points(
 id uuid primary key,creator_id uuid not null references public.profiles(id) on delete cascade,
 latitude double precision not null check(latitude between -90 and 90),longitude double precision not null check(longitude between -180 and 180),
 active boolean not null default true,created_at timestamptz not null default now(),removed_at timestamptz
);
create unique index meeting_one_active on public.meeting_points(creator_id) where active;
create table public.meeting_recipients(meeting_id uuid not null references public.meeting_points(id) on delete cascade,user_id uuid not null references public.profiles(id) on delete cascade,primary key(meeting_id,user_id));
create function public.can_read_meeting(mid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and (exists(select 1 from public.meeting_points m where m.id=mid and m.creator_id=auth.uid()) or exists(select 1 from public.meeting_recipients r where r.meeting_id=mid and r.user_id=auth.uid()));
$$;
alter table public.meeting_points enable row level security; alter table public.meeting_recipients enable row level security;
grant select on public.meeting_points,public.meeting_recipients to authenticated;
create policy meeting_read on public.meeting_points for select to authenticated using(public.can_read_meeting(id));
create policy recipients_read on public.meeting_recipients for select to authenticated using(public.can_read_meeting(meeting_id) and (user_id=auth.uid() or public.can_read_profile(user_id)));
create table private.push_outbox(id uuid primary key default gen_random_uuid(),recipient uuid not null references public.profiles(id) on delete cascade,meeting_id uuid not null references public.meeting_points(id) on delete cascade,kind text not null check(kind in('created','removed')),created_at timestamptz not null default now(),sent_at timestamptz,unique(recipient,meeting_id,kind));
alter table private.push_outbox enable row level security;
create function private.meeting_notice(mid uuid,event_kind text) returns void language plpgsql set search_path='' as $$
begin
 perform private.notify_users(array(select user_id from public.meeting_recipients where meeting_id=mid));
 insert into private.push_outbox(recipient,meeting_id,kind) select user_id,mid,event_kind from public.meeting_recipients where meeting_id=mid and user_id<>(select creator_id from public.meeting_points where id=mid) on conflict do nothing;
end $$;
create function public.create_meeting(mid uuid,lat double precision,lon double precision,all_people boolean default false,people uuid[] default '{}',group_ids uuid[] default '{}') returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); old_id uuid; targets uuid[];
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from public.meeting_points where id=mid and creator_id=caller) then return mid; end if;
 if cardinality(people)>200 or cardinality(group_ids)>50 then raise exception 'invalid_form'; end if;
 if exists(select 1 from unnest(people) u where not private.related(caller,u)) or exists(select 1 from unnest(group_ids) gid where not public.is_group_member(gid)) then raise exception 'not_authorized'; end if;
 select array_agg(distinct uid) into targets from (
 select caller uid union select unnest(people) union select user_id from public.group_members where group_id=any(group_ids)
 union select case when owner_id=caller then viewer_id else owner_id end from public.location_shares where all_people and (owner_id=caller or viewer_id=caller)) q;
 if cardinality(targets)>500 then raise exception 'invalid_form'; end if;
 for old_id in select id from public.meeting_points where creator_id=caller and active loop
 update public.meeting_points set active=false,removed_at=now() where id=old_id; perform private.meeting_notice(old_id,'removed'); end loop;
 insert into public.meeting_points(id,creator_id,latitude,longitude) values(mid,caller,lat,lon);
 insert into public.meeting_recipients select mid,unnest(targets); perform private.meeting_notice(mid,'created'); return mid;
end $$;
create function public.remove_meeting(mid uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 update public.meeting_points set active=false,removed_at=now() where id=mid and creator_id=private.require_user() and active;
 if found then perform private.meeting_notice(mid,'removed'); end if;
end $$;
create function public.meeting_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
begin perform private.require_user(); return coalesce((select jsonb_agg(to_jsonb(m)||jsonb_build_object('creator_name',p.display_name,'recipients',coalesce((select jsonb_agg(r.user_id) from public.meeting_recipients r where r.meeting_id=m.id and (r.user_id=auth.uid() or public.can_read_profile(r.user_id))),'[]')))
 from public.meeting_points m join public.profiles p on p.id=m.creator_id where public.can_read_meeting(m.id) and (m.active or m.removed_at>now()-interval '1 day')),'[]'); end $$;

create table private.invite_links(token_hash text primary key,kind text not null check(kind in('person','group')),owner_id uuid not null references public.profiles(id) on delete cascade,group_id uuid references public.groups(id) on delete cascade,expires_at timestamptz not null default now()+interval '7 days');
alter table private.invite_links enable row level security;
create function public.create_invite_link(kind text,gid uuid default null) returns text language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); token text:=encode(extensions.gen_random_bytes(32),'hex');
begin
 if kind not in('person','group') or (kind='group' and not exists(select 1 from public.groups where id=gid and creator_id=caller)) then raise exception 'not_authorized'; end if;
 insert into private.invite_links(token_hash,kind,owner_id,group_id) values(encode(extensions.digest(token,'sha256'),'hex'),kind,caller,case when kind='group' then gid end); return token;
end $$;
create function public.resolve_invite_link(token text,confirm boolean default false) returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); link private.invite_links; rid uuid; label text;
begin
 if not private.consume_lookup() then return null; end if;
 select * into link from private.invite_links where token_hash=encode(extensions.digest(token,'sha256'),'hex') and expires_at>now();
 if link.token_hash is null or link.owner_id=caller then return null; end if;
 if link.kind='group' then
 select name into label from public.groups where id=link.group_id;
 if confirm then perform private.request_group(link.group_id,caller,null); end if;
 else
 select display_name into label from public.profiles where id=link.owner_id;
 if confirm then
 perform private.pair_lock(caller,link.owner_id);
 if not exists(select 1 from public.location_shares where owner_id=caller and viewer_id=link.owner_id) then
 select id into rid from public.share_requests where least(sender_id,receiver_id)=least(caller,link.owner_id) and greatest(sender_id,receiver_id)=greatest(caller,link.owner_id) and status='pending';
 if rid is null then insert into public.share_requests(sender_id,receiver_id) values(caller,link.owner_id); end if; end if; end if;
 end if;
 return jsonb_build_object('kind',link.kind,'name',label);
end $$;

-- Remote defaults are not security settings; explicit device preferences win.
alter table private.app_bootstrap add column defaults jsonb not null default '{"gps_interval_seconds":60,"high_accuracy":true,"theme":"default","avatar_scale":1.0,"stale_grace_seconds":180}',add column features jsonb not null default '{"meeting_points":true,"invite_links":false,"client_analytics":false}';
update private.app_bootstrap set latest_version_code=4,latest_version_name='0.3',minimum_supported_version_code=4;
create or replace function public.app_bootstrap() returns jsonb language sql stable security definer set search_path='' as $$ select (to_jsonb(b)-'singleton')||jsonb_build_object('api_version',3) from private.app_bootstrap b where singleton; $$;
create function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
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
 'group_requests',public.group_inbox(),'meetings',public.meeting_inbox(),'server_time',clock_timestamp()); end $$;
revoke execute on function public.app_metadata() from public,anon;
grant execute on function public.app_metadata() to authenticated;
create table public.device_tokens(user_id uuid not null references public.profiles(id) on delete cascade,token text not null primary key check(length(token) between 20 and 4096),language text not null check(language in('it','en')),updated_at timestamptz not null default now());
alter table public.device_tokens enable row level security;
create function public.register_device(device_token text,lang text) returns void language plpgsql security definer set search_path='' as $$
begin insert into public.device_tokens values(private.require_user(),device_token,lang,now()) on conflict(token) do update set user_id=excluded.user_id,language=excluded.language,updated_at=now(); end $$;
create function public.unregister_device(device_token text) returns void language sql security definer set search_path='' as $$ delete from public.device_tokens where token=device_token and user_id=auth.uid(); $$;
create table private.client_events(id bigint generated always as identity primary key,user_id uuid references public.profiles(id) on delete cascade,event text not null check(event in('preferences','meeting_created','client_error')),app_version text not null,properties jsonb not null,created_at timestamptz not null default now());
alter table private.client_events enable row level security;
create function public.record_client_event(event_name text,version text,properties jsonb default '{}') returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); safe jsonb;
begin
 if not coalesce((select (features->>'client_analytics')::boolean from private.app_bootstrap where singleton),false) then return; end if;
 if length(version)>16 or event_name not in('preferences','meeting_created','client_error') then raise exception 'invalid_form'; end if;
 if (select count(*) from private.client_events where user_id=caller and created_at>now()-interval '1 hour')>=60 then return; end if;
 select coalesce(jsonb_object_agg(key,value),'{}') into safe from jsonb_each(properties) where (key='theme' and value #>> '{}' in('default','ocean','sunset','lavender','graphite','dark'))
 or (key='avatar_scale' and value #>> '{}' in('0.75','1.0','1.25','1.5'))
 or (key='language' and value #>> '{}' in('system','it','en'))
 or (key='error_code' and value #>> '{}' in('network','permission','location','unknown'));
 insert into private.client_events(user_id,event,app_version,properties) values(caller,event_name,version,safe);
end $$;
create table private.daily_usage(day date primary key,position_updates bigint not null default 0);
alter table private.daily_usage enable row level security;
create function private.count_position_update() returns trigger language plpgsql security definer set search_path='' as $$
begin insert into private.daily_usage(day,position_updates) values((now() at time zone 'UTC')::date,1)
 on conflict(day) do update set position_updates=private.daily_usage.position_updates+1; return new; end $$;
create trigger count_position_update after insert or update of recorded_at on public.latest_locations for each row execute function private.count_position_update();
create view private.usage_summary as select
 (select count(*) from public.profiles) users,(select count(*) from public.sharing_status where is_sharing and updated_at>now()-interval '1 day') sharing_users,
 (select count(*) from public.groups) groups,(select count(*) from public.group_members) memberships,(select count(*) from public.location_shares) relations,
 (select count(*) from public.share_requests where status='pending') person_requests,(select count(*) from public.group_requests where status='pending') group_requests,
 (select count(*) from public.meeting_points where active) meeting_points,(select count(*) from public.latest_locations where updated_at>now()-interval '1 day') users_with_updates,
 (select coalesce(sum(position_updates),0) from private.daily_usage) position_updates,
 (select count(*) from private.push_outbox where sent_at is null) pending_pushes;
alter table private.push_outbox add column leased_until timestamptz,add column attempts int not null default 0;
create function public.claim_push_batch() returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 with batch as (select id from private.push_outbox where sent_at is null and attempts<8 and (leased_until is null or leased_until<now()) order by created_at for update skip locked limit 50),
 leased as (update private.push_outbox q set leased_until=now()+interval '5 minutes',attempts=attempts+1 from batch where q.id=batch.id returning q.*)
 select coalesce(jsonb_agg(jsonb_build_object('id',q.id,'recipient',q.recipient,'meeting_id',q.meeting_id,'kind',q.kind,'creator_name',p.display_name,
 'tokens',coalesce((select jsonb_agg(t.token) from public.device_tokens t where t.user_id=q.recipient),'[]'))),'[]') into result
 from leased q join public.meeting_points m on m.id=q.meeting_id join public.profiles p on p.id=m.creator_id;
 return result;
end $$;
create function public.complete_push(job uuid) returns void language sql security definer set search_path='' as $$ update private.push_outbox set sent_at=now(),leased_until=null where id=job; $$;
revoke execute on function public.claim_push_batch(),public.complete_push(uuid) from public,anon,authenticated;
grant execute on function public.claim_push_batch(),public.complete_push(uuid) to service_role;
grant select,delete on public.device_tokens to service_role;
-- Payload-free account_events already provides RLS-filtered realtime invalidation.
revoke all on all tables in schema private from public,anon,authenticated;
revoke execute on all functions in schema private from public,anon,authenticated;
do $$ declare f regprocedure; begin
 for f in select p.oid::regprocedure from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.proname in('set_update_interval','contact_profiles_v03','set_group_sharing','rename_group','group_inbox','invite_group_member','respond_group_request','can_read_meeting','create_meeting','remove_meeting','meeting_inbox','create_invite_link','resolve_invite_link','register_device','unregister_device','record_client_event') loop
 execute format('revoke execute on function %s from public,anon',f); execute format('grant execute on function %s to authenticated',f); end loop;
end $$;
commit;
