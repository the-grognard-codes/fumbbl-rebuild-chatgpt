# Migration roadmap and first prototype

Status: **proposed implementation sequence**. No prototype or overhaul code was created during this audit. Effort ranges are planning estimates for one experienced developer with AI assistance, including normal implementation and verification; they are not measured productivity or calendar promises. Asset production is estimated separately. See [technology decisions](04-technology-and-decisions.md) and [PRD](05-product-requirements.md).

## Milestones, dependency order and acceptance

| Milestone | Deliverable and dependency | Acceptance gate | Engineering effort |
|---|---|---|---:|
| M0: reproducible baseline | Pin build tool versions, retain test baseline, isolate local configuration/database/schema, curated fixtures; begins from this audit | A fresh local setup builds, starts without FUMBBL, accepts a local match; documented tests reproduce; no live service credentials needed | 3–7 developer-days |
| M1: browser proof | Small JSON/browser adapter and diagnostic 2D client over unchanged rules; depends on M0 | Two browser sessions see the same move and forced block result; wrong-role and duplicate intents rejected; reconnect restores pending decision | 8–15 days |
| M2: independent product services | Versioned catalog, team builder/validation, saved teams, match create/join/ownership, frozen roster data; depends on M1 | Valid teams round-trip; invalid imports fail clearly; editing a team cannot change active match; authenticated users cannot take opponent role | 12–25 days |
| M3: complete desktop match loop | Setup/kickoff, all supported action and reroll prompts, drive/half/end, result/replay and disconnect UX; depends on M1, integrates M2 | Supported starter catalog can complete matches end-to-end; engine/adapter/browser tests agree; disabled or disputed cases explicitly resolved or excluded | 25–50 days |
| M4: public-service readiness | Java/runtime and supported Jetty upgrade, secure route policy, persistence/restart proof, backups, logs/metrics, renderer and asset robustness; follows M1 and merges before public beta | Restore after process failure during a decision; role/projection tests pass; measured load envelope; successful backup restore; content provenance review recorded | 12–25 days |
| M5: broader content and mobile | More validated roster/skill combinations, richer artwork, landscape touch, responsive panels; depends on desktop evidence | Each added content set has declared tests; landscape play has reachable prompts and no hover-only actions | 15–30+ days, content dependent |

M0–M4 total **60–122 engineering days**, excluding dedicated art production, major rule defects, legal review, provider/account setup, unbounded roster expansion, and ongoing maintenance. These ranges assume a limited starter catalog and reuse of the current executor/database. They are not a quote for every BB2025 roster or complete platform parity. Re-estimate after M1; the protocol/prompt integration and roster catalog are the largest current uncertainties.

Art planning: 2–4 artist-days for style/scale tests, 8–20 for a coherent core UI/pitch/status set, and approximately 5–15 per initial roster for a modest sprite set with team/pose variants. Portraits, unique named characters and elaborate animations expand this substantially. These are independent estimates pending an art brief and rights review, not an inference from file counts. Prototype placeholders need no full art overhaul.

Hosting has no dollar estimate because concurrency, region, retention and availability targets remain unspecified. Use the worksheet in the technology report and measure the prototype before selecting a host. AI assistance does not remove review, integration, browser testing or asset consistency work.

## First prototype: a local authoritative browser match slice

**Hypothesis:** existing BB2025 steps can drive a browser through a narrow adapter without porting the rules or relying on FUMBBL services. The proof must include an asynchronous decision and reconnect, not just a static board.

**Include:** one isolated JVM instance and disposable database; two local development identities; synthetic fixture teams; a 26×15 logical board confirmed against source constants during implementation; select, legal move, block choice, controlled dice scenario, and reconnect. Use neutral 2D tokens, labels and simple overlays. Fixture teams are explicitly test scenarios, not certified legal exhibition rosters.

**Exclude:** public deployment, external auth provider, full roster editor, league features, all match phases, exhaustive rules support, polished replacement art, mobile and historic replay import. The broader end-to-end match belongs to M3; it is not a prerequisite for learning from M1.

### Existing seams to use

Use the command→step→model-sync traces in [architecture](01-architecture-and-independence.md), and preserve current step behavior. Read existing `ClientCommand` and `ServerCommand` schemas, coordinate transforms, full-state joins, dialog prompts and optional compression before defining the adapter. Capture representative fixtures as the protocol contract.

Create a conceptual `BrowserMatchAdapter` that accepts actor-scoped intent, invokes the existing engine and returns the latest public view/prompt. Initial DTOs need only support the demonstrated actions. Record a protocol version, request correlation and monotonically ordered view revision so duplicate/reconnect behavior can be tested. These are proposed interface requirements, not assertions that current fields already provide them. Do not expose arbitrary Java class names, raw internal JSON, server-only state or client-chosen dice as the public contract.

Use the existing state harness and `TestRolls` as the behavior oracle. Test-only deterministic rolls must be enabled through local fixture setup, not through the public WebSocket message schema. Production mode must reject scenario loading. Until public authorization is implemented the diagnostic service binds to loopback only.

### Prototype acceptance scenarios

| Scenario | Procedure | Expected evidence |
|---|---|---|
| Initial sync | Load a known BB2025 fixture and connect home/away browsers | Both match engine player coordinates, ball, turn owner and visible prompt after coordinate conversion |
| Move | Acting player selects a fixture unit and moves to one valid adjacent square | Exactly one accepted update; both views reach the same revision and expected square |
| Invalid action | Submit wrong actor, off-board/illegal destination and stale intent | Rejection leaves position, move allowance and turn resources unchanged |
| Dice-dependent choice | Run existing Both Down with/without Block fixture through adapter; choose die | Outcomes match `BlockTest` assertions; UI displays server choice and final states |
| Duplicate delivery | Repeat the same correlated accepted action | No second movement, dice draw or resource consumption |
| Reconnect at decision | Disconnect acting browser before choosing block die; reconnect | Full resync restores ownership, pending choices and revision without replaying the action twice |
| Asset failure | Remove a fixture asset mapping in test configuration | Labeled fallback token; gameplay and log still function |
| Renderer failure | Exercise WebGL initialization failure | Clear supported-browser/rendering message; no blank unexplained game view |
| Independent operation | Start fresh with controlled local configuration | No FUMBBL credentials or required outbound FUMBBL requests; local service logs and network observations recorded |

Suggested performance checkpoints, **not measured promises**: p95 local accepted action→render under 250 ms excluding intentional animation, reconnect under 2 seconds on localhost, no unbounded growth during 1,000 fixture actions. Record machine/browser, payload sizes and process/browser memory; replace these targets if measured experience justifies a different threshold.

Stop the prototype with a short decision record: passed/failed scenarios, exact engine changes required, unresolved browser protocol assumptions, screenshots and logs. If failure reveals an architectural blocker, compare the cost of that specific seam change with a replacement. Do not turn a failed rendering experiment into an automatic engine rewrite.

## Behavior, schema and deployment migration

Characterize current behavior first and keep the desktop client available as a reference. Add browser-specific DTOs alongside legacy commands. Version browser protocol and saved state independently; a DTO version change need not rewrite existing replay storage. Reject unsupported versions explicitly.

Freeze rules/catalog/roster inputs per match. Introduce database migrations with backup/restore tests and a clear rollback boundary. Preserve unknown legacy fields only inside an explicit import adapter; never silently reinterpret old snapshots as current BB2025 data. Initial historical replay support can remain on the legacy client.

Roll out locally, then to an invite-only desktop beta, then expand content and public access. Each release has a pinned engine version and supported-content list. Do not upgrade active matches in place until compatibility is proven: drain them or retain the old service version through completion. A rollback must restore compatible code plus schema/data, not merely the web assets.

Observe accepted/rejected actions, queue depth/delay, disconnect/resync rates, persistence failures, snapshot age, asset failures and match completion errors. Never log authentication tokens or assume replay data is public. Confirm backup restore with a match paused at a decision. Keep admin/backup access separate from public gameplay routing.

## Decisions and risk gates

| Gate | Evidence needed | Default until resolved |
|---|---|---|
| Rules fidelity | Characterization plus authoritative resolution of disputed/disabled behavior | Preserve observed engine behavior, label unsupported cases |
| First roster catalog | Current validated position/skill/cost data and test matrix | Two curated starter rosters; synthetic fixtures only in prototype |
| Art identity/provenance | Small actual-size art trial and per-asset evidence | Neutral prototype tokens; no public promise to reuse legacy branding |
| Independence | Fresh local startup and observed absence of required remote services | Existing MariaDB architecture with isolated config; no live integration |
| Public authorization/privacy | Actor/role/recipient tests and route policy | Loopback-only diagnostic service |
| Operating budget | Load envelope, retention, region and host quote | One-region simple deployment; no vendor purchase |

Remaining owner choices are initial catalog breadth, final name/theme, account provider, budget/region and retention. The audit supplies defaults so local proof can proceed once separately requested. It does not authorize publishing or implementation.
