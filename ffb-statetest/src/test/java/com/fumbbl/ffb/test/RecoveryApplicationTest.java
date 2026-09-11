package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.match.MatchRepository;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.match.RecoveryRepository;
import com.fumbbl.ffb.server.match.SetupApplication;
import com.fumbbl.ffb.server.team.SavedTeamRepository;
import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryApplicationTest {
	@Test
	void recreatedApplicationRestoresAcceptedActivationAndRequestRetry() throws Exception {
		Fixture fixture = fixture();
		SetupApplication first = app(fixture);
		assertEquals("ACCEPTED", first.activate("home", activate(fixture.id, "activate").toString()).getString("code", null));
		assertNotNull(fixture.recovery.rows.get(fixture.id));
		JsonObject view = accepted(first.handle("home", load(fixture.id, "load"))).get("state").asObject();
		JsonObject choice = choice(fixture.id, "choose", view, "heads");
		assertEquals("ACCEPTED", first.handle(actor(view), choice).getString("code", null));

		SetupApplication restarted = app(fixture);
		assertTrue(restarted.activate("home", activate(fixture.id, "activate").toString()).getBoolean("duplicate", false));
		assertTrue(restarted.handle(actor(view), choice).getBoolean("duplicate", false));
		assertEquals("ACCEPTED", restarted.handle("home", load(fixture.id, "after")).getString("code", null));
	}

	@Test
	void unauthorizedCallsDoNotReadOrWriteRecoveryArtifacts() throws Exception {
		Fixture fixture = fixture();
		SetupApplication application = app(fixture);
		assertEquals("AUTHENTICATION_REQUIRED", application.activate("intruder", activate(fixture.id, "activate").toString()).getString("code", null));
		assertEquals("AUTHENTICATION_REQUIRED", application.handle("intruder", load(fixture.id, "load")).getString("code", null));
		assertEquals(0, fixture.recovery.reads);
		assertEquals(0, fixture.recovery.writes);
	}

	@Test
	void activatedMatchWithoutRecoveryArtifactFailsClosedWithoutReinitializing() throws Exception {
		Fixture fixture = fixture();
		fixture.matches.activate("home", "activate", fixture.id, 2);
		JsonObject response = app(fixture).handle("home", load(fixture.id, "load"));
		assertEquals("SESSION_UNAVAILABLE", response.getString("code", null));
		assertTrue(fixture.recovery.rows.isEmpty());
	}

	@Test
	void corruptRecoveryArtifactIsRejected() throws Exception {
		Fixture fixture = fixture();
		fixture.matches.activate("home", "activate", fixture.id, 2);
		fixture.recovery.rows.put(fixture.id, new RecoveryRepository.Record(fixture.id, 1, "{}"));
		assertEquals("RECOVERY_CORRUPT", app(fixture).handle("home", load(fixture.id, "load")).getString("code", null));
	}

	@Test
	void stagingFailureLeavesActivationRetryable() throws Exception {
		Fixture fixture = fixture();
		fixture.recovery.failure = Failure.BEFORE_WRITE;
		SetupApplication application = app(fixture);
		assertEquals("PERSISTENCE_FAILED", application.activate("home", activate(fixture.id, "activate").toString()).getString("code", null));
		assertEquals("AWAITING_SETUP", fixture.matches.load("home", fixture.id).document.lifecycle.name());
		assertEquals("ACCEPTED", application.activate("home", activate(fixture.id, "activate").toString()).getString("code", null));
		assertNotNull(fixture.recovery.rows.get(fixture.id));
	}

	@Test
	void ambiguousCheckpointEvictsAndRetryReconcilesCommittedOrLostWrite() throws Exception {
		for (Failure failure : new Failure[] { Failure.COMMITTED_UNKNOWN, Failure.BEFORE_WRITE }) {
			Fixture fixture = fixture();
			SetupApplication application = app(fixture);
			assertEquals("ACCEPTED", application.activate("home", activate(fixture.id, "activate").toString()).getString("code", null));
			JsonObject view = accepted(application.handle("home", load(fixture.id, "load"))).get("state").asObject();
			JsonObject choice = choice(fixture.id, "choose", view, "heads");
			fixture.recovery.failure = failure;
			assertEquals(failure == Failure.COMMITTED_UNKNOWN ? "MATCH_OUTCOME_UNKNOWN" : "PERSISTENCE_FAILED",
				application.handle(actor(view), choice).getString("code", null));
			RecoveryRepository.Record durable = fixture.recovery.rows.get(fixture.id);
			assertEquals(failure == Failure.COMMITTED_UNKNOWN ? 2 : 1, durable.generation);
			JsonObject retry = application.handle(actor(view), choice);
			assertEquals("ACCEPTED", retry.getString("code", null));
			assertEquals(failure == Failure.COMMITTED_UNKNOWN, retry.getBoolean("duplicate", false));
			assertEquals(2, fixture.recovery.rows.get(fixture.id).generation);
		}
	}

	private SetupApplication app(Fixture fixture) throws Exception { return new SetupApplication(new TestServer().getServer(), fixture.matches, fixture.recovery); }
	private JsonObject accepted(JsonObject response) { assertEquals("ACCEPTED", response.getString("code", null)); return response; }
	private JsonObject activate(String id, String requestId) {
		return new JsonObject().add("version", 1).add("type", "preparedMatch").add("operation", "activate")
			.add("requestId", requestId).add("matchId", id).add("expectedRevision", 2);
	}
	private JsonObject load(String id, String requestId) {
		return new JsonObject().add("version", 1).add("type", "setup").add("operation", "load").add("requestId", requestId).add("matchId", id);
	}
	private JsonObject choice(String id, String requestId, JsonObject view, String option) {
		return new JsonObject().add("version", 1).add("type", "setup").add("operation", "choice").add("requestId", requestId)
			.add("matchId", id).add("expectedRevision", view.get("revision")).add("promptId", view.get("prompt").asObject().get("id")).add("optionId", option);
	}
	private String actor(JsonObject view) { return view.getString("actor", null); }

	private Fixture fixture() throws Exception {
		RosterCatalog catalog = new RosterCatalog();
		Teams records = new Teams(); SavedTeamService teams = new SavedTeamService(records, catalog);
		Matches repository = new Matches();
		MatchService matches = new MatchService(repository, teams, catalog);
		String home = teams.create("home", draft(catalog)).document.teamId;
		String away = teams.create("away", draft(catalog)).document.teamId;
		String id = matches.create("home", "create", home, 1, "away").document.matchId;
		matches.join("away", "join", id, 1, away, 1);
		return new Fixture(matches, id, new Recovery());
	}

	private TeamDraft draft(RosterCatalog catalog) {
		List<TeamDraft.Player> players = new ArrayList<>();
		for (int slot = 1; slot <= 11; slot++) players.add(new TeamDraft.Player("p" + slot, slot, "lineman", Collections.emptyList()));
		Map<String, Integer> resources = new LinkedHashMap<>();
		for (String resource : catalog.getResources().keySet()) resources.put(resource, 0);
		return new TeamDraft(RosterCatalog.VERSION, "BB2025", "human", RosterCatalog.PRESET, null, players, resources);
	}

	private static final class Fixture {
		private final MatchService matches;
		private final String id;
		private final Recovery recovery;
		private Fixture(MatchService matches, String id, Recovery recovery) { this.matches = matches; this.id = id; this.recovery = recovery; }
	}

	private enum Failure { BEFORE_WRITE, COMMITTED_UNKNOWN }
	private static final class Recovery implements RecoveryRepository {
		private final Map<String, Record> rows = new LinkedHashMap<>();
		private int reads, writes;
		private Failure failure;
		public Record find(String matchId) { reads++; return rows.get(matchId); }
		public boolean save(Record record, long expected) throws SQLException {
			writes++;
			Failure next = failure; failure = null;
			if (next == Failure.BEFORE_WRITE) throw new SQLException("injected before write");
			Record prior = rows.get(record.matchId);
			if ((expected == 0 && prior != null) || (expected != 0 && (prior == null || prior.generation != expected))) return false;
			Record saved = new Record(record.matchId, expected + 1, record.json); rows.put(record.matchId, saved);
			if (next == Failure.COMMITTED_UNKNOWN) throw new OutcomeUnknown(saved, new SQLException("lost acknowledgement"));
			return true;
		}
	}

	private static final class Teams implements SavedTeamRepository {
		private final Map<String, Record> rows = new LinkedHashMap<>();
		public Record find(String owner, String id) { Record row = rows.get(id); return row != null && owner.equals(row.owner) ? row : null; }
		public List<Record> list(String owner) { return new ArrayList<>(rows.values()); }
		public void insert(Record record) { rows.put(record.teamId, record); }
		public boolean replace(Record record, int expected) { rows.put(record.teamId, record); return true; }
	}

	private static final class Matches implements MatchRepository {
		private final Map<String, Record> rows = new LinkedHashMap<>();
		public Record find(String id) { return rows.get(id); }
		public void insert(Record record) { rows.put(record.matchId, record); }
		public boolean replace(Record record, int expected) {
			Record prior = rows.get(record.matchId); if (prior == null || prior.documentVersion != expected) return false;
			rows.put(record.matchId, record); return true;
		}
	}
}
