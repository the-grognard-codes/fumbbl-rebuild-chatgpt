import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';

// Only random correlation/match identifiers vary. Preserve their equality and
// reuse relationships by assigning a distinct ordinal to every distinct UUID.
const [baselinePath, targetPath, outputPath] = process.argv.slice(2);
assert.ok(baselinePath && targetPath && outputPath, 'baseline target output paths required');
function normalize(frames) {
  const ids = new Map();
  return JSON.parse(JSON.stringify(frames, (key, value) => {
    if (['matchId', 'requestId'].includes(key) && typeof value === 'string'
      && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value)) {
      if (!ids.has(value)) ids.set(value, `correlation-${ids.size}`);
      return ids.get(value);
    }
    return value;
  }));
}
const baselineRaw = await readFile(baselinePath);
const targetRaw = await readFile(targetPath);
const baseline = normalize(JSON.parse(baselineRaw));
const target = normalize(JSON.parse(targetRaw));
const mismatches = [];
for (let index = 0; index < Math.max(target.length, baseline.length); index++) {
  if (!isDeepStrictEqual(target[index], baseline[index])) mismatches.push({ index, capability: target[index]?.capability });
}
const hash = value => createHash('sha256').update(value).digest('hex');
const result = { pass: mismatches.length === 0, frames: target.length, mismatches, baselinePath, targetPath,
  baselineSha256: hash(baselineRaw), targetSha256: hash(targetRaw),
  normalizedSha256: hash(JSON.stringify(target)),
  normalization: 'bijective ordinal replacement of UUID-valued matchId/requestId only; all other values and ordering compared exactly' };
await writeFile(outputPath, JSON.stringify(result, null, 2));
console.log(JSON.stringify(result));
if (!result.pass) process.exitCode = 1;
