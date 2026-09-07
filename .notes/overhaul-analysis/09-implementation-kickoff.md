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
- M0a/M0b/M1 application implementation not started. Subsequent owner-authorized tooling setup installed Docker Desktop and verified an isolated MariaDB container; see [container environment verification](10-container-environment.md). No game-server image, Compose application stack, schema migration or browser feature has been implemented.
