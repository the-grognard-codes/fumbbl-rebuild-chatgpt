package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Format 1: separate client choices, public projection and private frozen persistence. */
public final class MatchJson {
	public JsonObject handle(MatchService service, String owner, String text) {
		String requestId = null;
		try {
			JsonObject request = parse(text, 16384, 8);
			requestId = request.get("requestId").asString();
			if (!requestId.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException();
			if (request.get("version").asInt() != 1 || !"preparedMatch".equals(request.get("type").asString()))
				throw new MatchService.Failure("UNSUPPORTED_VERSION");
			MatchService.Result result;
			switch (request.get("operation").asString()) {
				case "create":
					exact(request, "version", "type", "operation", "requestId", "teamId", "expectedDocumentVersion", "intendedOpponent");
					result = service.create(owner, requestId, uuid(request.get("teamId")), positive(request.get("expectedDocumentVersion")), request.get("intendedOpponent").asString());
					break;
				case "join":
					exact(request, "version", "type", "operation", "requestId", "matchId", "expectedRevision", "teamId", "expectedDocumentVersion");
					result = service.join(owner, requestId, uuid(request.get("matchId")), positive(request.get("expectedRevision")), uuid(request.get("teamId")), positive(request.get("expectedDocumentVersion")));
					break;
				case "activate":
					exact(request, "version", "type", "operation", "requestId", "matchId", "expectedRevision");
					result = service.activate(owner, requestId, uuid(request.get("matchId")), positive(request.get("expectedRevision")));
					break;
				case "load":
					exact(request, "version", "type", "operation", "requestId", "matchId");
					result = service.load(owner, uuid(request.get("matchId")));
					break;
				default: throw new IllegalArgumentException();
			}
			return response(requestId, "ACCEPTED", result.duplicate, role(result.document, owner), publicDocument(result.document), null);
		} catch (MatchService.OutcomeUnknown failure) {
			return response(requestId, "MATCH_OUTCOME_UNKNOWN", false, null, null, failure.matchId);
		} catch (MatchService.Failure failure) {
			return response(requestId, failure.code, false, null, null, null);
		} catch (SavedTeamService.Failure failure) {
			return response(requestId, failure.code, false, null, null, null);
		} catch (SQLException failure) {
			return response(requestId, "PERSISTENCE_FAILED", false, null, null, null);
		} catch (RuntimeException failure) {
			return response(requestId, "INVALID_REQUEST", false, null, null, null);
		}
	}

	public JsonObject encode(MatchDocument document) {
		JsonObject out = publicDocument(document).add("homeOwner", document.home.owner);
		out.get("home").asObject().add("resolvedCatalog", JsonObject.readFrom(document.home.team.resolvedCatalogJson));
		if (document.away != null) out.get("away").asObject().add("owner", document.away.owner)
			.add("resolvedCatalog", JsonObject.readFrom(document.away.team.resolvedCatalogJson));
		JsonArray requests = new JsonArray();
		for (Map.Entry<String, MatchDocument.Request> entry : document.requests.entrySet())
			requests.add(new JsonObject().add("key", entry.getKey()).add("fingerprint", entry.getValue().fingerprint));
		out.add("requests", requests);
		if (document.completion != null) return out.set("formatVersion", 2).add("completion", JsonObject.readFrom(document.completion.json()));
		return out;
	}

	/** Inspect minimal persisted membership before reporting a format incompatibility to a caller. */
	void authorizePersisted(String owner, String text) {
		try {
			JsonObject object = parse(text, 16 * 1024 * 1024 + 65536, 16);
			String creator = object.get("homeOwner").asString();
			String invited = object.get("invitation").asObject().get("intendedOpponent").asString();
			if (!owner.equals(creator) && !owner.equals(invited)) throw new IllegalArgumentException();
		} catch (RuntimeException failure) { throw new MatchService.Failure("NOT_FOUND"); }
	}

	public MatchDocument decode(String text, int persistedVersion) {
		try {
			JsonObject object = parse(text, 16 * 1024 * 1024 + 65536, 16);
			if (object.getInt("formatVersion", -1) == 2) return decodeCompleted(object, persistedVersion);
			exact(object, "formatVersion", "matchId", "documentVersion", "lifecycle", "invitation", "home", "away", "homeOwner", "requests");
			if (object.get("formatVersion").asInt() != 1 || positive(object.get("documentVersion")) != persistedVersion) throw new IllegalArgumentException();
			String id = uuid(object.get("matchId")), creator = subject(object.get("homeOwner"));
			JsonObject invitation = object.get("invitation").asObject(); exact(invitation, "intendedOpponent");
			String invited = subject(invitation.get("intendedOpponent"));
			if (creator.equals(invited)) throw new IllegalArgumentException();
			MatchDocument.Member home = readMember(object.get("home").asObject(), creator, false);
			MatchDocument.Member away = object.get("away").isNull() ? null : readMember(object.get("away").asObject(), invited, true);
			MatchDocument.Lifecycle lifecycle = MatchDocument.Lifecycle.valueOf(object.get("lifecycle").asString());
			if (!"home".equals(home.role) || (away != null && !"away".equals(away.role))
				|| persistedVersion != (away == null ? 1 : lifecycle == MatchDocument.Lifecycle.AWAITING_SETUP ? 2 : lifecycle == MatchDocument.Lifecycle.ACTIVATED ? 3 : -1)
				|| lifecycle != (away == null ? MatchDocument.Lifecycle.WAITING_FOR_OPPONENT : persistedVersion == 2 ? MatchDocument.Lifecycle.AWAITING_SETUP : MatchDocument.Lifecycle.ACTIVATED)) throw new IllegalArgumentException();
			if (away != null && (!home.team.catalogVersion.equals(away.team.catalogVersion) || !home.team.presetId.equals(away.team.presetId)
				|| !home.team.presetVersion.equals(away.team.presetVersion) || !home.team.ruleset.equals(away.team.ruleset))) throw new IllegalArgumentException();
			JsonArray history = object.get("requests").asArray();
			if (history.size() != persistedVersion) throw new IllegalArgumentException();
			Map<String, MatchDocument.Request> requests = new LinkedHashMap<>();
			for (JsonValue value : history) {
				JsonObject request = value.asObject(); exact(request, "key", "fingerprint");
				String key = request.get("key").asString(), fingerprint = request.get("fingerprint").asString();
				int index = requests.size();
				String expectedOwner = index == 0 ? creator : index == 1 ? invited : null;
				String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
				boolean valid = index == 0 && key.matches(expectedOwner + "\n[A-Za-z0-9_-]{1,100}")
					&& fingerprint.matches("create\\|" + uuid + "\\|[1-9][0-9]*\\|" + invited)
					|| index == 1 && key.matches(expectedOwner + "\n[A-Za-z0-9_-]{1,100}")
					&& fingerprint.matches("join\\|" + id + "\\|1\\|" + uuid + "\\|[1-9][0-9]*")
					|| index == 2 && key.matches("(" + creator + "|" + invited + ")\n[A-Za-z0-9_-]{1,100}")
					&& fingerprint.equals("activate|" + id + "|2");
				if (!valid || requests.containsKey(key)) throw new IllegalArgumentException();
				requests.put(key, new MatchDocument.Request(fingerprint));
			}
			return new MatchDocument(id, persistedVersion, invited, lifecycle, home, away, requests);
		} catch (MatchService.Failure failure) { throw failure;
		} catch (RuntimeException failure) { throw new MatchService.Failure("SNAPSHOT_UNSUPPORTED"); }
	}

	private MatchDocument decodeCompleted(JsonObject object, int persistedVersion) {
		if (persistedVersion != 4) throw new IllegalArgumentException();
		exact(object, "formatVersion", "matchId", "documentVersion", "lifecycle", "invitation", "home", "away", "homeOwner", "requests", "completion");
		if (object.getInt("documentVersion", -1) != 4 || !"COMPLETED".equals(object.getString("lifecycle", null))) throw new IllegalArgumentException();
		CompletedMatch completed = new CompletedMatch(object.get("completion").toString());
		JsonObject prior = JsonObject.readFrom(object.toString()); prior.remove("completion"); prior.set("formatVersion", 1); prior.set("documentVersion", 3); prior.set("lifecycle", "ACTIVATED");
		MatchDocument activated = decode(prior.toString(), 3);
		validateCompletion(completed, activated);
		return activated.completed(completed);
	}

	/** Verifies the small, stable terminal envelope without interpreting game state. */
	void validateCompletion(CompletedMatch completed, MatchDocument document) {
		try {
			JsonObject artifact = JsonObject.readFrom(completed.json());
			exact(artifact, "formatVersion", "engineVersion", "ruleset", "catalogVersion", "presetId", "presetVersion", "matchId", "homeScore", "awayScore", "finalRevision", "events");
			if (artifact.getInt("formatVersion", -1) != 1 || !CompletedMatch.ENGINE_VERSION.equals(artifact.getString("engineVersion", null))
				|| !"BB2025".equals(artifact.getString("ruleset", null)) || !document.matchId.equals(artifact.getString("matchId", null))
				|| !document.home.team.catalogVersion.equals(artifact.getString("catalogVersion", null)) || !document.home.team.presetId.equals(artifact.getString("presetId", null))
				|| !document.home.team.presetVersion.equals(artifact.getString("presetVersion", null))) throw new IllegalArgumentException();
			int home = artifact.getInt("homeScore", -1), away = artifact.getInt("awayScore", -1), finalRevision = artifact.getInt("finalRevision", -1);
			if (home < 0 || away < 0 || home > 100 || away > 100 || finalRevision < 0) throw new IllegalArgumentException();
			JsonArray events = artifact.get("events").asArray(); if (events.size() == 0 || events.size() > 8193) throw new IllegalArgumentException();
			int prior = -1;
			for (JsonValue value : events) {
				JsonObject event = value.asObject(); exact(event, "revision", "kind", "state"); int revision = event.getInt("revision", -1);
				if (revision != prior + 1 || revision > finalRevision || !("START".equals(event.getString("kind", null)) || "ACTION".equals(event.getString("kind", null)) || "SELECTION".equals(event.getString("kind", null)) || "TOUCHDOWN".equals(event.getString("kind", null)) || "HALFTIME".equals(event.getString("kind", null)) || "FULL_TIME".equals(event.getString("kind", null)))) throw new IllegalArgumentException(); prior = revision;
				JsonObject state = event.get("state").asObject();
				exact(state, "half", "drive", "homeScore", "awayScore", "homeTurn", "awayTurn", "actions", "turn", "turnMode", "activePlayerId", "ball", "matchId", "revision", "callerRole", "phase", "actor", "prompt", "players", "weather", "homeRerolls", "awayRerolls");
				if (state.get("actions").asArray().size() != 0 || !state.get("prompt").isNull() || !"home".equals(state.getString("callerRole", null)) || !document.matchId.equals(state.getString("matchId", null)) || state.getInt("revision", -1) != revision) throw new IllegalArgumentException();
                validateReplayState(state);
                if (event.toString().getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException();
			}
			JsonObject terminal = events.get(events.size() - 1).asObject().get("state").asObject();
			if (prior != finalRevision || !"FULL_TIME".equals(events.get(events.size() - 1).asObject().getString("kind", null)) || !"FULL_TIME".equals(terminal.getString("phase", null)) || terminal.getInt("homeScore", -1) != home || terminal.getInt("awayScore", -1) != away) throw new IllegalArgumentException();
		} catch (RuntimeException failure) { throw new MatchService.Failure("REPLAY_UNSUPPORTED"); }
	}

    private void validateReplayState(JsonObject state) {
        for (String field : Arrays.asList("homeScore", "awayScore", "homeRerolls", "awayRerolls")) bounded(state.get(field), 0, 100);
        bounded(state.get("half"), 1, 2); bounded(state.get("drive"), 1, 100);
        bounded(state.get("homeTurn"), 0, 8); bounded(state.get("awayTurn"), 0, 8); bounded(state.get("turn"), 0, 8);
        if (!Arrays.asList("PRE_MATCH", "SETUP", "READY_FOR_KICKOFF", "PLAY", "FULL_TIME").contains(state.getString("phase", null))) throw new IllegalArgumentException();
        subject(state.get("actor")); shortText(state.get("turnMode")); shortText(state.get("weather"));
        if (!state.get("activePlayerId").isNull()) shortText(state.get("activePlayerId"));
        if (!state.get("ball").isNull()) {
            JsonObject ball = state.get("ball").asObject(); exact(ball, "x", "y");
            bounded(ball.get("x"), 0, 25); bounded(ball.get("y"), 0, 14);
        }
        JsonArray players = state.get("players").asArray(); if (players.size() > 32) throw new IllegalArgumentException();
        Set<String> ids = new HashSet<>();
        for (JsonValue value : players) {
            JsonObject player = value.asObject(); exact(player, "id", "name", "slot", "role", "state", "x", "y");
            shortText(player.get("id")); shortText(player.get("name")); shortText(player.get("state"));
            if (!ids.add(player.get("id").asString())) throw new IllegalArgumentException();
            bounded(player.get("slot"), 1, 16); subject(player.get("role"));
            if (player.get("x").isNull() != player.get("y").isNull()) throw new IllegalArgumentException();
            if (!player.get("x").isNull()) { bounded(player.get("x"), 0, 25); bounded(player.get("y"), 0, 14); }
        }
    }
    private void shortText(JsonValue value) {
        String text = value.asString(); if (text.isEmpty() || text.length() > 100) throw new IllegalArgumentException();
    }
    private void bounded(JsonValue value, int min, int max) {
        int number = value.asInt(); if (number < min || number > max) throw new IllegalArgumentException();
    }

	private MatchDocument.Member readMember(JsonObject object, String owner, boolean away) {
		if (away) {
			exact(object, "role", "sourceTeamId", "sourceDocumentVersion", "ruleset", "catalogVersion", "rosterId", "presetId", "presetVersion", "validation", "roster", "resolvedCatalog", "owner");
			if (!owner.equals(subject(object.get("owner")))) throw new IllegalArgumentException();
		} else exact(object, "role", "sourceTeamId", "sourceDocumentVersion", "ruleset", "catalogVersion", "rosterId", "presetId", "presetVersion", "validation", "roster", "resolvedCatalog");
		String ruleset = object.get("ruleset").asString(), catalogVersion = object.get("catalogVersion").asString();
		String rosterId = object.get("rosterId").asString(), presetId = object.get("presetId").asString(), presetVersion = object.get("presetVersion").asString();
		if (!"BB2025".equals(ruleset) || !RosterCatalog.VERSION.equals(catalogVersion) || !"human".equals(rosterId)
			|| !RosterCatalog.PRESET.equals(presetId) || !catalogVersion.equals(presetVersion)) throw new IllegalArgumentException();
		JsonObject resolved = object.get("resolvedCatalog").asObject();
		checkCatalog(resolved, catalogVersion, rosterId, presetId);
		JsonObject validation = object.get("validation").asObject(); exact(validation, "valid", "total", "budget", "skillPoints", "messages");
		if (!validation.get("valid").asBoolean() || validation.get("messages").asArray().size() != 0) throw new IllegalArgumentException();
		int total = amount(validation.get("total")), budget = amount(validation.get("budget")), points = amount(validation.get("skillPoints"));
		if (total > budget || budget != resolved.get("budget").asInt() || points > resolved.get("skillPoints").asInt()) throw new IllegalArgumentException();
		JsonObject roster = object.get("roster").asObject(); exact(roster, "captainId", "resources", "players");
		String captain = roster.get("captainId").isNull() ? null : identifier(roster.get("captainId"));
		JsonObject resources = roster.get("resources").asObject(); exact(resources, "rerolls", "assistantCoaches", "cheerleaders", "apothecary", "dedicatedFans");
		Map<String, Integer> quantities = new LinkedHashMap<>();
		for (String key : resources.names()) {
			int value = amount(resources.get(key));
			if (value > find(resolved.get("resources").asArray(), key).get("maximum").asInt()) throw new IllegalArgumentException();
			quantities.put(key, value);
		}
		JsonArray selections = roster.get("players").asArray();
		if (selections.size() < resolved.get("minPlayers").asInt() || selections.size() > resolved.get("maxPlayers").asInt()) throw new IllegalArgumentException();
		List<FrozenTeam.Player> players = new ArrayList<>(); Set<String> ids = new HashSet<>(); Set<Integer> slots = new HashSet<>();
		for (JsonValue value : selections) {
			JsonObject player = value.asObject(); exact(player, "id", "slot", "positionId", "skillIds", "position");
			String playerId = identifier(player.get("id")), positionId = identifier(player.get("positionId"));
			int slot = positive(player.get("slot"));
			if (slot > 16 || !ids.add(playerId) || !slots.add(slot)) throw new IllegalArgumentException();
			List<String> skills = strings(player.get("skillIds").asArray());
			if (skills.size() > 1) throw new IllegalArgumentException();
			for (String skill : skills) if (!find(resolved.get("skills").asArray(), skill).get("selectable").asBoolean()) throw new IllegalArgumentException();
			JsonObject position = player.get("position").asObject();
			exact(position, "name", "role", "race", "maximum", "cost", "ma", "st", "ag", "pa", "av", "primary", "secondary", "baseSkillIds", "parameters");
			JsonObject catalogPosition = find(resolved.get("positions").asArray(), positionId);
			for (String key : new String[] { "name", "role", "race", "maximum", "cost", "ma", "st", "ag", "pa", "av", "primary", "secondary" })
				if (!position.get(key).equals(catalogPosition.get(key))) throw new IllegalArgumentException();
			List<String> base = strings(position.get("baseSkillIds").asArray());
			Map<String, Integer> parameters = new LinkedHashMap<>();
			for (JsonValue skill : catalogPosition.get("baseSkills").asArray()) parameters.put(skill.asObject().get("id").asString(), skill.asObject().get("value").asInt());
			if (!base.equals(new ArrayList<>(parameters.keySet()))) throw new IllegalArgumentException();
			JsonObject suppliedParameters = position.get("parameters").asObject();
			if (!suppliedParameters.names().equals(new ArrayList<>(parameters.keySet()))) throw new IllegalArgumentException();
			for (String key : parameters.keySet()) if (parameters.get(key) != suppliedParameters.get(key).asInt()) throw new IllegalArgumentException();
			players.add(new FrozenTeam.Player(playerId, slot, positionId, skills, base, position.get("name").asString(), position.get("role").asString(),
				position.get("race").asString(), position.get("primary").asString(), position.get("secondary").asString(), amount(position.get("maximum")),
				amount(position.get("cost")), amount(position.get("ma")), amount(position.get("st")), amount(position.get("ag")), amount(position.get("pa")), amount(position.get("av")), parameters));
		}
		if (captain != null && !ids.contains(captain)) throw new IllegalArgumentException();
		FrozenTeam team = new FrozenTeam(uuid(object.get("sourceTeamId")), positive(object.get("sourceDocumentVersion")), owner, ruleset,
			catalogVersion, rosterId, presetId, presetVersion, captain, total, budget, points, players, quantities, resolved.toString());
		verifyFrozenEvaluation(team, resolved);
		return new MatchDocument.Member(object.get("role").asString(), owner, team);
	}

	/** Verify stored acceptance against stored facts, without reading or adopting a current catalog. */
	private void verifyFrozenEvaluation(FrozenTeam team, JsonObject catalog) {
		JsonObject policy = catalog.get("validationPolicy").asObject();
		Map<String, Integer> counts = new LinkedHashMap<>(), elites = new LinkedHashMap<>();
		long total = 0; int points = 0, secondary = 0;
		for (FrozenTeam.Player player : team.players) {
			if (!player.id.matches("[A-Za-z0-9_-]{1,40}")) throw new IllegalArgumentException();
			JsonObject position = find(catalog.get("positions").asArray(), player.positionId);
			int count = counts.getOrDefault(player.positionId, 0) + 1; counts.put(player.positionId, count);
			if (count > player.maximum || player.skillIds.size() > policy.get("maximumPurchasesPerPlayer").asInt()) throw new IllegalArgumentException();
			total += player.cost;
			boolean captain = player.id.equals(team.captainId);
			if (captain && !position.get("canCaptain").asBoolean()) throw new IllegalArgumentException();
			for (String id : player.skillIds) {
				JsonObject skill = find(catalog.get("skills").asArray(), id); String category = skill.get("category").asString();
				boolean primary = player.primary.contains(category);
				if (!skill.get("selectable").asBoolean() || (!primary && !player.secondary.contains(category))
					|| player.baseSkillIds.contains(id) || (captain && policy.get("captainSkillId").asString().equals(id))) throw new IllegalArgumentException();
				points += policy.get(primary ? "primarySkillPoints" : "secondarySkillPoints").asInt();
				total += policy.get("purchasedSkillGold").asInt();
				if (!primary) secondary++;
				if (skill.get("elite").asBoolean()) {
					int copies = elites.getOrDefault(id, 0) + 1; elites.put(id, copies);
					if (copies > catalog.get("maxElite").asInt()) throw new IllegalArgumentException();
				}
			}
		}
		for (Map.Entry<String, Integer> resource : team.resources.entrySet())
			total += (long) resource.getValue() * find(catalog.get("resources").asArray(), resource.getKey()).get("cost").asInt();
		if (total != team.total || points != team.skillPoints || secondary > catalog.get("maxSecondary").asInt()) throw new IllegalArgumentException();
	}

	private void checkCatalog(JsonObject catalog, String version, String roster, String preset) {
		exact(catalog, "version", "type", "requestId", "catalogVersion", "ruleset", "rosterId", "name", "presetId", "budget", "minPlayers", "maxPlayers", "skillPoints", "maxSecondary", "maxElite", "league", "specialRule", "positions", "skills", "resources", "unsupported", "validationPolicy");
		JsonObject policy = catalog.get("validationPolicy").asObject();
		exact(policy, "formatVersion", "primarySkillPoints", "secondarySkillPoints", "maximumPurchasesPerPlayer", "purchasedSkillGold", "captainSkillId", "captainGold", "captainSkillPoints", "unspentBudgetLost");
		if (policy.get("formatVersion").asInt() != 1 || policy.get("primarySkillPoints").asInt() != 1 || policy.get("secondarySkillPoints").asInt() != 2
			|| policy.get("maximumPurchasesPerPlayer").asInt() != 1 || policy.get("purchasedSkillGold").asInt() != 0
			|| !"pro".equals(policy.get("captainSkillId").asString()) || policy.get("captainGold").asInt() != 0 || policy.get("captainSkillPoints").asInt() != 0
			|| !policy.get("unspentBudgetLost").asBoolean()) throw new IllegalArgumentException();
		if (catalog.get("version").asInt() != 1 || !"catalog".equals(catalog.get("type").asString()) || !catalog.get("requestId").isNull()
			|| !version.equals(catalog.get("catalogVersion").asString()) || !"BB2025".equals(catalog.get("ruleset").asString())
			|| !roster.equals(catalog.get("rosterId").asString()) || !preset.equals(catalog.get("presetId").asString())) throw new IllegalArgumentException();
		for (String key : new String[] { "budget", "minPlayers", "maxPlayers", "skillPoints", "maxSecondary", "maxElite" }) amount(catalog.get(key));
		for (String key : new String[] { "name", "league", "specialRule", "unsupported" }) catalog.get(key).asString();
		Set<String> supported = new HashSet<>(Arrays.asList("block", "dodge", "catch", "pass", "sure-hands", "tackle", "pro", "right-stuff", "stunty", "bone-head", "loner", "mighty-blow", "thick-skull", "throw-team-mate"));
		Set<String> skillIds = new HashSet<>();
		for (JsonValue value : catalog.get("skills").asArray()) {
			JsonObject skill = value.asObject(); exact(skill, "id", "name", "category", "selectable", "elite");
			String id = identifier(skill.get("id")); if (!supported.contains(id) || !skillIds.add(id)) throw new IllegalArgumentException();
			skill.get("name").asString(); skill.get("category").asString(); skill.get("selectable").asBoolean(); skill.get("elite").asBoolean();
		}
		if (!skillIds.equals(supported)) throw new IllegalArgumentException();
		Set<String> positionIds = new HashSet<>();
		for (JsonValue value : catalog.get("positions").asArray()) {
			JsonObject position = value.asObject(); exact(position, "id", "name", "maximum", "cost", "ma", "st", "ag", "pa", "av", "role", "race", "primary", "secondary", "baseSkills", "canCaptain");
			if (!positionIds.add(identifier(position.get("id")))) throw new IllegalArgumentException();
			for (String key : new String[] { "name", "role", "race", "primary", "secondary" }) position.get(key).asString();
			for (String key : new String[] { "maximum", "cost", "ma", "st", "ag", "pa", "av" }) amount(position.get(key));
			position.get("canCaptain").asBoolean(); Set<String> bases = new HashSet<>();
			for (JsonValue base : position.get("baseSkills").asArray()) {
				JsonObject skill = base.asObject(); exact(skill, "id", "value"); String id = identifier(skill.get("id"));
				if (!supported.contains(id) || !bases.add(id) || skill.get("value").asInt() != ("loner".equals(id) ? 3 : "mighty-blow".equals(id) ? 1 : 0)) throw new IllegalArgumentException();
			}
		}
		if (!positionIds.equals(new HashSet<>(Arrays.asList("lineman", "halfling", "catcher", "thrower", "blitzer", "ogre")))) throw new IllegalArgumentException();
		Set<String> resourceIds = new HashSet<>();
		for (JsonValue value : catalog.get("resources").asArray()) {
			JsonObject resource = value.asObject(); exact(resource, "id", "name", "cost", "maximum");
			if (!resourceIds.add(identifier(resource.get("id")))) throw new IllegalArgumentException();
			resource.get("name").asString(); amount(resource.get("cost")); amount(resource.get("maximum"));
		}
		if (!resourceIds.equals(new HashSet<>(Arrays.asList("rerolls", "assistantCoaches", "cheerleaders", "apothecary", "dedicatedFans")))) throw new IllegalArgumentException();
	}

	public JsonObject publicDocument(MatchDocument document) {
		return new JsonObject().add("formatVersion", document.lifecycle == MatchDocument.Lifecycle.COMPLETED ? 2 : 1).add("matchId", document.matchId).add("documentVersion", document.documentVersion)
			.add("lifecycle", document.lifecycle.name()).add("invitation", new JsonObject().add("intendedOpponent", document.intendedOpponent))
			.add("home", member(document.home)).add("away", document.away == null ? JsonValue.NULL : member(document.away));
	}
	private JsonObject member(MatchDocument.Member member) {
		FrozenTeam team = member.team; JsonArray players = new JsonArray();
		for (FrozenTeam.Player player : team.players) {
			JsonObject parameters = new JsonObject(); for (Map.Entry<String, Integer> parameter : player.parameters.entrySet()) parameters.add(parameter.getKey(), parameter.getValue());
			JsonObject position = new JsonObject().add("name", player.name).add("role", player.role).add("race", player.race).add("maximum", player.maximum)
				.add("cost", player.cost).add("ma", player.ma).add("st", player.st).add("ag", player.ag).add("pa", player.pa).add("av", player.av)
				.add("primary", player.primary).add("secondary", player.secondary).add("baseSkillIds", array(player.baseSkillIds)).add("parameters", parameters);
			players.add(new JsonObject().add("id", player.id).add("slot", player.slot).add("positionId", player.positionId).add("skillIds", array(player.skillIds)).add("position", position));
		}
		JsonObject resources = new JsonObject(); for (Map.Entry<String, Integer> resource : team.resources.entrySet()) resources.add(resource.getKey(), resource.getValue());
		return new JsonObject().add("role", member.role).add("sourceTeamId", team.sourceTeamId).add("sourceDocumentVersion", team.sourceDocumentVersion)
			.add("ruleset", team.ruleset).add("catalogVersion", team.catalogVersion).add("rosterId", team.rosterId).add("presetId", team.presetId).add("presetVersion", team.presetVersion)
			.add("validation", new JsonObject().add("valid", true).add("total", team.total).add("budget", team.budget).add("skillPoints", team.skillPoints).add("messages", new JsonArray()))
			.add("roster", new JsonObject().add("captainId", team.captainId).add("resources", resources).add("players", players));
	}
	private JsonObject response(String id, String code, boolean duplicate, String role, JsonObject document, String recovery) {
		return new JsonObject().add("version", 1).add("type", "preparedMatch").add("requestId", id).add("code", code).add("duplicate", duplicate)
			.add("callerRole", role).add("document", document == null ? JsonValue.NULL : document).add("recoveryMatchId", recovery);
	}
	private String role(MatchDocument document, String owner) { return owner.equals(document.home.owner) ? "home" : document.away != null && owner.equals(document.away.owner) ? "away" : null; }
	private JsonObject find(JsonArray array, String id) { for (JsonValue value : array) if (id.equals(value.asObject().getString("id", null))) return value.asObject(); throw new IllegalArgumentException(); }
	private JsonArray array(List<String> values) { JsonArray result = new JsonArray(); for (String value : values) result.add(value); return result; }
	private List<String> strings(JsonArray array) { List<String> result = new ArrayList<>(); for (JsonValue value : array) { String id = identifier(value); if (result.contains(id)) throw new IllegalArgumentException(); result.add(id); } return result; }
	private String identifier(JsonValue value) { String id = value.asString(); if (!id.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException(); return id; }
	private String uuid(JsonValue value) { String id = value.asString(); if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException(); return id; }
	private String subject(JsonValue value) { String subject = value.asString(); if (!"home".equals(subject) && !"away".equals(subject)) throw new IllegalArgumentException(); return subject; }
	private int positive(JsonValue value) { int number = value.asInt(); if (number < 1 || number > 2147483646) throw new IllegalArgumentException(); return number; }
	private int amount(JsonValue value) { int number = value.asInt(); if (number < 0 || number > 5000000) throw new IllegalArgumentException(); return number; }
	private void exact(JsonObject object, String... fields) { if (object.size() != fields.length || !new HashSet<>(object.names()).equals(new HashSet<>(Arrays.asList(fields)))) throw new IllegalArgumentException(); }
	private JsonObject parse(String text, int bytes, int maximumDepth) {
		if (text == null || text.length() > bytes || text.getBytes(StandardCharsets.UTF_8).length > bytes) throw new IllegalArgumentException();
		boolean quoted = false, escaped = false; int depth = 0;
		for (int index = 0; index < text.length(); index++) {
			char ch = text.charAt(index);
			if (quoted) { if (escaped) escaped = false; else if (ch == '\\') escaped = true; else if (ch == '"') quoted = false; }
			else if (ch == '"') quoted = true;
			else if (ch == '{' || ch == '[') { if (++depth > maximumDepth) throw new IllegalArgumentException(); }
			else if (ch == '}' || ch == ']') depth--;
		}
		JsonObject object = JsonObject.readFrom(text); unique(object); return object;
	}
	private void unique(JsonValue value) {
		if (value.isObject()) { JsonObject object = value.asObject(); if (new HashSet<>(object.names()).size() != object.size()) throw new IllegalArgumentException(); for (JsonObject.Member member : object) unique(member.getValue()); }
		else if (value.isArray()) for (JsonValue item : value.asArray()) unique(item);
	}
}
