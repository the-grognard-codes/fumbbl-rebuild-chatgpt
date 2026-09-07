package com.fumbbl.ffb.server;

import com.fumbbl.ffb.net.commands.ServerCommand;
import com.fumbbl.ffb.server.net.ServerCommunication;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerReplayerShutdownTest {

	@Test
	void stopWaitsForQueuedReplaysToFinish() throws Exception {
		FantasyFootballServer server = mock(FantasyFootballServer.class);
		when(server.getCommunication()).thenReturn(mock(ServerCommunication.class));
		when(server.getDebugLog()).thenReturn(mock(DebugLog.class));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch completed = new CountDownLatch(1);
		ServerReplay firstReplay = replay(entered, release);
		ServerReplay secondReplay = replay();
		ServerReplayer replayer = new ServerReplayer(server);
		replayer.add(firstReplay);
		replayer.add(secondReplay);
		Thread worker = new Thread(replayer);
		worker.start();
		assertTrue(entered.await(5, TimeUnit.SECONDS));

		Thread shutdown = new Thread(() -> {
			replayer.stop();
			completed.countDown();
		});
		shutdown.start();
		assertFalse(completed.await(100, TimeUnit.MILLISECONDS));
		release.countDown();

		assertTrue(completed.await(5, TimeUnit.SECONDS));
		worker.join(1000);
		assertFalse(worker.isAlive());
		InOrder order = inOrder(firstReplay, secondReplay);
		order.verify(firstReplay).setComplete(true);
		order.verify(secondReplay).setComplete(true);
		verify(firstReplay).findRelevantCommandsInLog();
		verify(secondReplay).findRelevantCommandsInLog();
	}

	private ServerReplay replay() {
		return replay(null, null);
	}

	private ServerReplay replay(CountDownLatch entered, CountDownLatch release) {
		ServerReplay replay = mock(ServerReplay.class);
		when(replay.size()).thenAnswer(invocation -> {
			if (entered != null) {
				entered.countDown();
				if (!release.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("Test release timed out");
				}
			}
			return 0;
		});
		when(replay.isComplete()).thenReturn(true);
		when(replay.findRelevantCommandsInLog()).thenReturn(new ServerCommand[0]);
		return replay;
	}
}
