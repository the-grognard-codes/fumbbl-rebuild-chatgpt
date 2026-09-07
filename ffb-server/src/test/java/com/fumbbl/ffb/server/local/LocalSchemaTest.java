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
	void restartWithVersionOneDoesNotSeedOrResetData() throws Exception {
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
		when(version.getInt(1)).thenReturn(1);
		new LocalSchema().initialize(manager, "unused");
		verify(statement, never()).executeUpdate(anyString());
	}
}
