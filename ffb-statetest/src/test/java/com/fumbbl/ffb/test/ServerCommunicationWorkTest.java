package com.fumbbl.ffb.test;

import com.fumbbl.ffb.server.net.ServerCommunication;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerCommunicationWorkTest {
	@Test
	public void workRunsInFifoOrderSurvivesExceptionsAndDrainsOnShutdown() throws Exception {
		TestServer testServer = new TestServer();
		ServerCommunication communication = new ServerCommunication(testServer.getServer());
		List<Integer> order = Collections.synchronizedList(new ArrayList<>());
		CountDownLatch complete = new CountDownLatch(2);
		Thread worker = new Thread(communication, "communication-test");
		worker.start();
		assertTrue(communication.execute(() -> { order.add(1); throw new IllegalStateException("expected"); }));
		assertTrue(communication.execute(() -> { order.add(2); complete.countDown(); }));
		assertTrue(communication.execute(() -> { order.add(3); complete.countDown(); }));
		assertTrue(complete.await(5, TimeUnit.SECONDS));
		communication.shutdown();
		worker.join(1000);
		assertEquals(java.util.Arrays.asList(1, 2, 3), order);
		assertFalse(worker.isAlive());
		assertFalse(communication.execute(() -> order.add(4)));
	}
}
