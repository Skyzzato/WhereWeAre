import { createClient } from 'npm:@supabase/supabase-js@2.57.4';
import {pushData} from './payload.ts';
import {deliveryResult} from './delivery.ts';

// Called by a trusted scheduler only. Never expose these credentials in the APK.
const b64url = (bytes: Uint8Array) => btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
const encode = (value: unknown) => b64url(new TextEncoder().encode(JSON.stringify(value)));
async function accessToken(account: {client_email:string;private_key:string}) {
  const now=Math.floor(Date.now()/1000);
  const body=encode({alg:'RS256',typ:'JWT'})+'.'+encode({iss:account.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600});
  const der=Uint8Array.from(atob(account.private_key.replace(/-----[^-]+-----|\s/g,'')),c=>c.charCodeAt(0));
  const key=await crypto.subtle.importKey('pkcs8',der,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
  const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,new TextEncoder().encode(body));
  const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:body+'.'+b64url(new Uint8Array(signature))}),signal:AbortSignal.timeout(15000)});
  if(!response.ok) throw Error('FCM authentication failed');
  return (await response.json()).access_token as string;
}
export async function handle(request: Request): Promise<Response> {
  const secret=Deno.env.get('PUSH_DISPATCH_SECRET');
  if(request.method!=='POST' || !secret || request.headers.get('Authorization')!==`Bearer ${secret}`) return new Response('Unauthorized',{status:401});
  const config=Deno.env.get('FIREBASE_SERVICE_ACCOUNT');
  if(!config) return new Response('Push is not configured',{status:503});
  try {
    const account=JSON.parse(config);const token=await accessToken(account);
    const admin=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}});
    const {data:jobs,error}=await admin.rpc('claim_push_batch');if(error) throw Error('Queue unavailable');
    let completed=0;
    for(const job of jobs ?? []) {
      const delivery=await deliveryResult(job.tokens,async device=> {
        const {data:allowed,error:guardError}=await admin.rpc('push_job_authorized',{job:job.id});
        if(guardError) throw Error('Authorization check unavailable');
        if(!allowed) return 'invalid';
        const result=await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,{
          method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},signal:AbortSignal.timeout(15000),
          body:JSON.stringify({message:{token:device,data:pushData(job),android:{priority:'HIGH',ttl:'86400s'}}})
        });
        if(result.ok) return 'accepted';
        if(!result.ok) {
          const response=await result.json().catch(()=>({}));
          const invalid=response.error?.details?.some((detail:{errorCode?:string})=>detail.errorCode==='UNREGISTERED');
          if(invalid) {await admin.from('device_tokens').delete().eq('token',device);return 'invalid';}
        }
        return 'retry';
      });
      if(delivery.accepted) {const {error}=await admin.rpc('record_push_acceptance',{job:job.id});if(error) throw Error('Acceptance acknowledgement failed');}
      if(delivery.complete) {const {error}=await admin.rpc('complete_push',{job:job.id});if(error) throw Error('Queue acknowledgement failed');completed++;}
    }
    return Response.json({completed});
  } catch {return new Response('Dispatch failed; leased jobs will retry',{status:503});}
}
if(import.meta.main) Deno.serve(handle);
