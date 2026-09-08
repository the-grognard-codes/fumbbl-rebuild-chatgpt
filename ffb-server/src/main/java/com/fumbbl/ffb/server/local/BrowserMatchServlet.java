package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.FantasyFootballServer;

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

public class BrowserMatchServlet extends WebSocketServlet implements WebSocketCreator {
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
	public void configure(WebSocketServletFactory factory) {
		factory.getPolicy().setAsyncWriteTimeout(2000);
		factory.setCreator(this);
		if ("/tmp/ffb-browser-control".equals(server.getProperty("local.browser.control.file"))) {
			fixtureControl = new BrowserFixtureControl(server, adapter, this);
			fixtureControl.start();
		}
	}

	@Override
	public Object createWebSocket(ServletUpgradeRequest request, ServletUpgradeResponse response) {
		String origin = request.getHeader("Origin");
		if (!"http://127.0.0.1:5173".equals(origin) && !"http://localhost:5173".equals(origin)) {
			response.setSuccess(false);
			return null;
		}
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
