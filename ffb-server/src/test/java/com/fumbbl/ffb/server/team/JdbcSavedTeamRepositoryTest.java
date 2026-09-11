package com.fumbbl.ffb.server.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSavedTeamRepositoryTest {
	private Connection connection;
	private PreparedStatement statement;
	private JdbcSavedTeamRepository repository;
	private final SavedTeamRepository.Record record = new SavedTeamRepository.Record("team", "home", 2, "catalog", "canonical snapshot");
	@BeforeEach
	void setup() throws Exception {
		connection = mock(Connection.class); statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		repository = new JdbcSavedTeamRepository(() -> connection);
	}
	@Test
	void replacementBindsOwnerAndExpectedVersionAndCommitsOnce() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		assertTrue(repository.replace(record, 1));
		verify(statement).setString(5, "home"); verify(statement).setInt(6, 1);
		verify(connection).setAutoCommit(false); verify(connection).commit(); verify(connection, never()).rollback(); verify(connection).close();
	}
	@Test
	void staleReplacementRollsBackWithoutCommit() throws Exception {
		when(statement.executeUpdate()).thenReturn(0);
		assertFalse(repository.replace(record, 1)); verify(connection).rollback(); verify(connection, never()).commit(); verify(connection).close();
	}
	@Test
	void failedStatementAndFailedCommitBothRollBackAndClose() throws Exception {
		when(statement.executeUpdate()).thenThrow(new SQLException("failure"));
		assertThrows(SQLException.class, () -> repository.replace(record, 1)); verify(connection).rollback(); verify(connection).close();
		setup(); when(statement.executeUpdate()).thenReturn(1); doThrow(new SQLException("failure")).when(connection).commit();
		assertThrows(SavedTeamRepository.OutcomeUnknown.class, () -> repository.replace(record, 1)); verify(connection).rollback(); verify(connection).close();
	}
	@Test
	void failureClosingAcknowledgedCommitIsAnUnknownOutcomeNotARejectedWrite() throws Exception {
		when(statement.executeUpdate()).thenReturn(1); doThrow(new SQLException("close failure")).when(connection).close();
		assertThrows(SavedTeamRepository.OutcomeUnknown.class, () -> repository.replace(record, 1)); verify(connection).commit();
	}
	@Test
	void failedInsertAndCapacityCheckRollbackWithoutPartialRow() throws Exception {
		ResultSet rows = mock(ResultSet.class); when(statement.executeQuery()).thenReturn(rows);
		when(rows.next()).thenReturn(true); when(rows.getInt(1)).thenReturn(2, 0);
		when(statement.executeUpdate()).thenThrow(new SQLException("constraint failure"));
		assertThrows(SQLException.class, () -> repository.insert(record)); verify(connection).rollback(); verify(connection, never()).commit();
		setup(); rows = mock(ResultSet.class); when(statement.executeQuery()).thenReturn(rows);
		when(rows.next()).thenReturn(true); when(rows.getInt(1)).thenReturn(2, 50);
		assertThrows(SQLException.class, () -> repository.insert(record)); verify(statement, never()).executeUpdate(); verify(connection).rollback();
	}
	@Test
	void recoverySchemaPermitsSavedTeamCreationAndFutureSchemaStillFailsClosed() throws Exception {
		ResultSet rows = mock(ResultSet.class); when(statement.executeQuery()).thenReturn(rows);
		when(rows.next()).thenReturn(true); when(rows.getInt(1)).thenReturn(5, 0);
		when(statement.executeUpdate()).thenReturn(1);
		repository.insert(record); verify(connection).commit();
		setup(); rows = mock(ResultSet.class); when(statement.executeQuery()).thenReturn(rows);
		when(rows.next()).thenReturn(true); when(rows.getInt(1)).thenReturn(6);
		assertThrows(SQLException.class, () -> repository.insert(record)); verify(statement, never()).executeUpdate();
	}
}
