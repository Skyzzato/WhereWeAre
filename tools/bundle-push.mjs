import {readFileSync,writeFileSync} from 'node:fs';
const dir='supabase/functions/send-meeting-push/';
const entry=readFileSync(dir+'index.ts','utf8').replace(/^import \{(?:pushData|deliveryResult)\} from .*;\r?\n/gm,'');
writeFileSync(process.argv[2] ?? '.tools/v049-push-bundle.ts',readFileSync(dir+'payload.ts','utf8')+'\n'+readFileSync(dir+'delivery.ts','utf8')+'\n'+entry);
