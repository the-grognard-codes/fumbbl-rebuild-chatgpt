package com.fumbbl.ffb.server.local;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

/** Public, immutable projection of the local engine's pending block selection. */
final class BrowserChoice {
	private final String id;
	private final String actor;
	private final long revision;
	private final int optionCount;

	BrowserChoice(String id, String actor, long revision, int optionCount) {
		if (optionCount < 1 || optionCount > 3) throw new IllegalArgumentException("Invalid block option count");
		this.id = id;
		this.actor = actor;
		this.revision = revision;
		this.optionCount = optionCount;
	}

	String getId() { return id; }
	String getActor() { return actor; }

	int indexOf(String optionId) {
		for (int index = 0; index < optionCount; index++) {
			if (("die-" + index).equals(optionId)) return index;
		}
		return -1;
	}

	JsonObject toJson() {
		JsonArray options = new JsonArray();
		for (int index = 0; index < optionCount; index++) {
			options.add(new JsonObject().add("id", "die-" + index).add("label", "Both Down"));
		}
		return new JsonObject().add("id", id).add("type", "block").add("actor", actor)
			.add("revision", revision).add("options", options);
	}
}
