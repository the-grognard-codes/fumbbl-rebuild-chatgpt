# M4 workstreams — implementation prompt reference

Status: planning reference produced from the accepted roadmap, PRD, ADRs, kickoff record, and M3e handoff on 2026-09-11. It breaks M4 into independently reviewable tranches; it does not authorize deployment, account-provider procurement, credential changes, catalog expansion, or any deletion/reset of retained evidence.

## M4 outcome and dependency map

M4 is the public-service-readiness milestone. Its acceptance is not a single build or test run. It requires process-failure recovery at a pending decision, role/projection verification, a measured load envelope, a successful separate-environment backup restore, and recorded content provenance. Runtime, authentication, operations, and release acceptance must be independently recorded.

```text
R1 Runtime/transport ─────┬──> R3 Identity and route policy ──┐
                           ├──> R4 Capacity and retention ─────┼──> R6 release evidence
R2 Durable recovery ──────┼──> R5 Backup/operational failure ─┤
R3 Identity/route policy ─┘                                   │
R6 Renderer/content checks ───────────────────────────────────┘
```

Begin R1 and R2 in parallel. R3 needs an owner-approved account-provider and credential-lifecycle decision before production integration. R4 must use the recovery and runtime behavior actually delivered; R5 must back up and restore the compatible versions introduced by R1/R2. R6 is independently executable, but its evidence joins the final release record.

## Shared prompt preamble — include in every tranche request

> You are implementing one bounded M4 tranche in the FUMBBL rebuild. Preserve unrelated working-tree changes; do not reset, delete, truncate, or recreate existing database/backup volumes or retained synthetic evidence. The current accepted baseline is Java/Maven, the authoritative BB2025 Java engine, MariaDB/JDBC, React/Vite, and versioned JSON WebSockets. The frozen Human catalog, engine behavior, and replay schema are out of scope unless this tranche explicitly versions and validates a compatibility boundary. Keep server authority: membership/role checks precede every mutation, read, and retry; client input never determines dice, legality, or private state. Maintain one-at-a-time match mutation unless measured evidence justifies a separately reviewed concurrency change.
>
> Do not publish, deploy publicly, provision a cloud account, select/pay for an account provider, or change credentials. Preserve loopback/local safety until the required authorization and TLS/origin work are accepted. Do not upgrade active matches in place: drain them or retain a compatible runtime until completion, and demonstrate format/engine parity before any transition.
>
> Start by reading the M3e acceptance report, `browser-client/disconnect.md`, `browser-client/action-coverage.md`, schema-4 migration material, accepted ADRs, and the M4 handoff. Work in a small, reviewable slice. Add focused characterization/contract tests and exact evidence for all changed behavior. Run the narrowest meaningful checks first, then the risk-appropriate Maven/browser/database checks. Record commands, versions, fixtures, configuration, result data, and known limits. Do not claim public-service readiness or another tranche's gate from local-only evidence.

## R1 — Runtime and transport maintenance

**Objective:** replace the unsupported Java 8 server runtime/Jetty 9.4 direction with a supported runtime and Jetty line while preserving the Java 8 characterization baseline as a comparison oracle.

**Scope:** build/toolchain configuration, dependency compatibility inventory, Jetty 12.x integration, WebSocket framing/origin/queue/timeout/async-callback checks, and a controlled runtime compatibility boundary. Keep MariaDB/JDBC unless a demonstrated driver/server incompatibility requires a separately documented update.

**Must deliver:**

- A reproducible Java 8 baseline result and a separately reproducible target-runtime build/test result; record exact JDK vendor/version and dependency versions.
- An upgrade to a supported Jetty 12.x line, with tests for valid/invalid framing, decoded-size limits, origin enforcement appropriate to the environment, bounded slow-client queues/resync, timeouts, asynchronous callbacks, and clean close/failure behavior.
- A compatibility policy for active matches, including engine/catalog/replay/recovery artifact versions and a no-in-place-upgrade rule until parity is proven.
- Dependency and API changes documented as intentional compatibility adaptations, not silent behavior changes.

**Acceptance evidence:** existing behavior is characterized on Java 8; the target runtime passes the relevant reactor, browser-contract, and live local WebSocket checks; no duplicate action/dice/resource use occurs under retry; and no active match is silently reinitialized or reinterpreted.

**Out of scope:** durable restart recovery itself (R2), real-provider authentication (R3), capacity benchmarks (R4), external deployment, and catalog expansion.

## R2 — Durable in-progress engine recovery

**Objective:** recover an unfinished, authoritative match after a process kill without altering the pending decision, actor, resources, dice state, or request semantics.

**Scope:** versioned recovery artifact and repository/lifecycle behavior. The artifact must cover engine stack/dialog, frozen inputs, authoritative revision, dice state, accepted request history/idempotency information, and pending terminal persistence. Keep recovery data distinct from browser DTO and replay-format versions.

**Must deliver:**

- A defined recovery format and compatibility/rejection policy; pin engine, catalog, replay, and recovery versions.
- Atomic or explicitly recoverable persistence boundaries, including terminal commit ambiguity; preserve membership checks before every mutation/read/retry after restoration.
- Process-kill tests during pre-match, placement, defending-team decision, drive/half transition, and terminal commit.
- Evidence that restoration presents precisely the original decision, actor, resources, and revision, and that an acknowledged/lost-ack request is replayed without a second engine execution.

**Acceptance evidence:** real process termination—not graceful browser reconnect or completed-result read—followed by restart and two-client verification for every listed failure point. Include persisted/recovered artifact inspection and failed-recovery behavior for unsupported/corrupt versions.

**Out of scope:** generalized event-sourcing rewrite, historical replay import, capacity cleanup policy (R4), and public identity provider work (R3).

## R3 — Real identities and production route policy

**Prerequisite:** owner authorization selecting an account provider and credential lifecycle. Until then, finish only provider-neutral interfaces, local test doubles, and policy tests; do not create provider accounts or credentials.

**Objective:** replace operator-provisioned local bearer subjects with scoped, revocable identities and a route/recipient policy suitable for public exposure.

**Scope:** player/invitation/reconnect semantics; optional spectator policy; admin/result/replay/legacy route projections; expiry/revocation; TLS and validated production-origin policy; safe log/name rendering.

**Must deliver:**

- Explicit principals, scopes, expiry/revocation, invitation acceptance, and reconnect behavior, with no token or private account/team data in logs.
- A route-projection matrix covering player, spectator if introduced, admin, result/replay, and legacy endpoints. Each route must state authentication, authorization, recipient projection, mutation permissions, and failure response.
- TLS-required public profile and exact origin validation; local development remains explicitly separate and loopback-safe.

**Acceptance evidence:** independent positive and negative tests for each role/route projection, including cross-match and stale/revoked credentials, reconnect, duplicate intents, and attempts to read or mutate out-of-projection state.

**Out of scope:** selecting or purchasing a provider without authorization, public deployment, and expanding the spectator feature beyond an authorized read-only projection.

## R4 — Capacity, retention, and completed-session release

**Objective:** replace the current 32-resident-lifetime ceiling with a bounded, observable session/retention policy that cannot double-execute durable work.

**Scope:** release completed/failed engines, retained request/replay bounds, abandoned-match policy, operational metrics, and declared-workload measurement.

**Must deliver:**

- A documented lifecycle for active, completed, failed, abandoned, and recoverable sessions. Completed engine release occurs only after durable result/retry behavior proves it cannot reinitialize or execute twice.
- Explicit retention/eviction limits for request history and replay/recovery data, with correct behavior at limits and no silent loss of an action awaiting recovery.
- A declared workload, machine/JDK/browser/database configuration, and measured heap/RSS, CPU where practical, queue delay, p95 accepted-action and reconnect latency, snapshot/replay size, failures, and cleanup behavior.

**Acceptance evidence:** repeatable run results—not M3e functional-fault counts—with enough lifecycle pressure to exercise release, eviction, slow clients, reconnect, retry, and abandoned sessions. State the supported envelope and the chosen rejection/backpressure behavior; do not invent production capacity claims.

**Out of scope:** a per-match actor/microservice rewrite, host-sizing purchase, and changing recovery semantics without R2 review.

## R5 — Backups and operational-failure recovery

**Objective:** prove that a compatible backup can restore an M4 match state, including one paused at a decision, into a separate approved environment.

**Scope:** backup manifest/adapter, restore procedure, compatibility versioning, migration failure/rollback boundaries, and failure exercises: database outage, partial migration, full disk, ambiguous COMMIT, process restart, and rollback.

**Must deliver:**

- Backup coverage for schema/data and compatible engine/catalog/replay/recovery/runtime version identifiers; secrets are referenced by secure configuration, never captured as evidence.
- A reproducible restore into a separate local/approved environment that compares membership, frozen teams, results, pending decisions, versions, and recovery/idempotency behavior.
- Clear procedures and tested behavior for database outage, partial migration, full disk, ambiguous commit, restart, and rollback. A schema-4 restart or JDBC fault injection alone is insufficient.

**Acceptance evidence:** a retained backup restored separately, with a paused-decision match and completed result verified by both users and direct data comparison. Record restore time, data/version checks, observed failures, and the exact rollback boundary.

**Out of scope:** cloud-provider selection, production backup credential provisioning, unsupported cross-version restores, and destructive reset of existing evidence.

## R6 — Desktop renderer, accessibility, and content-release checks

**Objective:** establish that the present DOM-controlled desktop client is robust and accessible across declared browsers/layouts, and that public-facing assets have recorded provenance.

**Scope:** actual browser checks at 100% and 200% zoom, 1280x720 and 1920x1080 layouts, current Chrome/Edge/Firefox/Safari, screen-reader flows, keyboard controls, DOM fallbacks, and asset provenance recording. M1 Pixi/WebGL behavior remains a distinct test surface where relevant.

**Must deliver:**

- A browser/accessibility matrix with tested versions/platforms, layout and zoom evidence, keyboard paths for every current match control, and screen-reader evidence for state/prompt/error changes.
- Defects fixed or documented as explicit release blockers; preserve labeled fallback controls and an actionable renderer failure path instead of a blank game view.
- An asset inventory with source, license/permission/provenance status, and public-use disposition. Neutral tokens/local catalog evidence do not constitute an artwork license audit.

**Acceptance evidence:** recorded execution of the matrix plus provenance review. Evidence must distinguish the current DOM match surface from M1 Pixi tests and must not claim that Pixi automatically provides a canvas or accessibility fallback.

**Out of scope:** a visual redesign, new roster artwork, mobile delivery, or a public art-license conclusion without the necessary owner/legal evidence.

## Runtime decision — Java 8, 21, and 25

M4 explicitly covers a server-runtime upgrade from Java 8 to Java 21: ADR-001 accepts Java 21 as the new server target, and the M3e handoff calls for separately proving the accepted Java 21/supported-Jetty direction. The current Java 8/Maven checks are a characterization reference, not the M4 end state. Jetty 12 requires Java 17+, so remaining on Java 8 is incompatible with the accepted maintenance direction.

Java 25 is now the newer LTS and is the stronger long-term production target if this project chooses a currently maintained OpenJDK distribution. It is not necessary to unlock the architectural work: Java 21 already supports Jetty 12 and supplies the major modernization features most relevant here (records, pattern matching, virtual threads, structured concurrency as a preview API, and modern TLS/GC/runtime improvements). Java 25 adds newer language/runtime/platform features, but none is required for M4's turn-based service; adopt features only after profiling and compatibility needs justify them. The concrete advantage is lifecycle: Oracle identifies Java 25 as the latest LTS, while its free Java 21 updates are scheduled to move from NFTC to OTN terms after September 2026. Distribution-specific support/licensing differs, so record the chosen vendor and support policy rather than relying on Oracle dates for every distribution.

**Recommendation:** use a staged *verification* upgrade, Java 8 -> 21 -> 25, but do not make Java 21 a long-lived production deployment waypoint. First establish Java 21/Jetty 12 parity exactly as the accepted ADR requires; then run the same full compatibility matrix on Java 25 and make Java 25 the released M4 runtime if it passes. This isolates source/API/Jetty migration failures from 25-specific runtime changes, preserves a dependable diagnostic path, and avoids finishing M4 on an older LTS immediately before its Oracle free-update transition. Keep source/bytecode targeting intentional: compile for the chosen deployment JDK, do not claim Java 8 runtime compatibility after adopting Jetty 12, and pin the exact JDK in CI/container/local evidence.

Primary references: [ADR-001](04-technology-and-decisions.md#adr-001-java-backend-modernize-runtime-independently), [M4 handoff](verification/m3e/m4-handoff.md), [roadmap](06-roadmap-and-prototype.md), [Oracle Java downloads](https://www.oracle.com/java/technologies/downloads/), and [Oracle's Java 21 licensing transition notice](https://blogs.oracle.com/java/jdk-21-approaches-end-of-permissive-license).
