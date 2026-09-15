/** Notifications carry identifiers only. The authenticated app fetches current authorization and content. */
export function pushData(job: {recipient: string;kind: string;meeting_id?: string;request_id?: string;event_id?: string}): Record<string,string> {
  const key=job.kind==='checkin' ? 'event_id' : job.kind==='location_request' ? 'request_id' : 'meeting_id';
  const target=job[key];
  if(!target || !['created','removed','location_request','checkin'].includes(job.kind)) throw Error('Unsupported push job');
  return {recipient:job.recipient,kind:job.kind,[key]:target};
}
