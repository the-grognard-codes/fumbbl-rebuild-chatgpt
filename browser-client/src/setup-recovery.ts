import type { SetupCode } from './setup-protocol.ts';

export type RetainedSetup = { request: Record<string, unknown>; subject: string; matchId: string };
export const setupRetryKey = 'ffb.setup.pending.v1';

/** Storage is untrusted; only the bounded setup intent is retained, never credentials. */
export function decodeRetainedSetup(raw: string | null): RetainedSetup | null {
  if (!raw || raw.length > 4096) return null;
  try {
    const value = JSON.parse(raw);
    const request = value?.request;
    if (!value || Object.keys(value).sort().join() !== 'matchId,request,subject'
      || !['home', 'away'].includes(value.subject) || typeof value.matchId !== 'string'
      || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(value.matchId)
      || !request || request.version !== 1 || request.type !== 'setup'
      || request.matchId !== value.matchId || typeof request.requestId !== 'string'
      || !request.requestId || request.requestId.length > 100
      || !Number.isSafeInteger(request.expectedRevision) || request.expectedRevision < 0) return null;
    const fields: Record<string, string[]> = { choice: ['promptId', 'optionId'], place: ['playerId', 'to'], confirm: [], action: ['actionId'] };
    if (!Object.hasOwn(fields, request.operation)) return null;
    const keys = ['version', 'type', 'operation', 'requestId', 'matchId', 'expectedRevision', ...fields[request.operation]];
    if (Object.keys(request).sort().join() !== keys.sort().join()) return null;
    return value;
  } catch { return null; }
}

export function setupOutcomeUncertain(code: SetupCode): boolean {
  return code === 'PERSISTENCE_FAILED' || code === 'MATCH_OUTCOME_UNKNOWN' || code === 'COMPLETION_PENDING';
}
