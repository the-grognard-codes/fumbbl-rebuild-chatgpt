package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.local.BrowserTeamJson;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable accepted facts. Catalog content is resolved once, never refreshed when reading a match. */
public final class FrozenTeam {
	public final String sourceTeamId, owner, ruleset, catalogVersion, rosterId, presetId, presetVersion, captainId;
	public final String resolvedCatalogJson;
	public final int sourceDocumentVersion, total, budget, skillPoints;
	public final List<Player> players;
	public final Map<String, Integer> resources;

	public FrozenTeam(String sourceTeamId, int sourceDocumentVersion, String owner, TeamDraft draft,
		int total, int skillPoints, RosterCatalog catalog) {
		this(sourceTeamId, sourceDocumentVersion, owner, draft.ruleset, draft.catalogVersion, draft.rosterId,
			draft.presetId, draft.catalogVersion, draft.captainId, total, RosterCatalog.BUDGET, skillPoints,
			freezePlayers(draft, catalog), draft.resources, new BrowserTeamJson(catalog).catalog(null)
				.add("validationPolicy", new JsonObject().add("formatVersion", 1).add("primarySkillPoints", 1)
					.add("secondarySkillPoints", 2).add("maximumPurchasesPerPlayer", 1).add("purchasedSkillGold", 0)
					.add("captainSkillId", "pro").add("captainGold", 0).add("captainSkillPoints", 0).add("unspentBudgetLost", true)).toString());
	}

	public FrozenTeam(String sourceTeamId, int sourceDocumentVersion, String owner, String ruleset, String catalogVersion,
		String rosterId, String presetId, String presetVersion, String captainId, int total, int budget, int skillPoints,
		List<Player> players, Map<String, Integer> resources, String resolvedCatalogJson) {
		this.sourceTeamId = sourceTeamId; this.sourceDocumentVersion = sourceDocumentVersion; this.owner = owner;
		this.ruleset = ruleset; this.catalogVersion = catalogVersion; this.rosterId = rosterId;
		this.presetId = presetId; this.presetVersion = presetVersion; this.captainId = captainId;
		this.total = total; this.budget = budget; this.skillPoints = skillPoints;
		this.players = Collections.unmodifiableList(new ArrayList<>(players));
		this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
		this.resolvedCatalogJson = resolvedCatalogJson;
	}

	private static List<Player> freezePlayers(TeamDraft draft, RosterCatalog catalog) {
		List<Player> players = new ArrayList<>();
		for (TeamDraft.Player choice : draft.players) {
			RosterCatalog.Position position = catalog.getPositions().get(choice.positionId);
			if (position == null) throw new IllegalArgumentException("Unresolved position");
			Map<String, Integer> parameters = new LinkedHashMap<>();
			for (String skill : position.baseSkills) parameters.put(skill, position.skillValue(skill));
			players.add(new Player(choice.id, choice.slot, choice.positionId, choice.skillIds, position.baseSkills,
				position.name, position.role, position.race, position.primary, position.secondary, position.maximum,
				position.cost, position.ma, position.st, position.ag, position.pa, position.av, parameters));
		}
		return players;
	}

	public static final class Player {
		public final String id, positionId, name, role, race, primary, secondary;
		public final int slot, maximum, cost, ma, st, ag, pa, av;
		public final List<String> skillIds, baseSkillIds;
		public final Map<String, Integer> parameters;

		public Player(String id, int slot, String positionId, List<String> skillIds, List<String> baseSkillIds,
			String name, String role, String race, String primary, String secondary, int maximum, int cost,
			int ma, int st, int ag, int pa, int av, Map<String, Integer> parameters) {
			this.id = id; this.slot = slot; this.positionId = positionId;
			this.skillIds = Collections.unmodifiableList(new ArrayList<>(skillIds));
			this.baseSkillIds = Collections.unmodifiableList(new ArrayList<>(baseSkillIds));
			this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
			this.name = name; this.role = role; this.race = race; this.primary = primary; this.secondary = secondary;
			this.maximum = maximum; this.cost = cost; this.ma = ma; this.st = st; this.ag = ag; this.pa = pa; this.av = av;
		}
	}
}
