package com.fumbbl.ffb.server;

import com.fumbbl.ffb.model.change.ModelChange;
import com.fumbbl.ffb.model.change.ModelChangeId;
import com.fumbbl.ffb.net.commands.ServerCommand;
import com.fumbbl.ffb.net.commands.ServerCommandModelSync;
import com.fumbbl.ffb.net.commands.ServerCommandReplay;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Kalimar
 */
public class ServerReplayer implements Runnable {

	private final Set<ModelChangeId> markingAffectingChanges = new HashSet<ModelChangeId>() {{
		add(ModelChangeId.FIELD_MODEL_ADD_INTENSIVE_TRAINING);
		add(ModelChangeId.FIELD_MODEL_ADD_CARD_EFFECT);
		add(ModelChangeId.FIELD_MODEL_REMOVE_CARD_EFFECT);
		add(ModelChangeId.FIELD_MODEL_ADD_PRAYER);
		add(ModelChangeId.FIELD_MODEL_REMOVE_PRAYER);
		add(ModelChangeId.PLAYER_RESULT_SET_SERIOUS_INJURY);
		add(ModelChangeId.PLAYER_RESULT_SET_SERIOUS_INJURY_DECAY);
	}};

	private volatile boolean fStopped;
	private final List<ServerReplay> fReplayQueue;
	private final FantasyFootballServer fServer;
	private final CountDownLatch stopped = new CountDownLatch(1);
	private volatile Thread workerThread;

	public ServerReplayer(FantasyFootballServer pServer) {
		fServer = pServer;
		fReplayQueue = new LinkedList<>();
	}

	public void add(ServerReplay pReplay) {
		synchronized (fReplayQueue) {
			if (fStopped && Thread.currentThread() != workerThread) {
				throw new IllegalStateException("Server replayer is shutting down");
			}
			fReplayQueue.add(pReplay);
			fReplayQueue.notify();
		}
	}

	public void run() {
		workerThread = Thread.currentThread();
		try {
			while (true) {
				ServerReplay serverReplay = null;

				FantasyFootballServer server = getServer();
				try {

				synchronized (fReplayQueue) {
					try {
						while (fReplayQueue.isEmpty() && !fStopped) {
							fReplayQueue.wait();
						}
					} catch (InterruptedException e) {
						break;
					}
					if (fStopped && fReplayQueue.isEmpty()) {
						break;
					}
					serverReplay = fReplayQueue.remove(0);
				}

				while (serverReplay != null) {

					serverReplay.setComplete(true);

					ServerCommandReplay replayCommand = new ServerCommandReplay();
					replayCommand.setTotalNrOfCommands(serverReplay.size());
					replayCommand.setLastCommand(true);

					ServerCommand[] serverCommands = serverReplay.findRelevantCommandsInLog();
					for (ServerCommand serverCommand : serverCommands) {
						replayCommand.add(serverCommand);
						switch(serverCommand.getId()) {
							case SERVER_ADD_PLAYER:
								replayCommand.addMarkingAffectingCommand(serverCommand.getCommandNr());
								break;
							case SERVER_MODEL_SYNC:
								ServerCommandModelSync syncCommand = (ServerCommandModelSync) serverCommand;
								for (ModelChange change: syncCommand.getModelChanges().getChanges()) {
									if (markingAffectingChanges.contains(change.getChangeId())) {
										replayCommand.addMarkingAffectingCommand(serverCommand.getCommandNr());
										break;
									}
								}
 								break;
							default:
								break;
						}
						if (replayCommand.getNrOfCommands() >= ServerCommandReplay.MAX_NR_OF_COMMANDS) {
							serverReplay.setComplete(false);
							replayCommand.setLastCommand(false);
							break;
						}
					}

					server.getCommunication().send(serverReplay.getSession(), replayCommand, false);
					if (server.getDebugLog().isLogging(IServerLogLevel.DEBUG)) {
						StringBuilder message = new StringBuilder().append("Replay commands ").append(replayCommand.getCommandNr());
						message.append(replayCommand.findLowestCommandNr()).append(" - ")
							.append(replayCommand.findHighestCommandNr());
						message.append(" of ").append(replayCommand.getTotalNrOfCommands()).append(" total.");
						server.getDebugLog().log(IServerLogLevel.DEBUG, serverReplay.getGameId(),
							DebugLog.COMMAND_SERVER_SPECTATOR, message.toString());
					}

					if (!serverReplay.isComplete()) {
						serverReplay.setFromCommandNr(replayCommand.findHighestCommandNr() + 1);
					} else {
						serverReplay = null;
					}

				}

				} catch (Exception pException) {
					server.getDebugLog().log(serverReplay != null ? serverReplay.getGameId() : 0, pException);
				}

			}
		} finally {
			stopped.countDown();
		}
	}

	public void stop() {
		synchronized (fReplayQueue) {
			fStopped = true;
			fReplayQueue.notifyAll();
		}
		if (Thread.currentThread() == workerThread) {
			return;
		}
		if (workerThread == null) {
			return;
		}
		try {
			if (!stopped.await(20, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out draining server replays");
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted draining server replays", interrupted);
		}
	}

	public FantasyFootballServer getServer() {
		return fServer;
	}

}
