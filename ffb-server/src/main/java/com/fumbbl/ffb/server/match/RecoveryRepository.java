package com.fumbbl.ffb.server.match;

import java.sql.SQLException;

public interface RecoveryRepository {
	Record find(String matchId) throws SQLException;
	boolean save(Record record, long expectedGeneration) throws SQLException;

	final class Record {
		public final String matchId, json;
		public final long generation;

		public Record(String matchId, long generation, String json) {
			this.matchId = matchId;
			this.generation = generation;
			this.json = json;
		}
	}

	final class OutcomeUnknown extends SQLException {
		public final Record attempted;

		public OutcomeUnknown(Record attempted, SQLException cause) {
			super("Recovery commit outcome unknown", cause);
			this.attempted = attempted;
		}
	}
}
