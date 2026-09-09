# M2a: versioned starter catalog and authoritative team validation

Status: **M2a implemented and validated** on 2026-09-08 UTC / 2026-09-07 local.
**M2 is not complete. Next slice: M2b — saved-team round trip.**

The owner resolved the repository-only source blocker by authorizing Blood Bowl
Base BB2025 and selected 1,150,000 gold from the documented budget choices.
[Catalog provenance and LRB6 field comparison](../../../../browser-client/catalog.md)
records URLs, the explicit current fields, updated Ogre access, skill semantics,
unsupported content and legacy fields that are irrelevant to this bounded slice.
No missing required recruitment data was found for this declared subset.
The initial blocked investigation remains in [README](README.md) as history.

## Result and architecture

`bb2025-human-2026-09-08.1` supports the six regular Human positions and seven
purchasable skills. Other content is explicitly unsupported. Java verifies every
base/purchased skill name and category through the existing BB2025 skill factory,
then copies only immutable catalog facts. The catalog supplies all costs,
quantities and skill access; the draft contains identifiers and choices only.
No network lookup is needed at runtime. Tests pin the complete public catalog.

`RosterCatalog`, `TeamDraft` and `TeamValidation` live in a BB2025 application
package, with no inheritance between rulesets. Validation is pure and does not
write the draft, match, history or database. Cost is recomputed from identifiers;
client totals and other claims fail the strict JSON allowlist. Unknown catalog,
ruleset, roster, preset, position and skill references; duplicate player/slot/skill
choices; limits, quantity, eligibility and budget errors all fail explicitly.
Unpriceable requests have no partial total. Invalid but priceable drafts display
a diagnostic total with `valid:false`.

`BrowserTeamJson` is the explicit projection/decoder. Its parser guard bounds
UTF-8 payloads and nesting before recursive parsing. This is the only necessary
change to M1 input handling: legal M1 shapes, authentication, communication worker,
admission/delivery limits, action history and retry semantics remain intact.
Catalog/validation operations use the existing authenticated local WebSocket;
they are read-only evaluations outside action history and never broadcast.

`/teams` is a React DOM form. Pixi does not mount there. Users see source-derived
positions and select draft players, captain, purchased skills and resources.
Validation results are server output. Edits/disconnect discard old evaluations,
pending evaluation locks the form, retired-socket callbacks cannot update it,
and only the correlated result completes a pending evaluation. No raw XML,
Java object graph, class name, image URL, fixture control or dice is exposed.

## Changed files

All paths below are repository-relative. Preserved the pre-existing untracked
`.notes/overhaul-analysis/12-m2a-catalog-validation-prompt.md`.

- `ffb-server/src/main/java/com/fumbbl/ffb/server/team/bb2025/`:
  `RosterCatalog.java`, `TeamDraft.java`, `TeamValidation.java`.
- `ffb-server/src/main/java/com/fumbbl/ffb/server/local/`:
  new `BrowserTeamJson.java`, updated `BrowserMatchAdapter.java`.
- `ffb-server/src/test/java/com/fumbbl/ffb/server/local/BrowserTeamJsonTest.java`;
  `ffb-statetest/src/test/java/com/fumbbl/ffb/test/BrowserTeamAdapterTest.java`.
- `browser-client/src/`: `TeamPanel.tsx`, `team-protocol.ts`,
  `team-validation-view.ts`, updated `main.tsx` and `style.css`.
- `browser-client/team-request.schema.json`, `test/fixtures/catalog-v1.json`,
  `test/team-protocol.test.ts`, `test/test-team-schema.ps1`, `test/team-demo.mjs`.
- `browser-client/catalog.md`, `protocol.md`, `README.md`.
- `ffb-client-logic/src/main/java/com/fumbbl/ffb/client/model/ChangeList.java`:
  player-visible route entry in the latest version.
- `.dockerignore`: include only the pinned browser catalog test fixture required
  by Java container tests. `containers/local/Dockerfile` and `compose.yaml`:
  version identity `3.4.0-m2a.1`; runtime/dependency/volume configuration unchanged.
- `.notes/overhaul-analysis/09-implementation-kickoff.md` and this evidence directory.

## Exact checks and evidence

Commands run from repository root unless a browser-client working directory is
specified. Host Java runs use the unchanged workspace Maven/Java 8 toolchain.

| Check | Result |
|---|---|
| `./tools/build.ps1 test -Module ffb-server -Test BrowserTeamJsonTest -Offline` | 8 passed; [host log](java-focused-host.log) |
| `node --experimental-strip-types --test test/team-protocol.test.ts` in browser-client | 4 passed; [log](browser-focused.log) |
| `./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest' -Offline` | Passed; new integration plus existing engine/adapter/transport regressions; [log](java-adapter-focused.log) |
| `./tools/test-tooling.ps1` | Exit 0, all 9 checks passed; console output captured in this session (Write-Host bypassed the stdout-only log redirection) |
| `./tools/build.ps1 install -Offline` | All eight reactor projects passed; [log](install.log) |
| `./tools/build.ps1 verify -Offline` | All eight projects passed; **394 tests, zero failures/errors/skips** in current reports; [log](verify.log), [per-suite results](test-results.csv) |
| `./browser-client/test/test-team-schema.ps1` | Valid draft plus seven invalid schema cases passed with PowerShell Test-Json; [log](schema.log) |
| `npm test` in browser-client | 11 passed; [final log](browser-tests.log) |
| `npm run build` in browser-client | TypeScript and Vite production build passed; [final log](browser-build.log) |
| `docker compose -f containers/local/compose.yaml build server` | Current image built with Java tests; [log](container-build.log) |
| `docker compose -f containers/local/compose.yaml up -d --wait` | Server/database healthy, existing volumes and credentials preserved; [log](container-up.log) |
| `node test/team-demo.mjs` in browser-client | Passed in Chrome 152.0.7977.77, 1440×1080 viewport; [summary](team-demo.json), [wire](team-wire.json), [log](team-demo.log) |
| `git diff --check` | Passed after final edits; [log](diff-check.log) |
| Evidence credential scan | Zero current local browser credential matches; [result](sanitization.txt) |

Current full test totals are from this checkout's reports, not copied from the
historical M1c count. No disabled tests were added. CI workflow/build configuration
was not altered. The current `Maven Verify / Validate` lifecycle passed locally;
no hosted CI run was triggered because there was no commit or push.

Focused Java tests cover immutability, version/reference rejection, source/engine
skill mapping, recomputation, valid drafts, every legality error family, duplicate
JSON fields, wrong types, forbidden client fields, bounded oversized/nested inputs,
and complete draft input invariance. The engine integration test compares the
entire serialized match and adapter metrics before/after evaluations and malformed
requests, including repeated evaluation. Browser tests check the pinned wire
fixture, strict decoder failures, safe escaped DOM messages and server total rendering.

Live acceptance compares the server catalog to the pinned fixture; validates
an empty draft, then 11 linemen, two rerolls and one apothecary for **700,000**;
checks captain selection and an ineligible purchased skill; injects eight invalid
requests (total claim, catalog version, duplicate ID/slot, position, skill,
fractional quantity, oversized draft); then proves the form's next submitted draft
equals its original value and still costs 700,000. Expanding to 16 linemen/eight
rerolls yields **1,250,000**, rejected against the 1,150,000 budget. No page errors.
Screenshots were visually inspected: [valid](team-valid.png),
[over budget](team-over-budget.png); controls and results are legible and scroll
normally, and the route mounts no canvas.

## Corrections and limits

- An initial compile used the wrong Game constructor; corrected to the existing
  factory-manager constructor before passing tests. [Initial log](compile.log).
- Sandboxed ClassGraph initialization could not inspect the workspace; the
  authorized host run passed. [Failed attempt](java-focused.log). A sandboxed
  HTTP read for Elite icons was similarly blocked; the authorized read succeeded.
- Python's optional `jsonschema` module was unavailable; no dependency was added.
  The installed PowerShell Test-Json validator ran the actual schema checks.
- The first browser demo timed out on a select's accessible label. Explicit
  accessible names fixed the controls; the repeated demo passed.
  [Failed attempt](team-demo-selector-attempt.log). Visual review also caught stale
  status text after an unrelated rejection; only correlated rejections now affect
  the form. Final browser tests/build/demo were rerun after these client fixes.
- Initial evidence scanning encountered an empty log; a fail-fast scan excluding
  empty files subsequently completed with zero matches. No credentials were printed.

No general XML converter, broad roster coverage, all inducements, advancement,
saved-team files/persistence, database migrations, match assembly, frozen rosters,
ownership changes, public auth/TLS, spectators, full match setup, mobile or
production deployment is included. UI acceptance is local Chrome at the recorded
viewport; no new cross-browser/mobile certification or M1-scale load claim.
Recruitment validation does not certify a complete playable Human match loop.

Final image: `sha256:03283893f2c593bfa4b5da9bc894063fa590677174abaaa3aa4c807cf05eabde`;
[identity](container-identity.txt). Server is healthy on loopback 22227; Vite on
loopback 5173. After all demo browsers closed, [metrics](final-metrics.json) show
the default BOTH_DOWN fixture at revision zero, zero history, no authorized/active
connections and empty queues. No fixture reset was needed by the demo. The local
image restart created a new synthetic fixture lifetime; database/backup volumes
were preserved. No commit, push, deployment, credential change or volume reset.

**M2b handoff:** implement a saved-team round trip using the immutable draft and
catalog version. Revalidate through TeamValidation on save/load, define explicit
version mismatch behavior and test rejected writes for atomicity. Match creation,
frozen roster data and role ownership remain later M2 work; do not treat this
builder as a match-ready roster persistence implementation.
