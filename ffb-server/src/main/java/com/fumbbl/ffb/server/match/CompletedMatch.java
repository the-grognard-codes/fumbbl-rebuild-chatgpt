package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, canonical terminal match artifact. */
public final class CompletedMatch {
	public static final String ENGINE_VERSION = "ffb-3.4.0-bb2025-m3d.1";
	private static final int MAX_BYTES = 16 * 1024 * 1024;
	private final String json;

	public CompletedMatch(String json) { this.json = canonical(json); }
	public String json() { return json; }
	@Override public String toString() { return json; }
	@Override public boolean equals(Object other) { return other instanceof CompletedMatch && json.equals(((CompletedMatch) other).json); }
	@Override public int hashCode() { return json.hashCode(); }

	private static String canonical(String text) {
		if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Invalid completed match");
		try { depth(text); return sort(JsonObject.readFrom(text)).toString(); } catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid completed match"); }
	}
	private static void depth(String text) {
		boolean quoted = false, escaped = false; int depth = 0;
		for (int index = 0; index < text.length(); index++) {
			char ch = text.charAt(index);
			if (quoted) { if (escaped) escaped = false; else if (ch == '\\') escaped = true; else if (ch == '"') quoted = false; }
			else if (ch == '"') quoted = true;
			else if (ch == '{' || ch == '[') { if (++depth > 16) throw new IllegalArgumentException(); }
			else if (ch == '}' || ch == ']') depth--;
		}
		if (quoted || depth != 0) throw new IllegalArgumentException();
	}
	private static JsonValue sort(JsonValue value) {
		if (value.isArray()) { JsonArray result = new JsonArray(); for (JsonValue item : value.asArray()) result.add(sort(item)); return result; }
		if (!value.isObject()) return value;
		JsonObject object = value.asObject(), result = new JsonObject(); List<String> names = new ArrayList<String>(object.names()); Collections.sort(names);
		for (String name : names) result.add(name, sort(object.get(name))); return result;
	}
}
