package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.FantasyFootballServer;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Invoked on the existing communication worker; optional private checkpoints preserve activated engines. */
public final class SetupApplication {
	private final FantasyFootballServer server;
	private final MatchService matches;
	private final RecoveryRepository recovery;
	private final Map<String, Long> generations = new LinkedHashMap<>();
	private final Map<String, SetupSession> sessions = new LinkedHashMap<>();
	private final Map<String, String> pendingActivations = new LinkedHashMap<>();
	private long engineId = -2;
	private final java.util.Set<String> completionAcknowledged = new HashSet<>();
	private final java.util.Set<String> completionBroadcasts = new HashSet<>();
	public boolean takeCompletionBroadcast(String matchId) { return completionBroadcasts.remove(matchId); }

	public SetupApplication(FantasyFootballServer server, MatchService matches) {
		this(server, matches, null);
	}

	public SetupApplication(FantasyFootballServer server, MatchService matches, RecoveryRepository recovery) {
		this.server = server; this.matches = matches; this.recovery = recovery;
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
		if (recovery != null) return activateRecoverable(owner, text, request, matchId);
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

	private JsonObject activateRecoverable(String owner, String text, JsonObject request, String id) {
		try {
			MatchDocument document = matches.load(owner, id).document;
			if (document.lifecycle == MatchDocument.Lifecycle.AWAITING_SETUP
				&& document.documentVersion == request.get("expectedRevision").asInt()
				&& request.get("requestId").asString().matches("[A-Za-z0-9_-]{1,100}")
				&& document.request(owner, request.get("requestId").asString()) == null) {
				if (sessions.size() >= 32 && !sessions.containsKey(id)) return preparedFailure(request, "ACTIVATION_LIMIT");
				RecoveryRepository.Record staged = recovery.find(id);
				if (staged == null) {
					// Persist an unpublished initial checkpoint first. Activation can then be retried after any crash.
					SetupSession initial = new SetupSession(server, document, engineId--, true);
					if (!recovery.save(new RecoveryRepository.Record(id, 1, initial.recoveryArtifact()), 0))
						return preparedFailure(request, "CONFLICT");
				} else new SetupSession(server, document, staged.json); // Reject incompatible staged state before activation.
			}
			JsonObject response = new MatchJson().handle(matches, owner, text);
			if ("ACCEPTED".equals(response.getString("code", null)) && !sessions.containsKey(id))
				restore(id, matches.load(owner, id).document);
			return response;
		} catch (RecoveryRepository.OutcomeUnknown unknown) { return preparedFailure(request, "MATCH_OUTCOME_UNKNOWN"); }
		catch (SQLException unavailable) { return preparedFailure(request, "PERSISTENCE_FAILED"); }
		catch (MatchService.Failure rejected) { return preparedFailure(request, rejected.code); }
		catch (RuntimeException invalid) { return preparedFailure(request, "RECOVERY_CORRUPT"); }
	}

	private SetupSession restore(String id, MatchDocument document) throws SQLException {
		RecoveryRepository.Record record = recovery.find(id);
		if (record == null) return null; // Never initialize an already active pre-R2 match.
		if (sessions.size() >= 32) throw new MatchService.Failure("ACTIVATION_LIMIT");
		SetupSession session = new SetupSession(server, document, record.json);
		sessions.put(id, session);
		generations.put(id, record.generation);
		return session;
	}

	private void checkpoint(String owner, String id, SetupSession session, String before) throws SQLException {
		if (recovery == null) return;
		try {
			matches.load(owner, id); // Reauthorize before the durable mutation as well as before engine execution.
			String after = session.recoveryArtifact();
			if (after.equals(before)) return;
			long generation = generations.get(id);
			if (!recovery.save(new RecoveryRepository.Record(id, generation + 1, after), generation))
				throw new MatchService.Failure("RECOVERY_CONFLICT");
			generations.put(id, generation + 1);
		} catch (SQLException | RuntimeException failure) {
			sessions.remove(id); generations.remove(id); // Reconcile the durable outcome before any further use.
			throw failure;
		}
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
			if (recovery != null && !sessions.containsKey(id)
				&& (document.lifecycle == MatchDocument.Lifecycle.ACTIVATED || document.lifecycle == MatchDocument.Lifecycle.COMPLETED)) restore(id, document);
			if (document.lifecycle == MatchDocument.Lifecycle.COMPLETED && !sessions.containsKey(id)) {
                if (!"load".equals(request.getString("operation", null))) throw new MatchService.Failure("MATCH_COMPLETED");
                JsonObject artifact = JsonObject.readFrom(matches.result(owner, id).json());
                com.eclipsesource.json.JsonArray events = artifact.get("events").asArray();
                JsonObject terminal = events.get(events.size() - 1).asObject().get("state").asObject().set("callerRole", role);
                return new JsonObject().add("version", 1).add("type", "setupState").add("requestId", requestId)
                    .add("code", "ACCEPTED").add("duplicate", false).add("state", terminal);
            }
            if (document.lifecycle != MatchDocument.Lifecycle.COMPLETED && document.lifecycle != MatchDocument.Lifecycle.ACTIVATED) throw new MatchService.Failure("NOT_ACTIVATED");
			SetupSession session = sessions.get(id);
			if (session == null) throw new MatchService.Failure("SESSION_UNAVAILABLE");
			String before = recovery == null ? null : session.recoveryArtifact();
			JsonObject response;
			try {
				response = "load".equals(request.getString("operation", null))
					? session.reply(requestId, "ACCEPTED", false, role) : session.apply(role, request);
			} catch (RuntimeException failure) {
				checkpoint(owner, id, session, before);
				throw failure;
			}
			checkpoint(owner, id, session, before);
            // Persist before acknowledging terminal success. A retry/load reconciles without executing the engine again.
            if (session.isComplete()) {
                matches.complete(owner, id, session.completedMatch());
                if (completionAcknowledged.add(id)) completionBroadcasts.add(id);
            }
            return response;
		} catch (RecoveryRepository.OutcomeUnknown failure) { return failure(requestId, "MATCH_OUTCOME_UNKNOWN"); }
		catch (MatchService.OutcomeUnknown failure) { return failure(requestId, "MATCH_OUTCOME_UNKNOWN"); }
        catch (MatchService.Failure failure) { return failure(requestId, failure.code); }
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
