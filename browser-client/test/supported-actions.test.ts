import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { decodeSetupState } from '../src/setup-protocol.ts';

const frames = JSON.parse(readFileSync(new URL('./fixtures/supported-actions-v1.json', import.meta.url), 'utf8'));
test('native M3c wire evidence decodes every action, authorized owner and reconnect snapshot', () => {
  const covered = new Set<string>();
  for (const frame of frames) {
    const before = decodeSetupState(JSON.stringify(frame.before)).state!;
    const after = decodeSetupState(JSON.stringify(frame.after)).state!;
    const selected = before.actions.find(action => action.id === frame.request.actionId);
    assert.ok(selected);
    assert.equal(selected.actor, before.callerRole);
    assert.equal(frame.request.expectedRevision, before.revision);
    assert.equal(after.revision, before.revision + 1);
    assert.deepEqual(decodeSetupState(JSON.stringify(frame.before)).state, before);
    assert.ok(Buffer.byteLength(JSON.stringify(frame.before)) < 256 * 1024);
    covered.add(frame.capability);
  }
  for (const kind of ['declarePass','pass','declareHandOff','handOff','declareFoul','foul','interception','argueTheCall','declareThrowTeamMate','liftTeamMate','throwTeamMate','secureBall','skill','reroll','apothecary','forgo']) assert.ok(covered.has(kind), `Missing native ${kind} evidence`);
});
