import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto},onNotice:n=>console.log(n.message)});
try {
 for(const file of ['supabase/tests/local_bootstrap.sql','supabase/migrations/001_initial_schema.sql','supabase/tests/security.sql','supabase/migrations/002_v0_2.sql','supabase/migrations/003_storage_upload_guard.sql','supabase/tests/security_v02.sql','supabase/tests/storage_guard.sql']) {
   console.log('RUN '+file);await db.exec(readFileSync(file,'utf8').replace(/^\\set.*$/gm,''));
 }
 await db.exec('create role service_role nologin bypassrls');
 for(const file of ['supabase/migrations/004_v0_3.sql','supabase/tests/security_v03.sql',...(process.argv.includes('--v031') ? ['supabase/migrations/005_v0_31.sql','supabase/tests/security_v031.sql','supabase/tests/security_v03.sql'] : []),...(process.argv.includes('--v032') ? ['supabase/migrations/005_v0_31.sql','supabase/migrations/006_v0_32.sql'] : [])]) {
   console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
 }
 console.log('ALL SQL AND REGRESSION TESTS PASSED'+(process.argv.includes('--v032') ? ' (v0.32)' : process.argv.includes('--v031') ? ' (v0.31)' : ' (v0.3)'));
 if(process.argv.includes('--v033') || process.argv.includes('--v04')) {
   for(const file of ['supabase/migrations/005_v0_31.sql','supabase/tests/security_v031.sql','supabase/migrations/006_v0_32.sql','supabase/migrations/007_v0_33.sql','supabase/migrations/007_v0_33.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
     console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
   }
   console.log('ALL v0.33 SQL AND REGRESSION TESTS PASSED');
   if(process.argv.includes('--sharing-recovery') || process.argv.includes('--v04')) {
     await db.exec(readFileSync('supabase/tests/sharing_recovery.sql','utf8'));
     console.log('ALL SHARING RECOVERY TESTS PASSED');
   }
   if(process.argv.includes('--v04')) {
     for(const file of ['supabase/migrations/008_device_status.sql','supabase/migrations/008_device_status.sql','supabase/tests/device_status.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/009_shared_precision.sql','supabase/migrations/009_shared_precision.sql','supabase/tests/shared_precision.sql','supabase/tests/device_status.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/010_location_requests.sql','supabase/migrations/010_location_requests.sql','supabase/tests/location_requests.sql','supabase/tests/shared_precision.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/011_checkins_events.sql','supabase/migrations/011_checkins_events.sql','supabase/tests/checkins.sql','supabase/tests/location_requests.sql','supabase/tests/shared_precision.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/012_temporary_groups.sql','supabase/migrations/012_temporary_groups.sql','supabase/tests/temporary_groups.sql','supabase/tests/checkins.sql','supabase/tests/location_requests.sql','supabase/tests/shared_precision.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/013_flare_convergence.sql','supabase/migrations/013_flare_convergence.sql','supabase/tests/flare_convergence.sql','supabase/tests/temporary_groups.sql','supabase/tests/checkins.sql','supabase/tests/location_requests.sql','supabase/tests/shared_precision.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/014_places_rules.sql','supabase/migrations/014_places_rules.sql','supabase/tests/places_rules.sql','supabase/tests/flare_convergence.sql','supabase/tests/checkins.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/015_sos.sql','supabase/migrations/015_sos.sql','supabase/tests/sos.sql','supabase/tests/places_rules.sql','supabase/tests/checkins.sql','supabase/tests/flare_convergence.sql','supabase/tests/security_v033.sql','supabase/tests/security_v03.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     for(const file of ['supabase/migrations/016_finite_group_expiry.sql','supabase/migrations/016_finite_group_expiry.sql','supabase/tests/finite_group_expiry.sql','supabase/tests/temporary_groups.sql','supabase/tests/sos.sql','supabase/tests/location_requests.sql','supabase/tests/shared_precision.sql']) {
       console.log('RUN '+file);await db.exec(readFileSync(file,'utf8'));
     }
     console.log('ALL v0.4 IMPLEMENTED MIGRATION TESTS PASSED');
   }
 }
} catch(error) {console.error(error.message,error.where ?? '',error.detail ?? '');process.exitCode=1;}
finally {await db.close();}
