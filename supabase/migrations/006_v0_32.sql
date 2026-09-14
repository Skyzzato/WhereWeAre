begin;
-- Optional style metadata; NULL/old rows remain compatible and render as style 1.
alter table public.meeting_points add column if not exists flare_style_id integer not null default 1 check(flare_style_id between 1 and 30);
create or replace function public.create_meeting_styled(mid uuid,lat double precision,lon double precision,all_people boolean default false,people uuid[] default '{}',group_ids uuid[] default '{}',flare_style integer default 1) returns uuid
language plpgsql security definer set search_path='' as $$
declare result uuid; caller uuid:=private.require_user();
begin
 flare_style:=coalesce(flare_style,1); if flare_style<1 or flare_style>30 then flare_style:=1; end if;
 result:=public.create_meeting(mid,lat,lon,all_people,people,group_ids);
 update public.meeting_points set flare_style_id=flare_style where id=result and creator_id=caller;
 perform private.notify_users(array(select user_id from public.meeting_recipients where meeting_id=result));
 return result;
end $$;
revoke all on function public.create_meeting_styled(uuid,double precision,double precision,boolean,uuid[],uuid[],integer) from public,anon;
grant execute on function public.create_meeting_styled(uuid,double precision,double precision,boolean,uuid[],uuid[],integer) to authenticated;
update private.app_bootstrap set latest_version_code=6,latest_version_name='0.32';
commit;
