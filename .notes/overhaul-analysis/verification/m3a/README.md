# M3a closeout ? 2026-09-09

M3a is implemented and validated. Prepared matches activate once into the existing Java 8 BB2025 engine using frozen teams. Browser controls complete coin/receive choices, player placement, reserves and native legal setup confirmation. Both teams reached READY_FOR_KICKOFF, revision 26. Kickoff execution is the next slice.

The user-authorized fast-forward advanced feature/m3 from 162f8c7c9 to local main d53059164 before implementation. The initial tree was clean. No commit, push, deployment, volume reset or credential change occurred.

## Implementation and contract

Activation is a durable revision-3 compare-and-swap transition. Exact retries return the current document. A bounded same-JVM reservation reconciles a lost COMMIT acknowledgement without initializing twice. Initialization is reserved before invoking the engine; failure leaves ACTIVATED unavailable. A new JVM never initializes an already activated document.

Every setup load, choice, placement, confirmation, retry and peer projection rechecks persisted membership. Fixture credential labels do not establish product roles. Frozen rosters alone populate the engine, including the captain trait required by native setup validation. Existing rules and random dice remain authoritative; synthetic fixtures are not product inputs. The product engine does not enter legacy game persistence.

The browser retains an exact pending request within the page for explicit retry, reconciles snapshots after reconnect, checks subject/match identity, and gates controls by the persisted role and current actor. Native setup validation rejects illegal formations without advancing the revision. The existing M2 preparation form behavior is preserved.

See [setup protocol](../../../../browser-client/setup.md), [migration and restart behavior](../../../../containers/local/setup-activation-migration.md), [review](review.md), and [changed files](changed-files.txt).

## Verification

Commands executed from the repository root unless noted (PowerShell; configured Temurin 1.8.0_504 and Maven 3.9.9):

```powershell
./tools/test-tooling.ps1
./tools/build.ps1 test -Module ffb-statetest -Test 'MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest,SetupSessionTest' -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
# browser-client working directory
npm.cmd test
npm.cmd run build
node test/prepared-match-mock-form.mjs
node test/setup-demo.mjs prepare
# root working directory; Docker Desktop bin added to PATH
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
$testClasses = (Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
# browser-client: check after restart, then prepare a fresh live match
node test/setup-demo.mjs check
node test/setup-demo.mjs prepare
# root
git diff --check
```

- Tooling: 9 passed (`tooling.log`). Focused Java: 85 passed (`java-focused-final.log`).
- Final clean offline install and verify: all eight projects succeeded; 441 tests, zero failures/errors/skips (`install-final.log`, `verify-final.log`, [Surefire inventory](test-results.csv)). Hosted CI was not triggered.
- Browser: 27 passed and TypeScript/production build passed (`browser-tests-final.log`, `browser-build-final.log`). Existing Vite main-chunk size advisory remains (approximately 520 kB); it is not a build failure.
- Existing mounted preparation form regression passed (`prepared-form.log`). Real MariaDB rollback, concurrent activation, durable retries and lost acknowledgement passed (`jdbc-acceptance.log`); the duplicate-key warning is expected from its race scenario. This ran before the final envelope-validation/failure-response corrections, which are covered by the final focused and full Java runs.
- Independent final review approved after corrections to pending activation reconciliation and full envelope validation. Tests include failed-session response projection, captain/reserves legality, unchanged engine serialization on retry, removed membership/document denial, stale revisions and reversed roles.

Raw `.log` outputs are retained locally and ignored by Git under repository conventions. JSON, screenshots, review, inventory and this report are reviewable evidence artifacts.

## Two-browser demonstration and restart

Two isolated Chrome 152.0.7977.77 contexts used the existing local credentials with the creator identity reversed. Actual preparation/setup UI controls drove activation and legal setup. Both sources were edited from two to three rerolls after activation; the frozen documents and actual engine rerolls remained two. Exact retry, wrong-role rejection, stale rejection, illegal empty confirmation and pending-choice disconnect/rejoin passed. No deterministic dice were supplied.

The final live match is **6813f78a-6396-39e9-9e2c-b903da5e2f3d**, revision 26, READY_FOR_KICKOFF, with 11 legally placed players per team. [Preparation summary](prepare-summary.json), [home screenshot](setup-ready-home.png), [away screenshot](setup-ready-away.png) and sanitized `setup-wire-*.json` record the result. The home screenshot was visually inspected after the final run. Browser contexts closed; the JVM retains this session and Vite remains on loopback port 5173.

The preceding match **b9620c8a-5c75-36a4-bf56-260a8c1cf738** was ready before a real stop/start of the final JVM image. Both coaches then received SESSION_UNAVAILABLE; exact activation retry was duplicate, the persisted document was unchanged, and no engine was reconstructed. See [restart summary](restart-summary.json), [tested activation document](restarted-match.json), `restart-*.png`, `server-stop-final.log`, `server-start-final.log` and `browser-restart-check-final.log`. A fresh prepare run after this check leaves the final match above live and ready.

Final image: `ffb-server:3.4.0-m3a.1`, `sha256:593b8cb08cb623c59ee0d8b025f5e3186e1f70d3343938cbb7db92100e808528`. The local server/database are healthy; server port 22227 is loopback-only and the database port is unpublished. Schema remains 3; no DDL migration is required, but the document reader now supports ACTIVATED/revision 3. See [identity](container-identity.txt) and [status](container-status.txt).

## Failures, corrections and limits

An initial fast-forward attempt hit sandbox `.git/ORIG_HEAD.lock` permissions; authorized escalation succeeded. An initial Java compile passed Game where a factory source was needed; corrected to `game.getRules()`. Sandbox ClassGraph execution was denied; the same tests passed with authorized host execution. One browser-test invocation used the wrong working directory and failed before tests ran; the corrected invocation passed. Intermediate browser integration was corrected to preserve the M2 form and match the strict setup response contract. Review corrections were applied and reverified. Earlier logs are retained; final logs supersede intermediate results.

Only activation is durable. Setup snapshots/actions/history and pending activation reservations are process-local. Restart, initialization failure or loss of a session ends availability permanently for that activated document; there is no in-progress recovery or automatic reactivation. Capacity is 32 retained engine lifetimes per adapter (including failed/ready sessions), with 256 setup history entries per session. Completion cleanup and durable recovery are future work. Page reload loses the browser's in-memory retry convenience. No mobile/multiple-browser-engine certification, production account lifecycle/TLS or backup-restore certification is claimed.

## Remaining M2 and M3 acceptance

M2a catalog/validation and M2b saved-team/import/export evidence remain applicable; M2c durable preparation/restart evidence remains applicable. This slice closes the integration gap between frozen persisted match data and the real engine: source edits cannot change activated teams and persisted roles authorize every introduced setup operation, including reversed creator identity. Combined evidence supports bounded local M2 integration. Public account issuance/revocation and production transport remain undelivered and must not be inferred from local credentials.

M3a ends before the kick is taken. Remaining M3 work includes kickoff execution, supported gameplay choices/actions, drives/halves, match completion/results, lifecycle cleanup and any separately designed recovery. The catalog, infrastructure and authoritative rules were not broadened.
