# R2 local durable engine recovery acceptance

Final format 2 passed the relevant reactor, browser contracts/build, schema-4-copy
migration, and real SIGKILL/restart checks with two Chrome clients. This evidence
accepts the bounded local R2 behavior; it does not establish public-service,
identity-provider, capacity or backup/disaster-recovery readiness.

## Contract and compatibility

See [the recovery policy](../../../../containers/local/recovery.md) for the private
artifact, staged activation, checkpoint-before-ack, CAS/commit ambiguity, terminal
reconciliation and rejection contracts. Recovery format 2 pins runtime
`ffb-3.4.0-bb2025-r2.2`, engine `ffb-3.4.0-bb2025-m3d.1`, frozen Human catalog
`bb2025-human-2026-09-08.1`, replay 1 and browser JSON 1. Database marker 5 adds
only the recovery table. Native rules and replay serializers are unchanged.

Intentional adaptations are per-match server-seeded HMAC dice state for new
recoverable lifetimes, normalization of explicitly identified native map/set
properties, stable role/id action presentation, and schema-5 saved-team support.
Ordered engine stack, dice, reports and replay events are preserved. Membership
checks precede recovery access and retries; the single mutation worker is unchanged.

No active runtime was upgraded. Java 8/M3d on 22227, R1 on 22228, and the initial
format-1 R2 trial on 22229 remain running with their own storage. Final R2 uses
`ffb-local-r2b`, port 22230 and separate database/backup volumes. The initial trial
exposed cross-JVM unordered collection serialization and rejected its checkpoint;
its unfinished match `330309d2-6646-307c-95e5-c6b474bd46f9`, image, private checkpoint
and failed evidence under `process-run-*` are retained. Format 2 rejects format 1;
there is no migration or reinterpretation of that match.

## Environment and checks

Windows 11 amd64; Eclipse Adoptium Temurin JDK **21.0.11+10-LTS**; Maven **3.9.9**.
Container runtime is Temurin **21.0.11+10-LTS**, Jetty **12.1.13**, MariaDB **11.8.9**,
JDBC **3.5.8**. Exact image used for every final kill:
`sha256:a167432242b8b2764fdf8c91d7228dfabbef8a471f039338e93c14af6fc997b8`.
Compose/Dockerfile pin base image digests. Loopback-only binding, existing local
credentials, read-only container, 768 MiB maximum Java heap, and the existing
single communication worker remain in use. New database packet limit is 64 MiB.

Browser tooling: Node **24.19.0**, React **19.2.8**, Vite **8.2.2**, TypeScript
**7.0.2**, Playwright **1.62.1**, real Chrome **152.0.7977.83**, two independent
1440x1080 browser contexts at `http://127.0.0.1:5173`.

Commands from repository root unless noted:

```powershell
powershell -ExecutionPolicy Bypass -File tools/target-build.ps1 test -Module ffb-statetest -Test RecoveryApplicationTest,RecoverySessionTest,RecoveryScenariosTest -Offline
powershell -ExecutionPolicy Bypass -File tools/target-build.ps1 test -Module ffb-server -Test RecoveryDiceTest,RecoveryNativeJsonTest,JdbcRecoveryRepositoryTest,LocalSchemaTest,JdbcSavedTeamRepositoryTest -Offline
powershell -ExecutionPolicy Bypass -File tools/target-build.ps1 verify -Offline
$env:R2_RECOVERY_INSPECT=(Resolve-Path .tools/r2-prematch-retained.json).Path
powershell -ExecutionPolicy Bypass -File tools/target-build.ps1 test -Module ffb-statetest -Test RecoveryArtifactInspectionTest -Offline
Remove-Item Env:R2_RECOVERY_INSPECT
# browser-client directory:
npm.cmd test
npm.cmd run build
npm.cmd run dev
```

Final reactor: **522 tests, zero failures/errors, one optional inspection test
skipped**, all eight modules successful. The optional retained-artifact inspection
was also run explicitly with `R2_RECOVERY_INSPECT=.tools/r2-prematch-retained.json`
and passed; ordinary runs do not require that private fixture. Four native scenario
tests additionally restore checkpoints in a separate Java process and require
identical artifact bytes. Focused application tests cover authorization ordering,
missing checkpoints, staging failure and both outcomes of an ambiguous write.
Repository/schema tests cover CAS, transaction errors and migration rejection.
Browser: **38 tests passed**, production build passed with the existing Vite
large-chunk warning. See [verification logs](logs/). Host pauses affected some early
run timings; these are correctness results, not performance measurements.

The one-time migration command `node tools/r2-migration-copy.mjs` refuses existing
R2b volumes, takes a read-only schema-4 dump from R1, and imports into an empty new
database. After first server startup, `node tools/r2-migration-copy.mjs verify`
confirmed marker 5, zero initial recovery rows, and identical hashes of all saved
team/prepared/completed document bytes. See [copy manifest](migration-format2/schema4-copy.json)
and [migration result](migration-format2/schema5-verified.json). These commands
must not be rerun against the retained accepted project as a reset mechanism.

## Real process-kill results

From `browser-client`, with the final container healthy and Vite running:

```powershell
$env:M4_WS_ENDPOINT='ws://127.0.0.1:22230/browser/v1'
$env:M4_PROCESS_KILL='1'
$env:M3_CREATOR='home'
$env:M3_EVIDENCE='../.notes/overhaul-analysis/verification/r2/process-format2-run1'
node test/full-match-demo.mjs
node test/r2-rejection-demo.mjs
```

Use a new evidence directory for a new run; preserve these accepted artifacts.
The driver validates the fixed container/image, sends `docker kill --signal KILL`,
requires exit 137 without OOM, and starts the same container/image. It never uses
graceful reconnect as a substitute for termination. The fixture uses frozen Human
teams: home eleven linemen and zero team rerolls; away an Ogre plus ten linemen
and four rerolls. A noncaptain home player blocks the Ogre to reach a native
defending-team decision. Test-only native fixtures provide additional deterministic
boundary coverage; live clients cannot supply dice.

| Kill point | Revision | Dice counter | Two-client exact restoration | Retry |
| --- | ---: | ---: | --- | --- |
| Pre-match | 0 | 4 | Pass | No prior action |
| Placement, lost acknowledgment | 3 | 5 | Pass | Exact duplicate |
| Defending-team decision | 30 | 15 | Pass | Exact duplicate |
| Drive transition | 65 | 16 | Pass | Exact duplicate |
| Half transition | 100 | 22 | Pass | Exact duplicate |
| Pending terminal commit, lost acknowledgment | 142 | 32 | Pass | Exact duplicate |
| Terminal committed | 142 | 32 | Pass | Exact duplicate |

Every retry left full checkpoint bytes, dice counter and request history unchanged.
Both projected states were compared exactly, including decision, actor, resources
and authoritative revision. The terminal-pending test holds a row lock on only its
synthetic prepared match, waits for the terminal checkpoint to commit while result
persistence blocks, then kills the server. Restart reconciles the result without
another engine command. The post-commit kill verifies the other terminal boundary.
Match `7d039e43-eb99-3eb1-8bbd-c457a4b16876` finished **1-0**, revision **142**, with
**143 replay events**. Both terminal checkpoints share SHA-256
`1f6d9c43ed9c0bdc739ab36c76eb6a4fd5bd0691a591fe78b35a1a6844a4044a`.

See [machine-readable kill results](process-format2-run1/process-kills.json),
per-kill artifact inspections, wire records and screenshots in that directory.
Private dice seeds are redacted in checked-in inspection evidence; hashes refer
to original private bytes. Raw backups remain under `.tools`, outside published
evidence. No private future randomness is exposed through browser DTOs.

Two additional newly created fixtures were corrupted only after preserving their
original private checkpoints: unsupported recovery version 99 with a recomputed
checksum, and modified revision with a stale checksum. Following SIGKILL/restart,
both clients received `RECOVERY_UNSUPPORTED` or `RECOVERY_CORRUPT` with null state.
Stored artifacts remained unchanged. See [rejection results](rejections/rejections.json)
and the four corresponding UI screenshots.

## Limits

This covers the declared frozen Human action surface and listed boundaries, not
every possible catalog/engine state. Unsupported or non-round-tripping states fail
closed. An unacknowledged command killed before its durable checkpoint may execute
again from the previous durable state; its uncommitted dice/resource use is not
retained. Recovery does not claim physical exactly-once instruction execution.
Native wall-clock timers retain their original start and include downtime; this
does not establish public timed-match operation. Resident capacity remains bounded
at 32; retention/cleanup and load measurements belong to R4. No credentials,
provider, external deployment or public readiness gate changed. The unrelated
working-tree workflow edit was preserved.
