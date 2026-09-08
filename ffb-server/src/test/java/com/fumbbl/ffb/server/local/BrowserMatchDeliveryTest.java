package com.fumbbl.ffb.server.local;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BrowserMatchDeliveryTest {
	@Test
	public void blockedWriterDoesNotPreventHealthyPeerDelivery() {
		DelayedSink blocked = new DelayedSink();
		ImmediateSink healthy = new ImmediateSink();
		BrowserMatchDelivery blockedDelivery = new BrowserMatchDelivery(blocked, new BrowserMatchTransportMetrics(), 2, 100);
		BrowserMatchDelivery healthyDelivery = new BrowserMatchDelivery(healthy, new BrowserMatchTransportMetrics(), 2, 100);

		assertTrue(blockedDelivery.send("first"));
		assertTrue(blockedDelivery.send("second"));
		assertTrue(healthyDelivery.send("state"));

		assertEquals(1, blocked.messages.size());
		assertEquals("state", healthy.messages.get(0));
		blocked.completeNext();
		assertEquals("second", blocked.messages.get(1));
	}

	@Test
	public void overflowDisconnectsSlowPeerAndClearsBoundedQueue() {
		DelayedSink slow = new DelayedSink();
		BrowserMatchTransportMetrics metrics = new BrowserMatchTransportMetrics();
		BrowserMatchDelivery delivery = new BrowserMatchDelivery(slow, metrics, 2, 100);

		assertTrue(delivery.send("first"));
		assertTrue(delivery.send("second"));
		assertFalse(delivery.send("third"));

		assertEquals(1013, slow.closeCode);
		assertEquals(0, metrics.toJson().getLong("deliveryQueueDepth", -1));
		assertEquals(1, metrics.toJson().getLong("outboundOverload", -1));
		assertEquals(1, metrics.toJson().getLong("slowDisconnects", -1));
		assertFalse(delivery.send("later"));
		slow.completions.remove(0).succeeded();
		assertEquals(0, metrics.toJson().getLong("deliveryQueueDepth", -1));
		assertEquals(1, metrics.toJson().getLong("slowDisconnects", -1));
		assertEquals(1, metrics.toJson().getLong("outboundOverload", -1));
	}

	@Test
	public void writeFailureClearsPendingBytesWithoutWaitingForThePeer() {
		DelayedSink slow = new DelayedSink();
		BrowserMatchTransportMetrics metrics = new BrowserMatchTransportMetrics();
		BrowserMatchDelivery delivery = new BrowserMatchDelivery(slow, metrics, 2, 100);

		assertTrue(delivery.send("first"));
		assertTrue(delivery.send("second"));
		slow.completions.remove(0).failed(new RuntimeException("write failed"));

		assertEquals(1013, slow.closeCode);
		assertEquals(0, metrics.toJson().getLong("deliveryQueueDepth", -1));
		assertEquals(0, metrics.toJson().getLong("deliveryQueueBytes", -1));
		assertFalse(delivery.send("later"));
	}

	@Test
	public void watchdogTimeoutClosesBlockedWriterWithoutDelayingHealthyPeer() {
		AtomicLong now = new AtomicLong(100);
		DelayedSink blocked = new DelayedSink();
		ImmediateSink healthy = new ImmediateSink();
		BrowserMatchTransportMetrics metrics = new BrowserMatchTransportMetrics();
		BrowserMatchDelivery blockedDelivery = new BrowserMatchDelivery(blocked, metrics, 2, 100, now::get);
		BrowserMatchDelivery healthyDelivery = new BrowserMatchDelivery(healthy, new BrowserMatchTransportMetrics(), 2, 100, now::get);

		assertTrue(blockedDelivery.send("first"));
		assertTrue(blockedDelivery.send("second"));
		assertTrue(healthyDelivery.send("state"));
		assertFalse(blockedDelivery.expireIfStalled(2099, 2000));
		assertTrue(blockedDelivery.expireIfStalled(2100, 2000));

		assertEquals(1013, blocked.closeCode);
		assertEquals("state", healthy.messages.get(0));
		assertEquals(0, metrics.toJson().getLong("deliveryQueueBytes", -1));
	}

	@Test
	public void utf8ByteLimitRejectsMessageThatFitsCharacterLimit() {
		DelayedSink slow = new DelayedSink();
		BrowserMatchTransportMetrics metrics = new BrowserMatchTransportMetrics();
		BrowserMatchDelivery delivery = new BrowserMatchDelivery(slow, metrics, 2, 3);

		assertFalse(delivery.send("€€"));
		assertEquals(1013, slow.closeCode);
		assertEquals(1, metrics.toJson().getLong("outboundOverload", -1));
	}

	private static class DelayedSink implements BrowserMatchDelivery.Sink {
		private final List<String> messages = new ArrayList<>();
		private final List<BrowserMatchDelivery.Completion> completions = new ArrayList<>();
		private int closeCode;
		private boolean open = true;

		@Override public boolean isOpen() { return open; }
		@Override public void close(int statusCode, String reason) { closeCode = statusCode; open = false; }
		@Override public void send(String message, BrowserMatchDelivery.Completion completion) {
			messages.add(message);
			completions.add(completion);
		}
		private void completeNext() { completions.remove(0).succeeded(); }
	}

	private static class ImmediateSink implements BrowserMatchDelivery.Sink {
		private final List<String> messages = new ArrayList<>();
		@Override public boolean isOpen() { return true; }
		@Override public void close(int statusCode, String reason) { }
		@Override public void send(String message, BrowserMatchDelivery.Completion completion) {
			messages.add(message);
			completion.succeeded();
		}
	}
}
