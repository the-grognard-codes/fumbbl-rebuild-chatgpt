# M3d completion and private replay

The unchanged `human-exhibition-1150` preset uses native BB2025 scoring, two
eight-turn halves, subsequent drive setup/kickoff and halftime recovery/rerolls.
Overtime is disabled (the existing default); draws are final results. There is
no concession command, progression campaign, historical replay import or new
catalog content. Native endgame settlement still runs. Only its legacy external
replay-save side effect is bypassed for application-owned engine sessions.

## Live state and completion

The paired M3d setup projection adds `half` (1 or 2), `drive` (starting at 1),
`homeTurn`, `awayTurn`, `homeScore` and `awayScore`. `phase` additionally accepts
`FULL_TIME`, which has no actions. Pre-match displays half 1; the native engine's
pre-match half-zero sentinel is not a playable half. Touchdowns and halftime
are native transitions, not new browser commands. Use the existing setup and
kickoff controls for every new drive. Both views show the authoritative score.

The server records one initial state event at revision zero and one event for
each accepted mutation. Rejected actions and exact retries add no events. Event
kinds are `START`, `ACTION`, `SELECTION`, `TOUCHDOWN`, `HALFTIME`, and `FULL_TIME`.
These are server-recorded state projections after engine resolution, not a stable
domain-event log or a recording of every intermediate native animation/report.
When scoring also ends a half or match, the final half/full-time event contains
the new score. Replay never executes commands or rolls dice again.

Before acknowledging full time, the server atomically replaces the activated
document (revision 3) with a completed document (revision 4), including frozen
membership, teams, final result and the complete bounded replay. A database
failure before commit returns `PERSISTENCE_FAILED`; a lost commit acknowledgement
returns `MATCH_OUTCOME_UNKNOWN`. The engine may already be terminal in either
case. Reload setup or retry the exact retained request to reconcile; never assume
that failure means the turn should run again. The resident engine is not rerun.
The first successful reconciliation broadcasts full time to the peer even when
the engine request is a duplicate. Further retries do not broadcast or save again.

After JVM restart, completed matches remain readable through setup/result/replay.
New setup mutations return `MATCH_COMPLETED`; the in-memory action history is not
restored, so old gameplay requests receive that code instead of a duplicate ack.
Activation retries cannot initialize a completed match. In-progress matches still
return `SESSION_UNAVAILABLE` after restart. A terminal engine whose result was
never committed cannot recover after process loss; durable in-progress recovery
remains later work.

## Result and replay protocol

After the existing authenticated join, participants use the same WebSocket:

```json
{"version":1,"type":"matchResult","operation":"load","requestId":"result-1","matchId":"12345678-1234-1234-1234-123456789abc"}
{"version":1,"type":"matchResult","operation":"replay","requestId":"replay-1","matchId":"12345678-1234-1234-1234-123456789abc","index":0}
```

Fields are exact; replay adds only the nonnegative integer `index`. An index
outside `[0,eventCount)` is rejected. Responses have exactly `version:1`,
`type:"matchResult"`, `requestId`, `code`, `result`, and `event`. Success has
`code:"ACCEPTED"` and metadata:

```json
{"formatVersion":1,"engineVersion":"ffb-3.4.0-bb2025-m3d.1","ruleset":"BB2025","catalogVersion":"bb2025-human-2026-09-08.1","presetId":"human-exhibition-1150","presetVersion":"bb2025-human-2026-09-08.1","matchId":"12345678-1234-1234-1234-123456789abc","homeScore":1,"awayScore":0,"finalRevision":100,"eventCount":101}
```

`load` returns `event:null`. `replay` returns exactly one
`{revision,kind,state}`. State has the paired setup shape, with `callerRole:"home"`
as the fixed replay orientation, `actions:[]`, and `prompt:null`. This value
does not grant a role. Scores, team positions, ball, player states, turn, half,
drive, weather and rerolls come from recorded server state. Results and events
are read-only; read retries have no mutation semantics.

Errors return null result/event: `NOT_FOUND`, `NOT_COMPLETED`,
`REPLAY_UNSUPPORTED`, `SNAPSHOT_UNSUPPORTED`, `PERSISTENCE_FAILED`,
`INVALID_REQUEST`, or `AUTHENTICATION_REQUIRED`. Result reads and every indexed
event read check persisted membership before interpreting or returning artifacts.
There is no public match listing, spectator replay, token in events, raw Java
model, chat, or historical upload route. Both persisted participants can read the
same public-on-pitch history, using their actual match membership even when local
credential labels and match roles are reversed.

## Bounds and compatibility

Replay format 1 pins the engine, ruleset, catalog and preset versions above.
Unknown result/replay formats return explicit incompatibility and preserve stored
bytes; there is no automatic conversion. Prepared documents remain format 1 at
revisions 1–3. Completion uses document format 2 at revision 4. Older readers
reject these completed documents instead of treating them as fresh activations.
Deploy the local browser and server as a pair.

The resident replay has at most 8,193 events (initial plus up to 8,192 accepted
requests) and a 16 MiB artifact limit. Each persisted event is at most 64 KiB.
Mutation admission reserves 128 KiB for the next bounded projection, envelope
and array separators. `REPLAY_LIMIT` rejects new actions before engine execution
when this budget is exhausted. No earlier events are evicted or silently omitted.
The existing 32 resident-session limit and transport bounds remain; completed
resident sessions are retained for same-JVM retries. Result reads send one event
at a time, never the complete replay over the socket. The viewer retains one event.

Open `/results?matchId=...`, authenticate, and use First/Previous/Next/Last to
inspect recorded states. No historical playback or intermediate dice animation
is claimed. See the [migration guide](../containers/local/completed-match-migration.md)
and [actual M3d evidence](../.notes/overhaul-analysis/verification/m3d/README.md).
