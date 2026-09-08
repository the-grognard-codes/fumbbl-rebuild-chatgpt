package com.fumbbl.ffb.test;

import com.fumbbl.ffb.PlayerAction;
import com.fumbbl.ffb.PlayerState;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.local.BrowserMatchAdapter;
import com.eclipsesource.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrowserChoiceTest {
	@Test
	public void homeBothDown() throws Exception { verifyFixture(BrowserMatchAdapter.Fixture.BOTH_DOWN); }
	@Test
	public void homeBothDownWithBlock() throws Exception { verifyFixture(BrowserMatchAdapter.Fixture.BOTH_DOWN_BLOCK); }
	@Test
	public void awayBothDown() throws Exception { verifyFixture(BrowserMatchAdapter.Fixture.BOTH_DOWN_AWAY); }
	@Test
	public void awayBothDownWithBlock() throws Exception { verifyFixture(BrowserMatchAdapter.Fixture.BOTH_DOWN_AWAY_BLOCK); }

	private void verifyFixture(BrowserMatchAdapter.Fixture fixture) throws Exception {
		boolean blockSkill = fixture.name().endsWith("BLOCK");
		boolean awayChooses = fixture.name().contains("AWAY");
		TestServer server = new TestServer();
		BrowserMatchAdapter adapter = new BrowserMatchAdapter(server.getServer(), "home-token", "away-token", fixture);
		GameState state = state(adapter);
		Socket home = new Socket();
		Socket away = new Socket();
		join(adapter, home, "home");
		join(adapter, away, "away");
		JsonObject initial = home.snapshot();
		JsonObject prompt = initial.get("prompt").asObject();
		String owner = awayChooses ? "away" : "home";
		assertEquals(owner, prompt.getString("actor", null));
		assertEquals("home", initial.getString("turnOwner", null));
		assertEquals(0, prompt.getLong("revision", -1));
		assertEquals(awayChooses ? 2 : 1, prompt.get("options").asArray().size());
		assertEquals(initial.toString(), new JsonObject(away.snapshot()).set("actor", "home").toString());
		String request = choice("accept", 0, prompt.getString("id", null), "die-0");
		Socket chooser = awayChooses ? away : home;
		Socket observer = awayChooses ? home : away;
		String before = state.toJsonValue().toString();
		reject(adapter, observer, request, "WRONG_CHOICE_ACTOR", state);
		reject(adapter, chooser, choice("stale", 1, prompt.getString("id", null), "die-0"), "STALE_REVISION", state);
		reject(adapter, chooser, choice("id", 0, "forged", "die-0"), "CHOICE_MISMATCH", state);
		reject(adapter, chooser, choice("option", 0, prompt.getString("id", null), "die-9"), "INVALID_OPTION", state);
		reject(adapter, chooser, new JsonObject(JsonObject.readFrom(request)).set("requestId", "dice").add("dice", 2).toString(), "MALFORMED_MESSAGE", state);
		reject(adapter, chooser, new JsonObject(JsonObject.readFrom(request)).set("requestId", "scenario").add("scenario", "BOTH_DOWN").toString(), "MALFORMED_MESSAGE", state);
		reject(adapter, chooser, "{\"version\":1,\"type\":\"move\",\"requestId\":\"move\",\"expectedRevision\":0,\"playerId\":\"home-runner\",\"to\":{\"x\":6,\"y\":7}}", "CHOICE_PENDING", state);

		// Disconnect while a decision is pending. A fresh connection needs only its token.
		adapter.disconnect(chooser);
		reject(adapter, chooser, request, "AUTHENTICATION_REQUIRED", state);
		Socket resumed = new Socket();
		join(adapter, resumed, owner);
		assertEquals(new JsonObject(initial).set("actor", owner).toString(), resumed.snapshot().toString());
		assertEquals(before, state.toJsonValue().toString());
		int observerCount = observer.messages.size();
		adapter.receive(resumed, request);
		assertEquals("CHOICE_APPLIED", resumed.result().getString("code", null));
		assertEquals(1, resumed.snapshot().getLong("revision", -1));
		assertTrue(resumed.snapshot().get("prompt").isNull());
		assertEquals(observerCount + 1, observer.messages.size());
		assertEquals(new JsonObject(resumed.snapshot()).set("actor", observer.snapshot().get("actor")).toString(), observer.snapshot().toString());

		// Independent BlockTest-style engine oracle, with the same controlled rolls.
		GameState oracle = new GameStateBuilder(new TestServer().getGameState()).withRule("BB2025")
			.withTeam(true, team -> team.player("home1", p -> {
				p.at(7, 7).stats(6, awayChooses ? 2 : 3, 3, 5, 8);
				if (blockSkill) p.skill("Block");
			})).withTeam(false, team -> team.player("away1", p -> p.at(8, 7).stats(6, 3, 3, 5, 8))).build();
		oracle.getGame().getTurnDataHome().setReRolls(0);
		oracle.getGame().getTurnDataAway().setReRolls(0);
		TestRolls rolls = TestRolls.on(oracle).block("bothdown");
		if (awayChooses) rolls.block("bothdown");
		rolls.armor(2, 2);
		if (!blockSkill) rolls.armor(2, 2);
		StepEngine.start(oracle);
		StepEngine.respond(oracle, Commands.selectPlayer("home1", PlayerAction.BLOCK));
		StepEngine.respond(oracle, Commands.block("home1", "away1"));
		StepEngine.respond(oracle, Commands.blockChoice(0));
		assertEquals(base(oracle, "home1"), base(state, "home-runner"));
		assertEquals(base(oracle, "away1"), base(state, "away-runner"));
		assertEquals(blockSkill ? PlayerState.STANDING : PlayerState.PRONE, base(state, "home-runner"));
		assertEquals(PlayerState.PRONE, base(state, "away-runner"));
		assertEquals(oracle.getGame().isHomePlaying(), state.getGame().isHomePlaying());
		assertEquals(oracle.getGame().getTurnMode(), state.getGame().getTurnMode());
		assertEquals(oracle.getCurrentStep().getId(), state.getCurrentStep().getId());
		assertEquals(initial.get("resources"), resumed.snapshot().get("resources"));
		assertTrue(state.getDiceRoller().getTestRolls().values().stream().allMatch(List::isEmpty));

		// Pretend acknowledgement/snapshot were lost, reconnect and retry the exact request.
		String resolved = state.toJsonValue().toString();
		adapter.disconnect(resumed);
		Socket retry = new Socket();
		join(adapter, retry, owner);
		assertEquals(resumed.snapshot(), retry.snapshot());
		observerCount = observer.messages.size();
		int retryCount = retry.messages.size();
		adapter.receive(retry, request);
		assertEquals("CHOICE_APPLIED", retry.result().getString("code", null));
		assertEquals(1, retry.result().getLong("revision", -1));
		assertTrue(retry.result().getBoolean("duplicate", false));
		assertEquals(retryCount + 1, retry.messages.size());
		assertEquals(observerCount, observer.messages.size());
		assertEquals(resolved, state.toJsonValue().toString());
		assertTrue(state.getDiceRoller().getTestRolls().values().stream().allMatch(List::isEmpty));
		reject(adapter, retry, choice("accept", 1, prompt.getString("id", null), "die-0"), "REQUEST_ID_REUSED", state);
		reject(adapter, retry, choice("new", 1, prompt.getString("id", null), "die-0"), "NO_PENDING_CHOICE", state);
	}

	private void reject(BrowserMatchAdapter adapter, Socket socket, String request, String code, GameState state) {
		String before = state.toJsonValue().toString();
		int count = socket.messages.size();
		adapter.receive(socket, request);
		assertEquals(code, socket.result().getString("code", null));
		assertEquals(count + 1, socket.messages.size());
		assertEquals(before, state.toJsonValue().toString());
	}

	private int base(GameState state, String id) {
		return state.getGame().getFieldModel().getPlayerState(state.getGame().getPlayerById(id)).getBase();
	}

	private GameState state(BrowserMatchAdapter adapter) throws Exception {
		Field field = BrowserMatchAdapter.class.getDeclaredField("gameState");
		field.setAccessible(true);
		return (GameState) field.get(adapter);
	}

	private void join(BrowserMatchAdapter adapter, Socket socket, String actor) {
		adapter.receive(socket, new JsonObject().add("version", 1).add("type", "join").add("requestId", "join")
			.add("token", actor + "-token").toString());
	}

	private String choice(String id, long revision, String choiceId, String optionId) {
		return new JsonObject().add("version", 1).add("type", "choice").add("requestId", id)
			.add("expectedRevision", revision).add("choiceId", choiceId).add("optionId", optionId).toString();
	}

	private static class Socket implements BrowserMatchAdapter.Connection {
		private final List<JsonObject> messages = new ArrayList<>();
		public void send(String message) { messages.add(JsonObject.readFrom(message)); }
		JsonObject snapshot() { return last("snapshot"); }
		JsonObject result() { return last("result"); }
		JsonObject last(String type) {
			for (int index = messages.size() - 1; index >= 0; index--) {
				JsonObject message = messages.get(index);
				if (type.equals(message.getString("type", null))) return message;
			}
			throw new AssertionError(type);
		}
	}
}
