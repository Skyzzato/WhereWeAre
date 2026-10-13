begin;
-- Hosted projects may have broader default table grants than the local fixture.
-- Keep all meeting mutations behind the authenticated transactional RPCs.
revoke insert,update,delete,truncate,references,trigger on public.meeting_points,public.meeting_recipients from public,anon,authenticated;
grant select on public.meeting_points,public.meeting_recipients to authenticated;
notify pgrst,'reload schema';
commit;
