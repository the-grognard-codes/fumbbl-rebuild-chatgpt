package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.db.DbConnectionManager;
import com.fumbbl.ffb.server.db.DbInitializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Versioned migrations for the dedicated local database; never deletes existing data. */
public class LocalSchema {
	public void initialize(DbConnectionManager manager, String coachPasswordHash) throws SQLException {
		try (Connection connection = manager.openDbConnection(); Statement statement = connection.createStatement()) {
			boolean empty;
			try (ResultSet tables = statement.executeQuery("SHOW TABLES")) {
				empty = !tables.next();
			}
			if (!empty) {
				// An unversioned or partially initialized database fails closed. Never reset it on startup.
				int current;
				try (ResultSet version = statement.executeQuery("SELECT version FROM ffb_local_schema")) {
					if (!version.next()) throw new SQLException("Missing local schema version");
					current = version.getInt(1);
					if ((current < 1 || current > 5) || version.next()) {
						throw new SQLException("Unsupported local schema; use the documented explicit disposable reset");
					}
				}
				if (current == 1) migrateSavedTeams(connection);
				else verifySavedTeams(connection);
				if (current < 3) migratePreparedMatches(connection);
				if (current < 4) migrateCompletedMatches(connection);
				else verifyCompletedMatches(connection);
				if (current < 5) migrateRecovery(connection);
				else verifyRecovery(connection);
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
			migrateSavedTeams(connection);
			migratePreparedMatches(connection);
			migrateCompletedMatches(connection);
			migrateRecovery(connection);
		}
	}

	private void migrateSavedTeams(Connection connection) throws SQLException {
		// MariaDB DDL commits implicitly. Resume only after verifying the complete expected table;
		// a partial/foreign shape fails closed, and startup never drops or rewrites data.
		try (InputStream source = LocalSchema.class.getResourceAsStream("/local-schema/002-saved-teams.sql")) {
			if (source == null) throw new SQLException("Missing local schema migration 002");
			String ddl;
			try (Scanner scanner = new Scanner(source, "UTF-8").useDelimiter("\\A")) { ddl = scanner.next(); }
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate(ddl.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
				verifySavedTeams(connection);
				if (statement.executeUpdate("UPDATE ffb_local_schema SET version=2 WHERE version=1") != 1) throw new SQLException("Schema migration conflict");
				connection.commit();
			}
		} catch (IOException exception) { throw new SQLException("Unable to read local schema migration 002", exception); }
	}

	private void migratePreparedMatches(Connection connection) throws SQLException {
		try (InputStream source = LocalSchema.class.getResourceAsStream("/local-schema/003-prepared-matches.sql")) {
			if (source == null) throw new SQLException("Missing local schema migration 003");
			String ddl;
			try (Scanner scanner = new Scanner(source, "UTF-8").useDelimiter("\\A")) { ddl = scanner.next(); }
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate(ddl.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
				verifyPreparedMatches(connection);
				if (statement.executeUpdate("UPDATE ffb_local_schema SET version=3 WHERE version=2") != 1) throw new SQLException("Schema migration conflict");
				connection.commit();
			}
		} catch (IOException exception) { throw new SQLException("Unable to read local schema migration 003", exception); }
	}

	void verifyPreparedMatches(Connection connection) throws SQLException { verifyMatchTable(connection, false); }
    void verifyCompletedMatches(Connection connection) throws SQLException { verifyMatchTable(connection, true); }

    private void migrateCompletedMatches(Connection connection) throws SQLException {
        // One atomic MariaDB ALTER preserves the old or new complete shape across interruption.
        boolean migrated = false;
        try { verifyCompletedMatches(connection); migrated = true; }
        catch (SQLException notMigrated) { verifyPreparedMatches(connection); }
        try (Statement statement = connection.createStatement()) {
            if (!migrated) {
                String constraint = null;
                try (ResultSet rows = statement.executeQuery("SELECT CONSTRAINT_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='ffb_prepared_matches'")) {
                    while (rows.next()) {
                        String clause = rows.getString(2).toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s`()]+", "");
                        if ("octet_lengthdocument_json<=65536".equals(clause)) constraint = rows.getString(1);
                    }
                }
                if (constraint == null || !constraint.matches("[A-Za-z0-9_]+")) throw new SQLException("Missing original match size constraint");
                try (InputStream source = LocalSchema.class.getResourceAsStream("/local-schema/004-completed-matches.sql")) {
                    if (source == null) throw new SQLException("Missing migration 004");
                    try (Scanner scanner = new Scanner(source, "UTF-8").useDelimiter("\\A")) {
                        statement.executeUpdate(scanner.next().replace("${sizeConstraint}", constraint));
                    }
                } catch (IOException failure) { throw new SQLException("Unable to read migration 004", failure); }
            }
            verifyCompletedMatches(connection);
            if (statement.executeUpdate("UPDATE ffb_local_schema SET version=4 WHERE version=3") != 1) throw new SQLException("Schema migration conflict");
            connection.commit();
        }
    }

    private void verifyMatchTable(Connection connection, boolean completed) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet table = statement.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_prepared_matches'")) {
				if (!table.next() || !"InnoDB".equalsIgnoreCase(table.getString(1)) || table.next()) throw new SQLException("Prepared-match table engine mismatch");
			}
			String[] expected = {"match_id|char(36)|NO|ascii|ascii_bin", "document_version|int|NO|null|null",
				(completed ? "document_json|longtext|NO|utf8mb4|utf8mb4_bin" : "document_json|mediumtext|NO|utf8mb4|utf8mb4_bin")};
			try (ResultSet columns = statement.executeQuery("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_prepared_matches' ORDER BY ORDINAL_POSITION")) {
				for (String column : expected) {
					if (!columns.next()) throw new SQLException("Prepared-match columns missing");
					String actual = columns.getString(1) + "|" + columns.getString(2).replace("int(11)", "int") + "|" + columns.getString(3) + "|" + columns.getString(4) + "|" + columns.getString(5);
					if (!column.equals(actual)) throw new SQLException("Prepared-match column mismatch");
				}
				if (columns.next()) throw new SQLException("Unexpected prepared-match column");
			}
			Set<String> indexes = new HashSet<>();
			try (ResultSet rows = statement.executeQuery("SELECT INDEX_NAME,NON_UNIQUE,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_prepared_matches'")) {
				while (rows.next()) indexes.add(rows.getString(1) + "|" + rows.getInt(2) + "|" + rows.getString(3));
			}
			if (!indexes.equals(new HashSet<>(Arrays.asList("PRIMARY|0|match_id")))) throw new SQLException("Prepared-match index mismatch");
			Set<String> checks = new HashSet<>();
			try (ResultSet rows = statement.executeQuery("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='ffb_prepared_matches'")) {
				while (rows.next()) checks.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s`()]+", ""));
			}
			if (!checks.equals(new HashSet<>(Arrays.asList("document_versionbetween1and2147483646", (completed ? "octet_lengthdocument_json<=16842752" : "octet_lengthdocument_json<=65536"))))) throw new SQLException("Prepared-match constraint mismatch");
			try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='ffb_prepared_matches'")) {
				if (!rows.next() || rows.getInt(1) != 0) throw new SQLException("Unexpected prepared-match trigger");
			}
		}
	}

	private void migrateRecovery(Connection connection) throws SQLException {
		try (InputStream source = LocalSchema.class.getResourceAsStream("/local-schema/005-match-recovery.sql")) {
			if (source == null) throw new SQLException("Missing local schema migration 005");
			String ddl;
			try (Scanner scanner = new Scanner(source, "UTF-8").useDelimiter("\\A")) { ddl = scanner.next(); }
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate(ddl.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
				verifyRecovery(connection);
				if (statement.executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4") != 1) throw new SQLException("Schema migration conflict");
				connection.commit();
			}
		} catch (IOException exception) { throw new SQLException("Unable to read local schema migration 005", exception); }
	}

	void verifyRecovery(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet table = statement.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_match_recovery'")) {
				if (!table.next() || !"InnoDB".equalsIgnoreCase(table.getString(1)) || table.next()) throw new SQLException("Recovery table engine mismatch");
			}
			String[] expected = {"matchid|char(36)|NO|ascii|ascii_bin", "generation|bigint|NO|null|null", "artifact_json|longtext|NO|utf8mb4|utf8mb4_bin"};
			try (ResultSet columns = statement.executeQuery("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_match_recovery' ORDER BY ORDINAL_POSITION")) {
				for (String column : expected) {
					if (!columns.next()) throw new SQLException("Recovery columns missing");
					String actual = columns.getString(1) + "|" + columns.getString(2).replace("int(11)", "int").replace("bigint(20)", "bigint") + "|" + columns.getString(3) + "|" + columns.getString(4) + "|" + columns.getString(5);
					if (!column.equals(actual)) throw new SQLException("Recovery column mismatch");
				}
				if (columns.next()) throw new SQLException("Unexpected recovery column");
			}
			Set<String> indexes = new HashSet<>();
			try (ResultSet rows = statement.executeQuery("SELECT INDEX_NAME,NON_UNIQUE,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_match_recovery'")) {
				while (rows.next()) indexes.add(rows.getString(1) + "|" + rows.getInt(2) + "|" + rows.getString(3));
			}
			if (!indexes.equals(new HashSet<>(Arrays.asList("PRIMARY|0|matchid")))) throw new SQLException("Recovery index mismatch");
			Set<String> checks = new HashSet<>();
			try (ResultSet rows = statement.executeQuery("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='ffb_match_recovery'")) {
				while (rows.next()) checks.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s`()]+", ""));
			}
			if (!checks.equals(new HashSet<>(Arrays.asList("generation>0", "octet_lengthartifact_json<=33554432")))) throw new SQLException("Recovery constraint mismatch");
			try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='ffb_match_recovery'")) {
				if (!rows.next() || rows.getInt(1) != 0) throw new SQLException("Unexpected recovery trigger");
			}
		}
	}

	void verifySavedTeams(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			try (ResultSet table = statement.executeQuery("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_saved_teams'")) {
				if (!table.next() || !"InnoDB".equalsIgnoreCase(table.getString(1)) || table.next()) throw new SQLException("Saved-team table engine mismatch");
			}
			String[] expected = {"team_id|char(36)|NO|ascii|ascii_bin", "owner_subject|varchar(16)|NO|ascii|ascii_bin",
				"document_version|int|NO|null|null", "catalog_version|varchar(80)|NO|ascii|ascii_bin", "document_json|text|NO|utf8mb4|utf8mb4_bin"};
			try (ResultSet columns = statement.executeQuery("SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,CHARACTER_SET_NAME,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_saved_teams' ORDER BY ORDINAL_POSITION")) {
				for (String column : expected) {
					if (!columns.next()) throw new SQLException("Saved-team columns missing");
					String actual = columns.getString(1) + "|" + columns.getString(2).replace("int(11)", "int") + "|" + columns.getString(3) + "|" + columns.getString(4) + "|" + columns.getString(5);
					if (!column.equals(actual)) throw new SQLException("Saved-team column mismatch");
				}
				if (columns.next()) throw new SQLException("Unexpected saved-team column");
			}
			Set<String> indexes = new HashSet<>();
			try (ResultSet rows = statement.executeQuery("SELECT INDEX_NAME,NON_UNIQUE,COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ffb_saved_teams'")) {
				while (rows.next()) indexes.add(rows.getString(1) + "|" + rows.getInt(2) + "|" + rows.getString(3));
			}
			if (!indexes.equals(new HashSet<>(Arrays.asList("PRIMARY|0|team_id", "saved_team_owner|1|owner_subject")))) throw new SQLException("Saved-team index mismatch");
			Set<String> checks = new HashSet<>();
			try (ResultSet rows = statement.executeQuery("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='ffb_saved_teams'")) {
				while (rows.next()) checks.add(rows.getString(1).toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s`()]+", ""));
			}
			if (!checks.equals(new HashSet<>(Arrays.asList("owner_subjectin'home','away'", "document_versionbetween1and2147483646", "octet_lengthdocument_json<=16384")))) throw new SQLException("Saved-team constraint mismatch");
			try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='ffb_saved_teams'")) {
				if (!rows.next() || rows.getInt(1) != 0) throw new SQLException("Unexpected saved-team trigger");
			}
		}
	}
}
