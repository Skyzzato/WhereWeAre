begin;
-- Reuse existing groups/requests. No memberships or historical requests are deleted.
create function public.edit_group(gid uuid,group_name text,group_emoji text) returns void
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 update public.groups set name=btrim(group_name),emoji=group_emoji where id=gid and creator_id=caller;
 if not found then raise exception 'not_authorized'; end if;
 perform private.notify_users(array(select user_id from public.group_members where group_id=gid)
   ||array(select user_id from public.group_requests where group_id=gid and status='pending'));
end $$;

create function private.group_request_changed() returns trigger
language plpgsql security definer set search_path='' as $$
declare r public.group_requests;
begin
 if tg_op='DELETE' then r:=old; else r:=new; end if;
 perform private.notify_users(array(select user_id from public.group_members where group_id=r.group_id)
   ||array[r.user_id,r.inviter_id]);
 if tg_op='DELETE' then return old; else return new; end if;
end $$;
create trigger group_request_notify after insert or update or delete on public.group_requests
for each row execute function private.group_request_changed();

create function public.cancel_group_invitation(request_id uuid) returns void
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.group_requests; owner_id uuid;
begin
 -- Same lock ordering as acceptance: cancellation can never remove an accepted membership.
 select creator_id into owner_id from public.groups
 where id=(select group_id from public.group_requests where id=request_id) for update;
 select * into r from public.group_requests where id=request_id for update;
 if r.id is null then return; end if;
 if r.kind<>'invite' or (caller is distinct from owner_id and caller is distinct from r.inviter_id)
 then raise exception 'not_authorized'; end if;
 if r.status='pending' then delete from public.group_requests where id=request_id; end if;
end $$;

drop policy group_request_read on public.group_requests;
create policy group_request_read on public.group_requests for select to authenticated using(
 user_id=auth.uid() or exists(select 1 from public.groups g where g.id=group_id and g.creator_id=auth.uid())
 or (kind='invite' and status='pending' and public.is_group_member(group_id)));

create or replace function public.group_inbox() returns jsonb
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(jsonb_build_object(
 'id',r.id,'group_id',r.group_id,'group_name',g.name,'group_emoji',g.emoji,
 'user_id',r.user_id,'name',p.display_name,'kind',r.kind,'status',r.status,
 'can_respond',case when r.kind='invite' then caller=r.user_id else caller=g.creator_id end,
 'can_cancel',r.kind='invite' and r.status='pending' and (caller=g.creator_id or caller=r.inviter_id)))
 from public.group_requests r join public.groups g on g.id=r.group_id join public.profiles p on p.id=r.user_id
 where r.user_id=caller or g.creator_id=caller
 or (r.kind='invite' and r.status='pending' and public.is_group_member(r.group_id))),'[]'); end $$;

revoke all on function public.edit_group(uuid,text,text),public.cancel_group_invitation(uuid) from public,anon;
grant execute on function public.edit_group(uuid,text,text),public.cancel_group_invitation(uuid) to authenticated;
revoke all on function private.group_request_changed() from public,anon,authenticated;
-- This only advertises the update; v0.3 remains supported.
update private.app_bootstrap set latest_version_code=6,latest_version_name='0.31';
commit;
