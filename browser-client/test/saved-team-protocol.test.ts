import assert from 'node:assert/strict';
import { test } from 'node:test';
import { decodeSavedDocument, decodeSavedTeam, parseUniqueJson } from '../src/saved-team-protocol.ts';

const draft = { catalogVersion: 'retired-catalog', ruleset: 'BB2025', rosterId: 'human', presetId: 'preset', captainId: null, players: [], resources: { rerolls: 0, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } };
const document = { formatVersion: 1, teamId: '12345678-1234-1234-1234-123456789abc', documentVersion: 1, ruleset: 'BB2025', catalogVersion: 'retired-catalog', owner: { namespace: 'local', subject: 'home' }, draft, validation: { valid: true, total: 0, budget: 1150000, skillPoints: 0, messages: [] } };
const response = { version: 1, type: 'savedTeam', requestId: 'request', code: 'OK', document, versionStatus: 'MIGRATION_REQUIRED', validation: document.validation, teams: [] };
test('saved documents preserve unknown catalog identifiers structurally', () => {
  assert.deepEqual(decodeSavedDocument(document), document);
  assert.equal(decodeSavedTeam(JSON.stringify(response)).versionStatus, 'MIGRATION_REQUIRED');
});
test('saved-team decoder fails closed for unknown fields, types and invalid metadata', () => {
  for (const invalid of [
    { ...response, unexpected: true }, { ...response, code: 'OTHER' }, { ...response, teams: [{}] },
    { ...response, document: { ...document, teamId: document.teamId.toUpperCase() } },
    { ...response, document: { ...document, draft: { ...draft, catalogVersion: 5 } } },
    { ...response, document: { ...document, validation: { ...document.validation, valid: false } } },
  ]) assert.throws(() => decodeSavedTeam(JSON.stringify(invalid)));
});
test('duplicate JSON fields are rejected before JSON.parse could discard them', () => {
  assert.throws(() => parseUniqueJson('{"teamId":"one","teamId":"two"}'));
  assert.throws(() => decodeSavedTeam(JSON.stringify(response).replace('"code":"OK"', '"code":"OK","code":"CONFLICT"')));
});
