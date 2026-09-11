begin;
insert into auth.users(id) values('00000000-0000-0000-0000-000000000099');
select set_config('request.jwt.claim.sub','',true);
-- Internal Storage persistence has no JWT but carries verified ownership.
insert into storage.objects(bucket_id,name,owner_id) values('avatars',
 '00000000-0000-0000-0000-000000000099/00000000-0000-0000-0000-000000000001.webp',
 '00000000-0000-0000-0000-000000000099');
update public.profiles set deleting=true where id='00000000-0000-0000-0000-000000000099';
do $$ begin
 begin
  insert into storage.objects(bucket_id,name,owner_id) values('avatars',
   '00000000-0000-0000-0000-000000000099/00000000-0000-0000-0000-000000000002.webp',
   '00000000-0000-0000-0000-000000000099');
  raise exception 'tombstone accepted upload';
 exception when raise_exception then if sqlerrm<>'not_authorized' then raise; end if; end;
 begin
  insert into storage.objects(bucket_id,name) values('avatars','missing-owner/file.webp');
  raise exception 'missing owner accepted';
 exception when insufficient_privilege then null; end;
end $$;
rollback;
