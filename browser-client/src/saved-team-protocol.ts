import type { TeamDraft } from './team-protocol';

export type SavedValidation = { valid: boolean; total: number | null; budget: number; skillPoints: number; messages: { code: string; path: string; text: string }[] };
export type SavedDocument = { formatVersion: 1; teamId: string; documentVersion: number; ruleset: 'BB2025'; catalogVersion: string; owner: { namespace: 'local'; subject: 'home' | 'away' }; draft: TeamDraft; validation: SavedValidation };
export type SavedTeamResponse = { version: 1; type: 'savedTeam'; requestId: string; code: SavedTeamCode; document: SavedDocument | null; versionStatus: null | 'CURRENT' | 'MIGRATION_REQUIRED' | 'VERSION_UNAVAILABLE'; validation: SavedValidation | null; teams: { teamId: string; documentVersion: number; catalogVersion: string }[] };
export type SavedTeamCode = 'OK' | 'MALFORMED_TEAM_REQUEST' | 'INVALID_DOCUMENT_VERSION' | 'CONFLICT' | 'NOT_FOUND' | 'VALIDATION_FAILED' | 'MIGRATION_REQUIRED' | 'VERSION_UNAVAILABLE' | 'PERSISTENCE_FAILED' | 'SAVE_OUTCOME_UNKNOWN' | 'LIMIT_REACHED';
export type SavedTeamRequest =
  | { version: 1; type: 'savedTeam'; requestId: string; operation: 'list' }
  | { version: 1; type: 'savedTeam'; requestId: string; operation: 'load'; teamId: string }
  | { version: 1; type: 'savedTeam'; requestId: string; operation: 'create'; draft: TeamDraft }
  | { version: 1; type: 'savedTeam'; requestId: string; operation: 'update'; teamId: string; expectedDocumentVersion: number; draft: TeamDraft }
  | { version: 1; type: 'savedTeam'; requestId: string; operation: 'import'; document: SavedDocument };

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const codes = new Set<SavedTeamCode>(['OK', 'MALFORMED_TEAM_REQUEST', 'INVALID_DOCUMENT_VERSION', 'CONFLICT', 'NOT_FOUND', 'VALIDATION_FAILED', 'MIGRATION_REQUIRED', 'VERSION_UNAVAILABLE', 'PERSISTENCE_FAILED', 'SAVE_OUTCOME_UNKNOWN', 'LIMIT_REACHED']);
function object(value: unknown, keys: string[]): Record<string, unknown> { if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Expected object'); const result = value as Record<string, unknown>; if (Object.keys(result).length !== keys.length || keys.some(key => !Object.hasOwn(result, key))) throw Error('Unexpected fields'); return result; }
function text(value: unknown, max = 300): asserts value is string { if (typeof value !== 'string' || !value.length || value.length > max) throw Error('Invalid text'); }
function integer(value: unknown, max = 2147483646): asserts value is number { if (!Number.isSafeInteger(value) || (value as number) < 0 || (value as number) > max) throw Error('Invalid integer'); }
function bool(value: unknown): asserts value is boolean { if (typeof value !== 'boolean') throw Error('Invalid boolean'); }
function array(value: unknown, max: number): unknown[] { if (!Array.isArray(value) || value.length > max) throw Error('Invalid array'); return value; }
/** JSON.parse accepts duplicate object keys; protocol JSON must reject them before decoding. */
export function parseUniqueJson(source: string): unknown {
  let index = 0; const space = () => { while (/\s/.test(source[index] ?? '')) index++; };
  const string = () => { const start = index++; let escaped = false; while (index < source.length) { const char = source[index++]; if (escaped) { escaped = false; continue; } if (char === '\\') { escaped = true; continue; } if (char === '"') return JSON.parse(source.slice(start, index)); } throw Error('Unterminated string'); };
  const value = (): unknown => { space(); const char = source[index]; if (char === '"') return string(); if (char === '{') { index++; const result: Record<string, unknown> = {}; const keys = new Set<string>(); space(); if (source[index] === '}') { index++; return result; } while (true) { space(); if (source[index] !== '"') throw Error('Expected key'); const key = string(); if (keys.has(key)) throw Error('Duplicate key'); keys.add(key); space(); if (source[index++] !== ':') throw Error('Expected colon'); result[key] = value(); space(); if (source[index] === '}') { index++; return result; } if (source[index++] !== ',') throw Error('Expected comma'); } } if (char === '[') { index++; const result: unknown[] = []; space(); if (source[index] === ']') { index++; return result; } while (true) { result.push(value()); space(); if (source[index] === ']') { index++; return result; } if (source[index++] !== ',') throw Error('Expected comma'); } } const match = /^(true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?)/.exec(source.slice(index)); if (!match) throw Error('Invalid JSON'); index += match[0].length; return JSON.parse(match[0]); };
  const result = value(); space(); if (index !== source.length) throw Error('Trailing JSON'); return result;
}
function validateDraft(value: unknown): TeamDraft {
  const draft = object(value, ['catalogVersion', 'ruleset', 'rosterId', 'presetId', 'captainId', 'players', 'resources']);
  for (const key of ['catalogVersion', 'rosterId', 'presetId']) text(draft[key], 100);
  if (draft.ruleset !== 'BB2025') throw Error('Invalid ruleset');
  if (draft.captainId !== null) text(draft.captainId, 80);
  const players = array(draft.players, 16); const ids = new Set<string>(); const slots = new Set<number>();
  for (const item of players) { const player = object(item, ['id', 'slot', 'positionId', 'skillIds']); text(player.id, 80); text(player.positionId, 80); integer(player.slot, 16); if (player.slot < 1 || ids.has(player.id) || slots.has(player.slot)) throw Error('Duplicate player'); ids.add(player.id); slots.add(player.slot); for (const skill of array(player.skillIds, 2)) text(skill, 80); }
  const resources = object(draft.resources, ['rerolls', 'assistantCoaches', 'cheerleaders', 'apothecary', 'dedicatedFans']); for (const value of Object.values(resources)) integer(value, 16);
  return draft as TeamDraft;
}
function validation(value: unknown): SavedValidation {
  const item = object(value, ['valid', 'total', 'budget', 'skillPoints', 'messages']); bool(item.valid); if (item.total !== null) integer(item.total, 5000000); integer(item.budget, 5000000); integer(item.skillPoints, 100); const messages = array(item.messages, 100); for (const message of messages) { const entry = object(message, ['code', 'path', 'text']); text(entry.code, 80); text(entry.path, 160); text(entry.text, 300); } if (item.valid !== (messages.length === 0) || (item.valid && item.total === null)) throw Error('Inconsistent validation'); return item as SavedValidation;
}
export function decodeSavedDocument(value: unknown): SavedDocument {
  const document = object(value, ['formatVersion', 'teamId', 'documentVersion', 'ruleset', 'catalogVersion', 'owner', 'draft', 'validation']);
  if (document.formatVersion !== 1 || document.ruleset !== 'BB2025') throw Error('Unsupported document'); text(document.teamId, 36); if (!uuid.test(document.teamId)) throw Error('Invalid team id'); integer(document.documentVersion); if (document.documentVersion < 1) throw Error('Invalid document version'); text(document.catalogVersion, 100);
  const owner = object(document.owner, ['namespace', 'subject']); if (owner.namespace !== 'local' || (owner.subject !== 'home' && owner.subject !== 'away')) throw Error('Invalid owner');
  const draft = validateDraft(document.draft); if (draft.ruleset !== document.ruleset || draft.catalogVersion !== document.catalogVersion) throw Error('Document/draft mismatch');
  return { ...document, draft, validation: validation(document.validation) } as SavedDocument;
}
export function decodeSavedTeam(textJson: string): SavedTeamResponse {
  if (new TextEncoder().encode(textJson).length > 65536) throw Error('Response too large');
  const response = object(parseUniqueJson(textJson), ['version', 'type', 'requestId', 'code', 'document', 'versionStatus', 'validation', 'teams']);
  if (response.version !== 1 || response.type !== 'savedTeam') throw Error('Unsupported response'); text(response.requestId, 100); if (typeof response.code !== 'string' || !codes.has(response.code as SavedTeamCode)) throw Error('Invalid code');
  if (response.document !== null) decodeSavedDocument(response.document); if (response.versionStatus !== null && response.versionStatus !== 'CURRENT' && response.versionStatus !== 'MIGRATION_REQUIRED' && response.versionStatus !== 'VERSION_UNAVAILABLE') throw Error('Invalid version status'); if (response.validation !== null) validation(response.validation);
  const teams = array(response.teams, 50); for (const team of teams) { const entry = object(team, ['teamId', 'documentVersion', 'catalogVersion']); text(entry.teamId, 36); if (!uuid.test(entry.teamId)) throw Error('Invalid team id'); integer(entry.documentVersion); if (entry.documentVersion < 1) throw Error('Invalid document version'); text(entry.catalogVersion, 100); }
  return response as SavedTeamResponse;
}
export function savedTeamRequest(request: SavedTeamRequest): string { return JSON.stringify(request); }
