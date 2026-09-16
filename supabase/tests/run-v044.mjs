import {execFileSync} from 'node:child_process';
import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync,readdirSync} from 'node:fs';
// Historical version and retention assertions run at their original schema stage.
execFileSync(process.execPath,['supabase/tests/run-v03.mjs','--v04'],{stdio:'inherit'});
const db=await PGlite.create({extensions:{pgcrypto}});
try {
 await db.exec(readFileSync('supabase/tests/local_bootstrap.sql','utf8'));
 await db.exec('create role service_role nologin bypassrls');
 for(const f of readdirSync('supabase/migrations').filter(x=>x.endsWith('.sql')).sort()) await db.exec(readFileSync('supabase/migrations/'+f,'utf8'));
 for(const f of ['device_status.sql','sharing_recovery.sql','shared_precision.sql','location_requests.sql','checkins.sql','temporary_groups.sql','finite_group_expiry.sql','flare_convergence.sql','places_rules.sql','sos.sql','nearby_sos.sql','nearby_limits.sql','v043.sql','remote_v044_smoke.sql']) {
  console.log('TEST '+f);await db.exec(readFileSync('supabase/tests/'+f,'utf8').replace(/^\\set.*$/gm,''));
 }
 console.log('ALL v0.44 SQL REGRESSIONS PASSED (001-020)');
} catch(e) {console.error(e.message,e.where??'',e.detail??'');process.exitCode=1;} finally {await db.close();}

