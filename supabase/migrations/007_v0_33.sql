begin;
-- A private address-book association, separate from all GPS grants.
create table if not exists public.saved_people (
 user_id uuid not null references public.profiles(id) on delete cascade,
 person_id uuid not null references public.profiles(id) on delete cascade,
 primary key(user_id,person_id),check(user_id<>person_id)
);
alter table public.saved_people enable row level security;
revoke all on public.saved_people from public,anon,authenticated;
grant select on public.saved_people to authenticated;
drop policy if exists saved_people_read on public.saved_people;
create policy saved_people_read on public.saved_people for select to authenticated using(user_id=auth.uid());
-- Profile/avatar updates must also refresh saved contacts after a group is left.
-- This only invalidates metadata; it does not extend private.related or GPS RLS.
create or replace function private.saved_profile_changed() returns trigger language plpgsql security definer set search_path='' as $$
begin
 perform private.notify_users(array(select user_id from public.saved_people where person_id=new.id));
 return new;
end $$;
revoke all on function private.saved_profile_changed() from public,anon,authenticated;
drop trigger if exists saved_profile_notify on public.profiles;
create trigger saved_profile_notify after update on public.profiles for each row execute function private.saved_profile_changed();
create or replace function public.save_group_person(person uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 if person=caller or not private.common_group(caller,person) then raise exception 'not_authorized'; end if;
 insert into public.saved_people values(caller,person) on conflict do nothing;
 perform private.notify_users(array[caller]);
end $$;
create or replace function public.remove_connection(other_user_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin
 perform private.pair_lock(caller,other_user_id);
 delete from public.saved_people where user_id=caller and person_id=other_user_id;
 delete from public.location_shares where (owner_id=caller and viewer_id=other_user_id) or (owner_id=other_user_id and viewer_id=caller);
 perform private.notify_users(array[caller,other_user_id]);
end $$;
create or replace function public.can_read_profile(target uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and (private.related(auth.uid(),target) or exists(select 1 from public.saved_people where user_id=auth.uid() and person_id=target));
$$;
create or replace function public.contact_profiles_v03() returns table(user_id uuid,display_name text,avatar_path text,visibility_seconds int,common_group boolean,can_view boolean,update_interval_seconds int)
language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return query select p.id,p.display_name,p.avatar_path,p.visibility_seconds,private.common_group(caller,p.id),
 (p.id=caller or private.group_visible(p.id,caller) or exists(select 1 from public.location_shares s where s.owner_id=p.id and s.viewer_id=caller and s.enabled)),p.update_interval_seconds
 from public.profiles p where public.can_read_profile(p.id) and not p.deleting; end $$;
create or replace function public.request_saved_person(person uuid) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); code text;
begin
 if person=caller or not public.can_read_profile(person) then raise exception 'not_authorized'; end if;
 select invite_code into code from public.profiles where id=person;
 perform public.send_share_request(code);
end $$;
create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return jsonb_build_object(
 'profile',(select to_jsonb(p) from public.profiles p where id=caller),
 'names',coalesce((select jsonb_agg(n) from public.contact_names() n),'[]'),
 'requests',coalesce((select jsonb_agg(r) from public.share_requests r where (sender_id=caller and not sender_hidden) or (receiver_id=caller and not receiver_hidden)),'[]'),
 'shares',coalesce((select jsonb_agg(s) from public.location_shares s where owner_id=caller or viewer_id=caller),'[]'),
 'statuses',coalesce((select jsonb_agg(s) from public.sharing_status s where private.related(caller,s.user_id)),'[]'),
 'contacts',coalesce((select jsonb_agg(c) from public.contact_profiles_v03() c),'[]'),
 'groups',coalesce((select jsonb_agg(g) from public.groups g where public.is_group_member(g.id)),'[]'),
 'members',coalesce((select jsonb_agg(m) from public.group_members m where public.is_group_member(m.group_id)),'[]'),
 'saved_people',coalesce((select jsonb_agg(person_id) from public.saved_people where user_id=caller),'[]'),
 'group_requests',public.group_inbox(),'meetings',public.meeting_inbox(),'server_time',clock_timestamp()); end $$;
alter table public.meeting_points drop constraint if exists meeting_points_flare_style_id_check;
alter table public.meeting_points add constraint meeting_points_flare_style_id_check check(flare_style_id between 1 and 50);
create or replace function public.create_meeting_styled(mid uuid,lat double precision,lon double precision,all_people boolean default false,people uuid[] default '{}',group_ids uuid[] default '{}',flare_style integer default 1) returns uuid
language plpgsql security definer set search_path='' as $$
declare result uuid; caller uuid:=private.require_user();
begin
 perform 1 from public.profiles where id=caller for update;
 if exists(select 1 from public.meeting_points where id=mid and creator_id=caller) then return mid; end if;
 flare_style:=coalesce(flare_style,1);if flare_style<1 or flare_style>50 then flare_style:=1;end if;
 result:=public.create_meeting(mid,lat,lon,all_people,people,group_ids);
 update public.meeting_points set flare_style_id=flare_style where id=result and creator_id=caller;
 perform private.notify_users(array(select user_id from public.meeting_recipients where meeting_id=result));
 return result;
end $$;
revoke all on function public.save_group_person(uuid),public.request_saved_person(uuid) from public,anon;
grant execute on function public.save_group_person(uuid),public.request_saved_person(uuid) to authenticated;
update private.app_bootstrap set latest_version_code=8,latest_version_name='0.33';
commit;
