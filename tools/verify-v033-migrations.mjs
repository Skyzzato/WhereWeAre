import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const baseline = JSON.parse(readFileSync(new URL('supabase/tests/baseline-v033.json', root), 'utf8'));
for (const [path, expected] of Object.entries(baseline)) {
  // Git stores LF; checkout conversion must not look like a migration edit.
  const content = readFileSync(new URL(path, root), 'utf8').replace(/\r\n/g, '\n');
  const actual = createHash('sha256').update(content).digest('hex');
  if (actual !== expected) throw new Error(`Immutable migration changed: ${fileURLToPath(new URL(path, root))}`);
}
console.log('PASS: migrations 001–007 match the v0.33 baseline');
