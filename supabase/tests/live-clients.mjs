import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const props=Object.fromEntries(fs.readFileSync('local.properties','utf8').split(/\r?\n/).filter(x=>x.includes('=')).map(x=>[x.slice(0,x.indexOf('=')),x.slice(x.indexOf('=')+1)]));
const base=props.SUPABASE_URL,key=props.SUPABASE_ANON_KEY;
async function api(path,token,body){const r=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{apikey:key,...(token?{Authorization:`Bearer ${token}`} :{}),'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const t=await r.text();if(!r.ok)throw Error(`${r.status} ${t}`);return t?JSON.parse(t):null;}
const users=[];
for(const label of ['A','B']){const email=process.env['WWA_TEST_EMAIL_'+label],password=process.env['WWA_TEST_PASSWORD_'+label];assert.ok(email && password,'Provide credentials for two fresh disposable confirmed test accounts');const a=await api('/auth/v1/token?grant_type=password',null,{email,password});users.push({id:a.user.id,token:a.access_token,session:randomUUID()});}
const [a,b]=users; const rpc=(u,name,body={})=>api('/rest/v1/rpc/'+name,u.token,body);const rows=(u,table)=>api('/rest/v1/'+table+'?select=*',u.token);
function pass(x){console.log('PASS '+x)}
const sockets=[];
async function connect(u){
 const ws=new WebSocket(base.replace('https:','wss:')+'/realtime/v1/websocket?apikey='+encodeURIComponent(key)+'&vsn=1.0.0'); sockets.push(ws);u.events=[];
 await new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(Error('Realtime timeout')),15000);ws.onerror=()=>reject(Error('WebSocket error'));ws.onopen=()=>ws.send(JSON.stringify({topic:'realtime:wwa-test-'+u.id,event:'phx_join',payload:{config:{broadcast:{self:false},presence:{key:''},postgres_changes:['latest_locations','share_requests','location_shares','sharing_status'].flatMap(table=>['INSERT','UPDATE'].map(event=>({event,schema:'public',table})))},access_token:u.token},ref:'1'}));ws.onmessage=e=>{const m=JSON.parse(e.data);if(m.event==='postgres_changes')u.events.push(m.payload.data);if(m.event==='system'){if(m.payload.status==='ok'){clearTimeout(timeout);resolve()}else{clearTimeout(timeout);reject(Error(JSON.stringify(m)))}}};});
}
async function event(u,table,predicate=()=>true){const end=Date.now()+10000;while(Date.now()<end){if(u.events.some(e=>e.table===table&&predicate(e.record)))return;await new Promise(r=>setTimeout(r,100));}throw Error('Missing event '+table);}
try{
 pass('two authenticated client sessions');
 for(const u of users){const p=await rows(u,'profiles');assert.equal(p.length,1);u.code=p[0].invite_code;assert.match(u.code,/^[A-Z0-9]{4}-[A-Z0-9]{4}$/);}assert.notEqual(a.code,b.code);pass('personal codes and profile isolation');
 await Promise.all(users.map(connect));pass('both Realtime subscriptions acknowledged');
 assert.equal((await rpc(a,'lookup_user_by_invite_code',{code:b.code}))[0].user_id,b.id);pass('lookup by code');
 const request=await rpc(a,'send_share_request',{code:b.code});await event(b,'share_requests');pass('request received over Realtime');
 await rpc(b,'respond_to_share_request',{request_id:request,accept:true});await event(a,'location_shares');assert.equal((await rows(a,'location_shares')).filter(x=>x.enabled).length,2);pass('reciprocal acceptance');
 for(const u of users){const rev=(await rows(u,'sharing_status')).find(x=>x.user_id===u.id).revision;await rpc(u,'set_sharing',{active:true,session:u.session,expected_revision:rev});await rpc(u,'publish_location',{session:u.session,lat:u===a?41.9:45.4,lon:12.5,acc:12,fix_at:new Date().toISOString()});}
 await event(a,'latest_locations',r=>r.user_id===b.id);await event(b,'latest_locations',r=>r.user_id===a.id);assert.equal((await rows(a,'latest_locations')).length,2);assert.equal((await rows(b,'latest_locations')).length,2);pass('reciprocal location reads and Realtime delivery');
 a.events=[];await rpc(b,'set_location_share',{viewer:a.id,enabled:false});await event(a,'location_shares',r=>r.enabled===false);assert.equal((await rows(a,'latest_locations')).length,1);pass('revocation immediately removes remote visibility and delivers event');
 await rpc(b,'set_location_share',{viewer:a.id,enabled:true});a.events=[];await rpc(b,'set_sharing',{active:false,session:b.session});await event(a,'sharing_status',r=>r.user_id===b.id&&r.is_sharing===false);assert.equal((await rows(a,'latest_locations')).length,1);pass('explicit stop removes remote visibility and delivers event');
 await assert.rejects(()=>rpc(b,'publish_location',{session:b.session,lat:41,lon:12,acc:5,fix_at:new Date().toISOString()}),/sharing_stopped/);pass('late GPS upload rejected after stop');
 const rev=(await rows(b,'sharing_status')).find(x=>x.user_id===b.id).revision;b.session=randomUUID();await rpc(b,'set_sharing',{active:true,session:b.session,expected_revision:rev});
 for(const u of users) await rpc(u,'set_sharing',{active:false,session:null});
 pass('test sharing stopped');
}finally{for(const ws of sockets)ws.close();}

