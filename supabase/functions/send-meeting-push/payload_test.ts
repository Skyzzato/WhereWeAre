import {pushData} from './payload.ts';
Deno.test('meeting payload remains compatible and location request has no meeting identifier',()=>{
  const meeting=pushData({recipient:'user',kind:'created',meeting_id:'meeting'});
  if(JSON.stringify(meeting)!==JSON.stringify({recipient:'user',kind:'created',meeting_id:'meeting'})) throw Error('Meeting contract changed');
  const request=pushData({recipient:'user',kind:'location_request',request_id:'request'});
  if(request.request_id!=='request' || 'meeting_id' in request || Object.keys(request).length!==3) throw Error('Invalid request payload');
  const event=pushData({recipient:'user',kind:'checkin',event_id:'event'});
  if(event.event_id!=='event' || 'meeting_id' in event || Object.keys(event).length!==3) throw Error('Invalid event payload');
  const place=pushData({recipient:'user',kind:'place',event_id:'event'});
  if(place.event_id!=='event' || Object.keys(place).length!==3) throw Error('Invalid place payload');
  let rejected=false;
  try {pushData({recipient:'user',kind:'unknown'});} catch {rejected=true;}
  if(!rejected) throw Error('Unknown notification kind accepted');
});
