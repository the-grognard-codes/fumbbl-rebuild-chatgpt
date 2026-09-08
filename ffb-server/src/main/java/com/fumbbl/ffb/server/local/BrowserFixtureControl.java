package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.FantasyFootballServer;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit local operator mailbox. No HTTP route or browser command exposes it. */
public final class BrowserFixtureControl implements AutoCloseable {
	private final FantasyFootballServer server;
	private final BrowserMatchAdapter adapter;
	private final BrowserMatchServlet servlet;
	private final Path request = Paths.get("/tmp/ffb-browser-control.request");
	private final Path response = Paths.get("/tmp/ffb-browser-control.response");
	private final AtomicBoolean pending = new AtomicBoolean();
	private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(work -> {
		Thread thread = new Thread(work, "browser-fixture-control");
		thread.setDaemon(true);
		return thread;
	});
	private volatile boolean closed;
	private long lifetimes = 1;

	public BrowserFixtureControl(FantasyFootballServer server, BrowserMatchAdapter adapter, BrowserMatchServlet servlet) {
		this.server = server;
		this.adapter = adapter;
		this.servlet = servlet;
	}

	public void start() {
		poller.scheduleWithFixedDelay(this::poll, 0, 50, TimeUnit.MILLISECONDS);
	}

	private void poll() {
		if (closed || !Files.exists(request) || !pending.compareAndSet(false, true)) return;
		try {
			if (Files.size(request) > 1024) throw new IllegalArgumentException("Operator request too large");
			JsonObject command = JsonObject.readFrom(new String(Files.readAllBytes(request), StandardCharsets.UTF_8));
			Files.delete(request);
			if (!server.getCommunication().execute(() -> execute(command))) pending.set(false);
		} catch (Exception failure) {
			try { Files.deleteIfExists(request); } catch (Exception ignored) { }
			pending.set(false);
		}
	}

	private void execute(JsonObject command) {
		JsonObject result = new JsonObject().add("id", command.getString("id", "invalid"));
		try {
			if (closed) return;
			String operation = command.getString("operation", "");
			if ("hold".equals(operation)) {
				// Fixed, bounded operator fault. The acknowledgement lets the harness
				// fill ingress while the communication worker is deliberately occupied.
				writeResponse(new JsonObject().add("id", command.getString("id", "invalid")).add("ok", true).add("phase", "holding"));
				try { Thread.sleep(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
			} else if ("reset".equals(operation)) {
				BrowserMatchAdapter.Fixture fixture = BrowserMatchAdapter.Fixture.valueOf(command.getString("fixture", ""));
				servlet.closeConnections();
				adapter.resetFixture(fixture);
				lifetimes++;
			} else if (!"metrics".equals(operation)) throw new IllegalArgumentException("Unsupported operator operation");
			result.add("ok", true).add("fixtureLifetimes", lifetimes).add("adapter", adapter.measurements())
				.add("transport", servlet.getTransportMetrics())
				.add("jvmHeapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed())
				.add("jvmHeapCommittedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted())
				.add("jvmNonHeapUsedBytes", ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed())
				.add("jvmThreads", ManagementFactory.getThreadMXBean().getThreadCount())
				.add("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
		} catch (RuntimeException failure) {
			result.add("ok", false).add("error", "Operator command failed");
		} finally {
			try {
				writeResponse(result);
			} catch (Exception ignored) { /* Operator times out; never fail the communication worker. */ }
			pending.set(false);
		}
	}

	private void writeResponse(JsonObject result) {
		try {
			Path temporary = Paths.get(response + ".tmp");
			Files.write(temporary, result.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(temporary, response, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception failure) { throw new IllegalStateException("Operator response unavailable", failure); }
	}

	@Override
	public void close() {
		closed = true;
		poller.shutdownNow();
	}
}
