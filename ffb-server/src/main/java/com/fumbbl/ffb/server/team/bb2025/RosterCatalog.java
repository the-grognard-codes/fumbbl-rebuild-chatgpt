package com.fumbbl.ffb.server.team.bb2025;

import com.fumbbl.ffb.FactoryType.Factory;
import com.fumbbl.ffb.FactoryManager;
import com.fumbbl.ffb.SkillCategory;
import com.fumbbl.ffb.factory.SkillFactory;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.skill.Skill;
import com.fumbbl.ffb.option.GameOptionId;
import com.fumbbl.ffb.option.GameOptionString;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable curated recruitment data; provenance and exclusions: browser-client/catalog.md. */
public final class RosterCatalog {

	public static final String VERSION = "bb2025-human-2026-09-08.1";
	public static final String RULESET = "BB2025";
	public static final String ROSTER = "human";
	public static final String PRESET = "human-exhibition-1150";
	public static final int BUDGET = 1150000;
	public static final int MIN_PLAYERS = 11;
	public static final int MAX_PLAYERS = 16;
	public static final int SKILL_POINTS = 8;
	public static final int MAX_SECONDARY = 2;
	public static final int MAX_ELITE = 4;
	private final Map<String, Position> positions;
	private final Map<String, SkillOption> skills;
	private final Map<String, Resource> resources;

	public RosterCatalog() {
		Game game = new Game(null, new FactoryManager());
		game.getOptions().addOption(new GameOptionString(GameOptionId.RULESVERSION).setValue(RULESET));
		game.initializeRules();
		SkillFactory factory = game.getRules().getFactory(Factory.SKILL);
		Map<String, SkillOption> skillData = new LinkedHashMap<>();
		addSkill(skillData, factory, "block", "Block", SkillCategory.GENERAL, true, true);
		addSkill(skillData, factory, "dodge", "Dodge", SkillCategory.AGILITY, true, true);
		addSkill(skillData, factory, "catch", "Catch", SkillCategory.AGILITY, true, false);
		addSkill(skillData, factory, "pass", "Pass", SkillCategory.PASSING, true, false);
		addSkill(skillData, factory, "sure-hands", "Sure Hands", SkillCategory.GENERAL, true, false);
		addSkill(skillData, factory, "tackle", "Tackle", SkillCategory.GENERAL, true, false);
		addSkill(skillData, factory, "pro", "Pro", SkillCategory.GENERAL, true, false);
		addSkill(skillData, factory, "right-stuff", "Right Stuff", SkillCategory.TRAIT, false, false);
		addSkill(skillData, factory, "stunty", "Stunty", SkillCategory.TRAIT, false, false);
		addSkill(skillData, factory, "bone-head", "Bone Head", SkillCategory.TRAIT, false, false);
		addSkill(skillData, factory, "loner", "Loner", SkillCategory.TRAIT, false, false);
		addSkill(skillData, factory, "mighty-blow", "Mighty Blow", SkillCategory.STRENGTH, false, true);
		addSkill(skillData, factory, "thick-skull", "Thick Skull", SkillCategory.STRENGTH, false, false);
		addSkill(skillData, factory, "throw-team-mate", "Throw Team-Mate", SkillCategory.TRAIT, false, false);
		skills = Collections.unmodifiableMap(skillData);
		Map<String, Position> data = new LinkedHashMap<>();
		addPosition(data, new Position("lineman", "Human Lineman", 16, 50000, 6, 3, 3, 4, 9, "Lineman", "Human", "G", "ADS"));
		addPosition(data, new Position("halfling", "Halfling Hopeful", 3, 30000, 5, 2, 3, 4, 7, "Lineman", "Halfling", "A", "DGS", "dodge", "right-stuff", "stunty"));
		addPosition(data, new Position("catcher", "Human Catcher", 2, 75000, 8, 3, 3, 4, 8, "Catcher", "Human", "AG", "DPS", "catch", "dodge"));
		addPosition(data, new Position("thrower", "Human Thrower", 2, 75000, 6, 3, 3, 3, 9, "Thrower", "Human", "GP", "ADS", "pass", "sure-hands"));
		addPosition(data, new Position("blitzer", "Human Blitzer", 2, 85000, 7, 3, 3, 4, 9, "Blitzer", "Human", "GS", "AD", "block", "tackle"));
		addPosition(data, new Position("ogre", "Ogre", 1, 140000, 5, 5, 4, 5, 10, "Big Guy", "Ogre", "S", "AG", "bone-head", "loner", "mighty-blow", "thick-skull", "throw-team-mate"));
		positions = Collections.unmodifiableMap(data);
		Map<String, Resource> resourceData = new LinkedHashMap<>();
		resourceData.put("rerolls", new Resource("Team re-rolls", 50000, 8));
		resourceData.put("assistantCoaches", new Resource("Assistant coaches", 10000, 6));
		resourceData.put("cheerleaders", new Resource("Cheerleaders", 10000, 6));
		resourceData.put("apothecary", new Resource("Apothecary", 50000, 1));
		// Matched/exhibition starts at zero; drafting permits purchases up to three.
		resourceData.put("dedicatedFans", new Resource("Dedicated fans", 5000, 3));
		resources = Collections.unmodifiableMap(resourceData);
	}

	private void addSkill(Map<String, SkillOption> data, SkillFactory factory, String id, String name,
		SkillCategory category, boolean selectable, boolean elite) {
		Skill skill = factory.forName(name);
		if (skill == null || skill.getCategory() != category) {
			throw new IllegalStateException("Starter catalog skill mapping is unavailable: " + id);
		}
		data.put(id, new SkillOption(id, skill.getName(), category, selectable, elite));
	}

	private void addPosition(Map<String, Position> data, Position position) {
		for (String skill : position.baseSkills) {
			if (!skills.containsKey(skill)) throw new IllegalStateException("Unresolved base skill: " + skill);
		}
		data.put(position.id, position);
	}

	public Map<String, Position> getPositions() { return positions; }
	public Map<String, SkillOption> getSkills() { return skills; }
	public Map<String, Resource> getResources() { return resources; }

	public static final class Position {
		public final String id, name, role, race, primary, secondary;
		public final int maximum, cost, ma, st, ag, pa, av;
		public final List<String> baseSkills;

		private Position(String id, String name, int maximum, int cost, int ma, int st, int ag, int pa,
			int av, String role, String race, String primary, String secondary, String... baseSkills) {
			this.id = id; this.name = name; this.maximum = maximum; this.cost = cost;
			this.ma = ma; this.st = st; this.ag = ag; this.pa = pa; this.av = av;
			this.role = role; this.race = race; this.primary = primary; this.secondary = secondary;
			this.baseSkills = Collections.unmodifiableList(Arrays.asList(baseSkills.clone()));
		}

		public boolean canCaptain() { return !"Big Guy".equals(role); }
		/** Explicit source parameter, not the engine's default Loner (4+). */
		public int skillValue(String id) { return "loner".equals(id) ? 3 : "mighty-blow".equals(id) ? 1 : 0; }
	}

	public static final class SkillOption {
		public final String id, name, category;
		public final boolean selectable, elite;

		private SkillOption(String id, String name, SkillCategory category, boolean selectable, boolean elite) {
			this.id = id; this.name = name; this.selectable = selectable; this.elite = elite;
			this.category = category == SkillCategory.TRAIT ? "T" : category.name().substring(0, 1);
		}
	}

	public static final class Resource {
		public final String name;
		public final int cost, maximum;
		private Resource(String name, int cost, int maximum) { this.name = name; this.cost = cost; this.maximum = maximum; }
	}
}
