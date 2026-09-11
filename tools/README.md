# Java 8 build baseline (M0a)

**R1:** The Java 8 build instructions below now apply to frozen revision
`56c19c80070861e89f465f1989be403d3aad0ffd`, not the current Jetty 12 server sources.
Use [target-build.ps1](target-build.ps1) for current Java 21 sources, with
`test -Module ... -Test ...`, `install`, or `verify` and optional `-Offline`.
Supply `-JavaHome` on Linux. See [runtime policy](../containers/local/runtime-compatibility.md)
and [exact baseline reproduction](../.notes/overhaul-analysis/verification/r1/java8-baseline/README.md).

Run these commands from the repository root. Windows x64 setup needs only
Windows PowerShell 5.1 (included with Windows) or PowerShell 7. No global Maven,
JDK installation, administrator privileges, Node, Docker or database is needed.
An existing PowerShell 7 is used on Linux CI. Java 21 remains the accepted future
runtime; these entry points deliberately validate the exact Java 8 reference.

## Setup and versions

```powershell
./tools/bootstrap.ps1
./tools/build.ps1 info
./tools/test-tooling.ps1
```

If Windows execution policy prevents a script from running, use a process-only
invocation, e.g. `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/bootstrap.ps1`.
The same prefix works for the build and tooling-check scripts. Do not change the
machine execution policy. Scripts support PowerShell 5.1 and 7; neither requires
a module installation.

The version/checksum source of truth is [build-toolchain.json](build-toolchain.json):

| Component | Pin |
|---|---|
| Apache Maven | 3.9.9, official ZIP verified with SHA512 |
| Temurin HotSpot JDK | 8u504-b01 (`1.8.0_504-b01`), Windows x64 ZIP verified with SHA256 |
| Clean / resources / compiler plugins | 3.2.0 / 3.3.1 / 3.13.0 |
| Surefire / default jar / install plugins | 3.2.5 / 3.4.1 / 3.1.2 |
| Existing child jar / shade / assembly plugins | 2.4 / 3.5.1 / 3.6.0 (unchanged) |
| Mockito / JUnit Jupiter API | 4.11.0 / 5.5.0 (unchanged) |

Plugin management pins the defaults observed during the audit; child overrides
remain effective. No application dependencies, rules, tests or disabled scenarios
were changed. The existing `mockito5` profile remains available for separate
Java 21 work through direct Maven invocation; this launcher does not enable it.
A Java 21 build would not replace the Java 8 gate or demonstrate server startup.

Bootstrap first checks `.tools/downloads`, then the audit archives under
`.notes/overhaul-analysis/verification`, then downloads the exact URLs in the
manifest. Every reused/downloaded archive is checksum-checked before extraction.
It does not copy the old audit dependency cache. Already completed installations
are reused. A partial extraction produces an actionable error; move that named
tool directory aside and rerun setup. For a checksum mismatch, remove the named
bad archive and retry. Existing audit files are never modified.

Generated JDK/Maven installations, archives, selected JDK path, Maven repository
and tooling-check fixtures live under ignored `.tools/`. Build outputs remain in
each module's `target/`; tests may use the OS temporary directory as before.
Bootstrap does not change global PATH or JAVA_HOME. The launcher temporarily sets
JAVA_HOME, UTF-8 and Maven options, then restores the caller environment/location.
It ignores Maven startup scripts and uses the checked-in empty settings for both
user and global Maven settings, avoiding ambient credentials, mirrors and profiles.
The local Maven repository is always the absolute `.tools/repository` path.

For an existing **exact** Temurin JDK, including Linux:

```powershell
./tools/bootstrap.ps1 -JavaHome '/absolute/path/to/jdk8u504-b01'
```

Automatic JDK download supports Windows x64. Other hosts must provision the exact
platform JDK themselves and pass `-JavaHome`; CI uses `actions/setup-java` with the
manifest's exact `8.0.504+1` version. JAVA_HOME on PATH is never silently selected.
Moving a checkout or JDK requires rerunning bootstrap to refresh its saved path.

## Build and test commands

```powershell
# Print the actual Maven/JDK identity.
./tools/build.ps1 info

# Audit characterization subset: 39 tests in common, server and statetest.
./tools/build.ps1 focused

# Required complete reactor build, tests, packaging and local installation.
./tools/build.ps1 install

# Required CI lifecycle, independently of the install run.
./tools/build.ps1 verify

# All tests without cleaning or packaging.
./tools/build.ps1 test

# One suite, with its reactor prerequisites.
./tools/build.ps1 test -Module ffb-statetest -Test BlockTest

# A single existing block scenario.
./tools/build.ps1 test -Module ffb-statetest -Test 'BlockTest#bothDownResolvesBothPlayersToProne'
```

`install` runs `--batch-mode clean install`; `verify` runs `--batch-mode clean verify`.
Both include all seven child modules. No parallel reactor option is used.
`focused` reproduces the audit selector: BlockTest, ShadowingTest,
HandOffTurnoverTest, SafePassTest, SwarmingEndTurnTest, RulesTest,
ReRollApiEquivalenceTest and SessionTimeoutTaskTest. Custom selectors are allowed
only with `test`; `-Module` adds `-pl <module> -am`. Selectors allow prerequisite
modules without matching tests, so verify the expected nonzero test count in the
output (a misspelled custom selector can otherwise execute zero tests).
Do not use focused/selected tests as substitutes for the full lifecycle checks.

The scripts return Maven's exit status. XML test reports are under each tested
module's `target/surefire-reports/`. Save/copy them before another `clean` run.
To retain a log and preserve the exit status in a separate process or CI step:

```powershell
./tools/build.ps1 verify *> .tools/verify.log
$buildExit = $LASTEXITCODE
Get-Content .tools/verify.log -Tail 30
exit $buildExit
```

## Offline use, CI and boundaries

First setup/build needs access to Maven Central and (without a reused JDK archive)
the official Temurin GitHub release. For disconnected setup, place the two pinned
archives in `.tools/downloads/`, or retain the audit archives, then run
`./tools/bootstrap.ps1 -Offline`. An existing JDK can be supplied together with
`-Offline`. This prepares tools only; it does not make Maven dependencies available.
After the corresponding online build has filled the repository:

```powershell
./tools/build.ps1 focused -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
```

Missing cached plugins/dependencies fail offline; rerun online to resolve them.
No user `.m2` cache is required. Module-local bundled libraries are resolved from
the existing `ffb-client/repo` and `ffb-client-logic/repo` declarations.

[CI](../.github/workflows/maven-verify.yml) uses Ubuntu 24.04, the exact manifest
JDK, bootstrap, the offline tooling integration checks, and the same `verify`
entry point. Only `.tools/repository` is cached, keyed by OS, toolchain, POMs and
bundled libraries. No personal Maven repository is cached or required. Runner OS
patches and action major tags are not immutable VM/action snapshots. A local
Windows run is CI-lifecycle-equivalent, not a hosted Linux Actions result.

Expected audit baseline: **348 reported, 347 passed, one disabled, zero failures
or errors**. The disabled On The Ball/Dump Off case remains disabled. State tests
primarily characterize BB2025; passing tests are not a full rules coverage claim.
Reproducible here means fixed tooling and rerunnable checks, not byte-identical
ZIP/JAR output (legacy timestamps and Built-By metadata remain).

If execution is sandboxed, Maven downloads can fail with `Permission denied:
connect`, and the state harness can fail in ClassGraph with filesystem access
errors. Record the failure and rerun the same check with authorized permissions;
do not change tests or rules to hide an environment restriction.

Server/database startup, image/Compose packaging, fixture matches, browser
features, Java 21 compatibility and production artwork are separate slices. See
[kickoff status](../.notes/overhaul-analysis/09-implementation-kickoff.md#current-status)
for measured M0a results and the next M0b action.

References: [Maven configuration](https://maven.apache.org/guides/mini/guide-configuring-maven.html),
[setup-java version syntax](https://github.com/actions/setup-java), and the retained
[audit verification](../.notes/overhaul-analysis/07-verification-and-coverage.md).
