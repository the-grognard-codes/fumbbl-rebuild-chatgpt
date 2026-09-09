package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;
import com.fumbbl.ffb.server.team.bb2025.TeamValidation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit browser projection; never serializes the legacy model or input claims. */
public final class BrowserTeamJson {
	private final RosterCatalog catalog;
	private final TeamValidation validation;
	public BrowserTeamJson(RosterCatalog catalog) { this.catalog = catalog; validation = new TeamValidation(catalog); }

	/** Bound parsing before the recursive JSON parser sees untrusted input. */
	public void checkEnvelope(String text) {
		if (text == null || text.length() > 16384 || text.getBytes(StandardCharsets.UTF_8).length > 16384) {
			throw new IllegalArgumentException("Message exceeds 16 KiB.");
		}
		boolean quoted = false, escaped = false;
		int depth = 0;
		for (int index = 0; index < text.length(); index++) {
			char ch = text.charAt(index);
			if (quoted) {
				if (escaped) escaped = false;
				else if (ch == '\\') escaped = true;
				else if (ch == '"') quoted = false;
			} else if (ch == '"') quoted = true;
			else if (ch == '{' || ch == '[') { if (++depth > 8) throw new IllegalArgumentException("JSON nesting exceeds eight."); }
			else if (ch == '}' || ch == ']') depth--;
		}
	}

	public void fields(JsonObject object, String... fields) {
		if (object.size() != fields.length || !new HashSet<>(object.names()).equals(new HashSet<>(Arrays.asList(fields)))) {
			throw new IllegalArgumentException("Missing, duplicate or unsupported fields.");
		}
	}

	private String identifier(JsonValue value) {
		String id = value.asString();
		if (!id.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid identifier.");
		return id;
	}

	public TeamDraft decodeDraft(JsonObject draft) {
		fields(draft, "catalogVersion", "ruleset", "rosterId", "presetId", "captainId", "players", "resources");
		JsonArray players = draft.get("players").asArray();
		if (players.size() > 16) throw new IllegalArgumentException("Too many players.");
		List<TeamDraft.Player> choices = new ArrayList<>();
		for (JsonValue value : players) {
			JsonObject player = value.asObject();
			fields(player, "id", "slot", "positionId", "skillIds");
			JsonArray skillIds = player.get("skillIds").asArray();
			if (skillIds.size() > 2) throw new IllegalArgumentException("Too many skills.");
			List<String> skills = new ArrayList<>();
			for (JsonValue skill : skillIds) skills.add(identifier(skill));
			choices.add(new TeamDraft.Player(identifier(player.get("id")), player.get("slot").asInt(), identifier(player.get("positionId")), skills));
		}
		JsonObject resources = draft.get("resources").asObject();
		fields(resources, "rerolls", "assistantCoaches", "cheerleaders", "apothecary", "dedicatedFans");
		Map<String, Integer> quantities = new LinkedHashMap<>();
		for (String id : resources.names()) quantities.put(id, resources.get(id).asInt());
		return new TeamDraft(identifier(draft.get("catalogVersion")), identifier(draft.get("ruleset")),
			identifier(draft.get("rosterId")), identifier(draft.get("presetId")),
			draft.get("captainId").isNull() ? null : identifier(draft.get("captainId")), choices, quantities);
	}

	public JsonObject evaluate(String requestId, JsonObject draft) {
		TeamValidation.Evaluation result = validation.evaluate(decodeDraft(draft));
		JsonArray messages = new JsonArray();
		for (TeamValidation.Message message : result.messages) {
			messages.add(new JsonObject().add("code", message.code).add("path", message.path).add("text", message.text));
		}
		JsonObject response = header("teamValidation", requestId).add("valid", result.isValid())
			.add("budget", RosterCatalog.BUDGET).add("skillPoints", result.skillPoints).add("messages", messages);
		response.add("total", result.total == null ? JsonValue.NULL : JsonValue.valueOf(result.total));
		return response;
	}

	public JsonObject catalog(String requestId) {
		JsonArray positions = new JsonArray(), skills = new JsonArray(), resources = new JsonArray();
		for (RosterCatalog.Position position : catalog.getPositions().values()) {
			JsonArray baseSkills = new JsonArray();
			for (String id : position.baseSkills) baseSkills.add(new JsonObject().add("id", id).add("value", position.skillValue(id)));
			positions.add(new JsonObject().add("id", position.id).add("name", position.name)
				.add("maximum", position.maximum).add("cost", position.cost).add("ma", position.ma).add("st", position.st)
				.add("ag", position.ag).add("pa", position.pa).add("av", position.av).add("role", position.role).add("race", position.race)
				.add("primary", position.primary).add("secondary", position.secondary).add("baseSkills", baseSkills).add("canCaptain", position.canCaptain()));
		}
		for (RosterCatalog.SkillOption skill : catalog.getSkills().values()) {
			skills.add(new JsonObject().add("id", skill.id).add("name", skill.name).add("category", skill.category)
				.add("selectable", skill.selectable).add("elite", skill.elite));
		}
		for (Map.Entry<String, RosterCatalog.Resource> entry : catalog.getResources().entrySet()) {
			resources.add(new JsonObject().add("id", entry.getKey()).add("name", entry.getValue().name)
				.add("cost", entry.getValue().cost).add("maximum", entry.getValue().maximum));
		}
		return header("catalog", requestId).add("rosterId", RosterCatalog.ROSTER).add("name", "Human")
			.add("presetId", RosterCatalog.PRESET).add("budget", RosterCatalog.BUDGET)
			.add("minPlayers", RosterCatalog.MIN_PLAYERS).add("maxPlayers", RosterCatalog.MAX_PLAYERS)
			.add("skillPoints", RosterCatalog.SKILL_POINTS).add("maxSecondary", RosterCatalog.MAX_SECONDARY)
			.add("maxElite", RosterCatalog.MAX_ELITE).add("league", "Old World Classic").add("specialRule", "Team Captain")
			.add("positions", positions).add("skills", skills).add("resources", resources)
			.add("unsupported", "All other rosters, skills for purchase, star players, inducements, progression and match creation are unsupported. Unspent budget is lost.");
	}

	private JsonObject header(String type, String requestId) {
		return new JsonObject().add("version", 1).add("type", type).add("requestId", requestId)
			.add("catalogVersion", RosterCatalog.VERSION).add("ruleset", RosterCatalog.RULESET);
	}
}
