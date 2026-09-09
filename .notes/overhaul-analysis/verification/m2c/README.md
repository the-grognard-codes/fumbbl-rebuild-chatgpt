# M2c — durable match preparation

Implemented and validated, 2026-09-08 local / 2026-09-09 UTC. Overall M2 is not declared complete solely on this slice's results. The existing uncommitted M2a/M2b work was preserved. `initial-working-tree.txt` was captured after initial M2c edits and is not an isolated M2c diff. No commit, push, deployment, credential change, volume reset or disabled test was introduced.

## Result

Either local subject can create with an owned saved-team revision and explicitly invite the other subject. The creator receives match home membership regardless of its credential label. Only the invited identity may join as away. Reads and writes enforce membership and team ownership. The atomic versioned document moves from revision 1 `WAITING_FOR_OPPONENT` to revision 2 `AWAITING_SETUP`. No engine session, setup, kickoff or play starts.

The immutable source document read is the source-selection linearization point: subsequent edits cannot substitute different bytes. TeamValidation runs again. Frozen members retain choices, resolved statistics, skills/parameters, costs, resources, captain, source/catalog/preset versions, validation and private ownership. Persisted catalog and validation policy allow consistency checks without mutable saved teams or newer catalogs. Unknown snapshot formats are preserved and reported incompatible. The bounded engine converter explicitly maps supported skills and parameters and fails closed.

Atomic MariaDB rows contain membership, snapshots, lifecycle and retry fingerprints. Optimistic revisions select one concurrent join winner. Stable subject/request-derived match IDs and retained fingerprints reconcile exact requests after restart, including unknown COMMIT outcomes. Exact retries return the current document; changed request reuse is rejected. The browser stores pending requests before send, retains input, prevents replacement mutations during uncertainty, and reloads/retries after same-identity reconnect. Private retry metadata and ownership internals are excluded from public projections.

## Changed files

- New server package `ffb-server/src/main/java/com/fumbbl/ffb/server/match/`: MatchDocument, MatchRepository, JdbcMatchRepository, MatchService, FrozenTeam, MatchJson, FrozenTeamEngineConverter.
- Server integration: FantasyFootballServer, local/BrowserMatchAdapter, local/LocalSchema; schema-3 compatibility in team/JdbcSavedTeamRepository; migration `src/main/resources/local-schema/003-prepared-matches.sql`.
- New server tests: match/MatchServiceTest, FrozenTeamEngineConverterTest, JdbcMatchRepositoryTest and container-only MatchJdbcAcceptance; extended local/LocalSchemaTest; new state-test BrowserPreparedMatchAdapterTest.
- Browser additions: src/MatchPanel.tsx, src/prepared-match-protocol.ts, test/prepared-match-protocol.test.ts, test/prepared-match-mock-form.mjs, test/prepared-match-demo.mjs. Navigation/copy in main.tsx and TeamPanel.tsx; package.json demo script.
- Documentation: browser README/protocol and new prepared-match.md; container README/saved-team-migration and new prepared-match-migration.md; local image tag in Dockerfile/compose; latest ChangeList feature; kickoff status and this evidence directory. Other dirty files include inherited M2a/M2b work.

## Commands and actual evidence

From repository root, using the existing Java 8 host toolchain:

```powershell
./tools/test-tooling.ps1
./tools/build.ps1 test -Module ffb-statetest -Test 'MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest' -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
```

9 tooling checks passed (tooling.log). Final focused selector: 77 tests passed (java-focused-final.log). Clean install and verify passed all eight reactor projects (install.log, verify.log). Final Surefire inventory: **433 tests, zero failures/errors/skips** (test-results.csv). Local Maven Verify / Validate checks are green; hosted CI was not run because no push was requested.

From browser-client:

```powershell
npm test
npm run build
node test/prepared-match-mock-form.mjs
node test/prepared-match-demo.mjs prepare
```

20 browser tests passed (browser-tests-final.log); production build passed (browser-build-final.log). Existing approximately 509 kB chunk advisory remains. Mounted form checks passed (browser-mock-expanded.log): durable pending-before-send, unknown outcome, wrong-identity replay prevention, exact retry, authorization/stale/conflict text with retained selection, unrelated responses, retired sockets and authoritative reconnect.

Docker Desktop was absent from ordinary PATH. Container commands:

```powershell
$env:PATH="$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
$testClasses=(Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
```

After stop/start, from browser-client: `node test/prepared-match-demo.mjs check`. Final whitespace command: `git diff --check`.

Container build/up and actual MariaDB acceptance passed (container-build.log, container-up.log, jdbc-acceptance.log). Database checks exercised post-statement rollback and unchanged row counts, source isolation, reversed ownership, concurrent join/create, and real committed writes with lost acknowledgements followed by exact reconciliation. An expected duplicate-key warning contains only a synthetic match identifier. Migration 003 reached schema 3 with existing data preserved. Schema tests cover restart after table creation before version-marker advancement and refusal of mismatched schema.

Final two-browser demonstration passed (browser-prepare-final.log, server-stop-final.log, server-start-final.log, browser-restart-check-final.log). Chrome 152.0.7977.77, 1440x1080, separate sessions: subject away created as match home, subject home joined as match away. Source edits AND imports left snapshots unchanged. Fresh sessions after JVM restart loaded the exact complete pre-restart document and retried both original requests as duplicates. Match a84a1ef2-e30a-3ffd-9180-b65f884c2653 remains revision 2, awaiting setup. prepared-match-restart.json contains generated demonstration data/requests only. Summary JSON and live/restart screenshots accompany the logs; screenshots were visually checked.

Final image: ffb-server:3.4.0-m2c.1, sha256:42c6f0404040d5009529ca3db9b69e7ea018efe6c203954d11a3724d41998a8d (container-identity.txt). Healthy local server/Vite remain on loopback 22227/5173. Synthetic acceptance rows are retained; no volumes were reset.

## Failures and corrections

- Initial sandbox Java run failed during ClassGraph/FileUtils initialization (java-focused-initial.log). Authorized host run passed (java-focused-host.log), followed by final clean full builds.
- Early converter testing exposed an unsupported legacy JSON-array helper (java-focused-core.log); corrected to the existing object JSON setter. The next run exposed position parameter/category deduplication and a test purchasing Pass on an ineligible lineman (java-focused-core-corrected.log). Consistency checks were strengthened; the test now uses an eligible Catcher. Final suites pass.
- An early browser mock stalled because the connection closed before its queued reply. Scheduling was corrected; final expanded mounted-form and live tests pass. Compile-only scouting probes are not counted as test evidence.
- Independent review requested changes for concurrent join retry classification, ambiguous join commit handling, strict frozen consistency, and browser pending/reply handling. These were corrected; final review approved. Final focused/full and live checks followed the corrections.

## Overall M2 assessment and M3 boundary

| M2 acceptance criterion | Evidence and limit |
| --- | --- |
| Valid save/load/export/import round trip | M2a/M2b evidence; M2c revalidates selected saved revisions. |
| Invalid/incompatible selections rejected | M2a/M2b validation plus M2c stale, ownership, catalog/preset and frozen-consistency checks. |
| Source edits cannot change match roster | Demonstrated for durable preparation, including source import and JVM restart; not in-progress game recovery. |
| Authenticated participant cannot take opponent role | Local invitation/ownership enforcement, occupied-seat rejection and reversed creators pass. |

Combined evidence supports the bounded local M2 team/preparation scope. This does not declare the general product M2 milestone complete or solve public authentication. Existing credentials remain operator-provisioned local subjects; public accounts, issuance/revocation, production TLS and account lifecycle remain undelivered. Before M3 accepts product actions, it must enforce persisted membership on every setup/gameplay operation and audit transitions from legacy fixture routes. Fixture credential role labels must never authorize product roles. No new prepared-match operation has a known outstanding ownership gap.

Full setup, kickoff, gameplay, in-progress recovery, automatic catalog migration, production deployment and the other requested exclusions remain out of scope. Explicit restart-safe schema and rollback/reset boundaries are in containers/local/prepared-match-migration.md.
