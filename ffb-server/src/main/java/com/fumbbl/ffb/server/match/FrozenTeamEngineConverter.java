package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.FactoryType.Factory;
import com.fumbbl.ffb.PlayerType;
import com.fumbbl.ffb.SkillCategory;
import com.fumbbl.ffb.factory.IFactorySource;
import com.fumbbl.ffb.factory.SkillFactory;
import com.fumbbl.ffb.json.IJsonOption;
import com.fumbbl.ffb.model.Roster;
import com.fumbbl.ffb.model.RosterPlayer;
import com.fumbbl.ffb.model.RosterPosition;
import com.fumbbl.ffb.model.Team;
import com.fumbbl.ffb.model.skill.Skill;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;

/** Converts only persisted frozen facts; current catalog data is never read. */
public final class FrozenTeamEngineConverter {
	public Team convert(FrozenTeam frozen, IFactorySource factories) {
		if (!"BB2025".equals(frozen.ruleset)) throw new IllegalArgumentException("Unsupported frozen ruleset");
		if (!frozen.resources.keySet().equals(new HashSet<>(Arrays.asList("rerolls", "apothecary", "assistantCoaches", "cheerleaders", "dedicatedFans")))) throw new IllegalArgumentException("Unsupported frozen resources");
		SkillFactory skills = factories.getFactory(Factory.SKILL);
		Team team = new Team(factories);
		team.setId(frozen.sourceTeamId);
		team.setRosterId(frozen.rosterId);
		team.setReRolls(resource(frozen, "rerolls"));
		team.setApothecaries(resource(frozen, "apothecary"));
		team.setAssistantCoaches(resource(frozen, "assistantCoaches"));
		team.setCheerleaders(resource(frozen, "cheerleaders"));
		team.setDedicatedFans(resource(frozen, "dedicatedFans"));
		team.setTeamValue(frozen.total);
		for (FrozenTeam.Player source : frozen.players) {
			RosterPlayer player = new RosterPlayer();
			player.setId(frozen.sourceTeamId + ":" + source.id);
			player.setName(source.id);
			player.setNr(source.slot);
			player.setPositionId(source.positionId);
			player.setMovement(source.ma); player.setStrength(source.st); player.setAgility(source.ag);
			player.setPassing(source.pa); player.setArmour(source.av);
			for (String id : source.baseSkillIds) player.addSkill(skill(skills, id));
			for (String id : source.skillIds) player.addSkill(skill(skills, id));
			if (source.id.equals(frozen.captainId)) player.addSkill(skill(skills, "pro"));
			team.addPlayer(player);
		}
		team.updateRoster(roster(frozen, factories, skills), false, factories);
		return team;
	}

	private Roster roster(FrozenTeam frozen, IFactorySource factories, SkillFactory skills) {
		Map<String, FrozenTeam.Player> unique = new LinkedHashMap<String, FrozenTeam.Player>();
		for (FrozenTeam.Player player : frozen.players) {
			FrozenTeam.Player existing = unique.put(player.positionId, player);
			if (existing != null && (existing.ma != player.ma || existing.st != player.st || existing.ag != player.ag || existing.pa != player.pa || existing.av != player.av || existing.cost != player.cost || !existing.baseSkillIds.equals(player.baseSkillIds) || !existing.parameters.equals(player.parameters) || !existing.primary.equals(player.primary) || !existing.secondary.equals(player.secondary) || !existing.role.equals(player.role))) throw new IllegalArgumentException("Conflicting frozen position");
		}
		JsonObject document = new Roster().toJsonValue();
		IJsonOption.ROSTER_ID.addTo(document, frozen.rosterId);
		IJsonOption.ROSTER_NAME.addTo(document, frozen.rosterId);
		JsonObject catalog = JsonObject.readFrom(frozen.resolvedCatalogJson);
		JsonObject rerolls = resourceDefinition(catalog, "rerolls");
		IJsonOption.RE_ROLL_COST.addTo(document, rerolls.get("cost").asInt());
		IJsonOption.MAX_RE_ROLLS.addTo(document, rerolls.get("maximum").asInt());
		IJsonOption.APOTHECARY.addTo(document, resourceDefinition(catalog, "apothecary").get("maximum").asInt() > 0);
		JsonArray positions = new JsonArray();
		for (FrozenTeam.Player player : unique.values()) positions.add(position(player, skills));
		IJsonOption.POSITION_ARRAY.addTo(document, positions);
		return new Roster().initFrom(factories, document);
	}

	private JsonObject position(FrozenTeam.Player source, SkillFactory skills) {
		JsonObject value = new RosterPosition().toJsonValue();
		IJsonOption.POSITION_ID.addTo(value, source.positionId);
		IJsonOption.POSITION_NAME.addTo(value, source.name);
		IJsonOption.QUANTITY.addTo(value, source.maximum);
		IJsonOption.COST.addTo(value, source.cost);
		IJsonOption.MOVEMENT.addTo(value, source.ma);
		IJsonOption.STRENGTH.addTo(value, source.st);
		IJsonOption.AGILITY.addTo(value, source.ag);
		IJsonOption.PASSING.addTo(value, source.pa);
		IJsonOption.ARMOUR.addTo(value, source.av);
		IJsonOption.RACE.addTo(value, source.race);
		IJsonOption.PLAYER_TYPE.addTo(value, "Big Guy".equals(source.role) ? PlayerType.BIG_GUY : PlayerType.REGULAR);
		IJsonOption.SKILL_CATEGORIES_NORMAL.addTo(value, categories(source.primary));
		IJsonOption.SKILL_CATEGORIES_DOUBLE.addTo(value, categories(source.secondary));
		JsonArray names = new JsonArray(); JsonArray values = new JsonArray();
		if (!source.parameters.keySet().equals(new HashSet<>(source.baseSkillIds))) throw new IllegalArgumentException("Unsupported frozen parameters");
		for (String id : source.baseSkillIds) {
			names.add(skill(skills, id).getName());
			int parameter = source.parameters.get(id), expected = "loner".equals(id) ? 3 : "mighty-blow".equals(id) ? 1 : 0;
			if (parameter != expected) throw new IllegalArgumentException("Unsupported frozen parameter");
			values.add(parameter == 0 ? JsonValue.NULL : JsonValue.valueOf(Integer.toString(parameter)));
		}
		IJsonOption.SKILL_ARRAY.addTo(value, names);
		value.set("skillValues", values);
		return value;
	}

	private int resource(FrozenTeam team, String key) { Integer value = team.resources.get(key); if (value == null || value < 0) throw new IllegalArgumentException("Unsupported frozen resource: " + key); return value; }
	private Skill skill(SkillFactory factory, String id) { Skill value = factory.forName(name(id)); if (value == null) throw new IllegalArgumentException("Unsupported frozen skill: " + id); return value; }
	private JsonObject resourceDefinition(JsonObject catalog, String id) {
		for (JsonValue value : catalog.get("resources").asArray()) if (id.equals(value.asObject().getString("id", null))) return value.asObject();
		throw new IllegalArgumentException("Unsupported frozen resource definition");
	}
	private JsonArray categories(String encoded) {
		JsonArray result = new JsonArray();
		for (char category : encoded.toCharArray()) {
			switch (category) {
				case 'A': result.add(SkillCategory.AGILITY.getName()); break;
				case 'D': result.add(SkillCategory.DEVIOUS.getName()); break;
				case 'G': result.add(SkillCategory.GENERAL.getName()); break;
				case 'P': result.add(SkillCategory.PASSING.getName()); break;
				case 'S': result.add(SkillCategory.STRENGTH.getName()); break;
				default: throw new IllegalArgumentException("Unsupported frozen skill category");
			}
		}
		return result;
	}
	private String name(String id) { if ("block".equals(id)) return "Block"; if ("dodge".equals(id)) return "Dodge"; if ("catch".equals(id)) return "Catch"; if ("pass".equals(id)) return "Pass"; if ("sure-hands".equals(id)) return "Sure Hands"; if ("tackle".equals(id)) return "Tackle"; if ("pro".equals(id)) return "Pro"; if ("right-stuff".equals(id)) return "Right Stuff"; if ("stunty".equals(id)) return "Stunty"; if ("bone-head".equals(id)) return "Bone Head"; if ("loner".equals(id)) return "Loner"; if ("mighty-blow".equals(id)) return "Mighty Blow"; if ("thick-skull".equals(id)) return "Thick Skull"; if ("throw-team-mate".equals(id)) return "Throw Team-Mate"; throw new IllegalArgumentException("Unsupported frozen skill: " + id); }
}
