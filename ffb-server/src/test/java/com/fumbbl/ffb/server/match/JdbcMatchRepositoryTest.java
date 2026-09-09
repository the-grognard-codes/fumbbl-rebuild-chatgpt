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

class JdbcMatchRepositoryTest {
	private Connection connection;
	private PreparedStatement statement;
	private JdbcMatchRepository repository;
	private final MatchRepository.Record record = new MatchRepository.Record("match", 2, "complete snapshot and retry metadata");

	@BeforeEach
	void setup() throws Exception {
		connection = mock(Connection.class); statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		repository = new JdbcMatchRepository(() -> connection);
	}

	@Test
	void compareAndSwapCommitsCompleteDocumentOnce() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		assertTrue(repository.replace(record, 1));
		verify(statement).setString(2, record.json); verify(statement).setString(3, "match"); verify(statement).setInt(4, 1);
		verify(connection).commit(); verify(connection, never()).rollback(); verify(connection).close();
	}

	@Test
	void losingCompareAndSwapRollsBackWithoutCommit() throws Exception {
		assertFalse(repository.replace(record, 1));
		verify(connection).rollback(); verify(connection, never()).commit();
	}

	@Test
	void insertFailureRollsBackWithoutAcknowledgingMembership() throws Exception {
		when(statement.executeUpdate()).thenThrow(new SQLException("injected write failure"));
		assertThrows(SQLException.class, () -> repository.insert(record));
		verify(connection).rollback(); verify(connection, never()).commit(); verify(connection).close();
	}

	@Test
	void commitAcknowledgementFailureIsUnknownAndRetainsAttemptedLocator() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		doThrow(new SQLException("injected lost acknowledgement")).when(connection).commit();
		MatchRepository.OutcomeUnknown failure = assertThrows(MatchRepository.OutcomeUnknown.class, () -> repository.insert(record));
		org.junit.jupiter.api.Assertions.assertSame(record, failure.attempted);
		verify(connection).rollback(); verify(connection).close();
	}

	@Test
	void closeFailureAfterCommitIsConservativelyUnknown() throws Exception {
		when(statement.executeUpdate()).thenReturn(1);
		doThrow(new SQLException("injected close failure")).when(connection).close();
		assertThrows(MatchRepository.OutcomeUnknown.class, () -> repository.replace(record, 1));
		verify(connection).commit();
	}
}
