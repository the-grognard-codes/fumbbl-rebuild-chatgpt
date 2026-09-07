package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.db.DbConnectionManager;
import com.fumbbl.ffb.server.db.DbInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Version 1 is for a dedicated disposable local database only. */
public class LocalSchema {
	public void initialize(DbConnectionManager manager, String coachPasswordHash) throws SQLException {
		try (Connection connection = manager.openDbConnection(); Statement statement = connection.createStatement()) {
			boolean empty;
			try (ResultSet tables = statement.executeQuery("SHOW TABLES")) {
				empty = !tables.next();
			}
			if (!empty) {
				// An unversioned or partially initialized database fails closed. Never reset it on startup.
				try (ResultSet version = statement.executeQuery("SELECT version FROM ffb_local_schema")) {
					if (!version.next() || version.getInt(1) != 1 || version.next()) {
						throw new SQLException("Unsupported local schema; use the documented explicit disposable reset");
					}
				}
				return;
			}
			new DbInitializer(manager).initDb(false);
			statement.executeUpdate("ALTER TABLE ffb_games_serialized MODIFY serialized LONGBLOB");
			try (PreparedStatement seed = connection.prepareStatement("INSERT INTO ffb_coaches(name,password) VALUES (?,?)")) {
				for (String coach : new String[] {"FixtureHome", "FixtureAway"}) {
					seed.setString(1, coach);
					seed.setString(2, coachPasswordHash);
					seed.executeUpdate();
				}
			}
			connection.commit();
			statement.executeUpdate("CREATE TABLE ffb_local_schema (version INT NOT NULL PRIMARY KEY)");
			statement.executeUpdate("INSERT INTO ffb_local_schema VALUES (1)");
			connection.commit();
		}
	}
}
