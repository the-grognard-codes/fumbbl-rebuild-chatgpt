package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.local.BrowserMatchAdapter;
import com.fumbbl.ffb.server.local.BrowserMatchDelivery;
import com.fumbbl.ffb.server.local.BrowserMatchTransportMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrowserMatchAdapterTest {
	private BrowserMatchAdapter adapter;
	private Socket home;
	private Socket away;

	@BeforeEach
	public void setUp() throws Exception {
		TestServer server = new TestServer();
		adapter = new BrowserMatchAdapter(server.getServer(), "home-token", "away-token");
		home = new Socket();
		away = new Socket();
		join(home, "home-token", "home");
		join(away, "away-token", "away");
	}

	@Test
	public void acceptedMoveUsesEngineConsumesMovementAndBroadcastsOnce() throws Exception {
		int homeMessages = home.messages.size();
		int awayMessages = away.messages.size();
		adapter.receive(home, move("m1", 0, "home-runner", 6, 7));
		assertEquals("accepted", home.lastResult().getString("status", null));
		assertEquals(1, home.lastResult().getLong("revision", -1));
		assertEquals(homeMessages + 2, home.messages.size());
		assertEquals(awayMessages + 1, away.messages.size());
		assertEquals(1, player(home.lastSnapshot(), "home-runner").getInt("movementUsed", -1));
	}

	@Test
	public void rolePlayerNonAdjacentOffboardAndStaleRejectionsDoNotMutateGame() throws Exception {
		rejectUnchanged(away, move("role", 0, "away-runner", 19, 7), "WRONG_TURN");
		rejectUnchanged(home, move("player", 0, "away-runner", 19, 7), "WRONG_PLAYER");
		rejectUnchanged(home, move("far", 0, "home-runner", 7, 7), "NOT_ADJACENT");
		rejectUnchanged(home, move("off", 0, "home-runner", -1, 7), "OFF_BOARD");
		rejectUnchanged(home, move("stale", 9, "home-runner", 6, 7), "STALE_REVISION");
	}

	@Test
	public void occupiedAdjacentAndExhaustionDoNotMutateGame() throws Exception {
		state().getGame().getFieldModel().setPlayerCoordinate(state().getGame().getPlayerById("away-runner"), new FieldCoordinate(6, 7));
		rejectUnchanged(home, move("occupied", 0, "home-runner", 6, 7), "OCCUPIED");
		state().getGame().getFieldModel().setPlayerCoordinate(state().getGame().getPlayerById("away-runner"), new FieldCoordinate(20, 7));
		for (int index = 0; index < 6; index++) {
			adapter.receive(home, move("m" + index, index, "home-runner", 6 + index, 7));
		}
		assertEquals(6, player(home.lastSnapshot(), "home-runner").getInt("movementUsed", -1));
		rejectUnchanged(home, move("exhaust", 6, "home-runner", 12, 6), "MOVEMENT_EXHAUSTED");
	}

	@Test
	public void acceptedRetryIsIdempotentAndChangedPayloadRejected() throws Exception {
		String first = move("m1", 0, "home-runner", 6, 7);
		adapter.receive(home, first);
		String state = stateJson();
		int homeMessages = home.messages.size();
		int awayMessages = away.messages.size();
		adapter.receive(home, first);
		assertTrue(home.lastResult().getBoolean("duplicate", false));
		assertEquals(state, stateJson());
		assertEquals(homeMessages + 1, home.messages.size());
		assertEquals(awayMessages, away.messages.size());
		rejectUnchanged(home, move("m1", 1, "home-runner", 5, 6), "REQUEST_ID_REUSED");
	}

	@Test
	public void malformedUnknownDiceAndRebindAreRejected() throws Exception {
		rejectUnchanged(home, "{\"version\":\"one\",\"type\":\"move\",\"requestId\":\"bad\"}", "MALFORMED_MESSAGE");
		rejectUnchanged(home, "{\"version\":2,\"type\":\"move\",\"requestId\":\"bad\"}", "UNSUPPORTED_VERSION");
		rejectUnchanged(home, "{\"version\":1,\"type\":\"move\",\"requestId\":7}", "INVALID_REQUEST_ID");
		rejectUnchanged(home, "{\"version\":1,\"type\":\"move\",\"requestId\":\"to\",\"expectedRevision\":0,\"playerId\":\"home-runner\",\"to\":[]}", "MALFORMED_MESSAGE");
		rejectUnchanged(home, "{\"version\":1,\"type\":\"block\",\"requestId\":\"unknown\"}", "UNSUPPORTED_MESSAGE");
		rejectUnchanged(home, "{\"version\":1,\"type\":\"move\",\"requestId\":\"dice\",\"expectedRevision\":0,\"playerId\":\"home-runner\",\"to\":{\"x\":6,\"y\":7},\"dice\":[6]}", "MALFORMED_MESSAGE");
		String before = stateJson();
		adapter.receive(home, "{\"version\":1,\"type\":\"join\",\"requestId\":\"rebind\",\"token\":\"away-token\"}");
		assertEquals("IDENTITY_REBINDING_FORBIDDEN", home.lastResult().getString("code", null));
		assertEquals(before, stateJson());
	}

	@Test
	public void unauthenticatedAndInvalidTokenMessagesDoNotMutateState() throws Exception {
		Socket stranger = new Socket();
		rejectUnchanged(stranger, move("unauthenticated", 0, "home-runner", 6, 7), "AUTHENTICATION_REQUIRED");
		String before = stateJson();
		adapter.receive(stranger, "{\"version\":1,\"type\":\"join\",\"requestId\":\"invalid\",\"token\":\"wrong\"}");
		assertEquals("AUTHENTICATION_FAILED", stranger.lastResult().getString("code", null));
		assertEquals(before, stateJson());
	}

	@Test
	public void propertyOrderDoesNotChangeDuplicateIdentity() throws Exception {
		String first = move("semantic", 0, "home-runner", 6, 7);
		adapter.receive(home, first);
		String before = stateJson();
		adapter.receive(home, "{\"type\":\"move\",\"to\":{\"y\":7,\"x\":6},\"playerId\":\"home-runner\",\"expectedRevision\":0,\"requestId\":\"semantic\",\"version\":1}");
		assertTrue(home.lastResult().getBoolean("duplicate", false));
		assertEquals(before, stateJson());
	}

	@Test
	public void historyCapRetainsAcceptedReplayAndRejectsNewRequest() throws Exception {
		String accepted = move("accepted", 0, "home-runner", 6, 7);
		adapter.receive(home, accepted);
		for (int index = 0; index < 255; index++) {
			adapter.receive(home, move("reject" + index, 1, "home-runner", 9, 7));
		}
		String before = stateJson();
		adapter.receive(home, accepted);
		assertTrue(home.lastResult().getBoolean("duplicate", false));
		assertEquals(before, stateJson());
		adapter.receive(home, move("over-cap", 1, "home-runner", 7, 7));
		assertEquals("REQUEST_HISTORY_LIMIT", home.lastResult().getString("code", null));
	}

	@Test
	public void fullHistorySurvivesRejoinAndRetainsHistoricalRevision() throws Exception {
		String first = move("retained", 0, "home-runner", 6, 7);
		adapter.receive(home, first);
		adapter.receive(home, move("second", 1, "home-runner", 7, 7));
		for (int index = 0; index < 254; index++) {
			adapter.receive(home, move("limit" + index, 2, "home-runner", 20, 7));
		}
		adapter.disconnect(home);
		home = new Socket();
		join(home, "home-token", "rejoin");
		assertEquals(2, home.lastSnapshot().getLong("revision", -1));
		String before = stateJson();
		int broadcasts = away.messages.size();
		adapter.receive(home, first);
		assertTrue(home.lastResult().getBoolean("duplicate", false));
		assertEquals(1, home.lastResult().getLong("revision", -1));
		rejectUnchanged(home, move("retained", 2, "home-runner", 8, 7), "REQUEST_ID_REUSED");
		rejectUnchanged(home, move("new", 2, "home-runner", 8, 7), "REQUEST_HISTORY_LIMIT");
		assertEquals(before, stateJson());
		assertEquals(broadcasts, away.messages.size());
		assertEquals(256, adapter.measurements().getInt("historyEntries", -1));
	}

	@Test
	public void fixtureRetirementClearsAuthorizationAndHistoryWithNewIdentity() throws Exception {
		String first = move("retained", 0, "home-runner", 6, 7);
		adapter.receive(home, first);
		String oldMatch = home.lastSnapshot().getString("matchId", null);
		adapter.resetFixture(BrowserMatchAdapter.Fixture.MOVEMENT);
		rejectUnchanged(home, first, "AUTHENTICATION_REQUIRED");
		join(home, "home-token", "new-join");
		assertTrue(!oldMatch.equals(home.lastSnapshot().getString("matchId", null)));
		assertEquals(0, adapter.measurements().getInt("historyEntries", -1));
		adapter.receive(home, first);
		assertEquals("MOVED", home.lastResult().getString("code", null));
		assertTrue(!home.lastResult().getBoolean("duplicate", true));
		join(away, "away-token", "new-away");
		rejectUnchanged(away, first, "STALE_REVISION");
	}

	@Test
	public void publicNegativeWireFixturesLeaveFullEngineStateUnchanged() throws Exception {
		String fixtures = new String(Files.readAllBytes(Paths.get("../browser-client/test/fixtures/rejections-v1.json")), StandardCharsets.UTF_8);
		for (JsonValue value : JsonValue.readFrom(fixtures).asArray()) {
			JsonObject fixture = value.asObject();
			rejectUnchanged(home, fixture.get("request").toString(), fixture.getString("code", null));
		}
	}

	@Test
	public void blockedDeliveryStillAllowsEngineMutationHealthySnapshotAndExactRetryAfterRejoin() throws Exception {
		BrowserMatchTransportMetrics metrics = new BrowserMatchTransportMetrics();
		boolean[] open = {true};
		BrowserMatchDelivery delivery = new BrowserMatchDelivery(new BrowserMatchDelivery.Sink() {
			public boolean isOpen() { return open[0]; }
			public void close(int status, String reason) { open[0] = false; }
			public void send(String text, BrowserMatchDelivery.Completion completion) { /* Deliberately withhold completion. */ }
		}, metrics, 2, 4096);
		BrowserMatchAdapter.Connection slow = text -> delivery.send(text);
		adapter.receive(slow, "{\"version\":1,\"type\":\"join\",\"requestId\":\"slow-join\",\"token\":\"home-token\"}");
		String request = move("slow-accepted", 0, "home-runner", 6, 7);
		adapter.receive(slow, request);
		assertTrue(!open[0]);
		assertEquals(1, away.lastSnapshot().getLong("revision", -1));
		assertEquals(6, player(away.lastSnapshot(), "home-runner").getInt("x", -1));
		assertEquals(0, metrics.toJson().getLong("deliveryQueueBytes", -1));
		adapter.disconnect(slow);
		Socket recovered = new Socket();
		join(recovered, "home-token", "slow-rejoin");
		assertEquals(1, recovered.lastSnapshot().getLong("revision", -1));
		String before = stateJson();
		int observerMessages = away.messages.size();
		adapter.receive(recovered, request);
		assertTrue(recovered.lastResult().getBoolean("duplicate", false));
		assertEquals(before, stateJson());
		assertEquals(observerMessages, away.messages.size());
	}

	private void join(Socket socket, String token, String id) {
		adapter.receive(socket, "{\"version\":1,\"type\":\"join\",\"requestId\":\"" + id + "\",\"token\":\"" + token + "\"}");
	}

	private void rejectUnchanged(Socket socket, String payload, String code) throws Exception {
		String before = stateJson();
		adapter.receive(socket, payload);
		assertEquals(code, socket.lastResult().getString("code", null));
		assertEquals(before, stateJson());
	}

	private GameState state() throws Exception {
		Field field = BrowserMatchAdapter.class.getDeclaredField("gameState");
		field.setAccessible(true);
		return (GameState) field.get(adapter);
	}

	private String stateJson() throws Exception {
		return state().toJsonValue().toString();
	}

	private String move(String id, long revision, String player, int x, int y) {
		return new JsonObject().add("version", 1).add("type", "move").add("requestId", id)
			.add("expectedRevision", revision).add("playerId", player)
			.add("to", new JsonObject().add("x", x).add("y", y)).toString();
	}

	private JsonObject player(JsonObject snapshot, String id) {
		for (JsonValue value : snapshot.get("players").asArray()) {
			if (id.equals(value.asObject().getString("id", null))) return value.asObject();
		}
		throw new AssertionError(id);
	}

	private static class Socket implements BrowserMatchAdapter.Connection {
		private final List<JsonObject> messages = new ArrayList<>();

		public void send(String message) {
			messages.add(JsonObject.readFrom(message));
		}

		private JsonObject lastResult() {
			return last("result");
		}

		private JsonObject lastSnapshot() {
			return last("snapshot");
		}

		private JsonObject last(String type) {
			for (int index = messages.size() - 1; index >= 0; index--) {
				if (type.equals(messages.get(index).getString("type", null))) return messages.get(index);
			}
			throw new AssertionError(type);
		}
	}
}
