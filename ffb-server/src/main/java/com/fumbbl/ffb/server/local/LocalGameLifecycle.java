package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.RulesCollection;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.option.GameOptionId;
import com.fumbbl.ffb.option.GameOptionString;
import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.GameState;

/**
 * Configures games created by the local development server profile.
 */
public class LocalGameLifecycle {

	public static final String SERVER_LOCAL_PROPERTY = "server.local";

	public boolean isLocal(FantasyFootballServer server) {
		return server != null && Boolean.parseBoolean(server.getProperty(SERVER_LOCAL_PROPERTY));
	}

	public void configureOptions(GameState gameState) {
		Game game = gameState.getGame();
		GameOptionString rulesVersion = new GameOptionString(GameOptionId.RULESVERSION);
		rulesVersion.setValue(RulesCollection.Rules.BB2025.name());
		game.getOptions().addOption(rulesVersion);
	}
}
