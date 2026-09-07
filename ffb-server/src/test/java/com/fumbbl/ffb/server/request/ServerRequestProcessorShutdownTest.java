package com.fumbbl.ffb.server.request;

import com.fumbbl.ffb.server.DebugLog;
import com.fumbbl.ffb.server.FantasyFootballServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerRequestProcessorShutdownTest {

	@Test
	void shutdownDrainsQueuedAndWorkerCreatedRequestsInFifoOrder() throws Exception {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		when(server.getDebugLog()).thenReturn(mock(DebugLog.class));
		ServerRequestProcessor processor = new ServerRequestProcessor(server);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(1);
		List<Integer> order = Collections.synchronizedList(new ArrayList<>());

		processor.add(new ServerRequest() {
			@Override
			public void process(ServerRequestProcessor requestProcessor) {
				entered.countDown();
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new AssertionError("Test release timed out");
					}
				} catch (InterruptedException exception) {
					throw new AssertionError(exception);
				}
				order.add(1);
				requestProcessor.add(recordingRequest(order, 3));
			}
		});
		processor.add(recordingRequest(order, 2));
		processor.start();
		assertTrue(entered.await(5, TimeUnit.SECONDS));

		Thread shutdown = new Thread(() -> {
			processor.shutdown();
			completed.countDown();
		});
		shutdown.start();
		assertFalse(completed.await(100, TimeUnit.MILLISECONDS));
		release.countDown();

		assertTrue(completed.await(5, TimeUnit.SECONDS));
		processor.join(1000);
		assertFalse(processor.isAlive());
		assertEquals(Arrays.asList(1, 2, 3), order);
		assertFalse(processor.add(recordingRequest(order, 4)));
	}

	private ServerRequest recordingRequest(List<Integer> order, int value) {
		return new ServerRequest() {
			@Override
			public void process(ServerRequestProcessor requestProcessor) {
				order.add(value);
			}
		};
	}

	@Test
	void shutdownDoesNotRetryAnInFlightFailedRequest() throws Exception {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		when(server.getDebugLog()).thenReturn(mock(DebugLog.class));
		ServerRequestProcessor processor = new ServerRequestProcessor(server);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(1);
		processor.add(new ServerRequest() {
			@Override
			public void process(ServerRequestProcessor requestProcessor) {
				entered.countDown();
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new AssertionError("Test release timed out");
					}
				} catch (InterruptedException exception) {
					throw new AssertionError(exception);
				}
				throw new IllegalStateException("Expected test failure");
			}
		});
		processor.start();
		assertTrue(entered.await(5, TimeUnit.SECONDS));

		Thread shutdown = new Thread(() -> {
			processor.shutdown();
			completed.countDown();
		});
		shutdown.start();
		release.countDown();

		assertTrue(completed.await(5, TimeUnit.SECONDS));
		processor.join(1000);
		assertFalse(processor.isAlive());
	}
}
