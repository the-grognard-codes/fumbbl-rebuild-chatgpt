package com.fumbbl.ffb.server.match;

import com.eclipsesource.json.JsonObject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryDiceTest {
	@Test void formatOneHasPinnedCounterEncodingAndRejectionSamplingVector() {
		RecoveryDice dice = new RecoveryDice(new JsonObject().add("version", 1)
			.add("seed", java.util.Base64.getEncoder().encodeToString(new byte[32])).add("counter", 0));
		for (int expected : new int[] {4, 3, 4, 6, 1, 5, 1, 1, 4, 3, 6, 6, 1, 1, 5, 4}) assertEquals(expected, dice.roll(6));
		assertEquals(17, dice.snapshot().get("counter").asLong());
	}
	@Test void continuationPreservesEveryDieAndCounterAcrossSnapshot() {
		RecoveryDice live = new RecoveryDice();
		for (int i = 0; i < 31; i++) live.roll(6);
		RecoveryDice restored = new RecoveryDice(live.snapshot());
		for (int i = 0; i < 4096; i++) {
			int sides = new int[] { 2, 3, 6, 8, 16, 255 }[i % 6];
			int roll = live.roll(sides);
			assertTrue(roll >= 1 && roll <= sides);
			assertEquals(roll, restored.roll(sides));
		}
		assertEquals(live.snapshot(), restored.snapshot());
	}

	@Test void invalidOrUnsupportedStateFailsClosed() {
		JsonObject state = new RecoveryDice().snapshot();
		assertThrows(IllegalArgumentException.class, () -> new RecoveryDice(state.set("version", 2)));
		assertThrows(IllegalArgumentException.class, () -> new RecoveryDice(state.set("version", 1).set("counter", -1)));
		assertThrows(IllegalArgumentException.class, () -> new RecoveryDice(state.set("counter", 0).set("seed", "AA==")));
		assertThrows(IllegalArgumentException.class, () -> new RecoveryDice().roll(256));
	}
}
