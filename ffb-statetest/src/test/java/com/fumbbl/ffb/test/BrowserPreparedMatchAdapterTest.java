package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.local.BrowserMatchAdapter;
import com.fumbbl.ffb.server.match.MatchRepository;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.team.SavedTeamRepository;
import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserPreparedMatchAdapterTest {
	@Test
	void authenticatedSubjectCreatesItsOwnMatchRoleWithoutChangingFixtureOrHistory() throws Exception {
		RosterCatalog catalog = new RosterCatalog();
		SavedTeamService teams = new SavedTeamService(new Teams(), catalog);
		String id = teams.create("away", draft(catalog)).document.teamId;
		BrowserMatchAdapter adapter = new BrowserMatchAdapter(new TestServer().getServer(), "home-token", "away-token");
		adapter.setPreparedMatches(new MatchService(new Matches(), teams, catalog));
		List<String> output = new ArrayList<>(); BrowserMatchAdapter.Connection connection = output::add;
		JsonObject request = new JsonObject().add("version", 1).add("type", "preparedMatch").add("operation", "create")
			.add("requestId", "create-one").add("teamId", id).add("expectedDocumentVersion", 1).add("intendedOpponent", "home");
		adapter.receive(connection, request.toString());
		assertTrue(output.get(0).contains("AUTHENTICATION_REQUIRED"));
		adapter.receive(connection, "{\"version\":1,\"type\":\"join\",\"requestId\":\"auth\",\"token\":\"away-token\"}");
		Field field = BrowserMatchAdapter.class.getDeclaredField("gameState"); field.setAccessible(true);
		GameState state = (GameState) field.get(adapter);
		String before = state.getGame().toJsonValue().toString(), metrics = adapter.measurements().toString();
		adapter.receive(connection, request.toString());
		JsonObject response = JsonObject.readFrom(output.get(output.size() - 1));
		assertEquals("ACCEPTED", response.getString("code", null));
		assertEquals("home", response.getString("callerRole", null));
		assertEquals("WAITING_FOR_OPPONENT", response.get("document").asObject().getString("lifecycle", null));
		adapter.receive(connection, request.toString());
		assertTrue(JsonObject.readFrom(output.get(output.size() - 1)).getBoolean("duplicate", false));
		request.add("role", "away"); adapter.receive(connection, request.toString());
		assertTrue(!"ACCEPTED".equals(JsonObject.readFrom(output.get(output.size() - 1)).getString("code", null)));
		assertEquals(before, state.getGame().toJsonValue().toString()); assertEquals(metrics, adapter.measurements().toString());
	}
	private TeamDraft draft(RosterCatalog catalog) {
		List<TeamDraft.Player> players = new ArrayList<>();
		for (int slot = 1; slot <= 11; slot++) players.add(new TeamDraft.Player("p" + slot, slot, "lineman", Collections.emptyList()));
		Map<String, Integer> resources = new LinkedHashMap<>();
		for (String resource : catalog.getResources().keySet()) resources.put(resource, 0);
		return new TeamDraft(RosterCatalog.VERSION, "BB2025", "human", RosterCatalog.PRESET, null, players, resources);
	}
	private static final class Teams implements SavedTeamRepository {
		private final Map<String, Record> rows = new LinkedHashMap<>();
		public Record find(String owner, String id) { Record r = rows.get(id); return r != null && owner.equals(r.owner) ? r : null; }
		public List<Record> list(String owner) { return new ArrayList<>(rows.values()); }
		public void insert(Record record) { rows.put(record.teamId, record); }
		public boolean replace(Record record, int expected) { rows.put(record.teamId, record); return true; }
	}
	private static final class Matches implements MatchRepository {
		private final Map<String, Record> rows = new LinkedHashMap<>();
		public Record find(String id) { return rows.get(id); }
		public void insert(Record record) { rows.put(record.matchId, record); }
		public boolean replace(Record record, int expected) { rows.put(record.matchId, record); return true; }
	}
}
