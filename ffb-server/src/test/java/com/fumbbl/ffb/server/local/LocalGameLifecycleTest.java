package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.RulesCollection;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.GameOptions;
import com.fumbbl.ffb.option.GameOptionId;
import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalGameLifecycleTest {

	@Test
	void isLocalReadsServerLocalProfileProperty() {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		when(server.getProperty(LocalGameLifecycle.SERVER_LOCAL_PROPERTY)).thenReturn("true");

		assertTrue(new LocalGameLifecycle().isLocal(server));
	}

	@Test
	void configureOptionsUsesBb2025Rules() {
		GameState gameState = mock(GameState.class);
		Game game = mock(Game.class);
		GameOptions options = new GameOptions(game);
		when(gameState.getGame()).thenReturn(game);
		when(game.getOptions()).thenReturn(options);

		new LocalGameLifecycle().configureOptions(gameState);

		assertEquals(RulesCollection.Rules.BB2025.name(),
			options.getOptionWithDefault(GameOptionId.RULESVERSION).getValueAsString());
	}
}
