package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.FantasyFootballServer;

import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServletFactory;

public class BrowserMatchServlet extends JettyWebSocketServlet implements JettyWebSocketCreator {
	private final BrowserMatchAdapter adapter;
	private final FantasyFootballServer server;
	private final BrowserMatchTransport transport;
	private BrowserFixtureControl fixtureControl;

	public BrowserMatchServlet(FantasyFootballServer server, BrowserMatchAdapter adapter) {
		this.server = server;
		this.adapter = adapter;
		this.transport = new BrowserMatchTransport(server);
	}

	@Override
	public void configure(JettyWebSocketServletFactory factory) {
		// The existing delivery watchdog bounds asynchronous writes to two seconds.
		factory.setCreator(this);
		if ("/tmp/ffb-browser-control".equals(server.getProperty("local.browser.control.file"))) {
			fixtureControl = new BrowserFixtureControl(server, adapter, this);
			fixtureControl.start();
		}
	}

	@Override
	public Object createWebSocket(JettyServerUpgradeRequest request, JettyServerUpgradeResponse response) {
		String origin = request.getHeader("Origin");
		if (request.getHeaders("Origin").size() != 1
			|| (!"http://127.0.0.1:5173".equals(origin) && !"http://localhost:5173".equals(origin))) {
			response.setStatusCode(403);
			return null;
		}
		// Version 1 is uncompressed text JSON; no negotiated decoding expansion.
		response.setExtensions(java.util.Collections.emptyList());
		return new BrowserMatchSocket(server, adapter, transport);
	}

	public JsonObject getTransportMetrics() { return transport.getMetrics().toJson(); }

	public void closeConnections() { transport.closeConnections(); }

	@Override
	public void destroy() {
		if (fixtureControl != null) fixtureControl.close();
		transport.destroy();
		super.destroy();
	}
}
