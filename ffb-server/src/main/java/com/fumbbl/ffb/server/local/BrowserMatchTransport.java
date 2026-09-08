package com.fumbbl.ffb.server.local;

import com.fumbbl.ffb.server.FantasyFootballServer;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separates bounded browser traffic from the legacy communication queue. */
public class BrowserMatchTransport {
	static final int MAX_CONNECTIONS = 16;
	static final int MAX_INGRESS_WORK = 128;
	static final int MAX_OUTBOUND_BYTES = 256 * 1024;
	static final int MAX_OUTBOUND_MESSAGES = 64;
	static final long OUTBOUND_WRITE_TIMEOUT_MS = 2000;
	private final BrowserMatchTransportMetrics metrics = new BrowserMatchTransportMetrics();
	private final Semaphore connections = new Semaphore(MAX_CONNECTIONS);
	private final Semaphore ingress = new Semaphore(MAX_INGRESS_WORK);
	private final Set<BrowserMatchSocket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<BrowserMatchSocket, Boolean>());
	private final FantasyFootballServer server;
	private final AtomicBoolean stopped = new AtomicBoolean();
	private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(work -> {
		Thread thread = new Thread(work, "browser-match-write-watchdog");
		thread.setDaemon(true);
		return thread;
	});

	public BrowserMatchTransport(FantasyFootballServer server) {
		this.server = server;
		metrics.activeConnectionSupplier(() -> MAX_CONNECTIONS - connections.availablePermits());
		watchdog.scheduleWithFixedDelay(() -> {
			long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
			for (BrowserMatchSocket socket : sockets) socket.expireDelivery(now);
		}, 100, 100, TimeUnit.MILLISECONDS);
	}

	public boolean admitConnection() {
		boolean admitted = !stopped.get() && connections.tryAcquire();
		if (!admitted) metrics.admissionRejected();
		return admitted;
	}

	synchronized boolean connected(BrowserMatchSocket socket) {
		if (stopped.get()) {
			releaseConnection();
			return false;
		}
		sockets.add(socket);
		metrics.activeConnections(MAX_CONNECTIONS - connections.availablePermits());
		return true;
	}
	void disconnect(BrowserMatchSocket socket, Runnable cleanup) {
		if (!sockets.remove(socket)) return;
		boolean queued = server.getCommunication().execute(() -> {
			try {
				cleanup.run();
			} finally {
				releaseConnection();
			}
		});
		if (!queued) {
			try {
				cleanup.run();
			} finally {
				releaseConnection();
			}
		}
	}

	void disconnectFromEngineWorker(BrowserMatchSocket socket, Runnable cleanup) {
		if (!sockets.remove(socket)) return;
		try {
			cleanup.run();
		} finally {
			releaseConnection();
		}
	}

	private void releaseConnection() {
		connections.release();
		metrics.activeConnections(MAX_CONNECTIONS - connections.availablePermits());
	}

	public boolean submit(Runnable work, Runnable rejected) {
		if (stopped.get() || !ingress.tryAcquire()) {
			metrics.ingressOverload();
			rejected.run();
			return false;
		}
		long submittedAt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
		try {
			metrics.ingressQueueDepth(MAX_INGRESS_WORK - ingress.availablePermits());
			boolean queued = server.getCommunication().execute(() -> {
				metrics.ingressDelay(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - submittedAt);
				try {
					work.run();
				} finally {
					ingress.release();
					metrics.ingressQueueDepth(MAX_INGRESS_WORK - ingress.availablePermits());
				}
			});
			if (!queued) {
				ingress.release();
				metrics.ingressQueueDepth(MAX_INGRESS_WORK - ingress.availablePermits());
				rejected.run();
			}
			return queued;
		} catch (RuntimeException exception) {
			ingress.release();
			metrics.ingressQueueDepth(MAX_INGRESS_WORK - ingress.availablePermits());
			metrics.ingressOverload();
			rejected.run();
			return false;
		}
	}

	/** Safe from the communication worker: it does not wait for Jetty I/O. */
	public void closeConnections() {
		for (BrowserMatchSocket socket : sockets) socket.closeFromEngineWorker();
	}

	public BrowserMatchTransportMetrics getMetrics() { return metrics; }

	public void destroy() {
		BrowserMatchSocket[] remaining;
		synchronized (this) {
			if (!stopped.compareAndSet(false, true)) return;
			remaining = sockets.toArray(new BrowserMatchSocket[0]);
		}
		for (BrowserMatchSocket socket : remaining) socket.closeFromEngineWorker();
		watchdog.shutdownNow();
	}
}
