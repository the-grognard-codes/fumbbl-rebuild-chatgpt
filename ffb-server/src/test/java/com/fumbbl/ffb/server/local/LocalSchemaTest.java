package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.db.DbConnectionManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.startsWith;

class LocalSchemaTest {
	@Test
	void refusesUnversionedNonemptyDatabaseWithoutWriting() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class);
		Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables);
		when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenThrow(new SQLException("Missing version table"));
		assertThrows(SQLException.class, () -> new LocalSchema().initialize(manager, "unused"));
		verify(statement, never()).executeUpdate(anyString());
	}

	@Test
	void restartWithVersionFiveDoesNotSeedOrResetData() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class);
		Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class);
		ResultSet version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables);
		when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false);
		when(version.getInt(1)).thenReturn(5);
		LocalSchema schema = spy(new LocalSchema());
		doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyPreparedMatches(connection); doNothing().when(schema).verifyCompletedMatches(connection); doNothing().when(schema).verifyRecovery(connection);
        when(statement.executeUpdate("UPDATE ffb_local_schema SET version=4 WHERE version=3")).thenReturn(1);
		schema.initialize(manager, "unused");
		verify(schema).verifySavedTeams(connection);
		verify(statement, never()).executeUpdate(anyString());
	}

	@Test
	void versionFourCreatesAndVerifiesRecoveryBeforeAdvancingMarker() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(4);
		when(statement.executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4")).thenReturn(1);
		LocalSchema schema = spy(new LocalSchema());
		doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyPreparedMatches(connection); doNothing().when(schema).verifyCompletedMatches(connection); doNothing().when(schema).verifyRecovery(connection);
		schema.initialize(manager, "unused");
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(statement, schema, connection);
		order.verify(statement).executeUpdate(startsWith("CREATE TABLE IF NOT EXISTS ffb_match_recovery"));
		order.verify(schema).verifyRecovery(connection);
		order.verify(statement).executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4");
		order.verify(connection).commit();
	}

	@Test
	void corruptRecoveryTableCannotAdvanceVersionFour() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(4);
		LocalSchema schema = spy(new LocalSchema());
		doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyPreparedMatches(connection); doNothing().when(schema).verifyCompletedMatches(connection);
		doThrow(new SQLException("corrupt recovery table")).when(schema).verifyRecovery(connection);
		assertThrows(SQLException.class, () -> schema.initialize(manager, "unused"));
		verify(statement, never()).executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4");
	}

	@Test
	void corruptVersionFiveRecoveryTableFailsClosed() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(5);
		LocalSchema schema = spy(new LocalSchema());
		doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyCompletedMatches(connection);
		doThrow(new SQLException("corrupt recovery table")).when(schema).verifyRecovery(connection);
		assertThrows(SQLException.class, () -> schema.initialize(manager, "unused"));
		verify(statement, never()).executeUpdate(anyString());
	}

	@Test
	void unknownSchemaVersionFailsClosed() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(6);
		assertThrows(SQLException.class, () -> new LocalSchema().initialize(manager, "unused"));
		verify(statement, never()).executeUpdate(anyString());
	}

    @Test void completedMigrationResumesAfterAlterWithoutRepeatingDdl() throws Exception {
        DbConnectionManager manager = mock(DbConnectionManager.class);
        Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
        ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
        when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
        when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
        when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(3);
        when(statement.executeUpdate("UPDATE ffb_local_schema SET version=4 WHERE version=3")).thenReturn(1);
		when(statement.executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4")).thenReturn(1);
        LocalSchema schema = spy(new LocalSchema());
		doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyCompletedMatches(connection); doNothing().when(schema).verifyRecovery(connection);
        schema.initialize(manager, "unused");
        verify(statement, never()).executeUpdate(startsWith("ALTER"));
        verify(schema, never()).verifyPreparedMatches(connection);
        verify(statement).executeUpdate("UPDATE ffb_local_schema SET version=4 WHERE version=3");
    }

    @Test void incompatibleCompletionTableCannotAdvanceSchemaThree() throws Exception {
        DbConnectionManager manager = mock(DbConnectionManager.class);
        Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
        ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
        when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
        when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
        when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(3);
        LocalSchema schema = spy(new LocalSchema()); doNothing().when(schema).verifySavedTeams(connection);
        doThrow(new SQLException("not v4")).when(schema).verifyCompletedMatches(connection);
        doThrow(new SQLException("not v3")).when(schema).verifyPreparedMatches(connection);
        assertThrows(SQLException.class, () -> schema.initialize(manager, "unused"));
        verify(statement, never()).executeUpdate(anyString());
    }

	@Test
	void migrationResumesOnlyAfterVerifyingExistingTableAndNeverDropsData() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(1);
		when(statement.executeUpdate("UPDATE ffb_local_schema SET version=2 WHERE version=1")).thenReturn(1);
        when(statement.executeUpdate("UPDATE ffb_local_schema SET version=3 WHERE version=2")).thenReturn(1);
		LocalSchema schema = spy(new LocalSchema()); doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyPreparedMatches(connection); doNothing().when(schema).verifyCompletedMatches(connection); doNothing().when(schema).verifyRecovery(connection);
        when(statement.executeUpdate("UPDATE ffb_local_schema SET version=4 WHERE version=3")).thenReturn(1);
		when(statement.executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4")).thenReturn(1);
		schema.initialize(manager, "unused");
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(statement, schema, connection);
		order.verify(statement).executeUpdate(startsWith("CREATE TABLE IF NOT EXISTS ffb_saved_teams"));
		order.verify(schema).verifySavedTeams(connection);
		order.verify(statement).executeUpdate("UPDATE ffb_local_schema SET version=2 WHERE version=1");
		order.verify(connection).commit();
		verify(statement, never()).executeUpdate(startsWith("DROP"));
	}
	@Test
	void versionTwoResumesMatchMigrationWithoutRewritingSavedTeams() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(2);
		when(statement.executeUpdate("UPDATE ffb_local_schema SET version=3 WHERE version=2")).thenReturn(1);
		LocalSchema schema = spy(new LocalSchema());
		doNothing().when(schema).verifySavedTeams(connection); doNothing().when(schema).verifyPreparedMatches(connection); doNothing().when(schema).verifyCompletedMatches(connection); doNothing().when(schema).verifyRecovery(connection);
        when(statement.executeUpdate("UPDATE ffb_local_schema SET version=4 WHERE version=3")).thenReturn(1);
		when(statement.executeUpdate("UPDATE ffb_local_schema SET version=5 WHERE version=4")).thenReturn(1);
		schema.initialize(manager, "unused");
		org.mockito.InOrder order = org.mockito.Mockito.inOrder(statement, schema, connection);
		order.verify(schema).verifySavedTeams(connection);
		order.verify(statement).executeUpdate(startsWith("CREATE TABLE IF NOT EXISTS ffb_prepared_matches"));
		order.verify(schema).verifyPreparedMatches(connection);
		order.verify(statement).executeUpdate("UPDATE ffb_local_schema SET version=3 WHERE version=2");
		order.verify(connection).commit();
		verify(statement, never()).executeUpdate(startsWith("DROP"));
		verify(statement, never()).executeUpdate("UPDATE ffb_local_schema SET version=2 WHERE version=1");
	}

	@Test
	void incompatibleMatchTableDoesNotAdvanceVersionTwo() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(2);
		LocalSchema schema = spy(new LocalSchema()); doNothing().when(schema).verifySavedTeams(connection);
		doThrow(new SQLException("mismatch")).when(schema).verifyPreparedMatches(connection);
		assertThrows(SQLException.class, () -> schema.initialize(manager, "unused"));
		verify(statement, never()).executeUpdate("UPDATE ffb_local_schema SET version=3 WHERE version=2");
		verify(connection, never()).commit();
	}

	@Test
	void schemaMismatchCannotAdvanceMigrationVersion() throws Exception {
		DbConnectionManager manager = mock(DbConnectionManager.class);
		Connection connection = mock(Connection.class); Statement statement = mock(Statement.class);
		ResultSet tables = mock(ResultSet.class), version = mock(ResultSet.class);
		when(manager.openDbConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery("SHOW TABLES")).thenReturn(tables); when(tables.next()).thenReturn(true);
		when(statement.executeQuery("SELECT version FROM ffb_local_schema")).thenReturn(version);
		when(version.next()).thenReturn(true, false); when(version.getInt(1)).thenReturn(1);
		LocalSchema schema = spy(new LocalSchema()); doThrow(new SQLException("mismatch")).when(schema).verifySavedTeams(connection);
		assertThrows(SQLException.class, () -> schema.initialize(manager, "unused"));
		verify(statement, never()).executeUpdate("UPDATE ffb_local_schema SET version=2 WHERE version=1");
	}
}
