package com.fumbbl.ffb.server.team;

import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

/** Immutable snapshot; validationJson is a narrow value contract, not an engine object graph. */
public final class SavedTeamDocument {
	public final String teamId, owner, validationJson;
	public final int documentVersion;
	public final TeamDraft draft;
	public SavedTeamDocument(String teamId, String owner, int documentVersion, TeamDraft draft, String validationJson) {
		this.teamId = teamId; this.owner = owner; this.documentVersion = documentVersion;
		this.draft = draft; this.validationJson = validationJson;
	}
}
