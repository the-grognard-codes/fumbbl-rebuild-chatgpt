# Implementation kickoff and visual exploration

Recorded 2026-09-06 following the owner's audit acceptance. Repository HEAD before this documentation/artwork follow-up: `d4787f8dd`; working tree was clean. Historical audit measurements remain tied to the revision in the audit evidence index. This follow-up records decisions and produces art concepts; engineering implementation has not begun.

## Accepted direction

- ADR-001 through ADR-004 accepted: Java/Maven engine, staged Java 21/runtime modernization, TypeScript/React/PixiJS/Vite browser client, versioned JSON/WebSockets, narrow application boundaries.
- ADR-005 deferred. GCP is the likely future provider, not a selected service topology. No cloud account, hosting purchase or deployment is needed for local proof.
- M0 through M5 milestone direction accepted. Existing developer-day estimates are not estimates of Codex hours, prompts or sessions.
- Initial visual exploration: Humans, Orcs, pitch, dugouts and core match controls. This names the art samples; complete legal starter roster data still needs validation.
- Full production artwork and color variations for approximately 30 teams will be a separate sprint. This does not add all those teams to the first engineering release.
- Existing scope remains: BB2025 first, desktop first, roster builder and reproducible local testing included, mobile later, 2D only. No 3D, model sharing or league-management implementation.

## Local development assessment

**Verified:** the audit completed Java 8 clean install and clean verify, each with 347 passing and one disabled test. Full service startup was not demonstrated. The server initializes JDBC services; state-harness tests bypass full startup. See [verification](07-verification-and-coverage.md) and [independence assessment](01-architecture-and-independence.md).

**Current environment observation:** Java and Node commands resolve on this machine; Docker, `mariadbd` and `mysqld` commands did not resolve on PATH. This is not an exhaustive installed-software check or proof that virtualization is unavailable.

**Recommendation:** use the local machine for the JVM server, disposable MariaDB and two browser sessions. There is no architectural requirement for cloud hosting during build/test. M0 must demonstrate complete startup, resource needs and isolation rather than assuming these from passing unit tests. Bind diagnostic services to loopback, use local fixture identities and data, and eliminate required live FUMBBL calls. Keep controlled dice and scenario loading outside the public protocol.

**Database choice during M0:** inspect existing local tooling; use a disposable container if already available, otherwise evaluate a workspace-local MariaDB distribution with isolated data, ports, credentials and start/stop scripts. Do not install a global service or change firewall settings merely to make local tests run. Document any actual permission or environment blocker. Keep database selection separate from eventual GCP service selection.

**Subsequent owner decision:** package the server as a Linux Docker image for portability. The owner explicitly authorized installing Docker or MariaDB with an available package manager. Use Docker Desktop with WSL 2 on this Windows host and a separate official MariaDB container; a native MariaDB Windows service is unnecessary if container verification succeeds. This supersedes the earlier assumption that only already-installed container tooling could be used. Hosting selection remains deferred.

For M0, add a reproducible multi-stage server build (Maven build stage, smaller JVM runtime stage) and Compose orchestration for the server/database as the startup configuration becomes known. Pin tool/base-image versions, keep credentials/configuration outside the image, keep persistent database data in a separate named volume, and distinguish routine shutdown from explicit disposable-data reset. Publish local development ports to loopback only. Retain the Java 8 characterization baseline until Java 21 compatibility passes; containerization does not itself validate the runtime upgrade. The same versioned server artifact can be used locally and on a compatible future Linux container host; CPU architecture, storage, routing and platform lifecycle constraints still need testing. GCP service selection is not implied by choosing Docker.

## Scope a session by a demonstrable result

Codex can execute substantial multi-step work, but there is no reliable conversion from a milestone's engineering days into one session. Repository uncertainty, test duration, tool permissions and usage availability affect completion. Prefer one coherent slice with explicit acceptance tests, and allow another session when evidence exposes additional work. OpenAI's [long-running work guidance](https://developers.openai.com/blog/run-long-horizon-tasks-with-codex) recommends checkpointed plans, validation and durable status documents. The slice sizes below are project recommendations, not product limits.

| Slice | Deliverable | Completion evidence |
|---|---|---|
| M0a: build baseline | Reproducible project-local tooling and build/test entry points; retain Java 8 reference | Required build and verification succeed; exact versions and rerun instructions saved. Environmental failures are recorded as blockers, not completion |
| M0b: independent startup | Disposable local database/schema, isolated configuration, local fixture teams and lifecycle adapters only where required | JVM starts and accepts a fixture match without live FUMBBL credentials/required requests; stop/start and disposable-data behavior documented |
| M1a: browser movement | Minimal React/Pixi screen and narrow browser adapter using the existing engine | Two browser sessions show the same server-validated move; wrong-role, illegal, stale and duplicate movement tested |
| M1b: decisions and recovery | Controlled block fixture and pending-choice reconnect | Outcomes match existing engine tests; reconnect restores prompt/ownership/revision; duplicate choices do not consume twice; fixture controls remain local |
| M1c: robustness and closeout | Asset/renderer failure paths, bounded repeat-action run, protocol fixtures and measured report | Full M1 acceptance table accounted for; limitations explicit; recommendation reviewed against actual evidence |
| A0: visual exploration | Human and Orc concept sheets plus rough pitch/dugout/match UI | Owner reviews silhouettes, palette and layout; source prompts and known limitations saved |
| A1: production art trial | A few cleaned sprites at chosen native cell size and exact interactive board layout | Integer-scale readability, selection/status overlays, team differentiation and exported frames verified; not the full art sprint |

M0b may need several sessions if startup reveals additional service assumptions. M2 and later should be split when their inputs are known: for example catalog/validation, team save/load, then match ownership. Do not promise all of M3 in one prompt. M1 can use neutral tokens while A0/A1 are reviewed; art does not block engine validation.

## Session operating contract

At each implementation session, read the accepted ADRs, relevant milestone and current status; inspect the working tree and preserve unrelated edits. Implement the named slice, run focused checks followed by required project verification, and record the result. Save changed files, exact successful/failed checks, remaining work, important decisions and the next command/demo in a durable status entry. If interrupted, do not mark the slice done; a continuation reads that entry before acting.

Use the same task for closely related work when convenient. A new task can continue from the checked-in plan and status without needing the entire conversation. Committing, pushing and deploying remain separate user instructions.

## First engineering prompt

> Implement M0a from `.notes/overhaul-analysis/09-implementation-kickoff.md`, following accepted ADR-001 through ADR-004 and the audit verification report. Establish reproducible project-local build/test tooling, retain the Java 8 characterization baseline, and document exact setup and commands. Inspect existing tooling before adding dependencies. Run focused checks and the required build/CI verification. Preserve unrelated changes. Do not expand into database/server startup, browser features or production artwork. Finish by recording results, blockers and the next M0b action in the kickoff status section. Do not commit, push or deploy.

## Current status

- Audit complete; ADR-001–004 and milestone direction accepted.
- ADR-005 deferred, GCP likely.
- A0 concept previews generated; see [visual preview package](../art-preview/README.md). Style remains open to owner review. These are not production sprites or a functional UI.
- **M0a complete for the local Java 8 build/test baseline, 2026-09-06. M0b complete for independent fixture startup, 2026-09-07.** The versioned JVM image, separate MariaDB Compose service, local schema/fixtures and lifecycle adapters passed actual two-coach acceptance, stop/start persistence and disposable reset. See [M0b evidence](verification/m0b/README.md). M1 browser work has not started in this task.

### M0a implementation results — 2026-09-06

Started from `9120e865c` with a clean working tree. Inspected existing tools before
adding anything: PATH provided Temurin Java 21 and PowerShell; no global Maven
resolved. The audit's Maven 3.9.9 and Temurin 8u504-b01 archives/installations and
dependency cache were still present. Reused the two verified archives, extracted
fresh tools under `.tools/`, and populated a new Maven repository from dependencies
resolved by the project. The audit cache and historical evidence were preserved.
No application dependency was added or upgraded.

Changed files: root `pom.xml` pins the six previously implicit lifecycle plugin
versions; `.gitignore` excludes `.tools/`; `.mvn/settings.xml` isolates Maven
settings; `tools/build-toolchain.json`, `bootstrap.ps1`, `check-java.ps1`,
`build.ps1` and `test-tooling.ps1` provide checksum-verified setup, exact JDK
validation, build/test entry points and tooling integration checks. `Readme.md`
links the [exact setup and command guide](../../tools/README.md).
`.github/workflows/maven-verify.yml` selects Ubuntu 24.04 and the exact manifest
JDK, uses the same setup/verify scripts and caches only the project-local Maven
repository. Player-facing code and the existing characterization tests were not
changed; Java source/target 8, Mockito 4.11.0 and the disabled scenario remain.

Commands below run from repository root in PowerShell. Evidence, including the
failed sandbox attempt, successful console logs and per-suite CSV totals, is in
[the M0a verification record](verification/m0a/README.md).

| Check | Result |
|---|---|
| `./tools/bootstrap.ps1 -Offline` | Passed; reused audit archives, checked Maven SHA512 and JDK SHA256; repeated setup also passed |
| `./tools/build.ps1 info` | Maven 3.9.9; Temurin JDK `1.8.0_504-b01`; workspace-local tools/repository |
| `./tools/test-tooling.ps1` | 9 checks passed under PowerShell 7.6.5 and Windows PowerShell 5.1, including checksum rejection, missing setup, path spaces, environment restoration and nonzero failure propagation |
| `./tools/build.ps1 focused` | 39 passed; zero failures/errors/skips; successful authorized run took 1m10s |
| `./tools/build.ps1 install` | Required `clean install` passed, all eight reactor projects; 348 reported, 347 passed, one disabled, zero failures/errors; 2m10s |
| `./tools/build.ps1 verify -Offline` | Required `clean verify` CI lifecycle passed using the populated local cache, all eight reactor projects; same 348/347/1 totals, zero failures/errors; 1m45s |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./tools/build.ps1 test -Module ffb-statetest -Test 'BlockTest#bothDownResolvesBothPlayersToProne' -Offline` | One existing scenario passed; verifies the documented method-selector entry point under Windows PowerShell 5.1 |
| Syntax/configuration and diff checks | PowerShell scripts parsed; POM/settings XML and toolchain JSON parsed; workflow reviewed; `git diff --check` passed |

**Blockers and limits:** no remaining local M0a build/test blocker. The first
focused attempt was blocked by sandbox socket permissions while fetching a Maven
plugin; the identical command passed with authorized network/filesystem access.
Required lifecycles used that same approved execution context. The full offline
verify demonstrates the local dependency cache is sufficient. Hosted Ubuntu
Actions execution remains unverified: the CI lifecycle was reproduced on Windows,
and no push or workflow dispatch was performed. Automatic fresh network download
of the tool archives was not exercised here because verified copies were reused.
Java 21 compatibility, service startup, database integration and browser behavior
remain outside this slice. Legacy artifact timestamps/Built-By metadata mean this
is a reproducible toolchain/test baseline, not a byte-identical archive guarantee.

**Next M0b action:** start by reading `10-container-environment.md` and tracing
`ffb-server/server.ini`, `FantasyFootballServer.run()`/`initDb`, and the JDBC/schema
initialization paths to define the minimum isolated startup configuration and
fixture data. Then implement the required local lifecycle adapters, versioned
Linux server image and separate MariaDB Compose service as those startup inputs
become known. The next acceptance demo is a JVM accepting a fixture match without
required live FUMBBL calls, followed by documented stop/start and disposable-data
behavior with loopback-only ports. Do not treat the successful build or the earlier
MariaDB image smoke test as that demo. No commit, push, deployment, service startup,
browser feature or production artwork was performed during M0a.

### M0b implementation results — 2026-09-07

Executed the M0b handoff above; preserved M0a and unrelated concurrent working-tree
changes. [Startup trace](11-independent-startup.md) documents the server INI, JDBC
schema and local lifecycle decisions. [Container commands](../../containers/local/README.md)
cover startup, fixture acceptance, normal stop/start and explicit disposable reset.

The Java 8 image `ffb-server:3.4.0-m0b.1` uses digest-pinned Maven/JDK/JRE stages and
separate MariaDB 11.8.9. Config/secrets/fixtures are mounted outside the image.
Local schema version 1 initializes only an empty database and refuses unversioned
or partial data. Local adapters configure BB2025 before fixture roster loading,
disable S3 fallback and prevent JDBC close from shutting down the separate database.
Shutdown drains producer workers and database work before connection closure.

**Actual acceptance:** normal loopback profile returned HTTP 200 and accepted match
3; the same image accepted match 4 with only an internal network/no external route.
Both fixture coaches received game states over real WebSockets and JDBC readback
passed. Stop/start retained match 4. Explicit volume reset changed 4 game rows to 0,
recreated schema version 1/two coaches, and rejected the old match with HTTP 404.
A fresh two-coach match then passed as ID 1. Both services were left stopped, with
that fresh data and external secrets retained. MariaDB has no published port;
normal JVM access binds only `127.0.0.1:22227`. Docker Desktop's internal-only
port-publishing limitation requires separate normal and offline network profiles.

**Verification:** Linux image build passed common/server tests. Required host Java
8 clean install and clean verify both passed all eight reactor projects: 358 tests
reported, 357 passed, one existing disabled test, no failures/errors. Ten focused
tests protect the new behavior. Initial ClassGraph sandbox access failure was
resolved by authorized execution outside the sandbox. [Exact logs, test manifest,
failures and limitations](verification/m0b/README.md).

**Limits:** container Java 8u502-b07 differs from host reference 8u504-b01 because
the exact host-patch image tags were unavailable. No completed-match/replay cleanup,
pending-choice recovery, production auth, browser behavior or Java 21 claim. A late
replay-delete callback during shutdown remains logged/rejected with its DB copy
retained. No commit, push, deployment, browser feature or production artwork was
performed in this task.

**Next action:** M1a — start the documented local stack and introduce the narrow
browser adapter/two-browser movement demo using neutral tokens. Keep authority,
illegal moves, stale actions and duplicate actions in the acceptance criteria.
