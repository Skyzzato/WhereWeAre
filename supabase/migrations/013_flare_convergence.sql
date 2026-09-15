begin;
alter table public.meeting_points add column if not exists radius_m integer not null default 100 check(radius_m between 25 and 1000);
alter table public.meeting_points add column if not exists completed_at timestamptz;

-- Every derived value uses the same precision grant as the position itself.
create or replace function private.flare_participant(mid uuid,owner_id uuid,viewer_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare m public.meeting_points; l public.latest_locations; radius int; lat float8; lon float8; distance float8; uncertainty float8; state text;
begin
 select * into m from public.meeting_points where id=mid;
 if exists(select 1 from private.app_events e where e.sender_id=owner_id and e.kind='checkin'
  and e.payload->>'checkin_type'='arrived' and e.created_at>=m.created_at and e.expires_at>statement_timestamp()
  and exists(select 1 from private.event_recipients r where r.event_id=e.id and r.meeting_id=mid)
  and (owner_id=viewer_id or exists(select 1 from private.event_recipients r where r.event_id=e.id and r.user_id=viewer_id and r.meeting_id=mid))) then
  return jsonb_build_object('user_id',owner_id,'state','arrived','explicit',true);
 end if;
 radius:=private.effective_precision(owner_id,viewer_id);
 select x.* into l from public.latest_locations x join public.sharing_status s on s.user_id=x.user_id
  join public.profiles p on p.id=x.user_id where x.user_id=owner_id and s.is_sharing and s.session_id=x.share_session
  and not p.deleting and x.recorded_at>=statement_timestamp()-make_interval(secs=>p.visibility_seconds);
 if radius is null or l.user_id is null then return jsonb_build_object('user_id',owner_id,'state','unavailable'); end if;
 lat:=l.latitude;lon:=l.longitude;
 if radius>0 then select a.latitude,a.longitude into lat,lon from private.approximate_location(owner_id,lat,lon,radius) a; end if;
 -- Great-circle distance, clamped against floating point rounding.
 distance:=6371000*2*asin(sqrt(least(1.0,greatest(0.0,power(sin(radians(lat-m.latitude)/2),2)+cos(radians(lat))*cos(radians(m.latitude))*power(sin(radians(lon-m.longitude)/2),2)))));
 uncertainty:=case when radius>0 then radius else l.accuracy end;
 state:=case when l.recorded_at<statement_timestamp()-interval '2 minutes' or l.recorded_at>statement_timestamp()+interval '30 seconds' then 'stale'
  when distance+uncertainty<=m.radius_m then 'arrived' when greatest(0,distance-uncertainty)<=m.radius_m then 'uncertain' else 'approaching' end;
 return jsonb_build_object('user_id',owner_id,'state',state,'distance_m',round(distance::numeric),'precision_m',radius,'recorded_at',l.recorded_at,'explicit',false);
end $$;
revoke all on function private.flare_participant(uuid,uuid,uuid) from public,anon,authenticated;

create or replace function private.complete_flare(mid uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 -- All recipients, including the creator, must be arrived under every recipient's
 -- grant. Completion must not act as an oracle for a hidden precise position.
 perform 1 from public.meeting_points where id=mid and active for update;
 if not found then return; end if;
 if exists(select 1 from public.meeting_recipients where meeting_id=mid)
 and not exists(select 1 from public.meeting_recipients a cross join public.meeting_recipients b
  where a.meeting_id=mid and b.meeting_id=mid and private.flare_participant(mid,a.user_id,b.user_id)->>'state'<>'arrived') then
  update public.meeting_points set active=false,completed_at=clock_timestamp(),removed_at=clock_timestamp() where id=mid;
  perform private.meeting_notice(mid,'removed');
 end if;
end $$;
revoke all on function private.complete_flare(uuid) from public,anon,authenticated;

create or replace function private.flare_position_changed() returns trigger language plpgsql security definer set search_path='' as $$
declare mid uuid;
begin
 for mid in select m.id from public.meeting_points m join public.meeting_recipients r on r.meeting_id=m.id where m.active and r.user_id=new.user_id loop
  perform private.complete_flare(mid);
 end loop;
 return new;
end $$;
revoke all on function private.flare_position_changed() from public,anon,authenticated;
drop trigger if exists flare_position_changed on public.latest_locations;
create trigger flare_position_changed after insert or update on public.latest_locations for each row execute function private.flare_position_changed();

create or replace function private.flare_checkin_changed() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if new.meeting_id is not null then perform private.complete_flare(new.meeting_id); end if;
 return new;
end $$;
revoke all on function private.flare_checkin_changed() from public,anon,authenticated;
drop trigger if exists flare_checkin_changed on private.event_recipients;
create trigger flare_checkin_changed after insert on private.event_recipients for each row execute function private.flare_checkin_changed();

create or replace function public.meeting_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); mid uuid;
begin
 for mid in select id from public.meeting_points where active and public.can_read_meeting(id) loop perform private.complete_flare(mid); end loop;
 return coalesce((select jsonb_agg(to_jsonb(m)||jsonb_build_object('creator_name',p.display_name,
 'recipients',coalesce((select jsonb_agg(r.user_id) from public.meeting_recipients r where r.meeting_id=m.id and (r.user_id=caller or public.can_read_profile(r.user_id))),'[]'),
 'progress',coalesce((select jsonb_agg(private.flare_participant(m.id,r.user_id,caller)||jsonb_build_object('name',u.display_name))
 from public.meeting_recipients r join public.profiles u on u.id=r.user_id where r.meeting_id=m.id and (r.user_id=caller or public.can_read_profile(r.user_id))),'[]')))
 from public.meeting_points m join public.profiles p on p.id=m.creator_id where public.can_read_meeting(m.id) and (m.active or m.removed_at>statement_timestamp()-interval '1 day')),'[]');
end $$;
update private.app_bootstrap set features=features||'{"flare_convergence":true}'::jsonb;
commit;
