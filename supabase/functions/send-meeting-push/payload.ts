/** Notifications carry identifiers only. The authenticated app fetches current authorization and content. */
export function pushData(job: {recipient: string;kind: string;meeting_id?: string;request_id?: string}): Record<string,string> {
  const target=job.kind==='location_request' ? job.request_id : job.meeting_id;
  if(!target || !['created','removed','location_request'].includes(job.kind)) throw Error('Unsupported push job');
  return {recipient:job.recipient,kind:job.kind,[job.kind==='location_request' ? 'request_id' : 'meeting_id']:target};
}
