-- Prepared locally; no live deployment authorized by repository consolidation.
-- Preserve the existing minimum: raising it requires an explicit live decision.
update private.app_bootstrap
set latest_version_code = 17,
    latest_version_name = '0.48'
where singleton = true;
