package com.fumbbl.ffb.server.team;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.local.BrowserTeamJson;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;
import com.fumbbl.ffb.server.team.bb2025.TeamValidation;

/** Explicit saved document format 1. Imported computed fields are shape-checked, then discarded on save. */
public final class SavedTeamJson {
	private final BrowserTeamJson drafts;
	public SavedTeamJson(RosterCatalog catalog) { drafts = new BrowserTeamJson(catalog); }
	public void fields(JsonObject object, String... names) { drafts.fields(object, names); }
	public void checkEnvelope(String text) { drafts.checkEnvelope(text); }
	public TeamDraft draft(JsonObject value) { return drafts.decodeDraft(value); }
	public String teamId(JsonValue value) {
		String id = value.asString();
		if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid team ID");
		return id;
	}
	public int documentVersion(JsonValue value) {
		try {
			int version = value.asInt();
			if (version < 1 || version > 2147483646) throw new IllegalArgumentException();
			return version;
		} catch (RuntimeException failure) { throw new SavedTeamService.Failure("INVALID_DOCUMENT_VERSION"); }
	}
	public SavedTeamDocument decode(String text) {
		checkEnvelope(text);
		JsonObject object = JsonObject.readFrom(text);
		fields(object, "formatVersion", "teamId", "documentVersion", "ruleset", "catalogVersion", "owner", "draft", "validation");
		if (object.get("formatVersion").asInt() != 1) throw new SavedTeamService.Failure("INVALID_DOCUMENT_VERSION");
		String id = teamId(object.get("teamId"));
		int version = documentVersion(object.get("documentVersion"));
		JsonObject owner = object.get("owner").asObject();
		fields(owner, "namespace", "subject");
		String subject = owner.get("subject").asString();
		if (!"local".equals(owner.get("namespace").asString()) || !("home".equals(subject) || "away".equals(subject))) {
			throw new IllegalArgumentException("Unsupported owner metadata");
		}
		TeamDraft draft = draft(object.get("draft").asObject());
		if (!draft.catalogVersion.equals(object.get("catalogVersion").asString()) || !draft.ruleset.equals(object.get("ruleset").asString())) {
			throw new IllegalArgumentException("Inconsistent catalog metadata");
		}
		JsonObject validation = object.get("validation").asObject();
		fields(validation, "valid", "total", "budget", "skillPoints", "messages");
		validation.get("valid").asBoolean();
		for (String key : new String[] {"total", "budget", "skillPoints"}) {
			int amount = validation.get(key).asInt();
			if (amount < 0 || amount > 5000000) throw new IllegalArgumentException("Invalid computed field");
		}
		if (validation.get("messages").asArray().size() != 0) throw new IllegalArgumentException("Only saved snapshots may be imported");
		return new SavedTeamDocument(id, subject, version, draft, validation.toString());
	}
	public JsonObject encode(SavedTeamDocument document) {
		return new JsonObject().add("formatVersion", 1).add("teamId", document.teamId)
			.add("documentVersion", document.documentVersion).add("ruleset", document.draft.ruleset)
			.add("catalogVersion", document.draft.catalogVersion)
			.add("owner", new JsonObject().add("namespace", "local").add("subject", document.owner))
			.add("draft", encodeDraft(document.draft)).add("validation", JsonObject.readFrom(document.validationJson));
	}
	public JsonObject encodeDraft(TeamDraft draft) {
		JsonArray players = new JsonArray();
		for (TeamDraft.Player player : draft.players) {
			JsonArray skills = new JsonArray();
			for (String skill : player.skillIds) skills.add(skill);
			players.add(new JsonObject().add("id", player.id).add("slot", player.slot).add("positionId", player.positionId).add("skillIds", skills));
		}
		JsonObject resources = new JsonObject();
		for (String id : new String[] {"rerolls", "assistantCoaches", "cheerleaders", "apothecary", "dedicatedFans"}) resources.add(id, draft.resources.get(id));
		return new JsonObject().add("catalogVersion", draft.catalogVersion).add("ruleset", draft.ruleset).add("rosterId", draft.rosterId)
			.add("presetId", draft.presetId).add("captainId", draft.captainId).add("players", players).add("resources", resources);
	}
	public JsonObject evaluation(TeamValidation.Evaluation evaluation) {
		JsonArray messages = new JsonArray();
		for (TeamValidation.Message message : evaluation.messages) messages.add(new JsonObject().add("code", message.code).add("path", message.path).add("text", message.text));
		return new JsonObject().add("valid", evaluation.isValid()).add("total", evaluation.total == null ? JsonValue.NULL : JsonValue.valueOf(evaluation.total))
			.add("budget", RosterCatalog.BUDGET).add("skillPoints", evaluation.skillPoints).add("messages", messages);
	}
}
