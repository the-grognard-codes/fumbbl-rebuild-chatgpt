package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.db.DbConnectionManager;
import com.fumbbl.ffb.server.db.DbUpdateFactory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalDatabaseCloseTest {
	@Test
	void localCloseReleasesConnectionWithoutShuttingDownMariaDb() throws Exception {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		DbConnectionManager manager = mock(DbConnectionManager.class);
		when(manager.getServer()).thenReturn(server);
		when(manager.isStandalone()).thenReturn(true);
		when(server.getProperty("server.local")).thenReturn("true");
		// With no opened connection, an attempted SQL SHUTDOWN would also fail this test.
		new DbUpdateFactory(manager).closeDbConnection();
		verify(manager).closeDbConnection(null);
		verify(manager, never()).openDbConnection();
	}
}
