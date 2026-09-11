package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.FantasyFootballServer;

import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.api.WriteCallback;
import org.eclipse.jetty.ee8.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.ee8.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.ee8.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.ee8.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.ee8.websocket.api.annotations.WebSocket;

import java.util.concurrent.atomic.AtomicBoolean;

@WebSocket(maxTextMessageSize = 16 * 1024)
public class BrowserMatchSocket implements BrowserMatchAdapter.Connection {
	private final BrowserMatchAdapter adapter;
	private final FantasyFootballServer server;
	private final BrowserMatchTransport transport;
	private final AtomicBoolean active = new AtomicBoolean();
	private volatile BrowserMatchDelivery delivery;
	private volatile Session session;

	public BrowserMatchSocket(FantasyFootballServer server, BrowserMatchAdapter adapter, BrowserMatchTransport transport) {
		this.server = server;
		this.adapter = adapter;
		this.transport = transport;
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		this.session = session;
		if (!transport.admitConnection()) {
			session.close(1013, "Local browser connection limit reached");
			return;
		}
		active.set(true);
		if (!transport.connected(this)) {
			active.set(false);
			session.close(1012, "Local browser transport stopping");
			return;
		}
		try {
			session.setIdleTimeout(java.time.Duration.ofMinutes(5));
			delivery = new BrowserMatchDelivery(new SessionSink(session), transport.getMetrics(),
				BrowserMatchTransport.MAX_OUTBOUND_MESSAGES, BrowserMatchTransport.MAX_OUTBOUND_BYTES);
		} catch (RuntimeException exception) {
			retire(1011, "Local browser connection setup failed");
		}
	}

	@OnWebSocketMessage
	public void onText(String message) {
		if (!active.get() || session == null || !session.isOpen()) return;
		transport.submit(() -> {
			if (!active.get() || !session.isOpen()) return;
			try {
				adapter.receive(this, message);
			} catch (RuntimeException exception) {
				server.getDebugLog().log(-1, exception);
				retireFromEngineWorker(1011, "Local fixture unavailable; operator restart required");
			}
		}, () -> retire(1013, "Local browser transport overloaded"));
	}

	@OnWebSocketClose
	public void onClose(int statusCode, String reason) {
		boolean wasActive = active.getAndSet(false);
		BrowserMatchDelivery activeDelivery = delivery;
		if (activeDelivery != null) activeDelivery.close();
		if (wasActive) {
			transport.disconnect(this, () -> adapter.disconnect(this));
		}
	}

	@OnWebSocketMessage
	public void onBinary(byte[] bytes, int offset, int length) {
		retire(1003, "Browser protocol requires text JSON");
	}

	@OnWebSocketError
	public void onError(Throwable failure) {
		// Jetty may report a failure before its close notification. Release once.
		retire(1011, "Browser transport failed; reconnect for a full snapshot");
	}

	@Override
	public void send(String message) {
		if (!active.get()) return;
		BrowserMatchDelivery activeDelivery = delivery;
		if (activeDelivery != null) activeDelivery.send(message);
	}

	void closeFromEngineWorker() {
		retireFromEngineWorker(1012, "Local fixture reset; reconnect for a full snapshot");
	}

	private void retireFromEngineWorker(int statusCode, String reason) {
		if (active.getAndSet(false)) transport.disconnectFromEngineWorker(this, () -> adapter.disconnect(this));
		BrowserMatchDelivery activeDelivery = delivery;
		if (activeDelivery != null) activeDelivery.close();
		closeSession(statusCode, reason);
	}

	void expireDelivery(long now) {
		BrowserMatchDelivery activeDelivery = delivery;
		if (activeDelivery != null) activeDelivery.expireIfStalled(now, BrowserMatchTransport.OUTBOUND_WRITE_TIMEOUT_MS);
	}

	private void retire(int statusCode, String reason) {
		if (active.getAndSet(false)) transport.disconnect(this, () -> adapter.disconnect(this));
		BrowserMatchDelivery activeDelivery = delivery;
		if (activeDelivery != null) activeDelivery.close();
		closeSession(statusCode, reason);
	}

	private void closeSession(int statusCode, String reason) {
		Session activeSession = session;
		if (activeSession != null && activeSession.isOpen()) activeSession.close(statusCode, reason);
	}

	private class SessionSink implements BrowserMatchDelivery.Sink {
		private final Session session;
		private SessionSink(Session session) { this.session = session; }
		@Override public boolean isOpen() { return session.isOpen(); }
		@Override public void close(int statusCode, String reason) { retire(statusCode, reason); }
		@Override public void send(String message, BrowserMatchDelivery.Completion completion) {
			session.getRemote().sendString(message, new WriteCallback() {
				@Override public void writeFailed(Throwable failure) { completion.failed(failure); }
				@Override public void writeSuccess() { completion.succeeded(); }
			});
		}
	}
}
