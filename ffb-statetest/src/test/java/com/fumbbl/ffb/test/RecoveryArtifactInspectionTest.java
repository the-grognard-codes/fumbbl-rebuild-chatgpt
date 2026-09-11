package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.fumbbl.ffb.server.GameState;
import com.fumbbl.ffb.server.match.MatchDocument;
import com.fumbbl.ffb.server.match.MatchJson;
import com.fumbbl.ffb.server.match.SetupSession;
import com.fumbbl.ffb.server.match.RecoveryNativeJson;
import com.fumbbl.ffb.server.match.MatchService;
import com.fumbbl.ffb.server.util.UtilSkillBehaviours;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Read-only inspection of an explicitly supplied retained private process checkpoint. */
class RecoveryArtifactInspectionTest {
	@Test void retainedArtifactHasNativeAndDecisionParityInAnotherJvm() throws Exception {
		String path = System.getenv("R2_RECOVERY_INSPECT"); assumeTrue(path != null);
		JsonObject evidence = JsonObject.readFrom(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8));
		JsonObject checkpoint = evidence.get("checkpoint").asObject(), document = evidence.get("document").asObject();
		TestServer server = new TestServer(); GameState state = new GameState(server.getServer());
		JsonValue original = checkpoint.get("payload").asObject().get("native");
		state.initFrom(server.getServer().getFactorySource(), original);
		UtilSkillBehaviours.registerBehaviours(state.getGame(), server.getServer().getDebugLog());
		RecoveryNativeJson format = new RecoveryNativeJson();
		List<String> differences = new ArrayList<>(); diff(format.normalize(JsonObject.readFrom(original.toString())), format.normalize(state.toJsonValue(true, 0)), "native", differences);
		assertTrue(differences.isEmpty(), "Native differences (paths only): " + differences);
		MatchDocument frozen = new MatchJson().decode(document.toString(), document.get("documentVersion").asInt());
		if (checkpoint.get("payload").asObject().getInt("recoveryVersion", -1) == 1)
			assertEquals("RECOVERY_UNSUPPORTED", assertThrows(MatchService.Failure.class,
				() -> new SetupSession(server.getServer(), frozen, checkpoint.toString())).code);
		else new SetupSession(server.getServer(), frozen, checkpoint.toString());
	}

	private void diff(JsonValue a, JsonValue b, String path, List<String> differences) {
		if (differences.size() > 30) return;
		if (a == null || b == null) { if (a != b) differences.add(path + " missing"); return; }
		if (a.isObject() && b.isObject()) {
			java.util.Set<String> keys = new java.util.TreeSet<>(a.asObject().names()); keys.addAll(b.asObject().names());
			for (String key : keys) diff(a.asObject().get(key), b.asObject().get(key), path + "/" + key, differences);
		} else if (a.isArray() && b.isArray()) {
			if (a.asArray().size() != b.asArray().size()) differences.add(path + " size");
			for (int i = 0; i < Math.min(a.asArray().size(), b.asArray().size()); i++) diff(a.asArray().get(i), b.asArray().get(i), path + "/" + i, differences);
		} else if (!a.equals(b)) differences.add(path);
	}
}
