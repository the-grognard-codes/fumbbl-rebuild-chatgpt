# Browser setup protocol v1 (M3a)

The local `/browser/v1` socket now activates persisted prepared matches and drives
BB2025 pre-match/setup. It retains Java 8/Maven, MariaDB/JDBC, the current Human
exhibition preset, and the authoritative engine. `/matches` prepares and activates;
`/setup?matchId=<uuid>` presents the setup controls. The scenario board remains a
separate diagnostic route. Neither route accepts dice, seeds, fixture resets,
resolved teams, arbitrary engine commands, or caller-supplied roles for product setup.

## Durable activation

Authenticate using the existing local `join` envelope. Its credential label is an
identity subject, not a product role. Either persisted member may activate:

```json
{"version":1,"type":"preparedMatch","operation":"activate","requestId":"activate-1","matchId":"00000000-0000-0000-0000-000000000002","expectedRevision":2}
```

The existing prepared-match response returns revision 3, lifecycle `ACTIVATED`,
and the caller's persisted role. Activation records a third accepted request with
both frozen teams and membership in one compare-and-swap write. Exactly one fresh
transition can initialize the engine. Exact retries return the current durable
document with `duplicate:true`; changed request reuse fails. A different activation
key against an activated document returns `ALREADY_ACTIVATED`. `MATCH_NOT_READY`,
`STALE_REVISION`, and `ACTIVATION_LIMIT` do not initialize an engine.

A pre-COMMIT failure returns `PERSISTENCE_FAILED`. Lost COMMIT acknowledgement
returns `MATCH_OUTCOME_UNKNOWN`. A pending reservation in that same JVM permits
only the original exact request to reconcile and initialize once. Initialization
is reserved before it begins, including if it fails. Malformed or changed retries
cannot consume the original reservation. There is no automatic mutation replay.

Activation acknowledgement confirms the durable transition. Open setup to learn
whether its engine is available. A failure or process termination between the
write and initialization can leave an activated document without an engine; the
server reports `SESSION_UNAVAILABLE`, never reverses the lifecycle or retries an
already attempted initialization.

## Setup requests and delivery

After authentication, load subscribes this connection to one product match:

```json
{"version":1,"type":"setup","operation":"load","requestId":"setup-load-1","matchId":"00000000-0000-0000-0000-000000000002"}
{"version":1,"type":"setup","operation":"choice","requestId":"coin-1","matchId":"00000000-0000-0000-0000-000000000002","expectedRevision":0,"promptId":"00000000-0000-0000-0000-000000000002-0","optionId":"heads"}
{"version":1,"type":"setup","operation":"place","requestId":"place-1","matchId":"00000000-0000-0000-0000-000000000002","expectedRevision":2,"playerId":"<frozen-source-team-uuid>:p1","to":{"x":12,"y":6}}
{"version":1,"type":"setup","operation":"confirm","requestId":"confirm-1","matchId":"00000000-0000-0000-0000-000000000002","expectedRevision":13}
```

Placement `to:null` returns a player to reserves. Public coordinates always use
home orientation (26 by 15); only the adapter transforms an away command for the
legacy engine. Placement requires an owned player and an empty square in that
team's half. Repositioning and returning to reserves allow correction before
confirmation. Intermediate formations need not be complete: the unchanged BB2025
setup mechanic validates the whole formation before confirmation. Captain Pro and
the engine's Team Captain trait derive from the frozen captain selection.

All fields are allowlisted; duplicates, unknown fields, fractional revisions and
malformed coordinates fail before engine mutation. Existing 16 KiB ingress,
bounded nesting, communication worker, connection/admission/outbound queue limits
apply. Every load, choice, placement, confirmation, retry, and peer broadcast
re-reads persisted membership. Fixture credential role labels cannot authorize
product actions. No product engine is registered in legacy GameCache or the
legacy SessionManager. A small `usesLegacyPersistence` application seam suppresses
legacy database writes; legacy games retain their existing behavior.

One result envelope is returned to the sender:

```json
{"version":1,"type":"setupState","requestId":"setup-load-1","code":"ACCEPTED","duplicate":false,"state":{"matchId":"00000000-0000-0000-0000-000000000002","revision":0,"callerRole":"home","phase":"PRE_MATCH","actor":"away","prompt":{"id":"00000000-0000-0000-0000-000000000002-0","actor":"away","kind":"coin","options":["heads","tails"]},"players":[],"weather":"NICE","homeRerolls":0,"awayRerolls":0}}
```

`players` above is abbreviated. Each actual player has exactly `id`, `name`,
`slot`, `role`, `x`, `y`. Both coordinates are null in reserves. Other state fields
are exactly as shown. Rerolls reflect the engine at that phase, including its
start-half initialization. `prompt` is null outside pre-match decisions. `phase`
is `PRE_MATCH`, `SETUP`, or `READY_FOR_KICKOFF`. `actor` comes from the engine;
receive-choice ownership can differ from the active team. The other prompt kind
is `receive`, with `receive` and `kick` options.

Accepted mutations advance the setup revision once. Each other subscribed peer
receives one complete `setupState` with `requestId:null`. Loads and exact retries
send no broadcast. History is bounded to 256 accepted actions/illegal-setup
confirmations per resident match; new requests fail at capacity without eviction.
The key is persisted role plus request ID, with canonical semantic request data.
Exact retries resolve before stale/prompt checks, never rerun the engine, and
return the original code with `duplicate:true` and the **current full state**.
This current-snapshot policy differs from M1's fixture result revision policy.
Other rejected operations do not retain history or change engine state/revision.

`ILLEGAL_SETUP` returns the unchanged state so the browser can correct placement;
an exact retry of it also returns the current state. Other errors have null state
and `duplicate:false`. Codes include `NOT_ACTIVATED`, `SESSION_UNAVAILABLE`,
`WRONG_ACTOR`, `WRONG_PHASE`, `WRONG_PLAYER`, `ILLEGAL_PLACEMENT`, `ILLEGAL_SETUP`,
`PROMPT_MISMATCH`, `INVALID_OPTION`, `STALE_REVISION`, `REQUEST_ID_REUSED`,
`REQUEST_HISTORY_LIMIT`, `INVALID_REQUEST`, `NOT_FOUND`, `AUTHENTICATION_REQUIRED`,
`PERSISTENCE_FAILED`, and `SNAPSHOT_UNSUPPORTED`. Failed engines only return
`SESSION_UNAVAILABLE`, including loads and formerly accepted retries.

## Engine flow, reconnect and restart

Frozen teams enter the genuine BB2025 `StartGame` sequence, including initial
start, spectator/fan rolls, weather, petty-cash calculation, and the preset's
empty predefined inducement set. No inducement purchases or underdog prayers are
supported by this preset. Explicit activation replaces the legacy lobby's start
acknowledgement; no synthetic positions or deterministic rolls are installed.
The retained engine requests the coin call and kick/receive choice, sets up the
kicking team then the receiving team, and validates both confirmations. M3a stops
at the engine's `KICKOFF` step waiting for ball placement. No kick or kickoff event
has occurred; gameplay and match completion remain later M3 slices.

Disconnect disables actions. Same-JVM rejoin/load restores the full authoritative
state and pending prompt. The page retains its last request in memory for explicit
retry, guarded by authenticated subject and match ID. No automatic mutation replay
occurs. Reloading the page loses this convenience record. Retired socket callbacks
and unrelated replies cannot update the UI, and older snapshots cannot regress it.

**Server restart permanently retires these in-memory setup sessions.** Prepared
rows remain revision 3 `ACTIVATED`, with frozen teams and accepted activation
metadata. Load reports `SESSION_UNAVAILABLE`; even an exact activation retry cannot
reinitialize a session from another process lifetime. Prepare a new match to play
again. There is no in-progress restart recovery, persisted setup placement, durable
setup-action history, reset-to-awaiting operation, or automatic snapshot migration.

The local application retains at most 32 activation reservations/engine lifetimes,
including failures and kickoff-ready matches. At capacity new activations fail
closed. Fixture resets do not discard product matches or release this capacity.
A JVM restart clears resident capacity but retires the activated sessions as
specified above. Match completion/handoff and capacity release belong to later
M3 work; there is no silent eviction of ready matches.

See [migration boundary](../containers/local/setup-activation-migration.md) and
[actual evidence](../.notes/overhaul-analysis/verification/m3a/README.md).

## M3b extension

[Core-turn protocol](core-turns.md) extends this historical M3a boundary beyond kickoff readiness. Its snapshot fields and history limit supersede the corresponding M3a definitions.
