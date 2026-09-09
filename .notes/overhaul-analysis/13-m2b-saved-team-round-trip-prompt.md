# M2b implementation prompt — saved-team round trip

> Implement **M2b: saved-team round trip** after M2a’s immutable versioned
> catalog and server-authoritative `TeamValidation` are complete. Read
> `.notes/overhaul-analysis/04-technology-and-decisions.md`,
> `05-product-requirements.md`, `06-roadmap-and-prototype.md`,
> `09-implementation-kickoff.md`, the completed M2a status/evidence, and
> `12-m2a-catalog-validation-prompt.md` before changing code. Inspect the
> working tree and preserve unrelated in-progress work.
>
> Add a small saved-team service and browser flow that persist and retrieve an
> immutable validated team document. A saved document must include a stable team
> identifier, document version, ruleset, catalog version, draft data, the
> server-computed validation result, and sufficient metadata to identify its
> owner once ownership is introduced. Persist only an accepted, canonical form:
> pass every create, update, import, save, and load path through
> `TeamValidation`; recompute totals and legality on the server; never trust
> client-provided totals, validity flags, position data, skills, or catalog data.
>
> Define and document exact catalog-version behavior. A document whose catalog
> version is unavailable or differs from the current selectable catalog must not
> be silently rewritten, re-priced, or made selectable as current content. On
> load, return the saved document with an explicit version-status result. On
> edit/save, either validate against its pinned available catalog or reject with
> a clear migration-required/version-unavailable result; choose one behavior,
> apply it consistently, and test it. There is no automatic migration in this
> slice. The browser must clearly show the catalog/version status and preserve
> the saved draft rather than discarding it.
>
> Use a versioned, narrow JSON contract and a database schema migration with a
> clear local rollback/reset boundary. Keep persistence behind an application
> repository/service seam; do not serialize raw `Team`, `TeamSkeleton`, legacy
> XML, arbitrary Java object graphs, tokens, or server-only data. Imports are
> untrusted and bounded: reject malformed/oversized documents, unknown fields or
> identifiers where the contract requires rejection, duplicate player or slot
> IDs, invalid quantities, and stale/invalid document versions. Do not accept
> arbitrary remote image URLs. Keep the existing synthetic local fixtures and
> test-only dice/scenario controls outside this contract.
>
> Writes must be atomic. A failed validation, version check, parse, import, or
> persistence operation must leave the previously saved document untouched and
> must not create a partial row or orphaned data. Use optimistic document-version
> checks or an equivalent explicit conflict result for concurrent/stale updates;
> do not make last-write-wins an implicit policy. Ensure save/load behavior is
> stable across the documented local server restart. Do not log bearer tokens,
> imported private data, or complete saved-team documents at normal log levels.
>
> Extend the existing React DOM roster route/panel to create, list, load, edit,
> save, and import/export the validated team document. Keep forms keyboard
> usable, expose validation and conflict/version errors as text, and make no Pixi
> or board-state changes. Add a concise user-facing `ChangeList` entry for the
> available saved-team workflow.
>
> Follow ADR-001 through ADR-004: retain Java/Maven and the Java 8
> characterization baseline; reuse MariaDB/JDBC with an explicit versioned
> migration; use the existing React/Vite client; maintain versioned JSON adapters;
> and add only the application seams required. Do not rewrite rules, replace
> persistence/networking, upgrade Java or Jetty, add cloud services, enable
> Dependabot, or broaden the roster catalog without evidence.
>
> **Out of scope:** match creation/join, converting a saved team into a game
> team, freezing roster/catalog data for a match, opponent-role ownership or
> public authentication, spectator permissions, game setup, full BB2025 content,
> mobile, production artwork, public deployment, and automatic catalog migration.
> A saved team is not match-ready in M2b. Do not claim M2 is complete.
>
> Add focused Java tests for canonical save/load, server-side recomputation,
> valid import/export, unavailable/mismatched catalog versions, stale update
> conflict, restart persistence, and every rejected-write atomicity path. Add
> browser schema/form tests for successful round trip and clear rejection/status
> rendering. Prove that invalid writes leave the prior saved version byte-for-byte
> or semantically unchanged, as appropriate to the storage contract. Update the
> browser protocol, local-client/container documentation, migration/reset
> instructions, and a durable M2b kickoff status entry with changed files, exact
> commands, evidence, limitations, and the next slice (M2c: match creation,
> frozen roster data, and role ownership).
>
> Run focused Java and browser tests first, then required validation:
> `./tools/test-tooling.ps1`, the relevant `./tools/build.ps1 test ...` selector,
> `./tools/build.ps1 install -Offline`, `./tools/build.ps1 verify -Offline`,
> `npm test`, `npm run build`, a local container/browser save-load-restart
> demonstration, and `git diff --check`. Record actual environmental failures as
> failures or blockers, not passing evidence. Keep the CI `Maven Verify / Validate`
> check green. Do not add disabled tests. Do not commit, push, deploy, reset
> volumes, or alter credentials.
