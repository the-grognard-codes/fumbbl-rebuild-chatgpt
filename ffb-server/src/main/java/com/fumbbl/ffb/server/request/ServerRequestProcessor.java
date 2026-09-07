package com.fumbbl.ffb.server.request;

import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.IServerLogLevel;
import com.fumbbl.ffb.util.StringTool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Kalimar
 */
public class ServerRequestProcessor extends Thread {

	private volatile boolean fStopped;
	private final FantasyFootballServer fServer;
	private final BlockingQueue<ServerRequest> fRequestQueue;
	private final Object enqueueLock = new Object();
	private final ServerRequest shutdownMarker = new ServerRequest() {
		@Override
		public void process(ServerRequestProcessor processor) {
		}
	};
	private final CountDownLatch stopped = new CountDownLatch(1);

	public ServerRequestProcessor(FantasyFootballServer pServer) {
		fServer = pServer;
		fRequestQueue = new LinkedBlockingQueue<>();
	}

	public FantasyFootballServer getServer() {
		return fServer;
	}

	public boolean add(ServerRequest pServerRequest) {
		synchronized (enqueueLock) {
			if (fStopped && Thread.currentThread() != this) {
				return false;
			}
			getServer().getDebugLog().logWithOutGameId(IServerLogLevel.DEBUG,
				"Adding request to request processor queue: " + pServerRequest.getClass().getName());
			return fRequestQueue.offer(pServerRequest);
		}
	}

	@Override
	public void run() {
		getServer().getDebugLog().logWithOutGameId(IServerLogLevel.INFO, "Request Processor Started.");
		try {
			while (true) {
				ServerRequest request = null;
				try {
					request = fRequestQueue.take();
				} catch (InterruptedException pInterruptedException) {
					// continue with serverRequest == null
				}
				if (request != shutdownMarker) {
					handleRequestInternal(request, true);
				}
				synchronized (enqueueLock) {
					if (fStopped && fRequestQueue.isEmpty()) {
						break;
					}
				}
			}
		} finally {
			stopped.countDown();
		}
		getServer().getDebugLog().logWithOutGameId(IServerLogLevel.INFO, "Request Processor Stopped.");
	}

	public void shutdown() {
		synchronized (enqueueLock) {
			if (!fStopped) {
				fStopped = true;
				fRequestQueue.offer(shutdownMarker);
			}
		}
		if (Thread.currentThread() == this) {
			return;
		}
		if (getState() == State.NEW) {
			return;
		}
		try {
			if (!stopped.await(20, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out draining server requests");
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted draining server requests", interrupted);
		}
	}

	private void handleRequestInternal(ServerRequest request, boolean loopOnError) {
		boolean sent = !loopOnError;
		do {
			try {
				if (request != null) {
					getServer().getDebugLog().logWithOutGameId(IServerLogLevel.DEBUG,
						"Processing request from request processor queue: " + request.getClass().getName());
					request.process(this);
				}
				sent = true;
			} catch (Exception pAnyException) {
				getServer().getDebugLog().logWithOutGameId(IServerLogLevel.ERROR, StringTool.print(request.getRequestUrl()));
				getServer().getDebugLog().logWithOutGameId(pAnyException);
				if (!fStopped) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException pInterruptedException) {
						// just continue
					}
				}
			}
		} while (loopOnError && !fStopped && !sent);
	}

}
