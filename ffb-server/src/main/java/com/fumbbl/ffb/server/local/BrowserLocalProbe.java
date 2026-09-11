package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonObject;
import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.ee8.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.ee8.websocket.client.WebSocketClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Container operator probe for fresh startup on the internal-only Docker network. */
public final class BrowserLocalProbe {
	public static void main(String[] args) throws Exception { new BrowserLocalProbe().run(); }

	private void run() throws Exception {
		WebSocketClient client = new WebSocketClient();
		client.start();
		try {
			Peer home = join(client, "home");
			Peer away = join(client, "away");
			JsonObject first = home.next("snapshot");
			JsonObject other = away.next("snapshot");
			if (!first.getString("matchId", "").equals(other.getString("matchId", "")) || first.getLong("revision", -1) != 0) throw new IllegalStateException("Expected fresh shared fixture");
			JsonObject prompt = first.get("prompt").asObject();
			Peer owner = "home".equals(prompt.getString("actor", "")) ? home : away;
			owner.getSession().getRemote().sendString(new JsonObject().add("version", 1).add("type", "choice").add("requestId", "offline-choice")
				.add("expectedRevision", 0).add("choiceId", prompt.getString("id", ""))
				.add("optionId", prompt.get("options").asArray().get(0).asObject().getString("id", "")).toString());
			JsonObject result = owner.next("result");
			if (!"CHOICE_APPLIED".equals(result.getString("code", ""))) throw new IllegalStateException("Choice rejected");
			JsonObject resolvedHome = home.next("snapshot");
			JsonObject resolvedAway = away.next("snapshot");
			resolvedHome.remove("actor"); resolvedAway.remove("actor");
			if (!resolvedHome.toString().equals(resolvedAway.toString()) || resolvedHome.getLong("revision", -1) != 1 || !resolvedHome.get("prompt").isNull()) throw new IllegalStateException("Views differ");
			System.out.println(new JsonObject().add("passed", true).add("transport", "real WebSocket inside isolated container network")
				.add("initial", first).add("result", result).add("resolved", resolvedHome));
		} finally { client.stop(); }
	}

	private Peer join(WebSocketClient client, String actor) throws Exception {
		Peer peer = new Peer();
		ClientUpgradeRequest request = new ClientUpgradeRequest();
		request.setHeader("Origin", "http://127.0.0.1:5173");
		Session session = client.connect(peer, URI.create("ws://127.0.0.1:22227/browser/v1"), request).get(5, TimeUnit.SECONDS);
		String token = new String(Files.readAllBytes(Paths.get("/run/secrets/browser_" + actor + "_token")), StandardCharsets.UTF_8).trim();
		session.getRemote().sendString(new JsonObject().add("version", 1).add("type", "join").add("requestId", "probe-" + actor).add("token", token).toString());
		if (!"JOINED".equals(peer.next("result").getString("code", ""))) throw new IllegalStateException("Join rejected");
		return peer;
	}

	public static final class Peer extends WebSocketAdapter {
		private final ArrayBlockingQueue<JsonObject> messages = new ArrayBlockingQueue<>(16);
		@Override public void onWebSocketText(String message) {
			if (!messages.offer(JsonObject.readFrom(message))) getSession().close();
		}
		private JsonObject next(String type) throws Exception {
			JsonObject message = messages.poll(5, TimeUnit.SECONDS);
			if (message == null || !type.equals(message.getString("type", ""))) throw new IllegalStateException("Missing expected " + type);
			return message;
		}
	}
}
