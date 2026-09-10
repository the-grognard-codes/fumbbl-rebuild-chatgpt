package com.fumbbl.ffb.test;

import com.fumbbl.ffb.FieldCoordinate;
import com.fumbbl.ffb.PlayerState;
import com.fumbbl.ffb.Weather;
import com.fumbbl.ffb.dialog.DialogArgueTheCallParameter;
import com.fumbbl.ffb.dialog.DialogInterceptionParameter;
import com.fumbbl.ffb.net.commands.ClientCommandArgueTheCall;
import com.fumbbl.ffb.model.Game;
import com.fumbbl.ffb.model.RosterPlayer;
import com.fumbbl.ffb.model.skill.SkillWithValue;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.match.CorePromptActions;
import com.fumbbl.ffb.server.match.CoreTurnActions;
import com.fumbbl.ffb.server.match.CoreTurnActions.Action;
import com.fumbbl.ffb.server.net.ReceivedCommand;
import com.fumbbl.ffb.util.UtilPlayer;

import java.util.List;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallAndFoulActionsTest {
    private static final BrowserActionEvidence evidence = new BrowserActionEvidence();
    @AfterAll static void writeBrowserEvidence() throws Exception { evidence.write(); }
    @Test void interceptionIsOwnedByDefendingTeamAndMayBeDeclined() throws Exception {
        for (boolean intercept : new boolean[] {true, false}) {
            GameState state = fixture(true, false);
            state.getGame().getFieldModel().setPlayerCoordinate(state.getGame().getPlayerById("mate"), new FieldCoordinate(11, 7));
            state.getGame().getFieldModel().setPlayerCoordinate(state.getGame().getPlayerById("opponent"), new FieldCoordinate(9, 7));
            TestRolls.on(state).general(6, 6, 6);
            perform(state, "declarePass"); performId(state, "pass-11-7");
            assertTrue(state.getGame().getDialogParameter() instanceof DialogInterceptionParameter);
            assertTrue(actions(state).stream().allMatch(a -> a.role.equals("away")));
            String before = state.toJsonValue().toString(); actions(state); actions(state);
            assertEquals(before, state.toJsonValue().toString());
            performId(state, intercept ? "intercept:opponent" : "intercept:none");
            assertTrue(UtilPlayer.hasBall(state.getGame(), state.getGame().getPlayerById(intercept ? "opponent" : "mate")));
            assertFalse(actions(state).isEmpty());
        }
    }
    @Test void doublesFoulOffersNativeArgueCallAndDeclineResolvesBan() throws Exception {
        GameState state = fixture(true, false);
        Game game = state.getGame();
        game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
        game.getFieldModel().setPlayerState(game.getPlayerById("opponent"), new PlayerState(PlayerState.PRONE));
        TestRolls.on(state).armor(2, 2);
        perform(state, "declareFoul"); perform(state, "foul");
        assertTrue(game.getDialogParameter() instanceof DialogArgueTheCallParameter);
        Action yes = actions(state).stream().filter(a -> a.id.equals("argue:actor")).findFirst().get();
        assertEquals("actor", ((ClientCommandArgueTheCall) yes.command).getPlayerIds()[0]);
        performId(state, "argue:no");
        assertFalse(game.isHomePlaying());
        assertFalse(actions(state).isEmpty());
    }
    @Test void passChoiceAndAutomaticCatchRerollCompleteTheNativePass() throws Exception {
        GameState state = fixture(true, false, "Pass", "Catch");
        TestRolls.on(state).general(1, 6, 1, 6);
        perform(state, "declarePass"); performId(state, "pass-9-7");
        assertTrue(actions(state).stream().anyMatch(a -> a.label.contains("Pass")));
        performId(state, "skill:true");
        assertTrue(UtilPlayer.hasBall(state.getGame(), state.getGame().getPlayerById("mate")));
    }
    @Test void secureLooseBallUsesNativeAutomaticPickupAndEndsActivation() throws Exception {
        GameState state = fixture(true, false);
        state.getGame().getFieldModel().setBallCoordinate(new FieldCoordinate(8, 7));
        state.getGame().getFieldModel().setBallMoving(true);
        perform(state, "secureBall"); performId(state, "move-8-7");
        assertTrue(UtilPlayer.hasBall(state.getGame(), state.getGame().getPlayerById("actor")));
        assertTrue(state.getGame().getTurnDataHome().isSecureTheBallUsed());
        assertTrue(actions(state).stream().noneMatch(a -> a.kind.equals("secureBall")));
    }
    @Test void sureHandsPickupFailureOffersSkillAndRecovers() throws Exception {
        GameState state = fixture(true, false, "Sure Hands", "");
        state.getGame().getFieldModel().setBallCoordinate(new FieldCoordinate(8, 7));
        state.getGame().getFieldModel().setBallMoving(true);
        TestRolls.on(state).general(1, 6);
        perform(state, "select"); performId(state, "move-8-7");
        if (actions(state).stream().anyMatch(a -> a.id.equals("skill:true"))) performId(state, "skill:true");
        else if (actions(state).stream().anyMatch(a -> a.id.equals("reroll:skill"))) performId(state, "reroll:skill");
        assertTrue(UtilPlayer.hasBall(state.getGame(), state.getGame().getPlayerById("actor")));
    }
    @Test void boneHeadFailureOffersTeamRerollThenAllowsRecovery() throws Exception {
        GameState state = fixture(true, false, "Bone Head", "");
        state.getGame().getTurnDataHome().setReRolls(1);
        TestRolls.on(state).general(1, 6);
        perform(state, "select");
        performId(state, "move-8-7");
        assertTrue(actions(state).stream().anyMatch(a -> a.id.equals("reroll:team")));
        performId(state, "reroll:team");
        assertEquals(0, state.getGame().getTurnDataHome().getReRolls());
        assertTrue(actions(state).stream().anyMatch(a -> a.kind.equals("move")));
    }
    @Test void lonerFailureConsumesTeamRerollWithoutRepeatingFailedRoll() throws Exception {
        GameState state = fixture(true, false, "Bone Head", "");
        Game game = state.getGame();
        game.getPlayerById("actor").addTemporarySkills("test", Collections.singleton(new SkillWithValue(game.getRules().getSkillFactory().forName("Loner"), "3")));
        game.getTurnDataHome().setReRolls(1);
        TestRolls.on(state).general(1, 2);
        perform(state, "select"); performId(state, "move-8-7"); performId(state, "reroll:team");
        assertEquals(0, game.getTurnDataHome().getReRolls());
        assertTrue(game.getFieldModel().getPlayerState(game.getPlayerById("actor")).isConfused());
        assertFalse(actions(state).isEmpty());
    }
    @Test void blockDodgeAndTackleUseNativeOptionalSkillDecisions() throws Exception {
        for (boolean tackle : new boolean[] {true, false}) {
            GameState state = fixture(true, false, tackle ? "Tackle" : "Block", "");
            Game game = state.getGame();
            ((RosterPlayer) game.getPlayerById("opponent")).addSkill(game.getRules().getSkillFactory().forName("Dodge"));
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("mate"), new FieldCoordinate(20, 2));
            TestRolls.on(state).block("stumble").armor(2, 3);
            perform(state, "selectBlock"); perform(state, "block"); perform(state, "blockDie");
            settleSkillsAndPush(state);
            assertEquals(!tackle, game.getFieldModel().getPlayerState(game.getPlayerById("opponent")).isStanding());
        }
    }
    @Test void apothecaryKoAndCasualtyChoicesResolveForInjuredParticipant() throws Exception {
        for (boolean casualty : new boolean[] {false, true}) {
            GameState state = fixture(true, false);
            Game game = state.getGame(); game.getTurnDataAway().setApothecaries(1);
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("mate"), new FieldCoordinate(20, 2));
            TestRolls.on(state).block("pow").armor(6, 6).injury(casualty ? 6 : 4, casualty ? 6 : 4).casualty(16).general(6).casualty(16).general(6);
            perform(state, "selectBlock"); perform(state, "block"); perform(state, "blockDie");
            settleSkillsAndPush(state);
            Action use = actions(state).stream().filter(a -> a.kind.equals("apothecary") && a.label.startsWith("Use ")).findFirst().get();
            assertEquals("away", use.role); evidence.perform(state, use);
            if (casualty) {
                assertTrue(actions(state).stream().anyMatch(a -> a.id.equals("injury:new")));
                performId(state, "injury:new");
            }
            assertEquals(0, game.getTurnDataAway().getApothecaries());
            assertFalse(actions(state).isEmpty());
        }
    }
    private void settleSkillsAndPush(GameState state) {
        for (int n = 0; n < 10; n++) {
            Action action = actions(state).stream().filter(a -> a.id.equals("skill:true") || a.kind.equals("push") || a.id.equals("follow-up:no")).findFirst().orElse(null);
            if (action == null) return;
            evidence.perform(state, action);
        }
        throw new AssertionError("Native block decisions did not settle");
    }
    @Test void nativeMightyBlowThickSkullAndStuntyInjuriesRemainActionable() throws Exception {
        for (String trait : new String[] {"Mighty Blow", "Thick Skull", "Stunty"}) {
            GameState state = fixture(true, false, trait.equals("Mighty Blow") ? trait : "", "");
            Game game = state.getGame();
            if (!trait.equals("Mighty Blow")) ((RosterPlayer) game.getPlayerById("opponent")).addSkill(game.getRules().getSkillFactory().forName(trait));
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("mate"), new FieldCoordinate(20, 2));
            TestRolls.on(state).block("pow").armor(trait.equals("Mighty Blow") ? 4 : 6, trait.equals("Mighty Blow") ? 4 : 6)
                .injury(trait.equals("Stunty") ? 3 : 4, 4);
            perform(state, "selectBlock"); perform(state, "block"); perform(state, "blockDie"); settleSkillsAndPush(state);
            int result = game.getFieldModel().getPlayerState(game.getPlayerById("opponent")).getBase();
            assertEquals(trait.equals("Thick Skull") ? PlayerState.STUNNED : PlayerState.KNOCKED_OUT, result, trait);
            assertFalse(actions(state).isEmpty());
        }
    }
    @Test void forgoActivationUsesNativeActionAndLeavesNextPlayerSelectable() throws Exception {
        GameState state = fixture(true, false);
        perform(state, "forgo");
        assertFalse(state.getGame().getFieldModel().getPlayerState(state.getGame().getPlayerById("actor")).isActive());
        assertFalse(actions(state).isEmpty());
    }
    @Test void nativeProBlockMappingExposesEachDieAndRerollsExactlyOne() throws Exception {
        GameState state = fixture(true, false, "Pro", "");
        Game game = state.getGame();
        game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
        game.getFieldModel().setPlayerCoordinate(game.getPlayerById("mate"), new FieldCoordinate(15, 12));
        TestRolls.on(state).block("skull").general(6).block("pushback");
        perform(state, "selectBlock"); perform(state, "block");
        performId(state, "block-reroll:pro:0");
        assertTrue(actions(state).stream().anyMatch(a -> a.label.contains("PUSH")));
        assertFalse(actions(state).stream().anyMatch(a -> a.id.startsWith("block-reroll:pro")));
    }
    @Test void proFailureDoesNotOfferAnotherRerollOfTheOriginalRoll() throws Exception {
        GameState state = fixture(true, false, "Pro", "");
        state.getGame().getTurnDataHome().setReRolls(1);
        TestRolls.on(state).general(1, 1, 3, 6, 6);
        perform(state, "declarePass"); performId(state, "pass-9-7"); performId(state, "reroll:pro");
        assertFalse(state.getGame().isHomePlaying());
        assertEquals(1, state.getGame().getTurnDataHome().getReRolls());
    }
    @Test void accuratePassAndCatchUseNativeEngineForBothOrientations() throws Exception {
        for (boolean home : new boolean[] {true, false}) {
            GameState state = fixture(home, false);
            TestRolls.on(state).general(6, 6);
            perform(state, "declarePass");
            String before = state.toJsonValue().toString(); actions(state); actions(state);
            assertEquals(before, state.toJsonValue().toString());
            assertFalse(actions(state).stream().anyMatch(a -> a.id.equals("pass-25-14")));
            performId(state, "pass-9-7");
            assertTrue(UtilPlayer.hasBall(state.getGame(), state.getGame().getPlayerById("mate")));
            assertTrue((home ? state.getGame().getTurnDataHome() : state.getGame().getTurnDataAway()).isPassUsed());
        }
    }
    @Test void handOffCatchesAndConsumesSeparateTurnResource() throws Exception {
        for (boolean home : new boolean[] {true, false}) {
            GameState state = fixture(home, false);
            state.getGame().getFieldModel().setPlayerCoordinate(state.getGame().getPlayerById("mate"), new FieldCoordinate(8, 7));
            state.getGame().getFieldModel().setPlayerCoordinate(state.getGame().getPlayerById("opponent"), new FieldCoordinate(6, 7));
            TestRolls.on(state).general(6);
            perform(state, "declareHandOff");
            assertFalse(actions(state).stream().anyMatch(a -> a.id.equals("hand-off-opponent")));
            perform(state, "handOff");
            assertTrue(UtilPlayer.hasBall(state.getGame(), state.getGame().getPlayerById("mate")));
            assertTrue((home ? state.getGame().getTurnDataHome() : state.getGame().getTurnDataAway()).isHandOverUsed());
            assertFalse((home ? state.getGame().getTurnDataHome() : state.getGame().getTurnDataAway()).isPassUsed());
        }
    }
    @Test void failedPassOffersNativeRerollAndDeclineResolvesTurnover() throws Exception {
        GameState state = fixture(true, false);
        state.getGame().getTurnDataHome().setReRolls(1);
        TestRolls.on(state).general(1, 3, 6, 6, 6, 6);
        perform(state, "declarePass"); performId(state, "pass-9-7");
        assertTrue(actions(state).stream().anyMatch(a -> a.id.equals("reroll:team")));
        performId(state, "reroll:none");
        assertFalse(state.getGame().isHomePlaying());
    }
    @Test void foulUsesOpponentInCanonicalAwayOrientation() throws Exception {
        for (boolean home : new boolean[] {true, false}) {
            GameState state = fixture(home, false);
            Game game = state.getGame();
            game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
            game.getFieldModel().setPlayerState(game.getPlayerById("opponent"), new PlayerState(PlayerState.PRONE));
            TestRolls.on(state).armor(2, 3);
            perform(state, "declareFoul"); perform(state, "foul");
            assertTrue((home ? game.getTurnDataHome() : game.getTurnDataAway()).isFoulUsed());
            assertTrue(actions(state).stream().noneMatch(a -> a.kind.equals("declareFoul")));
        }
    }
    @Test void throwTeamMateOffersOnlyNativeRangeAndResolvesLanding() throws Exception {
        GameState state = fixture(true, true);
        state.getGame().getFieldModel().setPlayerCoordinate(state.getGame().getPlayerById("mate"), new FieldCoordinate(8, 7));
        TestRolls.on(state).general(6, 6, 3, 3, 3, 6, 6, 6, 6);
        perform(state, "declareThrowTeamMate"); perform(state, "liftTeamMate");
        assertEquals(PlayerState.PICKED_UP, state.getGame().getFieldModel().getPlayerState(state.getGame().getPlayerById("mate")).getBase());
        assertTrue(actions(state).stream().anyMatch(a -> a.kind.equals("throwTeamMate")));
        assertFalse(actions(state).stream().anyMatch(a -> a.id.equals("throw-mate-25-14")));
        performId(state, "throw-mate-11-7");
        assertTrue(state.getGame().getTurnDataHome().isTtmUsed());
        assertFalse(state.getGame().getFieldModel().getPlayerState(state.getGame().getPlayerById("mate")).getBase() == PlayerState.PICKED_UP);
        assertFalse(actions(state).isEmpty());
    }
    @Test void jumpOverPronePlayerUsesNativeJumpSquares() throws Exception {
        GameState state = fixture(true, false);
        Game game = state.getGame();
        game.getFieldModel().setPlayerCoordinate(game.getPlayerById("opponent"), new FieldCoordinate(8, 7));
        game.getFieldModel().setPlayerState(game.getPlayerById("opponent"), new PlayerState(PlayerState.PRONE));
        TestRolls.on(state).general(6);
        perform(state, "select"); perform(state, "jumpMode");
        Action jump = actions(state).stream().filter(a -> a.kind.equals("jump")).findFirst().get();
        evidence.perform(state, jump);
        assertEquals(2, new FieldCoordinate(7, 7).distanceInSteps(game.getFieldModel().getPlayerCoordinate(game.getPlayerById("actor"))));
    }

    private GameState fixture(boolean home, boolean ogre) throws Exception {
        return fixture(home, ogre, "", "");
    }
    private GameState fixture(boolean home, boolean ogre, String actorSkill, String mateSkill) throws Exception {
        GameState state = new GameState(new TestServer().getServer()) { @Override public boolean usesLegacyPersistence() { return false; } };
        new GameStateBuilder(state).withRule("BB2025").withWeather(Weather.NICE).withBallAt(7, 7)
            .withTeam(home, team -> {
                team.player("actor", p -> { p.at(7, 7).stats(6, ogre ? 5 : 3, 3, 3, 9); if (ogre) p.skill("Throw Team-mate"); if (!actorSkill.isEmpty()) p.skill(actorSkill); });
                team.player("mate", p -> { p.at(9, 7).stats(6, 3, 3, 4, 8); if (ogre) p.skill("Right Stuff"); if (!mateSkill.isEmpty()) p.skill(mateSkill); });
            })
            .withTeam(!home, team -> team.player("opponent", p -> p.at(20, 12).stats(6, 3, 3, 4, 9))).build();
        state.getGame().setHomePlaying(home);
        state.getGame().getFieldModel().setBallMoving(false);
        StepEngine.start(state);
        return state;
    }
    private List<Action> actions(GameState state) {
        List<Action> choices = new CorePromptActions(state).actions();
        return choices.isEmpty() ? new CoreTurnActions(state).actions() : choices;
    }
    private void perform(GameState state, String kind) {
        Action action = actions(state).stream().filter(a -> a.kind.equals(kind)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing " + kind + " at " + state.getCurrentStep().getId() + " " + state.getGame().getDialogParameter()));
        evidence.perform(state, action);
    }
    private void performId(GameState state, String id) {
        Action action = actions(state).stream().filter(a -> a.id.equals(id)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing " + id + " at " + state.getCurrentStep().getId() + " " + state.getGame().getDialogParameter()));
        evidence.perform(state, action);
    }
}
