package com.fumbbl.ffb.server.local;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeUnit;

/** Bounded, ordered asynchronous delivery for one browser connection. */
public class BrowserMatchDelivery {
	public interface Completion {
		void failed(Throwable failure);
		void succeeded();
	}

	public interface Sink {
		boolean isOpen();
		void close(int statusCode, String reason);
		void send(String message, Completion completion);
	}

	private static final int CLOSE_SLOW_CONSUMER = 1013;
	private final int maximumBytes;
	private final int maximumMessages;
	private final BrowserMatchTransportMetrics metrics;
	private final Sink sink;
	private final LongSupplier clock;
	private final Deque<QueuedMessage> messages = new ArrayDeque<>();
	private int bytes;
	private boolean writing;
	private boolean closed;
	private long writeStartedAt;

	public BrowserMatchDelivery(Sink sink, BrowserMatchTransportMetrics metrics, int maximumMessages, int maximumBytes) {
		this(sink, metrics, maximumMessages, maximumBytes, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
	}

	BrowserMatchDelivery(Sink sink, BrowserMatchTransportMetrics metrics, int maximumMessages, int maximumBytes, LongSupplier clock) {
		this.sink = sink;
		this.metrics = metrics;
		this.maximumMessages = maximumMessages;
		this.maximumBytes = maximumBytes;
		this.clock = clock;
	}

	public boolean send(String message) {
		String next = null;
		boolean close = false;
		synchronized (this) {
			int messageBytes = message.getBytes(StandardCharsets.UTF_8).length;
			if (closed || !sink.isOpen()) return false;
			if (messages.size() >= maximumMessages || bytes + messageBytes > maximumBytes) {
				closed = true;
				writing = false;
				metrics.outboundOverload();
				metrics.slowDisconnect();
				metrics.deliveryCleared(messages.size(), bytes);
				messages.clear();
				bytes = 0;
				close = true;
			} else {
				messages.addLast(new QueuedMessage(message, messageBytes, clock.getAsLong()));
				bytes += messageBytes;
				metrics.deliveryQueued(messageBytes);
				if (!writing) {
					writing = true;
					next = messages.peekFirst().text;
					writeStartedAt = clock.getAsLong();
					metrics.deliveryQueueDelay(0);
				}
			}
		}
		if (close) {
			closeSlowConsumer();
			return false;
		}
		if (next != null) write(next);
		return true;
	}

	public synchronized void close() {
		closed = true;
		metrics.deliveryCleared(messages.size(), bytes);
		messages.clear();
		bytes = 0;
		writing = false;
	}

	boolean expireIfStalled(long now, long timeoutMillis) {
		boolean close = false;
		synchronized (this) {
			if (!closed && writing && now - writeStartedAt >= timeoutMillis) {
				closed = true;
				writing = false;
				metrics.deliveryCleared(messages.size(), bytes);
				messages.clear();
				bytes = 0;
				metrics.slowDisconnect();
				close = true;
			}
		}
		if (close) closeSlowConsumer();
		return close;
	}

	private void write(String message) {
		try {
			sink.send(message, new Completion() {
				@Override
				public void failed(Throwable failure) { complete(false); }
				@Override
				public void succeeded() { complete(true); }
			});
		} catch (RuntimeException exception) {
			complete(false);
		}
	}

	private void complete(boolean succeeded) {
		String next = null;
		boolean close = false;
		synchronized (this) {
			if (!writing) return;
			QueuedMessage completed = messages.pollFirst();
			if (completed != null) {
				bytes -= completed.bytes;
				metrics.deliveryCompleted(completed.bytes);
			}
			writing = false;
			if (!succeeded || closed || !sink.isOpen()) {
				closed = true;
				metrics.deliveryCleared(messages.size(), bytes);
				messages.clear();
				bytes = 0;
				metrics.slowDisconnect();
				close = true;
			} else if (!messages.isEmpty()) {
				writing = true;
				QueuedMessage queued = messages.peekFirst();
				next = queued.text;
				writeStartedAt = clock.getAsLong();
				metrics.deliveryQueueDelay(writeStartedAt - queued.queuedAt);
			}
		}
		if (close) {
			closeSlowConsumer();
			return;
		}
		if (next != null) write(next);
	}

	private static class QueuedMessage {
		private final int bytes;
		private final long queuedAt;
		private final String text;
		private QueuedMessage(String text, int bytes, long queuedAt) {
			this.text = text;
			this.bytes = bytes;
			this.queuedAt = queuedAt;
		}
	}

	private void closeSlowConsumer() {
		try {
			sink.close(CLOSE_SLOW_CONSUMER, "Browser client cannot keep up; reconnect for a full snapshot");
		} catch (RuntimeException ignored) {
			// The session is already gone.
		}
	}
}
