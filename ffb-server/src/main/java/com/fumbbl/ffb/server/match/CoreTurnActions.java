package com.fumbbl.ffb.server.match;

import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.FieldCoordinateBounds;
import com.fumbbl.ffb.MoveSquare;
import com.fumbbl.ffb.PlayerAction;
import com.fumbbl.ffb.PlayerState;
import com.fumbbl.ffb.TurnMode;
import com.fumbbl.ffb.dialog.DialogPlayerChoiceParameter;
import com.fumbbl.ffb.PlayerChoiceMode;
import com.fumbbl.ffb.model.ActingPlayer;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.model.TargetSelectionState;
import com.fumbbl.ffb.net.commands.ClientCommand;
import com.fumbbl.ffb.net.commands.ClientCommandActingPlayer;
import com.fumbbl.ffb.net.commands.ClientCommandBlitzMove;
import com.fumbbl.ffb.net.commands.ClientCommandBlock;
import com.fumbbl.ffb.net.commands.ClientCommandEndTurn;
import com.fumbbl.ffb.net.commands.ClientCommandMove;
import com.fumbbl.ffb.net.commands.ClientCommandTargetSelected;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.step.StepId;
import com.fumbbl.ffb.util.UtilPlayer;
import com.fumbbl.ffb.util.pathfinding.PathFinderWithMultiJump;

import java.util.ArrayList;
import java.util.List;

/** Read-only projection of native core turn targets. No dice or state mutation. */
public final class CoreTurnActions {
    private final GameState state;
    public CoreTurnActions(GameState state) { this.state = state; }
    public List<Action> actions() {
        List<Action> result = new ArrayList<>();
        Game game = state.getGame();
        ActingPlayer acting = game.getActingPlayer();
        String role = game.isHomePlaying() ? "home" : "away";
        if (game.getTurnMode() == TurnMode.SELECT_BLITZ_TARGET && acting.getPlayer() != null) {
            FieldCoordinate from = game.getFieldModel().getPlayerCoordinate(acting.getPlayer());
            for (Player<?> target : game.getOtherTeam(game.getActingTeam()).getPlayers()) {
                FieldCoordinate at = game.getFieldModel().getPlayerCoordinate(target);
                if (!FieldCoordinateBounds.FIELD.isInBounds(at) || !game.getFieldModel().getPlayerState(target).canBeBlocked()) continue;
                FieldCoordinate[] path = new PathFinderWithMultiJump().getPathToBlitzTarget(game, target);
                if (!at.isAdjacent(from) && (path == null || path.length == 0)) continue;
                result.add(new Action("target-" + target.getId(), "blitzTarget", "Blitz " + target.getName(), role, new ClientCommandTargetSelected(target.getId())));
            }
            return result;
        }
        boolean consumedKickoffChoice = game.getDialogParameter() instanceof DialogPlayerChoiceParameter
            && (((DialogPlayerChoiceParameter) game.getDialogParameter()).getPlayerChoiceMode() == PlayerChoiceMode.CHARGE
                || ((DialogPlayerChoiceParameter) game.getDialogParameter()).getPlayerChoiceMode() == PlayerChoiceMode.SOLID_DEFENCE);
        if ((state.getCurrentStep().getId() != StepId.INIT_SELECTING && state.getCurrentStep().getId() != StepId.INIT_MOVING && state.getCurrentStep().getId() != StepId.INIT_BLOCKING) || (game.getDialogParameter() != null && !consumedKickoffChoice)
            || (game.getTurnMode() != TurnMode.REGULAR && game.getTurnMode() != TurnMode.BLITZ)) return result;
        result.add(new Action("end-turn", "endTurn", "End turn", role, new ClientCommandEndTurn(game.getTurnMode(), null)));
        if (acting.getPlayer() == null) {
            for (Player<?> player : game.getActingTeam().getPlayers()) {
                PlayerState status = game.getFieldModel().getPlayerState(player);
                FieldCoordinate at = game.getFieldModel().getPlayerCoordinate(player);
                if (!FieldCoordinateBounds.FIELD.isInBounds(at) || !status.isActive() || !status.isAbleToMove()) continue;
                boolean prone = status.getBase() == PlayerState.PRONE;
                result.add(new Action("select-" + player.getId(), prone ? "stand" : "select", (prone ? "Stand up " : "Move ") + player.getName(), role,
                    new ClientCommandActingPlayer(player.getId(), prone ? PlayerAction.STAND_UP : PlayerAction.MOVE, false)));
                if (!game.getTurnData().isBlitzUsed())
                    result.add(new Action("blitz-" + player.getId(), "blitz", "Start blitz with " + player.getName(), role,
                        new ClientCommandActingPlayer(player.getId(), prone ? PlayerAction.STAND_UP_BLITZ : PlayerAction.BLITZ_MOVE, false)));
                if (!prone && game.getTurnMode() == TurnMode.REGULAR && UtilPlayer.findAdjacentBlockablePlayers(game,
                    game.getOtherTeam(game.getActingTeam()), at).length > 0)
                    result.add(new Action("select-block-" + player.getId(), "selectBlock", "Block with " + player.getName(), role,
                        new ClientCommandActingPlayer(player.getId(), PlayerAction.BLOCK, false)));
            }
            return result;
        }
        String id = acting.getPlayerId();
        FieldCoordinate from = game.getFieldModel().getPlayerCoordinate(acting.getPlayer());
        result.add(new Action("end-action", "endAction", "End player action", role, new ClientCommandActingPlayer(null, null, false)));
        PlayerAction action = acting.getPlayerAction();
        if (action != null && (action.isMoving() || action.isStandingUp())) {
            for (MoveSquare square : game.getFieldModel().getMoveSquares()) {
                FieldCoordinate to = square.getCoordinate();
                if (!FieldCoordinateBounds.FIELD.isInBounds(to) || !to.isAdjacent(from) || game.getFieldModel().getPlayer(to) != null) continue;
                String label = "Move to " + to.getX() + ", " + to.getY();
                if (square.getMinimumRollDodge() > 0) label += " (dodge " + square.getMinimumRollDodge() + "+)";
                if (square.getMinimumRollGoForIt() > 0) label += " (rush " + square.getMinimumRollGoForIt() + "+)";
                ClientCommand command = action.isBlitzing() ? new ClientCommandBlitzMove(id, oriented(from, role), new FieldCoordinate[] { oriented(to, role) })
                    : new ClientCommandMove(id, oriented(from, role), new FieldCoordinate[] { oriented(to, role) }, null);
                result.add(new Action("move-" + to.getX() + "-" + to.getY(), "move", label, role, command));
            }
        }
        if (action != null && (action == PlayerAction.BLOCK || action.isBlitzing()) && !acting.hasBlocked()) {
            TargetSelectionState selected = game.getFieldModel().getTargetSelectionState();
            for (Player<?> target : UtilPlayer.findAdjacentBlockablePlayers(game, game.getOtherTeam(game.getActingTeam()), from)) {
                FieldCoordinate at = game.getFieldModel().getPlayerCoordinate(target);
                if (game.getFieldModel().getDiceDecoration(at) == null || selected != null && !target.getId().equals(selected.getSelectedPlayerId())) continue;
                result.add(new Action("block-" + target.getId(), "block", "Block " + target.getName(), role,
                    new ClientCommandBlock(id, target.getId(), false, false, false, false, false)));
            }
        }
        return result;
    }
    private FieldCoordinate oriented(FieldCoordinate coordinate, String role) { return "home".equals(role) ? coordinate : coordinate.transform(); }
    public static final class Action {
        public final String id, kind, label, role;
        public final ClientCommand command;
        public Action(String id, String kind, String label, String role, ClientCommand command) {
            this.id = id; this.kind = kind; this.label = label; this.role = role; this.command = command;
        }
    }
}
