package com.fumbbl.ffb.server;

import com.fumbbl.ffb.server.db.DbTransaction;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Kalimar
 */
public class DbUpdater implements Runnable {

	private final FantasyFootballServer fServer;
	private boolean fStopped;
	private final BlockingQueue<DbTransaction> fUpdateQueue;
	private final DbTransaction shutdownMarker = new DbTransaction();
	private final CountDownLatch stopped = new CountDownLatch(1);

	public DbUpdater(FantasyFootballServer pServer) {
		fServer = pServer;
		fUpdateQueue = new LinkedBlockingQueue<DbTransaction>();
	}

	public synchronized boolean add(DbTransaction dbTransaction) {
		if (fStopped) {
			return false;
		}
		return fUpdateQueue.offer(dbTransaction);
	}

	@Override
	public void run() {
		try {
			while (true) {
				DbTransaction update = null;
				try {
					update = fUpdateQueue.take();
				} catch (InterruptedException pInterruptedException) {
					// continue with dbTransaction == null
				}
				if (update == shutdownMarker) {
					break;
				}
				handleUpdateInternal(update);
			}
		} catch (Exception pException) {
			getServer().getDebugLog().logWithOutGameId(pException);
			stopped.countDown();
			System.exit(99);
		} finally {
			stopped.countDown();
		}
	}

	private void handleUpdateInternal(DbTransaction update) {
		if (update == null) {
			return;
		}
		update.executeUpdate(getServer());
	}

	public void shutdown() {
		synchronized (this) {
			if (!fStopped) {
				fStopped = true;
				fUpdateQueue.offer(shutdownMarker);
			}
		}
		try {
			if (!stopped.await(20, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out draining database updates");
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted draining database updates", interrupted);
		}
	}

	public FantasyFootballServer getServer() {
		return fServer;
	}

}
