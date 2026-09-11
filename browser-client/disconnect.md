# Match connection and recovery (M3e)

R1 adds a separately tested Java 21/Jetty 12 runtime with the same match and retry
formats. Its [compatibility policy](../containers/local/runtime-compatibility.md)
requires retaining/draining resident JVMs; it does not add unfinished-match
restart recovery. The product endpoint remains the retained local reference.

Use `/teams` to validate/save a team, `/matches` to select an owned revision and
invite/join an opponent, then `/setup?matchId=...` for setup and play. At full time,
**Open final result and replay** keeps the match ID. Content remains the
[versioned Human catalog](catalog.md); the scenario board is a separate test route.

## Browser disconnect and pending actions

The displayed match becomes read-only. Re-enter the original local credential
and join the same match to fetch its full current state and decisions. The
credential identifies a subject; the server reads its role from persisted match
membership. The creator is match home even with the credential labelled away.
No caller-supplied role can change that assignment.

Before sending a setup/play mutation, the browser saves the exact request,
subject and match ID in that tab's session storage (`ffb.setup.pending.v1`). No
credential is stored. If storage cannot be written, the action is not sent.
New mutations stay locked while its outcome is uncertain. Reconnect and choose
**Repeat last setup request** to reconcile; there is no automatic setup/play
replay. Same-tab page reload preserves a pending request. A successful load alone
does not establish which request produced a state, so it does not unlock an
uncertain action. Exact retry returns the current state without executing twice.

`PERSISTENCE_FAILED`, `MATCH_OUTCOME_UNKNOWN` and `COMPLETION_PENDING` keep this
lock: the engine may already have resolved the action or reached full time.
An accepted response or definite rejection resolves it. An unavailable resident
session discards its unusable retry. A denied load (`NOT_FOUND`) does not erase
another credential's retained action. Reconnect with its original credential
and match to reconcile. Retired sockets, unrelated requests and older snapshots
cannot resolve pending actions. A correlated response naming another match
closes the connection and preserves recovery.

After resolution, the last request remains in memory as a convenience retry
until navigation. Closing the tab, clearing site data or losing session storage
loses the record. Cross-device pending transfer is unsupported. The server can
still supply a current snapshot while its engine remains resident; do not invent
a replacement mutation from an uncertain old action.

Prepared-match create/join/activate has a separate durable retry contract: that
form automatically reconciles its exact request on same-identity reconnect.
Saved-team uncertain creates require loading the recovery document; they are not
automatically recreated. See [preparation](prepared-match.md) and
[saved-team protocol](protocol.md#m2b-saved-team-documents).

## Keyboard and status

Forms, choices, action search/selection, placement coordinates, retry and result
navigation use labelled native DOM controls. Enter activates the focused button.
The pitch has one Tab stop; arrows move square focus, Enter selects a square or
player, and Tab leaves the pitch. Home/away labels accompany color. Disconnected,
waiting-for-opponent, pending-outcome and full-time text explain the current action.
The snapshot lists score, half, drive, turns, weather, resources, ball and active
player. Rejections use alert text.

Verified in local Chrome at 1440×1080, with keyboard checks at 1280×720 and
enlarged root text. This is not certification of screen readers, browser zoom,
Edge, Firefox, Safari, mobile or every viewport.

## Browser reconnect versus server recovery

| Interruption | Supported outcome |
| --- | --- |
| Browser disconnect, same JVM | Full snapshot/prompt recovery; explicit exact setup/action retry executes at most once. |
| Same-tab reload with session storage | Pending request body/identity recover; original credential required. |
| JVM restart before activation | Saved teams, prepared membership/frozen teams and preparation retries survive. |
| JVM restart during setup/play or before terminal commit | `SESSION_UNAVAILABLE`; engine and action history cannot recover. No reinitialization or rollback to setup. |
| JVM restart after terminal commit | Private result/replay and read-only final state survive. Gameplay requests return `MATCH_COMPLETED`, not a restored in-memory duplicate acknowledgement. |

Schema 4 and server engine/replay version `ffb-3.4.0-bb2025-m3d.1` are unchanged.
M3e changes browser recovery and regression evidence; no migration or infrastructure
upgrade is needed. Pair with the existing M3d server. See
[migration/rollback boundaries](../containers/local/completed-match-migration.md).

Resident capacity remains 32 engine lifetimes, including completed/failed sessions;
completed engines are not automatically evicted. Action/history/replay bounds fail
closed. Restart frees capacity but loses unfinished engines; it is not a player
recovery procedure. Public accounts, credential lifecycle, TLS, runtime/Jetty
maintenance, process recovery, backup restore, retention and measured load remain
M4 work. Local acceptance does not imply public-service readiness.

See [M2/M3 acceptance and exact evidence](../.notes/overhaul-analysis/verification/m3e/README.md)
and the [M4 handoff](../.notes/overhaul-analysis/verification/m3e/m4-handoff.md).

## M4 R2 opt-in runtime boundary

The table above records the accepted M3e runtime. New matches created by the
isolated R2 runtime persist a private format-2 engine checkpoint before acknowledging
actions. After a process kill, authorized clients recover the original decision,
actor, resources and revision; exact accepted-request retries retain their original
outcome without another engine execution. Pending terminal results reconcile after
restart. Unsupported or corrupt checkpoints fail closed and remain stored.

Existing Java 8, R1 and trial format-1 lifetimes are not upgraded. An activated
match without a compatible checkpoint remains unavailable instead of restarting
at setup. See the [compatibility policy](../containers/local/recovery.md) and
[seven real process-kill checks and rejection evidence](../.notes/overhaul-analysis/verification/r2/README.md).
This is local process recovery, not a capacity, public authentication or deployment gate.
