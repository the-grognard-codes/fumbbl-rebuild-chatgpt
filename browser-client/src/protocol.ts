export type Role = 'home' | 'away';
export interface Point { x: number; y: number }
export type PlayerState = 'standing' | 'prone' | 'stunned' | 'other';
export interface Player extends Point { id: string; role: Role; state: PlayerState; movementUsed: number; movementAllowance: number }
export interface Resources { rerolls: number; rerollUsed: boolean; blitzUsed: boolean; passUsed: boolean; handOverUsed: boolean; foulUsed: boolean }
export interface ChoiceOption { id: string; label: string }
export interface Prompt { id: string; type: 'block'; actor: Role; revision: number; options: ChoiceOption[] }
export interface Snapshot {
  version: 1; type: 'snapshot'; revision: number; matchId: string; actor: Role;
  players: Player[]; ball: Point | null; turnOwner: Role; prompt: Prompt | null;
  resources: Record<Role, Resources>;
}
export interface Result {
  version: 1; type: 'result'; requestId: string | null; status: 'accepted' | 'rejected';
  code: string; revision: number; duplicate: boolean;
}
export interface Move { version: 1; type: 'move'; requestId: string; expectedRevision: number; playerId: string; to: Point }
export interface Choice { version: 1; type: 'choice'; requestId: string; expectedRevision: number; choiceId: string; optionId: string }
type ObjectValue = Record<string, unknown>;
const object = (v: unknown): v is ObjectValue => typeof v === 'object' && v !== null && !Array.isArray(v);
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const role = (v: unknown): v is Role => v === 'home' || v === 'away';
const playerState = (v: unknown): v is PlayerState => v === 'standing' || v === 'prone' || v === 'stunned' || v === 'other';
const point = (v: unknown): v is Point => object(v) && integer(v.x) && v.x < 26 && integer(v.y) && v.y < 15;
const resources = (v: unknown): v is Resources => object(v) && integer(v.rerolls)
  && ['rerollUsed', 'blitzUsed', 'passUsed', 'handOverUsed', 'foulUsed'].every(key => typeof v[key] === 'boolean');
const prompt = (v: unknown, revision: number): v is Prompt => object(v) && typeof v.id === 'string' && v.type === 'block'
  && role(v.actor) && v.revision === revision && Array.isArray(v.options) && v.options.length > 0
  && v.options.every(option => object(option) && typeof option.id === 'string' && typeof option.label === 'string')
  && new Set(v.options.map(option => (option as ChoiceOption).id)).size === v.options.length;
export function decode(raw: string): Snapshot | Result {
  const v: unknown = JSON.parse(raw);
  if (!object(v) || v.version !== 1 || !integer(v.revision)) throw Error('Unsupported server message');
  if (v.type === 'result' && (typeof v.requestId === 'string' || v.requestId === null)
    && (v.status === 'accepted' || v.status === 'rejected') && typeof v.code === 'string' && typeof v.duplicate === 'boolean') return v as unknown as Result;
  if (v.type === 'snapshot' && typeof v.matchId === 'string' && role(v.actor) && role(v.turnOwner)
    && object(v.resources) && resources(v.resources.home) && resources(v.resources.away)
    && (v.prompt === null || prompt(v.prompt, v.revision)) && (v.ball === null || point(v.ball)) && Array.isArray(v.players)
    && v.players.length > 0 && v.players.every(p => point(p) && object(p) && typeof p.id === 'string'
      && role(p.role) && playerState(p.state) && integer(p.movementUsed) && integer(p.movementAllowance))
    && new Set(v.players.map(p => p.id)).size === v.players.length) return v as unknown as Snapshot;
  throw Error('Invalid server message');
}
// Result revisions describe the request, not a new view. Only snapshots update the board.
export function newerSnapshot(current: Snapshot | null, incoming: Snapshot): Snapshot {
  if (current && (incoming.matchId !== current.matchId || incoming.actor !== current.actor)) throw Error('Session identity changed');
  return current && incoming.revision <= current.revision ? current : incoming;
}
