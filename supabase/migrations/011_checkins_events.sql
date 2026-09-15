begin;
create table if not exists private.app_events(
 id uuid primary key, sender_id uuid not null references public.profiles(id) on delete cascade,
 kind text not null check(kind in ('checkin')), created_at timestamptz not null default now(),
 expires_at timestamptz not null default now()+interval '24 hours', payload jsonb not null
);
create table if not exists private.event_recipients(
 event_id uuid not null references private.app_events(id) on delete cascade,
 user_id uuid not null references public.profiles(id) on delete cascade,
 payload jsonb not null, selected_groups uuid[] not null default '{}',personal boolean not null default false,
 meeting_id uuid references public.meeting_points(id) on delete set null, primary key(event_id,user_id)
);
create index if not exists event_recipient_user on private.event_recipients(user_id);
alter table private.app_events enable row level security;
alter table private.event_recipients enable row level security;
revoke all on private.app_events,private.event_recipients from public,anon,authenticated;
alter table private.push_outbox add column if not exists event_id uuid references private.app_events(id) on delete cascade;
alter table private.push_outbox drop constraint if exists push_outbox_kind_check;
alter table private.push_outbox add constraint push_outbox_kind_check check(kind in ('created','removed','location_request','checkin'));
alter table private.push_outbox drop constraint if exists push_outbox_target_check;
alter table private.push_outbox add constraint push_outbox_target_check check(
 (kind in ('created','removed') and meeting_id is not null and request_id is null and event_id is null) or
 (kind='location_request' and request_id is not null and meeting_id is null and event_id is null) or
 (kind='checkin' and event_id is not null and meeting_id is null and request_id is null));
create unique index if not exists one_event_push on private.push_outbox(recipient,event_id,kind) where event_id is not null;

create or replace function private.checkin_precision(owner_id uuid,viewer_id uuid,groups uuid[],personal boolean,meeting uuid) returns int
language sql stable security definer set search_path='' as $$
 select min(m) from (
  select coalesce((select shared_precision from public.location_shares where owner_id=checkin_precision.owner_id and viewer_id=checkin_precision.viewer_id),p.shared_precision) m
   from public.profiles p where id=owner_id and (personal or exists(select 1 from public.meeting_recipients where meeting_id=meeting and user_id=viewer_id))
  union all select coalesce(a.shared_precision,p.shared_precision) from public.group_members a
   join public.group_members b using(group_id) join public.profiles p on p.id=a.user_id
   where a.user_id=owner_id and b.user_id=viewer_id and a.group_id=any(groups)
 ) sources;
$$;
revoke all on function private.checkin_precision(uuid,uuid,uuid[],boolean,uuid) from public,anon,authenticated;

create or replace function public.create_checkin(eid uuid,checkin_type text,lat float8,lon float8,accuracy_m float8,fix_at timestamptz,
 people uuid[] default '{}',group_ids uuid[] default '{}',meeting uuid default null,message text default '') returns uuid
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); targets uuid[]; recipient uuid; radius int; pos record; content jsonb;
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from private.app_events where id=eid and sender_id=caller and kind='checkin') then return eid; end if;
 if eid is null or checkin_type not in ('here','arrived','okay') or checkin_type is null or lat is null or lon is null
 or not(lat between -90 and 90) or not(lon between -180 and 180) or accuracy_m is null or accuracy_m<0 or accuracy_m>100000
 or fix_at is null or fix_at>clock_timestamp()+interval '30 seconds' or fix_at<clock_timestamp()-interval '2 minutes'
 or length(coalesce(message,''))>160 or cardinality(people)>200 or cardinality(group_ids)>50 then raise exception 'invalid_form'; end if;
 if exists(select 1 from unnest(people) p where not public.can_read_profile(p))
 or exists(select 1 from unnest(group_ids) g where not public.is_group_member(g))
 or (meeting is not null and not exists(select 1 from public.meeting_points where id=meeting and active and public.can_read_meeting(id))) then raise exception 'not_authorized'; end if;
 select array_agg(distinct uid) into targets from (
  select unnest(people) uid union select user_id from public.group_members where group_id=any(group_ids)
  union select user_id from public.meeting_recipients where meeting_id=meeting
 ) t where uid<>caller and exists(select 1 from public.profiles where id=uid and not deleting);
 if coalesce(cardinality(targets),0) not between 1 and 500 then raise exception 'invalid_recipients'; end if;
 if (select count(*) from private.app_events where sender_id=caller and created_at>now()-interval '1 hour')>=30 then raise exception 'request_cooldown'; end if;
 content:=jsonb_build_object('checkin_type',checkin_type,'message',coalesce(message,''),'latitude',lat,'longitude',lon,'accuracy',accuracy_m,'recorded_at',fix_at,'precision_m',0);
 insert into private.app_events(id,sender_id,kind,payload) values(eid,caller,'checkin',content);
 foreach recipient in array targets loop
  -- Snapshot consent is explicit even when continuous sharing is OFF. Use the
  -- personal override plus overrides of selected groups that contain this recipient.
  radius:=private.checkin_precision(caller,recipient,coalesce(group_ids,'{}'),coalesce(recipient=any(people),false),meeting);
  if radius>0 then
   select * into pos from private.approximate_location(caller,lat,lon,radius);
   insert into private.event_recipients values(eid,recipient,content||jsonb_build_object('latitude',pos.latitude,'longitude',pos.longitude,'accuracy',radius,'precision_m',radius),coalesce(group_ids,'{}'),coalesce(recipient=any(people),false),meeting);
  else insert into private.event_recipients values(eid,recipient,content,coalesce(group_ids,'{}'),coalesce(recipient=any(people),false),meeting); end if;
  insert into private.push_outbox(recipient,event_id,kind) values(recipient,eid,'checkin') on conflict do nothing;
 end loop;
 perform private.notify_users(targets||array[caller]);
 return eid;
end $$;
create or replace function public.event_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(rowdata order by created_at desc) from (
 select e.created_at,jsonb_build_object('id',e.id,'sender_id',e.sender_id,'sender_name',p.display_name,'kind',e.kind,
  'created_at',e.created_at,'expires_at',e.expires_at,'payload',case when e.sender_id=caller then e.payload
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
 if not exists(select 1 from private.app_events where id=eid and sender_id=caller) then raise exception 'not_authorized'; end if;
 select array_agg(user_id) into targets from private.event_recipients where event_id=eid;
 -- Keep an identifier tombstone so late HTTP retries cannot resurrect a removed snapshot.
 update private.app_events set expires_at=least(expires_at,now()),payload='{}' where id=eid and sender_id=caller;
 delete from private.event_recipients where event_id=eid;
 delete from private.push_outbox where event_id=eid;
 perform private.notify_users(coalesce(targets,'{}')||array[caller]);
end $$;
revoke all on function public.create_checkin(uuid,text,float8,float8,float8,timestamptz,uuid[],uuid[],uuid,text),public.event_inbox(),public.remove_event(uuid) from public,anon;
grant execute on function public.create_checkin(uuid,text,float8,float8,float8,timestamptz,uuid[],uuid[],uuid,text),public.event_inbox(),public.remove_event(uuid) to authenticated;
create or replace function public.claim_push_batch() returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 with batch as (select id from private.push_outbox where sent_at is null and attempts<8 and (leased_until is null or leased_until<now()) order by created_at for update skip locked limit 50),
 leased as (update private.push_outbox q set leased_until=now()+interval '5 minutes',attempts=attempts+1 from batch where q.id=batch.id returning q.*)
 select coalesce(jsonb_agg(jsonb_build_object('id',q.id,'recipient',q.recipient,'meeting_id',q.meeting_id,'request_id',q.request_id,'event_id',q.event_id,'kind',q.kind,
 'tokens',coalesce((select jsonb_agg(t.token) from public.device_tokens t where t.user_id=q.recipient),'[]'))),'[]') into result from leased q;
 return result;
end $$;
do $$ begin
 if to_regprocedure('private.metadata_before_events()') is null then
  alter function public.app_metadata() rename to metadata_before_events;
  alter function public.metadata_before_events() set schema private;
 end if;
end $$;
revoke all on function private.metadata_before_events() from public,anon,authenticated;
create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
begin perform private.require_user();return private.metadata_before_events()||jsonb_build_object('events_available',true,'events',public.event_inbox());end $$;
revoke all on function public.app_metadata() from public,anon;
grant execute on function public.app_metadata() to authenticated;
update private.app_bootstrap set features=features||'{"checkins":true}'::jsonb;
commit;
