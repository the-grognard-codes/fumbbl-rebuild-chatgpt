package com.fumbbl.ffb.server.match;

import com.fumbbl.ffb.ApothecaryType;
import com.fumbbl.ffb.Direction;
import com.fumbbl.ffb.FactoryType;
import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.IDialogParameter;
import com.fumbbl.ffb.Pushback;
import com.fumbbl.ffb.PushbackSquare;
import com.fumbbl.ffb.ReRollProperty;
import com.fumbbl.ffb.ReRollSources;
import com.fumbbl.ffb.ReRolledAction;
import com.fumbbl.ffb.ReRolledActions;
import com.fumbbl.ffb.dialog.DialogApothecaryChoiceParameter;
import com.fumbbl.ffb.dialog.DialogArgueTheCallParameter;
import com.fumbbl.ffb.dialog.DialogBlockRollPropertiesParameter;
import com.fumbbl.ffb.dialog.DialogConfirmEndActionParameter;
import com.fumbbl.ffb.dialog.DialogFollowupChoiceParameter;
import com.fumbbl.ffb.dialog.DialogInterceptionParameter;
import com.fumbbl.ffb.dialog.DialogReRollPropertiesParameter;
import com.fumbbl.ffb.dialog.DialogSkillUseParameter;
import com.fumbbl.ffb.dialog.DialogUseApothecaryParameter;
import com.fumbbl.ffb.factory.BlockResultFactory;
import com.fumbbl.ffb.model.ActingPlayer;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.Player;
import com.fumbbl.ffb.net.commands.ClientCommand;
import com.fumbbl.ffb.net.commands.ClientCommandApothecaryChoice;
import com.fumbbl.ffb.net.commands.ClientCommandBlockChoice;
import com.fumbbl.ffb.net.commands.ClientCommandConfirm;
import com.fumbbl.ffb.net.commands.ClientCommandArgueTheCall;
import com.fumbbl.ffb.net.commands.ClientCommandFollowupChoice;
import com.fumbbl.ffb.net.commands.ClientCommandInterceptorChoice;
import com.fumbbl.ffb.net.commands.ClientCommandPushback;
import com.fumbbl.ffb.net.commands.ClientCommandUseApothecary;
import com.fumbbl.ffb.net.commands.ClientCommandUseProReRollForBlock;
import com.fumbbl.ffb.net.commands.ClientCommandUseReRoll;
import com.fumbbl.ffb.net.commands.ClientCommandUseSkill;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.util.UtilPassing;

import java.util.ArrayList;
import java.util.List;

/** Read-only command catalog for native decision dialogs that have no browser-specific rule implementation. */
public final class CorePromptActions {
	private final GameState state;

	public CorePromptActions(GameState state) {
		this.state = state;
	}

	public List<CoreTurnActions.Action> actions() {
		List<CoreTurnActions.Action> result = new ArrayList<>();
		Game game = state.getGame();
		if (game == null) return result;
		IDialogParameter dialog = game.getDialogParameter();
		if (dialog instanceof DialogBlockRollPropertiesParameter) {
			blockRoll(result, game, (DialogBlockRollPropertiesParameter) dialog);
		} else if (dialog instanceof DialogReRollPropertiesParameter) {
			reroll(result, game, (DialogReRollPropertiesParameter) dialog);
		} else if (dialog instanceof DialogFollowupChoiceParameter) {
			followUp(result, game);
		} else if (dialog instanceof DialogSkillUseParameter) {
            DialogSkillUseParameter choice = (DialogSkillUseParameter) dialog;
            String role = roleForPlayer(game, game.getPlayerById(choice.getPlayerId()));
            if (role != null) {
                for (boolean use : new boolean[] {false, true}) add(result, "skill:" + use, "skill",
                    use ? "Use " + choice.getSkill().getName() : "Decline skills", role,
                    new ClientCommandUseSkill(choice.getSkill(), use, choice.getPlayerId(), null, false));
                if (choice.getModifyingSkill() != null) add(result, "skill:modify", "skill", "Use " + choice.getModifyingSkill().getName(), role,
                    new ClientCommandUseSkill(choice.getModifyingSkill(), true, choice.getPlayerId(), null, false));
                else if (choice.isShowNeverUse()) add(result, "skill:never", "skill", "Do not use for this action", role,
                    new ClientCommandUseSkill(choice.getSkill(), false, choice.getPlayerId(), null, true));
            }
        } else if (dialog instanceof DialogUseApothecaryParameter) {
            DialogUseApothecaryParameter choice = (DialogUseApothecaryParameter) dialog;
            String role = roleForPlayer(game, game.getPlayerById(choice.getPlayerId()));
            if (role != null) {
                add(result, "apothecary:no", "apothecary", "Decline apothecary", role,
                    new ClientCommandUseApothecary(choice.getPlayerId(), false, null, null));
                for (ApothecaryType type : choice.getApothecaryTypes()) add(result, "apothecary:" + type.name(), "apothecary",
                    "Use " + type.name(), role, new ClientCommandUseApothecary(choice.getPlayerId(), true, type, null));
            }
        } else if (dialog instanceof DialogApothecaryChoiceParameter) {
            DialogApothecaryChoiceParameter choice = (DialogApothecaryChoiceParameter) dialog;
            String role = roleForPlayer(game, game.getPlayerById(choice.getPlayerId()));
            if (role != null) {
                add(result, "injury:old", "apothecary", "Keep " + choice.getPlayerStateOld().getDescription() + " " + choice.getSeriousInjuryOld(), role,
                    new ClientCommandApothecaryChoice(choice.getPlayerId(), choice.getPlayerStateOld(), choice.getSeriousInjuryOld(), choice.getPlayerStateOld()));
                add(result, "injury:new", "apothecary", "Choose " + choice.getPlayerStateNew().getDescription() + " " + choice.getSeriousInjuryNew(), role,
                    new ClientCommandApothecaryChoice(choice.getPlayerId(), choice.getPlayerStateNew(), choice.getSeriousInjuryNew(), choice.getPlayerStateOld()));
            }
		} else if (dialog instanceof DialogConfirmEndActionParameter) {
            String role = roleForTeam(game, ((DialogConfirmEndActionParameter) dialog).getTeamId());
			if (role != null) add(result, "confirm-end", "endAction", "Confirm ending action", role, new ClientCommandConfirm());
		} else if (dialog instanceof DialogArgueTheCallParameter) {
			DialogArgueTheCallParameter choice = (DialogArgueTheCallParameter) dialog;
			String role = roleForTeam(game, choice.getTeamId());
			if (role != null) {
				add(result, "argue:no", "argueTheCall", "Do not argue the call", role, new ClientCommandArgueTheCall(new String[0]));
				for (String playerId : choice.getPlayerIds()) add(result, "argue:" + playerId, "argueTheCall", "Argue the call for " + game.getPlayerById(playerId).getName(), role,
					new ClientCommandArgueTheCall(playerId));
			}
		} else if (dialog instanceof DialogInterceptionParameter) {
			DialogInterceptionParameter choice = (DialogInterceptionParameter) dialog;
			Player<?> thrower = game.getPlayerById(choice.getThrowerId());
			if (thrower != null) add(result, "intercept:none", "interception", "Do not intercept", roleForTeam(game, game.getOtherTeam(thrower.getTeam()).getId()),
				new ClientCommandInterceptorChoice(null, null));
			if (thrower != null) for (Player<?> interceptor : UtilPassing.findInterceptors(game, thrower, game.getPassCoordinate())) {
				String role = roleForPlayer(game, interceptor);
				if (role != null) add(result, "intercept:" + interceptor.getId(), "interception", "Intercept with " + interceptor.getName(), role,
					new ClientCommandInterceptorChoice(interceptor.getId(), choice.getInterceptionSkill()));
			}
        } else if (dialog == null && game.getFieldModel().getPushbackSquares().length > 0) {
			pushback(result, game);
		}
		return result;
	}

	private void blockRoll(List<CoreTurnActions.Action> result, Game game, DialogBlockRollPropertiesParameter dialog) {
		String role = roleForTeam(game, dialog.getChoosingTeamId());
		if (role == null || dialog.getBlockRoll() == null) return;
		for (int index = 0; index < dialog.getBlockRoll().length; index++) {
			add(result, "block-die:" + index, "blockDie", "Choose " + ((BlockResultFactory) game.getFactory(FactoryType.Factory.BLOCK_RESULT)).forRoll(dialog.getBlockRoll()[index]).getName() + " (die " + (index + 1) + ")", role,
				new ClientCommandBlockChoice(index));
		}
		if (dialog.hasProperty(ReRollProperty.TRR)) {
			add(result, "block-reroll:team", "reroll", "Use team re-roll", role,
				new ClientCommandUseReRoll(ReRolledActions.BLOCK, ReRollSources.TEAM_RE_ROLL));
		}
		if (dialog.hasProperty(ReRollProperty.PRO) || ReRollSources.PRO.getName(game).equals(
            dialog.getRrActionToSource().get(ReRolledActions.SINGLE_DIE_PER_ACTIVATION.getName(game.getRules().getSkillFactory())))) {
			for (int index = 0; index < dialog.getBlockRoll().length; index++) add(result, "block-reroll:pro:" + index, "reroll", "Use Pro on die " + (index + 1), role,
				new ClientCommandUseProReRollForBlock(index));
		}
	}

	private void reroll(List<CoreTurnActions.Action> result, Game game, DialogReRollPropertiesParameter dialog) {
		Player<?> player = game.getPlayerById(dialog.getPlayerId());
		String role = roleForPlayer(game, player);
		ReRolledAction action = dialog.getReRolledAction();
		if (role == null || action == null) return;
		String actionName = action.getName(game.getRules().getSkillFactory());
		add(result, "reroll:none", "reroll", "Do not re-roll " + actionName, role,
			new ClientCommandUseReRoll(action, null));
		if (dialog.hasProperty(ReRollProperty.TRR)) {
			add(result, "reroll:team", "reroll", "Use team re-roll for " + actionName, role,
				new ClientCommandUseReRoll(action, ReRollSources.TEAM_RE_ROLL));
		}
		if (dialog.hasProperty(ReRollProperty.PRO)) {
			add(result, "reroll:pro", "reroll", "Use Pro re-roll for " + actionName, role,
				new ClientCommandUseReRoll(action, ReRollSources.PRO));
		}
		if (dialog.getReRollSkill() != null) {
			add(result, "reroll:skill", "reroll", "Use " + dialog.getReRollSkill().getName(), role,
				new ClientCommandUseSkill(dialog.getReRollSkill(), true, player.getId(), action, false));
		}
		if (dialog.getModifyingSkill() != null) {
			add(result, "reroll:modify", "reroll", "Use " + dialog.getModifyingSkill().getName(), role,
				new ClientCommandUseSkill(dialog.getModifyingSkill(), true, player.getId(), action, false));
		}
	}

	private void followUp(List<CoreTurnActions.Action> result, Game game) {
		ActingPlayer acting = game.getActingPlayer();
		String role = acting == null ? null : roleForPlayer(game, acting.getPlayer());
		if (role == null) return;
		add(result, "follow-up:yes", "followUp", "Follow up", role, new ClientCommandFollowupChoice(true));
		add(result, "follow-up:no", "followUp", "Do not follow up", role, new ClientCommandFollowupChoice(false));
	}

	private void pushback(List<CoreTurnActions.Action> result, Game game) {
		for (PushbackSquare square : game.getFieldModel().getPushbackSquares()) {
			if (square.isLocked()) continue;
			String role = square.isHomeChoice() ? "home" : "away";
			FieldCoordinate from = source(square);
			Player<?> pushed = from == null ? null : game.getFieldModel().getPlayer(from);
			if (pushed == null) continue;
			Pushback pushback = new Pushback(pushed.getId(), square.getCoordinate());
			FieldCoordinate canonical = square.getCoordinate();
			ClientCommand command = new ClientCommandPushback("away".equals(role) ? pushback.transform() : pushback);
			add(result, "push:" + pushed.getId() + ":" + canonical.getX() + ":" + canonical.getY(), "push",
				"Push to " + canonical.getX() + ", " + canonical.getY(), role, command);
		}
	}

	private FieldCoordinate source(PushbackSquare square) {
		FieldCoordinate to = square.getCoordinate();
		Direction direction = square.getDirection();
		if (to == null || direction == null) return null;
		switch (direction) {
			case NORTH: return to.add(0, 1);
			case NORTHEAST: return to.add(-1, 1);
			case EAST: return to.add(-1, 0);
			case SOUTHEAST: return to.add(-1, -1);
			case SOUTH: return to.add(0, -1);
			case SOUTHWEST: return to.add(1, -1);
			case WEST: return to.add(1, 0);
			case NORTHWEST: return to.add(1, 1);
			default: return null;
		}
	}

	private String roleForTeam(Game game, String teamId) {
		if (game.getTeamHome() != null && game.getTeamHome().getId().equals(teamId)) return "home";
		if (game.getTeamAway() != null && game.getTeamAway().getId().equals(teamId)) return "away";
		return null;
	}

	private String roleForPlayer(Game game, Player<?> player) {
		if (player == null) return null;
		if (game.getTeamHome().hasPlayer(player)) return "home";
		if (game.getTeamAway().hasPlayer(player)) return "away";
		return null;
	}

	private void add(List<CoreTurnActions.Action> actions, String id, String kind, String label, String role,
		ClientCommand command) {
		actions.add(new CoreTurnActions.Action(id, kind, label, role, command));
	}
}
