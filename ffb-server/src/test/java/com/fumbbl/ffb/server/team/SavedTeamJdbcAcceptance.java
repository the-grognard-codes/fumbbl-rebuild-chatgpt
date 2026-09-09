package com.fumbbl.ffb.server.team;

import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.team.bb2025.TeamDraft;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Explicit container-only JDBC acceptance runner, outside the browser and shipped application. No data reset. */
public final class SavedTeamJdbcAcceptance {
	public static void main(String[] arguments) throws Exception {
		new SavedTeamJdbcAcceptance().run();
	}
	private void run() throws Exception {
		Properties config = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("/config/server.ini"))) { config.load(input); }
		String password = new String(Files.readAllBytes(Paths.get(config.getProperty("db.password.file"))), StandardCharsets.UTF_8).trim();
		JdbcSavedTeamRepository.Connections connections = () -> DriverManager.getConnection("jdbc:mariadb://database:3306/ffb_local", "ffb_local", password);
		JdbcSavedTeamRepository repository = new JdbcSavedTeamRepository(connections);
		String id = UUID.randomUUID().toString();
		// Keep the valid synthetic service-created document as local acceptance data; never delete/reset rows.
		RosterCatalog catalog = new RosterCatalog();
		SavedTeamService service = new SavedTeamService(repository, catalog);
		List<TeamDraft.Player> players = new ArrayList<>();
		for (int slot = 1; slot <= 11; slot++) players.add(new TeamDraft.Player("jdbc" + slot, slot, "lineman", Collections.emptyList()));
		Map<String, Integer> resources = new LinkedHashMap<>();
		for (String resource : catalog.getResources().keySet()) resources.put(resource, 0);
		TeamDraft draft = new TeamDraft(
			RosterCatalog.VERSION, "BB2025", "human",
			RosterCatalog.PRESET, null, players, resources);
		SavedTeamService.Loaded saved = service.create("home", draft);
		SavedTeamRepository.Record original = repository.find("home", saved.document.teamId);
		SavedTeamRepository.Record baseline = original;
		SavedTeamRepository.Record replacement = new SavedTeamRepository.Record(original.teamId, "home", 2, original.catalogVersion, original.json);
		JdbcSavedTeamRepository failingCommit = new JdbcSavedTeamRepository(() -> {
			Connection actual = connections.open();
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
				if ("commit".equals(method.getName())) throw new SQLException("Injected pre-commit failure");
				try { return method.invoke(actual, args); } catch (InvocationTargetException exception) { throw exception.getCause(); }
			});
		});
		expectFailure(() -> failingCommit.replace(replacement, 1));
		unchanged(repository, baseline);
		int count = repository.list("home").size();
		SavedTeamRepository.Record insertion = new SavedTeamRepository.Record(id, "home", 1, baseline.catalogVersion, baseline.json);
		expectFailure(() -> failingCommit.insert(insertion));
		if (repository.find("home", id) != null || repository.list("home").size() != count) throw new AssertionError("Partial insert");
		expectFailure(() -> repository.insert(baseline));
		unchanged(repository, baseline);
		SavedTeamRepository.Record oversized = new SavedTeamRepository.Record(baseline.teamId, "home", 2, baseline.catalogVersion, new String(new char[16385]).replace('\0', 'x'));
		expectFailure(() -> repository.replace(oversized, 1));
		unchanged(repository, baseline);
		CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
		ExecutorService workers = Executors.newFixedThreadPool(2);
		try {
			Callable<Boolean> update = () -> {
				ready.countDown(); start.await();
				try { new SavedTeamService(new JdbcSavedTeamRepository(connections), catalog).update("home", baseline.teamId, 1, draft); return true; }
				catch (SavedTeamService.Failure failure) { if (!"CONFLICT".equals(failure.code)) throw failure; return false; }
			};
			Future<Boolean> first = workers.submit(update), second = workers.submit(update);
			if (!ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("Workers not ready"); start.countDown();
			if (first.get(20, TimeUnit.SECONDS) == second.get(20, TimeUnit.SECONDS)) throw new AssertionError("CAS must have exactly one winner");
		} finally { workers.shutdownNow(); }
		SavedTeamService.Loaded reloaded = new SavedTeamService(new JdbcSavedTeamRepository(connections), catalog).load("home", baseline.teamId);
		if (reloaded.document.documentVersion != 2 || reloaded.validation.total != 550000) throw new AssertionError("Durable canonical reload");
		JdbcSavedTeamRepository lostAcknowledgement = new JdbcSavedTeamRepository(() -> {
			Connection actual = connections.open();
			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
				try {
					Object result = method.invoke(actual, args);
					if ("commit".equals(method.getName())) throw new SQLException("Injected lost commit acknowledgement");
					return result;
				} catch (InvocationTargetException exception) { throw exception.getCause(); }
			});
		});
		try {
			new SavedTeamService(lostAcknowledgement, catalog).update("home", baseline.teamId, 2, draft);
			throw new AssertionError("Lost acknowledgement must not claim success");
		} catch (SavedTeamRepository.OutcomeUnknown expected) {
			if (!expected.attempted.teamId.equals(baseline.teamId)) throw new AssertionError("Missing recovery ID");
		}
		if (service.load("home", baseline.teamId).document.documentVersion != 3) throw new AssertionError("Ambiguous outcome must be reconciled by load");
		System.out.println("PASS MariaDB pre-commit insert/update rollback, constraint/duplicate rollback, concurrent CAS, new-connection reload and post-commit unknown-outcome reconciliation");
	}
	private void unchanged(SavedTeamRepository repository, SavedTeamRepository.Record before) throws SQLException {
		SavedTeamRepository.Record after = repository.find("home", before.teamId);
		if (after.documentVersion != before.documentVersion || !after.json.equals(before.json)) throw new AssertionError("Rejected write changed bytes");
	}
	private void expectFailure(SqlAction action) throws SQLException {
		try { action.run(); } catch (SQLException expected) { return; }
		throw new AssertionError("Expected persistence failure");
	}
	private interface SqlAction { void run() throws SQLException; }
}
