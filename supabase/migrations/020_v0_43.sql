begin;
-- Consent remains permanent. Existing false rows are never re-enabled.
-- Only live availability belongs to the authenticated session.
alter table private.nearby_volunteers add column if not exists availability_session uuid;
create or replace function public.end_nearby_availability() returns void language plpgsql security definer set search_path='' as $$
begin
 perform pg_advisory_xact_lock(420018);
 update private.nearby_volunteers set available_until=null,acquired_at=null,latitude=null,longitude=null,accuracy=null,received_at=null
 where user_id=private.require_user() and (availability_session is null or availability_session=nullif(auth.jwt()->>'session_id','')::uuid);
end $$;
create or replace function public.refresh_nearby_sos(lat float8,lon float8,accuracy_m float8,fix_at timestamptz) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();c private.nearby_config;
begin
 perform pg_advisory_xact_lock(420018);select * into c from private.nearby_config;
 if not c.enabled then raise exception 'nearby_configuration';end if;
 if (lat between -90 and 90 and lon between -180 and 180 and accuracy_m between 0 and c.max_accuracy_m
 and fix_at<=clock_timestamp()+interval '30 seconds' and fix_at>=clock_timestamp()-make_interval(secs=>c.fix_seconds)) is not true then raise exception 'nearby_fresh_fix_required';end if;
 update private.nearby_volunteers set availability_session=nullif(auth.jwt()->>'session_id','')::uuid,latitude=lat,longitude=lon,accuracy=accuracy_m,acquired_at=fix_at,received_at=clock_timestamp(),
 available_until=clock_timestamp()+make_interval(secs=>c.availability_seconds)
 where user_id=caller and opted_in and (acquired_at is null or acquired_at<fix_at);
 if not found then raise exception 'nearby_consent_or_new_fix_required';end if;
end $$;

-- Read-only recovery checks ownership, including closed or expired SOS tombstones.
create or replace function public.sos_registered(eid uuid) returns boolean language sql security definer set search_path='' as $$
 select exists(select 1 from private.app_events where id=eid and sender_id=private.require_user() and kind='sos');
$$;
alter table private.saved_places add column if not exists emoji text not null default '📍' check(char_length(emoji)<=12);
alter table private.saved_places drop constraint if exists saved_places_slot_check;
alter table private.saved_places add constraint saved_places_slot_check check(slot>=0);
create or replace function private.place_name_key(value text) returns text language sql immutable strict set search_path='' as $$
 select lower(btrim(regexp_replace(value, '\s+', ' ', 'g')));
$$;
-- Unique index arbitrates concurrent writers. Grandfather pre-existing duplicates
-- with NULL keys, preserving their names; the guard still checks all legacy rows.
alter table private.saved_places add column name_key text;
with ranked as (select id,row_number() over(partition by owner_id,private.place_name_key(name) order by id) n from private.saved_places)
update private.saved_places p set name_key=private.place_name_key(p.name) from ranked r where r.id=p.id and r.n=1;
create unique index saved_places_owner_name_key on private.saved_places(owner_id,name_key);
-- Preserve pre-existing duplicate names and their rules. They remain editable;
-- serialize writes per owner and enforce normalization for every new or changed name.
create or replace function private.guard_place_name() returns trigger language plpgsql security definer set search_path='' as $$
begin
 perform 1 from public.profiles where id=new.owner_id for update;
 if exists(select 1 from private.saved_places p where p.owner_id=new.owner_id and p.id<>new.id and private.place_name_key(p.name)=private.place_name_key(new.name)) then raise exception 'place_duplicate';end if;
 new.name_key:=private.place_name_key(new.name);
 return new;
end $$;
create trigger guard_place_name before insert or update of name,owner_id on private.saved_places for each row execute function private.guard_place_name();
create or replace function public.save_place_v043(pid uuid,slot_number int,label text,lat float8,lon float8,radius int,icon text) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from private.saved_places where id=pid and owner_id<>caller) then raise exception 'not_authorized'; end if;
 if pid is null or slot_number<0 or slot_number is null or length(trim(label)) not between 1 and 60 or label is null
 or lat is null or lat not between -90 and 90 or lon is null or lon not between -180 and 180 or radius is null or radius not between 50 and 5000 then raise exception 'invalid_form'; end if;
 if icon is null or char_length(icon)>12 then raise exception 'invalid_form';end if;
 label:=btrim(regexp_replace(label, '\s+', ' ', 'g'));
 if exists(select 1 from private.saved_places where owner_id=caller and id<>pid and private.place_name_key(name)=private.place_name_key(label)) then raise exception 'place_duplicate';end if;
 if not exists(select 1 from private.saved_places where id=pid) then
  select coalesce(max(slot)+1,0) into slot_number from private.saved_places where owner_id=caller;
 end if;
 insert into private.saved_places(id,owner_id,slot,name,latitude,longitude,radius_m,emoji) values(pid,caller,slot_number,label,lat,lon,radius,icon)
 on conflict(id) do update set emoji=excluded.emoji,name=excluded.name,latitude=excluded.latitude,longitude=excluded.longitude,radius_m=excluded.radius_m where saved_places.owner_id=caller;
 update private.place_rules set stable_inside=null,candidate=null,candidate_at=null,last_fix=null where place_id=pid;
end $$;

-- Keep the old client signature, preserving its existing icon.
create or replace function public.save_place(pid uuid,slot_number int,label text,lat float8,lon float8,radius int) returns void language sql security definer set search_path='' as $$
 select public.save_place_v043(pid,slot_number,label,lat,lon,radius,coalesce((select emoji from private.saved_places where id=pid and owner_id=private.require_user()),'📍'));
$$;
revoke all on function public.sos_registered(uuid),public.end_nearby_availability(),public.save_place_v043(uuid,int,text,float8,float8,int,text) from public,anon;
grant execute on function public.sos_registered(uuid),public.end_nearby_availability(),public.save_place_v043(uuid,int,text,float8,float8,int,text) to authenticated;
revoke all on function private.place_name_key(text),private.guard_place_name() from public,anon,authenticated;

alter table private.app_events add column source_place_id uuid references private.saved_places(id) on delete set null;
create or replace function private.evaluate_place_rules() returns trigger language plpgsql security definer set search_path='' as $$
declare r private.place_rules; p private.saved_places; precision int; lat float8; lon float8; uncertainty float8; distance float8; inside boolean;
 eid uuid; target uuid; content jsonb;
begin
 if new.recorded_at<statement_timestamp()-interval '2 minutes' or new.recorded_at>statement_timestamp()+interval '30 seconds'
 or not exists(select 1 from public.sharing_status s where s.user_id=new.user_id and s.is_sharing and s.session_id=new.share_session) then return new; end if;
 for r in select * from private.place_rules where enabled and subject_id=new.user_id for update loop
  if r.last_fix is not null and new.recorded_at<=r.last_fix then continue; end if;
  precision:=private.effective_precision(new.user_id,r.owner_id);
  if precision is null then
   update private.place_rules set stable_inside=null,candidate=null,candidate_at=null,last_fix=new.recorded_at where id=r.id;continue;
  end if;
  select * into p from private.saved_places where id=r.place_id;
  lat:=new.latitude;lon:=new.longitude;uncertainty:=new.accuracy;
  if precision>0 then select a.latitude,a.longitude into lat,lon from private.approximate_location(new.user_id,lat,lon,precision) a;uncertainty:=precision;end if;
  distance:=6371000*2*asin(sqrt(least(1.0,greatest(0.0,power(sin(radians(lat-p.latitude)/2),2)+cos(radians(lat))*cos(radians(p.latitude))*power(sin(radians(lon-p.longitude)/2),2)))));
  inside:=case when distance+uncertainty<=p.radius_m then true when distance-uncertainty>p.radius_m+30 then false else null end;
  if inside is null then update private.place_rules set candidate=null,candidate_at=null,last_fix=new.recorded_at where id=r.id;continue;end if;
  if r.last_session is distinct from new.share_session or r.stable_inside is null or r.last_fix is null or new.recorded_at-r.last_fix>interval '10 minutes' then
   update private.place_rules set stable_inside=inside,candidate=null,candidate_at=null,last_fix=new.recorded_at,last_session=new.share_session where id=r.id;continue;
  end if;
  if inside=r.stable_inside then update private.place_rules set candidate=null,candidate_at=null,last_fix=new.recorded_at where id=r.id;continue;end if;
  if r.candidate is distinct from inside then
   update private.place_rules set candidate=inside,candidate_at=new.recorded_at,last_fix=new.recorded_at where id=r.id;continue;
  end if;
  update private.place_rules set last_fix=new.recorded_at where id=r.id;
  if new.recorded_at-r.candidate_at<interval '30 seconds' then continue;end if;
  update private.place_rules set stable_inside=inside,candidate=null,candidate_at=null where id=r.id;
  if (inside and r.kind='exit') or (not inside and r.kind<>'exit') or r.last_sent>statement_timestamp()-interval '15 minutes' then continue;end if;
  eid:=gen_random_uuid();
  content:=jsonb_build_object('place_name',p.name,'transition',case when inside then 'enter' else 'exit' end,
   'subject_name',(select display_name from public.profiles where id=new.user_id),'observed_at',new.recorded_at);
  insert into private.app_events(id,sender_id,kind,payload,source_place_id) values(eid,r.owner_id,'place',content,p.id);
  foreach target in array r.recipients loop
   if target=r.owner_id or private.related(r.owner_id,target) then
    insert into private.event_recipients(event_id,user_id,payload,personal) values(eid,target,content,true) on conflict do nothing;
    insert into private.push_outbox(recipient,event_id,kind) values(target,eid,'place') on conflict do nothing;
   end if;
  end loop;
  update private.place_rules set last_sent=clock_timestamp() where id=r.id;
  perform private.notify_users(r.recipients||array[r.owner_id]);
 end loop;
 return new;
end $$;

create or replace function public.remove_place(pid uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 -- Match evaluation lock order: no in-flight transition can enqueue after deletion.
 perform 1 from private.place_rules where place_id=pid and owner_id=caller for update;
 delete from private.push_outbox q using private.app_events e where q.event_id=e.id and e.source_place_id=pid and e.sender_id=caller and q.sent_at is null;
 delete from private.saved_places where id=pid and owner_id=caller;
end $$;

update private.app_bootstrap set latest_version_code=12,latest_version_name='0.43' where latest_version_code<=12;
notify pgrst, 'reload schema';
commit;
