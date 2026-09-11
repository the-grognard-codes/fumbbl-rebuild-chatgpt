package com.fumbbl.ffb.server.match;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Dedicated short transactions for recovery artifacts. */
public final class JdbcRecoveryRepository implements RecoveryRepository {
	public interface Connections { Connection open() throws SQLException; }
	private final Connections connections;

	public JdbcRecoveryRepository(Connections connections) { this.connections = connections; }

	@Override
	public Record find(String matchId) throws SQLException {
		try (Connection connection = connections.open(); PreparedStatement query = connection.prepareStatement(
			"SELECT matchid,generation,artifact_json FROM ffb_match_recovery WHERE matchid=?")) {
			query.setString(1, matchId);
			try (ResultSet rows = query.executeQuery()) {
				return rows.next() ? new Record(rows.getString(1), rows.getLong(2), rows.getString(3)) : null;
			}
		}
	}

	@Override
	public boolean save(Record record, long expectedGeneration) throws SQLException {
		if (expectedGeneration < 0 || expectedGeneration == Long.MAX_VALUE || record.generation != expectedGeneration + 1)
			throw new SQLException("Invalid recovery generation");
		boolean commitAttempted = false;
		try (Connection connection = connections.open()) {
			connection.setAutoCommit(false);
			try {
				int count;
				if (expectedGeneration == 0) {
					try (PreparedStatement statement = connection.prepareStatement(
						"INSERT INTO ffb_match_recovery(matchid,generation,artifact_json) VALUES (?,?,?)")) {
						statement.setString(1, record.matchId); statement.setLong(2, 1); statement.setString(3, record.json);
						count = statement.executeUpdate();
					}
				} else {
					try (PreparedStatement statement = connection.prepareStatement(
						"UPDATE ffb_match_recovery SET generation=?,artifact_json=? WHERE matchid=? AND generation=?")) {
						statement.setLong(1, expectedGeneration + 1); statement.setString(2, record.json);
						statement.setString(3, record.matchId); statement.setLong(4, expectedGeneration);
						count = statement.executeUpdate();
					}
				}
				if (count == 0) { connection.rollback(); return false; }
				if (count != 1) throw new SQLException("Unexpected recovery write count");
				commitAttempted = true;
				connection.commit();
				return true;
			} catch (SQLException failure) {
				try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
				if (expectedGeneration == 0 && duplicateKey(failure)) return false;
				throw failure;
			}
		} catch (SQLException failure) {
			if (commitAttempted) throw new OutcomeUnknown(record, failure);
			throw failure;
		}
	}

	private boolean duplicateKey(SQLException failure) {
		return "23000".equals(failure.getSQLState()) && failure.getErrorCode() == 1062;
	}
}
