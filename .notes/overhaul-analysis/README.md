# FUMBBL overhaul audit

**Recommendation: replace the desktop client with a browser client while retaining the Java rules engine behind tested application interfaces.** The existing engine and state tests are substantial reuse assets. Prove browser input, state updates, a dice decision and reconnect before committing to deeper engine changes.

Prepared 2026-09-06 for an independent, public noncommercial 2D game: BB2025 first, solo development with AI assistance, desktop first/mobile later, with an exhibition roster builder and local testing. No 3D, model-sharing app, or league-management implementation. Audited revision: `882fe8721dcc44b2958346629b41e469edb8d12b` on `main`. Application code/assets were not changed; the initial and final working-tree state contained only untracked `.notes/`. [Revision and verification metadata](evidence/audit-metadata.json).

## Executive assessment

| Finding | Consequence |
|---|---|
| Server already handles JSON/WebSocket commands and authoritative BB2025 steps | A browser port can begin at transport/view seams; a new rules language or transport is not inherently required |
| Common/server contain no direct AWT/Swing imports; 182 client-logic files do | Preserve server rules; replace browser presentation. Module separation is incomplete mainly on the client |
| Standalone runtime still initializes JDBC/database services | Local state tests are not proof of complete independent startup or match play |
| Required Java 8 build and CI lifecycles both passed | A reproducible baseline exists; it should anchor migration rather than be discarded |
| 1,260 media/archive files and 24 archive members were inventoried; 230 image samples reviewed | The art overhaul requires a semantic asset manifest and consistent art direction, not just higher-resolution exports |
| Six PNG-named files contain GIF data; 87 exact duplicate groups exist | Normalize formats and preserve semantic aliases when repackaging |
| Jetty dependency is from the now-EOL 9.4 line | Update hosting dependencies before public release; source inspection does not establish an exploit |
| Tests cover selected scenarios, with a disabled Dump-Off/On The Ball integration case | Passing builds do not establish full BB2025 fidelity, restart recovery or browser compatibility |

Supporting evidence and precise source references are in [architecture](01-architecture-and-independence.md), [code/rules](02-code-and-rules.md), [assets](03-assets-and-visual-strategy.md), [technology](04-technology-and-decisions.md), and [verification](07-verification-and-coverage.md). Jetty lifecycle status was checked against its [official support table](https://jetty.org/download.html).

## Recommended destination

Keep Java/Maven and initially MariaDB, modernizing the runtime/server dependencies separately from gameplay. Build a TypeScript browser application with React for standard controls and roster editing, PixiJS/WebGL for the board, and versioned JSON over native WebSockets. Start with existing command/step/snapshot semantics behind adapters. Defer replacement of the step executor, reflection registration, database, or replay model until a specific measured limitation justifies it.

Prefer crisp 2D player sprites with a restrained pitch and clean DOM/vector controls. Keep portraits/effects optional and progressively loaded. The actual art direction and reuse rights remain owner/content-review decisions. [Alternative comparison and ADRs](04-technology-and-decisions.md); [visual strategy](03-assets-and-visual-strategy.md).

## Report map

| Report | Read it for |
|---|---|
| [01 Architecture and independence](01-architecture-and-independence.md) | Current diagrams, critical flows, module choices, external contracts and standalone dependencies |
| [02 Code and rules](02-code-and-rules.md) | BB2025 implementation/test matrix, risks, characterization and reuse boundaries |
| [03 Assets and visual strategy](03-assets-and-visual-strategy.md) | Inventory results, visual findings, production pipeline, licensing gaps, audio/fonts |
| [04 Technology and decisions](04-technology-and-decisions.md) | Three overhaul approaches, preferred stack, rejected alternatives, interface/hosting decisions |
| [05 Product requirements](05-product-requirements.md) | User journeys, desktop scope, roster builder, local test mode, acceptance requirements |
| [06 Roadmap and prototype](06-roadmap-and-prototype.md) | Milestones, effort ranges, rollback, operating gates, first prototype specification |
| [07 Verification and coverage](07-verification-and-coverage.md) | Environment, exact commands, test counts, failures resolved, runtime gaps |
| [08 Evidence and coverage index](08-evidence-index.md) | Scope checklist, machine-readable data, source methodology and remaining uncertainty |
| [09 Implementation kickoff](09-implementation-kickoff.md) | Accepted owner decisions, local setup needs, session-sized slices and first engineering prompt |
| [10 Local container environment](10-container-environment.md) | Authorized Docker installation, verified MariaDB image and remaining server-container work |
| [11 Independent startup](11-independent-startup.md) | M0b startup trace, isolated fixture lifecycle, server image and actual acceptance evidence |
| [Preliminary art previews](../art-preview/README.md) | Human/Orc concepts and rough pitch, dugout and match UI |
| [Contact-sheet gallery](assets/README.md) | All 18 image categories with labeled source samples |

## Validation result

- Focused Java 8 selector: **39 passed**, zero failures/errors/skips.
- Required `clean install`: **347 passed, one existing disabled**, zero failures/errors.
- CI lifecycle `clean verify`: **347 passed, one existing disabled**, zero failures/errors.
- Packaged server entry point printed usage and exited successfully. This is not full server startup.
- No complete independent match, browser session, database recovery or Linux CI execution was performed. The tests used Windows and portable Java 8.

The 348 reported tests are parameter-expanded invocations, not 348 distinct source methods. Two test-named Swing programs are manual UI checks and were not run by Maven. Logs and per-suite XML/CSV are linked from the verification report.

## Next decision

**Owner update, 2026-09-06:** ADR-001–004 and milestone direction accepted; ADR-005 deferred with GCP likely. Human/Orc and match-screen art exploration is now requested, with full team artwork reserved for a separate sprint. See [implementation kickoff](09-implementation-kickoff.md) and [art previews](../art-preview/README.md). The audit results above remain historical measurements.

The next engineering step is the **local browser proof in M0–M1**, after a separate implementation request. It tests whether two browsers can drive the existing engine, resolve a controlled block and reconnect safely without FUMBBL. It does not need final artwork or a full rules rewrite.

Before broader delivery, choose the initial roster/skill catalog, final name/theme, account approach and operating budget/retention. The PRD records defaults for estimation. M0–M4 is estimated at **60–122 engineering days**, excluding dedicated art production and major rule remediation; this is a planning range for a limited starter catalog, not a calendar commitment. Re-estimate after the prototype.
