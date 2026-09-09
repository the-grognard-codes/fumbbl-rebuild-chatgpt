package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.local.BrowserMatchAdapter;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrowserTeamAdapterTest {
	@Test
	public void catalogAndDraftEvaluationUseExistingAuthorizationWithoutMutatingMatchOrHistory() throws Exception {
		BrowserMatchAdapter adapter = new BrowserMatchAdapter(new TestServer().getServer(), "home-token", "away-token");
		List<String> output = new ArrayList<>();
		BrowserMatchAdapter.Connection connection = output::add;
		adapter.receive(connection, "{\"version\":1,\"type\":\"catalog\",\"requestId\":\"c\"}");
		assertTrue(output.get(0).contains("AUTHENTICATION_REQUIRED"));
		adapter.receive(connection, "{\"version\":1,\"type\":\"join\",\"requestId\":\"j\",\"token\":\"home-token\"}");
		Field field = BrowserMatchAdapter.class.getDeclaredField("gameState"); field.setAccessible(true);
		GameState state = (GameState) field.get(adapter);
		String before = state.getGame().toJsonValue().toString();
		String metrics = adapter.measurements().toString();
		adapter.receive(connection, "{\"version\":1,\"type\":\"catalog\",\"requestId\":\"c\"}");
		assertTrue(output.get(output.size() - 1).contains(RosterCatalog.VERSION));
		JsonArray players = new JsonArray();
		for (int i = 1; i <= 11; i++) players.add(new JsonObject().add("id", "p" + i).add("slot", i).add("positionId", "lineman").add("skillIds", new JsonArray()));
		JsonObject draft = new JsonObject().add("catalogVersion", RosterCatalog.VERSION).add("ruleset", "BB2025")
			.add("rosterId", "human").add("presetId", RosterCatalog.PRESET).add("captainId", "p1").add("players", players)
			.add("resources", new JsonObject().add("rerolls", 2).add("assistantCoaches", 0).add("cheerleaders", 0).add("apothecary", 1).add("dedicatedFans", 0));
		JsonObject request = new JsonObject().add("version", 1).add("type", "validateTeam").add("requestId", "v").add("draft", draft);
		adapter.receive(connection, request.toString());
		assertEquals(700000, JsonObject.readFrom(output.get(output.size() - 1)).getInt("total", -1));
		String firstResult = output.get(output.size() - 1);
		adapter.receive(connection, request.toString()); assertEquals(firstResult, output.get(output.size() - 1));
		draft.add("total", 1); adapter.receive(connection, request.toString());
		assertTrue(output.get(output.size() - 1).contains("MALFORMED_TEAM_REQUEST"));
		draft.remove("total"); players.get(1).asObject().set("id", "p1"); adapter.receive(connection, request.toString());
		assertTrue(output.get(output.size() - 1).contains("DUPLICATE_PLAYER"));
		adapter.receive(connection, "[[[[[[[[[0]]]]]]]]]");
		assertTrue(output.get(output.size() - 1).contains("MALFORMED_MESSAGE"));
		assertEquals(before, state.getGame().toJsonValue().toString());
		assertEquals(metrics, adapter.measurements().toString());
	}
}
