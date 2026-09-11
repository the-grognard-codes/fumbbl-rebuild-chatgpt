# R1 — Java 21 / Jetty 12 local compatibility evidence

This tranche replaces the server build/runtime direction with Temurin Java 21
and Jetty 12.1.13. It preserves the Java 8 comparison oracle, BB2025 engine,
catalog, MariaDB/JDBC and schema-4 replay contracts. Evidence is local only;
R2 recovery, R3 identities/TLS, R4 capacity and public-service readiness are not
accepted by these results. No cloud account, credentials, public deployment,
commit or push is part of this work.

The declared local R1 checks passed: Java 8 verify 485/485, target Java 21 verify
491/491 (zero failures/errors/skips), 91 matching native traces and 91 mounted
browser traces. The isolated complete match included 75 lost-acknowledgement /
explicit-retry cycles without duplicate peer mutation broadcasts. No active
runtime cutover was performed.

Starting revision: `56c19c80070861e89f465f1989be403d3aad0ffd`.
The pre-existing untracked `.notes/overhaul-analysis/m4-workstreams-reference.md`
was preserved. M3e acceptance, disconnect/action coverage, schema-4 migration,
accepted ADRs and M4 handoff were read before implementation.

## Reproducible boundaries

- [Java 8 oracle](java8-baseline/README.md): separate untouched source archive,
  Temurin `1.8.0_504-b01`, Maven `3.9.9`, Jetty `9.4.0.v20161208`.
  Focused characterization and clean verify passed: 485 tests, zero failures,
  errors or skips. It remains a comparison oracle, not the current server build.
- Target: Eclipse Adoptium Temurin `21.0.11+10-LTS`, Maven `3.9.9`, Jetty
  `12.1.13`, Mockito `5.23.0`. The server compiles with release 21; shared engine
  and desktop modules retain source/target 8. Target launcher validates exact JDK
  vendor/patch and restores process environment; CI separates frozen baseline
  from current target. Hosted CI has not been run here.
- [Runtime policy](../../../../containers/local/runtime-compatibility.md): no
  resident engine moves between JVMs. Drain or retain its runtime until completion.
  Engine `ffb-3.4.0-bb2025-m3d.1`, catalog/preset version
  `bb2025-human-2026-09-08.1`, schema 4, protocol v1 and replay format 1 remain
  unchanged. There is no durable unfinished-engine recovery artifact in R1.

## Intentional compatibility adaptations

[Jetty's support table](https://jetty.org/download.html) lists 12.1 as supported.
The [migration guide](https://jetty.org/docs/jetty/12.1/programming-guide/migration/11-to-12.html)
documents the EE-specific artifacts. R1 uses the supported EE8 implementation
to retain `javax.servlet` contracts; it does not introduce a servlet/application
rewrite. Session imports move to `org.eclipse.jetty.ee8.websocket.api`, with an
explicit servlet-container initializer. Most changed Java files only adapt imports.

Idle timeout values become `Duration`. Browser idle remains five minutes; legacy
command sessions retain their disabled idle timeout. The removed Jetty async-write
setting is replaced by the already-existing two-second delivery watchdog (100 ms
poll interval), keeping the original bounded single engine worker, 16 connections,
128 queued ingress tasks, 64 outbound messages and 256 KiB per connection.
Slow consumers close with 1013 and reconnect for a full snapshot; their uncertain
actions still require exact retry. Browser write callbacks retain ordered delivery.
Completion is now idempotent so repeated/late callbacks cannot advance another write.
Legacy `sendBytesByFuture` becomes asynchronous `sendBytes` with failure cleanup.

Browser v1 explicitly rejects binary messages (1003), refuses compression
extensions, enforces the 16 KiB decoded UTF-8 message limit across fragments, and
requires exactly one allowed Origin (`http://127.0.0.1:5173` or
`http://localhost:5173`). Invalid UTF-8 closes 1007; unmasked/RSV frames close 1002;
oversize closes 1009. Failure cleanup releases admission once. These are transport
adaptations/hardening, not engine, dice, membership or replay changes.

[Target dependency tree](target-dependency-tree.txt) and
[baseline tree](java8-baseline/dependency-tree.txt) record transitive versions.
MariaDB server remains `11.8.9`, driver `3.5.8`; ClassGraph `4.8.98`, minimal-json
`0.9.5`, JUnit `5.5.0`, AWS SDK `1.12.272` and HttpMime `4.5.2` stay pinned.
Jetty brings its matching 12.1.13 EE8/core dependencies, servlet API `4.0.9`, ASM
`9.10.1`, and SLF4J `2.0.17`. Existing JDK-activated JAXB dependencies resolve on
Java 21. No broad dependency security/lifecycle audit is claimed. SLF4J reports
no provider; application DebugLog remains separate. Legacy/cloud adapters are
not accepted for public exposure by the local transport checks.

## Checks and evidence

| Check | Evidence / outcome |
| --- | --- |
| Frozen Java 8 focused and complete reactor | `java8-baseline/focused-host.log`, `verify-host.log`, suite CSV: passed |
| Target focused transport and engine/adapter/retry contracts | `target-focused-2.log`, `target-characterization.log`: passed |
| Actual loopback framing/origin/idle/callback contracts | `BrowserJettyContractTest`: 5 passed, including shortened test-only idle timeout after asserting production five-minute value |
| Queue bounds/watchdog/late callbacks | `BrowserMatchDeliveryTest`, `BrowserMatchSocketTest`, `BrowserMatchTransportTest`: passed; deterministic sink/worker fault injection, not capacity measurement |
| Target reactor install | `target-install-online.log`: all eight modules passed |
| Final target clean verify | `target-verify.log`, `target-test-suites.csv`, `target-test-totals.json`: eight modules, 491 tests, zero failures/errors/skips, 3m17s |
| Browser unit/build | `browser-tests.log`: 36 passed; `browser-build.log`: TypeScript/Vite passed, existing chunk-size advisory |
| Native trace parity | `engine-parity.json`, `parity-fixture.patch`, `java8-actions.json`, `target-actions.json`: exact comparison after bijective UUID normalization |
| Mounted target-native traces | `mounted-target.log`, `mounted-target/mounted-action-summary.json`: all 91 frames passed in two isolated browser contexts with reconnect/retry and observer synchronization |
| Actual JDBC fault contracts | `jdbc-match.log`, `jdbc-team.log`: rollback, CAS, durable retry and ambiguous commit reconciliation passed in isolated R1 MariaDB |
| Legacy API adaptation | `legacy-transport.log`: new isolated synthetic game 1 scheduled, loaded and both coaches joined using real HTTP/command WebSockets |
| Full isolated browser match | `isolated-complete-final/completed-match.json`: match `37123a5c-092b-3c38-bcb1-a3c118289acd`, 0–1, revision 135, 136 events; lost acknowledgement, explicit retry, stale/wrong-owner checks and transitions passed |
| Six-position live actions | `isolated-supported-second/core-turn-summary.json`: revision 64, four moves/turns, block decision reconnect/retry, hand-off/pass/throw-team-mate submissions passed |
| Live authorization | `authorization-summary.json`: unauthenticated operations denied, forged role/dice/fixture rejected; both subject states unchanged |
| Java 8 committed artifact on R1 | `legacy-replay-parity/summary.json`: result and all 139 events equal; terminal mutation rejected |
| Runtime/volume identities | `runtime-inventory.json`, image manifests, Java/database/tool versions: distinct local projects and retained reference JVM |

## Failures retained and limits

The first dependency fetch and Java 8 focused attempt failed under sandbox host
access restrictions; host reruns passed. An initially unquoted PowerShell `-D`
argument was corrected. The first container build captured a raw-frame test bug
in close-code decoding; corrected tests passed on host and Linux image. Target
offline install initially lacked the existing JAXB 2.3.3 artifact; online install
populated the local cache and passed.

Original 91-frame comparison found one random bounce in the argue-the-call test.
The ball-carrying fouler is sent off; the test had not supplied its D8 bounce.
The identical test-only patch pins north and asserts coordinate/consumption on
both runtimes. The frozen original baseline and logs are retained unchanged;
the patched Java 8 comparison uses a second source archive. Product dice/engine
were not changed and differing gameplay state is never normalized away.

The initial comparison Compose service shared the retained DB/backup. Review
rejected that configuration. Its sole match `0cf7de80-88cc-3a99-911c-b270ab7432e4`
completed (139 events); legacy replay parity was checked, then only that drained
R1 JVM stopped. Its container/rows/evidence remain. Delivered `compose.r1.yaml`
is standalone `ffb-local-r1` with new dedicated database/backup volumes/networks;
the original Java 8 JVM and existing volumes were not restarted or reset.

The first isolated full-match driver assumed a nonempty saved-team list and
timed out before activation; the driver now reconnects after creating its source.
The first six-position sample exhausted its bounded native hand-off setup attempts
(no transport exception); that failed sample and unfinished match are retained.
The second sample passed. This random driver is not exhaustive rules coverage.
Both unfinished sampled matches remain resident in R1; do not stop that JVM as a
cleanup step. Existing no-reinitialization tests also pass after simulated
application replacement: unavailable engines stay `SESSION_UNAVAILABLE`.

Chrome `152.0.7977.83`, Node `24.19.0`, npm version and Docker versions are
recorded alongside logs. Same-JVM resync/exact retry and completed-format parity
do not demonstrate R2 restart recovery, backup restoration, throughput, TLS,
provider identities, arbitrary browsers, or compatibility with every legacy route.
No active match was moved to a new engine or silently reinitialized.

Independent review approved the corrected isolated Compose configuration,
transport adaptations, parity normalization and evidence boundaries. Final
host/container tests remain the acceptance evidence; review is not a substitute.
