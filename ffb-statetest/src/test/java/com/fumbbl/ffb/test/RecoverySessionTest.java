package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.match.MatchDocument;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.match.SetupSession;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoverySessionTest {
	@Test void prematchAndPlacementRestoreBothViewsAndCommittedRetryWithoutConsumingDice() throws Exception {
		SetupSession fixture = new SetupSessionTest().session(11);
		Field field = SetupSession.class.getDeclaredField("document"); field.setAccessible(true);
		MatchDocument document = (MatchDocument) field.get(fixture);
		TestServer server = new TestServer();
		SetupSession original = new SetupSession(server.getServer(), document, -3, true);
		for (int decision = 0; decision < 2; decision++) {
			SetupSession restored = restore(server, document, original);
			JsonObject view = view(original, "home");
			JsonObject prompt = view.get("prompt").asObject();
			JsonObject request = new JsonObject().add("operation", "choice").add("requestId", "choice" + decision)
				.add("expectedRevision", view.get("revision")).add("promptId", prompt.get("id"))
				.add("optionId", decision == 0 ? "heads" : "receive");
			String role = view.get("actor").asString();
			assertEquals(original.apply(role, request), restored.apply(role, request));
			// Separate live executions have different elapsed times in model and log.
			// Restoration itself below still compares every field exactly.
			JsonObject continued = JsonObject.readFrom(original.recoveryArtifact()).get("payload").asObject();
			JsonObject continuedRestored = JsonObject.readFrom(restored.recoveryArtifact()).get("payload").asObject();
			removeElapsed(continued); removeElapsed(continuedRestored);
			compare(continued, continuedRestored, "continued");
			SetupSession after = restore(server, document, original);
			String beforeRetry = after.recoveryArtifact();
			assertTrue(after.apply(role, request).getBoolean("duplicate", false));
			assertEquals(beforeRetry, after.recoveryArtifact());
		}
		assertEquals("SETUP", view(original, "home").getString("phase", null));
		restore(server, document, original);
	}

	@Test void corruptArtifactFailsClosedAndLegacyLifetimeCannotBeUpgraded() throws Exception {
		SetupSession legacy = new SetupSessionTest().session(11);
		assertThrows(IllegalStateException.class, legacy::recoveryArtifact);
		Field field = SetupSession.class.getDeclaredField("document"); field.setAccessible(true);
		MatchDocument document = (MatchDocument) field.get(legacy);
		assertEquals("RECOVERY_CORRUPT", assertThrows(MatchService.Failure.class,
			() -> new SetupSession(new TestServer().getServer(), document, "{}")).code);
	}

	@Test void unsupportedVersionsAndUnknownRecoveryFieldsAreRejectedEvenWithValidChecksum() throws Exception {
		SetupSession fixture = new SetupSessionTest().session(11);
		Field field = SetupSession.class.getDeclaredField("document"); field.setAccessible(true);
		MatchDocument document = (MatchDocument) field.get(fixture);
		TestServer server = new TestServer();
		String artifact = new SetupSession(server.getServer(), document, -4, true).recoveryArtifact();
		for (String version : new String[] {"recoveryVersion", "replayVersion", "runtimeVersion", "engineVersion"}) {
			JsonObject payload = JsonObject.readFrom(artifact).get("payload").asObject();
			if (version.endsWith("Version") && (version.equals("runtimeVersion") || version.equals("engineVersion"))) payload.set(version, "unknown");
			else payload.set(version, 99);
			assertEquals("RECOVERY_UNSUPPORTED", assertThrows(MatchService.Failure.class,
				() -> new SetupSession(server.getServer(), document, signed(payload))).code);
		}
		JsonObject extra = JsonObject.readFrom(artifact).get("payload").asObject().add("futureSemantics", true);
		assertEquals("RECOVERY_CORRUPT", assertThrows(MatchService.Failure.class,
			() -> new SetupSession(server.getServer(), document, signed(extra))).code);
		JsonObject damaged = JsonObject.readFrom(artifact); damaged.get("payload").asObject().set("revision", 123);
		assertEquals("RECOVERY_CORRUPT", assertThrows(MatchService.Failure.class,
			() -> new SetupSession(server.getServer(), document, damaged.toString())).code);
	}

	private String signed(JsonObject payload) throws Exception {
		byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		StringBuilder checksum = new StringBuilder(); for (byte value : bytes) checksum.append(String.format("%02x", value & 255));
		return new JsonObject().add("payload", payload).add("sha256", checksum.toString()).toString();
	}

	private SetupSession restore(TestServer server, MatchDocument document, SetupSession original) {
		SetupSession restored = new SetupSession(server.getServer(), document, original.recoveryArtifact());
		assertEquals(view(original, "home"), view(restored, "home"));
		assertEquals(view(original, "away"), view(restored, "away"));
		assertArtifact(original, restored);
		return restored;
	}

	private JsonObject view(SetupSession session, String role) {
		return session.reply("inspect", "ACCEPTED", false, role).get("state").asObject();
	}

	private void assertArtifact(SetupSession original, SetupSession restored) {
		compare(JsonObject.readFrom(original.recoveryArtifact()).get("payload"), JsonObject.readFrom(restored.recoveryArtifact()).get("payload"), "payload");
	}
	private void compare(com.eclipsesource.json.JsonValue a, com.eclipsesource.json.JsonValue b, String path) {
		if (a.isObject() && b.isObject()) {
			assertEquals(a.asObject().names(), b.asObject().names(), path);
			for (String key : a.asObject().names()) compare(a.asObject().get(key), b.asObject().get(key), path + "/" + key);
		} else if (a.isArray() && b.isArray()) {
			assertEquals(a.asArray().size(), b.asArray().size(), path);
			for (int i = 0; i < a.asArray().size(); i++) compare(a.asArray().get(i), b.asArray().get(i), path + "/" + i);
		} else assertTrue(a.equals(b), path);
	}
	private void removeElapsed(com.eclipsesource.json.JsonValue value) {
		if (value.isObject()) {
			value.asObject().remove("gameTime");
			for (com.eclipsesource.json.JsonObject.Member member : value.asObject()) removeElapsed(member.getValue());
		} else if (value.isArray()) for (com.eclipsesource.json.JsonValue item : value.asArray()) removeElapsed(item);
	}
}
