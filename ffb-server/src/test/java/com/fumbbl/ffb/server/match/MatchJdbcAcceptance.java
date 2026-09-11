package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.team.JdbcSavedTeamRepository;
import com.fumbbl.ffb.server.team.SavedTeamService;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Container-only acceptance, never packaged or exposed as product operations. Retains synthetic evidence rows. */
public final class MatchJdbcAcceptance {
	private JdbcMatchRepository.Connections connections;
	private SavedTeamService teams;
	private RosterCatalog catalog;
	private final MatchJson json = new MatchJson();

	public static void main(String[] arguments) throws Exception { new MatchJdbcAcceptance().run(); }

	private void run() throws Exception {
		Properties config = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("/config/server.ini"))) { config.load(input); }
		String password = new String(Files.readAllBytes(Paths.get(config.getProperty("db.password.file"))), StandardCharsets.UTF_8).trim();
		connections = () -> DriverManager.getConnection("jdbc:mariadb://database:3306/ffb_local", "ffb_local", password);
		catalog = new RosterCatalog();
		teams = new SavedTeamService(new JdbcSavedTeamRepository(() -> connections.open()), catalog);
		String homeTeam = teams.create("home", draft(0)).document.teamId;
		String awayTeam = teams.create("away", draft(0)).document.teamId;
		MatchService normal = service(connections);
		JsonObject create = create(awayTeam, "home");
		JsonObject created = accepted(normal, "away", create);
		String id = created.get("document").asObject().getString("matchId", null);
		check("home".equals(created.getString("callerRole", null)), "reversed creator role");
		JdbcMatchRepository repository = new JdbcMatchRepository(connections);
		String initial = repository.find(id).json;
		teams.update("away", awayTeam, 1, draft(1));
		check(initial.equals(repository.find(id).json), "source edit modified snapshot");
		JsonObject retry = accepted(service(connections), "away", create);
		check(retry.getBoolean("duplicate", false), "create retry not recognized after new service");
		check(id.equals(retry.get("document").asObject().getString("matchId", null)), "duplicate created another match");

		JsonObject join = join(id, homeTeam);
		String rejected = json.handle(normal, "away", join.toString()).getString("code", null);
		check(!"ACCEPTED".equals(rejected), "creator took invited seat");
		check(initial.equals(repository.find(id).json), "authorization rejection changed document");

		// Inject a failure after SQL executes but before COMMIT, exercising real rollback.
		MatchService failed = service(faultConnections(false));
		JsonObject failedResponse = json.handle(failed, "home", join.toString());
		check("PERSISTENCE_FAILED".equals(failedResponse.getString("code", null)), "precommit failure misclassified");
		check(initial.equals(repository.find(id).json), "precommit failure left partial membership");
		long before = count();
		JsonObject failedCreate = json.handle(failed, "home", create(homeTeam, "away").toString());
		check("PERSISTENCE_FAILED".equals(failedCreate.getString("code", null)), "failed insert misclassified");
		check(before == count(), "failed create orphaned snapshot");

		CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try {
			java.util.concurrent.Callable<JsonObject> attempt = () -> {
				ready.countDown(); start.await();
				return json.handle(service(connections), "home", join(id, homeTeam).toString());
			};
			Future<JsonObject> first = workers.submit(attempt), second = workers.submit(attempt);
			check(ready.await(10, TimeUnit.SECONDS), "concurrent workers not ready"); start.countDown();
			boolean a = "ACCEPTED".equals(first.get(20, TimeUnit.SECONDS).getString("code", null));
			boolean b = "ACCEPTED".equals(second.get(20, TimeUnit.SECONDS).getString("code", null));
			check(a != b, "concurrent joins need exactly one winner");
		} finally { workers.shutdownNow(); }
		check(repository.find(id).documentVersion == 2, "join advanced lifecycle more than once");
		String joined = repository.find(id).json;
		check(joined.equals(repository.find(id).json), "authoritative reload changed data");

		// Commit really succeeds, but the acknowledgement is lost. Retry must resolve that same row.
		JsonObject unknownCreate = create(homeTeam, "away");
		JsonObject unknown = json.handle(service(faultConnections(true)), "home", unknownCreate.toString());
		check("MATCH_OUTCOME_UNKNOWN".equals(unknown.getString("code", null)), "lost acknowledgement not unknown");
		check(unknown.get("document").isNull(), "unknown outcome exposed attempted document as authoritative");
		JsonObject reconciled = accepted(service(connections), "home", unknownCreate);
		check(reconciled.getBoolean("duplicate", false), "unknown create retry duplicated mutation");
		String recoveredId = reconciled.get("document").asObject().getString("matchId", null);
		JsonObject unknownJoin = join(recoveredId, awayTeam).set("expectedDocumentVersion", 2);
		unknown = json.handle(service(faultConnections(true)), "away", unknownJoin.toString());
		check("MATCH_OUTCOME_UNKNOWN".equals(unknown.getString("code", null)), "join acknowledgement not unknown");
		reconciled = accepted(service(connections), "away", unknownJoin);
		check(reconciled.getBoolean("duplicate", false), "join retry not duplicate");
		check(repository.find(recoveredId).documentVersion == 2, "join retry advanced revision");
		long beforeCreates = count();
		JsonObject concurrentCreate = create(homeTeam, "away");
		ExecutorService creators = Executors.newFixedThreadPool(2);
		CountDownLatch createReady = new CountDownLatch(2), createStart = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<JsonObject> attempt = () -> {
				createReady.countDown(); createStart.await();
				return accepted(service(connections), "home", concurrentCreate);
			};
			Future<JsonObject> first = creators.submit(attempt), second = creators.submit(attempt);
			check(createReady.await(10, TimeUnit.SECONDS), "create workers not ready"); createStart.countDown();
			JsonObject a = first.get(20, TimeUnit.SECONDS), b = second.get(20, TimeUnit.SECONDS);
			check(a.get("document").equals(b.get("document")), "concurrent create returned different matches");
			check(a.getBoolean("duplicate", false) != b.getBoolean("duplicate", false), "concurrent create needs one original acceptance");
			check(count() == beforeCreates + 1, "concurrent create duplicated row");
		} finally { creators.shutdownNow(); }
		JsonObject activation = header("activate").add("matchId", recoveredId).add("expectedRevision", 2);
		String unactivated = repository.find(recoveredId).json;
		check("PERSISTENCE_FAILED".equals(json.handle(service(faultConnections(false)), "away", activation.toString()).getString("code", null)), "activation rollback misclassified");
		check(unactivated.equals(repository.find(recoveredId).json), "activation rollback changed durable state");
		check("MATCH_OUTCOME_UNKNOWN".equals(json.handle(service(faultConnections(true)), "away", activation.toString()).getString("code", null)), "activation lost acknowledgement misclassified");
		check(accepted(service(connections), "away", activation).getBoolean("duplicate", false), "activation retry not duplicate");
		check(repository.find(recoveredId).documentVersion == 3, "activation retry advanced revision");
		CompletedMatch recoveredCompletion = completed(recoveredId);
		String active = repository.find(recoveredId).json;
		try { service(faultConnections(false)).complete("home", recoveredId, recoveredCompletion); throw new AssertionError("completion rollback accepted"); }
		catch (SQLException expected) { }
		check(active.equals(repository.find(recoveredId).json), "completion rollback changed durable state");
		try { service(faultConnections(true)).complete("home", recoveredId, recoveredCompletion); throw new AssertionError("completion acknowledgement loss accepted"); }
		catch (MatchService.OutcomeUnknown expected) { }
		service(connections).complete("away", recoveredId, recoveredCompletion);
		check(repository.find(recoveredId).documentVersion == 4, "completion retry advanced revision");
		ExecutorService activators = Executors.newFixedThreadPool(2);
		CountDownLatch activationReady = new CountDownLatch(2), activationStart = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<JsonObject> attempt = () -> {
				activationReady.countDown(); activationStart.await();
				return json.handle(service(connections), "away", header("activate").add("matchId", id).add("expectedRevision", 2).toString());
			};
			Future<JsonObject> first = activators.submit(attempt), second = activators.submit(attempt);
			check(activationReady.await(10, TimeUnit.SECONDS), "activation workers not ready"); activationStart.countDown();
			boolean a = "ACCEPTED".equals(first.get(20, TimeUnit.SECONDS).getString("code", null));
			boolean b = "ACCEPTED".equals(second.get(20, TimeUnit.SECONDS).getString("code", null));
			check(a != b, "activation needs one winner");
			check(repository.find(id).documentVersion == 3, "concurrent activation advanced twice");
		} finally { activators.shutdownNow(); }
		CompletedMatch concurrentCompletion = completed(id);
		ExecutorService completers = Executors.newFixedThreadPool(2);
		CountDownLatch completionReady = new CountDownLatch(2), completionStart = new CountDownLatch(1);
		try {
			java.util.concurrent.Callable<Void> attempt = () -> { completionReady.countDown(); completionStart.await(); service(connections).complete("home", id, concurrentCompletion); return null; };
			Future<Void> first = completers.submit(attempt), second = completers.submit(attempt);
			check(completionReady.await(10, TimeUnit.SECONDS), "completion workers not ready"); completionStart.countDown();
			first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
			check(repository.find(id).documentVersion == 4, "concurrent completion advanced twice");
		} finally { completers.shutdownNow(); }
		System.out.println("M3d MariaDB acceptance passed: completion rollback, CAS, durable retry and lost acknowledgement.");
	}

	private MatchService service(JdbcMatchRepository.Connections source) { return new MatchService(new JdbcMatchRepository(source), teams, catalog); }
	private JsonObject accepted(MatchService service, String owner, JsonObject request) {
		JsonObject response = json.handle(service, owner, request.toString());
		check("ACCEPTED".equals(response.getString("code", null)), "expected acceptance, received " + response.getString("code", null));
		return response;
	}
	private JsonObject create(String teamId, String opponent) { return header("create").add("teamId", teamId).add("expectedDocumentVersion", 1).add("intendedOpponent", opponent); }
	private JsonObject join(String matchId, String teamId) { return header("join").add("matchId", matchId).add("expectedRevision", 1).add("teamId", teamId).add("expectedDocumentVersion", 1); }
	private JsonObject header(String operation) { return new JsonObject().add("version", 1).add("type", "preparedMatch").add("requestId", UUID.randomUUID().toString()).add("operation", operation); }
	private CompletedMatch completed(String id) {
		JsonObject state = new JsonObject().add("half", 2).add("drive", 1).add("homeScore", 0).add("awayScore", 0).add("homeTurn", 0).add("awayTurn", 0).add("actions", new com.eclipsesource.json.JsonArray()).add("turn", 0).add("turnMode", "END_GAME").add("activePlayerId", com.eclipsesource.json.JsonValue.NULL).add("ball", com.eclipsesource.json.JsonValue.NULL).add("matchId", id).add("revision", 0).add("callerRole", "home").add("phase", "FULL_TIME").add("actor", "home").add("prompt", com.eclipsesource.json.JsonValue.NULL).add("players", new com.eclipsesource.json.JsonArray()).add("weather", "NICE").add("homeRerolls", 0).add("awayRerolls", 0);
		return new CompletedMatch(new JsonObject().add("formatVersion", 1).add("engineVersion", CompletedMatch.ENGINE_VERSION).add("ruleset", "BB2025")
			.add("catalogVersion", RosterCatalog.VERSION).add("presetId", RosterCatalog.PRESET).add("presetVersion", RosterCatalog.VERSION).add("matchId", id)
			.add("homeScore", 0).add("awayScore", 0).add("finalRevision", 0).add("events", new com.eclipsesource.json.JsonArray().add(new JsonObject().add("revision", 0).add("kind", "FULL_TIME").add("state", state))).toString());
	}
	private TeamDraft draft(int rerolls) {
		List<TeamDraft.Player> players = new ArrayList<>();
		for (int slot = 1; slot <= 11; slot++) players.add(new TeamDraft.Player("jdbc" + slot, slot, "lineman", Collections.emptyList()));
		Map<String, Integer> resources = new LinkedHashMap<>();
		for (String resource : catalog.getResources().keySet()) resources.put(resource, 0);
		resources.put("rerolls", rerolls);
		return new TeamDraft(RosterCatalog.VERSION, "BB2025", "human", RosterCatalog.PRESET, null, players, resources);
	}
	private long count() throws SQLException {
		try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM ffb_prepared_matches"); ResultSet rows = statement.executeQuery()) {
			rows.next(); return rows.getLong(1);
		}
	}
	private JdbcMatchRepository.Connections faultConnections(boolean afterCommit) {
		return () -> {
			Connection actual = connections.open();
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (proxy, method, arguments) -> {
				try {
					Object result = method.invoke(actual, arguments);
					if (afterCommit && "commit".equals(method.getName())) throw new SQLException("Injected acknowledgement loss");
					if (!afterCommit && "prepareStatement".equals(method.getName()) && arguments[0] instanceof String
						&& (((String) arguments[0]).startsWith("INSERT") || ((String) arguments[0]).startsWith("UPDATE"))) {
						PreparedStatement statement = (PreparedStatement) result;
						return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class }, (ignored, call, args) -> {
							try { Object value = call.invoke(statement, args); if ("executeUpdate".equals(call.getName())) throw new SQLException("Injected precommit failure"); return value; }
							catch (InvocationTargetException failure) { throw failure.getCause(); }
						});
					}
					return result;
				} catch (InvocationTargetException failure) { throw failure.getCause(); }
			});
		};
	}
	private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
