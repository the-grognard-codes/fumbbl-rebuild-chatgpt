package com.fumbbl.ffb.server.net;

import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServletFactory;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.IServerProperty;
import com.fumbbl.ffb.util.StringTool;

/**
 * 
 * @author Kalimar
 */
public class CommandServlet extends JettyWebSocketServlet implements JettyWebSocketCreator {

	private FantasyFootballServer fServer;

	public CommandServlet(FantasyFootballServer pServer) {
		fServer = pServer;
	}

	@Override
	public void configure(JettyWebSocketServletFactory factory) {
		factory.setIdleTimeout(java.time.Duration.ofSeconds(10));
		factory.setCreator(this);
	}

	public Object createWebSocket(JettyServerUpgradeRequest pRequest, JettyServerUpgradeResponse pResponse) {
		String commandCompressionProperty = null;
		if (fServer != null) {
			commandCompressionProperty = fServer.getProperty(IServerProperty.SERVER_COMMAND_COMPRESSION);
		}
		boolean commandCompression = false;
		if (StringTool.isProvided(commandCompressionProperty)) {
			commandCompression = Boolean.parseBoolean(commandCompressionProperty);
		}
		return new CommandSocket(fServer, commandCompression);
	}

}