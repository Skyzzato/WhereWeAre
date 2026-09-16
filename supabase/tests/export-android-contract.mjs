import {PGlite} from '../../.tools/pglite/package/dist/index.js';
import {pgcrypto} from '../../.tools/pglite/package/dist/contrib/pgcrypto.js';
import {readFileSync,readdirSync,writeFileSync,mkdirSync} from 'node:fs';
const db=await PGlite.create({extensions:{pgcrypto}});
try {
 await db.exec(readFileSync('supabase/tests/local_bootstrap.sql','utf8'));
 await db.exec('create role service_role nologin bypassrls');
 for(const f of readdirSync('supabase/migrations').filter(x=>x.endsWith('.sql')).sort()) await db.exec(readFileSync('supabase/migrations/'+f,'utf8'));
 const fixture=readFileSync('supabase/tests/remote_v044_smoke.sql','utf8');
 await db.exec(fixture.slice(0,fixture.indexOf('select public.close_sos')));
 const {rows}=await db.query('select public.app_metadata() as metadata');
 mkdirSync('app/src/test/resources/contract',{recursive:true});
 for(const [name,value] of Object.entries(rows[0])) writeFileSync(`app/src/test/resources/contract/${name}.json`,JSON.stringify(value,null,2)+'\n');
 // A nearby-only SOS has no ordinary event_recipients rows: jsonb_agg returns null.
 await db.exec("select set_config('request.jwt.claim.sub','f0440000-0000-0000-0000-000000000002',true); select public.send_sos_v042('f0450000-0000-0000-0000-000000000020','help','{}','{}',null,null,null,null,true)");
 const nearby=await db.query('select public.app_metadata() as metadata');
 if(!nearby.rows[0].metadata.events.some(e=>e.recipients===null)) throw new Error('Null recipients regression fixture missing');
 writeFileSync('app/src/test/resources/contract/metadata-null-recipients.json',JSON.stringify(nearby.rows[0].metadata,null,2)+'\n');
 console.log('Exported synthetic PostgreSQL Android contract fixtures');
 await db.exec('rollback');
} finally {await db.close();}
