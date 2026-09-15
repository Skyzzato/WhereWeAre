begin;
alter table private.app_events drop constraint if exists app_events_kind_check;
alter table private.app_events add constraint app_events_kind_check check(kind in ('checkin','place','sos','sos_closed'));
alter table private.push_outbox drop constraint if exists push_outbox_kind_check;
alter table private.push_outbox add constraint push_outbox_kind_check check(kind in ('created','removed','location_request','checkin','place','sos','sos_closed'));
alter table private.push_outbox drop constraint if exists push_outbox_target_check;
alter table private.push_outbox add constraint push_outbox_target_check check(
 (kind in ('created','removed') and meeting_id is not null and request_id is null and event_id is null) or
 (kind='location_request' and request_id is not null and meeting_id is null and event_id is null) or
 (kind in ('checkin','place','sos','sos_closed') and event_id is not null and meeting_id is null and request_id is null));
alter table private.push_outbox add column if not exists accepted_at timestamptz;
create or replace function public.record_push_acceptance(job uuid) returns void language sql security definer set search_path='' as $$
 update private.push_outbox set accepted_at=coalesce(accepted_at,clock_timestamp()) where id=job;
$$;
revoke all on function public.record_push_acceptance(uuid) from public,anon,authenticated;
grant execute on function public.record_push_acceptance(uuid) to service_role;
create table if not exists private.sos_state(
 event_id uuid primary key references private.app_events(id) on delete cascade,
 closed_at timestamptz,reason text check(reason in ('resolved','okay','accidental'))
);
create table if not exists private.sos_replies(
 event_id uuid not null references private.app_events(id) on delete cascade,
 user_id uuid not null references public.profiles(id) on delete cascade,
 viewed_at timestamptz not null default now(),response text check(response in ('can_help','cannot_help')),
 primary key(event_id,user_id)
);
alter table private.sos_state enable row level security;
alter table private.sos_replies enable row level security;
revoke all on private.sos_state,private.sos_replies from public,anon,authenticated;

create or replace function public.send_sos(eid uuid,category text,people uuid[],group_ids uuid[],lat float8 default null,lon float8 default null,accuracy_m float8 default null,fix_at timestamptz default null)
returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); targets uuid[]; target uuid; content jsonb;
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from private.app_events where id=eid and sender_id=caller and kind='sos') then return eid;end if;
 if eid is null or category is null or category not in ('help','injured','lost','vehicle','accident') or coalesce(cardinality(people),0)>200 or coalesce(cardinality(group_ids),0)>50 then raise exception 'invalid_form';end if;
 if not ((lat is null and lon is null and accuracy_m is null and fix_at is null) or
  (lat between -90 and 90 and lon between -180 and 180 and accuracy_m between 0 and 100000 and fix_at<=clock_timestamp()+interval '30 seconds' and fix_at>=clock_timestamp()-interval '24 hours'))
  or (lat is null)<>(lon is null) or (lat is null)<>(accuracy_m is null) or (lat is null)<>(fix_at is null) then raise exception 'invalid_form';end if;
 if exists(select 1 from unnest(people) u where u is null or not public.can_read_profile(u)) or exists(select 1 from unnest(group_ids) g where g is null or not public.is_group_member(g)) then raise exception 'not_authorized';end if;
 select array_agg(distinct uid) into targets from (
  select unnest(people) uid union select user_id from public.group_members where group_id=any(group_ids)
 ) t where uid<>caller and exists(select 1 from public.profiles where id=uid and not deleting);
 if coalesce(cardinality(targets),0) not between 1 and 500 then raise exception 'invalid_recipients';end if;
 if exists(select 1 from private.app_events e join private.sos_state s on s.event_id=e.id where e.sender_id=caller and s.closed_at is null and e.expires_at>now()) then raise exception 'sos_already_active';end if;
 if exists(select 1 from private.app_events where sender_id=caller and kind='sos' and created_at>now()-interval '5 minutes')
 or (select count(*) from private.app_events where sender_id=caller and kind='sos' and created_at>now()-interval '1 day')>=6 then raise exception 'sos_cooldown';end if;
 content:=jsonb_build_object('category',category,'latitude',lat,'longitude',lon,'accuracy',accuracy_m,'recorded_at',fix_at);
 -- This explicit, limited SOS consent may share the best fix, independent of normal precision.
 insert into private.app_events(id,sender_id,kind,payload,expires_at) values(eid,caller,'sos',content,now()+interval '6 hours');
 insert into private.sos_state(event_id) values(eid);
 foreach target in array targets loop
  insert into private.event_recipients(event_id,user_id,payload,selected_groups,personal) values(eid,target,content,coalesce(group_ids,'{}'),coalesce(target=any(people),false));
  insert into private.push_outbox(recipient,event_id,kind) values(target,eid,'sos');
 end loop;
 perform private.notify_users(targets||array[caller]);
 return eid;
end $$;

create or replace function public.respond_sos(eid uuid,answer text default null) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); owner_id uuid;
begin
 select e.sender_id into owner_id from private.app_events e join private.sos_state s on s.event_id=e.id
 where e.id=eid and e.expires_at>now() and s.closed_at is null
 and exists(select 1 from private.event_recipients r where r.event_id=eid and r.user_id=caller and private.checkin_precision(e.sender_id,caller,r.selected_groups,r.personal,r.meeting_id) is not null);
 if owner_id is null then raise exception 'not_authorized';end if;
 if answer is not null and answer not in ('can_help','cannot_help') then raise exception 'invalid_form';end if;
 insert into private.sos_replies(event_id,user_id,response) values(eid,caller,answer)
 on conflict(event_id,user_id) do update set response=coalesce(excluded.response,sos_replies.response);
 perform private.notify_users(array[owner_id,caller]);
end $$;
create or replace function public.sos_status(eid uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if not exists(select 1 from private.app_events where id=eid and kind='sos' and sender_id=caller) then raise exception 'not_authorized';end if;
 return jsonb_build_object('recipients',coalesce((select jsonb_agg(jsonb_build_object('user_id',r.user_id,'name',p.display_name,
  'push_accepted',exists(select 1 from private.push_outbox q where q.event_id=eid and q.recipient=r.user_id and q.accepted_at is not null),
  'viewed',s.viewed_at is not null,'response',s.response)) from private.event_recipients r join public.profiles p on p.id=r.user_id
  left join private.sos_replies s on s.event_id=r.event_id and s.user_id=r.user_id where r.event_id=eid),'[]'));
end $$;
create or replace function public.close_sos(eid uuid,closure_reason text) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); targets uuid[]; target uuid; closed_id uuid:=gen_random_uuid();content jsonb;
begin
 perform 1 from private.app_events where id=eid and kind='sos' and sender_id=caller for update;
 if not found then raise exception 'not_authorized';end if;
 if closure_reason is null or closure_reason not in ('resolved','okay','accidental') then raise exception 'invalid_form';end if;
 update private.sos_state set closed_at=clock_timestamp(),reason=closure_reason where event_id=eid and closed_at is null;
 if not found then return;end if;
 select array_agg(user_id) into targets from private.event_recipients where event_id=eid;
 content:=jsonb_build_object('reason',closure_reason);
 update private.app_events set expires_at=least(expires_at,now()),payload='{}' where id=eid;
 update private.event_recipients set payload='{}' where event_id=eid;
 delete from private.push_outbox where event_id=eid and sent_at is null;
 insert into private.app_events(id,sender_id,kind,payload) values(closed_id,caller,'sos_closed',content);
 foreach target in array coalesce(targets,'{}') loop
  insert into private.event_recipients(event_id,user_id,payload,personal) values(closed_id,target,content,true);
  insert into private.push_outbox(recipient,event_id,kind) values(target,closed_id,'sos_closed');
 end loop;
 perform private.notify_users(coalesce(targets,'{}')||array[caller]);
end $$;
revoke all on function public.send_sos(uuid,text,uuid[],uuid[],float8,float8,float8,timestamptz),public.respond_sos(uuid,text),public.sos_status(uuid),public.close_sos(uuid,text) from public,anon;
grant execute on function public.send_sos(uuid,text,uuid[],uuid[],float8,float8,float8,timestamptz),public.respond_sos(uuid,text),public.sos_status(uuid),public.close_sos(uuid,text) to authenticated;

do $$ begin
 if to_regprocedure('private.metadata_before_sos()') is null then
  alter function public.app_metadata() rename to metadata_before_sos;
  alter function public.metadata_before_sos() set schema private;
 end if;
end $$;
revoke all on function private.metadata_before_sos() from public,anon,authenticated;
create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
begin perform private.require_user();return private.metadata_before_sos()||jsonb_build_object('sos_available',true);end $$;
revoke all on function public.app_metadata() from public,anon;
grant execute on function public.app_metadata() to authenticated;
update private.app_bootstrap set features=features||'{"sos":true,"nearby_sos":false}'::jsonb;
create or replace function public.event_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(rowdata order by created_at desc) from (
 select e.created_at,jsonb_build_object('id',e.id,'sender_id',e.sender_id,'sender_name',p.display_name,'kind',e.kind,
  'created_at',e.created_at,'expires_at',e.expires_at,'payload',case when e.sender_id=caller then e.payload
   when e.kind<>'checkin' then r.payload
   when current_precision.m>(r.payload->>'precision_m')::int then r.payload||(select jsonb_build_object('latitude',a.latitude,'longitude',a.longitude,'accuracy',current_precision.m,'precision_m',current_precision.m)
    from private.approximate_location(e.sender_id,(e.payload->>'latitude')::float8,(e.payload->>'longitude')::float8,current_precision.m) a)
   else r.payload end,
  'recipients',case when e.sender_id=caller then (select jsonb_agg(user_id) from private.event_recipients where event_id=e.id) else jsonb_build_array(caller) end) rowdata
 from private.app_events e join public.profiles p on p.id=e.sender_id
 left join private.event_recipients r on r.event_id=e.id and r.user_id=caller
 left join lateral (select private.checkin_precision(e.sender_id,caller,r.selected_groups,r.personal,r.meeting_id) m) current_precision on true
 where e.expires_at>now() and (e.sender_id=caller or (r.user_id=caller and current_precision.m is not null))
 ) events),'[]'); end $$;
create or replace function public.remove_event(eid uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); targets uuid[];
begin
 if exists(select 1 from private.app_events where id=eid and kind='sos') then raise exception 'use_sos_close';end if;
 if not exists(select 1 from private.app_events where id=eid and sender_id=caller) then raise exception 'not_authorized'; end if;
 select array_agg(user_id) into targets from private.event_recipients where event_id=eid;
 -- Keep an identifier tombstone so late HTTP retries cannot resurrect a removed snapshot.
 update private.app_events set expires_at=least(expires_at,now()),payload='{}' where id=eid and sender_id=caller;
 delete from private.event_recipients where event_id=eid;
 delete from private.push_outbox where event_id=eid;
 perform private.notify_users(coalesce(targets,'{}')||array[caller]);
end $$;

commit;
