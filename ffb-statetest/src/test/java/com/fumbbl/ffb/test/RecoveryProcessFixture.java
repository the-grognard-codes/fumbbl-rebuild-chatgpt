package com.fumbbl.ffb.test;

import com.eclipsesource.json.JsonObject;
import com.fumbbl.ffb.server.match.MatchDocument;
import com.fumbbl.ffb.server.match.MatchJson;
import com.fumbbl.ffb.server.match.SetupSession;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Separate-JVM characterization, in addition to the live SIGKILL/browser acceptance driver. */
public final class RecoveryProcessFixture {
	public static void main(String[] args) throws Exception {
		JsonObject input = JsonObject.readFrom(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
		JsonObject document = input.get("document").asObject();
		MatchDocument frozen = new MatchJson().decode(document.toString(), document.get("documentVersion").asInt());
		String artifact = input.get("artifact").asString();
		SetupSession restored = new SetupSession(new TestServer().getServer(), frozen, artifact);
		if (!artifact.equals(restored.recoveryArtifact())) throw new IllegalStateException("Checkpoint bytes changed across JVM restoration");
		System.out.println("PASS separate JVM checkpoint parity");
	}
}
