package com.fumbbl.ffb.server.match;

import java.sql.SQLException;

public interface MatchRepository {
	Record find(String matchId) throws SQLException;
	void insert(Record record) throws SQLException;
	boolean replace(Record record, int expectedVersion) throws SQLException;
	final class Record {
		public final String matchId, json; public final int documentVersion;
		public Record(String matchId, int documentVersion, String json) { this.matchId=matchId; this.documentVersion=documentVersion; this.json=json; }
	}
	final class OutcomeUnknown extends SQLException { public final Record attempted; public OutcomeUnknown(Record r, SQLException cause) { super("Match commit outcome unknown", cause); attempted=r; } }
}
