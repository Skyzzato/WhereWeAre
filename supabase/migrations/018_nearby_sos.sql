begin;
-- Empty string is the canonical removed icon. Existing IDs and memberships survive.
alter table public.groups drop constraint if exists groups_emoji_check;
alter table public.groups add constraint groups_emoji_check check(char_length(emoji) between 0 and 12);
create or replace function public.edit_group(gid uuid,group_name text,group_emoji text) returns void
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 update public.groups set name=btrim(group_name),emoji=coalesce(nullif(btrim(group_emoji),''),'') where id=gid and creator_id=caller;
 if not found then raise exception 'not_authorized';end if;
 perform private.notify_users(array(select user_id from public.group_members where group_id=gid)
 ||array(select user_id from public.group_requests where group_id=gid and status='pending'));
end $$;

create table private.nearby_config(
 singleton boolean primary key default true check(singleton),enabled boolean not null default true,
 availability_seconds int not null default 900 check(availability_seconds between 60 and 3600),
 fix_seconds int not null default 900 check(fix_seconds between 60 and 3600),
 radius_m int not null default 2000 check(radius_m between 100 and 10000),
 max_recipients int not null default 20 check(max_recipients between 1 and 20),
 sender_cooldown_seconds int not null default 300 check(sender_cooldown_seconds between 10 and 3600),
 sender_daily_limit int not null default 6 check(sender_daily_limit between 1 and 100),
 recipient_hourly_limit int not null default 6 check(recipient_hourly_limit between 1 and 100),
 max_accuracy_m int not null default 1000 check(max_accuracy_m between 10 and 2000)
);
insert into private.nearby_config default values;
create table private.nearby_volunteers(
 user_id uuid primary key references public.profiles(id) on delete cascade,
 opted_in boolean not null default false,available_until timestamptz,
 latitude float8,longitude float8,accuracy float8,acquired_at timestamptz,received_at timestamptz
);
create table private.nearby_invitations(
 event_id uuid references private.app_events(id) on delete cascade,
 user_id uuid references public.profiles(id) on delete cascade,
 created_at timestamptz not null default now(),viewed_at timestamptz,accepted_at timestamptz,withdrawn_at timestamptz,
 primary key(event_id,user_id)
);
create index nearby_recipient_rate on private.nearby_invitations(user_id,created_at);
alter table private.sos_state add column nearby_requested boolean not null default false;
alter table private.sos_state add column nearby_search text;
alter table private.nearby_config enable row level security;
alter table private.nearby_volunteers enable row level security;
alter table private.nearby_invitations enable row level security;
revoke all on private.nearby_config,private.nearby_volunteers,private.nearby_invitations from public,anon,authenticated;

create function public.nearby_sos_status() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); v private.nearby_volunteers;c private.nearby_config;
begin
 select * into c from private.nearby_config; select * into v from private.nearby_volunteers where user_id=caller;
 return jsonb_build_object('enabled',c.enabled,'opted_in',coalesce(v.opted_in,false),
 'available',coalesce(v.opted_in and v.available_until>now() and v.acquired_at>now()-make_interval(secs=>c.fix_seconds),false),
 'available_until',v.available_until,'server_time',clock_timestamp(),'radius_m',c.radius_m,'availability_seconds',c.availability_seconds);
end $$;
create function public.set_nearby_sos_consent(agree boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform pg_advisory_xact_lock(420018);
 if agree is null then raise exception 'invalid_form';end if;
 insert into private.nearby_volunteers(user_id,opted_in) values(caller,agree)
 on conflict(user_id) do update set opted_in=excluded.opted_in;
 if not agree then
  update private.nearby_volunteers set available_until=null,latitude=null,longitude=null,accuracy=null,acquired_at=null,received_at=null where user_id=caller;
  update private.nearby_invitations set withdrawn_at=coalesce(withdrawn_at,clock_timestamp()) where user_id=caller;
  delete from private.push_outbox q using private.nearby_invitations n where n.user_id=caller and q.recipient=caller and q.event_id=n.event_id and q.sent_at is null;
 end if;
 perform private.notify_users(array[caller]||array(select e.sender_id from private.app_events e join private.nearby_invitations n on n.event_id=e.id where n.user_id=caller));
end $$;
create function public.refresh_nearby_sos(lat float8,lon float8,accuracy_m float8,fix_at timestamptz) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();c private.nearby_config;
begin
 perform pg_advisory_xact_lock(420018);select * into c from private.nearby_config;
 if not c.enabled then raise exception 'nearby_configuration';end if;
 if (lat between -90 and 90 and lon between -180 and 180 and accuracy_m between 0 and c.max_accuracy_m
 and fix_at<=clock_timestamp()+interval '30 seconds' and fix_at>=clock_timestamp()-make_interval(secs=>c.fix_seconds)) is not true then raise exception 'nearby_fresh_fix_required';end if;
 update private.nearby_volunteers set latitude=lat,longitude=lon,accuracy=accuracy_m,acquired_at=fix_at,received_at=clock_timestamp(),
 available_until=clock_timestamp()+make_interval(secs=>c.availability_seconds)
 where user_id=caller and opted_in and (acquired_at is null or acquired_at<fix_at);
 if not found then raise exception 'nearby_consent_or_new_fix_required';end if;
end $$;
create function private.nearby_access(eid uuid,uid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.nearby_invitations n join private.nearby_volunteers v on v.user_id=n.user_id
 join private.app_events e on e.id=n.event_id join private.sos_state s on s.event_id=e.id cross join private.nearby_config c
 where n.event_id=eid and n.user_id=uid and n.withdrawn_at is null and v.opted_in and e.expires_at>now() and s.closed_at is null
 and (n.accepted_at is not null or (c.enabled and v.available_until>now() and v.acquired_at>now()-make_interval(secs=>c.fix_seconds))));
$$;
-- Preserve existing contact/group APIs and their policies; new registration uses the same event/outbox.
create function public.send_sos_v042(eid uuid,category text,people uuid[],group_ids uuid[],lat float8 default null,lon float8 default null,accuracy_m float8 default null,fix_at timestamptz default null,nearby boolean default false)
returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();targets uuid[];target uuid;content jsonb;c private.nearby_config;search_state text;
begin
 perform pg_advisory_xact_lock(420018);
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from private.app_events where id=eid and sender_id=caller and kind='sos') then return eid;end if;
 select * into c from private.nearby_config;
 if nearby and not c.enabled then raise exception 'nearby_configuration';end if;
 if eid is null or category is null or category not in ('help','injured','lost','vehicle','accident') or coalesce(cardinality(people),0)>200 or coalesce(cardinality(group_ids),0)>50 then raise exception 'invalid_form';end if;
 if not ((lat is null and lon is null and accuracy_m is null and fix_at is null) or
 (lat between -90 and 90 and lon between -180 and 180 and accuracy_m between 0 and 100000 and fix_at<=clock_timestamp()+interval '30 seconds' and fix_at>=clock_timestamp()-interval '24 hours'))
 or (lat is null)<>(lon is null) or (lat is null)<>(accuracy_m is null) or (lat is null)<>(fix_at is null) then raise exception 'invalid_form';end if;
 if exists(select 1 from unnest(people) u where u is null or not public.can_read_profile(u)) or exists(select 1 from unnest(group_ids) g where g is null or not public.is_group_member(g)) then raise exception 'not_authorized';end if;
 select array_agg(distinct uid) into targets from (select unnest(people) uid union select user_id from public.group_members where group_id=any(group_ids)) t
 where uid<>caller and exists(select 1 from public.profiles where id=uid and not deleting);
 if coalesce(cardinality(targets),0)>500 or (coalesce(cardinality(targets),0)=0 and not nearby) then raise exception 'invalid_recipients';end if;
 if exists(select 1 from private.app_events e join private.sos_state s on s.event_id=e.id where e.sender_id=caller and s.closed_at is null and e.expires_at>now()) then raise exception 'sos_already_active';end if;
 if exists(select 1 from private.app_events where sender_id=caller and kind='sos' and created_at>now()-make_interval(secs=>c.sender_cooldown_seconds))
 or (select count(*) from private.app_events where sender_id=caller and kind='sos' and created_at>now()-interval '1 day')>=c.sender_daily_limit then raise exception 'sos_cooldown';end if;
 content:=jsonb_build_object('category',category,'latitude',lat,'longitude',lon,'accuracy',accuracy_m,'recorded_at',fix_at);
 insert into private.app_events(id,sender_id,kind,payload,expires_at) values(eid,caller,'sos',content,now()+interval '6 hours');
 search_state:=case when not nearby then null when fix_at is null or fix_at<now()-make_interval(secs=>c.fix_seconds) or accuracy_m>c.max_accuracy_m then 'fresh_fix_required' else 'awaiting_response' end;
 insert into private.sos_state(event_id,nearby_requested,nearby_search) values(eid,nearby,search_state);
 foreach target in array coalesce(targets,'{}') loop
  insert into private.event_recipients(event_id,user_id,payload,selected_groups,personal) values(eid,target,content,coalesce(group_ids,'{}'),coalesce(target=any(people),false));
  insert into private.push_outbox(recipient,event_id,kind) values(target,eid,'sos');
 end loop;
 if nearby and search_state='awaiting_response' then
  for target in select v.user_id from private.nearby_volunteers v join public.profiles p on p.id=v.user_id
   where v.opted_in and v.user_id<>caller and not p.deleting and not(v.user_id=any(coalesce(targets,'{}')))
   and v.available_until>now() and v.acquired_at>now()-make_interval(secs=>c.fix_seconds) and v.accuracy between 0 and c.max_accuracy_m
   and 6371000*2*asin(sqrt(least(1.0,power(sin(radians(v.latitude-lat)/2),2)+cos(radians(lat))*cos(radians(v.latitude))*power(sin(radians(v.longitude-lon)/2),2))))<=c.radius_m
   and (select count(*) from private.nearby_invitations n where n.user_id=v.user_id and n.created_at>now()-interval '1 hour')<c.recipient_hourly_limit
   order by v.user_id limit c.max_recipients
  loop
   insert into private.nearby_invitations(event_id,user_id) values(eid,target);
   insert into private.push_outbox(recipient,event_id,kind) values(target,eid,'sos');
  end loop;
 end if;
 perform private.notify_users(coalesce(targets,'{}')||array[caller]||array(select user_id from private.nearby_invitations where event_id=eid));
 return eid;
end $$;
create or replace function public.send_sos(eid uuid,category text,people uuid[],group_ids uuid[],lat float8 default null,lon float8 default null,accuracy_m float8 default null,fix_at timestamptz default null)
returns uuid language sql security definer set search_path='' as $$
 select public.send_sos_v042(eid,category,people,group_ids,lat,lon,accuracy_m,fix_at,false);
$$;
alter function public.event_inbox() rename to inbox_before_nearby;
alter function public.inbox_before_nearby() set schema private;
create function public.event_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 return private.inbox_before_nearby()||coalesce((select jsonb_agg(jsonb_build_object('id',e.id,'kind','sos',
 'sender_id',case when n.accepted_at is not null then e.sender_id else '00000000-0000-0000-0000-000000000000'::uuid end,
 'sender_name',case when n.accepted_at is not null then p.display_name else '' end,
 'created_at',e.created_at,'expires_at',e.expires_at,'recipients',jsonb_build_array(caller),
 'payload',case when n.accepted_at is not null then e.payload||'{"nearby":true,"accepted":true}'::jsonb
 else jsonb_build_object('category',e.payload->>'category','nearby',true,'accepted',false,
 'area_latitude',floor((e.payload->>'latitude')::numeric*100)/100+0.005,
 'area_longitude',floor((e.payload->>'longitude')::numeric*100)/100+0.005) end))
 from private.nearby_invitations n join private.app_events e on e.id=n.event_id join public.profiles p on p.id=e.sender_id
 where n.user_id=caller and private.nearby_access(e.id,caller)),'[]');
end $$;
alter function public.respond_sos(uuid,text) rename to respond_before_nearby;
alter function public.respond_before_nearby(uuid,text) set schema private;
create function public.respond_sos(eid uuid,answer text default null) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform pg_advisory_xact_lock(420018);
 if exists(select 1 from private.nearby_invitations where event_id=eid and user_id=caller) then
  if not private.nearby_access(eid,caller) then raise exception 'not_authorized';end if;
  if answer is not null and answer not in ('can_help','cannot_help') then raise exception 'invalid_form';end if;
  update private.nearby_invitations set viewed_at=coalesce(viewed_at,clock_timestamp()),
   accepted_at=case when answer='can_help' then coalesce(accepted_at,clock_timestamp()) else accepted_at end,
   withdrawn_at=case when answer='cannot_help' then clock_timestamp() else withdrawn_at end where event_id=eid and user_id=caller;
  perform private.notify_users(array[caller]||array(select sender_id from private.app_events where id=eid));
 else perform private.respond_before_nearby(eid,answer);end if;
end $$;
alter function public.sos_status(uuid) rename to status_before_nearby;
alter function public.status_before_nearby(uuid) set schema private;
create function public.sos_status(eid uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 result:=private.status_before_nearby(eid);
 return result||jsonb_build_object('nearby_state',(select nearby_search from private.sos_state where event_id=eid),
 'recipients',(result->'recipients')||coalesce((select jsonb_agg(jsonb_build_object('user_id',n.user_id,'name',p.display_name,
 'push_accepted',exists(select 1 from private.push_outbox q where q.event_id=eid and q.recipient=n.user_id and q.accepted_at is not null),
 'viewed',n.viewed_at is not null,'response','can_help','nearby',true))
 from private.nearby_invitations n join public.profiles p on p.id=n.user_id
 where n.event_id=eid and n.accepted_at is not null and private.nearby_access(eid,n.user_id)),'[]'));
end $$;
-- Called immediately before each queued push; payload still contains identifiers only.
create function public.push_job_authorized(job uuid) returns boolean language plpgsql security definer set search_path='' as $$
declare q private.push_outbox;
begin
 select * into q from private.push_outbox where id=job and sent_at is null;
 if not found then return false;end if;
 if exists(select 1 from private.nearby_invitations where event_id=q.event_id and user_id=q.recipient) then
  return private.nearby_access(q.event_id,q.recipient);
 end if;
 if q.kind='sos' then return exists(select 1 from private.app_events e join private.event_recipients r on r.event_id=e.id
 where e.id=q.event_id and r.user_id=q.recipient and e.expires_at>now() and private.checkin_precision(e.sender_id,r.user_id,r.selected_groups,r.personal,r.meeting_id) is not null);end if;
 return true;
end $$;
alter function public.app_metadata() rename to metadata_before_nearby;
alter function public.metadata_before_nearby() set schema private;
create function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
begin perform private.require_user();return private.metadata_before_nearby()||jsonb_build_object('nearby_sos_available',(select enabled from private.nearby_config));end $$;
revoke all on function private.nearby_access(uuid,uuid),private.inbox_before_nearby(),private.respond_before_nearby(uuid,text),private.status_before_nearby(uuid),private.metadata_before_nearby() from public,anon,authenticated;
revoke all on function public.nearby_sos_status(),public.set_nearby_sos_consent(boolean),public.refresh_nearby_sos(float8,float8,float8,timestamptz),public.send_sos_v042(uuid,text,uuid[],uuid[],float8,float8,float8,timestamptz,boolean),public.event_inbox(),public.respond_sos(uuid,text),public.sos_status(uuid),public.app_metadata(),public.push_job_authorized(uuid) from public,anon;
grant execute on function public.nearby_sos_status(),public.set_nearby_sos_consent(boolean),public.refresh_nearby_sos(float8,float8,float8,timestamptz),public.send_sos_v042(uuid,text,uuid[],uuid[],float8,float8,float8,timestamptz,boolean),public.event_inbox(),public.respond_sos(uuid,text),public.sos_status(uuid),public.app_metadata() to authenticated;
revoke all on function public.push_job_authorized(uuid) from authenticated;
grant execute on function public.push_job_authorized(uuid) to service_role;
update private.app_bootstrap set features=features||'{"nearby_sos":true}'::jsonb;
commit;
