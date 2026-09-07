package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.ServerMode;
import com.fumbbl.ffb.server.db.DbConnectionManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/** Container-only entry point. No live credentials or destructive initDb argument. */
public class LocalServerMain {
	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected a local server properties file");
		}
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get(args[0]))) {
			properties.load(input);
		}
		if (!"true".equals(properties.getProperty("server.local"))
			|| !"jdbc:mariadb://database:3306/ffb_local".equals(properties.getProperty("db.url"))) {
			throw new IllegalArgumentException("Local startup requires server.local=true and the dedicated Compose database");
		}
		for (String key : properties.stringPropertyNames()) {
			if (key.startsWith("fumbbl.") || key.startsWith("backup.s3.")) {
				throw new IllegalArgumentException("Live service configuration is forbidden in the local profile");
			}
		}
		LocalServerMain launcher = new LocalServerMain();
		properties.setProperty("db.password", launcher.readSecret(properties, "db.password.file"));
		properties.setProperty("admin.password", launcher.readSecret(properties, "admin.password.file"));
		properties.setProperty("backup.password", properties.getProperty("admin.password"));
		String coachPassword = launcher.readSecret(properties, "local.coach.password.file");
		if (!coachPassword.matches("[0-9a-f]{32}")) {
			throw new IllegalArgumentException("Fixture coach secret must be an MD5 hex digest for the legacy local protocol");
		}
		FantasyFootballServer server = new FantasyFootballServer(ServerMode.STANDALONE, properties);
		Class.forName(properties.getProperty("db.driver"));
		DbConnectionManager manager = new DbConnectionManager(server);
		manager.setDbUrl(properties.getProperty("db.url"));
		manager.setDbUser(properties.getProperty("db.user"));
		manager.setDbPassword(properties.getProperty("db.password"));
		manager.setDbType("mariadb");
		new LocalSchema().initialize(manager, coachPassword);
		try {
			server.run();
			Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownResources, "local-server-shutdown"));
		} catch (Exception failure) {
			failure.printStackTrace();
			server.stop(99);
		}
	}

	private String readSecret(Properties properties, String key) throws Exception {
		String path = properties.getProperty(key);
		if (path == null) {
			throw new IllegalArgumentException("Missing " + key);
		}
		String secret = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
		if (secret.isEmpty()) {
			throw new IllegalArgumentException("Empty " + key);
		}
		return secret;
	}
}
