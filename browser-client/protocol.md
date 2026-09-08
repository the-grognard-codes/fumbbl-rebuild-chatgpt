# Browser protocol v1 — movement, choices and M1c transport bounds

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
