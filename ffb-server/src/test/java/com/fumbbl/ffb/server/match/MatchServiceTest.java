package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.team.SavedTeamDocument;
import com.fumbbl.ffb.server.team.SavedTeamJson;
import com.fumbbl.ffb.server.team.SavedTeamRepository;
import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchServiceTest {
	private RosterCatalog catalog;
	private Teams sources;
	private Matches matches;
	private SavedTeamService teams;
	private MatchService service;
	private final MatchJson json = new MatchJson();
	private String homeTeam, awayTeam;

	@BeforeEach
	void setup() throws Exception {
		catalog = new RosterCatalog(); sources = new Teams(); matches = new Matches();
		teams = new SavedTeamService(sources, catalog); service = new MatchService(matches, teams, catalog);
		homeTeam = teams.create("home", draft(0)).document.teamId; awayTeam = teams.create("away", draft(0)).document.teamId;
	}

	@Test
	void bothIdentitiesCanCreateAndOnlyInvitedIdentityTakesAwayRole() {
		for (String owner : new String[] { "home", "away" }) {
			String other = "home".equals(owner) ? "away" : "home";
			JsonObject created = accepted(owner, create("home".equals(owner) ? homeTeam : awayTeam, other));
			assertEquals("home", created.getString("callerRole", null));
			String id = matchId(created);
			assertEquals("AUTHORIZATION", invoke(owner, join(id, "home".equals(owner) ? homeTeam : awayTeam)).getString("code", null));
			JsonObject joined = accepted(other, join(id, "home".equals(other) ? homeTeam : awayTeam));
			assertEquals("away", joined.getString("callerRole", null));
			assertEquals("AWAITING_SETUP", joined.get("document").asObject().getString("lifecycle", null));
			assertEquals(joined.get("document"), accepted(owner, load(id)).get("document"));
			assertEquals(2, matches.rows.get(id).documentVersion);
		}
	}

	@Test
	void sourceEditAndImportCannotChangeEitherFrozenRoster() throws Exception {
		String id = matchId(accepted("home", create(homeTeam, "away")));
		accepted("away", join(id, awayTeam)); String before = matches.rows.get(id).json;
		teams.update("home", homeTeam, 1, draft(2));
		SavedTeamDocument imported = teams.load("away", awayTeam).document;
		teams.importDocument("away", new SavedTeamJson(catalog).encode(imported).toString());
		assertEquals(before, matches.rows.get(id).json);
		assertEquals(1, accepted("home", load(id)).get("document").asObject().get("home").asObject().getInt("sourceDocumentVersion", 0));
	}

	@Test
	void exactRetriesSurviveNewServiceAndReturnCurrentRecordEvenAfterSourceChanges() throws Exception {
		JsonObject create = create(homeTeam, "away"); String id = matchId(accepted("home", create));
		JsonObject join = join(id, awayTeam); accepted("away", join);
		teams.update("home", homeTeam, 1, draft(1)); teams.update("away", awayTeam, 1, draft(1));
		service = new MatchService(matches, teams, catalog);
		JsonObject retry = accepted("home", create); assertTrue(retry.getBoolean("duplicate", false));
		assertEquals(2, retry.get("document").asObject().getInt("documentVersion", 0));
		assertTrue(accepted("away", join).getBoolean("duplicate", false));
		assertEquals(1, matches.rows.size()); assertEquals(2, matches.writes);
		create.set("expectedDocumentVersion", 2); assertEquals("REQUEST_ID_REUSED", invoke("home", create).getString("code", null));
		join.set("expectedDocumentVersion", 2); assertEquals("REQUEST_ID_REUSED", invoke("away", join).getString("code", null));
	}

	@Test
	void changedJsonPropertyOrderIsAnExactSemanticRetry() {
		JsonObject create = create(homeTeam, "away"); accepted("home", create);
		JsonObject reordered = new JsonObject(); List<String> keys = new ArrayList<>(create.names()); Collections.reverse(keys);
		for (String key : keys) reordered.add(key, create.get(key));
		assertTrue(accepted("home", reordered).getBoolean("duplicate", false)); assertEquals(1, matches.rows.size());
	}

	@Test
	void foreignTeamStaleVersionAndInvitationFailuresDoNotWrite() throws Exception {
		assertEquals("NOT_FOUND", invoke("home", create(awayTeam, "away")).getString("code", null));
		assertEquals("INVITATION_REQUIRED", invoke("home", create(homeTeam, "home")).getString("code", null));
		teams.update("home", homeTeam, 1, draft(1));
		assertEquals("STALE_TEAM_REVISION", invoke("home", create(homeTeam, "away")).getString("code", null));
		assertEquals(0, matches.rows.size());
		assertEquals("AUTHENTICATION_REQUIRED", invoke("intruder", create(homeTeam, "away")).getString("code", null));
	}

	@Test
	void invalidUnavailableRulesetAndPresetSelectionsCannotCreate() {
		SavedTeamRepository.Record original = sources.rows.get(homeTeam);
		for (String kind : new String[] { "invalid", "catalog", "ruleset", "preset" }) {
			JsonObject document = JsonObject.readFrom(original.json); JsonObject draft = document.get("draft").asObject();
			String version = original.catalogVersion;
			if ("invalid".equals(kind)) draft.get("players").asArray().remove(0);
			if ("catalog".equals(kind)) { version = "unavailable"; draft.set("catalogVersion", version); document.set("catalogVersion", version); }
			if ("ruleset".equals(kind)) { draft.set("ruleset", "BB2020"); document.set("ruleset", "BB2020"); }
			if ("preset".equals(kind)) draft.set("presetId", "unsupported");
			sources.rows.put(homeTeam, new SavedTeamRepository.Record(homeTeam, "home", 1, version, document.toString()));
			JsonObject rejected = invoke("home", create(homeTeam, "away")); assertNotEquals("ACCEPTED", rejected.getString("code", null));
			assertEquals(0, matches.rows.size()); assertEquals(document.toString(), sources.rows.get(homeTeam).json);
		}
	}

	@Test
	void strictRequestsRejectClaimsDuplicatesBadVersionsAndExcessiveNesting() {
		JsonObject create = create(homeTeam, "away");
		for (String claim : new String[] { "owner", "role", "statistics", "price", "valid", "catalog", "document" }) {
			JsonObject request = JsonObject.readFrom(create.toString()).add(claim, "forged");
			assertEquals("INVALID_REQUEST", invoke("home", request).getString("code", null));
		}
		assertEquals("INVALID_REQUEST", json.handle(service, "home", create.toString().replace("\"version\":1", "\"version\":1,\"version\":1")).getString("code", null));
		assertEquals("INVALID_REQUEST", json.handle(service, "home", "[[[[[[[[[{}]]]]]]]]]").getString("code", null));
		assertEquals("INVALID_REQUEST", invoke("home", create.set("expectedDocumentVersion", 1.5)).getString("code", null));
		assertEquals(0, matches.rows.size());
	}

	@Test
	void occupiedSeatWithNewRequestCannotAdvanceOrReplaceTeam() {
		String id = matchId(accepted("home", create(homeTeam, "away"))); accepted("away", join(id, awayTeam));
		String before = matches.rows.get(id).json;
		assertEquals("SEAT_OCCUPIED", invoke("away", join(id, awayTeam).set("expectedRevision", 2)).getString("code", null));
		assertEquals(before, matches.rows.get(id).json);
	}

	@Test
	void persistenceFailuresAreAtomicAndUnknownCommitCanReconcile() {
		matches.failure = true;
		assertEquals("PERSISTENCE_FAILED", invoke("home", create(homeTeam, "away")).getString("code", null));
		assertEquals(0, matches.rows.size()); matches.failure = false;
		JsonObject create = create(homeTeam, "away"); matches.unknown = true;
		JsonObject response = invoke("home", create);
		assertEquals("MATCH_OUTCOME_UNKNOWN", response.getString("code", null)); assertTrue(response.get("document").isNull());
		String id = response.getString("recoveryMatchId", null); matches.unknown = false;
		assertEquals(id, matchId(accepted("home", create)));
		JsonObject join = join(id, awayTeam); matches.unknown = true;
		assertEquals("MATCH_OUTCOME_UNKNOWN", invoke("away", join).getString("code", null)); matches.unknown = false;
		assertTrue(accepted("away", join).getBoolean("duplicate", false)); assertEquals(2, matches.rows.get(id).documentVersion);
	}

	@Test
	void unsupportedPersistedFactsRemainUnchangedAndPrivateClaimsNeverProject() {
		String id = matchId(accepted("home", create(homeTeam, "away")));
		JsonObject publicRecord = accepted("home", load(id)).get("document").asObject();
		assertEquals(null, publicRecord.get("requests")); assertEquals(null, publicRecord.get("homeOwner"));
		assertEquals(null, publicRecord.get("home").asObject().get("resolvedCatalog"));
		MatchRepository.Record original = matches.rows.get(id);
		for (String corruption : new String[] { "format", "missing", "parameter", "catalog" }) {
			JsonObject stored = JsonObject.readFrom(original.json);
			if ("format".equals(corruption)) stored.set("formatVersion", 2);
			if ("missing".equals(corruption)) stored.get("home").asObject().get("roster").asObject().remove("resources");
			if ("parameter".equals(corruption)) stored.get("home").asObject().get("roster").asObject().get("players").asArray().get(0).asObject().get("position").asObject().get("parameters").asObject().add("unknown", 8);
			if ("catalog".equals(corruption)) stored.get("home").asObject().set("catalogVersion", "newer");
			matches.rows.put(id, new MatchRepository.Record(id, 1, stored.toString()));
			assertEquals("SNAPSHOT_UNSUPPORTED", invoke("home", load(id)).getString("code", null));
			assertEquals(stored.toString(), matches.rows.get(id).json);
		}
	}

	@Test
	void corruptFrozenCostsSkillsAndCaptainCannotBeReinterpretedAsAccepted() {
		String id = matchId(accepted("home", create(homeTeam, "away")));
		MatchRepository.Record original = matches.rows.get(id);
		for (String corruption : new String[] { "total", "points", "position-limit", "category", "captain", "base-duplicate", "secondary", "elite" }) {
			JsonObject stored = JsonObject.readFrom(original.json), home = stored.get("home").asObject();
			JsonObject roster = home.get("roster").asObject(), validation = home.get("validation").asObject();
			com.eclipsesource.json.JsonArray players = roster.get("players").asArray();
			if ("total".equals(corruption)) validation.set("total", 1);
			if ("points".equals(corruption)) validation.set("skillPoints", 1);
			if ("position-limit".equals(corruption)) {
				home.get("resolvedCatalog").asObject().get("positions").asArray().get(0).asObject().set("maximum", 10);
				for (com.eclipsesource.json.JsonValue player : players) player.asObject().get("position").asObject().set("maximum", 10);
			}
			if ("category".equals(corruption)) players.get(1).asObject().get("skillIds").asArray().add("pass");
			if ("captain".equals(corruption)) home.get("resolvedCatalog").asObject().get("positions").asArray().get(0).asObject().set("canCaptain", false);
			if ("base-duplicate".equals(corruption)) players.get(0).asObject().get("skillIds").asArray().add("pro");
			if ("secondary".equals(corruption)) { for (int i = 1; i <= 3; i++) players.get(i).asObject().get("skillIds").asArray().add("dodge"); validation.set("skillPoints", 6); }
			if ("elite".equals(corruption)) { for (int i = 1; i <= 5; i++) players.get(i).asObject().get("skillIds").asArray().add("block"); validation.set("skillPoints", 5); }
			matches.rows.put(id, new MatchRepository.Record(id, 1, stored.toString()));
			assertEquals("SNAPSHOT_UNSUPPORTED", invoke("home", load(id)).getString("code", null), corruption);
			assertEquals(stored.toString(), matches.rows.get(id).json);
		}
	}

	@Test
	void retiredCatalogAndStaleJoinLeaveWaitingMatchUnchanged() throws Exception {
		String id = matchId(accepted("home", create(homeTeam, "away"))); String original = matches.rows.get(id).json;
		teams.update("away", awayTeam, 1, draft(1));
		assertEquals("STALE_TEAM_REVISION", invoke("away", join(id, awayTeam)).getString("code", null));
		assertEquals("STALE_REVISION", invoke("away", join(id, awayTeam).set("expectedRevision", 2)).getString("code", null));
		service = new MatchService(matches, new SavedTeamService(sources, catalog, "retired"), catalog);
		assertEquals("MIGRATION_REQUIRED", invoke("away", join(id, awayTeam).set("expectedDocumentVersion", 2)).getString("code", null));
		assertEquals(original, matches.rows.get(id).json);
	}

	@Test
	void sourceEditDuringReadCannotSubstituteDifferentRevision() throws Exception {
		SavedTeamRepository.Record acceptedVersion = sources.rows.get(homeTeam);
		teams.update("home", homeTeam, 1, draft(3)); SavedTeamRepository.Record later = sources.rows.get(homeTeam);
		sources.rows.put(homeTeam, acceptedVersion); sources.afterRead = () -> sources.rows.put(homeTeam, later);
		JsonObject created = accepted("home", create(homeTeam, "away"));
		assertEquals(1, created.get("document").asObject().get("home").asObject().getInt("sourceDocumentVersion", 0));
		assertEquals(0, created.get("document").asObject().get("home").asObject().get("roster").asObject().get("resources").asObject().getInt("rerolls", -1));
		assertEquals(2, sources.rows.get(homeTeam).documentVersion);
	}

	private JsonObject accepted(String owner, JsonObject request) { JsonObject response = invoke(owner, request); assertEquals("ACCEPTED", response.getString("code", null)); return response; }
	private JsonObject invoke(String owner, JsonObject request) { return json.handle(service, owner, request.toString()); }
	private String matchId(JsonObject response) { return response.get("document").asObject().getString("matchId", null); }
	private JsonObject create(String team, String invited) { return header("create").add("teamId", team).add("expectedDocumentVersion", 1).add("intendedOpponent", invited); }
	private JsonObject join(String id, String team) { return header("join").add("matchId", id).add("expectedRevision", 1).add("teamId", team).add("expectedDocumentVersion", 1); }
	private JsonObject load(String id) { return header("load").add("matchId", id); }
	private JsonObject header(String operation) { return new JsonObject().add("version", 1).add("type", "preparedMatch").add("requestId", UUID.randomUUID().toString()).add("operation", operation); }
	private TeamDraft draft(int rerolls) {
		List<TeamDraft.Player> players = new ArrayList<>();
		for (int slot = 1; slot <= 11; slot++) players.add(new TeamDraft.Player("p" + slot, slot, "lineman", Collections.emptyList()));
		Map<String, Integer> resources = new LinkedHashMap<>(); for (String key : catalog.getResources().keySet()) resources.put(key, 0); resources.put("rerolls", rerolls);
		return new TeamDraft(RosterCatalog.VERSION, "BB2025", "human", RosterCatalog.PRESET, "p1", players, resources);
	}
	private static final class Teams implements SavedTeamRepository {
		final Map<String, Record> rows = new LinkedHashMap<>(); Runnable afterRead;
		public Record find(String owner, String id) { Record r = rows.get(id); if (afterRead != null) { Runnable callback = afterRead; afterRead = null; callback.run(); } return r != null && owner.equals(r.owner) ? r : null; }
		public List<Record> list(String owner) { return new ArrayList<>(rows.values()); }
		public void insert(Record record) { rows.put(record.teamId, record); }
		public boolean replace(Record record, int expected) { if (rows.get(record.teamId).documentVersion != expected) return false; rows.put(record.teamId, record); return true; }
	}
	private static final class Matches implements MatchRepository {
		final Map<String, Record> rows = new LinkedHashMap<>(); boolean failure, unknown; int writes;
		public Record find(String id) { return rows.get(id); }
		public void insert(Record record) throws SQLException { write(record); }
		public boolean replace(Record record, int expected) throws SQLException { if (rows.get(record.matchId).documentVersion != expected) return false; write(record); return true; }
		private void write(Record record) throws SQLException { if (failure) throw new SQLException("injected"); rows.put(record.matchId, record); writes++; if (unknown) throw new OutcomeUnknown(record, new SQLException("lost acknowledgement")); }
	}
}
