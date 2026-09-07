package com.fumbbl.ffb.server.admin;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.GameCache;
import com.fumbbl.ffb.server.IServerProperty;
import com.fumbbl.ffb.server.local.LocalGameLifecycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UtilBackupTest {

	@Test
	void loadGameStateDoesNotFallBackToS3ForLocalServer() {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		GameCache gameCache = mock(GameCache.class);
		when(server.getProperty(LocalGameLifecycle.SERVER_LOCAL_PROPERTY)).thenReturn("true");
		when(server.getProperty(IServerProperty.BACKUP_DIR)).thenReturn("target/local-backups-that-do-not-exist");
		when(server.getProperty(IServerProperty.BACKUP_EXTENSION)).thenReturn("gz");
		when(server.getGameCache()).thenReturn(gameCache);

		assertNull(UtilBackup.loadGameState(42L, server));

		verify(gameCache).queryFromDb(42L);
	}
}
