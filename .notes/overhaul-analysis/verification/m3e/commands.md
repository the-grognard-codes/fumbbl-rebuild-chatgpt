# M3e commands and affected-test manifest

Run from the repository root unless a different directory is shown. Host commands
needed authorized access to Docker/Chrome/PowerShell 7 and ClassGraph. No package
or infrastructure upgrades were performed. Full output was redirected to the
filenames in [README](README.md), preserving each command's exit status.

## Java and tooling

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-tooling.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test BrowserPreparedMatchAdapterTest
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test 'BallAndFoulActionsTest,CoreTurnActionsTest,SetupSessionTest,MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 install -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 verify -Offline
pwsh -NoProfile -File browser-client/test/test-team-schema.ps1
pwsh -NoProfile -File browser-client/test/test-saved-team-schema.ps1
```

New Java regression:
`BrowserPreparedMatchAdapterTest#reversedMembershipAndFrozenSourcesProtectPlacementConfirmationAndKickoff`.
It removes saved sources before activation, checks native frozen team IDs,
exercises reversed subjects through choices, placement, confirmation and kickoff,
compares full engine JSON around wrong-owner/stale/duplicate actions, then removes
the persisted match and verifies every recorded mutation fails without engine change.
Remaining selector suites cover the full supported action matrix, validation,
transactions, activation/completion, transport and original fixture isolation.

## Browser

In `browser-client`, keep Vite running in a separate terminal:

```powershell
npm.cmd run dev
```

Then:

```powershell
npm.cmd test
npm.cmd run build
node test/setup-recovery-demo.mjs
node test/results-mock.mjs
node test/prepared-match-mock-form.mjs
node test/saved-team-mock-form.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/matrix-final'
node test/supported-actions-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/live-reversed-final'
$env:M3_INTEGRATED='1'
node test/full-match-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/live-home'
$env:M3_CREATOR='home'
node test/full-match-demo.mjs
Remove-Item Env:M3_CREATOR
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/supported-live-final'
node test/supported-play-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/m2a'
node test/team-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/m2b'
node test/saved-team-demo.mjs prepare
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/m2c'
node test/prepared-match-demo.mjs prepare
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/setup'
node test/setup-demo.mjs prepare
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e'
node test/integrated-auth-demo.mjs
```

Use a fresh evidence directory when reproducing to preserve previous captures.
The live drivers create synthetic saved teams/matches within the supported catalog;
they retain rows and require available capacity. Do not reset volumes or delete
records to work around capacity. `M3_INTEGRATED=1` injects lost UI acknowledgements,
same-page disconnect/reconnect, exact retries and stale/wrong-owner probes, and
updates/imports only the driver's own newly created source teams after activation.
No seed, dice, fixture control or arbitrary legacy command is sent.

New tests `setup-recovery.test.ts` validate retained-request/uncertain-outcome
policy. `setup-recovery-demo.mjs` exercises the mounted component, keyboard/pitch,
unknown outcome, page reload, wrong identity, denied load, retired socket and
foreign-match frames. `results-mock.mjs` adds foreign load/replay response checks.
The 88-frame matrix is mounted native-wire evidence, not live service evidence.

## MariaDB and restart

Wait for active live demonstrations to finish before restarting. From the root:

```powershell
$env:PATH="$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
$testClasses=(Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.team.SavedTeamJdbcAcceptance
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
docker compose -f containers/local/compose.yaml ps --format json
docker inspect ffb-local-m0b-server-1 --format '{{.Image}}'
```

From `browser-client`, consuming the captured artifacts:

```powershell
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/restart-reversed'
$env:M3_LIVE_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/live-reversed-final'
node test/result-restart-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/restart-home'
$env:M3_LIVE_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/live-home'
node test/result-restart-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/m2b'
node test/saved-team-demo.mjs check
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/m2c'
node test/prepared-match-demo.mjs check
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/setup'
node test/setup-demo.mjs check
```

After the final response-correlation corrections and restart checks, the fresh
reviewed-source full-match command was:

```powershell
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/m3e/live-reviewed'
$env:M3_INTEGRATED='1'
node test/full-match-demo.mjs
```

Final whitespace check from root: `git diff --check`. No commit/push or hosted CI
was run. The full local verify lifecycle is the recorded Maven acceptance.
