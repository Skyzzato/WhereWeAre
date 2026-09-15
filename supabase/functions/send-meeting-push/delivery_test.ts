import {deliveryResult} from './delivery.ts';
Deno.test('SOS push acceptance never follows from empty or invalid tokens',async()=>{
  const empty=await deliveryResult([],async()=>{throw Error('must not send');});
  if(!empty.complete || empty.accepted) throw Error('Empty device list claimed acceptance');
  const invalid=await deliveryResult(['old'],async()=> 'invalid');
  if(!invalid.complete || invalid.accepted) throw Error('Invalid token claimed acceptance');
  const mixed=await deliveryResult(['ok','later'],async token=>token==='ok'?'accepted':'retry');
  if(mixed.complete || !mixed.accepted) throw Error('Partial success lost or retry suppressed');
});
