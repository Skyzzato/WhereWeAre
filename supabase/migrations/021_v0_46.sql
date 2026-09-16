begin;
-- Sole authoritative functional quota switch. Counters/history remain intact.
alter table private.nearby_config add column sos_quota_enabled boolean not null default false;
create or replace function public.send_sos_v042(eid uuid,category text,people uuid[],group_ids uuid[],lat float8 default null,lon float8 default null,accuracy_m float8 default null,fix_at timestamptz default null,nearby boolean default false)
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
 if c.sos_quota_enabled and (exists(select 1 from private.app_events where sender_id=caller and kind='sos' and created_at>now()-make_interval(secs=>c.sender_cooldown_seconds))
 or (select count(*) from private.app_events where sender_id=caller and kind='sos' and created_at>now()-interval '1 day')>=c.sender_daily_limit) then raise exception 'sos_cooldown';end if;
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
   and v.acquired_at>now()-make_interval(secs=>c.fix_seconds) and v.accuracy between 0 and c.max_accuracy_m
   and 6371000*2*asin(sqrt(least(1.0,power(sin(radians(v.latitude-lat)/2),2)+cos(radians(lat))*cos(radians(v.latitude))*power(sin(radians(v.longitude-lon)/2),2))))<=c.radius_m
   and (not c.sos_quota_enabled or (select count(*) from private.nearby_invitations n where n.user_id=v.user_id and n.created_at>now()-interval '1 hour')<c.recipient_hourly_limit)
   order by v.user_id limit c.max_recipients
  loop
   insert into private.nearby_invitations(event_id,user_id) values(eid,target);
   insert into private.push_outbox(recipient,event_id,kind) values(target,eid,'sos');
  end loop;
 end if;
 perform private.notify_users(coalesce(targets,'{}')||array[caller]||array(select user_id from private.nearby_invitations where event_id=eid));
 return eid;
end $$;
create or replace function private.nearby_access(eid uuid,uid uuid) returns boolean language sql stable security definer set search_path='' as $$
 select exists(select 1 from private.nearby_invitations n join private.nearby_volunteers v on v.user_id=n.user_id
 join private.app_events e on e.id=n.event_id join private.sos_state s on s.event_id=e.id cross join private.nearby_config c
 where n.event_id=eid and n.user_id=uid and n.withdrawn_at is null and v.opted_in and e.expires_at>now() and s.closed_at is null
 and (n.accepted_at is not null or (c.enabled and v.acquired_at>now()-make_interval(secs=>c.fix_seconds))));
$$;
create or replace function public.nearby_sos_status() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); v private.nearby_volunteers;c private.nearby_config;
begin
 select * into c from private.nearby_config; select * into v from private.nearby_volunteers where user_id=caller;
 return jsonb_build_object('consent_initialized',v.user_id is not null,'sos_quota_enabled',c.sos_quota_enabled,'enabled',c.enabled,'opted_in',coalesce(v.opted_in,false),
 'available',coalesce(v.opted_in and v.acquired_at>now()-make_interval(secs=>c.fix_seconds),false),
 'available_until',v.acquired_at+make_interval(secs=>c.fix_seconds),'server_time',clock_timestamp(),'radius_m',c.radius_m,'availability_seconds',c.availability_seconds);
end $$;

-- Account-scoped dismissals; no client access to global event deletion or another user's list.
create table private.event_dismissals (
 user_id uuid not null references public.profiles(id) on delete cascade,
 event_id uuid not null references private.app_events(id) on delete cascade,
 dismissed_at timestamptz not null default clock_timestamp(),
 primary key(user_id,event_id)
);
alter table private.event_dismissals enable row level security;
revoke all on private.event_dismissals from public,anon,authenticated;
alter function public.event_inbox() rename to inbox_before_dismissals;
alter function public.inbox_before_dismissals() set schema private;
revoke all on function private.inbox_before_dismissals() from public,anon,authenticated;
create function public.event_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 return coalesce((select jsonb_agg(item order by (item->>'created_at')::timestamptz desc,item->>'id') from
 (select distinct on (value->>'id') value as item from jsonb_array_elements(private.inbox_before_dismissals())
 where not exists(select 1 from private.event_dismissals d where d.user_id=caller and d.event_id=(value->>'id')::uuid)) events),'[]');
end $$;
create function public.dismiss_event(eid uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if exists(select 1 from private.event_dismissals where user_id=caller and event_id=eid) then return;end if;
 if not exists(select 1 from jsonb_array_elements(private.inbox_before_dismissals()) e where e->>'id'=eid::text) then raise exception 'not_authorized';end if;
 insert into private.event_dismissals(user_id,event_id) values(caller,eid) on conflict do nothing;
 perform private.notify_users(array[caller]);
end $$;
revoke all on function public.event_inbox(),public.dismiss_event(uuid) from public,anon;
grant execute on function public.event_inbox(),public.dismiss_event(uuid) to authenticated;
create function public.own_active_sos() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 return (select e from jsonb_array_elements(private.inbox_before_dismissals()) e
 where e->>'kind'='sos' and e->>'sender_id'=caller::text limit 1);
end $$;
revoke all on function public.own_active_sos() from public,anon;
grant execute on function public.own_active_sos() to authenticated;
-- Pending push must not reinterpret a dismissal as a fresh reception.
alter function public.push_job_authorized(uuid) rename to push_authorized_before_dismissals;
alter function public.push_authorized_before_dismissals(uuid) set schema private;
revoke all on function private.push_authorized_before_dismissals(uuid) from public,anon,authenticated,service_role;
create function public.push_job_authorized(job uuid) returns boolean language sql security definer set search_path='' as $$
 select private.push_authorized_before_dismissals(job) and not exists(
 select 1 from private.push_outbox q join private.event_dismissals d on d.user_id=q.recipient and d.event_id=q.event_id where q.id=job);
$$;
revoke all on function public.push_job_authorized(uuid) from public,anon,authenticated;
grant execute on function public.push_job_authorized(uuid) to service_role;
update private.app_bootstrap set latest_version_code=15,latest_version_name='0.46' where latest_version_code<=15;
notify pgrst, 'reload schema';
commit;
