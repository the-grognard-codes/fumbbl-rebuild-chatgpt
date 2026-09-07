# Draft product requirements: independent 2D exhibition game

Status: proposed PRD for owner review. Source labels: **U** = user decisions in this task; **N** = historical notes; **C** = current source behavior; **R** = audit recommendation. A recommendation is not a newly authorized implementation task.

## Purpose and boundaries

Enable two people to create teams, start an exhibition match, play BB2025-supported rules through a desktop browser, recover a disconnected session, and inspect the result. Provide a reproducible local setup for development and rules testing. **U:** public noncommercial project, independent first, BB2025 first, solo developer with AI assistance, 2D only, desktop first/mobile later. **U:** assess roster builder and local testing; exclude 3D and model sharing. League administration and live FUMBBL compatibility are outside initial delivery.

**N, superseded or tentative:** Gemini stack choices are candidates, not requirements. Pixel art remains a preference to evaluate. Fixed 16:9 layout is a reference, not a requirement to letterbox every browser. The sample mobile stat list omits PA, while the code models passing explicitly: include MA/ST/AG/PA/AV for BB2025. [RosterPlayer fields](../../ffb-common/src/main/java/com/fumbbl/ffb/model/RosterPlayer.java#L58); [notes](../FUMBBL_Rebuild_Summary.md).

## Audience and complete journeys

| User | Required journey | Acceptance evidence |
|---|---|---|
| Exhibition player | Select/create a team, invite an opponent, review match options, finish setup, play, resume after interruption, see final result | Two real browser sessions finish the supported match preset; each client agrees with authoritative state |
| Team creator | Pick a catalog roster, add eligible positions/skills/resources, see costs and validation, save/load/export team | Server recomputes cost and eligibility; changed catalog version cannot silently change a saved match |
| Developer/tester | Start local services without FUMBBL, load a named scenario, control dice through test-only facilities, reproduce a failure | Fixture + rules/catalog version + action sequence yields expected state; test features absent from public profile |
| Spectator, later within desktop delivery | Join an authorized read-only match and follow public action log | Spectator commands cannot mutate gameplay or read fields outside its projection |

## Functional priorities

| ID | Requirement | Basis | Delivery |
|---|---|---|---|
| GAME-01 | Independent match create/join with home/away ownership; repeat join/reconnect must not create extra teams or turns | U/R; current join/session implementation as reference | Prototype identities locally; authenticated product flow before public use |
| GAME-02 | Versioned BB2025 match presets and explicitly listed supported roster/skill options | U/R | First playable increment has a small declared catalog; expand only with evidence |
| GAME-03 | Setup, kickoff, movement, blocks, passing, rerolls, turnovers, touchdowns, drive/half/full-time progression | C/R | Desktop beta; each declared capability must have characterization and end-to-end evidence |
| GAME-04 | Context actions and legal-target feedback; outcome is always decided server-side | C/R | Start with select/move/block, then all supported decisions |
| GAME-05 | Clear prompt for the current decision, actor and remaining resources; rejected actions explain why without corrupting state | C/R | Prototype onward |
| GAME-06 | Reconnect while a decision is pending, with authoritative snapshot and current prompt | C/R | Prototype exercise; durable restart recovery before public release |
| GAME-07 | Result and replay/history tied to engine/rules/roster versions | C/R | Desktop beta; no promise to import all historical FUMBBL replays |
| TEAM-01 | Catalog-driven team editor with validation and cost totals, save/load and import/export | U/R | Local scaffold first; persistent product editor next |
| LOCAL-01 | Documented isolated local launch, database setup, fixtures and test commands | U/R | First engineering milestone |
| UX-01 | Legible 2D board, status panels, dice, action log and accessible action controls | U/N/R | Desktop prototype then beta |
| MOBILE-01 | Landscape touch play, pan/zoom and collapsible panels | U/N | Later phase; avoid hover-only semantics now |

A BB2025 label does not establish every roster, skill or option is complete. Maintain a public supported-content list and expose unsupported combinations before a match is created. Characterization tests lock observed behavior; disputed rules need an authoritative decision recorded separately. [Capability matrix](02-code-and-rules.md).

## Roster builder and data ownership

**C:** `RosterPosition` stores cost and quantity; `Roster` stores reroll price/limits and other roster constraints. `Team` serializes team/player data. `TeamSkeleton` can carry XML content inside JSON, so an existing JSON response is not necessarily a normalized browser-ready team. The local sample XML also contains external IDs and image bases; it is migration input, not a validated current catalog. [RosterPosition](../../ffb-common/src/main/java/com/fumbbl/ffb/model/RosterPosition.java#L46); [Roster](../../ffb-common/src/main/java/com/fumbbl/ffb/model/Roster.java#L35); [TeamSkeleton.toJsonValue](../../ffb-common/src/main/java/com/fumbbl/ffb/model/TeamSkeleton.java#L138).

**R:** implement the builder as a route in the same web application, backed by the same catalog/validation service. Keep its import/export contract reusable by a command-line fixture loader; do not begin with a separately deployed companion app.

Data concepts: immutable catalog version and ruleset; roster/position identifiers; player choices and permitted enhancements; team resources; calculated costs; validation messages; team document version. At match creation freeze the selected teams and catalog version. Editing a saved team must not mutate a running match. Treat imported documents as untrusted data; limit size and reject invalid references, duplicate players/slots and invalid quantities. Do not accept arbitrary remote image URLs in the initial product catalog.

Separate two explicit workflows:

- **Validated exhibition:** enforce the selected roster/preset constraints; server recomputes totals; explain over-budget/quantity/eligibility failures. Initial budgets and optional progression rules are versioned preset data, not hard-coded UI assumptions.
- **Local test scenario:** deliberately allow synthetic statistics, skills, positions and dice sequences in a local-only fixture loader. Label results as scenarios. Never let a public request enable this mode or supply deterministic dice.

`GameStateBuilder` currently constructs synthetic teams, assigns stats/skills and starts in regular turn mode rather than exercising roster legality or pregame. Use its schema as a fixture reference, not as a production validation service. Its unknown-skill lookup can skip unresolved names, so a future fixture importer must fail visibly instead of silently changing the scenario. [GameStateBuilder.withRule/withTeam/stats/applySkills/build](../../ffb-statetest/src/main/java/com/fumbbl/ffb/test/GameStateBuilder.java).

## Desktop experience and 2D visuals

Default to a top-down coordinate grid with upright 2D sprites, clear cell occupancy and a visible ball carrier. This preserves the observed sprite presentation while avoiding isometric picking/occlusion work. Evaluate aesthetic variants through small art boards later, without changing coordinate semantics. [Visual audit](03-assets-and-visual-strategy.md).

Use HTML text and controls for player cards, roster forms, action choices and log. Keep animations decorative: disabling them must not skip decisions or hide results. Pair home/away colors with shapes/outlines or labels; provide text names for statuses and dice results. Maintain visible focus, keyboard selection/action access, zoom, and a text summary of the selected square/player. Avoid requiring sound, color or hover to understand play.

**Proposed test targets, not existing performance measurements:** desktop checks at 1280×720 and 1920×1080 with 100%/200% browser zoom; Chrome, Edge, Firefox and Safari current stable at validation time; no clipped decision buttons; no silent WebGL failure. A smaller browser window may scroll/reflow panels. Later mobile checks include landscape phone/tablet, pinch/pan, accidental drag protection and reachable decision controls.

## Reliability and public-service requirements

Server owns identities, actions, legality, costs, dice and results. A rejected or repeated command must not consume resources twice. A lost connection shows disconnected/read-only state until resync; client animation completion does not authorize a game transition. Bound incoming messages, queues and logs. Record operational errors without tokens or private team/account content.

Before public launch: scoped player/spectator/admin authorization, origin/TLS policy, safe name/log rendering, independent accounts, asset provenance review, dependency maintenance, backup restore, restart recovery and a documented support path. These respond to a public multiplayer service's concrete requirements; this audit does not claim a completed penetration test or legal clearance.

## Owner decisions still needed for implementation

The audit can recommend defaults but cannot infer: initial roster/skill catalog breadth, final name/theme, public account provider, hosting budget/region, retention and expected simultaneous matches. Defaults for estimating are two curated starter rosters, invite-only exhibition matching, no public chat in the first slice, one region and conservative retention configurable by the operator. None prevents a local diagnostic prototype. Catalog legality and asset provenance are release gates, not settled by the historical notes.
