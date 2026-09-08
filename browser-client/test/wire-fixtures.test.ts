import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decode, newerSnapshot } from '../src/protocol.ts';
import type { Snapshot } from '../src/protocol.ts';

const fixtures = JSON.parse(readFileSync(new URL('./fixtures/wire-v1.json', import.meta.url), 'utf8'));
test('sanitized captured server fixtures decode including every controlled choice and rejoin', () => {
  assert.equal(fixtures.length, 5);
  for (const fixture of fixtures) {
    let view: Snapshot | null = null;
    let pending = 0, resolved = 0, retries = 0;
    for (const raw of fixture.messages) {
      const message = decode(JSON.stringify(raw));
      assert.doesNotMatch(JSON.stringify(raw), /token|password|com\.fumbbl|diceRoller|gameState|testRoll/i);
      if (message.type === 'snapshot') {
        view = newerSnapshot(view, message);
        if (message.prompt) {
          pending++;
          assert.equal(message.prompt.actor, fixture.name.includes('AWAY') ? 'away' : 'home');
          assert.equal(message.prompt.revision, message.revision);
          assert.equal(message.prompt.options.length, fixture.name.includes('AWAY') ? 2 : 1);
          assert.ok(message.prompt.id.startsWith(message.matchId));
          for (const option of message.prompt.options) assert.equal(option.label, 'Both Down');
        } else resolved++;
      } else if (message.duplicate) {
        retries++; assert.equal(message.status, 'accepted'); assert.ok(view && message.revision <= view.revision);
      }
    }
    assert.ok(resolved > 0); assert.ok(retries > 0);
    if (fixture.name !== 'MOVEMENT') assert.ok(pending >= 2, 'pending rejoin captured');
  }
});
test('a fresh join accepts a new lifetime while an established session rejects actor or match changes', () => {
  const raw = fixtures[0].messages.find((m: Snapshot) => m.type === 'snapshot');
  const snapshot = decode(JSON.stringify(raw)) as Snapshot;
  const replacement = { ...snapshot, matchId: 'new-lifetime', revision: 0, prompt: null };
  assert.equal(newerSnapshot(null, replacement), replacement);
  assert.throws(() => newerSnapshot(snapshot, replacement));
  assert.throws(() => newerSnapshot(snapshot, { ...snapshot, actor: 'away' }));
});
