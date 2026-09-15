begin;
insert into auth.users(id,raw_user_meta_data) values('00000000-0000-0000-0000-000000000001','{"display_name":"Owner"}');
set local role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',true);
do $$ begin
 begin
  perform public.create_group_timed('Invalid expiry','G','infinity'::timestamptz);
  raise exception 'infinite expiry accepted';
 exception when check_violation then null;end;
 if exists(select 1 from public.groups where name='Invalid expiry') then raise exception 'failed creation left a group';end if;
end $$;
reset role;
rollback;
