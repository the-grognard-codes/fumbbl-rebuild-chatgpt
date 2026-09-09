package com.fumbbl.ffb.server.match;

/** Versioned durable preparation document. Membership, rather than a credential label, assigns roles. */
public final class MatchDocument {
	public enum Lifecycle { WAITING_FOR_OPPONENT, AWAITING_SETUP }
	public final String matchId, intendedOpponent;
	public final int documentVersion;
	public final Lifecycle lifecycle;
	public final Member home, away;
	public final java.util.Map<String, Request> requests;
	public MatchDocument(String matchId, int documentVersion, String intendedOpponent, Lifecycle lifecycle, Member home, Member away) { this(matchId,documentVersion,intendedOpponent,lifecycle,home,away,new java.util.LinkedHashMap<String,Request>()); }
	public MatchDocument(String matchId, int documentVersion, String intendedOpponent, Lifecycle lifecycle, Member home, Member away, java.util.Map<String,Request> requests) {
		this.matchId=matchId; this.documentVersion=documentVersion; this.intendedOpponent=intendedOpponent; this.lifecycle=lifecycle; this.home=home; this.away=away;
		this.requests=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String,Request>(requests));
	}
	public MatchDocument joined(Member opponent, String requestKey, String fingerprint) {
		if (away != null) throw new IllegalStateException("Seat occupied");
		java.util.Map<String,Request> next=new java.util.LinkedHashMap<String,Request>(requests); next.put(requestKey,new Request(fingerprint));
		return new MatchDocument(matchId, documentVersion + 1, intendedOpponent, Lifecycle.AWAITING_SETUP, home, opponent,next);
	}
	public Request request(String owner,String requestId) { return requests.get(owner + "\n" + requestId); }
	public static final class Request { public final String fingerprint; public Request(String fingerprint){this.fingerprint=fingerprint;} }
	public static final class Member {
		public final String role, owner;
		public final FrozenTeam team;
		public Member(String role, String owner, FrozenTeam team) { this.role=role; this.owner=owner; this.team=team; }
	}
}
