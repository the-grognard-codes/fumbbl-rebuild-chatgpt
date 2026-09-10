package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.match.FrozenTeam;
import com.fumbbl.ffb.server.match.MatchDocument;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.match.SetupSession;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;
import com.fumbbl.ffb.server.team.bb2025.TeamValidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupSessionTest {
	@Test void twelvePlayerRosterMustFieldItsCaptainAndCanCorrectPlacementThroughReserves() throws Exception {
		SetupSession session = session(12); choices(session);
		JsonObject view = view(session); String actor = view.getString("actor", null);
		String captain = null, replacement = null;
		int index = 0;
		for (JsonValue value : view.get("players").asArray()) {
			JsonObject player = value.asObject();
			if (!actor.equals(player.getString("role", null))) continue;
			if (player.getInt("slot", 0) == 1) { captain = player.getString("id", null); continue; }
			int x = index < 3 ? 12 : 10; if ("away".equals(actor)) x = 25 - x;
			int y = index < 3 ? index + 6 : index + 1;
			assertEquals("ACCEPTED", session.apply(actor, request(view(session), "place").add("playerId", player.get("id"))
				.add("to", new JsonObject().add("x", x).add("y", y))).getString("code", null));
			replacement = player.getString("id", null); index++;
		}
		assertEquals("ILLEGAL_SETUP", session.apply(actor, request(view(session), "confirm")).getString("code", null));
		assertEquals("ACCEPTED", session.apply(actor, request(view(session), "place").add("playerId", replacement).add("to", JsonValue.NULL)).getString("code", null));
		assertEquals("ACCEPTED", session.apply(actor, request(view(session), "place").add("playerId", captain)
			.add("to", new JsonObject().add("x", "home".equals(actor) ? 10 : 15).add("y", 11))).getString("code", null));
		assertEquals("ACCEPTED", session.apply(actor, request(view(session), "confirm")).getString("code", null));
	}
	@Test void failedEngineCannotAdvertiseAnAcceptedSnapshotOrRetry() throws Exception {
		SetupSession session = session(11);
		java.lang.reflect.Field failed = SetupSession.class.getDeclaredField("failed");
		failed.setAccessible(true); failed.setBoolean(session, true);
		JsonObject response = session.reply("load", "ACCEPTED", true, "home");
		assertEquals("SESSION_UNAVAILABLE", response.getString("code", null));
		assertTrue(response.get("state").isNull());
		assertEquals(false, response.getBoolean("duplicate", true));
	}
	@Test void genuinePrematchAndBothSetupsReachKickoffWithoutFixtureDice() throws Exception {
		SetupSession session = session(11);
		JsonObject view = view(session);
		assertEquals("coin", view.get("prompt").asObject().getString("kind", null));
		choices(session);
		for (int side = 0; side < 2; side++) {
			view = view(session); String role = view.getString("actor", null);
			assertEquals("SETUP", view.getString("phase", null));
			JsonObject invalid = request(view, "confirm");
			assertEquals("ILLEGAL_SETUP", session.apply(role, invalid).getString("code", null));
			int index = 0;
			for (JsonValue value : view.get("players").asArray()) {
				JsonObject player = value.asObject();
				if (!role.equals(player.getString("role", null))) continue;
				int x = index < 3 ? 12 : 10; if ("away".equals(role)) x = 25 - x;
				int y = index < 3 ? 6 + index : 4 + index - 3;
				JsonObject move = request(view(session), "place").add("playerId", player.get("id"))
					.add("to", new JsonObject().add("x", x).add("y", y));
				assertEquals("ACCEPTED", session.apply(role, move).getString("code", null));
				String after = serialized(session);
				assertTrue(session.apply(role, move).getBoolean("duplicate", false));
				assertEquals(after, serialized(session));
				index++;
			}
			assertEquals("ACCEPTED", session.apply(role, request(view(session), "confirm")).getString("code", null));
		}
		assertEquals("READY_FOR_KICKOFF", view(session).getString("phase", null));
		assertEquals(26, view(session).getInt("revision", -1));
	}

	@Test void wrongRoleInvalidPlacementAndStaleRequestsDoNotChangeEngineView() throws Exception {
		SetupSession session = session(11);
		JsonObject view = view(session), prompt = view.get("prompt").asObject();
		JsonObject choice = request(view, "choice").add("promptId", prompt.get("id")).add("optionId", "heads");
		String role = view.getString("actor", null), before = view.toString();
        final String wrong = "home".equals(role) ? "away" : "home";
		assertEquals("WRONG_ACTOR", assertThrows(MatchService.Failure.class,
			() -> session.apply(wrong, choice)).code);
		assertEquals(before, view(session).toString());
		choices(session);
		view = view(session); role = view.getString("actor", null);
		final String actor = role;
		JsonObject bad = request(view, "place").add("playerId", view.get("players").asArray().get(0).asObject().get("id"))
			.add("to", new JsonObject().add("x", 26).add("y", 0));
		String atSetup = view.toString();
		assertThrows(MatchService.Failure.class, () -> session.apply(actor, bad));
		assertEquals(atSetup, view(session).toString());
	}

    @Test void kickoffAndBothParticipantsMoveAcrossMultipleTurns() throws Exception {
        SetupSession session = readySession();
        JsonObject snapshot = view(session);
        JsonObject kick = snapshot.get("actions").asArray().get(90).asObject();
        submit(session, kick);
        for (int i = 0; i < 20 && !"REGULAR".equals(view(session).getString("turnMode", null)); i++) {
            JsonObject current = view(session);
            assertTrue(current.get("actions").asArray().size() > 0, "Unsupported kickoff: " + serialized(session));
            JsonObject next = current.get("actions").asArray().get(0).asObject();
            for (JsonValue value : current.get("actions").asArray()) if (value.asObject().getString("id", "").contains("decline-event")) next = value.asObject();
            submit(session, next);
        }
        assertEquals("REGULAR", view(session).getString("turnMode", null));
        for (int turn = 0; turn < 4; turn++) {
            JsonObject select = findAction(session, "select");
            submit(session, select);
            JsonObject move = findAction(session, "move");
            JsonObject request = request(view(session), "action").add("actionId", move.get("id"));
            String role = move.getString("actor", null);
            String before = serialized(session);
            TestRolls.on(engine(session)).block("skull");
            int diceBefore = engine(session).getDiceRoller().getTestRolls().values().stream().mapToInt(java.util.List::size).sum();
            JsonObject illegal = request(view(session), "action").add("actionId", "not-issued");
            assertEquals("INVALID_OPTION", assertThrows(MatchService.Failure.class, () -> session.apply(role, illegal)).code);
            assertEquals(before, serialized(session));
            assertEquals(diceBefore, engine(session).getDiceRoller().getTestRolls().values().stream().mapToInt(java.util.List::size).sum());
            assertEquals("WRONG_ACTOR", assertThrows(MatchService.Failure.class,
                () -> session.apply("home".equals(role) ? "away" : "home", request)).code);
            assertEquals(before, serialized(session));
            assertEquals("ACCEPTED", session.apply(role, request).getString("code", null));
            String after = serialized(session);
            assertTrue(session.apply(role, request).getBoolean("duplicate", false));
            assertEquals(after, serialized(session));
            assertEquals(diceBefore, engine(session).getDiceRoller().getTestRolls().values().stream().mapToInt(java.util.List::size).sum());
            JsonObject stale = JsonObject.readFrom(request.toString()).set("requestId", UUID.randomUUID().toString());
            assertEquals("STALE_REVISION", assertThrows(MatchService.Failure.class, () -> session.apply(role, stale)).code);
            assertEquals(after, serialized(session));
            for (int decision = 0; decision < 10 && !hasAction(session, "endTurn"); decision++) {
                assertTrue(view(session).get("actions").asArray().size() > 0, "Unsupported decision: " + serialized(session));
                submit(session, view(session).get("actions").asArray().get(0).asObject());
            }
            submit(session, findAction(session, "endTurn"));
        }
        assertTrue(view(session).getInt("turn", 0) >= 3);
    }
    @Test void everyKickoffResultCanProgressToRegularTurn() throws Exception {
        for (int total = 2; total <= 12; total++) {
            SetupSession session = readySession();
            int first = Math.min(6, total - 1), second = total - first;
            TestRolls.on(engine(session)).general(1, 1, first, second);
            for (int i = 0; i < 100; i++) TestRolls.on(engine(session)).general(3);
            submit(session, view(session).get("actions").asArray().get(82).asObject());
            for (int i = 0; i < 15 && !"REGULAR".equals(view(session).getString("turnMode", null)); i++) {
                JsonObject chosen = null;
                for (JsonValue value : view(session).get("actions").asArray()) {
                    JsonObject action = value.asObject();
                    if (chosen == null || action.getString("id", "").contains("decline-event") || action.getString("id", "").contains("end-event")) chosen = action;
                }
                assertTrue(chosen != null, "Kickoff " + total + " stalled at " + engine(session).getCurrentStep().getId());
                submit(session, chosen);
            }
            assertEquals("REGULAR", view(session).getString("turnMode", null), "Kickoff " + total);
        }
    }
    @Test void selectedSolidDefencePlayerCanBeRedeployedAndConfirmed() throws Exception {
        SetupSession session = readySession();
        TestRolls.on(engine(session)).general(1, 1, 2, 2, 3);
        submit(session, view(session).get("actions").asArray().get(82).asObject());
        JsonObject pick = null;
        for (JsonValue value : view(session).get("actions").asArray()) if (value.asObject().getString("id", "").contains("event-pick:")) { pick = value.asObject(); break; }
        assertTrue(pick != null, "Expected Solid Defence player choice");
        String playerId = pick.getString("id", "").split("event-pick:")[1];
        com.fumbbl.ffb.FieldCoordinate original = engine(session).getGame().getFieldModel().getPlayerCoordinate(engine(session).getGame().getPlayerById(playerId));
        submit(session, pick);
        submit(session, actionEnding(session, ":event-confirm"));
        assertEquals("SOLID_DEFENCE", view(session).getString("turnMode", null));
        JsonObject invalid = request(view(session), "action").add("actionId", actionEnding(session, ":confirm-solid-defence").get("id"));
        String before = serialized(session), role = view(session).getString("actor", null);
        assertEquals("ILLEGAL_SETUP", assertThrows(MatchService.Failure.class, () -> session.apply(role, invalid)).code);
        assertEquals(before, serialized(session));
        submit(session, actionEnding(session, ":" + original.getX() + ":" + original.getY()));
        submit(session, actionEnding(session, ":confirm-solid-defence"));
        assertTrue(!"SOLID_DEFENCE".equals(view(session).getString("turnMode", null)));
    }
    @Test void selectedChargePlayerCanTakeNativeKickoffTurn() throws Exception {
        SetupSession session = readySession();
        TestRolls.on(engine(session)).general(1, 1, 5, 5, 3, 3, 3);
        submit(session, view(session).get("actions").asArray().get(82).asObject());
        JsonObject pick = null;
        for (JsonValue value : view(session).get("actions").asArray()) if (value.asObject().getString("id", "").contains("event-pick:")) { pick = value.asObject(); break; }
        assertTrue(pick != null, "Expected Charge selection");
        JsonObject toggle = request(view(session), "action").add("actionId", pick.get("id"));
        String role = pick.getString("actor", null);
        String engineBefore = serialized(session);
        assertEquals("ACCEPTED", session.apply(role, toggle).getString("code", null));
        assertEquals(engineBefore, serialized(session), "Selection does not yet execute the engine");
        String selected = view(session).toString();
        assertTrue(session.apply(role, toggle).getBoolean("duplicate", false));
        assertEquals(selected, view(session).toString());
        submit(session, actionEnding(session, ":event-confirm"));
        assertEquals("BLITZ", view(session).getString("turnMode", null));
        submit(session, findAction(session, "select"));
        submit(session, findAction(session, "move"));
        submit(session, findAction(session, "endTurn"));
        assertTrue(!"BLITZ".equals(view(session).getString("turnMode", null)));
    }

    @Test void skullChoiceCausesOneTurnoverAndRetryConsumesNoDice() throws Exception {
        SetupSession session = readySession();
        TestRolls.on(engine(session)).general(1, 1, 3, 3, 3, 3, 3, 3);
        submit(session, view(session).get("actions").asArray().get(82).asObject());
        assertEquals("REGULAR", view(session).getString("turnMode", null));
        String originalActor = view(session).getString("actor", null);
        engine(session).getDiceRoller().clearTestRolls();
        TestRolls.on(engine(session)).block("skull", "pushback").armor(2, 2);
        submit(session, findAction(session, "selectBlock"));
        submit(session, findAction(session, "block"));
        JsonObject die = findAction(session, "blockDie");
        JsonObject choose = request(view(session), "action").add("actionId", die.get("id"));
        assertEquals("ACCEPTED", session.apply(die.getString("actor", null), choose).getString("code", null));
        assertTrue(!originalActor.equals(view(session).getString("actor", null)), "Native turnover changes active team");
        String after = serialized(session);
        int queued = engine(session).getDiceRoller().getTestRolls().values().stream().mapToInt(java.util.List::size).sum();
        assertTrue(session.apply(die.getString("actor", null), choose).getBoolean("duplicate", false));
        assertEquals(after, serialized(session));
        assertEquals(queued, engine(session).getDiceRoller().getTestRolls().values().stream().mapToInt(java.util.List::size).sum());
    }

    private JsonObject actionEnding(SetupSession session, String ending) {
        for (JsonValue value : view(session).get("actions").asArray()) if (value.asObject().getString("id", "").endsWith(ending)) return value.asObject();
        throw new AssertionError("Missing action ending " + ending + " at " + view(session));
    }

    private boolean hasAction(SetupSession session, String kind) {
        for (JsonValue value : view(session).get("actions").asArray()) if (kind.equals(value.asObject().getString("kind", null))) return true;
        return false;
    }
    private JsonObject findAction(SetupSession session, String kind) throws Exception {
        for (JsonValue value : view(session).get("actions").asArray()) if (kind.equals(value.asObject().getString("kind", null))) return value.asObject();
        throw new AssertionError("Missing " + kind + ": " + serialized(session));
    }
    private void submit(SetupSession session, JsonObject action) {
        assertEquals("ACCEPTED", session.apply(action.getString("actor", null), request(view(session), "action").add("actionId", action.get("id"))).getString("code", null));
    }
    private SetupSession readySession() throws Exception {
        SetupSession session = session(11); choices(session);
        for (int side = 0; side < 2; side++) {
            JsonObject snapshot = view(session); String role = snapshot.getString("actor", null); int index = 0;
            for (JsonValue value : snapshot.get("players").asArray()) {
                JsonObject player = value.asObject(); if (!role.equals(player.getString("role", null))) continue;
                int x = index < 3 ? 12 : 10; if ("away".equals(role)) x = 25 - x;
                int y = index < 3 ? 6 + index : index + 1;
                session.apply(role, request(view(session), "place").add("playerId", player.get("id")).add("to", new JsonObject().add("x", x).add("y", y))); index++;
            }
            assertEquals("ACCEPTED", session.apply(role, request(view(session), "confirm")).getString("code", null));
        }
        return session;
    }

	private void choices(SetupSession session) {
		for (int i = 0; i < 2; i++) {
			JsonObject view = view(session), prompt = view.get("prompt").asObject();
			JsonObject choice = request(view, "choice").add("promptId", prompt.get("id"))
				.add("optionId", prompt.get("options").asArray().get(0));
			assertEquals("ACCEPTED", session.apply(prompt.getString("actor", null), choice).getString("code", null));
		}
	}
	private JsonObject view(SetupSession session) { return session.reply("load", "ACCEPTED", false, "home").get("state").asObject(); }
	private String serialized(SetupSession session) throws Exception { return engine(session).toJsonValue().toString(); }
    private com.fumbbl.ffb.server.GameState engine(SetupSession session) throws Exception {
		java.lang.reflect.Field field = SetupSession.class.getDeclaredField("state"); field.setAccessible(true);
		return (com.fumbbl.ffb.server.GameState) field.get(session);
	}
	private JsonObject request(JsonObject view, String operation) {
		return new JsonObject().add("version", 1).add("type", "setup").add("operation", operation)
			.add("requestId", UUID.randomUUID().toString()).add("matchId", view.get("matchId"))
			.add("expectedRevision", view.get("revision"));
	}
	SetupSession session(int count) throws Exception {
		RosterCatalog catalog = new RosterCatalog(); List<TeamDraft.Player> players = new ArrayList<>();
		for (int i = 1; i <= count; i++) players.add(new TeamDraft.Player("p" + i, i, "lineman", Collections.emptyList()));
		Map<String, Integer> resources = new LinkedHashMap<>();
		for (String key : catalog.getResources().keySet()) resources.put(key, "rerolls".equals(key) ? 2 : 0);
		TeamDraft draft = new TeamDraft(RosterCatalog.VERSION, "BB2025", "human", RosterCatalog.PRESET, "p1", players, resources);
		TeamValidation.Evaluation evaluation = new TeamValidation(catalog).evaluate(draft);
		FrozenTeam home = new FrozenTeam(UUID.randomUUID().toString(), 1, "away", draft, evaluation.total, evaluation.skillPoints, catalog);
		FrozenTeam away = new FrozenTeam(UUID.randomUUID().toString(), 1, "home", draft, evaluation.total, evaluation.skillPoints, catalog);
		MatchDocument document = new MatchDocument(UUID.randomUUID().toString(), 3, "home", MatchDocument.Lifecycle.ACTIVATED,
			new MatchDocument.Member("home", "away", home), new MatchDocument.Member("away", "home", away));
		return new SetupSession(new TestServer().getServer(), document, -2);
	}
}
