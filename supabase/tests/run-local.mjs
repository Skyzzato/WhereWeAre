// Install @electric-sql/pglite 0.5.8 into .tools/pglite/package (or npm locally).
import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto},onNotice:n=>console.log(n.message)});
try {
 for(const file of ['supabase/tests/local_bootstrap.sql','supabase/migrations/001_initial_schema.sql','supabase/tests/security.sql','supabase/migrations/002_v0_2.sql','supabase/migrations/003_storage_upload_guard.sql','supabase/tests/security_v02.sql','supabase/tests/storage_guard.sql']) {
   console.log('RUN '+file);
   await db.exec(readFileSync(file,'utf8').replace(/^\\set.*$/gm,''));
 }
 console.log('ALL SQL TESTS PASSED');
} catch(error) { console.error(error.message,error.where ?? '',error.detail ?? ''); process.exitCode=1; }
finally {await db.close();}
