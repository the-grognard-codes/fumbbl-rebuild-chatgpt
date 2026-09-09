export type ResourceId = 'rerolls' | 'assistantCoaches' | 'cheerleaders' | 'apothecary' | 'dedicatedFans';
export type DraftPlayer = { id: string; slot: number; positionId: string; skillIds: string[] };
export type TeamDraft = { catalogVersion: string; ruleset: 'BB2025'; rosterId: string; presetId: string; captainId: string | null; players: DraftPlayer[]; resources: Record<ResourceId, number> };
export type Position = { id: string; name: string; maximum: number; cost: number; ma: number; st: number; ag: number; pa: number; av: number; role: string; race: string; primary: string; secondary: string; baseSkills: { id: string; value: number }[]; canCaptain: boolean };
export type Skill = { id: string; name: string; category: string; selectable: boolean; elite: boolean };
type Header = { version: 1; requestId: string; catalogVersion: string; ruleset: 'BB2025' };
export type Catalog = Header & { type: 'catalog'; rosterId: string; name: string; presetId: string; budget: number; minPlayers: number; maxPlayers: number; skillPoints: number; maxSecondary: number; maxElite: number; league: string; specialRule: string; positions: Position[]; skills: Skill[]; resources: { id: ResourceId; name: string; cost: number; maximum: number }[]; unsupported: string };
export type Validation = Header & { type: 'teamValidation'; valid: boolean; total: number | null; budget: number; skillPoints: number; messages: { code: string; path: string; text: string }[] };
export const catalogVersion = 'bb2025-human-2026-09-08.1';

function object(value: unknown, keys: string[]): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('Expected object');
  const result = value as Record<string, unknown>;
  if (Object.keys(result).length !== keys.length || keys.some(key => !Object.hasOwn(result, key))) throw Error('Unexpected fields');
  return result;
}
function text(value: unknown, max = 300): asserts value is string { if (typeof value !== 'string' || !value.length || value.length > max) throw Error('Invalid text'); }
function integer(value: unknown, max = 2000000): asserts value is number { if (!Number.isSafeInteger(value) || (value as number) < 0 || (value as number) > max) throw Error('Invalid integer'); }
function bool(value: unknown): asserts value is boolean { if (typeof value !== 'boolean') throw Error('Invalid boolean'); }
function array(value: unknown, max: number): unknown[] { if (!Array.isArray(value) || value.length > max) throw Error('Invalid array'); return value; }
function unique(items: unknown[], key: string) { const ids = items.map(item => (item as Record<string, unknown>)[key]); if (new Set(ids).size !== ids.length) throw Error('Duplicate identifier'); }

export function decodeTeam(textJson: string): Catalog | Validation {
  if (new TextEncoder().encode(textJson).length > 16384) throw Error('Response too large');
  const value: unknown = JSON.parse(textJson);
  if (!value || typeof value !== 'object') throw Error('Invalid message');
  const kind = (value as Record<string, unknown>).type;
  const header = ['version', 'type', 'requestId', 'catalogVersion', 'ruleset'];
  const message = object(value, [...header, ...(kind === 'catalog'
    ? ['rosterId', 'name', 'presetId', 'budget', 'minPlayers', 'maxPlayers', 'skillPoints', 'maxSecondary', 'maxElite', 'league', 'specialRule', 'positions', 'skills', 'resources', 'unsupported']
    : ['valid', 'budget', 'skillPoints', 'messages', 'total'])]);
  if (message.version !== 1 || message.ruleset !== 'BB2025' || message.catalogVersion !== catalogVersion) throw Error('Unsupported catalog or ruleset');
  text(message.requestId, 100); integer(message.budget); integer(message.skillPoints, 32);
  if (kind === 'catalog') {
    for (const key of ['rosterId', 'name', 'presetId', 'league', 'specialRule', 'unsupported']) text(message[key]);
    for (const key of ['minPlayers', 'maxPlayers', 'maxSecondary', 'maxElite']) integer(message[key], 16);
    const skills = array(message.skills, 32);
    for (const value of skills) {
      const skill = object(value, ['id', 'name', 'category', 'selectable', 'elite']);
      text(skill.id, 80); text(skill.name, 80); text(skill.category, 1);
      if (!'ADGMPST'.includes(skill.category)) throw Error('Unknown skill category');
      bool(skill.selectable); bool(skill.elite);
    }
    unique(skills, 'id');
    const skillIds = new Set(skills.map(skill => (skill as Skill).id));
    const positions = array(message.positions, 16);
    if (!positions.length) throw Error('Empty catalog');
    for (const value of positions) {
      const position = object(value, ['id', 'name', 'maximum', 'cost', 'ma', 'st', 'ag', 'pa', 'av', 'role', 'race', 'primary', 'secondary', 'baseSkills', 'canCaptain']);
      for (const key of ['id', 'name', 'role', 'race', 'primary', 'secondary']) text(position[key], 80);
      for (const key of ['ma', 'st', 'ag', 'pa', 'av', 'maximum']) integer(position[key], 16);
      integer(position.cost); bool(position.canCaptain);
      const base = array(position.baseSkills, 16);
      for (const value of base) { const skill = object(value, ['id', 'value']); if (!skillIds.has(skill.id as string)) throw Error('Unresolved base skill'); integer(skill.value, 6); }
      unique(base, 'id');
    }
    unique(positions, 'id');
    const resources = array(message.resources, 5);
    const ids = ['rerolls', 'assistantCoaches', 'cheerleaders', 'apothecary', 'dedicatedFans'];
    if (resources.length !== ids.length) throw Error('Missing resources');
    for (const value of resources) { const resource = object(value, ['id', 'name', 'cost', 'maximum']); if (!ids.includes(resource.id as string)) throw Error('Unknown resource'); text(resource.name); integer(resource.cost); integer(resource.maximum, 8); }
    unique(resources, 'id');
    return message as Catalog;
  }
  if (kind !== 'teamValidation') throw Error('Unknown response type');
  bool(message.valid);
  if (message.total !== null) integer(message.total, 5000000);
  const messages = array(message.messages, 100);
  for (const value of messages) { const item = object(value, ['code', 'path', 'text']); text(item.code, 80); text(item.path, 100); text(item.text); }
  if (message.valid !== (messages.length === 0) || (message.valid && message.total === null)) throw Error('Inconsistent validation');
  return message as Validation;
}

export function emptyDraft(catalog: Catalog): TeamDraft {
  return { catalogVersion: catalog.catalogVersion, ruleset: catalog.ruleset, rosterId: catalog.rosterId, presetId: catalog.presetId, captainId: null, players: [], resources: { rerolls: 0, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } };
}
