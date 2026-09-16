import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync,readdirSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto}});
try {
 await db.exec(readFileSync('supabase/tests/local_bootstrap.sql','utf8'));
 await db.exec('create role service_role nologin bypassrls');
 for(const file of readdirSync('supabase/migrations').filter(x=>x.endsWith('.sql')).sort()) {
  console.log('MIGRATION '+file);await db.exec(readFileSync('supabase/migrations/'+file,'utf8'));
 }
 for(const file of ['v043.sql','sos.sql','nearby_sos.sql','nearby_limits.sql','places_rules.sql','checkins.sql','temporary_groups.sql','shared_precision.sql','location_requests.sql']) {
  console.log('TEST '+file);await db.exec(readFileSync('supabase/tests/'+file,'utf8'));
 }
 // Concurrent callers at the API boundary; PGlite serializes transactions on one connection.
 // The advisory lock still requires validation with independent production PostgreSQL sessions.
 await db.exec(`insert into auth.users(id,raw_user_meta_data) values ('00000000-0000-0000-0000-000000000001','{"display_name":"Concurrency test"}');
 set role authenticated;select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000001',false);`);
 await Promise.all(Array.from({length:12},()=>db.query(`select public.send_sos_v042('80000000-0000-0000-0000-000000000001','help','{}','{}',46,11,10,now(),true)`)));
 await db.exec('reset role');
 const counts=await db.query('select count(*)::int n from private.app_events');
 if(counts.rows[0].n!==1) throw Error('Concurrent retries duplicated SOS');
 console.log('12 concurrent API retries: one event (single PGlite connection)');
 console.log('v0.43 SQL TESTS PASSED');
} catch(e) {console.error(e.message,e.where ?? '',e.detail ?? '');process.exitCode=1;}
finally {await db.close();}
