import type { TeamDraft } from './team-protocol';
import type { SavedDocument, SavedTeamResponse } from './saved-team-protocol';

export type SavedTeamUiState = { saved: SavedDocument | null; draft: TeamDraft | null; versionStatus: string | null; dirty: boolean; teams: SavedTeamResponse['teams']; status: string };

/** Applies only authoritative loads/saves; conflict and validation errors retain local edits. */
export function applySavedTeamResponse(state: SavedTeamUiState, response: SavedTeamResponse): SavedTeamUiState {
  const next = { ...state, status: response.code === 'OK' ? 'Connected' : `Saved team: ${response.code}` };
  if (response.teams.length || (response.code === 'OK' && response.document === null)) next.teams = response.teams;
  if (response.document && (response.code === 'OK' || response.code === 'MIGRATION_REQUIRED' || response.code === 'VERSION_UNAVAILABLE')) {
    next.saved = response.document; next.draft = response.document.draft; next.dirty = false;
  }
  if (response.document || response.versionStatus !== null) next.versionStatus = response.versionStatus;
  return next;
}

export function canEditSavedTeam(catalogVersion: string | null, state: Pick<SavedTeamUiState, 'saved' | 'draft' | 'versionStatus'>, pending: boolean) {
  return !!catalogVersion && !!state.draft && !pending && (state.versionStatus === null || state.versionStatus === 'CURRENT') && (!state.saved || state.saved.catalogVersion === catalogVersion);
}
