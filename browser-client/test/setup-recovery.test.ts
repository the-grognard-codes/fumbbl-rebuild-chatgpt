import assert from 'node:assert/strict';
import test from 'node:test';
import { decodeRetainedSetup, setupOutcomeUncertain } from '../src/setup-recovery.ts';

const matchId = '12345678-1234-1234-1234-123456789abc';
const retained = { subject: 'away', matchId, request: { version: 1, type: 'setup', operation: 'action', requestId: 'lost-ack', matchId, expectedRevision: 7, actionId: 'native-action' } };
test('retains the exact uncertain action and original subject across reload', () => {
  assert.deepEqual(decodeRetainedSetup(JSON.stringify(retained)), retained);
  for (const code of ['PERSISTENCE_FAILED', 'MATCH_OUTCOME_UNKNOWN', 'COMPLETION_PENDING'] as const) assert.equal(setupOutcomeUncertain(code), true);
  for (const code of ['ACCEPTED', 'STALE_REVISION', 'WRONG_ACTOR', 'SESSION_UNAVAILABLE'] as const) assert.equal(setupOutcomeUncertain(code), false);
});
test('does not restore corrupt storage, credentials, foreign matches or product-external commands', () => {
  for (const value of [null, '{', 'x'.repeat(4097), JSON.stringify({ ...retained, token: 'never-retain' }),
    JSON.stringify({ ...retained, subject: 'admin' }), JSON.stringify({ ...retained, request: { ...retained.request, matchId: 'another' } }),
    JSON.stringify({ ...retained, request: { ...retained.request, operation: 'fixture' } }),
    JSON.stringify({ ...retained, request: { ...retained.request, dice: [6] } })]) assert.equal(decodeRetainedSetup(value), null);
});
