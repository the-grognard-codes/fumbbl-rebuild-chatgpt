# Verification and coverage audit

Status: **completed baseline verification**. Both required Java 8 lifecycle runs passed: 348 reported tests, 347 passed, one existing disabled test, zero failures/errors in each run. Full independent service startup and browser play remain unverified.

## Scope and environment

Audit date: 2026-09-06 (America/New_York). This work changes only audit notes and generated build output; no source, configuration, or asset edits were made. Existing tests were inspected before execution for server/database/network side effects.

The host initially exposed Temurin JDK 21.0.11 on PATH and no Maven. Bounded searches covered Program Files Java/Eclipse Adoptium/Apache, the user's .m2, Scoop, Chocolatey, Downloads, Tools, local Programs, and repository/Codex filenames. No Java 8 or runnable Maven installation was found. The existing Maven cache contained 1,188 files totaling 47,274,110 bytes and was copied into `verification/maven-cache` to keep dependency/install writes in the workspace.

Portable Apache Maven 3.9.9 was downloaded from Maven Central and checked against its published SHA512. Portable Temurin 8u504-b01 was downloaded through the official Adoptium API and checked against the API's SHA256. Metadata and hashes are in `verification/java8-metadata.json` and `verification/tooling.txt`. System installation and global PATH/JAVA_HOME were not changed; JAVA_HOME was set only for each build process. The originally installed Java 21 profile was not used for Java 8 CI reproduction.

The root POM declares source/target 8, seven child modules including `ffb-statetest`, Mockito inline 4.11.0 by default, and a manually activated `mockito5` profile. The actual reactor order is common, tools, server, client-logic, resources, client, statetest. Maven 3.9.9 resolves compiler 3.13.0 and Surefire 3.2.5 defaults; these are not pinned in project POMs. CI (`.github/workflows/maven-verify.yml`) uses Ubuntu latest and Temurin Java 8 with `mvn --batch-mode clean verify`. This audit uses Windows and therefore does not reproduce Linux packaging/platform behavior.

## Safety and side effects

`ffb-statetest/.../TestServer.java:25` creates a temporary directory and logging objects, then constructs a STANDALONE server and manually installs session, communication, RNG, game cache, and DbUpdater objects. It does not invoke `FantasyFootballServer.run()` or start the database updater thread. The timeout unit test mocks all sessions and server communication. Inspection found no live database/service startup in existing test setup. Maven goals compile, test, package ZIP/JAR artifacts, and install into the workspace cache; no deployment goal was run.

The first sandboxed Java 8 state-test run failed during ClassGraph static initialization with AccessDeniedException while resolving the module's current directory. Rules/reroll/timeout tests passed. An approved escalation of the identical offline selector passed all 39 tests. This is a demonstrated sandbox/environment restriction rather than a gameplay defect. Logs preserve both attempts. Portable download initially hit the sandbox's socket restriction and succeeded after approved escalation. A first focused invocation also exposed PowerShell argument splitting of an unquoted Maven dotted property; its corrected quoted command is recorded below.

## Reproducible commands

Run from repository root in PowerShell. Set process-local variables:

```powershell
$env:JAVA_HOME = (Resolve-Path .notes\overhaul-analysis\verification\java8\jdk8u504-b01).Path
$cache = (Resolve-Path .notes\overhaul-analysis\verification\maven-cache).Path
$maven = '.notes\overhaul-analysis\verification\apache-maven-3.9.9\bin\mvn.cmd'
```

Focused selector (successful elevated, offline execution):

```powershell
& $maven --batch-mode -o "-Dmaven.repo.local=$cache" -pl ffb-statetest -am '-Dtest=BlockTest,ShadowingTest,HandOffTurnoverTest,SafePassTest,SwarmingEndTurnTest,RulesTest,ReRollApiEquivalenceTest,SessionTimeoutTaskTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Required build and CI-equivalent lifecycle:

```powershell
& $maven --batch-mode "-Dmaven.repo.local=$cache" clean install
& $maven --batch-mode "-Dmaven.repo.local=$cache" clean verify
```

An offline `clean install` attempt failed before compiling because clean/install plugin artifacts were absent from the copied cache. Retrying with Maven Central downloads addresses only missing tooling/dependencies and changes no application code. Exact console logs and exit codes are retained in `verification/`.

## Focused scenario evidence

| Test suite | Invocations | Behavior observed in successful run | Limits |
| --- | ---: | --- | --- |
| RulesTest | 9 | Common eligibility, identity, and selected BB2016/BB2020 exclusion checks | Enum eligibility only; not complete factory/step isolation across rulesets |
| SessionTimeoutTaskTest | 1 | Active sessions stay open; expired game/replay sessions close | Mocked sessions; no real WebSocket connection |
| ReRollApiEquivalenceTest | 12 | Legacy/new reroll API delegation and skill/parameter combinations agree | Equivalence does not independently prove the rules interpretation |
| BlockTest | 4 | Player/block initiation; forced Both Down without Block; Both Down with Block; forced pushback/follow-up | BB2025, small fixtures; not all dice/skills/chain pushes |
| SwarmingEndTurnTest | 4 | Correct coach completes Swarming; unrelated/duplicate/setup end-turn handling | BB2025 focused kickoff branch |
| HandOffTurnoverTest | 4 | Failed catch and loose/opponent/team catches cause or avoid turnover as asserted | BB2025 fixture scenarios |
| ShadowingTest | 2 | Successful/failed Shadowing clears defender state | BB2025, not comprehensive movement/pathfinding |
| SafePassTest | 3 | Natural/rerolled natural one and declining Safe Pass | BB2025 limited pass scenarios |

Successful focused totals: 39 invocations, 0 failures, 0 errors, 0 skipped. XML reports and CSV are retained under `verification/focused-passed-reports` and `verification/focused-results.csv`.

## Independent startup feasibility and remaining gaps

A full independent server was not launched. `FantasyFootballServer.run():158-185` loads a JDBC driver and configures/prepares persistence before starting networking even for STANDALONE. `DbConnectionManager.openDbConnection():28` calls DriverManager and disables auto-commit; startup's initialization mode can create database schema. The existing server.ini was not used for execution. A disposable successful startup would need a prepared compatible database/schema and controlled service configuration; no disposable database was provisioned in this audit. A follow-up PATH check found no docker, podman, mariadbd or mysqld executable. This is a remaining runtime-verification gap, not a restriction on future local testing; the usage-only process check is not startup success.

The in-memory state harness demonstrates gameplay step execution without FUMBBL services. It does not establish two-client connectivity, authentication, live persistence, restart/replay recovery, resource download behavior, UI rendering, or a complete independent match. No JaCoCo instrumentation, mutation testing, fuzzing, load/concurrency run, or measured line/branch coverage was configured or added. Test count is not a coverage percentage.

The only source-declared disabled test found is `OnTheBallPassBlockIntegrationTest.blockOnThrowerWithDumpOffTriggersPassBlock`, annotated `Requires removing DUMP_OFF guard in StepPassBlock.executeStep()`. Its desired behavior remains unverified. All explicit state fixture `.withRule(...)` literals found were BB2025; GameStateBuilder also defaults to BB2025. Existing gameplay state tests therefore do not constitute a BB2016/BB2020 regression matrix.

## Full build results

`clean install` completed successfully at 2026-09-06T16:06:56-04:00 in 2 minutes 18 seconds (Maven-reported elapsed time), with all eight reactor projects successful. Exact log: `verification/clean-install-java8.log`; per-suite evidence: `verification/clean-install-results.csv` and `verification/clean-install-reports/`.

| Module | Executed suites | Reported tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| ffb-common | 5 | 174 | 0 | 0 | 0 |
| ffb-server | 6 | 77 | 0 | 0 | 0 |
| ffb-client-logic | 2 | 23 | 0 | 0 | 0 |
| ffb-statetest | 10 | 74 | 0 | 0 | 1 |
| Total | 23 | 348 | 0 | 0 | 1 |

This means 347 passed and one was source-disabled. Tools, resources, and desktop client contain no Maven-executed test suites. Of 25 `*Test.java` files in test source directories, `DialogBlockRollPropertiesTest` and `DialogReRollPropertiesTest` are manual Swing `main` programs, not annotated JUnit tests; they do not appear in Surefire and were not launched. Maven success therefore does not validate those dialogs visually.

Additional full-suite coverage includes 47 marker generator cases, property consistency checks, 34 step-engine execution cases, three BB2025 block-injury cases, one crowd-push state case, On The Ball mechanics/skills/pass interception scenarios, nine dialog key-guard cases, and 14 client marking-handler cases. Detailed method inventories are in `verification/unit-test-scenarios.txt` and `verification/state-test-scenarios.txt`. Coverage breadth remains sparse relative to the entire ruleset and network/persistence/UI surface.

Build warnings included deprecated/unchecked Java usage and overlapping META-INF/MANIFEST.MF entries during shading. No compilation or test failures remained. Successful server and client ZIP packaging is observed; runtime startup remains separately unverified.

A bounded package smoke check also passed: the freshly assembled server ZIP was extracted into `verification/server-package`, then `verification/java8/jdk8u504-b01/bin/java.exe -jar verification/server-package/FantasyFootballServer.jar` (paths relative to the audit directory) printed the four documented usage forms and exited 0. Source inspection confirmed the no-argument path exits before reading server.ini or constructing/running the server (`FantasyFootballServer.main():314-328`). `verification/packaged-server-usage.log` preserves the result. This verifies that the packaged entry point loads, not that a game service starts. `verification/packaged-artifacts-sha256.csv` records final client/server ZIP hashes.


## CI lifecycle and final evidence

`clean verify` completed with **BUILD SUCCESS, ExitCode=0** at 2026-09-06T16:09:25-04:00 in 1 minute 57 seconds. All eight reactor projects succeeded. The suite totals match clean install: **348 reported, 347 passed, one source-disabled, zero failures/errors**. Evidence: [console log](verification/clean-verify-java8.log), [per-suite CSV](verification/clean-verify-results.csv), and XML files under `verification/clean-verify-reports/`. This rerun was required to exercise the repository's CI lifecycle separately; no additional product tests were added.

The successful build runs used portable Java 8 and an isolated Maven cache. Initial sandbox failures and offline missing-plugin failures are retained for diagnosis; they are not counted as remaining product defects. The Java 21 `mockito5` profile, Linux CI platform, real MariaDB lifecycle, browser client, live WebSocket interoperability, and full-match recovery were not exercised. No required failed check is being represented as passing.

Portable Java/Maven archives, extracted tools, copied dependency cache and package extraction are ignored by the report directory's `.gitignore`; they are local reproducibility tooling, not reviewable product changes. Keep text logs/CSV/XML and checksum metadata when sharing the report. Logs/XML contain local filesystem paths and runtime properties; review them before publishing externally. Nothing was committed, pushed, deployed, or written to live FUMBBL services.
