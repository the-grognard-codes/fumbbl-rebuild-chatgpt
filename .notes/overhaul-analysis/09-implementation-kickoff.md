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

- **M2a implemented and validated, 2026-09-07 local / 2026-09-08 UTC.** The owner
  approved Blood Bowl Base BB2025 as source of truth and selected a 1,150,000-gold
  preset. A versioned Human catalog, immutable draft/validation seams and React
  DOM builder now pass focused and full Java/browser checks and a local live demo.
  See [M2a closeout](verification/m2a/closeout.md) and the preserved
  [original provenance gate](verification/m2a/README.md). M2 is not complete.
  Next slice: **M2b — saved-team round trip**.

- Audit complete; ADR-001–004 and milestone direction accepted.
- ADR-005 deferred, GCP likely.
- A0 concept previews generated; see [visual preview package](../art-preview/README.md). Style remains open to owner review. These are not production sprites or a functional UI.
- **M0a complete for the local Java 8 build/test baseline, 2026-09-06. M0b complete for independent fixture startup, 2026-09-07. M1a complete for browser movement, 2026-09-07. M1b complete for controlled decisions/reconnect, 2026-09-07.** See [M0b evidence](verification/m0b/README.md), [M1a evidence](verification/m1a/README.md), and separate [M1b evidence](verification/m1b/README.md). M1b passed four engine-parity and two-browser choice/reconnect variants. **M1c robustness/closeout and overall M1 complete, 2026-09-07**, following independent approval of the [M1c report](verification/m1c/README.md) and all nine acceptance rows.

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

### M1a implementation results — 2026-09-07

**Complete for the browser movement slice.** Started at `4fd75d0d0` with a clean
working tree. Implemented TypeScript/React/PixiJS with neutral home/away tokens,
server-derived state/resource panels, coordinate controls and correlated result
log. Added the local `/browser/v1` adapter with distinct generated home/away bearer
credentials, canonical 26×15 coordinates, strict version/field validation,
monotonic revision, semantic request dedupe and fail-closed history capacity.
The existing communication queue serializes browser requests. The BB2025 Select/
Move sequence and `GameState.handleCommand` execute the move; rules were not ported
or changed. Fixture setup runs locally, not through the browser schema.

**Acceptance evidence:** two independent Chrome 152 browser contexts joined as
home/away and synchronized revision 0. Home moved (5,7) → (6,7); both observed
revision 1, movement used 1/6 and unchanged team resources. Wrong-role, illegal
and stale requests were rejected; accepted retry returned `duplicate:true` with
no second movement or broadcast. Engine tests compare the entire serialized
GameState around rejection/retry. [Screenshots, sanitized requests/responses,
exact image identity, commands, failures and limits](verification/m1a/README.md).

**Exact successful checks:** root PowerShell commands were
`./containers/local/setup.ps1`,
`docker compose -f containers/local/compose.yaml build server`,
`docker compose -f containers/local/compose.yaml up -d --wait`,
`./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserMatchAdapterTest,ServerCommunicationWorkTest,ShadowingTest' -Offline`,
`./tools/build.ps1 install -Offline`, `./tools/build.ps1 verify -Offline`,
`./tools/test-tooling.ps1`, and `git diff --check`. In `browser-client`,
`npm install --no-audit --no-fund`,
`npm install --save-dev --save-exact playwright@1.62.1 --no-audit --no-fund`,
`npm test`, `npm run build`, `npm run dev`, and a second terminal's `npm run demo`
completed the frontend/demo checks. Docker PATH and rerun instructions are in the
[client guide](../../browser-client/README.md). The final demo was repeated after
refreshing the image from final source; it passed.

| Check | Result |
|---|---|
| Focused BB2025/adapter/queue selector | 11 passed, no failures/errors |
| Required Java 8 clean install | All 8 reactor projects; 367 reported, 366 passed, 1 existing disabled; 2m37s |
| Required Java 8 clean verify | Same 367/366/1, no failures/errors; 2m25s |
| Tooling checks | 9 passed |
| Browser runtime schema/revision tests | 4 passed |
| TypeScript/Vite build | Passed |
| Actual two-browser demo | Passed: exactly one accepted engine move/broadcast, both revision 1 |

Changed areas: new `browser-client/`; local browser adapter/socket/servlet and
startup registration; existing queue work seam; two focused test classes;
container image `3.4.0-m1a.1` plus mounted home/away secrets; change-list feature
entry; README/protocol/evidence/status documents. No unrelated changes were present
at entry, and existing database/backup volumes and credentials were preserved.

**Resolved issues:** sandbox Docker/npm/ClassGraph access required authorized host
execution; no global tool install. Strict-parser tests found a malformed-version
exception, then passed after moving parsing before mutation. The first browser
build needed Vite CSS types. Review found missing resource DTO validation; it was
added and tested. These failures/corrections are recorded in the evidence report.

**Limits:** in-memory synthetic fixture only; no persisted browser match, public
identity provider or durable dedupe. Browser fixture game ID -1 remains outside
legacy cache/JDBC. It resets only with local server restart. History caps at 256
IDs without evicting accepted requests; new IDs fail closed. Unexpected engine
failure quarantines the fixture and closes the socket; recovery is not claimed.
Block/decision/reconnect are M1b. Renderer/asset fault injection, 1,000-action
metrics, slow-client robustness and protocol closeout are M1c. Java 21/public
Jetty/TLS/cloud/hosted-CI execution remain unverified. The healthy stack and Vite
loopback server were left running at browser revision 1; demo browser contexts
closed after evidence capture. No commit, push or deployment.

**Next M1b action:** read `browser-client/protocol.md`, this M1a evidence, and
`ffb-statetest/.../BlockTest.java`. Add controlled local Both Down fixtures and
server-owned choice DTOs, then demonstrate engine parity and full pending-choice
resync after disconnect with correct actor/revision and idempotent choice retries.
Keep dice and scenario-loading controls outside the browser protocol. Do not
claim M1 complete until M1b and M1c acceptance are separately evidenced.

### M1b implementation results — 2026-09-07

Controlled local startup fixtures now pause the BB2025 engine at Both Down,
with/without attacker Block and with home/away choice ownership. Server-owned
DTOs project prompt ID, owner, revision and all options. Both browsers receive
the full current state after rejoin; actor-scoped exact choice retries return
the retained accepted result without rerunning the engine or broadcasting again.
Dice/scenario loading remain local JVM configuration and test setup only.

All four variants passed independent `BlockTest`-style engine oracle checks and
live two-browser acceptance on `ffb-server:3.4.0-m1b.1`. The live runs withheld
the chooser's result and resolved snapshot, rejoined it, and explicitly retried
the original request. Both views agreed at revision 1; the observer received
exactly one resolved snapshot. The existing movement browser demo also passed.

Validation: 17 focused Java tests; 5 browser decoder tests; TypeScript/Vite build;
Java 8 clean install and clean verify, each with 370 passed / 1 existing disabled /
zero failures/errors across eight reactor projects. Independent ownership/retry
review found no actionable issues. See the [separate M1b acceptance table, wire
traces, screenshots, commands and limitations](verification/m1b/README.md).

The existing uncommitted M1a work was preserved. No commit, push, deployment or
database reset. The local JVM/database are healthy with a fresh default
`BOTH_DOWN` pending at revision 0; the loopback browser development server remains
available. Request history remains bounded and in memory; JVM restart creates a
new match lifetime. These fixtures do not constitute a general playable match.

**Next action at M1b closeout (historical): M1c** — separately evidence renderer/asset failure paths, bounded
repeat-action metrics, queue/history/slow-client behavior and protocol closeout.
Do not mark overall M1 complete until that acceptance and report are reviewed.

## M1c robustness and overall M1 closeout — 2026-09-07

**M1c and overall M1 are complete. Next milestone: M2.** The independent
review approved concurrency, ownership, ordered delivery, cleanup, idempotency,
measurement methodology and the final evidence-to-conclusion mapping. All nine
original M1 acceptance rows are accounted for in the separate
[M1c report](verification/m1c/README.md); historical M1a/M1b records are preserved.

M1c adds bounded browser admission and ordered asynchronous delivery with
failure/overflow disconnect and resync, actual diagnostic asset loading and
labeled fallbacks, actionable WebGL initialization failure with tested reload
recovery, executable wire fixtures, and operator-only fixture lifecycle controls.
The Java 8 BB2025 engine remains authoritative. Movement, all four controlled
choice/reconnect variants, failure demonstrations, history capacity and healthy
peer isolation passed against the final local image `ffb-server:3.4.0-m1c.1`
(identity recorded in the report), including fresh isolated-network verification.

The measured run kept the JVM and browser processes running across 100 fixture
lifetimes: 1,000 submissions comprised 600 mutations, 200 rejections and 200 exact
retries, with 100 reconnects. Accepted action-to-render submission p95 was
23.40 ms; reconnect p95 was 50.20 ms, with zero failures/timeouts. Browser memory
fell after cleanup; JVM RSS showed a late plateau but did not return to baseline.
These are observed finite-run results with enforced resource bounds, not proof
of absence of every leak or GPU presentation timing.

Final Java verify reported 386 tests: 385 passed and one existing disabled;
focused regressions, browser tests/build and required install also passed.
No commit, push, deployment, credential replacement or volume deletion occurred.
The final local stack is healthy, Vite remains on loopback port 5173, the server
on loopback port 22227, and the fresh default BOTH_DOWN fixture has revision zero,
zero history and zero active browser connections. The filesystem-only operator
mailbox remains explicitly enabled. M2 work has not started; follow the roadmap's
catalog/team validation, frozen roster data, saved teams and match ownership scope.

### M2b saved-team round trip — 2026-09-08

**M2b implemented and validated. M2 remains incomplete.** The browser team route
now creates, lists, loads, edits, saves and imports/exports immutable validated
documents through a saved-team service/repository and MariaDB schema migration 002.
Server validation recomputes every save/import and load; optimistic document
versions prevent stale replacement. Historical catalogs remain visible and locked,
with explicit migration-required or unavailable status and no automatic migration.

Final evidence: 53 focused Java regressions; 9 tooling checks; Java 8 offline
install and verify across all eight projects (409 tests, zero failures/errors/skips);
17 browser tests, schema checks and production build; actual MariaDB rollback/CAS/
lost-acknowledgement reconciliation; live browser save-load-export-import and
complete-document equality after JVM stop/start. Independent final review approved.
See [changed files, exact commands, failures, screenshots and limits](verification/m2b/README.md).

Existing M2a working-tree changes, credentials and database/backup volumes were
preserved. No commit, push, deployment, reset or disabled tests. The final local
M2b server and database are healthy. Saved teams are not match-ready; public
accounts and match ownership remain out of scope. Schema rollback/reset boundaries
are documented in [the migration guide](../../containers/local/saved-team-migration.md).

**Next slice: M2c — match creation, frozen roster data and role ownership.**

### M2c durable match preparation — 2026-09-08

**M2c implemented and validated. Overall M2 is not declared complete solely by this slice.** Either local identity can create an explicitly invited match using an owned saved-team revision. Persisted roles, revalidated immutable rosters, atomic optimistic writes and durable retries survive JVM restart. The final state is AWAITING_SETUP; no setup/gameplay is initialized.

Final evidence: 77 focused Java tests, 9 tooling checks, clean Java 8 offline install/verify across eight projects (433 tests, zero failures/errors/skips), 20 browser tests and production build, mounted error/reconnect checks, actual MariaDB concurrency/rollback/lost-acknowledgement tests, and two-browser reversed-role/source-edit/import/restart demonstration. Independent final review approved. Initial failures and corrections are recorded honestly.

See [M2c changed files, exact commands, evidence and M2 assessment](verification/m2c/README.md), [contract](../../browser-client/prepared-match.md), and [migration/rollback boundaries](../../containers/local/prepared-match-migration.md). Existing M2a/M2b work, credentials and volumes were preserved. No commit, push or deployment occurred.

Combined evidence supports bounded local M2 acceptance. Public accounts, credential lifecycle and production TLS remain outside this slice. M3 must enforce persisted membership on every setup/gameplay action and keep fixture credential labels outside product authorization. Full setup/kickoff/gameplay and in-progress recovery remain undelivered.

### M3a activation and legal setup ? 2026-09-09

**M3a implemented and validated.** Revision-3 activation initializes the existing engine once from frozen teams; every introduced operation uses persisted membership. Browser pre-match choices, placement/reserves and native legal setup confirmation reach READY_FOR_KICKOFF for both teams. Reversed creator identity, source-edit isolation, exact retry and reconnect were demonstrated in two browser contexts. A real JVM restart returns SESSION_UNAVAILABLE without reinitializing an activated match. No in-progress recovery is claimed.

Final evidence: 85 focused Java tests, 9 tooling checks, offline clean install/verify across eight projects with 441 tests and no failures/errors/skips, 27 browser tests, production build, actual MariaDB activation fault/race checks, two-browser setup and final-image restart checks. Independent review approved. See [M3a evidence, exact commands, failures, final live match and limits](verification/m3a/README.md), [setup protocol](../../browser-client/setup.md), and [migration guide](../../containers/local/setup-activation-migration.md).

Combined M2 evidence now includes genuine frozen-team engine integration and persisted-role setup authorization. Public accounts/credential lifecycle/TLS remain outside bounded local acceptance. Next: kickoff execution and subsequent M3 gameplay/completion work. No commit, push, deployment, volume reset or credential change occurred.

### M3b kickoff and core turn actions - 2026-09-09

M3b implemented and validated for the frozen catalog. Server-issued typed actions drive native kickoff, movement/standing, dodge/rush decisions, blocks/pushes/follow-up, blitz, rerolls, turnovers and end turn. Persisted roles and revision/request identity checks precede engine execution. Same-JVM reconnect restores pending decisions.

Evidence: 98 focused Java tests, 9 tooling checks, offline clean install/verify across eight projects (454 tests, zero failures/errors/skips), 29 browser tests/build, and final-image two-browser kickoff/block-decision reconnect/retry with four actual moves over four completed turns. Independent core review approved. See [exact evidence and failures](verification/m3b/README.md) and [supported/remaining controls](../../browser-client/core-turns.md). Complete-match UX and durable in-progress restart recovery remain later M3 work. No commit, push, deployment, volume reset or credential change occurred.
