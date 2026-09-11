package com.fumbbl.ffb.server.team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Dedicated short JDBC transactions; never shares the legacy game updater connection. */
public final class JdbcSavedTeamRepository implements SavedTeamRepository {
	public interface Connections { Connection open() throws SQLException; }
	private final Connections connections;
	public JdbcSavedTeamRepository(Connections connections) { this.connections = connections; }

	@Override
	public Record find(String owner, String teamId) throws SQLException {
		try (Connection connection = connections.open(); PreparedStatement query = connection.prepareStatement(
			"SELECT team_id, owner_subject, document_version, catalog_version, document_json FROM ffb_saved_teams WHERE owner_subject=? AND team_id=?")) {
			query.setString(1, owner); query.setString(2, teamId);
			try (ResultSet rows = query.executeQuery()) { return rows.next() ? record(rows) : null; }
		}
	}

	@Override
	public List<Record> list(String owner) throws SQLException {
		try (Connection connection = connections.open(); PreparedStatement query = connection.prepareStatement(
			"SELECT team_id, owner_subject, document_version, catalog_version, document_json FROM ffb_saved_teams WHERE owner_subject=? ORDER BY team_id LIMIT 50")) {
			query.setString(1, owner);
			List<Record> result = new ArrayList<>();
			try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(record(rows)); }
			return result;
		}
	}

	@Override
	public void insert(Record record) throws SQLException {
		boolean commitAttempted = false;
		try (Connection connection = connections.open()) {
			connection.setAutoCommit(false);
			try {
				// Serialize capacity checks across JVMs as well as connections. At most 50 documents per local owner.
				try (PreparedStatement lock = connection.prepareStatement("SELECT version FROM ffb_local_schema FOR UPDATE");
					ResultSet rows = lock.executeQuery()) {
					if (!rows.next()) throw new SQLException("Saved-team schema unavailable");
					int schemaVersion = rows.getInt(1);
					if (schemaVersion != 2 && schemaVersion != 3 && schemaVersion != 4) throw new SQLException("Saved-team schema unavailable");
				}
				try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM ffb_saved_teams WHERE owner_subject=?")) {
					count.setString(1, record.owner);
					try (ResultSet rows = count.executeQuery()) {
						if (!rows.next() || rows.getInt(1) >= 50) throw new SQLException("Saved-team capacity reached", "54000");
					}
				}
				try (PreparedStatement insert = connection.prepareStatement(
					"INSERT INTO ffb_saved_teams(team_id,owner_subject,document_version,catalog_version,document_json) VALUES (?,?,?,?,?)")) {
					insert.setString(1, record.teamId); insert.setString(2, record.owner); insert.setInt(3, record.documentVersion);
					insert.setString(4, record.catalogVersion); insert.setString(5, record.json); insert.executeUpdate();
				}
				commitAttempted = true;
				connection.commit();
			} catch (SQLException exception) { rollback(connection, exception); throw exception; }
		} catch (SQLException failure) { if (commitAttempted) throw new OutcomeUnknown(record, failure); throw failure; }
	}

	@Override
	public boolean replace(Record record, int expectedVersion) throws SQLException {
		boolean commitAttempted = false;
		try (Connection connection = connections.open()) {
			connection.setAutoCommit(false);
			try (PreparedStatement update = connection.prepareStatement(
				"UPDATE ffb_saved_teams SET document_version=?,catalog_version=?,document_json=? WHERE team_id=? AND owner_subject=? AND document_version=?")) {
				update.setInt(1, record.documentVersion); update.setString(2, record.catalogVersion); update.setString(3, record.json);
				update.setString(4, record.teamId); update.setString(5, record.owner); update.setInt(6, expectedVersion);
				boolean changed = update.executeUpdate() == 1;
				if (changed) { commitAttempted = true; connection.commit(); } else connection.rollback();
				return changed;
			} catch (SQLException exception) { rollback(connection, exception); throw exception; }
		} catch (SQLException failure) { if (commitAttempted) throw new OutcomeUnknown(record, failure); throw failure; }
	}

	private void rollback(Connection connection, SQLException cause) {
		try { connection.rollback(); } catch (SQLException failure) { cause.addSuppressed(failure); }
	}
	private Record record(ResultSet rows) throws SQLException {
		return new Record(rows.getString(1), rows.getString(2), rows.getInt(3), rows.getString(4), rows.getString(5));
	}
}
