package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Format-2 recovery normalization of native maps/sets, never ordered engine sequences. */
public final class RecoveryNativeJson {
	public JsonObject normalize(JsonObject snapshot) {
		JsonObject game = snapshot.get("game").asObject();
		JsonObject options = game.get("gameOptions").asObject();
		options.set("gameOptionArray", sorted(options.get("gameOptionArray").asArray(),
			Comparator.comparing(value -> value.asObject().get("gameOptionId").asString())));
		for (String name : new String[] {"teamHome", "teamAway"}) {
			JsonObject team = game.get(name).asObject();
			for (JsonValue item : team.get("roster").asObject().get("positionArray").asArray()) {
				JsonObject position = item.asObject();
				for (String categories : new String[] {"skillCategoriesNormal", "skillCategoriesDouble"})
					position.set(categories, sorted(position.get(categories).asArray(), Comparator.comparing(JsonValue::asString)));
			}
		}
		JsonObject field = game.get("fieldModel").asObject();
		for (String set : new String[] {"pushbackSquareArray", "moveSquareArray", "trackNumberArray", "diceDecorationArray", "fieldMarkerArray", "playerMarkerArray"})
			sortMember(field, set);
		for (JsonValue player : field.get("playerDataArray").asArray()) {
			sortMember(player.asObject(), "cards"); sortMember(player.asObject(), "cardEffects");
		}
		if (game.get("actingPlayer") != null && !game.get("actingPlayer").isNull()) sortMember(game.get("actingPlayer").asObject(), "usedSkills");
		normalizeNamedSets(snapshot);
		return snapshot;
	}

	private void sortMember(JsonObject object, String name) {
		JsonValue value = object.get(name);
		if (value != null && value.isArray()) object.set(name, sorted(value.asArray(), Comparator.comparing(item -> canonical(item).toString())));
	}

	private void normalizeNamedSets(JsonValue value) {
		if (value.isObject()) {
			// These property names are the native InducementSet map and BlockRoll source set.
			for (JsonObject.Member member : value.asObject()) normalizeNamedSets(member.getValue());
			sortMember(value.asObject(), "inducementArray"); sortMember(value.asObject(), "reRollSources");
		} else if (value.isArray()) for (JsonValue item : value.asArray()) normalizeNamedSets(item);
	}

	private JsonValue canonical(JsonValue value) {
		if (value.isObject()) {
			JsonObject result = new JsonObject(); value.asObject().names().stream().sorted().forEach(key -> result.add(key, canonical(value.asObject().get(key)))); return result;
		}
		if (value.isArray()) { JsonArray result = new JsonArray(); for (JsonValue item : value.asArray()) result.add(canonical(item)); return result; }
		return value;
	}

	private JsonArray sorted(JsonArray values, Comparator<JsonValue> comparator) {
		List<JsonValue> items = new ArrayList<>(); for (JsonValue value : values) items.add(value);
		items.sort(comparator); JsonArray result = new JsonArray(); items.forEach(result::add); return result;
	}
}
