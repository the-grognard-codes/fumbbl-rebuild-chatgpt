package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.FieldCoordinateBounds;
import com.fumbbl.ffb.FactoryType;
import com.fumbbl.ffb.MoveSquare;
import com.fumbbl.ffb.PlayerAction;
import com.fumbbl.ffb.PlayerState;
import com.fumbbl.ffb.TurnMode;
import com.fumbbl.ffb.Weather;
import com.fumbbl.ffb.BlockResult;
import com.fumbbl.ffb.dialog.DialogBlockRollPropertiesParameter;
import com.fumbbl.ffb.factory.BlockResultFactory;
import com.fumbbl.ffb.factory.SkillFactory;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.RosterPlayer;
import com.fumbbl.ffb.model.Team;
import com.fumbbl.ffb.model.TurnData;
import com.fumbbl.ffb.net.commands.ClientCommandActingPlayer;
import com.fumbbl.ffb.net.commands.ClientCommandMove;
import com.fumbbl.ffb.net.commands.ClientCommandBlock;
import com.fumbbl.ffb.net.commands.ClientCommandBlockChoice;
import com.fumbbl.ffb.option.GameOptionId;
import com.fumbbl.ffb.option.GameOptionString;
import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.net.ReceivedCommand;
import com.fumbbl.ffb.server.factory.SequenceGeneratorFactory;
import com.fumbbl.ffb.server.step.generator.Select;
import com.fumbbl.ffb.server.step.generator.SequenceGenerator;
import com.fumbbl.ffb.server.util.UtilSkillBehaviours;
import com.fumbbl.ffb.server.team.bb2025.RosterCatalog;
import com.fumbbl.ffb.server.match.MatchJson;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.match.SetupApplication;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Local diagnostic match only. It deliberately exposes no fixture controls. */
public class BrowserMatchAdapter {

	/** Selected by local JVM configuration, never by a browser message. */
	public enum Fixture { MOVEMENT, BOTH_DOWN, BOTH_DOWN_BLOCK, BOTH_DOWN_AWAY, BOTH_DOWN_AWAY_BLOCK }

	public interface Connection {
		void send(String message);
	}

	private static final int PROTOCOL_VERSION = 1;
	private static final int REQUEST_HISTORY_LIMIT = 256;
	private final String homeToken;
	private final String awayToken;
	private final FantasyFootballServer server;
	private GameState gameState;
	private Fixture fixture;
	private String matchId = UUID.randomUUID().toString();
	private BrowserChoice pendingChoice;
	private final Map<Connection, String> actors = new LinkedHashMap<>();
	private final Map<String, RequestRecord> requests = new LinkedHashMap<>();
	private long revision;
	private boolean failed;
	private final BrowserTeamJson teamJson = new BrowserTeamJson(new RosterCatalog());
	private BrowserSavedTeamJson savedTeams;
	private MatchService preparedMatches;
	private final MatchJson preparedMatchJson = new MatchJson();
	private SetupApplication setup;
	private final Map<Connection, String> setupSubscriptions = new LinkedHashMap<>();

	public synchronized void setSavedTeams(BrowserSavedTeamJson savedTeams) { this.savedTeams = savedTeams; }
	public synchronized void setPreparedMatches(MatchService preparedMatches) {
		this.preparedMatches = preparedMatches;
		this.setup = new SetupApplication(server, preparedMatches);
	}

	public BrowserMatchAdapter(FantasyFootballServer server, String homeToken, String awayToken) {
		this(server, homeToken, awayToken, Fixture.MOVEMENT);
	}

	public BrowserMatchAdapter(FantasyFootballServer server, String homeToken, String awayToken, Fixture fixture) {
		if (homeToken == null || awayToken == null || homeToken.isEmpty() || awayToken.isEmpty() || homeToken.equals(awayToken)) {
			throw new IllegalArgumentException("Browser fixture requires distinct home and away tokens");
		}
		this.homeToken = homeToken;
		this.awayToken = awayToken;
		this.server = server;
		this.fixture = java.util.Objects.requireNonNull(fixture);
		this.gameState = createFixture(server);
		if (fixture != Fixture.MOVEMENT) pendingChoice = readChoice();
	}

	public synchronized void receive(Connection connection, String text) {
		JsonObject message;
		String requestId = null;
		String type;
		try {
			teamJson.checkEnvelope(text);
			message = JsonObject.readFrom(text);
			JsonValue id = message.get("requestId");
			if (id != null && id.isString()) requestId = id.asString();
			if (message.getInt("version", -1) != PROTOCOL_VERSION) {
				connection.send(result(requestId, "rejected", "UNSUPPORTED_VERSION", revision, false).toString());
				return;
			}
			if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,100}")) {
				connection.send(result(requestId, "rejected", "INVALID_REQUEST_ID", revision, false).toString());
				return;
			}
			type = message.getString("type", null);
			if ("join".equals(type)) {
				requireFields(message, "version", "type", "requestId", "token");
				message.get("token").asString();
			} else if ("move".equals(type)) {
				requireFields(message, "version", "type", "requestId", "expectedRevision", "playerId", "to");
				if (message.get("expectedRevision").asLong() < 0) throw new IllegalArgumentException();
				message.get("playerId").asString();
				JsonObject to = message.get("to").asObject();
				requireFields(to, "x", "y");
				to.get("x").asInt();
				to.get("y").asInt();
			} else if ("choice".equals(type)) {
				requireFields(message, "version", "type", "requestId", "expectedRevision", "choiceId", "optionId");
				if (message.get("expectedRevision").asLong() < 0) throw new IllegalArgumentException();
				message.get("choiceId").asString();
				message.get("optionId").asString();
			}
		} catch (RuntimeException exception) {
			connection.send(result(requestId, "rejected", "MALFORMED_MESSAGE", revision, false).toString());
			return;
		}
        if ("matchResult".equals(type)) {
            connection.send(new com.fumbbl.ffb.server.match.MatchResultJson().handle(preparedMatches, actors.get(connection), message).toString());
            return;
        }
		if ("setup".equals(type)) {
			if (!actors.containsKey(connection) || setup == null) {
				connection.send(result(requestId, "rejected", "AUTHENTICATION_REQUIRED", revision, false).toString());
				return;
			}
			JsonObject response = setup.handle(actors.get(connection), message);
			connection.send(response.toString());
			if ("ACCEPTED".equals(response.getString("code", null))) {
				String id = message.getString("matchId", null);
				setupSubscriptions.put(connection, id);
				boolean completedNow = setup.takeCompletionBroadcast(id);
				if (completedNow || !"load".equals(message.getString("operation", null)) && !response.getBoolean("duplicate", false)) {
					for (Map.Entry<Connection, String> entry : setupSubscriptions.entrySet()) {
						if (id.equals(entry.getValue()) && entry.getKey() != connection && actors.containsKey(entry.getKey())) {
							JsonObject load = new JsonObject().add("version", 1).add("type", "setup").add("operation", "load")
								.add("requestId", "broadcast").add("matchId", id);
							JsonObject view = setup.handle(actors.get(entry.getKey()), load);
							view.set("requestId", JsonValue.NULL);
							entry.getKey().send(view.toString());
						}
					}
				}
			}
			return;
		}
		if ("preparedMatch".equals(type)) {
			// The authenticated subject identifies a person; MatchService derives roles from persisted membership.
			if (!actors.containsKey(connection)) {
				connection.send(result(requestId, "rejected", "AUTHENTICATION_REQUIRED", revision, false).toString());
			} else if (preparedMatches == null) {
				connection.send(result(requestId, "rejected", "PERSISTENCE_UNAVAILABLE", revision, false).toString());
			} else connection.send((JsonValue.valueOf("activate").equals(message.get("operation"))
				? setup.activate(actors.get(connection), text)
				: preparedMatchJson.handle(preparedMatches, actors.get(connection), text)).toString());
			return;
		}
		if ("savedTeam".equals(type)) {
			if (!actors.containsKey(connection)) {
				connection.send(result(requestId, "rejected", "AUTHENTICATION_REQUIRED", revision, false).toString());
			} else if (savedTeams == null) {
				connection.send(result(requestId, "rejected", "PERSISTENCE_UNAVAILABLE", revision, false).toString());
			} else connection.send(savedTeams.handle(actors.get(connection), text).toString());
			return;
		}
		if ("catalog".equals(type) || "validateTeam".equals(type)) {
			if (!actors.containsKey(connection)) {
				connection.send(result(requestId, "rejected", "AUTHENTICATION_REQUIRED", revision, false).toString());
				return;
			}
			try {
				if ("catalog".equals(type)) {
					teamJson.fields(message, "version", "type", "requestId");
					connection.send(teamJson.catalog(requestId).toString());
				} else {
					teamJson.fields(message, "version", "type", "requestId", "draft");
					connection.send(teamJson.evaluate(requestId, message.get("draft").asObject()).toString());
				}
			} catch (RuntimeException exception) {
				connection.send(result(requestId, "rejected", "MALFORMED_TEAM_REQUEST", revision, false).toString());
			}
			return;
		}
		if (failed) {
			connection.send(result(requestId, "rejected", "FIXTURE_UNAVAILABLE", revision, false).toString());
			return;
		}
		if ("join".equals(type)) join(connection, message, requestId);
		else if ("move".equals(type) || "choice".equals(type)) action(connection, message, requestId);
		else connection.send(result(requestId, "rejected", "UNSUPPORTED_MESSAGE", revision, false).toString());
	}

	private void requireFields(JsonObject object, String... names) {
		if (object.size() != names.length || !new HashSet<>(object.names()).equals(new HashSet<>(Arrays.asList(names)))) {
			throw new IllegalArgumentException("Unexpected protocol fields");
		}
	}

	public synchronized void disconnect(Connection connection) {
		actors.remove(connection);
		setupSubscriptions.remove(connection);
	}

	/** Operator/test lifecycle only; caller serializes this on the communication worker. */
	public synchronized void resetFixture(Fixture next) {
		actors.clear();
		setupSubscriptions.clear();
		requests.clear();
		pendingChoice = null;
		gameState = null;
		failed = true;
		revision = 0;
		matchId = UUID.randomUUID().toString();
		fixture = java.util.Objects.requireNonNull(next);
		gameState = createFixture(server);
		if (fixture != Fixture.MOVEMENT) pendingChoice = readChoice();
		failed = false;
	}

	/** Sanitized operator measurements; never included in gameplay messages. */
	public synchronized JsonObject measurements() {
		return new JsonObject().add("matchId", matchId).add("revision", revision)
			.add("historyEntries", requests.size()).add("authorizedConnections", actors.size())
			.add("fixture", fixture.name()).add("failed", failed);
	}

	private void join(Connection connection, JsonObject message, String requestId) {
		String token = message.getString("token", null);
		String actor = homeToken.equals(token) ? "home" : awayToken.equals(token) ? "away" : null;
		if (actor == null) {
			connection.send(result(requestId, "rejected", "AUTHENTICATION_FAILED", revision, false).toString());
			return;
		}
		if (actors.containsKey(connection) && !actors.get(connection).equals(actor)) {
			connection.send(result(requestId, "rejected", "IDENTITY_REBINDING_FORBIDDEN", revision, false).toString()); return;
		}
		actors.put(connection, actor);
		connection.send(result(requestId, "accepted", "JOINED", revision, false).toString());
		connection.send(snapshot(actor).toString());
	}

	private void action(Connection connection, JsonObject message, String requestId) {
		String actor = actors.get(connection);
		if (actor == null) {
			connection.send(result(requestId, "rejected", "AUTHENTICATION_REQUIRED", revision, false).toString());
			return;
		}
		// Normalize property order: JSON objects are unordered, request identity is semantic.
		boolean choice = "choice".equals(message.getString("type", null));
		String fingerprint = choice ? new JsonObject().add("type", "choice").add("expectedRevision", message.get("expectedRevision"))
			.add("choiceId", message.get("choiceId")).add("optionId", message.get("optionId")).toString()
			: new JsonObject().add("type", "move").add("expectedRevision", message.get("expectedRevision"))
			.add("playerId", message.get("playerId")).add("x", message.get("to").asObject().get("x"))
			.add("y", message.get("to").asObject().get("y")).toString();
		RequestRecord prior = requests.get(actor + "\\n" + requestId);
		if (prior != null) {
			if (!prior.payload.equals(fingerprint)) {
				connection.send(result(requestId, "rejected", "REQUEST_ID_REUSED", revision, false).toString());
				return;
			}
			connection.send(result(requestId, prior.accepted ? "accepted" : "rejected", prior.code, prior.revision,
				prior.accepted).toString());
			return;
		}
		if (requests.size() >= REQUEST_HISTORY_LIMIT) {
			connection.send(result(requestId, "rejected", "REQUEST_HISTORY_LIMIT", revision, false).toString());
			return;
		}
		long expectedRevision = message.getLong("expectedRevision", -1L);
		if (expectedRevision != revision) {
			record(actor, requestId, fingerprint, false, "STALE_REVISION", revision);
			connection.send(result(requestId, "rejected", "STALE_REVISION", revision, false).toString());
			return;
		}
		String playerId = message.getString("playerId", null);
		JsonObject to = message.get("to") != null && message.get("to").isObject() ? message.get("to").asObject() : null;
		String failure = choice ? validateChoice(actor, message) : validateMove(actor, playerId, to);
		if (failure != null) {
			record(actor, requestId, fingerprint, false, failure, revision);
			connection.send(result(requestId, "rejected", failure, revision, false).toString());
			return;
		}
		try {
			if (choice) {
				gameState.handleCommand(new ReceivedCommand(new ClientCommandBlockChoice(
					pendingChoice.indexOf(message.getString("optionId", null))), null));
				if (gameState.getGame().getDialogParameter() != null) {
					throw new IllegalStateException("Unexpected dialog after controlled Both Down choice");
				}
				pendingChoice = null;
			} else {
				applyMove(playerId, to);
			}
		} catch (RuntimeException exception) {
			failed = true;
			throw exception;
		}
		revision++;
		String code = choice ? "CHOICE_APPLIED" : "MOVED";
		record(actor, requestId, fingerprint, true, code, revision);
		connection.send(result(requestId, "accepted", code, revision, false).toString());
		for (Map.Entry<Connection, String> entry : actors.entrySet()) {
			entry.getKey().send(snapshot(entry.getValue()).toString());
		}
	}

	private void applyMove(String playerId, JsonObject to) {
		FieldCoordinate from = gameState.getGame().getFieldModel().getPlayerCoordinate(gameState.getGame().getPlayerById(playerId));
		FieldCoordinate target = new FieldCoordinate(to.getInt("x", -1), to.getInt("y", -1));
		int movementBefore = gameState.getGame().getActingPlayer().getCurrentMove();
		try {
			// The isolated fixture has no legacy sessions. Null is the harness's trusted
			// internal home command; browser identity has already been checked above.
			gameState.handleCommand(new ReceivedCommand(
				new ClientCommandMove(playerId, from, new FieldCoordinate[]{target}, null), null));
			if (!target.equals(gameState.getGame().getFieldModel().getPlayerCoordinate(gameState.getGame().getPlayerById(playerId)))
				|| gameState.getGame().getActingPlayer().getCurrentMove() != movementBefore + 1) {
				throw new IllegalStateException("BB2025 movement postcondition failed");
			}
		} catch (RuntimeException exception) {
			// Never report a malformed/rejected request after partial engine execution.
			// Quarantine this disposable fixture; socket closes and operator restarts it.
			failed = true;
			throw exception;
		}
	}

	private String validateChoice(String actor, JsonObject message) {
		if (pendingChoice == null) return "NO_PENDING_CHOICE";
		if (!pendingChoice.getActor().equals(actor)) return "WRONG_CHOICE_ACTOR";
		if (!pendingChoice.getId().equals(message.getString("choiceId", null))) return "CHOICE_MISMATCH";
		if (pendingChoice.indexOf(message.getString("optionId", null)) < 0) return "INVALID_OPTION";
		return null;
	}

	private BrowserChoice readChoice() {
		Game game = gameState.getGame();
		if (!(game.getDialogParameter() instanceof DialogBlockRollPropertiesParameter)) {
			throw new IllegalStateException("Expected engine block dialog");
		}
		DialogBlockRollPropertiesParameter dialog = (DialogBlockRollPropertiesParameter) game.getDialogParameter();
		if (dialog.hasActualReRoll()) throw new IllegalStateException("Fixture must expose every available choice");
		String owner = game.getTeamHome().getId().equals(dialog.getChoosingTeamId()) ? "home"
			: game.getTeamAway().getId().equals(dialog.getChoosingTeamId()) ? "away" : null;
		if (owner == null) throw new IllegalStateException("Unknown choosing team");
		BlockResultFactory factory = game.getFactory(FactoryType.Factory.BLOCK_RESULT);
		for (int roll : dialog.getBlockRoll()) {
			if (factory.forRoll(roll) != BlockResult.BOTH_DOWN) throw new IllegalStateException("Unexpected controlled roll");
		}
		return new BrowserChoice(matchId + "-choice-0", owner, revision, dialog.getBlockRoll().length);
	}

	private String validateMove(String actor, String playerId, JsonObject to) {
		if (pendingChoice != null) return "CHOICE_PENDING";
		if (fixture != Fixture.MOVEMENT) return "FIXTURE_FINISHED";
		Game game = gameState.getGame();
		if (!"home".equals(actor) || !game.isHomePlaying()) return "WRONG_TURN";
		if (!"home-runner".equals(playerId)) return "WRONG_PLAYER";
		if (to == null) return "INVALID_DESTINATION";
		FieldCoordinate target = new FieldCoordinate(to.getInt("x", -1), to.getInt("y", -1));
		if (!FieldCoordinateBounds.FIELD.isInBounds(target)) return "OFF_BOARD";
		FieldCoordinate from = game.getFieldModel().getPlayerCoordinate(game.getPlayerById(playerId));
		if (from.distanceInSteps(target) != 1) return "NOT_ADJACENT";
		if (game.getFieldModel().getPlayer(target) != null) return "OCCUPIED";
		if (game.getActingPlayer().getCurrentMove() >= game.getActingPlayer().getPlayer().getMovementWithModifiers()) return "MOVEMENT_EXHAUSTED";
		if (target.equals(game.getFieldModel().getBallCoordinate())) return "UNSUPPORTED_DICE";
		MoveSquare moveSquare = game.getFieldModel().getMoveSquare(target);
		if (moveSquare == null) return "ILLEGAL_MOVE";
		if (moveSquare.getMinimumRollDodge() > 0 || moveSquare.getMinimumRollGoForIt() > 0) return "UNSUPPORTED_DICE";
		return null;
	}

	private void record(String actor, String requestId, String payload, boolean accepted, String code, long responseRevision) {
		requests.put(actor + "\\n" + requestId, new RequestRecord(payload, accepted, code, responseRevision));
	}

	private JsonObject result(String requestId, String status, String code, long responseRevision, boolean duplicate) {
		return new JsonObject().add("version", PROTOCOL_VERSION).add("type", "result").add("requestId", requestId)
			.add("status", status).add("code", code).add("revision", responseRevision).add("duplicate", duplicate);
	}

	private JsonObject snapshot(String actor) {
		Game game = gameState.getGame();
		JsonArray players = new JsonArray();
		for (Player<?> player : game.getPlayers()) {
			FieldCoordinate coordinate = game.getFieldModel().getPlayerCoordinate(player);
			if (coordinate != null) players.add(new JsonObject().add("id", player.getId())
				.add("role", game.getTeamHome().hasPlayer(player) ? "home" : "away").add("x", coordinate.getX()).add("y", coordinate.getY())
				.add("movementUsed", player.getId().equals(game.getActingPlayer().getPlayerId()) ? game.getActingPlayer().getCurrentMove() : 0)
				.add("movementAllowance", player.getMovementWithModifiers())
				.add("state", playerState(game.getFieldModel().getPlayerState(player).getBase())));
		}
		FieldCoordinate ball = game.getFieldModel().getBallCoordinate();
		return new JsonObject().add("version", PROTOCOL_VERSION).add("type", "snapshot").add("revision", revision)
			.add("matchId", matchId).add("actor", actor).add("players", players)
			.add("ball", ball == null ? JsonValue.NULL : new JsonObject().add("x", ball.getX()).add("y", ball.getY()))
			.add("turnOwner", game.isHomePlaying() ? "home" : "away")
			.add("prompt", pendingChoice == null ? JsonValue.NULL : pendingChoice.toJson())
			.add("resources", new JsonObject().add("home", resources(game.getTurnDataHome())).add("away", resources(game.getTurnDataAway())));
	}

	private String playerState(int base) {
		if (base == PlayerState.STANDING) return "standing";
		if (base == PlayerState.PRONE) return "prone";
		if (base == PlayerState.STUNNED) return "stunned";
		return "other";
	}

	private JsonObject resources(TurnData turn) {
		return new JsonObject().add("rerolls", turn.getReRolls()).add("rerollUsed", turn.isReRollUsed())
			.add("blitzUsed", turn.isBlitzUsed()).add("passUsed", turn.isPassUsed())
			.add("handOverUsed", turn.isHandOverUsed()).add("foulUsed", turn.isFoulUsed());
	}

	private GameState createFixture(FantasyFootballServer server) {
		GameState state = new GameState(server);
		Game game = state.getGame();
		// Never register this fixture in GameCache/SessionManager/JDBC or legacy joins.
		game.setId(-1);
		game.getOptions().addOption(new GameOptionString(GameOptionId.RULESVERSION).setValue("BB2025"));
		game.initializeRules();
		state.initRulesDependentMembers();
		UtilSkillBehaviours.registerBehaviours(game, server.getDebugLog());
		Team home = team(server, "homeTeam", "Home");
		Team away = team(server, "awayTeam", "Away");
		game.setTeamHome(home);
		game.setTeamAway(away);
		boolean block = fixture != Fixture.MOVEMENT;
		game.getTurnDataHome().setReRolls(block ? 0 : 3);
		game.getTurnDataAway().setReRolls(block ? 0 : 3);
		addPlayer(game, home, "home-runner", block ? 7 : 5, 7);
		addPlayer(game, away, "away-runner", block ? 8 : 20, 7);
		if (fixture == Fixture.BOTH_DOWN_BLOCK || fixture == Fixture.BOTH_DOWN_AWAY_BLOCK) {
			SkillFactory skills = game.getFactory(FactoryType.Factory.SKILL);
			((RosterPlayer) game.getPlayerById("home-runner")).addSkill(skills.forName("Block"));
		}
		if (fixture == Fixture.BOTH_DOWN_AWAY || fixture == Fixture.BOTH_DOWN_AWAY_BLOCK) {
			((RosterPlayer) game.getPlayerById("home-runner")).setStrength(2);
		}
		if (block) {
			state.getDiceRoller().addTestRoll("bothdown", game, home);
			if (fixture == Fixture.BOTH_DOWN_AWAY || fixture == Fixture.BOTH_DOWN_AWAY_BLOCK) {
				state.getDiceRoller().addTestRoll("bothdown", game, home);
			}
			for (int index = 0; index < (fixture == Fixture.BOTH_DOWN_BLOCK || fixture == Fixture.BOTH_DOWN_AWAY_BLOCK ? 2 : 4); index++) {
				state.getDiceRoller().addTestRoll("2", game, home);
			}
		}
		game.getFieldModel().setBallInPlay(true);
		game.getFieldModel().setBallCoordinate(new FieldCoordinate(12, 7));
		game.getFieldModel().setWeather(Weather.NICE);
		game.setHomePlaying(true);
		game.setTurnMode(TurnMode.REGULAR);
		SequenceGeneratorFactory factory = game.getFactory(FactoryType.Factory.SEQUENCE_GENERATOR);
		Select select = (Select) factory.forName(SequenceGenerator.Type.Select.name());
		select.pushSequence(new Select.SequenceParams(state, false));
		state.startNextStep();
		state.handleCommand(new ReceivedCommand(
			new ClientCommandActingPlayer("home-runner", block ? PlayerAction.BLOCK : PlayerAction.MOVE, false), null));
		if (block) state.handleCommand(new ReceivedCommand(new ClientCommandBlock("home-runner", "away-runner",
			false, false, false, false, false), null));
		return state;
	}

	private Team team(FantasyFootballServer server, String id, String name) {
		Team team = new Team(server);
		team.setId(id);
		team.setName(name);
		return team;
	}

	private void addPlayer(Game game, Team team, String id, int x, int y) {
		RosterPlayer player = new RosterPlayer();
		player.setId(id);
		player.setName(id);
		player.setTeam(team);
		player.setNr(team.getMaxPlayerNr() + 1);
		player.setMovement(6);
		player.setStrength(3);
		player.setAgility(3);
		player.setPassing(5);
		player.setArmour(8);
		team.addPlayer(player);
		game.getFieldModel().setPlayerState(player, new PlayerState(PlayerState.STANDING).changeActive(true));
		game.getFieldModel().setPlayerCoordinate(player, new FieldCoordinate(x, y));
	}

	private static class RequestRecord {
		private final String payload;
		private final boolean accepted;
		private final String code;
		private final long revision;

		private RequestRecord(String payload, boolean accepted, String code, long revision) {
			this.payload = payload;
			this.accepted = accepted;
			this.code = code;
			this.revision = revision;
		}
	}
}
