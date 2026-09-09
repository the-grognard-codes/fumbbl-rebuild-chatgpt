package com.fumbbl.ffb.server.team.bb2025;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Value input only: no costs, stats, base skills or engine objects are accepted. */
public final class TeamDraft {
	public final String catalogVersion, ruleset, rosterId, presetId, captainId;
	public final List<Player> players;
	public final Map<String, Integer> resources;

	public TeamDraft(String catalogVersion, String ruleset, String rosterId, String presetId,
		String captainId, List<Player> players, Map<String, Integer> resources) {
		this.catalogVersion = Objects.requireNonNull(catalogVersion);
		this.ruleset = Objects.requireNonNull(ruleset);
		this.rosterId = Objects.requireNonNull(rosterId);
		this.presetId = Objects.requireNonNull(presetId);
		this.captainId = captainId;
		this.players = Collections.unmodifiableList(new ArrayList<>(players));
		this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
	}

	public static final class Player {
		public final String id, positionId;
		public final int slot;
		public final List<String> skillIds;
		public Player(String id, int slot, String positionId, List<String> skillIds) {
			this.id = Objects.requireNonNull(id); this.slot = slot;
			this.positionId = Objects.requireNonNull(positionId);
			this.skillIds = Collections.unmodifiableList(new ArrayList<>(skillIds));
		}
	}
}
