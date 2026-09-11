import { parseUniqueJson } from './saved-team-protocol.ts';
import type { MatchRole } from './prepared-match-protocol.ts';

export type SetupCode = 'ACCEPTED' | 'SESSION_UNAVAILABLE' | 'NOT_ACTIVATED' | 'WRONG_ACTOR' | 'WRONG_PHASE' | 'WRONG_PLAYER' | 'ILLEGAL_PLACEMENT' | 'ILLEGAL_SETUP' | 'PROMPT_MISMATCH' | 'INVALID_OPTION' | 'REQUEST_ID_REUSED' | 'REQUEST_HISTORY_LIMIT' | 'STALE_REVISION' | 'INVALID_REQUEST' | 'NOT_FOUND' | 'AUTHENTICATION_REQUIRED' | 'PERSISTENCE_FAILED' | 'SNAPSHOT_UNSUPPORTED' | 'REPLAY_UNSUPPORTED' | 'MATCH_COMPLETED' | 'COMPLETION_PENDING' | 'MATCH_OUTCOME_UNKNOWN' | 'COMPLETION_CONFLICT' | 'REPLAY_LIMIT' | 'RECOVERY_UNSUPPORTED' | 'RECOVERY_CORRUPT' | 'RECOVERY_CONFLICT' | 'RECOVERY_LIMIT' | 'ACTIVATION_LIMIT';
export type SetupPlayer = { id: string; name: string; slot: number; role: MatchRole; x: number | null; y: number | null; state: string };
export type SetupPrompt = { id: string; actor: MatchRole; kind: 'coin' | 'receive'; options: ('heads' | 'tails' | 'receive' | 'kick')[] };
export type SetupAction = { id: string; label: string; actor: MatchRole; kind: string };
export type SetupState = { matchId: string; revision: number; callerRole: MatchRole; phase: 'PRE_MATCH' | 'SETUP' | 'READY_FOR_KICKOFF' | 'PLAY' | 'FULL_TIME'; actor: MatchRole; prompt: SetupPrompt | null; players: SetupPlayer[]; weather: string; homeRerolls: number; awayRerolls: number; actions: SetupAction[]; turn: number; turnMode: string; ball: { x: number; y: number } | null; activePlayerId: string | null; half: number; homeTurn: number; awayTurn: number; homeScore: number; awayScore: number; drive: number };
export type SetupResponse = { version: 1; type: 'setupState'; requestId: string | null; code: SetupCode; duplicate: boolean; state: SetupState | null };
const codes = new Set<SetupCode>(['ACCEPTED','SESSION_UNAVAILABLE','NOT_ACTIVATED','WRONG_ACTOR','WRONG_PHASE','WRONG_PLAYER','ILLEGAL_PLACEMENT','ILLEGAL_SETUP','PROMPT_MISMATCH','INVALID_OPTION','REQUEST_ID_REUSED','REQUEST_HISTORY_LIMIT','STALE_REVISION','INVALID_REQUEST','NOT_FOUND','AUTHENTICATION_REQUIRED','PERSISTENCE_FAILED','SNAPSHOT_UNSUPPORTED','REPLAY_UNSUPPORTED','MATCH_COMPLETED','COMPLETION_PENDING','MATCH_OUTCOME_UNKNOWN','COMPLETION_CONFLICT','REPLAY_LIMIT','RECOVERY_UNSUPPORTED','RECOVERY_CORRUPT','RECOVERY_CONFLICT','RECOVERY_LIMIT','ACTIVATION_LIMIT']);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function object(value: unknown, keys: string[]) { if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Expected object'); const result = value as Record<string, unknown>; if (Object.keys(result).length !== keys.length || keys.some(key => !Object.hasOwn(result, key))) throw Error('Unexpected fields'); return result; }
function role(value: unknown): asserts value is MatchRole { if (value !== 'home' && value !== 'away') throw Error('Invalid role'); }
function text(value: unknown, maximum = 100): asserts value is string { if (typeof value !== 'string' || !value || value.length > maximum) throw Error('Invalid text'); }
function integer(value: unknown, maximum = 5_000_000): asserts value is number { if (!Number.isSafeInteger(value) || (value as number) < 0 || (value as number) > maximum) throw Error('Invalid integer'); }
export function decodeSetupStateValue(value: unknown): SetupState {
	const result = object(value, ['matchId','revision','callerRole','phase','actor','prompt','players','weather','homeRerolls','awayRerolls','actions','turn','turnMode','ball','activePlayerId','half','homeTurn','awayTurn','homeScore','awayScore','drive']); text(result.matchId, 36); if (!uuid.test(result.matchId)) throw Error('Invalid match'); integer(result.revision, 2147483646); role(result.callerRole); role(result.actor);
	if ((result.phase !== 'PRE_MATCH' && result.phase !== 'SETUP' && result.phase !== 'READY_FOR_KICKOFF' && result.phase !== 'PLAY' && result.phase !== 'FULL_TIME') || !Array.isArray(result.players) || result.players.length > 32) throw Error('Invalid state'); text(result.weather); integer(result.homeRerolls, 100); integer(result.awayRerolls, 100); integer(result.turn); text(result.turnMode); integer(result.half, 2); if (result.half < 1) throw Error('Invalid half'); integer(result.homeTurn, 8); integer(result.awayTurn, 8); integer(result.homeScore, 100); integer(result.awayScore, 100); integer(result.drive, 100); if (result.drive < 1) throw Error('Invalid drive');
	const players = result.players.map(value => { const player = object(value, ['id','name','slot','role','x','y','state']); text(player.id); text(player.name); integer(player.slot, 16); if (player.slot < 1) throw Error('Invalid slot'); role(player.role); text(player.state); if ((player.x === null) !== (player.y === null)) throw Error('Invalid coordinate'); if (player.x !== null) { integer(player.x, 25); integer(player.y, 14); } return player as SetupPlayer; });
	if (new Set(players.map(player => player.id)).size !== players.length) throw Error('Repeated player');
	if (!Array.isArray(result.actions) || result.actions.length > 8192) throw Error('Invalid actions');
	const actions = result.actions.map(value => { const action = object(value, ['id','label','actor','kind']); text(action.id, 200); text(action.label, 200); role(action.actor); text(action.kind, 60); return action as SetupAction; });
	if (new Set(actions.map(action => action.id)).size !== actions.length) throw Error('Repeated action');
	let ball: { x: number; y: number } | null = null;
	if (result.ball !== null) { const value = object(result.ball, ['x','y']); integer(value.x, 25); integer(value.y, 14); ball = value as { x: number; y: number }; }
	if (result.activePlayerId !== null) text(result.activePlayerId);
	let prompt: SetupPrompt | null = null;
	if (result.prompt !== null) { const value = object(result.prompt, ['id','actor','kind','options']); text(value.id); role(value.actor); if ((value.kind !== 'coin' && value.kind !== 'receive') || !Array.isArray(value.options) || value.options.length !== 2) throw Error('Invalid prompt'); const expected = value.kind === 'coin' ? ['heads','tails'] : ['receive','kick']; if (!value.options.every(option => typeof option === 'string' && expected.includes(option)) || new Set(value.options).size !== 2) throw Error('Invalid options'); prompt = value as SetupPrompt; }
	return { ...result, players, actions, ball, prompt } as SetupState;
}
export function decodeSetupState(json: string): SetupResponse {
	if (new TextEncoder().encode(json).length > 65536) throw Error('Response too large'); const result = object(parseUniqueJson(json), ['version','type','requestId','code','duplicate','state']);
	if (result.version !== 1 || result.type !== 'setupState' || (result.requestId !== null && (typeof result.requestId !== 'string' || !result.requestId || result.requestId.length > 100)) || typeof result.code !== 'string' || !codes.has(result.code as SetupCode) || typeof result.duplicate !== 'boolean') throw Error('Invalid setup response');
	if (result.code === 'ACCEPTED' || result.code === 'ILLEGAL_SETUP' ? result.state === null : result.state !== null || result.duplicate) throw Error('Inconsistent setup response');
	return { ...result, state: result.state === null ? null : decodeSetupStateValue(result.state) } as SetupResponse;
}
/** Client-side affordance only; the server remains authoritative for placement legality. */
export function canPlaceReserve(state: SetupState, playerId: string, x: number, y: number): boolean {
	const player = state.players.find(item => item.id === playerId);
	return state.phase === 'SETUP' && state.actor === state.callerRole && !!player && player.role === state.callerRole && Number.isSafeInteger(x) && Number.isSafeInteger(y)
		&& y >= 0 && y < 15 && (state.callerRole === 'home' ? x >= 0 && x <= 12 : x >= 13 && x < 26)
		&& !state.players.some(item => item.x === x && item.y === y);
}
