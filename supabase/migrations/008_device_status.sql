begin;
-- Keep device state behind exactly the same RLS/session/consent rules as location.
alter table public.latest_locations add column if not exists battery_level integer;
alter table public.latest_locations add column if not exists location_enabled boolean;
alter table public.latest_locations add column if not exists device_status_at timestamptz;
do $$ begin
 if not exists(select 1 from pg_constraint where conrelid='public.latest_locations'::regclass and conname='battery_level_valid') then
  alter table public.latest_locations add constraint battery_level_valid check(battery_level between 0 and 100);
 end if;
end $$;
create or replace function private.clear_device_status_on_session_change()
returns trigger language plpgsql set search_path='' as $$
begin
 if new.share_session is distinct from old.share_session then
  new.battery_level:=null;new.location_enabled:=null;new.device_status_at:=null;
 end if;
 return new;
end $$;
revoke all on function private.clear_device_status_on_session_change() from public,anon,authenticated;
drop trigger if exists clear_device_status_on_session_change on public.latest_locations;
create trigger clear_device_status_on_session_change before update on public.latest_locations
for each row execute function private.clear_device_status_on_session_change();
create or replace function public.update_device_status(session uuid,battery integer,location_enabled boolean)
returns boolean language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); current_status public.sharing_status;
begin
 if battery is not null and battery not between 0 and 100 then raise exception 'invalid_battery'; end if;
 if location_enabled is null then raise exception 'invalid_location_status'; end if;
 select * into current_status from public.sharing_status where user_id=caller for update;
 if not current_status.is_sharing or current_status.session_id is distinct from session then raise exception 'sharing_stopped'; end if;
 update public.latest_locations l set battery_level=battery,location_enabled=update_device_status.location_enabled,
  device_status_at=clock_timestamp() where l.user_id=caller and l.share_session=session;
 return found;
end $$;
revoke all on function public.update_device_status(uuid,integer,boolean) from public,anon;
grant execute on function public.update_device_status(uuid,integer,boolean) to authenticated;
update private.app_bootstrap set features=features||'{"device_status":true}'::jsonb;
commit;
