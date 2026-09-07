# M0a verification — 2026-09-06

Windows 11 x64; PowerShell 7.6.5 for full lifecycles, plus Windows PowerShell 5.1
for tooling and method-selector checks. Starting revision `9120e865c`, initially
clean working tree. Source/target 8 and the existing test baseline are unchanged.
See [setup/commands](../../../../tools/README.md) and
[kickoff status](../../09-implementation-kickoff.md#current-status).

## Reproduction and results

Run from repository root in PowerShell:

| Exact command | Exit | Evidence |
|---|---:|---|
| `./tools/bootstrap.ps1 -Offline` | 0 | [Setup log](bootstrap-offline.log); fresh `.tools/` extraction reused checksum-verified audit archives, repeated setup passed |
| `./tools/build.ps1 info` | 0 | [Actual tool versions](toolchain.txt) |
| `./tools/test-tooling.ps1` | 0 | [PowerShell 7: 9 passed](tooling-checks.log) |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/test-tooling.ps1` | 0 | [Windows PowerShell 5.1: 9 passed](tooling-checks-ps51.log) |
| `./tools/build.ps1 focused` (initial sandbox attempt) | 1 | [Maven Central socket restriction](focused-java8.log); failed before tests |
| `./tools/build.ps1 focused` (authorized network/filesystem access) | 0 | [39 passed](focused-java8-approved.log), [suite totals](focused-results.csv); finished 23:36:42 EDT, Maven duration 1m10s |
| `./tools/build.ps1 install` (authorized execution) | 0 | [Clean install](clean-install-java8.log), [suite totals](clean-install-results.csv); finished 23:40:44 EDT, 2m10s |
| `./tools/build.ps1 verify -Offline` (authorized execution) | 0 | [Clean verify](clean-verify-java8-offline.log), [suite totals](clean-verify-results.csv); finished 23:43:12 EDT, 1m45s |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/build.ps1 test -Module ffb-statetest -Test 'BlockTest#bothDownResolvesBothPlayersToProne' -Offline` | 0 | [One scenario passed](single-method-ps51.log); finished 23:43:45 EDT |

Console logs were captured with PowerShell `*> <log-path>`; `$LASTEXITCODE` was
saved immediately after each build and propagated. Full lifecycle CSVs were
derived from the 23 Surefire XML suites immediately after each clean run. The
focused CSV includes only the eight explicitly selected suites (unselected old
reports can remain after Maven `test`, which does not clean). The subsequent
single-method check overwrote BlockTest's working XML; the saved full-run CSV and
console logs retain its complete lifecycle results.

| Module | Suites | Reported | Passed | Disabled | Failures/errors |
|---|---:|---:|---:|---:|---:|
| ffb-common | 5 | 174 | 174 | 0 | 0 |
| ffb-server | 6 | 77 | 77 | 0 | 0 |
| ffb-client-logic | 2 | 23 | 23 | 0 | 0 |
| ffb-statetest | 10 | 74 | 73 | 1 | 0 |
| Total in each full lifecycle | 23 | 348 | 347 | 1 | 0 |

All eight reactor projects passed in both full runs. Tools, resources and desktop
client have no Maven-executed tests. The existing On The Ball/Dump Off scenario
remains disabled. Packaging produced the server/client ZIPs; their final verify
[SHA256 values](packaged-artifacts-sha256.csv) identify this run only, not a promise
of deterministic archive bytes. No packaged server was started.

PowerShell syntax, root POM/settings XML, and toolchain JSON parsed successfully;
workflow configuration was reviewed and `git diff --check` passed. No YAML linter
was installed for this work (the inspected system Python has no PyYAML).

## Environment and remaining limits

The bootstrap reused official Maven 3.9.9 and Temurin 8u504-b01 archives already
inspected during the audit, verifying the manifest hashes before fresh extraction.
The new `.tools/repository` began empty; it was populated by the focused and
install runs, without copying the audit/user Maven cache. The complete offline
verify then passed. Maven downloads needed permission beyond the sandbox; the
failed attempt is retained and its successful rerun is separately identified.
No required failed check remains represented as passing.

The first PowerShell 5.1 tooling-harness trial exposed its treatment of redirected
native stderr as PowerShell errors. The harness was corrected to assert process
exit codes; all nine checks then passed under both shells. This changed tooling
validation only, with no gameplay-test changes.

No remaining local M0a blocker. Hosted Linux CI was not run, and neither the
automatic archive-download path nor non-Windows bootstrap execution was exercised
on this host. Those limitations do not establish Linux/runtime compatibility.
Java 21, real server/database lifecycle, browser functionality and artwork remain
separate work. No commit, push, workflow dispatch or deployment was performed.
