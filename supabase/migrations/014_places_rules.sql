begin;
create table if not exists private.saved_places(
 id uuid primary key,owner_id uuid not null references public.profiles(id) on delete cascade,
 slot int not null check(slot between 0 and 3),name text not null check(length(name) between 1 and 60),
 latitude float8 not null check(latitude between -90 and 90),longitude float8 not null check(longitude between -180 and 180),
 radius_m int not null check(radius_m between 50 and 5000),unique(owner_id,slot)
);
create table if not exists private.place_rules(
 id uuid primary key,place_id uuid not null references private.saved_places(id) on delete cascade,
 owner_id uuid not null references public.profiles(id) on delete cascade,
 subject_id uuid not null references public.profiles(id) on delete cascade,
 kind text not null check(kind in ('enter','exit','arrival')),enabled boolean not null default false,
 recipients uuid[] not null default '{}',stable_inside boolean,candidate boolean,candidate_at timestamptz,last_fix timestamptz,last_session uuid,last_sent timestamptz
);
alter table private.saved_places enable row level security;
alter table private.place_rules enable row level security;
revoke all on private.saved_places,private.place_rules from public,anon,authenticated;
drop policy if exists own_place on private.saved_places;
create policy own_place on private.saved_places to authenticated using(owner_id=auth.uid()) with check(owner_id=auth.uid());
drop policy if exists own_rule on private.place_rules;
create policy own_rule on private.place_rules to authenticated using(owner_id=auth.uid()) with check(owner_id=auth.uid());
create index if not exists rule_subject on private.place_rules(subject_id) where enabled;

create or replace function public.save_place(pid uuid,slot_number int,label text,lat float8,lon float8,radius int) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from private.saved_places where id=pid and owner_id<>caller) then raise exception 'not_authorized'; end if;
 if pid is null or slot_number not between 0 and 3 or slot_number is null or length(trim(label)) not between 1 and 60 or label is null
 or lat is null or lat not between -90 and 90 or lon is null or lon not between -180 and 180 or radius is null or radius not between 50 and 5000 then raise exception 'invalid_form'; end if;
 insert into private.saved_places values(pid,caller,slot_number,trim(label),lat,lon,radius)
 on conflict(id) do update set name=excluded.name,latitude=excluded.latitude,longitude=excluded.longitude,radius_m=excluded.radius_m where saved_places.owner_id=caller;
 update private.place_rules set stable_inside=null,candidate=null,candidate_at=null,last_fix=null where place_id=pid;
end $$;
create or replace function public.remove_place(pid uuid) returns void language plpgsql security definer set search_path='' as $$
begin delete from private.saved_places where id=pid and owner_id=private.require_user();end $$;
create or replace function public.save_place_rule(rid uuid,pid uuid,rule_kind text,subject uuid,targets uuid[],armed boolean) returns void
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform 1 from public.profiles where id=caller for update;
 if not exists(select 1 from private.saved_places where id=pid and owner_id=caller)
 or exists(select 1 from private.place_rules where id=rid and owner_id<>caller) then raise exception 'not_authorized'; end if;
 if rid is null or armed is null or rule_kind is null or rule_kind not in ('enter','exit','arrival') or subject is null then raise exception 'invalid_form'; end if;
 if rule_kind='arrival' then
  if subject=caller or (armed and private.effective_precision(subject,caller) is null) then raise exception 'not_authorized'; end if;
  targets:=array[caller];
 else
  if subject<>caller or coalesce(cardinality(targets),0) not between 1 and 50
  or exists(select 1 from unnest(targets) t where t is null or t=caller or (armed and not private.related(caller,t))) then raise exception 'not_authorized'; end if;
 end if;
 if not exists(select 1 from private.place_rules where id=rid) and (select count(*) from private.place_rules where owner_id=caller)>=20 then raise exception 'invalid_form'; end if;
 insert into private.place_rules(id,place_id,owner_id,subject_id,kind,enabled,recipients) values(rid,pid,caller,subject,rule_kind,armed,targets)
 on conflict(id) do update set place_id=excluded.place_id,subject_id=excluded.subject_id,kind=excluded.kind,enabled=excluded.enabled,recipients=excluded.recipients,
 stable_inside=null,candidate=null,candidate_at=null,last_fix=null where place_rules.owner_id=caller;
end $$;
create or replace function public.remove_place_rule(rid uuid) returns void language plpgsql security definer set search_path='' as $$
begin delete from private.place_rules where id=rid and owner_id=private.require_user();end $$;
create or replace function public.places_rules() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return jsonb_build_object(
 'places',coalesce((select jsonb_agg(to_jsonb(p)-'owner_id') from private.saved_places p where owner_id=caller),'[]'),
 'rules',coalesce((select jsonb_agg(jsonb_build_object('id',id,'place_id',place_id,'subject_id',subject_id,'kind',kind,'enabled',enabled,'recipients',recipients)) from private.place_rules where owner_id=caller),'[]'));
end $$;
revoke all on function public.save_place(uuid,int,text,float8,float8,int),public.remove_place(uuid),public.save_place_rule(uuid,uuid,text,uuid,uuid[],boolean),public.remove_place_rule(uuid),public.places_rules() from public,anon;
grant execute on function public.save_place(uuid,int,text,float8,float8,int),public.remove_place(uuid),public.save_place_rule(uuid,uuid,text,uuid,uuid[],boolean),public.remove_place_rule(uuid),public.places_rules() to authenticated;

alter table private.app_events drop constraint if exists app_events_kind_check;
alter table private.app_events add constraint app_events_kind_check check(kind in ('checkin','place'));
alter table private.push_outbox drop constraint if exists push_outbox_kind_check;
alter table private.push_outbox add constraint push_outbox_kind_check check(kind in ('created','removed','location_request','checkin','place'));
alter table private.push_outbox drop constraint if exists push_outbox_target_check;
alter table private.push_outbox add constraint push_outbox_target_check check(
 (kind in ('created','removed') and meeting_id is not null and request_id is null and event_id is null) or
 (kind='location_request' and request_id is not null and meeting_id is null and event_id is null) or
 (kind in ('checkin','place') and event_id is not null and meeting_id is null and request_id is null));

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
  insert into private.app_events(id,sender_id,kind,payload) values(eid,r.owner_id,'place',content);
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
revoke all on function private.evaluate_place_rules() from public,anon,authenticated;
drop trigger if exists evaluate_place_rules on public.latest_locations;
create trigger evaluate_place_rules after insert or update on public.latest_locations for each row execute function private.evaluate_place_rules();

do $$ begin
 if to_regprocedure('private.metadata_before_places()') is null then
  alter function public.app_metadata() rename to metadata_before_places;
  alter function public.metadata_before_places() set schema private;
 end if;
end $$;
revoke all on function private.metadata_before_places() from public,anon,authenticated;
create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
begin perform private.require_user();return private.metadata_before_places()||jsonb_build_object('places_available',true);end $$;
revoke all on function public.app_metadata() from public,anon;
grant execute on function public.app_metadata() to authenticated;
update private.app_bootstrap set features=features||'{"places_rules":true}'::jsonb;
commit;
