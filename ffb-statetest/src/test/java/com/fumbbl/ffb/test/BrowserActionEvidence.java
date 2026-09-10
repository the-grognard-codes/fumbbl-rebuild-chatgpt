package com.fumbbl.ffb.test;

import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.match.SetupSession;
import com.fumbbl.ffb.server.match.CoreTurnActions.Action;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test-only injection and wire evidence. Never packaged or exposed by a product route. */
final class BrowserActionEvidence {
    private final Map<GameState, SetupSession> sessions = new IdentityHashMap<>();
    private final JsonArray frames = new JsonArray();

    void perform(GameState state, Action action) {
        try {
            SetupSession session = sessions.get(state);
            if (session == null) {
                session = new SetupSessionTest().session(11);
                Field field = SetupSession.class.getDeclaredField("state");
                field.setAccessible(true); field.set(session, state);
                sessions.put(state, session);
            }
            final SetupSession current = session;
            JsonObject before = session.reply("load", "ACCEPTED", false, action.role);
            JsonObject view = before.get("state").asObject();
            JsonObject request = new JsonObject().add("version", 1).add("type", "setup").add("operation", "action")
                .add("requestId", UUID.randomUUID().toString()).add("matchId", view.get("matchId"))
                .add("expectedRevision", view.get("revision")).add("actionId", view.getInt("revision", 0) + ":" + action.id);
            String serialized = state.toJsonValue().toString();
            String other = "home".equals(action.role) ? "away" : "home";
            assertEquals("WRONG_ACTOR", assertThrows(MatchService.Failure.class, () -> current.apply(other, request)).code);
            JsonObject stale = JsonObject.readFrom(request.toString()).set("expectedRevision", -1);
            assertEquals("STALE_REVISION", assertThrows(MatchService.Failure.class, () -> current.apply(action.role, stale)).code);
            assertEquals(serialized, state.toJsonValue().toString());
            // A fresh load is exactly the snapshot used after same-JVM reconnect.
            assertEquals(before, session.reply("load", "ACCEPTED", false, action.role));
            JsonObject after = session.apply(action.role, request);
            assertEquals("ACCEPTED", after.getString("code", null));
            serialized = state.toJsonValue().toString();
            int dice = queuedDice(state);
            assertTrue(session.apply(action.role, request).getBoolean("duplicate", false));
            assertEquals(serialized, state.toJsonValue().toString());
            assertEquals(dice, queuedDice(state));
            frames.add(new JsonObject().add("capability", action.kind).add("before", before).add("request", request).add("after", after));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private int queuedDice(GameState state) { return state.getDiceRoller().getTestRolls().values().stream().mapToInt(java.util.List::size).sum(); }
    void write() throws Exception {
        Files.createDirectories(Paths.get("target"));
        Files.write(Paths.get("target", "m3c-actions.json"), frames.toString().getBytes(StandardCharsets.UTF_8));
    }
}
