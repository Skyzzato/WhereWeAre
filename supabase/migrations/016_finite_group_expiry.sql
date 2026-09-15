begin;
-- PostgreSQL accepts infinity as a timestamp, but clients need an actual date.
-- A rejected create/edit remains atomic, including membership and notices.
alter table public.groups drop constraint if exists group_expiry_finite;
alter table public.groups add constraint group_expiry_finite check(expires_at is null or isfinite(expires_at));
commit;
