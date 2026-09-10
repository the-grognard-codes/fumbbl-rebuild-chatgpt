import assert from 'node:assert/strict';
import { test } from 'node:test';
import { decodeMatchResult } from '../src/result-protocol.ts';

const state = { matchId: '12345678-1234-1234-1234-123456789abc', revision: 7, callerRole: 'home', phase: 'FULL_TIME', actor: 'home', prompt: null, players: [], weather: 'NICE', homeRerolls: 0, awayRerolls: 0, actions: [], turn: 8, turnMode: 'FULL_TIME', ball: null, activePlayerId: null, half: 2, homeTurn: 8, awayTurn: 8, homeScore: 2, awayScore: 1, drive: 3 };
const result = { formatVersion: 1, engineVersion: 'ffb-3.4.0-bb2025-m3d.1', ruleset: 'BB2025', catalogVersion: 'bb2025-human-2026-09-08.1', presetId: 'human-exhibition-1150', presetVersion: 'bb2025-human-2026-09-08.1', matchId: state.matchId, homeScore: 2, awayScore: 1, finalRevision: 7, eventCount: 8 };
const response = { version: 1, type: 'matchResult', requestId: 'load', code: 'ACCEPTED', result, event: null };
test('decodes a terminal result and first/last bounded native replay events', () => { assert.equal(decodeMatchResult(JSON.stringify(response)).result?.eventCount, 8); const first = decodeMatchResult(JSON.stringify({ ...response, requestId: 'first', event: { revision: 0, kind: 'START', state: { ...state, revision: 0, phase: 'PLAY' } } })); assert.equal(first.event?.revision, 0); const replay = decodeMatchResult(JSON.stringify({ ...response, requestId: 'replay', event: { revision: 7, kind: 'FULL_TIME', state } })); assert.equal(replay.event?.state.phase, 'FULL_TIME'); });
test('fails closed for metadata, event and rejected-response inconsistencies', () => { assert.throws(() => decodeMatchResult(JSON.stringify({ ...response, result: { ...result, extra: true } }))); assert.throws(() => decodeMatchResult(JSON.stringify({ ...response, event: { revision: 7, kind: 'FULL_TIME', state: { ...state, actions: [{ id: 'a', label: 'a', actor: 'home', kind: 'a' }] } } }))); assert.throws(() => decodeMatchResult(JSON.stringify({ ...response, code: 'NOT_COMPLETED' }))); });

test('accepts the maximum recorded history and reports unsupported formats explicitly', () => {
  assert.equal(decodeMatchResult(JSON.stringify({ ...response, result: { ...result, finalRevision: 8192, eventCount: 8193 } })).result?.eventCount, 8193);
  assert.throws(() => decodeMatchResult(JSON.stringify({ ...response, result: { ...result, finalRevision: 8193, eventCount: 8194 } })));
  assert.throws(() => decodeMatchResult(JSON.stringify({ ...response, result: { ...result, engineVersion: 'future' } })));
  assert.equal(decodeMatchResult(JSON.stringify({ ...response, code: 'REPLAY_UNSUPPORTED', result: null })).code, 'REPLAY_UNSUPPORTED');
});
