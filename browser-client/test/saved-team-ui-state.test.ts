import assert from 'node:assert/strict';
import { test } from 'node:test';
import { applySavedTeamResponse, canEditSavedTeam } from '../src/saved-team-ui-state.ts';
import type { SavedDocument, SavedTeamResponse } from '../src/saved-team-protocol.ts';

const draft = { catalogVersion: 'retired', ruleset: 'BB2025' as const, rosterId: 'human', presetId: 'preset', captainId: null, players: [], resources: { rerolls: 0, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } };
const document: SavedDocument = { formatVersion: 1, teamId: '12345678-1234-1234-1234-123456789abc', documentVersion: 2, ruleset: 'BB2025', catalogVersion: 'retired', owner: { namespace: 'local', subject: 'home' }, draft, validation: { valid: true, total: 0, budget: 1, skillPoints: 0, messages: [] } };
const base = { saved: document, draft: { ...draft, resources: { ...draft.resources, rerolls: 3 } }, versionStatus: 'CURRENT', dirty: true, teams: [], status: 'Connected' };
function response(code: SavedTeamResponse['code'], payload: Partial<SavedTeamResponse> = {}): SavedTeamResponse { return { version: 1, type: 'savedTeam', requestId: 'r', code, document: null, versionStatus: null, validation: null, teams: [], ...payload }; }
test('conflict and invalid writes retain the dirty form and saved authoritative document', () => {
  for (const code of ['CONFLICT', 'VALIDATION_FAILED'] as const) {
    const next = applySavedTeamResponse(base, response(code));
    assert.equal(next.saved, document); assert.equal(next.draft?.resources.rerolls, 3); assert.equal(next.dirty, true); assert.match(next.status, new RegExp(code));
  }
});
test('unavailable documents are preserved for export but cannot be edited', () => {
  const next = applySavedTeamResponse(base, response('OK', { document, versionStatus: 'VERSION_UNAVAILABLE' }));
  assert.equal(next.saved, document); assert.equal(next.dirty, false); assert.equal(canEditSavedTeam('current', next, false), false);
  assert.equal(canEditSavedTeam('retired', { ...next, versionStatus: 'CURRENT' }, false), true);
});
test('list and conflict replies do not clear a retained migration lock', () => {
  const locked = { ...base, versionStatus: 'MIGRATION_REQUIRED' };
  assert.equal(applySavedTeamResponse(locked, response('OK')).versionStatus, 'MIGRATION_REQUIRED');
  assert.equal(applySavedTeamResponse(locked, response('CONFLICT')).versionStatus, 'MIGRATION_REQUIRED');
});
