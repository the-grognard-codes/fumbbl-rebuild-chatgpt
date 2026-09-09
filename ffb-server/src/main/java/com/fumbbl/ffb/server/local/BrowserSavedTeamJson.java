package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.team.SavedTeamJson;
import com.fumbbl.ffb.server.team.SavedTeamRepository;
import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;

import java.sql.SQLException;

/** Local authenticated adapter. Errors never echo private input or JDBC exception details. */
public final class BrowserSavedTeamJson {
	private final SavedTeamService service;
	private final SavedTeamJson json = new SavedTeamJson(new RosterCatalog());
	public BrowserSavedTeamJson(SavedTeamService service) { this.service = service; }
	public JsonObject handle(String owner, String text) {
		JsonObject response = new JsonObject().add("version", 1).add("type", "savedTeam").add("requestId", JsonValue.NULL)
			.add("code", "OK").add("document", JsonValue.NULL).add("versionStatus", JsonValue.NULL)
			.add("validation", JsonValue.NULL).add("teams", new JsonArray());
		try {
			json.checkEnvelope(text);
			JsonObject request = JsonObject.readFrom(text);
			String requestId = request.get("requestId").asString();
			if (!requestId.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException();
			response.set("requestId", requestId);
			if (request.get("version").asInt() != 1 || !"savedTeam".equals(request.get("type").asString())) throw new IllegalArgumentException();
			if (!"home".equals(owner) && !"away".equals(owner)) throw new SavedTeamService.Failure("AUTHENTICATION_REQUIRED");
			String operation = request.get("operation").asString();
			SavedTeamService.Loaded loaded;
			switch (operation) {
				case "list":
					json.fields(request, "version", "type", "requestId", "operation");
					JsonArray teams = new JsonArray();
					for (SavedTeamRepository.Record record : service.list(owner)) teams.add(new JsonObject().add("teamId", record.teamId)
						.add("documentVersion", record.documentVersion).add("catalogVersion", record.catalogVersion));
					return response.set("teams", teams);
				case "load":
					json.fields(request, "version", "type", "requestId", "operation", "teamId");
					loaded = service.load(owner, json.teamId(request.get("teamId"))); break;
				case "create":
					json.fields(request, "version", "type", "requestId", "operation", "draft");
					loaded = service.create(owner, json.draft(request.get("draft").asObject())); break;
				case "update":
					json.fields(request, "version", "type", "requestId", "operation", "teamId", "expectedDocumentVersion", "draft");
					loaded = service.update(owner, json.teamId(request.get("teamId")), json.documentVersion(request.get("expectedDocumentVersion")),
						json.draft(request.get("draft").asObject())); break;
				case "import":
					json.fields(request, "version", "type", "requestId", "operation", "document");
					loaded = service.importDocument(owner, request.get("document").asObject().toString()); break;
				default: throw new IllegalArgumentException();
			}
			return response.set("document", json.encode(loaded.document)).set("versionStatus", loaded.versionStatus)
				.set("validation", json.evaluation(loaded.validation));
		} catch (SavedTeamService.Failure failure) {
			response.set("code", failure.code);
			if (failure.validation != null) response.set("validation", json.evaluation(failure.validation));
			if ("MIGRATION_REQUIRED".equals(failure.code) || "VERSION_UNAVAILABLE".equals(failure.code)) response.set("versionStatus", failure.code);
		} catch (SavedTeamRepository.OutcomeUnknown failure) {
			// This is the attempted snapshot, explicitly NOT a successful saved document response.
			response.set("code", "SAVE_OUTCOME_UNKNOWN").set("document", JsonObject.readFrom(failure.attempted.json))
				.set("versionStatus", "CURRENT").set("validation", JsonObject.readFrom(failure.attempted.json).get("validation"));
		} catch (SQLException failure) {
			response.set("code", "54000".equals(failure.getSQLState()) ? "LIMIT_REACHED" : "PERSISTENCE_FAILED");
		} catch (RuntimeException failure) { response.set("code", "MALFORMED_TEAM_REQUEST"); }
		return response;
	}
}
