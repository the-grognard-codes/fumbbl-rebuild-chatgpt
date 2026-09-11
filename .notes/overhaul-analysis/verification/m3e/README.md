# M3e integrated acceptance and disconnect experience

Implemented and verified 2026-09-10 local / 2026-09-11 UTC, starting at
`e0fbdae73c01a3d0164ceccd7209d506f0fd0f46` with a clean working tree.
Accepted ADRs, product requirements, roadmap/kickoff, M2a–M2c closeouts, M3a–M3d
evidence, browser protocols and applicable schema/migration guides were consulted.

**M2 and M3 acceptance is satisfied for the declared local BB2025 Human exhibition
scope in desktop Chrome. M4/public-service readiness is not complete.** This is
not a claim of general BB2025 content, all browsers or durable unfinished-match
recovery. The acceptance rows below define exactly what is supported and proved.

## Changes

Setup/play now stores an uncertain exact request before send in tab session storage,
without credentials. It survives page reload and requires its original subject/match.
Storage failure prevents send; unknown completion/storage responses keep new actions
locked until exact reconciliation. A denied load does not discard another subject's
pending request. Foreign-match correlated replies fail closed without erasing recovery.

Connection, opponent, pending, error and full-time text is explicit. Setup/result
links preserve match ID; preparation has a Disconnect button. The pitch has one
Tab stop, arrow-key navigation and visible focus; actions remain native DOM controls.
No wire/schema/catalog/engine behavior changed. Java changes are a focused regression
and the user-facing ChangeList entry. See [product contract](../../../../browser-client/disconnect.md).

## M2 acceptance assessment

| Roadmap criterion | Evidence | Assessment |
| --- | --- | --- |
| Valid teams round-trip | Current M2a validation, M2b save/load/export/import/recomputed costs and post-JVM complete-document equality; prior M2a/M2b closeouts | Passed locally. |
| Invalid imports fail clearly | Live stale import/invalid-save invariance, schema checks, server validation suites and mounted conflict/unavailable-catalog forms | Passed; unknown catalogs remain preserved/locked, never silently migrated. |
| Editing a source cannot alter an active match | Integrated games update and import their own synthetic sources after activation; complete frozen prepared documents stay equal. New Java test removes both sources before activation and checks native team IDs and all setup/kickoff operations. | Passed for frozen active engines and completed records. |
| Authenticated participant cannot take opponent role | Both creator directions, invitation/ownership tests, reversed-role choice/place/confirm/action tests, live wrong-owner rejection, unauthenticated mutation/read probes and forged-role rejection | Passed for operator-provisioned local subjects. Public account/credential lifecycle remains M4. |

This supersedes historical M2a/M2b/M2c statements that later integration was still
pending. It does not convert local bearer subjects into public product accounts.

## M3 acceptance assessment

| Roadmap criterion | Evidence | Assessment |
| --- | --- | --- |
| Supported preset completes end-to-end | Two independent Chrome contexts per match; actual saved-team selection/create/join/activation, setup, native kickoff/play, touchdown, later drive, halftime, full time, private result and replay. Both creator directions passed. | Passed for declared preset, native random dice. |
| Engine/adapter/browser capability agreement | 128 focused Java tests; complete action/skill matrix; 88 native-wire before/request/after frames mounted in two contexts; live six-position hand-off/pass/throw team-mate and block prompt recovery | Passed for documented families, not every possible board/dice combination. |
| Clear disconnect and pending decision recovery | 151 lost-reply/reconnect/exact-retry cycles across the two initial completed matches, including coin/receive, placement, setup confirmation, gameplay and terminal action. Wrong-owner/stale actions rejected; both full states compared. Mounted reload/unknown-outcome/wrong-identity/retired-socket/foreign-match tests. | Passed for same-JVM reconnect and same-tab pending recovery. |
| Drive/half/end and durable result/history | Matches below, native transition events, exact retries without extra peer broadcasts; all 273 recorded states compared after one JVM restart; both members can read final result/replay and cannot mutate completed matches. | Passed for committed completed artifacts. |
| Disabled/disputed cases resolved or excluded | [Capability matrix](../../../../browser-client/action-coverage.md) enumerates all supported action/skill families and explicit noncatalog exclusions. Full Java reactor reports no skipped tests. | Passed within catalog; no rule rewrite or catalog expansion. |

The two initial integrated completed matches:

| Creator subject → persisted role | Match | Result | Revision / events | Lost-reply recovery cycles |
| --- | --- | --- | --- | --- |
| away → home | `a431f11b-3c7d-30cd-ade4-d361c982bd9b` | home 0–1 away | 138 / 139 | 78: 2 choices, 6 placements, 6 confirmations, 64 actions |
| home → home | `6fb53808-0e86-3f57-8749-4dea3b7a3c65` | home 0–1 away | 133 / 134 | 73: 2 choices, 6 placements, 6 confirmations, 59 actions |

After the final foreign-response review corrections, a fresh complete integrated
run also passed: `fbc14ead-1c82-318f-a9c2-697b51720b42`, creator subject away,
home 0–1 away at revision 140, 141 events and 80 lost-reply recovery cycles.
`live-reviewed.log` and `live-reviewed/` are final-source full-match evidence.
Across all three successful complete runs there were 231 recovery cycles.
The two earlier committed artifacts supplied the 273-event process-restart proof;
the final match was completed after that restart and remains available.

The driver deliberately withholds action replies/snapshots from the acting UI,
reconnects it, explicitly retries the original request and asserts the observer
gets no second mutation broadcast. Each tested stale probe uses a new request ID.
Every authoritative state comparison differs only in callerRole. These are local
fault-injection checks, not simulated server-process recovery or a latency benchmark.

Live six-position match `e74b7522-1763-3a79-9d3f-b5999382522f` reached revision 66:
four safe moves/four turns, native block-die reconnect/retry and hand-off, pass,
throw team-mate submissions. Deterministic dice exist only in the native unit/wire
fixtures; all live games used the engine's random rolls. Test fixtures are absent
from product requests.

## Verification and exact evidence

| Check | Actual result / local artifact |
| --- | --- |
| Tooling | 9 passed, `tooling.log` |
| New narrow Java suite | 5 passed, `java-narrow-host.log`; initial sandbox ClassGraph failure retained in `java-narrow.log` |
| Required focused Java selector | 128 passed (67 server + 61 statetest), `java-focused.log` |
| Offline clean install | All eight reactor projects passed, 4m02s, `install.log` |
| Offline clean verify | All eight passed, 3m55s; 484 tests, 0 failures/errors/skips, `verify.log`, `test-suites.csv` |
| Browser unit/runtime tests and build | 36 passed; TypeScript/Vite passed, `browser-tests-final.log`, `browser-build-final.log`. Existing main-chunk >500 kB advisory retained. |
| Schemas | Draft valid + seven invalid cases; saved-document valid/invalid checks, `team-schema-host.log`, `saved-schema-host.log` |
| Final mounted capability matrix | All 88 frames passed after review fixes, `matrix-final.log`, `matrix-final/mounted-action-summary.json` |
| Mounted setup recovery | Unknown outcomes, keyboard/pitch, page reload, identity mismatch, denied load, retired socket and foreign-match response; `recovery-reviewed.log`, `recovery/summary.json` |
| Mounted results/preparation/saved teams | Passed correlation, pending, error, identity and reconnect checks; `results-reviewed.log`, `preparation-mounted.log`, `saved-mounted.log` |
| Real MariaDB fault checks | Team and match rollback/CAS/ambiguous COMMIT/reconciliation passed, `jdbc-team.log`, `jdbc-match.log` |
| Current M2 and setup live preparation | `m2a-live.log`, `m2b-prepare.log`, `m2c-prepare.log`, `setup-prepare.log` and corresponding directories |
| Complete matches | `live-reversed-final.log`, `live-home.log`; each directory contains gameplay traces, transition/result screenshots and `completed-match.json` |
| Six-position live actions | `supported-live-final.log`, `supported-live-final/core-turn-summary.json` and wire/screenshots |
| Live authorization negatives | `authorization.log`, `authorization-summary.json`: seven unauthenticated operation families denied; both subjects reject role/dice/fixture fields and unknown match with unchanged state |
| JVM restart | `server-stop.log`, `server-start.log`: server only; database/backup volumes and credentials retained |
| Completed replay equality | `result-restart.log`, `restart-reversed/summary.json`: all 139 states equal; `result-home-restart.log`, `restart-home/summary.json`: all 134 equal |
| Saved/prepared restart | `m2b-restart.log`, `m2c-restart.log`: complete documents and durable preparation retries unchanged |
| Unfinished restart boundary | `setup-restart.log`, `setup/restart-summary.json`: both participants receive SESSION_UNAVAILABLE; activation retry cannot initialize again |
| Final reviewed-source full match | `live-reviewed.log`, `live-reviewed/completed-match.json`: revision 140, 141 events, 80 recovery cycles |
| Whitespace / credential scan | `whitespace.log`: git diff --check passed; `evidence-check.txt`: no current browser credentials found in captured evidence |

Browser: Chrome 152.0.7977.83, separate contexts, 1440×1080; keyboard mounted check
at 1280×720 with enlarged root text (not actual 200% browser zoom). Result and
keyboard screenshots were visually inspected. Logs are retained under the existing
ignored `*.log` policy; summaries and scripts remain directly reviewable.

The existing server image `ffb-server:3.4.0-m3d.1`, digest
`sha256:89d626ff9e294d719a1f775eca51d211709be598d82040bd5643a92460984f70`,
is unchanged: no production server code changed in M3e, so no container rebuild
or migration was needed. Schema 4, engine/replay version, Java 8/Maven,
MariaDB/JDBC and React/Vite dependencies remain pinned. `running-image-id.txt` and
`compose-status.json` record identity/healthy loopback services.

Final local JVM/database remain healthy and Vite remains on loopback 5173; JVM
port is loopback 22227 and MariaDB has no published port. Demo browser contexts
are closed. The final post-restart completed engine occupies one resident lifetime;
earlier unfinished activation records remain durable but unavailable, as tested.

Exact command entry points and the affected-test manifest are in [commands](commands.md).

## Failures and corrections

- Sandboxed Docker/PowerShell 7 lookup and Java ClassGraph could not access host
  tools/workspace. Authorized host execution passed; no global installation,
  machine policy or credentials changed. Process-only ExecutionPolicy Bypass was
  used for the existing build scripts.
- The first integrated driver counted peer broadcasts before delivery (`live.log`).
  It now waits for the peer's rendered revision before counting. The next attempt
  tried to duplicate its deliberately stale probe at a transition (`live-corrected.log`);
  integrated transition checks now use their already-verified original retries.
- Editing the UI during a development-server demonstration triggered a Vite reload
  and lost driver instrumentation (`live-reversed.log`). The UI was frozen and
  the complete runs repeated successfully. Earlier test rows were retained.
- The six-position driver's guaranteed-movement assertion selected its Ogre and
  legitimately stopped for Bone Head (`supported-live.log`, `supported-live/failure.json`).
  Its guaranteed movement segment now selects a non-Ogre from its known draft.
  Ogre/trait behavior remains covered by native matrix tests and live throw team-mate;
  no engine behavior was changed to satisfy the driver.
- The old result mock used ambiguous substring `Final score` (`results-mounted.log`).
  Its exact heading locator passed. Final review then found foreign-match correlated
  responses could clear setup recovery or result pending status. Both fail closed
  now; mounted regressions, final browser tests/build and full matrix passed.
- One evidence-summary probe assumed UTF-16 for a UTF-8 image-ID file; the file was
  read correctly afterward. Test counts came from XML, not that failed probe.

Final independent review approved the persisted-membership/frozen-team paths,
new Java regression, and the targeted foreign-response corrections with no remaining
actionable finding. Implementation, final acceptance mapping and evidence review
were completed by the root agent; no review approval substitutes for runtime checks.

## Limits and remaining work

Same-JVM browser reconnect is supported. In-progress JVM recovery is not: an engine
lost during setup, a decision, transition or before terminal commit cannot resume.
Committed result durability is not backup restoration. Closing a tab or clearing
storage can lose the pending browser request. No automatic cross-device recovery,
concession, overtime, spectator mode, historical replay import or broad catalog
is introduced. Replay shows recorded public states, not every intermediate report.

There are only two local bearer subjects, no public account lifecycle/TLS policy,
and resident capacity remains 32 lifetimes including completed/failed sessions.
Bounded history/replay limits can stop unusually long matches; completion cleanup,
abandonment/retention, load/heap benchmarks and operational support remain undone.
Actual multi-browser/zoom/mobile/screen-reader certification, maintained runtime/
Jetty and asset provenance review are not claimed. Follow the [M4 handoff](m4-handoff.md).

No commit, push, deployment, volume reset, credential alteration, infrastructure
upgrade, rules rewrite or new public service occurred.
