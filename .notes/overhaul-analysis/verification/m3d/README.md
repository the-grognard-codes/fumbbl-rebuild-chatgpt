# M3d completion, results and replay — 2026-09-10

Implemented and verified for the existing frozen BB2025 Human exhibition preset.
The final two-browser match finished **home 1–0 away**, revision **149**, with
**150 recorded events**. Both participants retrieved the same result and used
the replay viewer. After restarting only the JVM, both participants retrieved
the same result and all 150 recorded states matched the original live states
exactly. In-progress restart recovery remains outside this completed slice.

## Scope and implementation

The working tree was clean at entry. Accepted ADRs, PRD, roadmap, implementation
kickoff, M2a–M2c closeouts, M3c evidence, browser protocol and existing migration
guides were consulted. Java 8/Maven, MariaDB/JDBC, React/Vite and the authoritative
BB2025 rules engine remain. Frozen teams supply all match roster data; persisted
membership authorizes setup, gameplay, result and every indexed replay read.

Native engine scoring, drive setup/kickoff, halftime and full time run unchanged.
The setup projection now shows both scores, half, drive and both turn counters.
The preset explicitly retains no overtime. The generic final-step persistence
guard routes application-owned matches away from legacy replay-save requests;
it does not change any ruleset's scoring or settlement rules.

The server records bounded post-resolution state events, not client commands or
an independent rules implementation. Result and replay are one immutable artifact
stored with frozen membership/teams in the existing match row via a revision-3
to revision-4 CAS. Commit precedes a successful terminal acknowledgement. Exact
retries never rerun the engine or append events. If the original save failed or
its acknowledgement was lost, the first successful reconciliation broadcasts
full time to the peer once. New mutations after completion are rejected.

Schema 4 widens the match JSON column and its bound. One atomic ALTER is followed
by exact table verification and marker advancement; restart can resume after
ALTER. Existing rows, teams, database/backup volumes and credentials were kept.
Completed documents use format 2; older preparation documents retain format 1.
Replay format 1 pins `ffb-3.4.0-bb2025-m3d.1` and the existing rules/catalog/preset.
Unknown formats fail explicitly without rewriting stored content. No historical
replay import, public replay listing, spectator access or new catalog was added.

See [wire and lifecycle contract](../../../../browser-client/results-replay.md)
and [migration/rollback boundaries](../../../../containers/local/completed-match-migration.md).

## Verification

| Check | Result and evidence |
| --- | --- |
| Tooling checks | 9 passed; `tooling.log` |
| Relevant Java selector | 127 passed (67 server + 60 statetest); `java-focused-final.log` |
| Clean offline install | All eight reactor projects passed, 2m49s; `install.log` |
| Clean offline verify | All eight projects passed, 2m26s; **483 tests, zero failures/errors/skips**; `verify.log`, `test-suites.csv` |
| Browser tests | **34 passed**, zero failures/skips; `browser-tests-final.log` |
| Browser production build | TypeScript and Vite passed; `browser-build-final.log`; existing >500 kB main-chunk advisory remains (532.90 kB) |
| Final container build/start | Passed, healthy server/database; `container-build-final.log`, `container-up-final.log` |
| Actual MariaDB fault/concurrency checks | Completion rollback, concurrent CAS, lost acknowledgement and exact reconciliation passed; `jdbc-acceptance.log` |
| Live two-browser match | Passed with native random dice and DOM team selection/setup/actions; `live-complete.log`, `live/completed-match.json`, `live/gameplay-0.json`, `live/gameplay-1.json` |
| Mounted result UI | Two isolated browser contexts; revision-zero replay, pending navigation, uncorrelated delayed reply, explicit unsupported response and identity reconnect; `results-mounted-corrected.log` |
| JVM restart result/replay | Both participants passed; all 150 states exactly equal live projections; completed setup reads work and new mutations return MATCH_COMPLETED; `result-restart.log`, `restart/summary.json` |
| Visual inspection | Live result and restarted replay screenshots inspected; score, navigation, pitch and player tokens are legible; `live/result-0.png`, `restart/replay-0.png` |
| Whitespace | `git diff --check` passed; `whitespace.log` |
| Evidence secrets scan | No current local browser credentials found in evidence; `evidence-check.txt` |

Final match: `11075be4-67c8-3c67-8195-6e4459b63058`.
The creator used local subject `away` but was persisted as match home; the other
subject was match away. Saved teams were selected through the DOM from existing
owned documents. Touchdown at revision 74 started drive 2; halftime at revision
107 started drive 3; full time at revision 149 had both turn counters at 8.
The driver retried transition requests and checked unchanged revision. Browser
version was Chrome **152.0.7977.83**, two separate contexts, 1440×1080 viewport.
The synthetic driver does not submit a seed, dice or fixture control to the server.

Image `ffb-server:3.4.0-m3d.1` and running container both resolve to
`sha256:89d626ff9e294d719a1f775eca51d211709be598d82040bd5643a92460984f70`;
`image-id.txt`, `running-image-id.txt`, `compose-status.json`. The healthy local
server and Vite remain on loopback 22227/5173; the database has no published port.
The final restart preserves all completed results and retires resident engines.

## Exact commands

From repository root, Windows PowerShell, using the unchanged workspace toolchain:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-tooling.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test 'BallAndFoulActionsTest,CoreTurnActionsTest,SetupSessionTest,MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 install -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 verify -Offline
$env:PATH="$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait server
$testClasses=(Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
git diff --check
```

From `browser-client`:

```powershell
npm.cmd run dev
npm.cmd test
npm.cmd run build
node test/full-match-demo.mjs
node test/results-mock.mjs
# After restarting only the JVM:
node test/result-restart-demo.mjs
```

The final restart driver consumes the saved live evidence and compares every
server replay projection, not merely a hash, score or event count. Result route
requests are read-only. Fixture traces exclude authentication messages and tokens.

## Failures, corrections and review

- PowerShell initially refused direct script execution. The documented process-only
  `-ExecutionPolicy Bypass` invocation was used; no machine policy changed.
- Initial Java tests failed because sandboxed ClassGraph could not read the working
  directory (`java-initial.log`). Host execution resolved this. A generated target
  copy of migration 004 was read-only (`java-corrected.log`); clearing that generated
  file's read-only attribute allowed the build, without changing source permissions.
- The first native touchdown test caught drive counting during pre-match half
  initialization (`java-host.log`). Projection now treats the native 0→1 sentinel
  transition as initialization and uses the engine's one-based half number.
- The initial assumption that the existing match column could store replay was
  wrong: schema 003 enforces 64 KiB. Migration 004 was added and demonstrated.
- The first persistence/broadcast regression fixture retained a pre-match prompt
  in its synthetic terminal event (`java-retry.log`); redacting it like the actual
  recorder corrected the fixture. `java-retry-corrected.log` passes both rollback
  and unknown-acknowledgement branches, one persisted record and one peer update.
- Independent review found revision-zero/max-event browser decoding defects,
  insufficient reserve accounting at the replay byte limit, and a missed peer
  broadcast after terminal persistence recovery. All were corrected. Boundary,
  pre-mutation rejection and broadcast retry regressions pass. A separate final
  independent approval was not obtained; final integration and validation were
  performed by the root agent.
- Initial live driver attempts failed because Vite was not running
  (`live-initial.log`), because its action-selection loop repeatedly selected a
  player without a useful move (`live-second.log`, `driver-loop-wire.json`), and
  because it queried result messages from the setup route (`live-final.log`).
  The corrected driver limits selection per turn, routes around occupied squares,
  and switches to `/results` before result/replay reads. `live-complete.log` passes.
  The earlier full-time match from the route-check attempt and all other created
  rows remain in the database; nothing was reset to hide an unsuccessful run.
- The mounted driver initially passed a function through Playwright's serialized
  argument boundary (`results-mounted.log`). It now passes snapshot data and
  supplies a fresh credential for reconnect; the corrected mounted run passes.
- An evidence-summary script initially assumed UTF-16 for a UTF-8 log; the final
  counts were read from XML reports and the exact module summaries instead.

## Limits and remaining work

This certifies the supported preset's local full-match loop, not every possible
skill interaction or public deployment. Replay is a sequence of recorded public
states; it does not animate every native report or rerun dice. It excludes private
dialogs, credentials, raw engine data and historical replay import.

In-progress engine state/dedupe remain resident. If the JVM is lost before a
terminal artifact commits, the match cannot reconstruct that engine or finish
persisting it. Completed artifacts survive restart; this is not backup-restore
certification. Resident-session cleanup, durable in-progress recovery, public
accounts/TLS, spectators, mobile/cross-browser certification, broad catalogs and
load/heap-capacity benchmarking remain future work. Replay/history admission
limits fail closed and do not promise arbitrarily long matches.

No commit, push, deployment, volume reset, credential alteration, infrastructure
upgrade or new public service occurred.
