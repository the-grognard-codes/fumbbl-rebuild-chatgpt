import { parseUniqueJson } from './saved-team-protocol.ts';
import { decodeSetupStateValue } from './setup-protocol.ts';
import type { SetupState } from './setup-protocol.ts';

export type MatchResultCode = 'ACCEPTED' | 'NOT_FOUND' | 'NOT_COMPLETED' | 'REPLAY_UNSUPPORTED' | 'PERSISTENCE_FAILED' | 'INVALID_REQUEST' | 'AUTHENTICATION_REQUIRED' | 'SNAPSHOT_UNSUPPORTED';
export type MatchResultMetadata = { formatVersion: 1; engineVersion: 'ffb-3.4.0-bb2025-m3d.1'; ruleset: 'BB2025'; catalogVersion: 'bb2025-human-2026-09-08.1'; presetId: 'human-exhibition-1150'; presetVersion: 'bb2025-human-2026-09-08.1'; matchId: string; homeScore: number; awayScore: number; finalRevision: number; eventCount: number };
export type ReplayEvent = { revision: number; kind: string; state: SetupState };
export type MatchResultResponse = { version: 1; type: 'matchResult'; requestId: string | null; code: MatchResultCode; result: MatchResultMetadata | null; event: ReplayEvent | null };
const codes = new Set<MatchResultCode>(['ACCEPTED', 'NOT_FOUND', 'NOT_COMPLETED', 'REPLAY_UNSUPPORTED', 'PERSISTENCE_FAILED', 'INVALID_REQUEST', 'AUTHENTICATION_REQUIRED', 'SNAPSHOT_UNSUPPORTED']);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function object(value: unknown, keys: string[]) { if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Expected object'); const result = value as Record<string, unknown>; if (Object.keys(result).length !== keys.length || keys.some(key => !Object.hasOwn(result, key))) throw Error('Unexpected fields'); return result; }
function text(value: unknown, maximum = 100): asserts value is string { if (typeof value !== 'string' || !value || value.length > maximum) throw Error('Invalid text'); }
function integer(value: unknown, maximum = 5_000_000): asserts value is number { if (!Number.isSafeInteger(value) || (value as number) < 0 || (value as number) > maximum) throw Error('Invalid integer'); }
export function decodeMatchResult(json: string): MatchResultResponse {
  if (new TextEncoder().encode(json).length > 65536) throw Error('Response too large'); const response = object(parseUniqueJson(json), ['version', 'type', 'requestId', 'code', 'result', 'event']);
  if (response.version !== 1 || response.type !== 'matchResult' || (response.requestId !== null && (typeof response.requestId !== 'string' || !response.requestId || response.requestId.length > 100)) || typeof response.code !== 'string' || !codes.has(response.code as MatchResultCode)) throw Error('Invalid match result response');
  if (response.code !== 'ACCEPTED' && (response.result !== null || response.event !== null)) throw Error('Inconsistent result response'); if (response.code === 'ACCEPTED' && response.result === null) throw Error('Missing result metadata');
  let result: MatchResultMetadata | null = null;
  if (response.result !== null) { const value = object(response.result, ['formatVersion', 'engineVersion', 'ruleset', 'catalogVersion', 'presetId', 'presetVersion', 'matchId', 'homeScore', 'awayScore', 'finalRevision', 'eventCount']); if (value.formatVersion !== 1 || value.engineVersion !== 'ffb-3.4.0-bb2025-m3d.1' || value.ruleset !== 'BB2025' || value.catalogVersion !== 'bb2025-human-2026-09-08.1' || value.presetId !== 'human-exhibition-1150' || value.presetVersion !== 'bb2025-human-2026-09-08.1') throw Error('Unsupported result metadata'); text(value.matchId, 36); if (!uuid.test(value.matchId)) throw Error('Invalid match id'); integer(value.homeScore, 100); integer(value.awayScore, 100); integer(value.finalRevision, 8192); integer(value.eventCount, 8193); if (value.eventCount !== value.finalRevision + 1) throw Error('Invalid event count'); result = value as MatchResultMetadata; }
  let event: ReplayEvent | null = null;
  if (response.event !== null) { if (!result) throw Error('Event without result'); const value = object(response.event, ['revision', 'kind', 'state']); integer(value.revision, result.finalRevision); if (!['START', 'ACTION', 'SELECTION', 'TOUCHDOWN', 'HALFTIME', 'FULL_TIME'].includes(value.kind as string)) throw Error('Invalid event kind'); const state = decodeSetupStateValue(value.state); if (state.matchId !== result.matchId || state.revision !== value.revision || state.callerRole !== 'home' || state.actions.length !== 0 || state.prompt !== null) throw Error('Invalid replay event'); event = { revision: value.revision, kind: value.kind as string, state }; }
  return { ...response, result, event } as MatchResultResponse;
}
