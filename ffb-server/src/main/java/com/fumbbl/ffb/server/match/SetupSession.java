package com.fumbbl.ffb.server.match;

import com.fumbbl.ffb.FactoryType;
import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.FieldCoordinateBounds;
import com.fumbbl.ffb.IDialogParameter;
import com.fumbbl.ffb.PlayerState;
import com.fumbbl.ffb.TurnMode;
import com.fumbbl.ffb.dialog.DialogReceiveChoiceParameter;
import com.fumbbl.ffb.factory.MechanicsFactory;
import com.fumbbl.ffb.mechanics.Mechanic;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.Team;
import com.fumbbl.ffb.net.commands.ClientCommand;
import com.fumbbl.ffb.net.commands.ClientCommandCoinChoice;
import com.fumbbl.ffb.net.commands.ClientCommandEndTurn;
import com.fumbbl.ffb.net.commands.ClientCommandReceiveChoice;
import com.fumbbl.ffb.net.commands.ClientCommandSetupPlayer;
import com.fumbbl.ffb.option.GameOptionBoolean;
import com.fumbbl.ffb.option.GameOptionId;
import com.fumbbl.ffb.option.GameOptionString;
import com.fumbbl.ffb.server.FantasyFootballServer;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.factory.SequenceGeneratorFactory;
import com.fumbbl.ffb.server.match.CoreTurnActions.Action;
import com.fumbbl.ffb.server.mechanic.SetupMechanic;
import com.fumbbl.ffb.server.net.ReceivedCommand;
import com.fumbbl.ffb.server.step.StepId;
import com.fumbbl.ffb.server.step.generator.SequenceGenerator;
import com.fumbbl.ffb.server.step.generator.StartGame;
import com.fumbbl.ffb.server.util.UtilSkillBehaviours;
import com.fumbbl.ffb.util.UtilBox;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One in-memory engine lifetime. Construction is allowed only after a fresh durable activation. */
public final class SetupSession {
	private final GameState state;
	private final String matchId;
	private final Map<String, Record> history = new LinkedHashMap<>();
	private final Set<String> kickoffSelection = new LinkedHashSet<>();
	private int revision;
	private int drive = 1;
	private int replayBytes;
	private final JsonArray events = new JsonArray();
	private final MatchDocument document;
	private boolean failed;

	public SetupSession(FantasyFootballServer server, MatchDocument document, long engineId) {
		this.document = document;
		matchId = document.matchId;
		state = new GameState(server) {
			@Override public boolean usesLegacyPersistence() { return false; }
		};
		Game game = state.getGame();
		game.setId(engineId);
		game.getOptions().addOption(new GameOptionString(GameOptionId.RULESVERSION).setValue("BB2025"));
		// The frozen exhibition preset excludes all inducement/prayer purchases.
		game.getOptions().addOption(new GameOptionBoolean(GameOptionId.USE_PREDEFINED_INDUCEMENTS).setValue(true));
		game.getOptions().addOption(new GameOptionBoolean(GameOptionId.INDUCEMENT_PRAYERS_AVAILABLE_FOR_UNDERDOG).setValue(false));
		game.getOptions().addOption(new GameOptionBoolean(GameOptionId.OVERTIME).setValue(false));
		game.initializeRules();
		state.initRulesDependentMembers();
		UtilSkillBehaviours.registerBehaviours(game, server.getDebugLog());
		FrozenTeamEngineConverter converter = new FrozenTeamEngineConverter();
		game.setTeamHome(converter.convert(document.home.team, game.getRules()));
		game.setTeamAway(converter.convert(document.away.team, game.getRules()));
		initializeTeam(game.getTeamHome(), document.home.owner, "Home");
		initializeTeam(game.getTeamAway(), document.away.owner, "Away");
		UtilBox.refreshBoxes(game);
		game.setHomePlaying(false);
		game.setTurnMode(TurnMode.START_GAME);
		// Persisted preparation and explicit activation replace the legacy lobby's start acknowledgement.
		game.setStarted(new Date());
		SequenceGeneratorFactory factory = game.getFactory(FactoryType.Factory.SEQUENCE_GENERATOR);
		((StartGame) factory.forName(SequenceGenerator.Type.StartGame.name()))
			.pushSequence(new SequenceGenerator.SequenceParams(state));
		state.startNextStep();
		assertSupported();
		recordEvent("START");
	}

	private void initializeTeam(Team team, String owner, String name) {
		team.setCoach(owner); team.setName(name);
		for (Player<?> player : team.getPlayers()) {
			state.getGame().getFieldModel().setPlayerState(player, new PlayerState(PlayerState.RESERVE));
			UtilBox.putPlayerIntoBox(state.getGame(), player);
		}
	}

	public JsonObject apply(String role, JsonObject request) {
		String id = request.getString("requestId", null);
		String key = role + "\n" + id;
		String fingerprint = canonical(request);
		Record prior = history.get(key);
		if (prior != null) {
			if (!prior.fingerprint.equals(fingerprint)) throw new MatchService.Failure("REQUEST_ID_REUSED");
			return reply(id, prior.code, true, role);
		}
		if (failed) throw new MatchService.Failure("SESSION_UNAVAILABLE");
		if (isComplete()) throw new MatchService.Failure("MATCH_COMPLETED");
		// Reserve 128 KiB: one bounded 64 KiB projection plus the envelope and up to 8,193 array separators.
		if (replayBytes > 16 * 1024 * 1024 - 128 * 1024) throw new MatchService.Failure("REPLAY_LIMIT");
		if (history.size() >= 8192) throw new MatchService.Failure("REQUEST_HISTORY_LIMIT");
		if (request.get("expectedRevision").asInt() != revision) throw new MatchService.Failure("STALE_REVISION");
		if (!"action".equals(request.getString("operation", null)) && !role.equals(actor())) throw new MatchService.Failure("WRONG_ACTOR");
		String operation = request.getString("operation", null);
		ClientCommand command;
		Game game = state.getGame();
		if ("action".equals(operation)) {
            Action selected = null;
            for (Action action : actions()) if (actionId(action).equals(request.getString("actionId", null))) selected = action;
            if (selected == null) throw new MatchService.Failure("INVALID_OPTION");
            if (!role.equals(selected.role)) throw new MatchService.Failure("WRONG_ACTOR");
            command = selected.command;
            if (command == null) {
                if (!selected.id.startsWith("event-pick:")) throw new MatchService.Failure("INVALID_OPTION");
                String player = selected.id.substring("event-pick:".length());
                if (!kickoffSelection.remove(player)) kickoffSelection.add(player);
                revision++;
                history.put(key, new Record(fingerprint, "ACCEPTED"));
                recordEvent("SELECTION");
                return reply(id, "ACCEPTED", false, role);
            }
            if ("confirm-solid-defence".equals(selected.id)) {
                IDialogParameter previous = game.getDialogParameter();
                if (!mechanic().checkSetup(state, game.isHomePlaying(), state.getKickingSwarmers())) {
                    game.setDialogParameter(previous);
                    throw new MatchService.Failure("ILLEGAL_SETUP");
                }
            }
        } else if ("choice".equals(operation)) {
			if (!promptId().equals(request.getString("promptId", null))) throw new MatchService.Failure("PROMPT_MISMATCH");
			String option = request.getString("optionId", null);
			if (step() == StepId.COIN_CHOICE && ("heads".equals(option) || "tails".equals(option)))
				command = new ClientCommandCoinChoice("heads".equals(option));
			else if (step() == StepId.RECEIVE_CHOICE && ("receive".equals(option) || "kick".equals(option)))
				command = new ClientCommandReceiveChoice("receive".equals(option));
			else throw new MatchService.Failure("INVALID_OPTION");
		} else if ("place".equals(operation)) {
			if (step() != StepId.SETUP) throw new MatchService.Failure("WRONG_PHASE");
			Player<?> player = game.getPlayerById(request.getString("playerId", null));
			Team team = "home".equals(role) ? game.getTeamHome() : game.getTeamAway();
			if (player == null || !team.hasPlayer(player)) throw new MatchService.Failure("WRONG_PLAYER");
			JsonValue to = request.get("to");
			FieldCoordinate coordinate;
			if (to.isNull()) {
				int box = "home".equals(role) ? FieldCoordinate.RSV_HOME_X : FieldCoordinate.RSV_AWAY_X;
				int y = 0;
				while (game.getFieldModel().getPlayer(new FieldCoordinate(box, y)) != null) y++;
				coordinate = new FieldCoordinate(box, y);
			}
			else {
				coordinate = new FieldCoordinate(to.asObject().get("x").asInt(), to.asObject().get("y").asInt());
				FieldCoordinateBounds half = "home".equals(role) ? FieldCoordinateBounds.HALF_HOME : FieldCoordinateBounds.HALF_AWAY;
				if (!half.isInBounds(coordinate) || game.getFieldModel().getPlayer(coordinate) != null)
					throw new MatchService.Failure("ILLEGAL_PLACEMENT");
			}
			command = new ClientCommandSetupPlayer(player.getId(), "home".equals(role) ? coordinate : coordinate.transform());
		} else if ("confirm".equals(operation)) {
			if (step() != StepId.SETUP) throw new MatchService.Failure("WRONG_PHASE");
			// The retained engine checks the exact current formation. Restore its temporary error dialog on rejection.
			IDialogParameter previous = game.getDialogParameter();
			if (!mechanic().checkSetup(state, game.isHomePlaying())) {
				game.setDialogParameter(previous);
				history.put(key, new Record(fingerprint, "ILLEGAL_SETUP"));
				return reply(id, "ILLEGAL_SETUP", false, role);
			}
			command = new ClientCommandEndTurn(TurnMode.SETUP, null);
		} else throw new MatchService.Failure("INVALID_REQUEST");
		int oldHalf = game.getHalf();
		int oldScore = homeScore() + awayScore();
		try {
			// No legacy socket is registered for these private engine IDs. Authorization above is persisted-role based.
			state.handleCommand(new ReceivedCommand(command, "home".equals(role)));
            kickoffSelection.clear();
			assertSupported();
			revision++;
			history.put(key, new Record(fingerprint, "ACCEPTED"));
			boolean newHalf = oldHalf > 0 && game.getHalf() != oldHalf;
			boolean touchdown = homeScore() + awayScore() != oldScore;
			if (!isComplete() && (newHalf || touchdown)) drive++;
			recordEvent(isComplete() ? "FULL_TIME" : newHalf ? "HALFTIME" : touchdown ? "TOUCHDOWN" : "ACTION");
			return reply(id, "ACCEPTED", false, role);
		} catch (RuntimeException failure) {
			failed = true;
            MatchService.Failure unavailable = new MatchService.Failure("SESSION_UNAVAILABLE");
            unavailable.initCause(failure);
            throw unavailable;
		}
	}

	public JsonObject reply(String requestId, String code, boolean duplicate, String role) {
		return new JsonObject().add("version", 1).add("type", "setupState").add("requestId", requestId)
			.add("code", failed ? "SESSION_UNAVAILABLE" : code).add("duplicate", !failed && duplicate)
			.add("state", failed ? JsonValue.NULL : view(role));
	}

	private JsonObject view(String role) {
		Game game = state.getGame();
		JsonArray players = new JsonArray();
		for (Team team : new Team[] { game.getTeamHome(), game.getTeamAway() }) {
			for (Player<?> player : team.getPlayers()) {
				FieldCoordinate at = game.getFieldModel().getPlayerCoordinate(player);
				boolean onPitch = FieldCoordinateBounds.FIELD.isInBounds(at);
				players.add(new JsonObject().add("id", player.getId()).add("name", player.getName()).add("slot", player.getNr())
					.add("role", team == game.getTeamHome() ? "home" : "away")
                    .add("state", game.getFieldModel().getPlayerState(player).getDescription())
					.add("x", onPitch ? JsonValue.valueOf(at.getX()) : JsonValue.NULL)
					.add("y", onPitch ? JsonValue.valueOf(at.getY()) : JsonValue.NULL));
			}
		}
		JsonValue prompt = JsonValue.NULL;
		if (step() == StepId.COIN_CHOICE || step() == StepId.RECEIVE_CHOICE) {
			boolean coin = step() == StepId.COIN_CHOICE;
			prompt = new JsonObject().add("id", promptId()).add("actor", actor()).add("kind", coin ? "coin" : "receive")
				.add("options", new JsonArray().add(coin ? "heads" : "receive").add(coin ? "tails" : "kick"));
		}
		JsonArray legal = new JsonArray();
        for (Action action : actions()) legal.add(new JsonObject().add("id", actionId(action)).add("kind", action.kind)
            .add("label", action.label).add("actor", action.role));
        FieldCoordinate ball = game.getFieldModel().getBallCoordinate();
        return new JsonObject().add("half", Math.max(1, Math.min(2, game.getHalf()))).add("drive", drive)
            .add("homeScore", homeScore()).add("awayScore", awayScore())
            .add("homeTurn", game.getTurnDataHome().getTurnNr()).add("awayTurn", game.getTurnDataAway().getTurnNr())
            .add("actions", legal).add("turn", game.getTurnData().getTurnNr()).add("turnMode", game.getTurnMode().name())
            .add("activePlayerId", game.getActingPlayer().getPlayerId())
            .add("ball", FieldCoordinateBounds.FIELD.isInBounds(ball) ? new JsonObject().add("x", ball.getX()).add("y", ball.getY()) : JsonValue.NULL)
            .add("matchId", matchId).add("revision", revision).add("callerRole", role)
			.add("phase", isComplete() ? "FULL_TIME" : step() == StepId.KICKOFF ? "READY_FOR_KICKOFF" : step() == StepId.SETUP ? "SETUP" : step() == StepId.COIN_CHOICE || step() == StepId.RECEIVE_CHOICE ? "PRE_MATCH" : "PLAY")
			.add("actor", actor()).add("prompt", prompt).add("players", players)
			.add("weather", game.getFieldModel().getWeather().name())
			.add("homeRerolls", game.getTurnDataHome().getReRolls()).add("awayRerolls", game.getTurnDataAway().getReRolls());
	}

	private String actor() {
        List<Action> available = actions();
        if (!available.isEmpty()) return available.get(0).role;
		if (step() == StepId.RECEIVE_CHOICE) {
			String team = ((DialogReceiveChoiceParameter) state.getGame().getDialogParameter()).getChoosingTeamId();
			return team.equals(state.getGame().getTeamHome().getId()) ? "home" : "away";
		}
		return state.getGame().isHomePlaying() ? "home" : "away";
	}
	private String promptId() { return matchId + "-" + revision; }
	private StepId step() { return state.getCurrentStep() == null ? StepId.END_GAME : state.getCurrentStep().getId(); }
    private void assertSupported() {
        if (!isComplete() && state.getCurrentStep() == null) throw new IllegalStateException("No resident engine step");
    }
    private String actionId(Action action) { return revision + ":" + action.id; }
    private List<Action> actions() {
        if (isComplete()) return java.util.Collections.emptyList();
        List<Action> result = new KickoffActions(state, kickoffSelection).actions();
        if (result.isEmpty()) result = new CorePromptActions(state).actions();
        if (result.isEmpty()) result = new CoreTurnActions(state).actions();
        return result;
    }

    public boolean isComplete() { return state.getGame().getFinished() != null; }
    private int homeScore() { return state.getGame().getGameResult().getTeamResultHome().getScore(); }
    private int awayScore() { return state.getGame().getGameResult().getTeamResultAway().getScore(); }
    private void recordEvent(String kind) {
        JsonObject snapshot = view("home").set("actions", new JsonArray()).set("prompt", JsonValue.NULL);
        JsonObject event = new JsonObject().add("revision", revision).add("kind", kind).add("state", snapshot);
        replayBytes += event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        events.add(event);
    }
    public CompletedMatch completedMatch() {
        if (!isComplete() || failed) throw new MatchService.Failure("NOT_COMPLETED");
        return new CompletedMatch(new JsonObject().add("formatVersion", 1)
            .add("engineVersion", "ffb-3.4.0-bb2025-m3d.1").add("ruleset", document.home.team.ruleset)
            .add("catalogVersion", document.home.team.catalogVersion).add("presetId", document.home.team.presetId)
            .add("presetVersion", document.home.team.presetVersion).add("matchId", matchId)
            .add("homeScore", homeScore()).add("awayScore", awayScore()).add("finalRevision", revision)
            .add("events", events).toString());
    }
	private SetupMechanic mechanic() {
		MechanicsFactory factory = state.getGame().getFactory(FactoryType.Factory.MECHANIC);
		return (SetupMechanic) factory.forName(Mechanic.Type.SETUP.name());
	}
	private String canonical(JsonObject request) {
		JsonObject result = new JsonObject();
		request.names().stream().sorted().forEach(name -> {
			JsonValue value = request.get(name);
			if ("to".equals(name) && !value.isNull()) value = new JsonObject().add("x", value.asObject().get("x")).add("y", value.asObject().get("y"));
			result.add(name, value);
		});
		return result.toString();
	}
	private static final class Record {
		final String fingerprint, code;
		Record(String fingerprint, String code) { this.fingerprint = fingerprint; this.code = code; }
	}
}
