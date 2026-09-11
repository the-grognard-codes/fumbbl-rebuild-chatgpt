package com.fumbbl.ffb.server.match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

class JdbcRecoveryRepositoryTest {
	private Connection connection;
	private PreparedStatement statement;
	private JdbcRecoveryRepository repository;
	private RecoveryRepository.Record record(long generation) { return new RecoveryRepository.Record("match", generation, "staged recovery artifact"); }

	@BeforeEach
	void setup() throws Exception {
		connection = mock(Connection.class); statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		repository = new JdbcRecoveryRepository(() -> connection);
	}

	@Test
	void initialSaveInsertsGenerationOneAndCommits() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		assertTrue(repository.save(record(1), 0));
		verify(statement).setLong(2, 1); verify(connection).commit(); verify(connection, never()).rollback();
	}

	@Test
	void compareAndSwapAdvancesTheExpectedGeneration() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		assertTrue(repository.save(record(8), 7));
		verify(statement).setLong(1, 8); verify(statement).setString(3, "match"); verify(statement).setLong(4, 7);
		verify(connection).commit();
	}

	@Test
	void duplicateOrStaleSaveRollsBackWithoutCommit() throws Exception {
		when(statement.executeUpdate()).thenReturn(0);
		assertFalse(repository.save(record(1), 0));
		verify(connection).rollback(); verify(connection, never()).commit();
	}

	@Test
	void commitAcknowledgementFailureIsOutcomeUnknown() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		doThrow(new SQLException("lost acknowledgement")).when(connection).commit();
		RecoveryRepository.Record record = record(8);
		RecoveryRepository.OutcomeUnknown failure = assertThrows(RecoveryRepository.OutcomeUnknown.class, () -> repository.save(record, 7));
		org.junit.jupiter.api.Assertions.assertSame(record, failure.attempted);
		verify(connection).rollback(); verify(connection).close();
	}

	@Test
	void closeFailureAfterCommitIsOutcomeUnknown() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		doThrow(new SQLException("close failure")).when(connection).close();
		assertThrows(RecoveryRepository.OutcomeUnknown.class, () -> repository.save(record(8), 7));
		verify(connection).commit();
	}

	@Test
	void overflowingGenerationIsRejected() {
		assertThrows(SQLException.class, () -> repository.save(record(1), Long.MAX_VALUE));
	}

	@Test
	void duplicateInsertConflictRollsBackAndReturnsFalse() throws Exception {
		when(statement.executeUpdate()).thenThrow(new SQLException("duplicate", "23000", 1062));
		assertFalse(repository.save(record(1), 0));
		verify(connection).rollback(); verify(connection, never()).commit();
	}

	@Test
	void mismatchedRecordGenerationIsRejectedBeforeWriting() throws Exception {
		assertThrows(SQLException.class, () -> repository.save(record(2), 0));
		verify(connection, never()).setAutoCommit(false);
	}
}
