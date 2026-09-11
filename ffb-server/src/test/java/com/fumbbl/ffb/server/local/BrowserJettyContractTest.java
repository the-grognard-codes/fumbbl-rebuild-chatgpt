package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.net.ServerCommunication;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises the EE8 Jetty endpoint over a loopback WebSocket, not mocked frames. */
public class BrowserJettyContractTest {
	private BrowserMatchAdapter adapter;
	private ExecutorService communicationExecutor;
	private Server jetty;
	private BrowserMatchServlet servlet;
	private int port;

	@BeforeEach
	public void startJetty() throws Exception {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		ServerCommunication communication = mock(ServerCommunication.class);
		adapter = mock(BrowserMatchAdapter.class);
		communicationExecutor = Executors.newSingleThreadExecutor();
		when(server.getCommunication()).thenReturn(communication);
		when(server.getProperty(anyString())).thenReturn(null);
		when(communication.execute(any(Runnable.class))).thenAnswer(invocation -> {
			communicationExecutor.execute(invocation.getArgument(0));
			return true;
		});

		jetty = new Server();
		ServerConnector connector = new ServerConnector(jetty);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		jetty.addConnector(connector);
		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		JettyWebSocketServletContainerInitializer.configure(context, null);
		servlet = new BrowserMatchServlet(server, adapter);
		context.addServlet(new ServletHolder(servlet), "/browser/v1/*");
		jetty.setHandler(context);
		jetty.start();
		port = connector.getLocalPort();
	}

	@AfterEach
	public void stopJetty() throws Exception {
		if (jetty != null) jetty.stop();
		if (communicationExecutor != null) {
			communicationExecutor.shutdownNow();
			communicationExecutor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	@Test
	public void allowedOriginsAcceptFragmentedTextDisableExtensionsAndDeliverCallback() throws Exception {
		for (String origin : new String[]{"http://127.0.0.1:5173", "http://localhost:5173"}) {
			CountDownLatch received = new CountDownLatch(1);
			CountDownLatch disconnected = new CountDownLatch(1);
			AtomicReference<BrowserMatchAdapter.Connection> connection = new AtomicReference<>();
			doAnswer(invocation -> {
				connection.set(invocation.getArgument(0));
				received.countDown();
				return null;
			}).when(adapter).receive(any(BrowserMatchAdapter.Connection.class), anyString());
			doAnswer(invocation -> {
				disconnected.countDown();
				return null;
			}).when(adapter).disconnect(any(BrowserMatchAdapter.Connection.class));

			try (RawWebSocket socket = open(origin, "Sec-WebSocket-Extensions: permessage-deflate")) {
				assertFalse(socket.handshake.toLowerCase().contains("sec-websocket-extensions:"));
				socket.sendFrame(0x1, false, false, true, "{\"type\":\"".getBytes(StandardCharsets.UTF_8));
				socket.sendFrame(0x0, true, false, true, "join\"}".getBytes(StandardCharsets.UTF_8));
				assertTrue(received.await(5, TimeUnit.SECONDS));
				assertNotNull(connection.get());
				connection.get().send("callback");
				assertEquals("callback", socket.readFrame().text());
				socket.sendFrame(0x8, true, false, true, new byte[]{0x03, (byte) 0xe8});
				assertEquals(0x8, socket.readFrame().opcode);
				assertTrue(disconnected.await(5, TimeUnit.SECONDS));
			}
		}
	}

	@Test
	public void rejectsMissingNullForeignAndMultipleOrigins() throws Exception {
		List<String[]> origins = new ArrayList<>();
		origins.add(new String[0]);
		origins.add(new String[]{""});
		origins.add(new String[]{"null"});
		origins.add(new String[]{"https://example.invalid"});
		origins.add(new String[]{"http://127.0.0.1:5173", "https://example.invalid"});
		for (String[] candidate : origins) {
			try (RawWebSocket socket = open(candidate)) {
				assertTrue(socket.handshake.startsWith("HTTP/1.1 403"), socket.handshake);
			}
		}
	}

	@Test
	public void setsBrowserMessageIdleTimeoutAndCleansUpAfterAnIdleClose() throws Exception {
		CountDownLatch received = new CountDownLatch(1);
		AtomicReference<BrowserMatchAdapter.Connection> connection = new AtomicReference<>();
		doAnswer(invocation -> {
			connection.set(invocation.getArgument(0));
			received.countDown();
			return null;
		}).when(adapter).receive(any(BrowserMatchAdapter.Connection.class), anyString());
		try (RawWebSocket socket = open("http://127.0.0.1:5173")) {
			socket.sendFrame(0x1, true, false, true, "{}".getBytes(StandardCharsets.UTF_8));
			assertTrue(received.await(5, TimeUnit.SECONDS));
			Session session = session(connection.get());
			assertEquals(Duration.ofMinutes(5), session.getIdleTimeout());
			// Keep production's five-minute contract, but shorten this real session so
			// the test verifies Jetty's idle close and adapter cleanup promptly.
			session.setIdleTimeout(Duration.ofMillis(100));
			assertEquals(0x8, socket.readFrame().opcode);
		}
		assertEventuallyNoActiveConnections();
	}

	@Test
	public void rejectsBinaryAndProtocolInvalidFrames() throws Exception {
		assertCloseCode(frame(0x2, true, false, true, new byte[]{1}), 1003);
		assertCloseCode(frame(0x1, true, false, false, "text".getBytes(StandardCharsets.UTF_8)), 1002);
		assertCloseCode(frame(0x1, true, true, true, "text".getBytes(StandardCharsets.UTF_8)), 1002);
		assertCloseCode(frame(0x1, true, false, true, new byte[]{(byte) 0xc3, 0x28}), 1007);
	}

	@Test
	public void enforcesDecodedUtf8LimitAcrossFragments() throws Exception {
		CountDownLatch received = new CountDownLatch(1);
		doAnswer(invocation -> {
			received.countDown();
			return null;
		}).when(adapter).receive(any(BrowserMatchAdapter.Connection.class), anyString());
		byte[] exactlySixteenKiB = (repeat("€", 5461) + "a").getBytes(StandardCharsets.UTF_8);
		assertEquals(16 * 1024, exactlySixteenKiB.length);
		try (RawWebSocket socket = open("http://127.0.0.1:5173")) {
			socket.sendFrame(0x1, false, false, true, slice(exactlySixteenKiB, 0, 8191));
			socket.sendFrame(0x0, true, false, true, slice(exactlySixteenKiB, 8191, exactlySixteenKiB.length));
			assertTrue(received.await(5, TimeUnit.SECONDS));
		}
		byte[] tooLarge = (repeat("€", 5461) + "ab").getBytes(StandardCharsets.UTF_8);
		assertEquals(16 * 1024 + 1, tooLarge.length);
		assertCloseCode(frame(0x1, false, false, true, slice(tooLarge, 0, 8191)),
			frame(0x0, true, false, true, slice(tooLarge, 8191, tooLarge.length)), 1009);
	}

	private void assertCloseCode(Frame... frames) throws Exception {
		try (RawWebSocket socket = open("http://127.0.0.1:5173")) {
			for (Frame frame : frames) socket.sendFrame(frame.opcode, frame.fin, frame.rsv, frame.masked, frame.payload);
			Frame close = socket.readFrame();
			assertEquals(0x8, close.opcode);
			assertTrue(close.payload.length >= 2);
			int closeCode = ((close.payload[0] & 0xff) << 8) | (close.payload[1] & 0xff);
			assertEquals(frames[frames.length - 1].expectedCloseCode, closeCode);
		}
		assertEventuallyNoActiveConnections();
	}

	private Session session(BrowserMatchAdapter.Connection connection) throws Exception {
		java.lang.reflect.Field field = BrowserMatchSocket.class.getDeclaredField("session");
		field.setAccessible(true);
		return (Session) field.get(connection);
	}

	private void assertEventuallyNoActiveConnections() throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (servlet.getTransportMetrics().getLong("activeConnections", -1) != 0 && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
		assertEquals(0, servlet.getTransportMetrics().getLong("activeConnections", -1));
	}

	private Frame frame(int opcode, boolean fin, boolean rsv, boolean masked, byte[] payload) {
		return new Frame(opcode, fin, rsv, masked, payload, 0);
	}

	private Frame frame(int opcode, boolean fin, boolean rsv, boolean masked, byte[] payload, int expectedCloseCode) {
		return new Frame(opcode, fin, rsv, masked, payload, expectedCloseCode);
	}

	private void assertCloseCode(Frame frame, int expectedCloseCode) throws Exception {
		frame.expectedCloseCode = expectedCloseCode;
		assertCloseCode(new Frame[]{frame});
	}

	private void assertCloseCode(Frame first, Frame second, int expectedCloseCode) throws Exception {
		second.expectedCloseCode = expectedCloseCode;
		assertCloseCode(first, second);
	}

	private RawWebSocket open(String origin, String... extraHeaders) throws IOException {
		return open(new String[]{origin}, extraHeaders);
	}

	private RawWebSocket open(String[] origins, String... extraHeaders) throws IOException {
		Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);
		socket.setSoTimeout(5000);
		String key = Base64.getEncoder().encodeToString(new byte[16]);
		StringBuilder request = new StringBuilder("GET /browser/v1 HTTP/1.1\r\nHost: 127.0.0.1:")
			.append(port).append("\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: ")
			.append(key).append("\r\n");
		for (String origin : origins) request.append("Origin: ").append(origin).append("\r\n");
		for (String header : extraHeaders) request.append(header).append("\r\n");
		request.append("\r\n");
		socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
		socket.getOutputStream().flush();
		return new RawWebSocket(socket, readHttpHeader(socket.getInputStream()));
	}

	private static String readHttpHeader(InputStream input) throws IOException {
		ByteArrayOutputStream response = new ByteArrayOutputStream();
		int matched = 0;
		while (matched < 4) {
			int value = input.read();
			if (value < 0) break;
			response.write(value);
			matched = value == "\r\n\r\n".charAt(matched) ? matched + 1 : value == '\r' ? 1 : 0;
		}
		return new String(response.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	private static byte[] slice(byte[] bytes, int from, int to) {
		byte[] result = new byte[to - from];
		System.arraycopy(bytes, from, result, 0, result.length);
		return result;
	}

	private static String repeat(String value, int count) {
		StringBuilder result = new StringBuilder(value.length() * count);
		for (int index = 0; index < count; index++) result.append(value);
		return result.toString();
	}

	private static class Frame {
		private final boolean fin;
		private final boolean masked;
		private final int opcode;
		private final byte[] payload;
		private final boolean rsv;
		private int expectedCloseCode;
		private Frame(int opcode, boolean fin, boolean rsv, boolean masked, byte[] payload, int expectedCloseCode) {
			this.opcode = opcode;
			this.fin = fin;
			this.rsv = rsv;
			this.masked = masked;
			this.payload = payload;
			this.expectedCloseCode = expectedCloseCode;
		}
		private String text() { return new String(payload, StandardCharsets.UTF_8); }
	}

	private static class RawWebSocket implements AutoCloseable {
		private final InputStream input;
		private final OutputStream output;
		private final Socket socket;
		private final String handshake;
		private final SecureRandom random = new SecureRandom();
		private RawWebSocket(Socket socket, String handshake) throws IOException {
			this.socket = socket;
			this.handshake = handshake;
			input = socket.getInputStream();
			output = socket.getOutputStream();
		}
		private void sendFrame(int opcode, boolean fin, boolean rsv, boolean masked, byte[] payload) throws IOException {
			output.write((fin ? 0x80 : 0) | (rsv ? 0x40 : 0) | opcode);
			int maskBit = masked ? 0x80 : 0;
			if (payload.length < 126) output.write(maskBit | payload.length);
			else if (payload.length <= 0xffff) {
				output.write(maskBit | 126);
				output.write(payload.length >>> 8);
				output.write(payload.length);
			} else throw new IllegalArgumentException("Test frame is unexpectedly large");
			if (masked) {
				byte[] mask = new byte[4];
				random.nextBytes(mask);
				output.write(mask);
				for (int index = 0; index < payload.length; index++) output.write(payload[index] ^ mask[index % mask.length]);
			} else output.write(payload);
			output.flush();
		}
		private Frame readFrame() throws IOException {
			int first = input.read();
			int second = input.read();
			if (first < 0 || second < 0) throw new IOException("WebSocket closed before a frame");
			int length = second & 0x7f;
			if (length == 126) length = (input.read() << 8) | input.read();
			byte[] payload = new byte[length];
			for (int offset = 0; offset < payload.length;) {
				int read = input.read(payload, offset, payload.length - offset);
				if (read < 0) throw new IOException("WebSocket closed in frame");
				offset += read;
			}
			return new Frame(first & 0x0f, (first & 0x80) != 0, (first & 0x40) != 0, (second & 0x80) != 0, payload, 0);
		}
		@Override public void close() throws IOException { socket.close(); }
	}
}
