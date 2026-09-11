package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.ClientMode;
import com.fumbbl.ffb.PasswordChallenge;
import com.fumbbl.ffb.net.commands.ClientCommandJoin;
import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.ee8.websocket.client.WebSocketClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A container-only smoke test for the local server profile. It deliberately uses
 * the same HTTP and command WebSocket protocols as a client, rather than server internals.
 */
public class LocalAcceptanceDemo {

	private static final String BASE_URL = "http://127.0.0.1:22227";
	private static final String COMMAND_URL = "ws://127.0.0.1:22227/command/";
	private static final String ADMIN_SECRET = "/run/secrets/admin_password";
	private static final String COACH_SECRET = "/run/secrets/coach_password";
	private static final long TIMEOUT_MILLIS = 20000L;

	public static void main(String[] args) {
		try {
			new LocalAcceptanceDemo().run(args);
		} catch (Exception exception) {
			System.err.println("FAIL: " + safeMessage(exception));
			System.exit(1);
		}
	}

	private void run(String[] args) throws Exception {
		if (args.length == 1 && "ready".equals(args[0])) {
			ensureOk(get("/admin/cache"), "readiness");
			System.out.println("PASS ready");
			return;
		}
		if (args.length == 1 && "shutdown".equals(args[0])) {
			shutdown();
			System.out.println("PASS shutdown");
			return;
		}
		if (args.length == 2 && "check".equals(args[0])) {
			long gameId = parseGameId(args[1]);
			verifySnapshot(loadSnapshot(gameId), gameId);
			System.out.println("PASS gameId=" + gameId);
			return;
		}
		if (args.length == 1 && "create".equals(args[0])) {
			long gameId = schedule();
			JsonObject snapshot = waitForSnapshot(gameId);
			verifySnapshot(snapshot, gameId);
			joinBothCoaches(gameId, snapshot);
			verifySnapshot(loadSnapshot(gameId), gameId);
			System.out.println("PASS gameId=" + gameId);
			return;
		}
		throw new IllegalArgumentException("Usage: LocalAcceptanceDemo [create|check <gameId>|shutdown|ready]");
	}

	private long schedule() throws Exception {
		String response = authenticatedGet("/admin/schedule?teamHomeId=fixture-home&teamAwayId=fixture-away", "/admin/challenge");
		ensureOk(response, "schedule");
		Matcher matcher = Pattern.compile("<gameId>([0-9]+)</gameId>").matcher(response);
		if (!matcher.find()) {
			throw new IOException("Schedule response did not contain a game id");
		}
		return parseGameId(matcher.group(1));
	}

	private void shutdown() throws Exception {
		try {
			String response = authenticatedGet("/admin/shutdown", "/admin/challenge");
			ensureOk(response, "shutdown");
		} catch (IOException exception) {
			// The server may close its HTTP listener before Jetty flushes the response.
			if (!isExpectedServerClose(exception)) {
				throw exception;
			}
		}
	}

	private boolean isExpectedServerClose(IOException exception) {
		String message = exception.getMessage();
		if (message == null) {
			return false;
		}
		String lowerCase = message.toLowerCase();
		return lowerCase.contains("connection") || lowerCase.contains("unexpected end") || lowerCase.contains("eof");
	}

	private JsonObject waitForSnapshot(long gameId) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
		IOException lastFailure = null;
		while (System.nanoTime() < deadline) {
			try {
				JsonObject snapshot = loadSnapshot(gameId);
				if (isUsableSnapshot(snapshot)) {
					return snapshot;
				}
			} catch (IOException exception) {
				lastFailure = exception;
			}
			Thread.sleep(200L);
		}
		throw new IOException("Timed out waiting for game " + gameId + " with two 11-player teams", lastFailure);
	}

	private JsonObject loadSnapshot(long gameId) throws Exception {
		String response = authenticatedGet("/gamestate/get?fromDb=true&gameId=" + gameId, "/gamestate/challenge");
		try {
			return JsonObject.readFrom(response);
		} catch (RuntimeException exception) {
			throw new IOException("Game state response was not JSON for game " + gameId, exception);
		}
	}

	private boolean isUsableSnapshot(JsonObject snapshot) {
		try {
			JsonObject game = snapshot.get("game").asObject();
			return hasElevenPlayers(game.get("teamHome").asObject()) && hasElevenPlayers(game.get("teamAway").asObject());
		} catch (Exception exception) {
			return false;
		}
	}

	private void verifySnapshot(JsonObject snapshot, long gameId) throws IOException {
		if (!isUsableSnapshot(snapshot)) {
			throw new IOException("Game " + gameId + " does not contain two 11-player teams");
		}
		JsonObject game = snapshot.get("game").asObject();
		if (game.getLong("gameId", 0) != gameId
			|| !"fixture-home".equals(game.get("teamHome").asObject().getString("teamId", null))
			|| !"fixture-away".equals(game.get("teamAway").asObject().getString("teamId", null))) {
			throw new IOException("Unexpected fixture identity in saved game " + gameId);
		}
		if (!hasRulesVersion(game, "BB2025")) {
			throw new IOException("Game " + gameId + " is not a BB2025 game");
		}
	}

	private boolean hasElevenPlayers(JsonObject team) {
		JsonValue players = team.get("playerArray");
		return team.getString("teamId", null) != null && players != null && players.isArray()
			&& players.asArray().size() == 11;
	}

	private boolean hasRulesVersion(JsonObject game, String expected) {
		JsonObject options = game.get("gameOptions").asObject();
		JsonArray values = options.get("gameOptionArray").asArray();
		for (JsonValue value : values) {
			JsonObject option = value.asObject();
			if ("rulesVersion".equals(option.getString("gameOptionId", null))
				&& expected.equals(option.getString("gameOptionValue", null))) {
				return true;
			}
		}
		return false;
	}

	private void joinBothCoaches(long gameId, JsonObject snapshot) throws Exception {
		JsonObject game = snapshot.get("game").asObject();
		WebSocketClient client = new WebSocketClient();
		client.start();
		try {
			AcceptanceSocket home = connect(client);
			AcceptanceSocket away = connect(client);
			sendJoin(home, "FixtureHome", gameId, game.get("teamHome").asObject());
			sendJoin(away, "FixtureAway", gameId, game.get("teamAway").asObject());
			awaitGameState(home, gameId, "home");
			awaitGameState(away, gameId, "away");
			home.close();
			away.close();
		} finally {
			client.stop();
		}
	}

	private AcceptanceSocket connect(WebSocketClient client) throws Exception {
		AcceptanceSocket socket = new AcceptanceSocket();
		client.connect(socket, new URI(COMMAND_URL)).get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		return socket;
	}

	private void sendJoin(AcceptanceSocket socket, String coach, long gameId, JsonObject team) throws IOException {
		ClientCommandJoin command = new ClientCommandJoin(ClientMode.PLAYER);
		command.setCoach(coach);
		command.setPassword(readSecret(COACH_SECRET));
		command.setGameId(gameId);
		command.setGameName("Local Acceptance " + gameId);
		command.setTeamId(team.getString("teamId", null));
		command.setTeamName(team.getString("teamName", null));
		socket.getSession().getRemote().sendString(command.toJsonValue().toString());
	}

	private void awaitGameState(AcceptanceSocket socket, long gameId, String side) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS);
		while (System.nanoTime() < deadline) {
			String message = socket.messages.poll(500L, TimeUnit.MILLISECONDS);
			if (message == null) {
				continue;
			}
			try {
				JsonObject command = JsonObject.readFrom(message);
				if ("serverGameState".equals(command.getString("netCommandId", null))) {
					JsonObject game = command.get("game").asObject();
					if (game.getLong("gameId", 0L) == gameId && isUsableGame(game)) {
						return;
					}
				}
			} catch (RuntimeException ignored) {
				// Other server messages need no handling for this acceptance check.
			}
		}
		throw new IOException("Timed out waiting for " + side + " WebSocket game state for game " + gameId);
	}

	private boolean isUsableGame(JsonObject game) {
		return hasElevenPlayers(game.get("teamHome").asObject()) && hasElevenPlayers(game.get("teamAway").asObject())
			&& hasRulesVersion(game, "BB2025");
	}

	private String authenticatedGet(String path, String challengePath) throws Exception {
		String challenge = requestChallenge(challengePath);
		String response = PasswordChallenge.createResponse(challenge, PasswordChallenge.fromHexString(readSecret(ADMIN_SECRET)));
		return get(path + (path.contains("?") ? "&" : "?") + "response=" + URLEncoder.encode(response, "UTF-8"));
	}

	private String requestChallenge(String path) throws Exception {
		String response = get(path);
		Matcher matcher = Pattern.compile("<challenge>([0-9a-f]+)</challenge>").matcher(response);
		if (!matcher.find()) {
			throw new IOException("Challenge response did not contain a challenge");
		}
		return matcher.group(1);
	}

	private String get(String path) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(BASE_URL + path).toURL().openConnection();
		connection.setConnectTimeout((int) TIMEOUT_MILLIS);
		connection.setReadTimeout((int) TIMEOUT_MILLIS);
		connection.setRequestMethod("GET");
		int status = connection.getResponseCode();
		InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
		String response = read(stream);
		if (status < 200 || status >= 300) {
			throw new IOException("HTTP " + status + " for " + path.split("\\?")[0]);
		}
		return response;
	}

	private String readSecret(String path) throws IOException {
		String secret = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).trim();
		if (!secret.matches("[0-9a-f]{32}")) {
			throw new IOException("Invalid MD5 secret at " + path);
		}
		return secret;
	}

	private static String read(InputStream stream) throws IOException {
		if (stream == null) {
			return "";
		}
		StringBuilder response = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			char[] buffer = new char[1024];
			int count;
			while ((count = reader.read(buffer)) >= 0) {
				response.append(buffer, 0, count);
			}
		}
		return response.toString();
	}

	private static void ensureOk(String response, String operation) throws IOException {
		if (!response.contains("<status>ok</status>")) {
			throw new IOException("Admin " + operation + " was rejected");
		}
	}

	private static long parseGameId(String value) {
		try {
			long gameId = Long.parseLong(value);
			if (gameId > 0) {
				return gameId;
			}
		} catch (NumberFormatException ignored) {
			// Produce one useful message below.
		}
		throw new IllegalArgumentException("gameId must be a positive number");
	}

	private static String safeMessage(Exception exception) {
		String message = exception.getMessage();
		return message == null ? exception.getClass().getSimpleName() : message;
	}

	private static class AcceptanceSocket extends WebSocketAdapter {
		private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

		@Override
		public void onWebSocketText(String message) {
			messages.offer(message);
		}

		@Override
		public void onWebSocketBinary(byte[] payload, int offset, int length) {
			messages.offer(new String(payload, offset, length, StandardCharsets.UTF_8));
		}

		private void close() {
			Session session = getSession();
			if (session != null && session.isOpen()) {
				session.close();
			}
		}
	}
}
