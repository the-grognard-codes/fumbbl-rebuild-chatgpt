# Browser protocol v1 — movement, choices and M1c transport bounds

## M2a catalog and draft evaluation

The same authenticated local WebSocket now accepts two read-only operations:

```json
{"version":1,"type":"catalog","requestId":"catalog-1"}
{"version":1,"type":"validateTeam","requestId":"validate-1","draft":{"catalogVersion":"bb2025-human-2026-09-08.1","ruleset":"BB2025","rosterId":"human","presetId":"human-exhibition-1150","captainId":null,"players":[],"resources":{"rerolls":0,"assistantCoaches":0,"cheerleaders":0,"apothecary":0,"dedicatedFans":0}}}
```

The empty draft above returns `PLAYER_COUNT`. Each player is exactly
`{"id":"p1","slot":1,"positionId":"lineman","skillIds":[]}`. Supply 11–16
players with unique IDs and integer slots 1–16 for a legal draft. `skillIds`
contains only purchased skills. The server owns base skills, stats, costs,
position limits and resource prices. Captain ID is null or a draft player ID.
No supplied total is accepted, even if numerically correct.

Successful catalog lookup returns `type:"catalog"`; the complete executable
projection is `test/fixtures/catalog-v1.json`. Every response includes version,
request ID, catalog version and ruleset. See [content and source mapping](catalog.md).

```json
{"version":1,"type":"teamValidation","requestId":"validate-1","catalogVersion":"bb2025-human-2026-09-08.1","ruleset":"BB2025","valid":false,"budget":1150000,"skillPoints":0,"messages":[{"code":"PLAYER_COUNT","path":"players","text":"A draft must contain 11 to 16 players."}],"total":0}
```

`total` is the server-computed gold cost, or null if references/quantities cannot
be priced. A total on an invalid draft is diagnostic, never acceptance.
`skillPoints` is computed independently of gold. `valid` is true only with no
messages. Unknown version/ruleset/roster/preset produces `CATALOG_VERSION`,
`RULESET`, `ROSTER`, or `PRESET` and no total. Legality messages include
`POSITION`, `POSITION_LIMIT`, `PLAYER_COUNT`, `PLAYER_ID`, `DUPLICATE_PLAYER`,
`DUPLICATE_SLOT`, `SLOT`, `CAPTAIN`, `CAPTAIN_INELIGIBLE`, `SKILL`,
`SKILL_INELIGIBLE`, `DUPLICATE_SKILL`, `SKILL_LIMIT`, `SKILL_POINTS`,
`SECONDARY_LIMIT`, `ELITE_LIMIT`, `QUANTITY` and `OVER_BUDGET`.

[Request schema](team-request.schema.json) defines legal draft structure.
The decoder also accepts some bounded illegal drafts to explain legality errors;
it does not silently coerce numbers, strings, missing or duplicate object fields.
Unexpected fields, wrong types, missing fields, more than 16 player entries or
more than two skill entries per player fail as `MALFORMED_TEAM_REQUEST` using
the existing rejected-result envelope. Two skills decode only to explain the
one-purchase limit; three or more fail at the decoder. More than 16 KiB UTF-8
or nesting deeper than eight is rejected before recursive parsing as
`MALFORMED_MESSAGE` (the existing socket byte limit can close oversized frames
before adapter execution). The nesting guard also protects existing message types;
their valid shapes and transport/queue semantics are unchanged.

These operations require the existing join but do not modify a team or match,
increment match revision, occupy action history, or broadcast. Repeating an
evaluation recomputes it; there is no persistence or exactly-once mutation to
claim. Request IDs correlate responses only. The browser locks its draft while
evaluation is pending and clears results on edits/disconnect, rejecting responses
from retired sockets. Join snapshots remain fixture snapshots, never a team
catalog. Raw XML, legacy model JSON, class names, artwork URLs, dice and fixture
controls are absent. Import/export files remain out of scope; incoming draft
JSON is nevertheless treated as untrusted input.

## M2b saved-team documents

After the existing authenticated `join`, saved-team traffic uses the same WebSocket
and a single strict envelope. `list` has only the four header/operation fields;
`load` adds `teamId`; `create` adds `draft`; `update` adds `teamId`,
`expectedDocumentVersion`, and `draft`; `import` adds a JSON `document`.

```json
{"version":1,"type":"savedTeam","requestId":"save-1","operation":"update","teamId":"12345678-1234-1234-1234-123456789abc","expectedDocumentVersion":3,"draft":{"catalogVersion":"bb2025-human-2026-09-08.1","ruleset":"BB2025","rosterId":"human","presetId":"human-exhibition-1150","captainId":null,"players":[],"resources":{"rerolls":0,"assistantCoaches":0,"cheerleaders":0,"apothecary":0,"dedicatedFans":0}}}
```

Every response is `{version,type:"savedTeam",requestId,code,document,
versionStatus,validation,teams}`. `teams` is at most 50 metadata records on a
list response and is empty for all other operations. Codes are `OK`,
`MALFORMED_TEAM_REQUEST`, `INVALID_DOCUMENT_VERSION`, `CONFLICT`, `NOT_FOUND`,
`VALIDATION_FAILED`, `MIGRATION_REQUIRED`, `VERSION_UNAVAILABLE`,
`PERSISTENCE_FAILED`, `SAVE_OUTCOME_UNKNOWN`, and `LIMIT_REACHED`.

An authoritative document has format version 1, a canonical lower-case UUID,
positive document version, `BB2025`, catalog version, local `home` or `away`
owner, the strict M2a draft, and server-computed validation. Imports never trust
claimed validation. An existing ID imports only for the same owner and matching
document version; an unknown ID may create only at version 1 and receives a new
server UUID. The server never migrates a catalog automatically. A load preserves
its original document and draft, while `versionStatus` reports `CURRENT`,
`MIGRATION_REQUIRED`, or `VERSION_UNAVAILABLE` and validation is evaluated again.
Clients keep unavailable documents visible and editing locked, including across a
reconnect; they can still export them. See [saved-team schema](saved-team.schema.json).

Catalog policy is **current-catalog-only writes**. The only available catalog is
`bb2025-human-2026-09-08.1`. If it remains available but a different catalog becomes
selectable, its documents return `MIGRATION_REQUIRED`. Any other catalog version
returns `VERSION_UNAVAILABLE`. Load always evaluates through `TeamValidation`,
but unavailable content returns an unpriced diagnostic evaluation; the saved
draft, prices and stored validation are not rewritten. Create, update and import
reject both statuses. Updating the catalogVersion field is not a migration and
cannot bypass the old document's version check. There is no automatic migration.

`formatVersion:1` describes the file contract; `documentVersion` is the separate
optimistic revision, from 1 through 2,147,483,646. Creates generate a server UUID
and revision 1. Each replacement increments its revision exactly once with an
atomic JDBC compare-and-swap. Stale updates and stale imports return `CONFLICT`.
Importing an existing owned ID replaces it; importing an unknown revision-1 ID
creates a new server ID and binds the current local owner. Unknown IDs at later
revisions fail with `CONFLICT` rather than pretending they are fresh documents.
File owner metadata cannot change an existing document's owner. Local subjects
are provisional credential-derived namespaces, not public accounts or match roles.

Only canonical identifier/choice data plus newly computed accepted validation is
stored. The supplied validation object's known numeric/boolean fields are
structurally checked and discarded; totals, budgets, skill points and acceptance
are recomputed. Unknown and duplicate fields are rejected at every object level.
The whole request is limited to 16 KiB UTF-8 and eight levels of nesting, including
the import envelope. Quantities must be JSON integers, never strings or fractions.
No names, artwork URLs, legacy model/XML objects, tokens, dice or fixture data
belong to this schema. The owner-scoped list and create capacity are both 50;
`LIMIT_REACHED` leaves the existing rows untouched. This slice has no delete API.

Validation, parse, version, capacity and pre-commit SQL failures leave stored
bytes unchanged. `PERSISTENCE_FAILED` is a known pre-commit failure. Once COMMIT
is attempted, a database/connection failure may mean its acknowledgement was
lost: `SAVE_OUTCOME_UNKNOWN` returns the **attempted**, unconfirmed document and
its recovery ID. Never display that as a completed save or automatically retry
create. Refresh/list and load that ID to reconcile; the browser preserves edits
and blocks further saves/imports meanwhile. A disconnected socket likewise does
not prove rejection. Requests correlate responses but are not durable create
idempotency keys; an uncertain create must be reconciled before another create.
No server log prints request bodies, documents, imported content or credentials.

The saved-team repository uses dedicated short JDBC transactions on the existing
communication worker; it does not alter fixture/game state or gameplay history.
Migration and stop/start boundaries are in the [container guide](../containers/local/README.md).
Saved teams are not match-ready. M2c owns match creation, frozen roster data and
role ownership; M2 remains incomplete.

## Existing match contract

Transport: uncompressed text JSON over `ws://127.0.0.1:22227/browser/v1`.
This route exists only in the explicit local server profile. Allowed browser origins
are exactly `http://127.0.0.1:5173` and `http://localhost:5173`. TLS/public identities,
public route hardening and durable protocol recovery are outside M1a.

Each connection authenticates with a join. A server-side token mapping binds it to
home or away. Subsequent actions contain no actor credential or role selection.
Internal class names, raw Java snapshots, server-only fields and fixture controls
are never part of this protocol.

```json
{"version":1,"type":"join","requestId":"join-home-1","token":"<local home credential>"}
{"version":1,"type":"result","requestId":"join-home-1","status":"accepted","code":"JOINED","revision":0,"duplicate":false}
```

Successful join then sends a full `snapshot`. Public fields include `version:1`,
`type:"snapshot"`, a random per-JVM-fixture `matchId`, `revision`, caller `actor`, `players`, `ball`,
`turnOwner:"home"` and `prompt:null`. Each player contains `id`, `role`, canonical
`x/y`, `movementUsed`, `movementAllowance`, and `state` (`standing`, `prone`,
`stunned`, or `other`). In the movement fixture, home starts at (5,7), away at (20,7),
ball at (12,7), movement allowance 6. Both snapshots are identical except `actor`.
No away-coordinate inversion is performed: the browser contract always uses the
home orientation. The browser validates incoming JSON at runtime.

`resources.home` and `resources.away` each contain integer `rerolls` and boolean
`rerollUsed`, `blitzUsed`, `passUsed`, `handOverUsed`, and `foulUsed`. Both start with
three rerolls and all flags false in the movement fixture. Controlled block
fixtures start with zero rerolls so every available decision is projected.
The browser validates these fields and the live
demo compares them before/after movement. Engine tests compare the full internal
serialized state for rejected and duplicate actions, including turn resources.

```json
{"version":1,"type":"move","requestId":"move-home-1","expectedRevision":0,"playerId":"home-runner","to":{"x":6,"y":7}}
{"version":1,"type":"result","requestId":"move-home-1","status":"accepted","code":"MOVED","revision":1,"duplicate":false}
```

The result is correlated by `requestId` and delivered to its sender. One accepted
move increments revision exactly once and broadcasts one full snapshot to each
joined connection. The step engine consumes movement; the adapter does not update
player coordinates itself. The fixture is isolated from legacy matches. Browser
requests execute on the existing server communication worker, preserving serial
mutation. Full snapshots are deliberately used for this tiny fixture.

A rejected action returns `status:"rejected"`, a reason `code`, the unchanged
revision, and `duplicate:false`. Wrong role/player, off-board, occupied,
nonadjacent, exhausted/unsupported movement, malformed payloads, unauthenticated
actions and unsupported versions are rejected before engine mutation.

```json
{"version":1,"type":"move","requestId":"stale-home-2","expectedRevision":0,"playerId":"home-runner","to":{"x":7,"y":7}}
{"version":1,"type":"result","requestId":"stale-home-2","status":"rejected","code":"STALE_REVISION","revision":1,"duplicate":false}
```

Request history is scoped to `(actor, requestId)` for this fixture lifetime.
Duplicate lookup precedes stale-revision validation. An exact accepted retry
returns the original accepted revision with `duplicate:true`; it neither invokes
the engine nor broadcasts another movement snapshot. Reusing an ID with a changed
body returns `REQUEST_ID_REUSED`. A response revision describes that request;
the browser advances its board only from newer snapshots, never from a result.
Rejected requests do not advance revision or consume game/turn resources.
An unsupported protocol version is explicitly rejected as `UNSUPPORTED_VERSION`.

There is a bounded 256-request history. At capacity, new IDs fail closed with
`REQUEST_HISTORY_LIMIT`; retained IDs still resolve and cannot be applied again.
Restarting the local JVM creates a fresh fixture and history. This is not durable
idempotency across restarts. Clients never automatically replay pending requests
after disconnection. The M1b recovery contract below retains explicit retries
within a fixture lifetime. The M1c delivery policy below defines the verified
queue, history and slow-client bounds.

Concrete sanitized wire examples from the two-browser acceptance run are saved in
the kickoff evidence directory. No request type accepts controlled dice, seed,
scenario, raw game model, or fixture reset; those belong to local setup/tests only.

## M1b pending block choices and recovery

Local startup selects a controlled fixture; the browser cannot load one. Each
block fixture begins at revision 0 after the existing BB2025 engine selects the
home attacker at (7,7), blocks the away defender at (8,7), and rolls Both Down.
Variants cover attacker with/without Block and home/away choosing. Away chooses
two dice when the home attacker has strength 2 against strength 3. Both options
are Both Down, with distinct option IDs. Armor rolls are 2+2. These controls are
entirely in local setup, using the same dice mechanism as engine tests.

The snapshot contains the complete public pending choice on **both** connections:

```json
{"id":"<match-id>-choice-0","type":"block","actor":"away","revision":0,"options":[{"id":"die-0","label":"Both Down"},{"id":"die-1","label":"Both Down"}]}
```

This object is `snapshot.prompt`; it is not a standalone message. The choosing
actor comes from the engine dialog's team, independently of `turnOwner`. Public
options are identical for both viewers; only the authenticated owner may submit.
Prompt IDs are scoped to the unique fixture lifetime. Prompt revision equals
the snapshot revision. No browser-supplied actor, dice result, raw engine dialog,
or legacy command is accepted.

```json
{"version":1,"type":"choice","requestId":"choice-away-1","expectedRevision":0,"choiceId":"<match-id>-choice-0","optionId":"die-0"}
{"version":1,"type":"result","requestId":"choice-away-1","status":"accepted","code":"CHOICE_APPLIED","revision":1,"duplicate":false}
```

The engine resolves the selected option; the adapter increments revision once,
clears the prompt, and broadcasts full snapshots including player states and the
engine's current turn owner. Without Block both players are prone; with Block
only the defender is prone. The fixture ends after this one choice. Further
movement returns `FIXTURE_FINISHED`; there is no general block-start intent yet.

Movement during a prompt returns `CHOICE_PENDING`. Choice validation returns
`WRONG_CHOICE_ACTOR`, `CHOICE_MISMATCH`, `INVALID_OPTION`, or `NO_PENDING_CHOICE`.
Authentication, strict field allowlists, stale revisions and shared 256-request
history apply to choices just as to movement. Fingerprints include intent type,
revision and choice/option IDs. Exact accepted retries resolve **before** stale
revision or pending-choice checks, even after the prompt has disappeared. They
return the original accepted revision with `duplicate:true`, invoke no engine
command, consume no dice/resources and send no extra snapshot. Changed bodies
with a retained request ID return `REQUEST_ID_REUSED`.

Disconnect removes only connection authorization. The engine, prompt, revision
and actor-scoped history remain in memory. Rejoin with the same credential sends
the full current snapshot: before resolution this restores prompt ID, owner,
revision and every option; after a lost acknowledgement it shows the resolved
state. The browser requires a fresh join snapshot before enabling actions and
never automatically replays requests. Same-page **Repeat last request** retains
the exact original choice and is allowed only for the same match ID and actor.
A reload can restore the pending decision but loses the cached request; a JVM
restart changes match ID and has no durable retry guarantee. Re-entering another
actor's credential establishes a new connection, never rebinds an existing one.

## M1c delivery and lifecycle policy

Gameplay wire fields are unchanged from M1b. Browser ingress is limited to 128
queued/in-flight work items across at most 16 admitted connections. Each text
message remains limited to 16 KiB. Engine work still executes on the existing
single communication worker; browser admission never drops legacy work. Saturated
ingress closes the offending connection with 1013 before that submission enters
the adapter/history. Already executed requests retain their outcome. Queued work
on a retired socket is cancelled before adapter execution. A client cannot infer
whether its last request executed from the close code alone: rejoin and explicitly
retry the exact request within the same match/actor lifetime.

Each connection has an ordered asynchronous FIFO capped at 64 messages and 256 KiB
of UTF-8 payload, including the in-flight message. Results are correlated,
nonreplaceable records; snapshots are full replaceable views. This implementation
deliberately coalesces neither: overflow disconnects and requires full resync.
It never silently drops a result while keeping the connection usable. Results
remain recoverable from adapter history even when their connection fails before
delivery. A two-second write watchdog and Jetty async write timeout terminate
stalled delivery; write failure/overflow/disconnect clear queued payloads and
retire authorization. Late write callbacks cannot resurrect queues. Cleanup is
idempotent and remains bounded independently of exhausted browser ingress.

The 256-entry history is unchanged and shared across actor-scoped keys. Valid
authenticated actions that reach history admission occupy entries, including
validation rejections; malformed/unsupported/unauthenticated messages do not.
Capacity rejections do not evict entries. Exact accepted retries return the
original accepted revision with `duplicate:true`, before stale/prompt validation.
Changed bodies remain rejected at capacity and after reconnect. Overload before
adapter admission creates no history entry; never treat a socket close as a
correlated accepted/rejected result.

Close status 1013 is best-effort. During a large live burst Chrome observed 1006
(abnormal close) while server metrics confirmed ingress overflow; clients must
also reconnect/resync after an abnormal close and must not infer request outcome.

Local operator fixture replacement retires connections, releases the old engine
fixture and history, creates a fresh match ID, and requires new joins. It runs on
the communication worker via an explicitly enabled container-file mailbox, with
no HTTP/WebSocket gameplay control. The browser disables cached retries when
either match ID or actor changes. No durable restart recovery is promised.

Executable sanitized wire examples live in `test/fixtures/`; Java tests execute
negative requests and compare the complete internal state, and browser tests
decode captured M1b movement/choice/rejoin/retry messages. M1a's older snapshot
capture predates the required `state` projection added in M1b; M1b-compatible
client/server artifacts must be paired. M1c does not introduce another wire
schema change. See the separate M1c report for final acceptance status.
# M2c prepared-match contract

`preparedMatch` is a version-1 authenticated local-browser message. It is separate from the synthetic fixture and never initializes regular play. The server resolves ownership, catalog content, validation and match roles; the browser sends only saved-team identifiers and optimistic revisions.

Create: `{version:1,type:"preparedMatch",operation:"create",requestId,teamId,expectedDocumentVersion,intendedOpponent:"home"|"away"}`. Join: `{version:1,type:"preparedMatch",operation:"join",requestId,matchId,expectedRevision,teamId,expectedDocumentVersion}`. Load: `{version:1,type:"preparedMatch",operation:"load",requestId,matchId}`.

Every response has `{version:1,type:"preparedMatch",requestId,code,duplicate,callerRole,recoveryMatchId,document}`. `recoveryMatchId` is null except for `MATCH_OUTCOME_UNKNOWN`; it is the only match detail returned for an unknown commit outcome. The document includes its identifier/revision, `WAITING_FOR_OPPONENT` or `AWAITING_SETUP`, invitation policy, and frozen home/away rosters. Each member records source team/revision, ruleset/catalog, `rosterId`, `presetId`, `presetVersion`, resolved positions and integer skill parameters (including explicit zero values). Clients fail closed on unknown fields and never reconstruct a frozen roster from mutable saved-team records or a newer catalog. `callerRole` is persisted match membership, not a credential label. A match ID does not authorize a vacant seat: the server checks invitation and ownership. `ACCEPTED` is a successful mutation or load; an exact idempotent retry returns `ACCEPTED` with `duplicate:true`. On `MATCH_OUTCOME_UNKNOWN`, retain the exact request; load the recovery identifier and/or retry that exact request to reconcile.

The complete [M2c field, invitation, frozen-document and durable retry policy](prepared-match.md)
is the normative supplement for this message family. Exact retries reconcile
unknown outcomes; the browser locks new mutations and reloads authoritative data
after same-identity reconnect. M1 fixture messages/history remain separate.

## M3a prepared-match activation and setup

The separate [setup protocol](setup.md) extends the persisted prepared-match flow
with `preparedMatch/activate` and the `setup`/`setupState` message family. It covers
genuine pre-match choices, legal placement and both setup confirmations. Product
roles are persisted membership; the diagnostic credential label never selects a
match role. Activated sessions are unavailable after JVM restart, even though the
activation record and frozen teams persist. M1 fixture wire behavior is unchanged.

## M3b core turns

See [core-turn actions and decisions](core-turns.md) for the extended setup snapshot, server-issued action IDs, same-JVM recovery and explicit remaining controls.
