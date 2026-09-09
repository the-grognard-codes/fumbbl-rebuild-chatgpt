package com.fumbbl.ffb.server.team.bb2025;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure evaluation. Neither accepted nor rejected evaluations mutate/save a draft or match. */
public final class TeamValidation {
	private final RosterCatalog catalog;
	public TeamValidation(RosterCatalog catalog) { this.catalog = catalog; }

	public Evaluation evaluate(TeamDraft draft) {
		List<Message> messages = new ArrayList<>();
		if (!RosterCatalog.VERSION.equals(draft.catalogVersion)) add(messages, "CATALOG_VERSION", "catalogVersion", "Unsupported catalog version.");
		if (!RosterCatalog.RULESET.equals(draft.ruleset)) add(messages, "RULESET", "ruleset", "Only BB2025 is supported.");
		if (!RosterCatalog.ROSTER.equals(draft.rosterId)) add(messages, "ROSTER", "rosterId", "Unsupported roster.");
		if (!RosterCatalog.PRESET.equals(draft.presetId)) add(messages, "PRESET", "presetId", "Unsupported preset.");
		if (!messages.isEmpty()) return new Evaluation(null, 0, messages);
		if (draft.players.size() > 16) {
			add(messages, "PLAYER_COUNT", "players", "A draft must contain 11 to 16 players.");
			return new Evaluation(null, 0, messages);
		}
		if (draft.players.size() < 11) add(messages, "PLAYER_COUNT", "players", "A draft must contain 11 to 16 players.");
		Set<String> ids = new HashSet<>();
		Set<Integer> slots = new HashSet<>();
		Map<String, Integer> counts = new HashMap<>();
		Map<String, Integer> elites = new HashMap<>();
		int total = 0, points = 0, secondary = 0;
		boolean priced = true, captainFound = draft.captainId == null;
		for (int index = 0; index < draft.players.size(); index++) {
			TeamDraft.Player player = draft.players.get(index);
			String path = "players[" + index + "]";
			if (!player.id.matches("[A-Za-z0-9_-]{1,40}")) add(messages, "PLAYER_ID", path, "Invalid player ID.");
			if (!ids.add(player.id)) add(messages, "DUPLICATE_PLAYER", path, "Player IDs must be unique.");
			if (!slots.add(player.slot)) add(messages, "DUPLICATE_SLOT", path, "Player slots must be unique.");
			if (player.slot < 1 || player.slot > 16) add(messages, "SLOT", path, "Slots must be integers from 1 to 16.");
			RosterCatalog.Position position = catalog.getPositions().get(player.positionId);
			if (position == null) {
				add(messages, "POSITION", path, "Unknown or unsupported position."); priced = false; continue;
			}
			total += position.cost;
			int count = counts.getOrDefault(position.id, 0) + 1;
			counts.put(position.id, count);
			if (count > position.maximum) add(messages, "POSITION_LIMIT", path, "Too many players at this position.");
			boolean captain = player.id.equals(draft.captainId);
			if (captain) {
				captainFound = true;
				if (!position.canCaptain()) add(messages, "CAPTAIN_INELIGIBLE", path, "A Big Guy cannot be Team Captain.");
			}
			if (player.skillIds.size() > 1) add(messages, "SKILL_LIMIT", path, "At most one purchased skill per player.");
			Set<String> chosen = new HashSet<>();
			for (String id : player.skillIds) {
				RosterCatalog.SkillOption skill = catalog.getSkills().get(id);
				if (skill == null) { add(messages, "SKILL", path, "Unknown skill identifier."); continue; }
				if (!chosen.add(id) || position.baseSkills.contains(id) || (captain && "pro".equals(id))) {
					add(messages, "DUPLICATE_SKILL", path, "The player already has this skill."); continue;
				}
				if (!skill.selectable || (!position.primary.contains(skill.category) && !position.secondary.contains(skill.category))) {
					add(messages, "SKILL_INELIGIBLE", path, "This skill is unsupported or unavailable to this position."); continue;
				}
				boolean primary = position.primary.contains(skill.category);
				points += primary ? 1 : 2;
				if (!primary) secondary++;
				if (skill.elite) {
					int eliteCount = elites.getOrDefault(id, 0) + 1; elites.put(id, eliteCount);
					if (eliteCount > RosterCatalog.MAX_ELITE) add(messages, "ELITE_LIMIT", path, "Each purchased Elite skill is limited to four copies.");
				}
			}
		}
		if (!captainFound) add(messages, "CAPTAIN", "captainId", "Captain must reference a player in this draft.");
		if (points > RosterCatalog.SKILL_POINTS) add(messages, "SKILL_POINTS", "players", "The preset allows eight skill points.");
		if (secondary > RosterCatalog.MAX_SECONDARY) add(messages, "SECONDARY_LIMIT", "players", "The preset allows two Secondary skills.");
		if (!draft.resources.keySet().equals(catalog.getResources().keySet())) {
			add(messages, "RESOURCES", "resources", "Provide exactly the supported resources."); priced = false;
		}
		for (Map.Entry<String, RosterCatalog.Resource> entry : catalog.getResources().entrySet()) {
			Integer quantity = draft.resources.get(entry.getKey());
			if (quantity == null || quantity < 0 || quantity > entry.getValue().maximum) {
				add(messages, "QUANTITY", "resources." + entry.getKey(), "Resource quantity is outside its permitted range."); priced = false;
			} else total += quantity * entry.getValue().cost;
		}
		if (priced && total > RosterCatalog.BUDGET) add(messages, "OVER_BUDGET", "resources", "Total exceeds the preset budget.");
		return new Evaluation(priced ? total : null, points, messages);
	}

	private void add(List<Message> messages, String code, String path, String text) { messages.add(new Message(code, path, text)); }

	public static final class Message {
		public final String code, path, text;
		private Message(String code, String path, String text) { this.code = code; this.path = path; this.text = text; }
	}

	public static final class Evaluation {
		public final Integer total;
		public final int skillPoints;
		public final List<Message> messages;
		private Evaluation(Integer total, int skillPoints, List<Message> messages) {
			this.total = total; this.skillPoints = skillPoints;
			this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
		}
		public boolean isValid() { return messages.isEmpty(); }
	}
}
