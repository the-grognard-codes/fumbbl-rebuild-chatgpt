package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.Team;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.match.MatchDocument;
import com.fumbbl.ffb.server.match.SetupSession;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recovery checkpoints at transitions that retain native step-local state. */
class RecoveryScenariosTest {
	@Test void prematchCheckpointRestoresInAnotherJvm() throws Exception { restore(recoverable()); }
	private int requestNumber;
	@Test
	void placementCheckpointRestoresBothViewsAndRetry() throws Exception {
		SetupSession session = recoverable(); choosePrematch(session);
		JsonObject snapshot = view(session, "home"); String role = snapshot.getString("actor", null);
		JsonObject player = firstPlayer(snapshot, role);
		JsonObject request = request(snapshot, "place", "placement").add("playerId", player.get("id"))
			.add("to", new JsonObject().add("x", "home".equals(role) ? 10 : 15).add("y", 4));
		assertEquals("ACCEPTED", session.apply(role, request).getString("code", null));
		assertRestoresAndRetries(session, role, request);
	}

	@Test
	void defendingTeamBlockDecisionCheckpointRestoresNativeState() throws Exception {
		SetupSession session = ready(recoverable()); advanceKickoff(session);
		String[] players = forceUphillBlock(session);
		submit(session, actionFor(session, "selectBlock", players[0]), "select-block");
		submit(session, actionFor(session, "block", players[1]), "block");
		JsonObject die = action(session, "blockDie");
		JsonObject request = request(view(session, "home"), "action", "defending-die").add("actionId", die.get("id"));
		Game game = engine(session).getGame();
		String active = game.getTeamHome().hasPlayer(game.getActingPlayer().getPlayer()) ? "home" : "away";
		assertTrue(!active.equals(die.getString("actor", null)), "An uphill block leaves die selection to the defending team: " + game.getDialogParameter().toJsonValue());
		SetupSession restored = restore(session);
		assertEquals(session.apply(die.getString("actor", null), request), restored.apply(die.getString("actor", null), request));
		String after = restored.recoveryArtifact();
		assertTrue(restored.apply(die.getString("actor", null), request).getBoolean("duplicate", false));
		assertEquals(after, restored.recoveryArtifact());
	}

	@Test
	void touchdownDriveAndTerminalCompletedMatchRestoreExactly() throws Exception {
		SetupSession session = ready(recoverable()); advanceKickoff(session);
		Game game = engine(session).getGame(); Player<?> scorer = game.getActingTeam().getPlayers()[0];
		boolean home = game.isHomePlaying(); FieldCoordinate at = new FieldCoordinate(home ? 24 : 1, 7);
		game.getFieldModel().setPlayerCoordinate(scorer, at); game.getFieldModel().setBallCoordinate(at);
		game.getFieldModel().setBallInPlay(true); game.getFieldModel().setBallMoving(false);
		JsonObject select = null;
		for (JsonValue item : view(session, "home").get("actions").asArray()) {
			JsonObject candidate = item.asObject();
			if ("select".equals(candidate.getString("kind", null)) && candidate.getString("id", "").endsWith(scorer.getId())) select = candidate;
		}
		assertTrue(select != null); submit(session, select, "score-select");
		JsonObject score = ending(session, ":move-" + (home ? 25 : 0) + "-7");
		JsonObject touchdown = request(view(session, "home"), "action", "touchdown").add("actionId", score.get("id"));
		assertEquals("ACCEPTED", session.apply(score.getString("actor", null), touchdown).getString("code", null));
		assertEquals("SETUP", view(session, "home").getString("phase", null));
		assertRestoresAndRetries(session, score.getString("actor", null), touchdown);
		SetupSession terminal = playToFullTime(session);
		SetupSession restored = restore(terminal);
		assertEquals(terminal.completedMatch().json(), restored.completedMatch().json());
	}

	private SetupSession playToFullTime(SetupSession session) throws Exception {
		boolean restoredAtHalftime = false;
		JsonObject last = null; String role = null;
		for (int number = 0; number < 160 && !session.isComplete(); number++) {
			JsonObject snapshot = view(session, "home");
			if ("SETUP".equals(snapshot.getString("phase", null))) {
				if (snapshot.getInt("half", 0) == 2 && !restoredAtHalftime) {
					session = restore(session); restoredAtHalftime = true;
				}
				arrange(session); continue;
			}
			JsonObject selected;
			if ("READY_FOR_KICKOFF".equals(snapshot.getString("phase", null))) {
				engine(session).getDiceRoller().clearTestRolls();
				TestRolls.on(engine(session)).general(1, 1, 3, 3, 3, 3, 3, 3);
				selected = snapshot.get("actions").asArray().get(82).asObject();
			} else if (hasAction(session, "endTurn")) selected = action(session, "endTurn");
			else selected = snapshot.get("actions").asArray().get(0).asObject();
			last = request(view(session, "home"), "action", "full-" + number).add("actionId", selected.get("id"));
			role = selected.getString("actor", null);
			assertEquals("ACCEPTED", session.apply(role, last).getString("code", null));
		}
		assertTrue(session.isComplete()); assertTrue(restoredAtHalftime);
		String artifact = session.recoveryArtifact();
		assertTrue(session.apply(role, last).getBoolean("duplicate", false)); assertEquals(artifact, session.recoveryArtifact());
		return session;
	}

	private SetupSession ready(SetupSession session) { choosePrematch(session); arrange(session); arrange(session); return session; }
	private void choosePrematch(SetupSession session) {
		for (int i = 0; i < 2; i++) {
			JsonObject state = view(session, "home"), prompt = state.get("prompt").asObject();
			JsonObject command = request(state, "choice", "prematch-" + i).add("promptId", prompt.get("id")).add("optionId", prompt.get("options").asArray().get(0));
			assertEquals("ACCEPTED", session.apply(prompt.getString("actor", null), command).getString("code", null));
		}
	}
	private void arrange(SetupSession session) {
		JsonObject snapshot = view(session, "home"); String role = snapshot.getString("actor", null); int index = 0;
		for (JsonValue value : snapshot.get("players").asArray()) {
			JsonObject player = value.asObject(); if (!role.equals(player.getString("role", null))) continue;
			int x = index < 3 ? 12 : 10; if ("away".equals(role)) x = 25 - x;
			JsonObject command = request(view(session, "home"), "place", unique("setup-" + role + "-" + index)).add("playerId", player.get("id"))
				.add("to", new JsonObject().add("x", x).add("y", index < 3 ? 6 + index : index + 1));
			assertEquals("ACCEPTED", session.apply(role, command).getString("code", null)); index++;
		}
		assertEquals("ACCEPTED", session.apply(role, request(view(session, "home"), "confirm", unique("confirm-" + role))).getString("code", null));
	}
	private void advanceKickoff(SetupSession session) {
		TestRolls.on(engine(session)).general(1, 1, 3, 3, 3, 3, 3, 3);
		submit(session, view(session, "home").get("actions").asArray().get(82).asObject(), "kickoff");
		for (int i = 0; i < 20 && !"REGULAR".equals(view(session, "home").getString("turnMode", null)); i++)
			submit(session, view(session, "home").get("actions").asArray().get(0).asObject(), "kickoff-next-" + i);
		assertEquals("REGULAR", view(session, "home").getString("turnMode", null));
	}
	private void assertRestoresAndRetries(SetupSession original, String role, JsonObject prior) throws Exception {
		SetupSession restored = restore(original); String before = restored.recoveryArtifact();
		assertTrue(restored.apply(role, prior).getBoolean("duplicate", false)); assertEquals(before, restored.recoveryArtifact());
	}
	private SetupSession restore(SetupSession original) throws Exception {
		java.nio.file.Path directory = java.nio.file.Paths.get("target", "r2-process-characterization");
		java.nio.file.Files.createDirectories(directory);
		java.nio.file.Path artifact = java.nio.file.Files.createTempFile(directory, "checkpoint-", ".json");
		java.nio.file.Files.write(artifact, new JsonObject().add("document", new com.fumbbl.ffb.server.match.MatchJson().encode(document(original)))
			.add("artifact", original.recoveryArtifact()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		String javaExecutable = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString();
		Process process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("surefire.test.class.path"),
			RecoveryProcessFixture.class.getName(), artifact.toAbsolutePath().toString()).redirectErrorStream(true).start();
		assertTrue(process.waitFor(45, java.util.concurrent.TimeUnit.SECONDS), "Separate recovery JVM timed out");
		String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		assertEquals(0, process.exitValue(), output);
		SetupSession restored = new SetupSession(new TestServer().getServer(), document(original), original.recoveryArtifact());
		assertEquals(view(original, "home"), view(restored, "home")); assertEquals(view(original, "away"), view(restored, "away"));
		assertEquals(original.recoveryArtifact(), restored.recoveryArtifact()); return restored;
	}
	private SetupSession recoverable() throws Exception {
		MatchDocument seed = document(new SetupSessionTest().session(11));
		java.util.Map<String, MatchDocument.Request> requests = new java.util.LinkedHashMap<>();
		requests.put(seed.home.owner + "\ncreate", new MatchDocument.Request("create|" + seed.home.team.sourceTeamId + "|1|" + seed.away.owner));
		requests.put(seed.away.owner + "\njoin", new MatchDocument.Request("join|" + seed.matchId + "|1|" + seed.away.team.sourceTeamId + "|1"));
		requests.put(seed.home.owner + "\nactivate", new MatchDocument.Request("activate|" + seed.matchId + "|2"));
		MatchDocument document = new MatchDocument(seed.matchId, 3, seed.intendedOpponent, seed.lifecycle, seed.home, seed.away, requests);
		return new SetupSession(new TestServer().getServer(), document, -9, true);
	}
	private MatchDocument document(SetupSession session) throws Exception { Field field = SetupSession.class.getDeclaredField("document"); field.setAccessible(true); return (MatchDocument) field.get(session); }
	private GameState engine(SetupSession session) { try { Field field = SetupSession.class.getDeclaredField("state"); field.setAccessible(true); return (GameState) field.get(session); } catch (Exception failure) { throw new AssertionError(failure); } }
	private JsonObject view(SetupSession session, String role) { return session.reply("inspect", "ACCEPTED", false, role).get("state").asObject(); }
	private JsonObject firstPlayer(JsonObject state, String role) { for (JsonValue value : state.get("players").asArray()) if (role.equals(value.asObject().getString("role", null))) return value.asObject(); throw new AssertionError("No player"); }
	private String unique(String prefix) { return prefix + "-" + requestNumber++; }
	private String[] forceUphillBlock(SetupSession session) {
		Game game = engine(session).getGame(); Team attacker = game.isHomePlaying() ? game.getTeamHome() : game.getTeamAway();
		Team defender = game.isHomePlaying() ? game.getTeamAway() : game.getTeamHome();
		game.getTurnDataHome().setReRolls(0); game.getTurnDataAway().setReRolls(0);
		for (Player<?> player : attacker.getPlayers()) game.getFieldModel().setPlayerCoordinate(player, null);
		for (Player<?> player : defender.getPlayers()) game.getFieldModel().setPlayerCoordinate(player, null);
		Player<?> attackerPlayer = attacker.getPlayers()[1], target = defender.getPlayers()[0];
		// Explicit native-state fixture: an injured attacker facing a stronger defender.
		attackerPlayer.setStrength(1); target.setStrength(6);
		game.getFieldModel().setPlayerCoordinate(attackerPlayer, new FieldCoordinate(10, 7));
		game.getFieldModel().setPlayerCoordinate(target, new FieldCoordinate(11, 7));
		game.getFieldModel().setPlayerCoordinate(defender.getPlayers()[1], new FieldCoordinate(10, 6));
		game.getFieldModel().setPlayerCoordinate(defender.getPlayers()[2], new FieldCoordinate(10, 8));
		return new String[] { attackerPlayer.getId(), target.getId() };
	}
	private JsonObject request(JsonObject state, String operation, String id) { return new JsonObject().add("operation", operation).add("requestId", id).add("expectedRevision", state.get("revision")); }
	private JsonObject action(SetupSession session, String kind) { for (JsonValue value : view(session, "home").get("actions").asArray()) if (kind.equals(value.asObject().getString("kind", null))) return value.asObject(); throw new AssertionError("Missing " + kind); }
	private JsonObject actionFor(SetupSession session, String kind, String player) { for (JsonValue value : view(session, "home").get("actions").asArray()) { JsonObject action = value.asObject(); if (kind.equals(action.getString("kind", null)) && action.getString("id", "").endsWith(player)) return action; } throw new AssertionError("Missing " + kind + " for " + player); }
	private boolean hasAction(SetupSession session, String kind) { for (JsonValue value : view(session, "home").get("actions").asArray()) if (kind.equals(value.asObject().getString("kind", null))) return true; return false; }
	private JsonObject ending(SetupSession session, String ending) { for (JsonValue value : view(session, "home").get("actions").asArray()) if (value.asObject().getString("id", "").endsWith(ending)) return value.asObject(); throw new AssertionError("Missing " + ending); }
	private void submit(SetupSession session, JsonObject action, String id) { assertEquals("ACCEPTED", session.apply(action.getString("actor", null), request(view(session, "home"), "action", id).add("actionId", action.get("id"))).getString("code", null)); }
}
