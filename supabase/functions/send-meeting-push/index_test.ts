import {handle} from './index.ts';

Deno.test('dispatcher rejects untrusted calls and fails closed without Firebase', async () => {
  const secret=Deno.env.get('PUSH_DISPATCH_SECRET');
  const account=Deno.env.get('FIREBASE_SERVICE_ACCOUNT');
  try {
    Deno.env.set('PUSH_DISPATCH_SECRET','local-test-secret');
    Deno.env.delete('FIREBASE_SERVICE_ACCOUNT');
    const cases: [Request,number][] = [
      [new Request('http://localhost',{method:'GET'}),401],
      [new Request('http://localhost',{method:'POST'}),401],
      [new Request('http://localhost',{method:'POST',headers:{Authorization:'Bearer wrong'}}),401],
      [new Request('http://localhost',{method:'POST',headers:{Authorization:'Bearer local-test-secret'}}),503],
    ];
    for(const [request,expected] of cases) {
      const response=await handle(request);
      if(response.status!==expected) throw Error(`Expected ${expected}, received ${response.status}`);
      await response.text();
    }
    Deno.env.set('FIREBASE_SERVICE_ACCOUNT','invalid-json');
    const response=await handle(new Request('http://localhost',{method:'POST',headers:{Authorization:'Bearer local-test-secret'}}));
    if(response.status!==503) throw Error('Invalid Firebase configuration must fail closed');
    if((await response.text()).includes('invalid-json')) throw Error('Secrets must not appear in errors');
  } finally {
    if(secret===undefined) Deno.env.delete('PUSH_DISPATCH_SECRET');else Deno.env.set('PUSH_DISPATCH_SECRET',secret);
    if(account===undefined) Deno.env.delete('FIREBASE_SERVICE_ACCOUNT');else Deno.env.set('FIREBASE_SERVICE_ACCOUNT',account);
  }
});
