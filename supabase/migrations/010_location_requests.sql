begin;
-- One request lifecycle, two explicit purposes. Location requests never grant
-- the reciprocal consent of a new personal connection.
alter table public.share_requests add column if not exists purpose text not null default 'connection' check(purpose in ('connection','location'));
drop index if exists public.one_pending_pair;
create unique index if not exists one_pending_connection_pair on public.share_requests
 (least(sender_id,receiver_id),greatest(sender_id,receiver_id)) where status='pending' and purpose='connection';
create unique index if not exists one_pending_location_request on public.share_requests(sender_id,receiver_id) where status='pending' and purpose='location';
alter table private.push_outbox alter column meeting_id drop not null;
alter table private.push_outbox add column if not exists request_id uuid references public.share_requests(id) on delete cascade;
alter table private.push_outbox drop constraint if exists push_outbox_kind_check;
alter table private.push_outbox add constraint push_outbox_kind_check check(kind in ('created','removed','location_request'));
alter table private.push_outbox drop constraint if exists push_outbox_target_check;
alter table private.push_outbox add constraint push_outbox_target_check check(
 (kind in ('created','removed') and meeting_id is not null and request_id is null) or
 (kind='location_request' and request_id is not null and meeting_id is null));
create unique index if not exists one_request_push on private.push_outbox(recipient,request_id,kind) where request_id is not null;

create or replace function public.request_location(person uuid) returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); rid uuid;
begin
 if caller=person or not private.related(caller,person) or not exists(select 1 from public.profiles where id=person and not deleting) then raise exception 'not_authorized'; end if;
 perform 1 from public.profiles where id=caller for update;
 perform private.pair_lock(caller,person);
 update public.share_requests set status='cancelled',responded_at=now() where sender_id=caller and receiver_id=person and purpose='location' and status='pending' and created_at<now()-interval '24 hours';
 select id into rid from public.share_requests where sender_id=caller and receiver_id=person and purpose='location' and status='pending';
 if rid is not null then return rid; end if;
 if exists(select 1 from public.share_requests where sender_id=caller and receiver_id=person and purpose='location' and created_at>now()-interval '10 minutes') then raise exception 'request_cooldown'; end if;
 if (select count(*) from public.share_requests where sender_id=caller and purpose='location' and created_at>now()-interval '1 hour')>=30 then raise exception 'request_cooldown'; end if;
 insert into public.share_requests(sender_id,receiver_id,purpose) values(caller,person,'location') returning id into rid;
 insert into private.push_outbox(recipient,request_id,kind) values(person,rid,'location_request') on conflict do nothing;
 perform private.notify_users(array[caller,person]);
 return rid;
end $$;
create or replace function public.respond_location_request(request_id uuid,accept boolean) returns boolean language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.share_requests;
begin
 select * into r from public.share_requests where id=request_id and purpose='location';
 if r.id is null or r.receiver_id<>caller then raise exception 'not_authorized'; end if;
 perform private.pair_lock(r.sender_id,r.receiver_id);
 select * into r from public.share_requests where id=request_id for update;
 if r.status<>'pending' then return r.status='accepted'; end if;
 if r.created_at<now()-interval '24 hours' or not private.related(caller,r.sender_id) then raise exception 'request_expired'; end if;
 update public.share_requests set status=case when accept then 'accepted'::public.request_status else 'rejected'::public.request_status end,responded_at=now() where id=request_id;
 if accept then
  insert into public.location_shares(owner_id,viewer_id,enabled) values(caller,r.sender_id,true)
  on conflict(owner_id,viewer_id) do update set enabled=true;
 end if;
 perform private.notify_users(array[r.sender_id,r.receiver_id]);
 return accept;
end $$;
create or replace function public.location_request_inbox() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user();
begin return coalesce((select jsonb_agg(to_jsonb(r)||jsonb_build_object('sender_name',p.display_name))
 from public.share_requests r join public.profiles p on p.id=r.sender_id
 where r.purpose='location' and r.receiver_id=caller and r.status='pending' and r.created_at>=now()-interval '24 hours'
 and private.related(caller,r.sender_id)),'[]'); end $$;
revoke all on function public.request_location(uuid),public.respond_location_request(uuid,boolean),public.location_request_inbox() from public,anon;
grant execute on function public.request_location(uuid),public.respond_location_request(uuid,boolean),public.location_request_inbox() to authenticated;

create or replace function public.send_share_request(code text) returns uuid language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); target uuid; request uuid;
begin
 target:=private.lookup(code); if target is null then return null; end if;
 perform private.pair_lock(caller,target);
 select id into request from public.share_requests where status='pending' and purpose='connection' and
 least(sender_id,receiver_id)=least(caller,target) and greatest(sender_id,receiver_id)=greatest(caller,target);
 if request is not null then return request; end if;
 if exists(select 1 from public.location_shares where owner_id=caller and viewer_id=target) then raise exception 'already_connected'; end if;
 insert into public.share_requests(sender_id,receiver_id) values(caller,target) returning id into request;
 return request;
end $$;
create or replace function public.respond_to_share_request(request_id uuid,accept boolean) returns void language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); r public.share_requests;
begin
 select * into r from public.share_requests where id=request_id;
 if r.id is null or r.receiver_id<>caller or r.purpose<>'connection' then raise exception 'not_authorized'; end if;
 perform private.pair_lock(r.sender_id,r.receiver_id);
 select * into r from public.share_requests where id=request_id for update;
 if r.status<>'pending' then return; end if;
 update public.share_requests set status=case when accept then 'accepted'::public.request_status else 'rejected'::public.request_status end,responded_at=now() where id=request_id;
 if accept then
  insert into public.location_shares(owner_id,viewer_id,enabled) values(r.sender_id,r.receiver_id,true),(r.receiver_id,r.sender_id,true)
  on conflict(owner_id,viewer_id) do update set enabled=true;
 end if;
end $$;
create or replace function public.resolve_invite_link(token text,confirm boolean default false) returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); link private.invite_links; rid uuid; label text;
begin
 if not private.consume_lookup() then return null; end if;
 select * into link from private.invite_links where token_hash=encode(extensions.digest(token,'sha256'),'hex') and expires_at>now();
 if link.token_hash is null or link.owner_id=caller then return null; end if;
 if link.kind='group' then
  select name into label from public.groups where id=link.group_id;
  if confirm then perform private.request_group(link.group_id,caller,null); end if;
 else
  select display_name into label from public.profiles where id=link.owner_id;
  if confirm then
   perform private.pair_lock(caller,link.owner_id);
   if not exists(select 1 from public.location_shares where owner_id=caller and viewer_id=link.owner_id) then
    select id into rid from public.share_requests where least(sender_id,receiver_id)=least(caller,link.owner_id) and greatest(sender_id,receiver_id)=greatest(caller,link.owner_id) and status='pending' and purpose='connection';
    if rid is null then insert into public.share_requests(sender_id,receiver_id) values(caller,link.owner_id); end if;
   end if;
  end if;
 end if;
 return jsonb_build_object('kind',link.kind,'name',label);
end $$;
create or replace function public.claim_push_batch() returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 with batch as (select id from private.push_outbox where sent_at is null and attempts<8 and (leased_until is null or leased_until<now()) order by created_at for update skip locked limit 50),
 leased as (update private.push_outbox q set leased_until=now()+interval '5 minutes',attempts=attempts+1 from batch where q.id=batch.id returning q.*)
 select coalesce(jsonb_agg(jsonb_build_object('id',q.id,'recipient',q.recipient,'meeting_id',q.meeting_id,'request_id',q.request_id,'kind',q.kind,
 'tokens',coalesce((select jsonb_agg(t.token) from public.device_tokens t where t.user_id=q.recipient),'[]'))),'[]') into result from leased q;
 return result;
end $$;
-- Keep the legacy metadata shape and hide location requests from old clients.
-- A private copy keeps the complete existing metadata implementation intact.
do $$ begin
 if to_regprocedure('private.metadata_before_location_requests()') is null then
  alter function public.app_metadata() rename to metadata_before_location_requests;
  alter function public.metadata_before_location_requests() set schema private;
 end if;
end $$;
revoke all on function private.metadata_before_location_requests() from public,anon,authenticated;
create or replace function public.app_metadata() returns jsonb language plpgsql security definer set search_path='' as $$
declare caller uuid:=private.require_user(); data jsonb;
begin
 data:=private.metadata_before_location_requests();
 return data||jsonb_build_object('location_requests_available',true,'location_requests',public.location_request_inbox(),
 'requests',coalesce((select jsonb_agg(r) from public.share_requests r where purpose='connection' and ((sender_id=caller and not sender_hidden) or (receiver_id=caller and not receiver_hidden))),'[]'));
end $$;
revoke all on function public.app_metadata() from public,anon;
grant execute on function public.app_metadata() to authenticated;
update private.app_bootstrap set features=features||'{"location_requests":true}'::jsonb;
commit;
