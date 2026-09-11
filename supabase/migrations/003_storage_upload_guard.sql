begin;
-- Storage authorizes the upload using RLS, then persists the object using its
-- internal connection. That connection can have no auth.uid(); owner_id is set
-- by Storage from the verified JWT. Retain the profile lock and tombstone check.
create or replace function private.avatar_write_guard() returns trigger
language plpgsql security definer set search_path='' as $$
declare available boolean; uploader text;
begin
 if new.bucket_id='avatars' then
  uploader := coalesce(auth.uid()::text,new.owner_id);
  if uploader is null or split_part(new.name,'/',1) is distinct from uploader then
   raise exception 'not_authorized' using errcode='42501';
  end if;
  select not deleting into available from public.profiles where id::text=uploader for share;
  if available is distinct from true then raise exception 'not_authorized'; end if;
 end if;
 return new;
end $$;
revoke all on function private.avatar_write_guard() from public,anon,authenticated;
commit;
