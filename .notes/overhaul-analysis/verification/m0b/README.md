# M0b verification — 2026-09-07

**Passed: independent JVM startup and fixture-match acceptance, normal stop/start,
and explicit disposable reset.** This is application evidence, separate from M0a
build success and the earlier MariaDB tooling smoke test.

## Acceptance record

[acceptance.log](acceptance.log) records the final image runs:

| Check | Actual result |
|---|---|
| Normal host access | HTTP 200 on `127.0.0.1:22227/admin/cache`; Docker lists exactly `127.0.0.1:22227->22227/tcp` |
| Separate database | MariaDB 11.8.9, Connector/J 3.5.8; no database port published; schema version 1 |
| Real match acceptance | Normal profile passed match 3; two synthetic coaches joined over real WebSockets and each received a BB2025 game state with two 11-player teams; authenticated snapshot retrieval loaded it from JDBC |
| No required live calls | Same image passed match 4 using `compose.offline.yaml`; inspected server had only the internal network, an empty gateway and no published ports; `internal=true` |
| Stop/start persistence | Stopped both services, restarted both, and `check 4` passed; SIGTERM logged `database queue drained`, exit 143, `OOMKilled=false` |
| Explicit reset | Before reset 4 game rows; `down --volumes` removed only this Compose project's database/backup volumes; fresh startup had 0 games, schema version 1 and 2 coaches |
| Old data absent | `check 4` after reset returned expected HTTP 404 and exit 1 |
| Fresh fixture after reset | `create` passed as game 1 |
| End state | Both services stopped; MariaDB exit 0, JVM SIGTERM exit 143 with queue drained; new game 1, volumes, image cache and ignored secret files retained |

The client schedules through existing authenticated `/admin/schedule`, polls
`/gamestate/get?fromDb=true`, sends actual `ClientCommandJoin` messages for
FixtureHome/FixtureAway, and validates both WebSocket game states and a persisted
snapshot. It does not bypass the running server with a state-test harness.

The normal profile needs a publishing bridge because this Docker Desktop engine
does not publish from an internal-only network. It has loopback-only inbound
exposure but permits outbound routing. The offline override removes that bridge,
proving no live FUMBBL credentials or required requests are needed. These are two
explicitly documented network configurations of the same artifact, not a claim
that a Docker internal network publishes host ports.

## Build and tests

| Command/check | Result |
|---|---|
| Docker multi-stage build | Passed; Maven 3.9.9, Java 8 Linux stages, common 174 tests + server 87 tests, zero failures/errors/skips; [log](image-build.log) |
| `./tools/build.ps1 install -Offline` | All eight reactor projects passed; 358 reported, 357 passed, one existing disabled test, zero failures/errors; 1m38s; [log](clean-install.log) |
| `./tools/build.ps1 verify -Offline` | Same passing totals; 1m34s; [log](clean-verify.log), [per-suite results](test-results.csv) |
| Focused changes | Local lifecycle/options, local backup fallback, schema preservation/rejection, database close, and FIFO/in-flight shutdown tests passed before full verification |
| Independent review | Initial producer/drain race and failure cleanup findings repaired; final focused review approved the local acceptance path |
| Diff/config checks | `git diff --check` passed; Compose parsed and ran both configurations; setup script ran and preserves existing secrets |

Affected-test manifest (10 new tests): `LocalGameLifecycleTest` (2), `UtilBackupTest`
(1), `LocalSchemaTest` (2), `LocalDatabaseCloseTest` (1), `DbUpdaterShutdownTest` (1),
`ServerRequestProcessorShutdownTest` (2), `ServerReplayerShutdownTest` (1).

Server image: `ffb-server:3.4.0-m0b.1`, Linux amd64.
Verified local image ID:
`sha256:66edaea871f43070fe294e568f3adf6664e8d6e122a57a32da3c84cbbf73ac95`.
Maven and JDK/JRE/MariaDB digests are in the Dockerfile/Compose file.
Container Java is Temurin `1.8.0_502-b07`; host reference remains the M0a
`1.8.0_504-b01`. Exact host-patch container tags were unavailable. No Java 21
compatibility or byte-identical rebuild claim is made.

## Failures classified and resolved

- Docker access was unavailable inside the sandbox; authorized host execution
  succeeded. The required first host install also hit ClassGraph `AccessDeniedException`
  on the state-test module directory. The same offline install passed outside the
  sandbox. [Initial failure](clean-install-sandbox-failure.log).
- First real JVM startup required `server.log.file` as well as the log folder.
- First headless client ignored binary UTF-8 frames; the server had accepted the
  joins, but client acceptance correctly failed until framing was fixed.
- A build captured an in-progress worker import edit; fixed. A replay test initially
  mocked `isComplete()` as permanently false; fixed the test double and synchronization.
- Initial stop tried legacy standalone SQL `SHUTDOWN`; local JDBC close now omits it.
- Configured loopback binding on an internal-only network did not publish a port;
  final normal/offline profiles and host HTTP check resolve that mismatch.
  [Initial lifecycle failure evidence](lifecycle.log).

## Limits and next demo

No remaining blocker for this M0b acceptance scope. The fixtures are synthetic
startup data, not an authoritative legal roster catalog. The demo reaches joined
game states; it does not prove a finished match, pending-choice recovery, replay
cleanup during shutdown, browser movement, hardened public authentication, Java 21
or cloud deployment. A late replay-delete callback to a closed communication queue
is logged/rejected and leaves its database copy; that lifecycle extension remains
outside this demo. Existing legacy development/admin protocol privileges remain.

Next: M1a's narrow browser adapter and two-browser server-validated movement demo,
using this local stack and neutral tokens. No browser feature or production artwork
was implemented in this task, and no commit, push or deployment was performed.
Concurrent unrelated art-preview changes observed at closeout were preserved.

See [run/stop/reset commands](../../../../containers/local/README.md) and
[startup trace](../../11-independent-startup.md).
