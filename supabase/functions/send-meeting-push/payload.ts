/** Notifications carry identifiers only. The authenticated app fetches current authorization and content. */
export function pushData(job: {recipient: string;kind: string;meeting_id?: string;request_id?: string;event_id?: string}): Record<string,string> {
  const key=['checkin','place','sos','sos_closed'].includes(job.kind) ? 'event_id' : job.kind==='location_request' ? 'request_id' : 'meeting_id';
  const target=job[key];
  if(!target || !['created','removed','location_request','checkin','place','sos','sos_closed'].includes(job.kind)) throw Error('Unsupported push job');
  return {recipient:job.recipient,kind:job.kind,[key]:target};
}
