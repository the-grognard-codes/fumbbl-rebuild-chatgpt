# M3c supported action and skill coverage — 2026-09-10

M3c is implemented for the existing frozen Human catalog/preset. The
[coverage matrix](../../../../browser-client/action-coverage.md) inventories every
supported action/decision family and all 14 catalog skills/traits, linking native
engine characterization, adapter tests and browser evidence. Review found no
remaining concrete uncovered reachable action/prompt family in that scope.
No rules engine, catalog, infrastructure or public-service expansion was made.

## Changes and authorization

BallAndFoulActions projects pass, hand-off, foul, secure ball and throw team-mate
declarations/targets using native mechanics. CoreTurnActions adds their native
step paths plus jump and forgo. CorePromptActions adds interception and argue,
and recognizes native BB2025 Pro block rerolls. Server-issued options retain
persisted membership, revision validation and exact retry behavior. Team data
comes from the frozen activated roster. SetupPanel searches large action lists,
resets selection/search at a new revision and marks the ball on the grid.

The starter catalog and preset are unchanged; six positions and all 14
skills/traits remain available. Hand-off targets are restricted to teammates
after checking the authoritative BB2025 reference. No engine rules were altered.
The protocol, browser guides, migration boundary, kickoff and latest user-facing
ChangeList were updated. Dockerfile/compose changes only identify the M3c build.

The initial working tree was clean. Accepted ADRs, PRD, roadmap, kickoff, M2a
historical and closeout evidence, M2b/M2c closeouts, M3b evidence, browser protocol,
catalog and saved-team/prepared-match/setup migration boundaries were consulted.
Existing database records, volumes and credentials were retained. No commit,
push, public deployment, volume reset or credential alteration was performed.

## Final checks

| Check | Result / evidence |
| --- | --- |
| Tooling | 9 passed; tooling.log |
| Relevant Java selector | 117 passed; focused-complete.log (61 server + 56 statetest, including 19 M3c) |
| Clean offline install | All eight reactor projects passed; install.log, Java 1.8.0_504 / Maven 3.9.9, finished 2026-09-10 00:05:28 -04:00 |
| Clean offline verify | 473 tests, zero failures/errors/skips; verify.log, test-suites.csv, finished 00:09:22 -04:00 |
| Browser unit/protocol tests | 30 passed; browser-tests.log |
| Browser production build | tsc and Vite passed, exit 0; browser-build-final.log. Existing 522.93 kB main chunk produces the >500 kB advisory |
| Mounted UI in two isolated Chrome contexts | 88 native traces passed, including search/reset, wrong-side disabled controls, reconnect before choices, lost acknowledgement/exact retry and peer synchronization; mounted-final.log and mounted-action-summary.json |
| Actual local two-browser play | Passed; live-second.log and live/core-turn-summary.json; no page errors |
| Independent review | APPROVE after opponent hand-off correction; review.md |
| Whitespace / evidence secrets | whitespace.log and evidence-check.log |

Commands from repository root (host process access was authorized for Maven's
ClassGraph, Docker and Chrome):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-tooling.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test BallAndFoulActionsTest,CoreTurnActionsTest,SetupSessionTest,MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 install -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 verify -Offline
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait server
git diff --check
```

From `browser-client`, with the existing Vite app listening at 127.0.0.1:5173:

```powershell
npm.cmd test
npm.cmd run build
node test/supported-actions-demo.mjs
node test/supported-play-demo.mjs
```

The final running container's `.Image` was checked against the tag:
`ffb-server:3.4.0-m3c.1`,
`sha256:dee5499bf537f09f540b338328517079a486e99b0bfa08e0953fbc0da7a0f6da`.
The server and existing MariaDB are healthy; image-id.txt and compose-status.json
record their identity/status. The live demonstration uses this final image.

## Browser evidence boundaries

The mounted script uses the real SetupPanel and strict decoder with test-only
WebSocket responses captured from the real SetupSession/native engine in B.
It executes 88 representative action traces and verifies every offered action is
visible to its owner. The Java helper rejects wrong-role/stale requests without
engine mutation, restores snapshots, and checks exact retry does not consume
additional dice. Mounted lost-acknowledgement behavior simulates transport loss;
it is not proof of live service packet loss. The actual service's retry and pending
decision recovery are separately exercised by L.

Fixture generation: running BallAndFoulActionsTest writes
`ffb-statetest/target/m3c-actions.json`. The checked-in
`browser-client/test/fixtures/supported-actions-v1.json` was copied from the
successful clean install run. IDs are synthetic random test IDs; subsequent
generations need not be byte-identical. Deterministic dice and reflection-based
state injection are confined to statetest, with no production fixture route.

Live match `7970e94e-238f-372f-8645-2d7e967927f7` reached revision 73 in Chrome
152.0.7977.83. The credential named away created persisted home membership and
the credential named home joined as away. Both rosters contain the six positions,
captain Pro, apothecary and team rerolls. Native kickoff, block-die pending reconnect,
wrong-role/stale rejection, exact retry and four actual moves over four turns pass.
The same run submits hand-off, pass and throw team-mate actions and resolves their
native continuations. Random dice do not guarantee successful throws/catches;
successful and failed outcomes are characterized in Java. Foul, interception and
rare injury/skill branches have native Java plus mounted browser evidence, not a
claim that all occurred in this live match. Sanitized per-participant wire traces
and final screenshots are under live/. Mounted screenshots cover pass,
interception and apothecary continuations. Interception and live screenshots were
visually inspected: board, ball, roles, action controls and frozen player table
rendered legibly with no visible clipping at the tested desktop viewport.

## Initial failures and corrections

- Initial compilation used the wrong Keyword package; corrected to model.Keyword.
- Sandbox ClassGraph initialization failed; authorized host execution worked.
- Characterization fixtures initially omitted Weather.NICE, expected Catch to
  prompt although native Catch rerolls automatically, expected Bone Head at
  selection instead of first movement, and used the wrong casualty dice queue.
  Fixtures/assertions were corrected to native behavior; no product dice change.
- The initial live script could not lift a teammate after earlier movement had
  separated the Ogre and Halfling. It now moves the Ogre toward the eligible
  teammate before lifting. live-initial.log is a failed attempt; live-second.log
  and the final summary supersede it. Created records were retained.
- Initial PowerShell output redirection reported Vite's advisory as a
  NativeCommandError despite the successful build. A direct rerun and the final
  capture explicitly confirmed npm exit 0. The bundle-size advisory remains.
- Independent review found the desktop hand-over helper allowed opponents.
  The browser now projects only eligible teammates; both orientations reject an
  adjacent opponent in the regression. Follow-up review approved.

Intermediate logs are retained as failure history, not counted as acceptance.

## Remaining work and limits

No uncovered action/prompt family remains in the declared starter scope; this is
not exhaustive enumeration of every board arrangement or all dice combinations.
M3d still owns scoring/drive/half/completion UX, full-match acceptance, results,
replay and cleanup. Same-JVM reconnect is covered; durable in-progress restart
recovery remains unsupported. Restart retires resident sessions while preserving
prepared documents/frozen rosters. No new restart claim or migration is made.
Public accounts/TLS and all noncatalog content remain outside this local scope.
