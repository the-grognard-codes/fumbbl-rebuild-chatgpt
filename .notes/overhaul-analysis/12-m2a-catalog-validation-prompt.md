# M2a implementation prompt — catalog and team validation

> Implement **M2a: versioned BB2025 starter catalog and server-authoritative team
> validation**. This is the first deliberately bounded M2 slice. Read
> `.notes/overhaul-analysis/04-technology-and-decisions.md`,
> `05-product-requirements.md`, `06-roadmap-and-prototype.md`,
> `09-implementation-kickoff.md`, `browser-client/protocol.md`, and the M1c
> closeout report before changing code. Inspect the current working tree and
> preserve all unrelated in-progress work.
>
> Deliver a small, explicit, immutable catalog for the selected BB2025 starter
> content, plus the Java `RosterCatalog` and `TeamValidation` application seams
> needed to evaluate a browser-ready team draft. The catalog and every draft must
> carry a catalog version and ruleset. Define a narrow, documented JSON contract
> and provide a React DOM route/panel in the existing browser client that displays
> the catalog, allows a user to compose a draft, and renders the server's computed
> total and validation messages. Use Pixi only for the board; roster controls are
> normal accessible DOM controls.
>
> The server is authoritative: it must recompute cost and legality from catalog
> identifiers, rather than accepting client totals, quantities, positions, skills
> or resource claims. Validate the selected preset budget and roster constraints;
> make unknown catalog/position/skill identifiers, duplicate player or slot IDs,
> invalid quantities, malformed or oversized imports, and over-budget or
> ineligible selections fail clearly and without partial mutation. Treat imports
> as untrusted data. Do not expose raw `TeamSkeleton` XML, mutable Java model JSON,
> arbitrary remote image URLs, Java class names, fixture controls, deterministic
> dice, or server-only fields in the browser contract. The existing synthetic
> browser fixtures remain local test scenarios, not catalog entries.
>
> Establish the starter catalog from existing BB2025 data only after tracing its
> source, cost, quantity and permitted-skill semantics. Publish the exact
> supported roster/position/skill set and mark everything else unsupported. Do
> not invent roster rules or silently skip unknown skills. If the available source
> cannot support a defensible curated starter catalog, record that precise blocker
> and stop rather than guessing. Keep the catalog small; this slice does not
> promise broad BB2025 roster coverage.
>
> Follow ADR-001 through ADR-004: retain the Java/Maven engine and Java 8
> characterization baseline; use the existing TypeScript/React/Vite client;
> keep Pixi out of rules and roster state; use a versioned JSON adapter rather
> than exporting the legacy object graph; introduce only the necessary application
> seams. Do not rewrite the step executor, replace persistence/networking, upgrade
> Java or Jetty, add a cloud service, enable Dependabot, or change the existing
> M1 transport/queue semantics without a demonstrated need.
>
> **Out of scope for M2a:** saved-team persistence or import/export files,
> database migrations for teams, match creation/join, frozen match rosters,
> opponent-role ownership, public authentication/TLS, spectator flow, full match
> setup, arbitrary roster breadth, artwork production, mobile, and production
> deployment. Those are later M2/M3/M4 work. Do not claim M2 is complete when
> M2a passes.
>
> Add focused tests for catalog versioning, server-side recomputation, valid
> drafts, each rejection class, JSON schema/decoder behavior, and the browser
> form's validation rendering. Test that client-supplied totals cannot affect the
> server result and that invalid requests leave the evaluated draft unchanged.
> Prefer current engine/catalog data as the oracle; do not add a parallel rules
> implementation. Update the browser protocol and local-client documentation,
> add a player-visible `ChangeList` entry if the new route is exposed, and add a
> durable M2a status entry with changed files, exact checks, evidence, limitations,
> and the next slice (M2b: saved-team round trip).
>
> Run the narrowest relevant Java and browser tests first, then all required
> validation: `./tools/test-tooling.ps1`, the appropriate
> `./tools/build.ps1 test ...` selector, `./tools/build.ps1 install -Offline`,
> `./tools/build.ps1 verify -Offline`, `npm test`, `npm run build`, and
> `git diff --check`. Run a local container/browser demonstration if the new
> catalog route touches the live adapter. Record environmental failures accurately;
> do not treat them as passing evidence. The current CI `Maven Verify / Validate`
> check must remain green. Do not add disabled tests. Do not commit, push, deploy,
> reset volumes, or alter credentials.
