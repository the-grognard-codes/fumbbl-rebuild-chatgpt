package com.fumbbl.ffb.server.match;

import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamValidation;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Durable preparation only. No engine session, setup step or game is initialized here. */
public final class MatchService {
	private final MatchRepository matches;
	private final SavedTeamService teams;
	private final RosterCatalog catalog;
	private final TeamValidation validation;
	private final MatchJson json = new MatchJson();

	public MatchService(MatchRepository matches, SavedTeamService teams, RosterCatalog catalog) {
		this.matches = matches; this.teams = teams; this.catalog = catalog; validation = new TeamValidation(catalog);
	}

	public Result create(String owner, String requestId, String teamId, int expectedTeamVersion, String intendedOpponent) throws SQLException {
		identity(owner); selection(teamId, expectedTeamVersion); requestId(requestId);
		if (!opponent(owner, intendedOpponent)) throw new Failure("INVITATION_REQUIRED");
		String fingerprint = "create|" + teamId + "|" + expectedTeamVersion + "|" + intendedOpponent;
		String id = UUID.nameUUIDFromBytes(("prepared-match-v1\n" + owner + "\n" + requestId).getBytes(StandardCharsets.UTF_8)).toString();
		MatchRepository.Record existing = matches.find(id);
		if (existing != null) return repeated(required(owner, id), owner, requestId, fingerprint);
		FrozenTeam frozen = freeze(owner, teamId, expectedTeamVersion);
		Map<String, MatchDocument.Request> requests = new LinkedHashMap<>();
		requests.put(owner + "\n" + requestId, new MatchDocument.Request(fingerprint));
		MatchDocument document = new MatchDocument(id, 1, intendedOpponent, MatchDocument.Lifecycle.WAITING_FOR_OPPONENT,
			new MatchDocument.Member("home", owner, frozen), null, requests);
		try {
			matches.insert(record(document));
			return new Result(document, false);
		} catch (MatchRepository.OutcomeUnknown failure) {
			throw new OutcomeUnknown(id);
		} catch (SQLException failure) {
			// Only a uniqueness conflict can reconcile another writer. Other precommit failures remain rejected.
			if (failure.getSQLState() != null && failure.getSQLState().startsWith("23") && matches.find(id) != null)
				return repeated(required(owner, id), owner, requestId, fingerprint);
			throw failure;
		}
	}

	public Result join(String owner, String requestId, String matchId, int expectedMatchVersion,
		String teamId, int expectedTeamVersion) throws SQLException {
		identity(owner); selection(teamId, expectedTeamVersion); selection(matchId, expectedMatchVersion); requestId(requestId);
		MatchDocument document = required(owner, matchId);
		if (!owner.equals(document.intendedOpponent)) throw new Failure("AUTHORIZATION");
		String fingerprint = "join|" + matchId + "|" + expectedMatchVersion + "|" + teamId + "|" + expectedTeamVersion;
		if (document.request(owner, requestId) != null) return repeated(document, owner, requestId, fingerprint);
		if (document.away != null) throw new Failure("SEAT_OCCUPIED");
		if (document.documentVersion != expectedMatchVersion) throw new Failure("STALE_REVISION");
		FrozenTeam frozen = freeze(owner, teamId, expectedTeamVersion);
		if (!document.home.team.ruleset.equals(frozen.ruleset) || !document.home.team.catalogVersion.equals(frozen.catalogVersion)
			|| !document.home.team.presetId.equals(frozen.presetId) || !document.home.team.presetVersion.equals(frozen.presetVersion))
			throw new Failure("INCOMPATIBLE_TEAM");
		MatchDocument joined = document.joined(new MatchDocument.Member("away", owner, frozen), owner + "\n" + requestId, fingerprint);
		try {
			if (!matches.replace(record(joined), document.documentVersion)) {
				MatchDocument after = required(owner, matchId);
				if (after.request(owner, requestId) != null) return repeated(after, owner, requestId, fingerprint);
				throw new Failure("CONFLICT");
			}
			return new Result(joined, false);
		} catch (MatchRepository.OutcomeUnknown failure) { throw new OutcomeUnknown(matchId); }
	}

	public Result load(String owner, String matchId) throws SQLException {
		identity(owner); selection(matchId, 1);
		return new Result(required(owner, matchId), false);
	}

	private Result repeated(MatchDocument document, String owner, String requestId, String fingerprint) {
		MatchDocument.Request prior = document.request(owner, requestId);
		if (prior == null || !prior.fingerprint.equals(fingerprint)) throw new Failure("REQUEST_ID_REUSED");
		return new Result(document, true);
	}

	private FrozenTeam freeze(String owner, String teamId, int expectedVersion) throws SQLException {
		// One immutable read is the linearization point for source selection. Later edits cannot substitute bytes.
		SavedTeamService.Loaded loaded = teams.load(owner, teamId);
		if (!owner.equals(loaded.document.owner)) throw new Failure("NOT_FOUND");
		if (loaded.document.documentVersion != expectedVersion) throw new Failure("STALE_TEAM_REVISION");
		if (!"CURRENT".equals(loaded.versionStatus)) throw new Failure(loaded.versionStatus);
		TeamValidation.Evaluation result = validation.evaluate(loaded.document.draft);
		if (!result.isValid() || result.total == null) throw new Failure("VALIDATION_FAILED");
		return new FrozenTeam(loaded.document.teamId, loaded.document.documentVersion, owner, loaded.document.draft,
			result.total, result.skillPoints, catalog);
	}

	private MatchDocument required(String owner, String id) throws SQLException {
		MatchRepository.Record record = matches.find(id);
		if (record == null) throw new Failure("NOT_FOUND");
		json.authorizePersisted(owner, record.json);
		MatchDocument document = json.decode(record.json, record.documentVersion);
		if (!id.equals(document.matchId) || !id.equals(record.matchId)) throw new Failure("SNAPSHOT_UNSUPPORTED");
		return document;
	}

	private MatchRepository.Record record(MatchDocument document) {
		String text = json.encode(document).toString();
		json.decode(text, document.documentVersion); // Refuse an unrepresentable snapshot before any write.
		return new MatchRepository.Record(document.matchId, document.documentVersion, text);
	}

	private void identity(String owner) { if (!"home".equals(owner) && !"away".equals(owner)) throw new Failure("AUTHENTICATION_REQUIRED"); }
	private boolean opponent(String owner, String other) { return "home".equals(owner) ? "away".equals(other) : "home".equals(other); }
	private void requestId(String id) { if (id == null || !id.matches("[A-Za-z0-9_-]{1,100}")) throw new Failure("INVALID_REQUEST"); }
	private void selection(String id, int version) {
		if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			|| version < 1 || version > 2147483646) throw new Failure("INVALID_REQUEST");
	}
	public static final class Result {
		public final MatchDocument document;
		public final boolean duplicate;
		Result(MatchDocument document, boolean duplicate) { this.document = document; this.duplicate = duplicate; }
	}
	public static final class Failure extends IllegalArgumentException {
		public final String code;
		public Failure(String code) { super(code); this.code = code; }
	}
	public static final class OutcomeUnknown extends IllegalStateException {
		public final String matchId;
		OutcomeUnknown(String matchId) { super("MATCH_OUTCOME_UNKNOWN"); this.matchId = matchId; }
	}
}
