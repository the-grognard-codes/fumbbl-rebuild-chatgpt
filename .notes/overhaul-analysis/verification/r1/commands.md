# R1 commands and affected-test manifest

Commands run from repository root unless stated. Use a fresh evidence path when
reproducing; retain earlier artifacts and all database/backup volumes. Host access
was needed for JVM instrumentation/ClassGraph, Docker and Chrome. No global
execution-policy or credential changes were made.

## Java

The [baseline commands](java8-baseline/README.md) archive the frozen git revision,
run the focused selector and clean verify with Java 8, absolute Maven settings
and the project-local cache. `.tools/r1-java8-baseline` was never changed to Jetty 12.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/target-build.ps1 info
powershell -NoProfile -ExecutionPolicy Bypass -File tools/target-build.ps1 test -Module ffb-statetest -Test 'BallAndFoulActionsTest,CoreTurnActionsTest,SetupSessionTest,MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest,BrowserJettyContractTest'
powershell -NoProfile -ExecutionPolicy Bypass -File tools/target-build.ps1 install
powershell -NoProfile -ExecutionPolicy Bypass -File tools/target-build.ps1 verify -Offline
```

`target-focused-2.log` uses the narrower delivery/socket/transport/session-timeout
selector before engine characterization. The initial offline install failure and
successful online cache-warming install have separate logs. Resolved dependencies:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
& .tools/apache-maven-3.9.9/bin/mvn.cmd -B -ntp -o -s .mvn/settings.xml -gs .mvn/settings.xml "-Dmaven.repo.local=$PWD/.tools/repository" -pl ffb-server org.apache.maven.plugins:maven-dependency-plugin:3.7.0:tree
```

The seeded comparison applies only [parity-fixture.patch](parity-fixture.patch)
to a **second** fresh frozen Java 8 archive. Run the changed method first, then
the complete `BallAndFoulActionsTest` on both runtimes. Copy each
`ffb-statetest/target/m3c-actions.json` to separate retained evidence files, then:

```powershell
node browser-client/test/runtime-parity.mjs .notes/overhaul-analysis/verification/r1/java8-actions.json .notes/overhaul-analysis/verification/r1/target-actions.json .notes/overhaul-analysis/verification/r1/engine-parity.json
```

No game state is ignored: only random UUID-valued `matchId` and `requestId` are
mapped bijectively to encounter-order labels. Before/request/after values, dice
consequences, resources, actions, prompts and ordering must otherwise be equal.

## Isolated runtime and database

```powershell
$env:PATH="$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
docker compose -f containers/local/compose.r1.yaml build server
docker compose -f containers/local/compose.r1.yaml up -d --wait
$testClasses=(Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.r1.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
docker compose -f containers/local/compose.r1.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.team.SavedTeamJdbcAcceptance
docker compose -f containers/local/compose.r1.yaml exec -T server java -cp 'FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.local.LocalAcceptanceDemo create
```

`--rm` above removes only one-off process containers, never named data volumes.
R1 has its own MariaDB 11.8.9/database/backup/networks and reuses the existing secret
files unchanged. Do not combine the R1 Compose file with the reference Compose
file or override the project name. Leave unfinished match JVMs running.

Historical `container-build-final.log` built this same image through the first
experimental `server-r1` service; delivered configuration uses standalone `server`.
`comparison-drain-stop.log` stops only `ffb-local-m0b-server-r1-1` after its sole
match completed and replay comparison finished. It never stops the reference JVM.

## Browser (from browser-client)

Vite was already running on loopback 5173. Browser tests alone select port 22228;
the product's fixed reference endpoint remains 22227.

```powershell
npm.cmd test
npm.cmd run build
$env:M4_WS_ENDPOINT='ws://127.0.0.1:22228/browser/v1'
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/r1/isolated-complete-final'
$env:M3_INTEGRATED='1'
node test/full-match-demo.mjs
Remove-Item Env:M3_INTEGRATED
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/r1/isolated-supported-second'
node test/supported-play-demo.mjs
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/r1'
$env:M4_MATCH_ID='75d2cf5a-b6f4-3578-a57b-d3da18f12712'
node test/integrated-auth-demo.mjs
$env:M4_ACTION_FIXTURE='../.notes/overhaul-analysis/verification/r1/target-actions.json'
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/r1/mounted-target'
node test/supported-actions-demo.mjs
```

The historical Java 8 committed artifact was compared before retiring the first
comparison JVM, using `result-restart-demo.mjs`, `M4_WS_ENDPOINT` as above,
`M3_LIVE_EVIDENCE=../.notes/overhaul-analysis/verification/m3e/live-reversed-final`
and output `legacy-replay-parity`. This was format comparison, not a process
restart, backup restore or recovery test. Its match is not copied into the fresh
R1 database, and that command cannot simply be repeated against the new database.

`runtime-inventory.json` records images, lifecycle timestamps, ports, volume names
and networks. Tool/JDK/database version files and dependency trees record pins.
Final `git diff --check` and a local scan of R1 text artifacts against configured
secrets are recorded without writing the secrets into evidence.
