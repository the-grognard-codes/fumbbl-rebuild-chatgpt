package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.local.BrowserMatchAdapter;
import com.fumbbl.ffb.server.match.MatchRepository;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.match.SetupApplication;
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
	void activationUsesPersistedReversedRolesAndNeverReinitializesAfterRetryOrRestart() throws Exception {
		RosterCatalog catalog = new RosterCatalog();
		SavedTeamService teams = new SavedTeamService(new Teams(), catalog);
		Matches repository = new Matches();
		MatchService matches = new MatchService(repository, teams, catalog);
		String home = teams.create("away", draft(catalog)).document.teamId;
		String away = teams.create("home", draft(catalog)).document.teamId;
		String id = matches.create("away", "create", home, 1, "home").document.matchId;
		matches.join("home", "join", id, 1, away, 1);
		SetupApplication app = new SetupApplication(new TestServer().getServer(), matches);
		JsonObject activation = new JsonObject().add("version", 1).add("type", "preparedMatch").add("operation", "activate")
			.add("requestId", "activate").add("matchId", id).add("expectedRevision", 2);
		assertEquals("ACCEPTED", app.activate("away", activation.toString()).getString("code", null));
		JsonObject load = new JsonObject().add("version", 1).add("type", "setup").add("operation", "load")
			.add("requestId", "load").add("matchId", id);
		JsonObject view = app.handle("home", load).get("state").asObject();
		assertEquals("away", view.getString("callerRole", null));
		assertEquals("home", app.handle("away", load).get("state").asObject().getString("callerRole", null));
		String before = view.toString();
		assertTrue(app.activate("away", activation.toString()).getBoolean("duplicate", false));
		assertEquals(before, app.handle("home", load).get("state").toString());
		JsonObject choice = new JsonObject().add("version", 1).add("type", "setup").add("operation", "choice")
			.add("requestId", "choose").add("matchId", id).add("expectedRevision", 0)
			.add("promptId", view.get("prompt").asObject().get("id")).add("optionId", "heads");
		assertEquals("WRONG_ACTOR", app.handle("away", choice).getString("code", null));
		assertEquals("ACCEPTED", app.handle("home", choice).getString("code", null));
		String after = app.handle("home", load).get("state").toString();
		assertTrue(app.handle("home", choice).getBoolean("duplicate", false));
		assertEquals(after, app.handle("home", load).get("state").toString());
		choice.set("optionId", "tails");
		assertEquals("REQUEST_ID_REUSED", app.handle("home", choice).getString("code", null));
		load.add("role", "away");
		assertEquals("INVALID_REQUEST", app.handle("home", load).getString("code", null));
		load.remove("role");
		SetupApplication restarted = new SetupApplication(new TestServer().getServer(), matches);
		assertTrue(restarted.activate("away", activation.toString()).getBoolean("duplicate", false));
		assertEquals("SESSION_UNAVAILABLE", restarted.handle("away", load).getString("code", null));
		// Every read/action checks persisted membership, even while the engine remains resident.
		repository.rows.remove(id);
		assertEquals("NOT_FOUND", app.handle("home", load).getString("code", null));
		assertEquals("NOT_FOUND", app.handle("home", choice).getString("code", null));
	}
	@Test
	void lostActivationCommitAcknowledgementInitializesOnceOnlyOnExactInProcessReconciliation() throws Exception {
		RosterCatalog catalog = new RosterCatalog();
		SavedTeamService teams = new SavedTeamService(new Teams(), catalog);
		Matches repository = new Matches();
		MatchRepository ambiguous = new MatchRepository() {
			public Record find(String id) { return repository.find(id); }
			public void insert(Record record) { repository.insert(record); }
			public boolean replace(Record record, int expected) throws java.sql.SQLException {
				repository.replace(record, expected);
				if (record.documentVersion == 3) throw new MatchRepository.OutcomeUnknown(record, new java.sql.SQLException("lost acknowledgement"));
				return true;
			}
		};
		MatchService service = new MatchService(ambiguous, teams, catalog);
		String h = teams.create("home", draft(catalog)).document.teamId, a = teams.create("away", draft(catalog)).document.teamId;
		String id = service.create("home", "create", h, 1, "away").document.matchId;
		service.join("away", "join", id, 1, a, 1);
		SetupApplication app = new SetupApplication(new TestServer().getServer(), service);
		JsonObject activate = new JsonObject().add("version", 1).add("type", "preparedMatch").add("operation", "activate")
			.add("requestId", "activate").add("matchId", id).add("expectedRevision", 2);
		assertEquals("MATCH_OUTCOME_UNKNOWN", app.activate("home", activate.toString()).getString("code", null));
		JsonObject malformedRetry = JsonObject.readFrom(activate.toString()).set("version", 2);
		assertEquals("UNSUPPORTED_VERSION", app.activate("home", malformedRetry.toString()).getString("code", null));
		malformedRetry.set("version", 1).set("type", "setup");
		assertEquals("UNSUPPORTED_VERSION", app.activate("home", malformedRetry.toString()).getString("code", null));
		malformedRetry.set("type", "preparedMatch").set("operation", "load");
		assertEquals("INVALID_REQUEST", app.activate("home", malformedRetry.toString()).getString("code", null));
		assertTrue(app.activate("home", activate.toString()).getBoolean("duplicate", false));
		JsonObject load = new JsonObject().add("version", 1).add("type", "setup").add("operation", "load")
			.add("requestId", "load").add("matchId", id);
		assertEquals("ACCEPTED", app.handle("home", load).getString("code", null));
		String before = app.handle("home", load).toString();
		assertTrue(app.activate("home", activate.toString()).getBoolean("duplicate", false));
		assertEquals(before, app.handle("home", load).toString());
		assertEquals("SESSION_UNAVAILABLE", new SetupApplication(new TestServer().getServer(), service).handle("home", load).getString("code", null));
	}
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
