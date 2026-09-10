package com.fumbbl.ffb.server.match;

import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.FieldCoordinateBounds;
import com.fumbbl.ffb.PlayerChoiceMode;
import com.fumbbl.ffb.PlayerState;
import com.fumbbl.ffb.TurnMode;
import com.fumbbl.ffb.dialog.DialogPlayerChoiceParameter;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.Team;
import com.fumbbl.ffb.model.property.NamedProperties;
import com.fumbbl.ffb.net.commands.ClientCommandEndTurn;
import com.fumbbl.ffb.net.commands.ClientCommandKickoff;
import com.fumbbl.ffb.net.commands.ClientCommandPlayerChoice;
import com.fumbbl.ffb.net.commands.ClientCommandSetupPlayer;
import com.fumbbl.ffb.net.commands.ClientCommandTouchback;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.match.CoreTurnActions.Action;
import com.fumbbl.ffb.server.step.StepId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects bounded kickoff decisions without rolling or changing engine state. */
public final class KickoffActions {
    private final GameState state;
    private final Set<String> selectedPlayers;
    public KickoffActions(GameState state) { this(state, java.util.Collections.<String>emptySet()); }
    public KickoffActions(GameState state, Set<String> selectedPlayers) {
        this.state = state;
        this.selectedPlayers = selectedPlayers;
    }
    public List<Action> actions() {
        List<Action> actions = new ArrayList<>();
        Game game = state.getGame();
        String role = game.isHomePlaying() ? "home" : "away";
        if (state.getCurrentStep().getId() == StepId.KICKOFF) {
            for (int x = game.isHomePlaying() ? 13 : 0; x < (game.isHomePlaying() ? 26 : 13); x++) {
                for (int y = 0; y < 15; y++) {
                    FieldCoordinate to = new FieldCoordinate(x, y);
                    actions.add(new Action("kick-" + x + "-" + y, "kickoff", "Kick to " + x + ", " + y, role,
                        new ClientCommandKickoff(oriented(to, role))));
                }
            }
        } else if (game.getTurnMode() == TurnMode.TOUCHBACK) {
            // Kickoff retains the kicking side as active; receiving side places the touchback.
            role = game.isHomePlaying() ? "away" : "home";
            Team receiving = game.getOtherTeam(game.getActingTeam());
            for (Player<?> player : receiving.getPlayers()) {
                FieldCoordinate to = game.getFieldModel().getPlayerCoordinate(player);
                if (FieldCoordinateBounds.FIELD.isInBounds(to) && game.getFieldModel().getPlayerState(player).hasTacklezones()
                    && !player.hasSkillProperty(NamedProperties.preventHoldBall)) {
                    actions.add(new Action("touch-" + player.getId(), "touchback", "Give ball to " + player.getName(), role,
                        new ClientCommandTouchback(oriented(to, role))));
                }
            }
            if (actions.isEmpty()) for (int x = 0; x < 26; x++) for (int y = 0; y < 15; y++) {
                FieldCoordinate to = new FieldCoordinate(x, y);
                actions.add(new Action("touch-" + x + "-" + y, "touchback", "Place ball at " + x + ", " + y, role,
                    new ClientCommandTouchback(oriented(to, role))));
            }
        } else if (state.getCurrentStep().getId() == StepId.APPLY_KICKOFF_RESULT && game.getTurnMode() != TurnMode.SOLID_DEFENCE && game.getDialogParameter() instanceof DialogPlayerChoiceParameter) {
            DialogPlayerChoiceParameter dialog = (DialogPlayerChoiceParameter) game.getDialogParameter();
            if (dialog.getPlayerChoiceMode() == PlayerChoiceMode.SOLID_DEFENCE || dialog.getPlayerChoiceMode() == PlayerChoiceMode.CHARGE) {
                role = game.getTeamHome().getId().equals(dialog.getTeamId()) ? "home" : "away";
                List<Player<?>> eligible = eligible(game, dialog);
                for (Player<?> player : eligible) {
                    boolean selected = selectedPlayers.contains(player.getId());
                    if (!selected && selectedPlayers.size() >= dialog.getMaxSelects()) continue;
                    actions.add(new Action("event-pick:" + player.getId(), "kickoffChoice",
                        (selected ? "Deselect " : "Select ") + player.getName(), role, null));
                }
                List<Player<?>> selected = selected(eligible);
                if (selected.size() >= dialog.getMinSelects() && selected.size() <= dialog.getMaxSelects()) {
                    actions.add(new Action("event-confirm", "kickoffChoice", "Confirm " + dialog.getPlayerChoiceMode().name(), role,
                        new ClientCommandPlayerChoice(dialog.getPlayerChoiceMode(), selected.toArray(new Player<?>[0]))));
                }
                if (dialog.getMinSelects() == 0) actions.add(new Action("decline-event", "kickoffChoice",
                    "Decline " + dialog.getPlayerChoiceMode().name(), role,
                    new ClientCommandPlayerChoice(dialog.getPlayerChoiceMode(), new Player<?>[0])));
            }
        } else if (game.getTurnMode() == TurnMode.SOLID_DEFENCE) {
            solidDefence(actions, game, role);

        } else if (game.getTurnMode() == TurnMode.HIGH_KICK || game.getTurnMode() == TurnMode.QUICK_SNAP) {
            actions.add(new Action("end-event", "kickoffChoice", "Finish " + game.getTurnMode().name(), role,
                new ClientCommandEndTurn(game.getTurnMode(), null)));
            for (Player<?> player : game.getActingTeam().getPlayers()) {
                FieldCoordinate from = game.getFieldModel().getPlayerCoordinate(player);
                if (!FieldCoordinateBounds.FIELD.isInBounds(from) || !game.getFieldModel().getPlayerState(player).isActive()) continue;
                for (int x = 0; x < 26; x++) for (int y = 0; y < 15; y++) {
                    FieldCoordinate to = new FieldCoordinate(x, y);
                    boolean legal = game.getFieldModel().getPlayer(to) == null && (game.getTurnMode() == TurnMode.HIGH_KICK
                        ? to.equals(game.getFieldModel().getBallCoordinate()) : Math.max(Math.abs(x - from.getX()), Math.abs(y - from.getY())) == 1);
                    if (legal) actions.add(new Action("event-" + player.getId() + "-" + x + "-" + y, "kickoffMove",
                        player.getName() + " to " + x + ", " + y, role, new ClientCommandSetupPlayer(player.getId(), oriented(to, role))));
                }
            }
        }
        return actions;
    }

    private List<Player<?>> eligible(Game game, DialogPlayerChoiceParameter dialog) {
        List<Player<?>> players = new ArrayList<>();
        Team team = game.getTeamHome().getId().equals(dialog.getTeamId()) ? game.getTeamHome() : game.getTeamAway();
        for (String id : dialog.getPlayerIds()) {
            Player<?> player = game.getPlayerById(id);
            if (player != null && team.hasPlayer(player)) players.add(player);
        }
        return players;
    }

    private List<Player<?>> selected(List<Player<?>> eligible) {
        List<Player<?>> result = new ArrayList<>();
        for (Player<?> player : eligible) if (selectedPlayers.contains(player.getId())) result.add(player);
        return result;
    }

    private void solidDefence(List<Action> actions, Game game, String role) {
        Team team = game.getActingTeam();
        List<Player<?>> reserves = new ArrayList<>();
        for (Player<?> player : team.getPlayers()) {
            FieldCoordinate coordinate = game.getFieldModel().getPlayerCoordinate(player);
            if (game.getFieldModel().getPlayerState(player).getBase() == PlayerState.RESERVE && !FieldCoordinateBounds.FIELD.isInBounds(coordinate)) {
                reserves.add(player);
            }
        }
        // One player at a time bounds the snapshot and permits correcting a placed formation.
        Player<?> placing = reserves.isEmpty() ? null : reserves.get(0);
        if (placing == null) for (Player<?> player : team.getPlayers()) {
            if (!game.getFieldModel().getPlayerState(player).isActive()
                || !FieldCoordinateBounds.FIELD.isInBounds(game.getFieldModel().getPlayerCoordinate(player))) continue;
            if (selectedPlayers.contains(player.getId()) && placing == null) placing = player;
            actions.add(new Action("event-pick:" + player.getId(), "kickoffChoice", "Reposition " + player.getName(), role, null));
        }
        if (placing != null) {
            Player<?> player = placing;
            for (int x = "home".equals(role) ? 0 : 13; x < ("home".equals(role) ? 13 : 26); x++) for (int y = 0; y < 15; y++) {
                FieldCoordinate to = new FieldCoordinate(x, y);
                if (game.getFieldModel().getPlayer(to) == null) actions.add(new Action("solid-place:" + player.getId() + ":" + x + ":" + y,
                    "kickoffMove", "Place " + player.getName() + " at " + x + ", " + y, role,
                    new ClientCommandSetupPlayer(player.getId(), oriented(to, role))));
            }
        }
        actions.add(new Action("confirm-solid-defence", "kickoffChoice", "Confirm Solid Defence", role,
            new ClientCommandEndTurn(TurnMode.SOLID_DEFENCE, coordinates(game, team, role))));
    }

    private Map<String, FieldCoordinate> coordinates(Game game, Team team, String role) {
        Map<String, FieldCoordinate> coordinates = new HashMap<>();
        for (Player<?> player : team.getPlayers()) {
            FieldCoordinate coordinate = game.getFieldModel().getPlayerCoordinate(player);
            if (coordinate != null) coordinates.put(player.getId(), oriented(coordinate, role));
        }
        return coordinates;
    }

    private FieldCoordinate oriented(FieldCoordinate coordinate, String role) { return "home".equals(role) ? coordinate : coordinate.transform(); }
}
