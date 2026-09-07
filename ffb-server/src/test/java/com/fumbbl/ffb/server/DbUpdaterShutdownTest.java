package com.fumbbl.ffb.server;

import com.fumbbl.ffb.server.db.DbTransaction;
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

class DbUpdaterShutdownTest {
	@Test
	void shutdownWaitsForInFlightUpdateAndDrainsInOrder() throws Exception {
		DbUpdater updater = new DbUpdater(null);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(1);
		List<Integer> order = Collections.synchronizedList(new ArrayList<>());
		updater.add(new DbTransaction() {
			@Override
			public void executeUpdate(FantasyFootballServer server) {
				entered.countDown();
				try {
					if (!release.await(5, TimeUnit.SECONDS)) { throw new AssertionError("Test release timed out"); }
				} catch (InterruptedException exception) {
					throw new AssertionError(exception);
				}
				order.add(1);
			}
		});
		updater.add(new DbTransaction() {
			@Override
			public void executeUpdate(FantasyFootballServer server) { order.add(2); }
		});
		Thread worker = new Thread(updater);
		worker.start();
		assertTrue(entered.await(5, TimeUnit.SECONDS));
		Thread shutdown = new Thread(() -> { updater.shutdown(); completed.countDown(); });
		shutdown.start();
		try {
			assertFalse(completed.await(100, TimeUnit.MILLISECONDS));
		} finally { release.countDown(); }
		assertTrue(completed.await(5, TimeUnit.SECONDS));
		worker.join(1000);
		assertFalse(worker.isAlive());
		assertEquals(Arrays.asList(1, 2), order);
		assertFalse(updater.add(new DbTransaction()));
	}
}
