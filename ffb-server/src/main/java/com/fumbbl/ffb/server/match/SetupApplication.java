package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.FantasyFootballServer;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Invoked on the existing communication worker. No recovery or reconstruction of activated engines. */
public final class SetupApplication {
	private final FantasyFootballServer server;
	private final MatchService matches;
	private final Map<String, SetupSession> sessions = new LinkedHashMap<>();
	private final Map<String, String> pendingActivations = new LinkedHashMap<>();
	private long engineId = -2;

	public SetupApplication(FantasyFootballServer server, MatchService matches) {
		this.server = server; this.matches = matches;
	}

	public JsonObject activate(String owner, String text) {
		JsonObject request = JsonObject.readFrom(text);
		String matchId, key;
		try {
			if (request.size() != 6 || !new HashSet<>(request.names()).equals(new HashSet<>(Arrays.asList(
				"version", "type", "operation", "requestId", "matchId", "expectedRevision")))) throw new IllegalArgumentException();
			if (request.get("version").asInt() != 1 || !"preparedMatch".equals(request.get("type").asString())
				|| !"activate".equals(request.get("operation").asString())) throw new IllegalArgumentException();
			matchId = request.get("matchId").asString();
			key = owner + "|" + request.get("requestId").asString() + "|" + request.get("expectedRevision").asInt();
		} catch (RuntimeException invalid) { return new MatchJson().handle(matches, owner, text); }
		// Keep resident engines bounded; never evict a live lifetime and permit reinitialization.
		try {
			MatchDocument document = matches.load(owner, matchId).document;
			if (document.lifecycle == MatchDocument.Lifecycle.AWAITING_SETUP && !sessions.containsKey(matchId)) {
				if (sessions.size() + pendingActivations.size() >= 32 && !pendingActivations.containsKey(matchId))
					return new JsonObject().add("version", 1).add("type", "preparedMatch").add("requestId", request.get("requestId"))
						.add("code", "ACTIVATION_LIMIT").add("duplicate", false).add("callerRole", JsonValue.NULL)
						.add("document", JsonValue.NULL).add("recoveryMatchId", JsonValue.NULL);
				pendingActivations.put(matchId, key);
			}
		} catch (SQLException failure) {
			return preparedFailure(request, "PERSISTENCE_FAILED");
		} catch (MatchService.Failure failure) {
			return preparedFailure(request, failure.code);
		}
		JsonObject response = new MatchJson().handle(matches, owner, text);
		if ("ACCEPTED".equals(response.getString("code", null)) && key.equals(pendingActivations.get(matchId))
			&& !sessions.containsKey(matchId)) {
			String id = response.get("document").asObject().getString("matchId", null);
			// A local pending reservation permits reconciliation; a retry after restart has no reservation.
			// Reserve the lifetime before initialization, including failures. Never attempt initialization twice.
			pendingActivations.remove(id);
			sessions.put(id, null);
			try {
				sessions.put(id, new SetupSession(server, matches.load(owner, id).document, engineId--));
			} catch (SQLException | RuntimeException failure) {
				// ACTIVATED remains durable; load reports SESSION_UNAVAILABLE. No rollback or phantom recovery.
			}
		} else if (!"MATCH_OUTCOME_UNKNOWN".equals(response.getString("code", null)) && key.equals(pendingActivations.get(matchId))) {
			pendingActivations.remove(matchId);
		}
		return response;
	}

	private JsonObject preparedFailure(JsonObject request, String code) {
		return new JsonObject().add("version", 1).add("type", "preparedMatch").add("requestId", request.get("requestId"))
			.add("code", code).add("duplicate", false).add("callerRole", JsonValue.NULL)
			.add("document", JsonValue.NULL).add("recoveryMatchId", JsonValue.NULL);
	}

	public JsonObject handle(String owner, JsonObject request) {
		String requestId = request.getString("requestId", null);
		try {
			validate(request);
			String id = request.getString("matchId", null);
			MatchDocument document = matches.load(owner, id).document;
			String role = owner.equals(document.home.owner) ? "home"
				: document.away != null && owner.equals(document.away.owner) ? "away" : null;
			if (role == null) throw new MatchService.Failure("NOT_FOUND");
			if (document.lifecycle != MatchDocument.Lifecycle.ACTIVATED) throw new MatchService.Failure("NOT_ACTIVATED");
			SetupSession session = sessions.get(id);
			if (session == null) throw new MatchService.Failure("SESSION_UNAVAILABLE");
			return "load".equals(request.getString("operation", null))
				? session.reply(requestId, "ACCEPTED", false, role) : session.apply(role, request);
		} catch (MatchService.Failure failure) { return failure(requestId, failure.code); }
		catch (SQLException failure) { return failure(requestId, "PERSISTENCE_FAILED"); }
		catch (RuntimeException failure) { return failure(requestId, "INVALID_REQUEST"); }
	}

	public JsonObject failure(String id, String code) {
		return new JsonObject().add("version", 1).add("type", "setupState").add("requestId", id)
			.add("code", code).add("duplicate", false).add("state", JsonValue.NULL);
	}

	private void validate(JsonObject request) {
		String operation = request.get("operation").asString();
		String[] base = { "version", "type", "operation", "requestId", "matchId" };
		java.util.List<String> fields = new java.util.ArrayList<>(Arrays.asList(base));
		if (!"load".equals(operation)) {
			fields.add("expectedRevision");
			if (request.get("expectedRevision").asInt() < 0) throw new IllegalArgumentException();
		}
		switch (operation) {
			case "load": case "confirm": break;
			case "action": fields.add("actionId"); if (request.get("actionId").asString().length() > 200) throw new IllegalArgumentException(); break;
			case "choice": fields.add("promptId"); fields.add("optionId"); request.get("promptId").asString(); request.get("optionId").asString(); break;
			case "place":
				fields.add("playerId"); fields.add("to"); request.get("playerId").asString();
				if (!request.get("to").isNull()) {
					JsonObject to = request.get("to").asObject();
					if (to.size() != 2 || !new HashSet<>(to.names()).equals(new HashSet<>(Arrays.asList("x", "y")))) throw new IllegalArgumentException();
					to.get("x").asInt(); to.get("y").asInt();
				}
				break;
			default: throw new IllegalArgumentException();
		}
		if (request.size() != fields.size() || !new HashSet<>(request.names()).equals(new HashSet<>(fields))
			|| request.get("version").asInt() != 1 || !"setup".equals(request.get("type").asString())
			|| !request.get("requestId").asString().matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException();
	}
}
