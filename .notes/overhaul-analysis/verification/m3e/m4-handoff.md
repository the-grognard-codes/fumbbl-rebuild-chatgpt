# M4 handoff from integrated local M2/M3 acceptance

The accepted architecture remains Java/Maven with the authoritative BB2025 engine,
MariaDB/JDBC, React/Vite and versioned JSON WebSockets. Do not broaden the frozen
Human catalog while proving operational recovery. ADR-005 hosting is deferred;
there is no authorized public deployment, service/account provider or credential change.

## Starting state

Read the [M3e acceptance report](README.md), [disconnect contract](../../../../browser-client/disconnect.md),
[capability matrix](../../../../browser-client/action-coverage.md), accepted ADRs,
PRD/roadmap and schema-4 migration. The running server remains the paired M3d image;
M3e does not change its engine/replay schema. Host Java 8/Maven checks are green.
Both creator directions completed matches and persisted private replay. Completed
results, saved teams and prepared documents survive JVM restart; unfinished engines
explicitly do not. The database contains retained synthetic evidence rows, including
unfinished matches retired by the acceptance restart. Do not reset or delete them
as part of ordinary startup or migration.

## Bounded next workstreams and gates

1. **Durable in-progress engine recovery.** Define a versioned recovery artifact
   covering engine stack/dialog, frozen inputs, authoritative revision, dice state,
   accepted request history and pending terminal persistence. Preserve membership
   checks before every mutation/read/retry. Kill the process during pre-match,
   placement, a defending-team decision, drive/half transition and terminal commit.
   Restore exactly the same decision, actor and resources; replay an acknowledged
   and lost-ack request without a second engine execution. A graceful browser
   reconnect or completed-result read is insufficient evidence.
2. **Runtime and transport maintenance.** Retain the current Java 8 characterization
   reference while separately proving the accepted Java 21/supported Jetty direction.
   Revalidate dependencies, framing, origin checks, queues, timeouts and async
   callbacks. Do not upgrade active matches in place without format/engine parity.
3. **Real identities and route policy.** Choose and obtain authorization for account
   provider and credential lifecycle. Replace two operator-provisioned local bearer
   subjects with scoped accounts; add expiry/revocation, safe reconnect and invitation
   semantics. Require TLS and validated production origins. Test player, spectator
   (if introduced), admin, result/replay and legacy route projections independently.
4. **Capacity, retention and completed-session release.** Current resident limit is
   32 lifetimes including completed/failed engines. Release completed engines only
   after proving durable result/retry semantics cannot reinitialize or double-execute
   them. Bound retained request/replay data and define abandoned-match policy. Measure
   heap/RSS, queue delay, p95 action/reconnect latency and replay size on a declared
   workload; M3e fault counts are functional checks, not a load benchmark.
5. **Backups and operational failure.** Back up schema/data together with compatible
   engine/catalog versions; restore into a separate approved environment and compare
   membership, frozen teams, results and pending decisions. Exercise database outage,
   partial migration, full disk, ambiguous COMMIT, restart and rollback. Schema-4
   routine restart and JDBC fault injection do not certify backup restoration.
6. **Desktop/renderer and content release checks.** Current match controls are DOM;
   M1 Pixi scenarios remain separate. Perform actual 100%/200% browser zoom, 1280×720
   and 1920×1080 layout checks, current Chrome/Edge/Firefox/Safari and screen-reader
   testing. Preserve keyboard and fallback controls. Review asset provenance before
   public use; neutral tokens and local catalog evidence are not an artwork license audit.

M4 is incomplete. Public-service acceptance requires all applicable roadmap gates:
process-failure recovery at a decision, role/projection tests, measured load envelope,
successful backup restore and recorded content provenance. Runtime, authentication,
operations and release acceptance should be recorded independently; no check above
authorizes publishing or provisioning an external service.
