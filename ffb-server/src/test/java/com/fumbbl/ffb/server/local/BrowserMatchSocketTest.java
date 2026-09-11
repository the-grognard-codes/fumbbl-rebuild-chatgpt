package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.net.ServerCommunication;
import org.eclipse.jetty.ee8.websocket.api.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BrowserMatchSocketTest {
	@Test
	public void queuedBrowserMessageCannotReachAdapterAfterFixtureResetRetiresSocket() {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		BrowserMatchAdapter adapter = mock(BrowserMatchAdapter.class);
		ServerCommunication communication = mock(ServerCommunication.class);
		Session session = mock(Session.class);
		List<Runnable> queuedWork = new ArrayList<>();
		when(server.getCommunication()).thenReturn(communication);
		when(session.isOpen()).thenReturn(true);
		when(communication.execute(any(Runnable.class))).thenAnswer(invocation -> {
			queuedWork.add(invocation.getArgument(0));
			return true;
		});
		BrowserMatchTransport transport = new BrowserMatchTransport(server);
		BrowserMatchSocket socket = new BrowserMatchSocket(server, adapter, transport);

		socket.onConnect(session);
		socket.onText("{\"type\":\"join\"}");
		socket.closeFromEngineWorker();
		queuedWork.get(0).run();
		socket.onClose(1000, "closed");

		verify(adapter, never()).receive(any(BrowserMatchAdapter.Connection.class), any(String.class));
		verify(adapter, times(1)).disconnect(socket);
		verify(session, times(1)).close(eq(1012), any(String.class));
		transport.destroy();
	}

	@Test
	public void failedConnectionSetupReleasesAdmissionWithoutJettyCloseCallback() {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		BrowserMatchAdapter adapter = mock(BrowserMatchAdapter.class);
		ServerCommunication communication = mock(ServerCommunication.class);
		when(server.getCommunication()).thenReturn(communication);
		when(communication.execute(any(Runnable.class))).thenAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return true;
		});
		BrowserMatchTransport transport = new BrowserMatchTransport(server);
		for (int index = 0; index < 32; index++) {
			Session broken = mock(Session.class);
			when(broken.isOpen()).thenReturn(true);
			doThrow(new IllegalStateException("closed during setup")).when(broken).setIdleTimeout(java.time.Duration.ofMinutes(5));
			new BrowserMatchSocket(server, adapter, transport).onConnect(broken);
		}
		assertEquals(0, transport.getMetrics().toJson().getLong("activeConnections", -1));
		Session healthy = mock(Session.class);
		when(healthy.isOpen()).thenReturn(true);
		new BrowserMatchSocket(server, adapter, transport).onConnect(healthy);
		assertEquals(1, transport.getMetrics().toJson().getLong("activeConnections", -1));
		assertEquals(0, transport.getMetrics().toJson().getLong("admissionRejected", -1));
		transport.destroy();
	}

	@Test
	public void unadmittedSocketIgnoresFrames() {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		BrowserMatchAdapter adapter = mock(BrowserMatchAdapter.class);
		ServerCommunication communication = mock(ServerCommunication.class);
		Session session = mock(Session.class);
		BrowserMatchTransport transport = new BrowserMatchTransport(server);
		when(server.getCommunication()).thenReturn(communication);
		when(session.isOpen()).thenReturn(true);
		for (int index = 0; index < BrowserMatchTransport.MAX_CONNECTIONS; index++) transport.admitConnection();
		BrowserMatchSocket socket = new BrowserMatchSocket(server, adapter, transport);

		socket.onConnect(session);
		socket.onText("{\"type\":\"join\"}");
		socket.onClose(1000, "closed");

		verify(session).close(eq(1013), any(String.class));
		verify(communication, never()).execute(any(Runnable.class));
		transport.destroy();
	}
}
