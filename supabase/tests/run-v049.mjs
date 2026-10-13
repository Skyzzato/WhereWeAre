import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync,readdirSync,writeFileSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto}});
try {
 await db.exec(readFileSync('supabase/tests/local_bootstrap.sql','utf8'));
 await db.exec('create role service_role nologin bypassrls');
 // pg_net/cron activation (027) is verified live; these extensions are not in PGlite.
 const migrations=readdirSync('supabase/migrations').filter(x=>/^\d{3}_.*\.sql$/.test(x) && Number(x.slice(0,3))<=28 && !/^(023|027)_/.test(x)).sort();
 for(const f of migrations) {console.log('MIGRATION '+f);await db.exec(readFileSync('supabase/migrations/'+f,'utf8'));}
 for(const f of ['device_status.sql','sharing_recovery.sql','shared_precision.sql','location_requests.sql','checkins.sql','temporary_groups.sql','finite_group_expiry.sql','flare_convergence.sql','places_rules.sql','v043.sql','remote_v044_smoke.sql','v046.sql','version_policy.sql','v049.sql']) {
  console.log('TEST '+f);await db.exec(readFileSync('supabase/tests/'+f,'utf8').replace(/^\\set.*$/gm,''));
 }
 // Disposable database only: mirror the currently supported live version.
 await db.exec("update private.app_bootstrap set latest_version_code=18,latest_version_name='0.49',minimum_supported_version_code=18 where singleton");
 console.log('TEST v049_read_only.sql');
 await db.exec(readFileSync('supabase/tests/v049_read_only.sql','utf8'));
 console.log('ALL v0.49 SQL TESTS PASSED');
} catch(e) {console.error(e.message,e.where??'',e.detail??'');process.exitCode=1;} finally {await db.close();}
