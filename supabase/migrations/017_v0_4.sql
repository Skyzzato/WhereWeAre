begin;
-- Advertise the requested release without raising the minimum supported client
-- or enabling experimental functionality.
update private.app_bootstrap set latest_version_code=9,latest_version_name='0.4';
commit;
