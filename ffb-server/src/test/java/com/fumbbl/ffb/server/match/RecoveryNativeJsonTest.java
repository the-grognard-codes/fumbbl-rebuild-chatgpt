package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryNativeJsonTest {
	@Test void unorderedNativeCollectionsNormalizeButStackDiceAndReportsKeepTheirOrder() {
		JsonObject first = fixture(false), permuted = fixture(true);
		RecoveryNativeJson format = new RecoveryNativeJson();
		assertEquals(format.normalize(first), format.normalize(permuted));
		assertEquals("[6,1,5]", first.get("currentStep").asObject().get("blockRoll").toString());
		assertEquals("[\"second\",\"first\"]", first.get("stepStack").asObject().get("steps").toString());
		assertEquals("[\"injury\",\"turnover\"]", first.get("currentStep").asObject().get("reports").toString());
		assertEquals(first, format.normalize(JsonObject.readFrom(first.toString())), "Normalization is idempotent");
	}

	private JsonObject fixture(boolean reverse) {
		JsonArray categories = reverse ? new JsonArray().add("strength").add("agility") : new JsonArray().add("agility").add("strength");
		JsonObject position = new JsonObject().add("skillCategoriesNormal", categories).add("skillCategoriesDouble", categories);
		JsonObject team = new JsonObject().add("roster", new JsonObject().add("positionArray", new JsonArray().add(position)));
		JsonObject a = new JsonObject().add("gameOptionId", "A").add("gameOptionValue", true);
		JsonObject b = new JsonObject().add("gameOptionId", "B").add("gameOptionValue", false);
		JsonObject game = new JsonObject().add("teamHome", team).add("teamAway", JsonObject.readFrom(team.toString()))
			.add("gameOptions", new JsonObject().add("gameOptionArray", reverse ? new JsonArray().add(b).add(a) : new JsonArray().add(a).add(b)))
			.add("fieldModel", new JsonObject().add("playerDataArray", new JsonArray()))
			.add("actingPlayer", new JsonObject().add("usedSkills", categories));
		return new JsonObject().add("game", game).add("stepStack", new JsonObject().add("steps", new JsonArray().add("second").add("first")))
			.add("currentStep", new JsonObject().add("blockRoll", new JsonArray().add(6).add(1).add(5)).add("reports", new JsonArray().add("injury").add("turnover")));
	}
}
