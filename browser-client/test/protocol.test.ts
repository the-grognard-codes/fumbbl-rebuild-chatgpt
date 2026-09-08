import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decode, newerSnapshot } from '../src/protocol.ts';
import type { Snapshot } from '../src/protocol.ts';
const resources = { rerolls: 3, rerollUsed: false, blitzUsed: false, passUsed: false, handOverUsed: false, foulUsed: false };
const snapshot: Snapshot = { version: 1, type: 'snapshot', revision: 1, matchId: 'm1b', actor: 'home', players: [{ id: 'runner', role: 'home', state: 'standing', x: 6, y: 7, movementUsed: 1, movementAllowance: 6 }], ball: { x: 12, y: 7 }, turnOwner: 'home', prompt: null, resources: { home: resources, away: resources } };
test('validates wire data instead of trusting TypeScript assertions', () => {
  assert.deepEqual(decode(JSON.stringify(snapshot)), snapshot);
  for (const bad of [{ ...snapshot, version: 2 }, { ...snapshot, revision: -1 }, { ...snapshot, actor: 'spectator' }, { ...snapshot, players: [{ ...snapshot.players[0], x: 26 }] }, { ...snapshot, players: [{ ...snapshot.players[0], state: 'missing' }] }, { ...snapshot, prompt: {} }]) assert.throws(() => decode(JSON.stringify(bad)));
});
test('accepts only complete current-revision block prompts with unique options', () => {
  const prompt = { id: 'block-1', type: 'block', actor: 'away', revision: 1, options: [{ id: 'push', label: 'Push' }, { id: 'both-down', label: 'Both down' }] } as const;
  assert.deepEqual(decode(JSON.stringify({ ...snapshot, prompt })), { ...snapshot, prompt });
  for (const bad of [
    { ...prompt, revision: 0 }, { ...prompt, type: 'pass' }, { ...prompt, actor: 'none' },
    { ...prompt, options: [] }, { ...prompt, options: [{ id: 'push', label: 'Push' }, { id: 'push', label: 'Again' }] },
    { ...prompt, options: [{ id: 'push' }] }
  ]) assert.throws(() => decode(JSON.stringify({ ...snapshot, prompt: bad })));
});
test('requires and validates both teams turn resources', () => {
  for (const bad of [null, {}, { home: resources }, { home: { ...resources, rerolls: -1 }, away: resources }, { home: resources, away: { ...resources, blitzUsed: 0 } }]) {
    assert.throws(() => decode(JSON.stringify({ ...snapshot, resources: bad })));
  }
});
test('older or duplicate snapshots cannot roll the board back', () => {
  assert.equal(newerSnapshot(snapshot, { ...snapshot, revision: 0 }), snapshot);
  assert.equal(newerSnapshot(snapshot, { ...snapshot }), snapshot);
  assert.equal(newerSnapshot(snapshot, { ...snapshot, revision: 2 }).revision, 2);
  assert.throws(() => newerSnapshot(snapshot, { ...snapshot, actor: 'away' }));
});
test('accepted duplicate result preserves historical request revision', () => {
  const result = { version: 1, type: 'result', requestId: 'move-1', status: 'accepted', code: 'MOVED', revision: 1, duplicate: true };
  assert.deepEqual(decode(JSON.stringify(result)), result);
  assert.throws(() => decode(JSON.stringify({ ...result, duplicate: 'true' })));
});
