import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto},onNotice:n=>console.log(n.message)});
try {
 for(const file of ['supabase/tests/local_bootstrap.sql','supabase/migrations/001_initial_schema.sql','supabase/tests/security.sql','supabase/migrations/002_v0_2.sql','supabase/migrations/003_storage_upload_guard.sql','supabase/tests/security_v02.sql','supabase/tests/storage_guard.sql']) {
   console.log('RUN '+file);await db.exec(readFileSync(file,'utf8').replace(/^\\set.*$/gm,''));
 }
 await db.exec('create role service_role nologin bypassrls');
 for(const file of ['supabase/migrations/004_v0_3.sql','supabase/tests/security_v03.sql']) {
   console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
 }
 console.log('ALL V0.3 SQL AND REGRESSION TESTS PASSED');
} catch(error) {console.error(error.message,error.where ?? '',error.detail ?? '');process.exitCode=1;}
finally {await db.close();}
