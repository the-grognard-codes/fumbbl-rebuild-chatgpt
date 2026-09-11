# R1 Java 8 characterization baseline

This comparison oracle is frozen revision `56c19c80070861e89f465f1989be403d3aad0ffd`. It is not Java 21/Jetty 12 target evidence and does not establish container or live-service readiness.

The successful host runs used `C:\Users\jaken\Git-Hub\fumbbl-rebuild-chatgpt\.tools\jdk8u504-b01`: Temurin OpenJDK 64-Bit Server VM, `java.runtime.version = 1.8.0_504-b01`. Maven was Apache Maven `3.9.9` (`8e8579a9e76f7d015ee5ec7bfcdc97d260186937`).

`focused-host.log` records the M3e focused selector passing. `verify-host.log` records complete `clean verify` passing in 3m12s. The final Surefire XML reports **485 tests, 0 failures, 0 errors, 0 skipped**; [test-suites.csv](test-suites.csv) contains the module counts. Archived `ffb-statetest/target/m3c-actions.json` SHA-256: `4BA5245A3D7768030A38A1B1A6AABF0CC4C4BA832651363ADF1B5EE252C34F51`.

The direct, offline frozen `ffb-server` dependency tree is [dependency-tree.txt](dependency-tree.txt): Jetty `9.4.0.v20161208`, MariaDB client `3.5.8`, ClassGraph `4.8.98`, JUnit `5.5.0`, and Mockito inline/JUnit `4.11.0`.

## Reproduce from a fresh archive

Run from the repository root. The local Maven repository must already contain the pinned dependencies.

```powershell
$root = (Get-Location).Path
$archive = Join-Path $root '.tools/r1-java8-baseline-reproduce'
New-Item -ItemType Directory -Path $archive
git archive 56c19c80070861e89f465f1989be403d3aad0ffd -o "$archive.tar"
tar -xf "$archive.tar" -C $archive
$jdk = Join-Path $root '.tools/jdk8u504-b01'
$maven = Join-Path $root '.tools/apache-maven-3.9.9/bin/mvn.cmd'
$settings = (Resolve-Path (Join-Path $archive '.mvn/settings.xml')).Path
$repository = (Resolve-Path (Join-Path $root '.tools/repository')).Path
$env:JAVA_HOME = $jdk
& $maven -f (Join-Path $archive 'pom.xml') -o -B -ntp --settings $settings --global-settings $settings "-Dmaven.repo.local=$repository" -pl ffb-statetest -am "-Dtest=BallAndFoulActionsTest,CoreTurnActionsTest,SetupSessionTest,MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
& $maven -f (Join-Path $archive 'pom.xml') -o -B -ntp --settings $settings --global-settings $settings "-Dmaven.repo.local=$repository" clean verify
```

The quoted `-D` arguments are required. `focused.log` retains the earlier PowerShell unquoted-property failure and `focused-quoted.log` retains the sandbox ClassGraph/Mockito access failure. `focused-host.log` is the successful host rerun; the earlier logs remain as execution-condition evidence.
