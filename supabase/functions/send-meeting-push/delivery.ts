/** Queue completion is not proof of FCM acceptance or delivery to a person. */
export async function deliveryResult(tokens: string[], send: (token: string)=>Promise<'accepted'|'invalid'|'retry'>) {
  let complete=true,accepted=false;
  for(const token of tokens) {
    const result=await send(token);
    if(result==='accepted') accepted=true;
    if(result==='retry') complete=false;
  }
  return {complete,accepted};
}
