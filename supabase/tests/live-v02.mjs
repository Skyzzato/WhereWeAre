// Live acceptance test: creates only disposable users; deletes only those users.
// Requires the project's test-mode email autoconfirm and local client configuration.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';

const props = Object.fromEntries(fs.readFileSync('local.properties', 'utf8').split(/\r?\n/)
  .filter(x => x.includes('=')).map(x => [x.slice(0, x.indexOf('=')), x.slice(x.indexOf('=') + 1)]));
const base = props.SUPABASE_URL, key = props.SUPABASE_ANON_KEY;
assert.ok(base?.startsWith('https://') && key, 'Client configuration required');
assert.equal(process.env.WWA_ALLOW_DISPOSABLE_SIGNUP, 'yes', 'Explicit test-account opt-in required');
const users = [], sockets = [];
const pass = message => console.log('PASS ' + message);
async function request(path, user, body, method = body === undefined ? 'GET' : 'POST', raw = false) {
  if (method === 'GET' && path.startsWith('/storage/v1/object/authenticated/')) path += '?cacheNonce=' + randomUUID();
  const response = await fetch(base + path, {
    method, signal: AbortSignal.timeout(20000),
    headers: { apikey: key, ...(user ? { Authorization: `Bearer ${user.token}` } : {}),
      'Content-Type': raw ? 'image/webp' : 'application/json', 'Cache-Control': 'no-store, max-age=0' },
    body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
  });
  return response;
}
async function api(...args) {
  const response = await request(...args);
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${(await response.text()).slice(0, 400)}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
const rpc = (user, name, body = {}) => api('/rest/v1/rpc/' + name, user, body);
const rows = (user, table) => api('/rest/v1/' + table + '?select=*', user);
async function start(user, ageSeconds = 0) {
  const status = (await rows(user, 'sharing_status')).find(r => r.user_id === user.id);
  user.session = randomUUID();
  await rpc(user, 'set_sharing', { active: true, session: user.session, expected_revision: status.revision });
  await rpc(user, 'publish_location', { session: user.session, lat: 41.9, lon: 12.5, acc: 2000,
    fix_at: new Date(Date.now() - ageSeconds * 1000).toISOString() });
}
async function subscribe(user) {
  const socket = new WebSocket(base.replace('https:', 'wss:') + '/realtime/v1/websocket?apikey=' + encodeURIComponent(key) + '&vsn=1.0.0');
  sockets.push(socket); user.events = [];
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(Error('Realtime subscription timeout')), 15000);
    socket.onerror = () => { clearTimeout(timeout); reject(Error('Realtime transport error')); };
    socket.onopen = () => socket.send(JSON.stringify({ topic: 'realtime:qa-' + user.id, event: 'phx_join', ref: '1',
      payload: { access_token: user.token, config: { broadcast: { self: false }, presence: { key: '' },
        postgres_changes: [{ event: 'UPDATE', schema: 'public', table: 'account_events' }] } } }));
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.event === 'postgres_changes') user.events.push(message.payload.data);
      if (message.event === 'system') {
        clearTimeout(timeout);
        if (message.payload.status === 'ok') resolve(); else reject(Error('Realtime subscription rejected'));
      }
    };
  });
}
async function deletion(user) {
  assert.ok(users.includes(user) && user.email.startsWith('wwa-qa-'), 'Delete only users created by this run');
  const result = await api('/functions/v1/delete-account', user, {});
  assert.equal(result.deleted, true); user.deleted = true;
}
try {
  const bootstrap = await rpc(null, 'app_bootstrap');
  assert.equal(bootstrap.latest_version_code, 2); assert.equal(bootstrap.maintenance_mode, false);
  pass('anonymous bootstrap v0.2');
  for (const label of ['A', 'B', 'C']) {
    const email = `wwa-qa-${randomUUID()}@example.com`, password = randomUUID() + 'aA9!';
    const data = await api('/auth/v1/signup', null, { email, password, data: { display_name: 'WWA QA ' + label } });
    const user = { id: data.user?.id, token: data.access_token, email, password, label };
    users.push(user);
    assert.ok(user.id && user.token, 'Disposable signup must return an authenticated session');
  }
  const [a, b, c] = users;
  pass('three disposable accounts created');
  // Exercise the original client-to-client workflow as a separate process.
  await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, ['supabase/tests/live-clients.mjs'], { stdio: 'inherit', env: {
      ...process.env, WWA_TEST_EMAIL_A: a.email, WWA_TEST_PASSWORD_A: a.password,
      WWA_TEST_EMAIL_B: b.email, WWA_TEST_PASSWORD_B: b.password,
    } });
    child.on('error', reject); child.on('exit', code => code === 0 ? resolve() : reject(Error('v0.1 live regression failed')));
  });
  await rpc(a, 'remove_connection', { other_user_id: b.id });
  await subscribe(a);
  const gid = await rpc(a, 'create_group', { group_name: 'Collaudo 🏔️', group_emoji: '🏔️' });
  const group = (await rows(a, 'groups')).find(r => r.id === gid);
  assert.equal((await rows(c, 'groups')).length, 0);
  assert.equal(await rpc(b, 'join_group', { code: group.invite_code.toUpperCase() }), gid);
  assert.equal(await rpc(b, 'join_group', { code: group.invite_code }), gid);
  assert.equal((await rows(a, 'group_members')).length, 2);
  pass('group creation, normalized join, retry and third-party isolation');
  await start(c, 1900);
  await rpc(a, 'join_group', { code: 'invalid-code' });
  // A fresh group participant has no earlier newer fix: exercise timeout with an old synthetic fix.
  await rpc(c, 'join_group', { code: group.invite_code });
  await rpc(c, 'set_visibility', { seconds: 1800 });
  assert.ok(!(await rows(a, 'latest_locations')).some(r => r.user_id === c.id));
  assert.ok((await rows(c, 'latest_locations')).some(r => r.user_id === c.id));
  await rpc(c, 'set_visibility', { seconds: 86400 });
  assert.ok((await rows(a, 'latest_locations')).some(r => r.user_id === c.id));
  await rpc(c, 'remove_group_member', { gid, member: c.id });
  pass('owner visibility timeout and own fix retention');
  await start(b);
  assert.ok((await rows(a, 'latest_locations')).some(r => r.user_id === b.id));
  assert.equal((await rpc(c, 'contact_profiles')).length, 1);
  const path = `${b.id}/${randomUUID()}.webp`;
  const pixel = Buffer.from('UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA', 'base64');
  const upload = await request('/storage/v1/object/avatars/' + path, b, pixel, 'POST', true);
  assert.ok(upload.ok, 'Avatar upload: ' + upload.status + ' ' + (upload.ok ? '' : await upload.text()));
  await rpc(b, 'set_avatar', { path });
  assert.equal((await request('/storage/v1/object/authenticated/avatars/' + path, a)).status, 200);
  assert.notEqual((await request('/storage/v1/object/authenticated/avatars/' + path, c)).status, 200);
  assert.notEqual((await request('/storage/v1/object/public/avatars/' + path, null)).status, 200);
  pass('real Storage upload, authorized avatar read, private and third-party denial');
  a.events = [];
  await rpc(a, 'remove_group_member', { gid, member: b.id });
  assert.ok(!(await rpc(a, 'contact_profiles')).some(r => r.user_id === b.id));
  assert.ok(!(await rows(a, 'latest_locations')).some(r => r.user_id === b.id));
  assert.notEqual((await request('/storage/v1/object/authenticated/avatars/' + path, a)).status, 200);
  const deadline = Date.now() + 12000;
  while (!a.events.some(e => e.record.user_id === a.id) && Date.now() < deadline) await new Promise(r => setTimeout(r, 100));
  assert.ok(a.events.some(e => e.record.user_id === a.id));
  assert.ok(a.events.every(e => e.record.user_id === a.id));
  pass('membership revocation removes location/avatar and sends private Realtime invalidation');
  await rpc(b, 'join_group', { code: group.invite_code });
  await deletion(b);
  assert.ok(!(await rows(a, 'group_members')).some(r => r.user_id === b.id));
  assert.ok(!(await rpc(a, 'contact_profiles')).some(r => r.user_id === b.id));
  assert.notEqual((await request('/storage/v1/object/authenticated/avatars/' + path, a)).status, 200);
  assert.notEqual((await request('/auth/v1/token?grant_type=password', null, { email: b.email, password: b.password })).status, 200);
  assert.notEqual((await request('/storage/v1/object/avatars/' + `${b.id}/${randomUUID()}.webp`, b, pixel, 'POST', true)).status, 200);
  pass('authenticated deletion removes user/avatar/membership; deleted JWT cannot upload');
  await rpc(c, 'join_group', { code: group.invite_code });
  await deletion(a);
  assert.equal((await rows(c, 'groups')).length, 0);
  assert.equal((await rows(c, 'group_members')).length, 0);
  pass('creator deletion cascades group and memberships');
  console.log('ALL LIVE V0.2 TESTS PASSED');
} finally {
  for (const socket of sockets) socket.close();
  for (const user of users.filter(u => !u.deleted)) {
    try { await deletion(user); pass('disposable account ' + user.label + ' cleaned up'); }
    catch { console.error('CLEANUP REQUIRED for test user id ' + user.id); process.exitCode = 1; }
  }
}
