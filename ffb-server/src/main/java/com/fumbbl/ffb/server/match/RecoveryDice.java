package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Private per-match HMAC-SHA256 counter stream, format 1. Never sent to clients. */
public final class RecoveryDice {
	private final byte[] seed;
	private long counter;

	public RecoveryDice() {
		seed = new byte[32];
		new SecureRandom().nextBytes(seed);
	}

	public RecoveryDice(JsonObject snapshot) {
		if (snapshot.size() != 3 || snapshot.getInt("version", -1) != 1) throw new IllegalArgumentException("Unsupported dice state");
		seed = Base64.getDecoder().decode(snapshot.get("seed").asString());
		counter = snapshot.get("counter").asLong();
		if (seed.length != 32 || counter < 0) throw new IllegalArgumentException("Invalid dice state");
	}

	public int roll(int sides) {
		if (sides < 1 || sides > 255) throw new IllegalArgumentException("Invalid die");
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(seed, "HmacSHA256"));
			int value;
			do {
				if (counter == Long.MAX_VALUE) throw new IllegalStateException("Dice stream exhausted");
				value = mac.doFinal(ByteBuffer.allocate(8).putLong(counter++).array())[0] & 255;
			} while (value >= 256 - 256 % sides);
			return 1 + value % sides;
		} catch (GeneralSecurityException unavailable) { throw new IllegalStateException("Dice provider unavailable", unavailable); }
	}

	public JsonObject snapshot() {
		return new JsonObject().add("version", 1).add("seed", Base64.getEncoder().encodeToString(seed)).add("counter", counter);
	}
}
