begin;
-- Minimum supported client remains unchanged. Never downgrade a newer deployment.
update private.app_bootstrap set latest_version_code=11,latest_version_name='0.42'
where latest_version_code<=11;
commit;
