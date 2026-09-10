import { createClient } from 'npm:@supabase/supabase-js@2.57.4';

// No client-supplied user id is accepted. Administrative credentials stay here.
Deno.serve(async (request) => {
  if (request.method !== 'POST') return new Response('Method not allowed', { status: 405 });
  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) return new Response('Unauthorized', { status: 401 });
  const admin = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await admin.auth.getUser(authorization.slice(7));
  if (error || !data.user) return new Response('Unauthorized', { status: 401 });
  const id = data.user.id;
  const tombstone = await admin.from('profiles').update({ deleting: true }).eq('id', id);
  if (tombstone.error) return new Response('Unable to prepare deletion; retry', { status: 500 });
  // Quiesce location writes first. Cascades cover profiles, requests, memberships,
  // owned groups, invalidation rows, lookup limits and latest_locations.
  const stopped = await admin.from('sharing_status').update({ is_sharing: false }).eq('user_id', id);
  if (stopped.error) return new Response('Unable to stop sharing; retry', { status: 500 });
  // Removing Storage first is required by Auth; retry is safe after partial failure.
  while (true) {
    const listed = await admin.storage.from('avatars').list(id, { limit: 100 });
    if (listed.error) return new Response('Unable to list avatars; retry', { status: 500 });
    if (!listed.data.length) break;
    const removed = await admin.storage.from('avatars').remove(listed.data.map((item) => `${id}/${item.name}`));
    if (removed.error) return new Response('Unable to remove avatars; retry', { status: 500 });
  }
  const deleted = await admin.auth.admin.deleteUser(id);
  if (deleted.error) return new Response('Unable to delete account; retry', { status: 500 });
  return Response.json({ deleted: true });
});
