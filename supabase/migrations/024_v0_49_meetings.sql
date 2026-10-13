begin;
-- 023 is an unrelated, untracked local reset. It is NOT a prerequisite.
alter table public.meeting_points add column expires_at timestamptz;
alter table public.meeting_points add column label text not null default 'Punto di ritrovo';
update public.meeting_points set expires_at=case when created_at is null or created_at>clock_timestamp()
 then clock_timestamp() else created_at+interval '1 hour' end;
alter table public.meeting_points alter column expires_at set not null;
create function private.meeting_lifetime() returns trigger language plpgsql set search_path='' as $$
begin
 if tg_op='INSERT' then
  new.created_at:=clock_timestamp();
  new.expires_at:=least(coalesce(new.expires_at,new.created_at+interval '1 hour'),new.created_at+interval '1 hour');
 else
  new.created_at:=old.created_at;
  new.expires_at:=least(coalesce(new.expires_at,old.expires_at),old.expires_at,old.created_at+interval '1 hour');
  if not old.active and new.active then raise exception 'meeting_gone';end if;
 end if;
 if new.expires_at<=clock_timestamp() then new.active:=false;new.completed_at:=old.completed_at;end if;
 return new;
end $$;
create trigger meeting_lifetime before insert or update on public.meeting_points for each row execute function private.meeting_lifetime();
alter table public.meeting_recipients add column personal boolean not null default false;
alter table public.meeting_recipients add column selected_groups uuid[] not null default '{}';
-- Existing recipients have no provenance. Restrict them to relationships that exist at deployment.
update public.meeting_recipients r set personal=exists(select 1 from public.meeting_points m join public.location_shares s
 on s.owner_id=m.creator_id and s.viewer_id=r.user_id and s.enabled where m.id=r.meeting_id),
 selected_groups=array(select a.group_id from public.group_members a join public.group_members b using(group_id)
 join public.meeting_points m on m.creator_id=a.user_id where m.id=r.meeting_id and b.user_id=r.user_id);
create function private.meeting_authorized(mid uuid,uid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select uid is not null and exists(select 1 from public.meeting_points m where m.id=mid and
 (m.creator_id=uid or exists(select 1 from public.meeting_recipients r where r.meeting_id=m.id and r.user_id=uid and (
  (r.personal and exists(select 1 from public.location_shares s where s.owner_id=m.creator_id and s.viewer_id=uid and s.enabled))
  or exists(select 1 from public.group_members a join public.group_members b using(group_id) join public.groups g on g.id=a.group_id
   where a.user_id=m.creator_id and b.user_id=uid and a.group_id=any(r.selected_groups) and (g.expires_at is null or g.expires_at>statement_timestamp()))))));
$$;
create or replace function public.can_read_meeting(mid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select private.meeting_authorized(mid,auth.uid()) and exists(select 1 from public.meeting_points where id=mid and expires_at>clock_timestamp());
$$;
alter function public.create_meeting(uuid,float8,float8,boolean,uuid[],uuid[]) rename to create_meeting_before_v049;
alter function public.create_meeting_before_v049(uuid,float8,float8,boolean,uuid[],uuid[]) set schema private;
create function public.create_meeting(mid uuid,lat float8,lon float8,all_people boolean default false,people uuid[] default '{}',group_ids uuid[] default '{}') returns uuid
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();result uuid;
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from public.meeting_points where id=mid and creator_id=caller) then return mid;end if;
 result:=private.create_meeting_before_v049(mid,lat,lon,all_people,people,group_ids);
 update public.meeting_recipients r set personal=r.user_id=any(people) or (all_people and exists(select 1 from public.location_shares s where s.owner_id=caller and s.viewer_id=r.user_id and s.enabled)),
 selected_groups=array(select g from unnest(group_ids) g where exists(select 1 from public.group_members where group_id=g and user_id=r.user_id)) where meeting_id=result;
 return result;
end $$;

create or replace function private.complete_flare(mid uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 -- All recipients, including the creator, must be arrived under every recipient's
 -- grant. Completion must not act as an oracle for a hidden precise position.
 perform 1 from public.meeting_points where id=mid and active and expires_at>clock_timestamp() for update;
 if not found then return; end if;
 if exists(select 1 from public.meeting_recipients where meeting_id=mid)
 and not exists(select 1 from public.meeting_recipients a cross join public.meeting_recipients b
  where a.meeting_id=mid and b.meeting_id=mid and private.flare_participant(mid,a.user_id,b.user_id)->>'state'<>'arrived') then
  update public.meeting_points set active=false,completed_at=clock_timestamp(),removed_at=clock_timestamp() where id=mid;
  perform private.meeting_notice(mid,'removed');
 end if;
end $$;
revoke all on function private.complete_flare(uuid) from public,anon,authenticated;


alter table private.app_events drop constraint app_events_kind_check;
-- Consistent with authoritative meeting creation even when several RPCs share a transaction.
alter table private.app_events alter column created_at set default clock_timestamp();
alter table private.app_events add constraint app_events_kind_check check(kind in ('checkin','place','sos','sos_closed','meeting_removed'));
alter table private.push_outbox drop constraint push_outbox_kind_check;
alter table private.push_outbox add constraint push_outbox_kind_check check(kind in ('created','removed','location_request','checkin','place','sos','sos_closed','meeting_removed'));
alter table private.push_outbox drop constraint push_outbox_target_check;
alter table private.push_outbox add constraint push_outbox_target_check check(
 (kind in ('created','removed') and meeting_id is not null and request_id is null and event_id is null) or
 (kind='location_request' and request_id is not null and meeting_id is null and event_id is null) or
 (kind in ('checkin','place','sos','sos_closed','meeting_removed') and event_id is not null and meeting_id is null and request_id is null));

create function public.remove_meeting_v049(mid uuid) returns text language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();m public.meeting_points;eid uuid;content jsonb;targets uuid[];
begin
 select * into m from public.meeting_points where id=mid for update;
 -- No ID oracle for strangers, including removed points. Cleanup can remove the tombstone.
 if not found then return 'gone';end if;
 if not private.meeting_authorized(mid,caller) then raise exception 'not_authorized';end if;
 if not m.active or m.expires_at<=clock_timestamp() then return 'gone';end if;
 select array_agg(user_id) into targets from public.meeting_recipients where meeting_id=mid;
 update public.meeting_points set active=false,removed_at=clock_timestamp() where id=mid;
 -- The lock and active predicate yield one event even after concurrent/retried calls.
 if caller<>m.creator_id then
  eid:=gen_random_uuid();
  content:=jsonb_build_object('meeting_id',mid,'meeting_name',m.label,'actor_name',(select display_name from public.profiles where id=caller));
  insert into private.app_events(id,sender_id,kind,payload) values(eid,caller,'meeting_removed',content);
  insert into private.event_recipients(event_id,user_id,payload,personal) values(eid,m.creator_id,content,true);
  insert into private.push_outbox(recipient,event_id,kind) values(m.creator_id,eid,'meeting_removed');
 end if;
 perform private.notify_users(coalesce(targets,'{}')||array[m.creator_id]);
 return 'removed';
end $$;
create or replace function public.remove_meeting(mid uuid) returns void language plpgsql security definer set search_path='' as $$
begin perform public.remove_meeting_v049(mid);end $$;

-- Event content has no coordinates and remains readable to its recipient after relationship revocation.
alter function public.event_inbox() rename to event_inbox_before_v049;
alter function public.event_inbox_before_v049() set schema private;
create function public.event_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(item order by (item->>'created_at')::timestamptz desc) from (
 select value item from jsonb_array_elements(private.event_inbox_before_v049()) where value->>'kind'<>'meeting_removed'
 union all select jsonb_build_object('id',e.id,'sender_id',e.sender_id,'sender_name',e.payload->>'actor_name','kind',e.kind,
 'created_at',e.created_at,'expires_at',e.expires_at,'payload',e.payload,'recipients',jsonb_build_array(caller))
 from private.app_events e join private.event_recipients r on r.event_id=e.id and r.user_id=caller
 where e.kind='meeting_removed' and e.expires_at>statement_timestamp()
 and not exists(select 1 from private.event_dismissals d where d.user_id=caller and d.event_id=e.id)
 ) inbox),'[]');end $$;
create or replace function public.dismiss_event(eid uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if exists(select 1 from private.event_dismissals where user_id=caller and event_id=eid) then return;end if;
 if not exists(select 1 from jsonb_array_elements(public.event_inbox()) e where e->>'id'=eid::text) then raise exception 'not_authorized';end if;
 insert into private.event_dismissals(user_id,event_id) values(caller,eid) on conflict do nothing;
 perform private.notify_users(array[caller]);
end $$;
alter function public.push_job_authorized(uuid) rename to push_authorized_before_v049;
alter function public.push_authorized_before_v049(uuid) set schema private;
create function public.push_job_authorized(job uuid) returns boolean language sql security definer set search_path='' as $$
 select private.push_authorized_before_v049(job) and exists(select 1 from private.push_outbox q where q.id=job and
 (case when q.kind='meeting_removed' then exists(select 1 from private.app_events e join private.event_recipients r on r.event_id=e.id
   where e.id=q.event_id and r.user_id=q.recipient and e.expires_at>statement_timestamp())
 when q.meeting_id is not null then private.meeting_authorized(q.meeting_id,q.recipient) and exists(select 1 from public.meeting_points m where m.id=q.meeting_id and m.expires_at>statement_timestamp())
 else true end));
$$;
create function private.cleanup_meetings() returns void language plpgsql security definer set search_path='' as $$
begin
 -- Invalidation is enforced by reads/operations immediately, independent of this material cleanup.
 delete from public.meeting_points where expires_at<=clock_timestamp();
 delete from private.app_events where kind='meeting_removed' and expires_at<=clock_timestamp();
end $$;
-- pg_cron already exists on the verified production project. Test databases have no scheduler.
do $$ begin
 if exists(select 1 from pg_extension where extname='pg_cron') then
  perform cron.schedule('whereweare-meeting-expiry','* * * * *','select private.cleanup_meetings()');
 end if;
end $$;
revoke all on function private.meeting_lifetime(),private.meeting_authorized(uuid,uuid),private.create_meeting_before_v049(uuid,float8,float8,boolean,uuid[],uuid[]),private.event_inbox_before_v049(),private.push_authorized_before_v049(uuid),private.cleanup_meetings() from public,anon,authenticated,service_role;
revoke all on function public.create_meeting(uuid,float8,float8,boolean,uuid[],uuid[]),public.remove_meeting_v049(uuid),public.event_inbox() from public,anon;
grant execute on function public.create_meeting(uuid,float8,float8,boolean,uuid[],uuid[]),public.remove_meeting_v049(uuid),public.event_inbox() to authenticated;
revoke all on function public.push_job_authorized(uuid) from public,anon,authenticated;
grant execute on function public.push_job_authorized(uuid) to service_role;
update private.app_bootstrap set features=features||'{"meeting_ttl":true,"meeting_delete":true}'::jsonb;
notify pgrst,'reload schema';
commit;
