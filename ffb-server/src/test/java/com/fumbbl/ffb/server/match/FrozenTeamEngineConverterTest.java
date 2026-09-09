package com.fumbbl.ffb.server.match;

import com.fumbbl.ffb.FactoryManager;
import com.fumbbl.ffb.FactoryType.Factory;
import com.fumbbl.ffb.factory.SkillFactory;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.Team;
import com.fumbbl.ffb.option.GameOptionId;
import com.fumbbl.ffb.option.GameOptionString;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;
import com.fumbbl.ffb.server.team.bb2025.TeamValidation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrozenTeamEngineConverterTest {
	private RosterCatalog catalog;
	private Game game;
	private SkillFactory skills;
	private final FrozenTeamEngineConverter converter = new FrozenTeamEngineConverter();

	@BeforeEach
	void setup() {
		catalog = new RosterCatalog(); game = new Game(null, new FactoryManager());
		game.getOptions().addOption(new GameOptionString(GameOptionId.RULESVERSION).setValue("BB2025")); game.initializeRules();
		skills = game.getRules().getFactory(Factory.SKILL);
	}

	@Test
	void allPositionsBaseSkillsParametersCaptainAndResourcesSurviveConversion() {
		FrozenTeam frozen = frozen(null); Team team = converter.convert(frozen, game.getRules());
		assertEquals("human", team.getRosterId()); assertNotNull(team.getRoster()); assertEquals(50000, team.getRoster().getReRollCost());
		assertEquals(8, team.getRoster().getMaxReRolls()); assertEquals(2, team.getReRolls()); assertEquals(1, team.getApothecaries());
		assertEquals(11, team.getPlayers().length); assertEquals(frozen.total, team.getTeamValue());
		for (FrozenTeam.Player source : frozen.players) {
			Player<?> player = team.getPlayerById(frozen.sourceTeamId + ":" + source.id);
			assertNotNull(player); assertNotNull(player.getPosition()); assertEquals(source.slot, player.getNr());
			assertEquals(source.ma, player.getMovement()); assertEquals(source.st, player.getStrength()); assertEquals(source.ag, player.getAgility());
			assertEquals(source.pa, player.getPassing()); assertEquals(source.av, player.getArmour()); assertEquals(source.cost, player.getPosition().getCost());
			for (String id : source.baseSkillIds) assertTrue(player.hasSkillExcludingTemporaryOnes(skills.forName(catalog.getSkills().get(id).name)), id);
			if (source.positionId.equals("ogre")) {
				assertEquals("3", player.getPosition().getSkillValue(skills.forName("Loner")));
				assertEquals("1", player.getPosition().getSkillValue(skills.forName("Mighty Blow")));
			}
		}
		assertTrue(team.getPlayerById(frozen.sourceTeamId + ":p1").hasSkillExcludingTemporaryOnes(skills.forName("Pro")));
		// Conversion creates neither a game membership nor a regular-play phase.
		assertEquals(0, game.getTeamHome().getPlayers().length); assertEquals(0, game.getTeamAway().getPlayers().length);
	}

	@Test
	void everyPurchasableSkillResolvesAndIndependentConversionCannotMutateFrozenData() {
		for (RosterCatalog.SkillOption option : catalog.getSkills().values()) if (option.selectable) {
			FrozenTeam frozen = frozen(option.id); Team team = converter.convert(frozen, game.getRules());
			assertTrue(team.getPlayerById(frozen.sourceTeamId + ":p11").hasSkillExcludingTemporaryOnes(skills.forName(option.name)), option.id);
			team.setReRolls(7); assertEquals(2, frozen.resources.get("rerolls").intValue());
			assertEquals(2, converter.convert(frozen, game.getRules()).getReRolls());
		}
		assertNotEquals(converter.convert(frozen(null), game.getRules()).getPlayers()[0].getId(), converter.convert(frozen(null), game.getRules()).getPlayers()[0].getId());
	}

	@Test
	void unsupportedSkillAndParameterFailWithoutReturningPartialTeam() {
		FrozenTeam frozen = frozen(null); List<FrozenTeam.Player> players = new ArrayList<>(frozen.players);
		FrozenTeam.Player original = players.get(0);
		players.set(0, copy(original, Collections.singletonList("unmapped"), original.parameters));
		assertThrows(IllegalArgumentException.class, () -> converter.convert(copy(frozen, players), game.getRules()));
		players.set(0, copy(original, original.skillIds, Collections.singletonMap("unknown", 99)));
		assertThrows(IllegalArgumentException.class, () -> converter.convert(copy(frozen, players), game.getRules()));
	}

	private FrozenTeam frozen(String purchase) {
		List<TeamDraft.Player> players = new ArrayList<>(); int slot = 1;
		for (String position : catalog.getPositions().keySet()) players.add(new TeamDraft.Player("p" + slot, slot++, position, Collections.emptyList()));
		while (slot <= 11) { players.add(new TeamDraft.Player("p" + slot, slot, slot == 11 && "pass".equals(purchase) ? "catcher" : "lineman", slot == 11 && purchase != null ? Collections.singletonList(purchase) : Collections.emptyList())); slot++; }
		Map<String, Integer> resources = new LinkedHashMap<>(); for (String key : catalog.getResources().keySet()) resources.put(key, 0);
		resources.put("rerolls", 2); resources.put("apothecary", 1);
		TeamDraft draft = new TeamDraft(RosterCatalog.VERSION, "BB2025", "human", RosterCatalog.PRESET, "p1", players, resources);
		TeamValidation.Evaluation result = new TeamValidation(catalog).evaluate(draft); assertTrue(result.isValid());
		return new FrozenTeam(UUID.randomUUID().toString(), 1, "home", draft, result.total, result.skillPoints, catalog);
	}
	private FrozenTeam.Player copy(FrozenTeam.Player p, List<String> skills, Map<String, Integer> parameters) {
		return new FrozenTeam.Player(p.id, p.slot, p.positionId, skills, p.baseSkillIds, p.name, p.role, p.race, p.primary, p.secondary, p.maximum, p.cost, p.ma, p.st, p.ag, p.pa, p.av, parameters);
	}
	private FrozenTeam copy(FrozenTeam t, List<FrozenTeam.Player> players) {
		return new FrozenTeam(t.sourceTeamId, t.sourceDocumentVersion, t.owner, t.ruleset, t.catalogVersion, t.rosterId, t.presetId, t.presetVersion, t.captainId, t.total, t.budget, t.skillPoints, players, t.resources, t.resolvedCatalogJson);
	}
}
