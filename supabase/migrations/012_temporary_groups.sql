begin;
alter table public.groups add column if not exists expires_at timestamptz;
create or replace function private.group_active(gid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.groups where id=gid and (expires_at is null or expires_at>statement_timestamp()));
$$;
revoke all on function private.group_active(uuid) from public,anon,authenticated;
create or replace function public.group_open(gid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and private.group_active(gid);
$$;
revoke all on function public.group_open(uuid) from public,anon;
grant execute on function public.group_open(uuid) to authenticated;
create or replace function public.is_group_member(gid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select private.group_active(gid) and exists(select 1 from public.group_members where group_id=gid and user_id=auth.uid());
$$;
create or replace function private.common_group(a uuid,b uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.group_members x join public.group_members y using(group_id)
 where x.user_id=a and y.user_id=b and private.group_active(x.group_id));
$$;
create or replace function private.group_visible(owner_id uuid,viewer_id uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from public.group_members a join public.group_members b using(group_id)
 where a.user_id=owner_id and b.user_id=viewer_id and a.sharing_enabled and private.group_active(a.group_id));
$$;
create or replace function private.precision_sources(owner_id uuid,viewer_id uuid)
returns table(kind text,source_id uuid,name text,precision_m int) language sql stable security definer set search_path='' as $$
 select 'person',s.viewer_id,p.display_name,coalesce(s.shared_precision,o.shared_precision)
 from public.location_shares s join public.profiles o on o.id=s.owner_id join public.profiles p on p.id=s.viewer_id
 where s.owner_id=precision_sources.owner_id and s.viewer_id=precision_sources.viewer_id and s.enabled and not p.deleting and not o.deleting
 union all
 select 'group',g.id,g.name,coalesce(a.shared_precision,o.shared_precision)
 from public.group_members a join public.group_members b using(group_id)
 join public.groups g on g.id=a.group_id join public.profiles o on o.id=a.user_id join public.profiles p on p.id=b.user_id
 where a.user_id=owner_id and b.user_id=viewer_id and a.sharing_enabled and not o.deleting and not p.deleting and private.group_active(g.id);
$$;
create or replace function private.checkin_precision(owner_id uuid,viewer_id uuid,groups uuid[],personal boolean,meeting uuid) returns int
language sql stable security definer set search_path='' as $$
 select min(m) from (
  select coalesce((select shared_precision from public.location_shares where owner_id=checkin_precision.owner_id and viewer_id=checkin_precision.viewer_id),p.shared_precision) m
   from public.profiles p where id=owner_id and (personal or exists(select 1 from public.meeting_recipients where meeting_id=meeting and user_id=viewer_id))
  union all select coalesce(a.shared_precision,p.shared_precision) from public.group_members a
   join public.group_members b using(group_id) join public.profiles p on p.id=a.user_id
   where a.user_id=owner_id and b.user_id=viewer_id and a.group_id=any(groups) and private.group_active(a.group_id)
 ) sources;
$$;
drop policy if exists group_request_read on public.group_requests;
create policy group_request_read on public.group_requests for select to authenticated using(public.group_open(group_id) and (
 user_id=auth.uid() or exists(select 1 from public.groups g where g.id=group_id and g.creator_id=auth.uid())
 or (kind='invite' and status='pending' and public.is_group_member(group_id))));
create or replace function private.request_group(gid uuid,target uuid,inviter uuid) returns uuid language plpgsql set search_path='' as $$
declare result uuid;
begin
 perform 1 from public.groups where id=gid for update;
 if not private.group_active(gid) then raise exception 'group_expired'; end if;
 if exists(select 1 from public.group_members where group_id=gid and user_id=target) then return null; end if;
 select id into result from public.group_requests where group_id=gid and user_id=target and status='pending';
 if result is null then
  insert into public.group_requests(group_id,user_id,inviter_id,kind) values(gid,target,inviter,case when inviter is null then 'join' else 'invite' end) returning id into result;
 end if;
 perform private.notify_users(array[target,(select creator_id from public.groups where id=gid)]);return result;
end $$;
create or replace function public.respond_group_request(request_id uuid,accept boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.group_requests; owner_id uuid;
begin
 select creator_id into owner_id from public.groups where id=(select group_id from public.group_requests where id=request_id) for update;
 select * into r from public.group_requests where id=request_id for update;
 if r.id is null or (r.kind='join' and caller is distinct from owner_id) or (r.kind='invite' and caller<>r.user_id) then raise exception 'not_authorized'; end if;
 if not private.group_active(r.group_id) then raise exception 'group_expired'; end if;
 if r.status<>'pending' then return; end if;
 update public.group_requests set status=case when accept then 'accepted' else 'rejected' end,responded_at=now() where id=request_id;
 if accept then insert into public.group_members(group_id,user_id) values(r.group_id,r.user_id) on conflict do nothing; end if;
 perform private.notify_users(array[r.user_id,owner_id]);
end $$;
create or replace function public.group_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(jsonb_build_object(
 'id',r.id,'group_id',r.group_id,'group_name',g.name,'group_emoji',g.emoji,
 'user_id',r.user_id,'name',p.display_name,'kind',r.kind,'status',r.status,
 'can_respond',case when r.kind='invite' then caller=r.user_id else caller=g.creator_id end,
 'can_cancel',r.kind='invite' and r.status='pending' and (caller=g.creator_id or caller=r.inviter_id)))
 from public.group_requests r join public.groups g on g.id=r.group_id join public.profiles p on p.id=r.user_id
 where private.group_active(g.id) and (r.user_id=caller or g.creator_id=caller
 or (r.kind='invite' and r.status='pending' and public.is_group_member(r.group_id)))),'[]'); end $$;
create or replace function public.create_group_timed(group_name text,group_emoji text,end_at timestamptz) returns uuid
language plpgsql security definer set search_path='' as $$
declare gid uuid;
begin
 if end_at is not null and end_at<=statement_timestamp() then raise exception 'invalid_expiry'; end if;
 gid:=public.create_group(group_name,group_emoji);
 update public.groups set expires_at=end_at where id=gid;
 return gid;
end $$;
create or replace function public.edit_group_timed(gid uuid,group_name text,group_emoji text,end_at timestamptz) returns void
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform 1 from public.groups where id=gid and creator_id=caller for update;
 if not found then raise exception 'not_authorized'; end if;
 if not private.group_active(gid) then raise exception 'group_expired'; end if;
 if end_at is not null and end_at<=statement_timestamp() then raise exception 'invalid_expiry'; end if;
 perform public.edit_group(gid,group_name,group_emoji);
 update public.groups set expires_at=end_at where id=gid and creator_id=caller;
 perform private.notify_users(array(select user_id from public.group_members where group_id=gid));
end $$;
revoke all on function public.create_group_timed(text,text,timestamptz),public.edit_group_timed(uuid,text,text,timestamptz) from public,anon;
grant execute on function public.create_group_timed(text,text,timestamptz),public.edit_group_timed(uuid,text,text,timestamptz) to authenticated;
do $$ begin
 if to_regprocedure('private.metadata_before_temporary_groups()') is null then
  alter function public.app_metadata() rename to metadata_before_temporary_groups;
  alter function public.metadata_before_temporary_groups() set schema private;
 end if;
end $$;
revoke all on function private.metadata_before_temporary_groups() from public,anon,authenticated;
create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
begin perform private.require_user();return private.metadata_before_temporary_groups()||jsonb_build_object('temporary_groups_available',true);end $$;
revoke all on function public.app_metadata() from public,anon;
grant execute on function public.app_metadata() to authenticated;
update private.app_bootstrap set features=features||'{"temporary_groups":true}'::jsonb;
commit;
