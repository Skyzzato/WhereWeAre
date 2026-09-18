import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync,readdirSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto}});
try {
 await db.exec(readFileSync('supabase/tests/local_bootstrap.sql','utf8'));
 await db.exec('create role service_role nologin bypassrls');
 const migrations=readdirSync('supabase/migrations').filter(x=>x.endsWith('.sql')).sort();
 for(const file of migrations.filter(x=>!x.startsWith('022_'))) await db.exec(readFileSync('supabase/migrations/'+file,'utf8'));
 const before=(await db.query('select minimum_supported_version_code from private.app_bootstrap')).rows;
 const policy=readFileSync('supabase/migrations/022_v0_48_version_policy.sql','utf8');
 await db.exec(policy);
 await db.exec(policy);
 const after=(await db.query('select minimum_supported_version_code from private.app_bootstrap')).rows;
 if(JSON.stringify(before)!==JSON.stringify(after)) throw Error('022 changed minimum');
 await db.exec(readFileSync('supabase/tests/version_policy.sql','utf8'));
 console.log('PASS 001–022, 022 idempotent, minimum preserved, public bootstrap anon/authenticated');
} catch(e) {console.error(e.message);process.exitCode=1;} finally {await db.close();}
