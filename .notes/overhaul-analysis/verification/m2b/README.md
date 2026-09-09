# M2b — saved-team round trip

**Implemented and validated, 2026-09-08. M2 remains incomplete.**
Next slice: **M2c — match creation, frozen roster data and role ownership**.
Saved documents are not match-ready. No commit, push, deployment, credential
change, database reset or volume deletion was performed.

## Result and boundaries

The existing `/teams` React DOM route now creates, lists, loads, edits, saves,
imports and exports accepted team documents. M2a's catalog presentation and all
pre-existing working-tree changes were preserved. No Pixi/board implementation,
ruleset executor, Java/Jetty version, CI workflow or external service was changed.

`SavedTeamService` uses the existing `TeamValidation` for creates, updates,
imports and loads (including list readback). `SavedTeamDocument` and `TeamDraft`
are immutable values. `SavedTeamJson` explicitly projects only format version,
server UUID, optimistic document revision, ruleset/catalog, local owner namespace,
draft choices and newly computed accepted validation. Imported computed claims
are shape-checked and discarded. Unknown/duplicate fields, invalid versions,
identifiers, quantities, duplicate players/slots and illegal drafts fail before
mutation. Requests are bounded to 16 KiB UTF-8 and eight levels of JSON nesting.

The repository seam stores one complete JSON snapshot in an InnoDB row, with
atomic owner/ID/revision predicates for replacements. Creation has a transactionally
enforced capacity of 50 documents per local owner. No last-write-wins policy,
partial dependent rows, raw legacy team/XML objects or arbitrary image URLs exist.
Local subjects `home`/`away` identify the existing credential scope for this slice;
they do not implement public accounts or match-role ownership.

Catalog policy is **current-catalog-only writes**. Available but no-longer-selectable
catalogs return `MIGRATION_REQUIRED`; unknown versions return `VERSION_UNAVAILABLE`.
Both reject create/update/import. Load preserves the original document and draft,
returns the explicit status and a separate fresh evaluation, and never silently
re-prices stored content. There is no automatic migration. Tests exercise the
available/retired branch with an alternate selectable version and the unavailable
branch with a stored historical document. No second real catalog was invented.

The browser preserves edits on conflicts and validation errors, locks historical
documents, rejects duplicate-key file JSON, and preserves documents across socket
reconnect. Downloads are the last server snapshot, explicitly excluding unsaved
edits. Expected SQL failures roll back. Once COMMIT has been attempted, an error
can indicate a lost acknowledgement: `SAVE_OUTCOME_UNKNOWN` returns the attempted
snapshot/ID for reconciliation, not a false rejection or claimed successful save.
The UI blocks another save/import until a load or explicit new-draft action.
No automatic retries or durable create idempotency are claimed.

Schema migration 002 upgrades the existing local database to version 2. It verifies
the exact table shape before advancing the marker and on subsequent starts; an
interruption after DDL can resume without resetting data. Full instructions and
the paired-code/database rollback boundary are in
[saved-team-migration.md](../../../../containers/local/saved-team-migration.md).

## Changed files (relative to the M2a working tree)

- `ffb-server/src/main/java/com/fumbbl/ffb/server/team/`:
  `SavedTeamDocument.java`, `SavedTeamJson.java`, `SavedTeamRepository.java`,
  `SavedTeamService.java`, `JdbcSavedTeamRepository.java`.
- `ffb-server/src/main/java/com/fumbbl/ffb/server/local/`:
  `BrowserSavedTeamJson.java`, saved-team dispatch in `BrowserMatchAdapter.java`,
  schema migration/verification in `LocalSchema.java`.
- `ffb-server/src/main/java/com/fumbbl/ffb/server/FantasyFootballServer.java`:
  local-only repository/service wiring with dedicated short JDBC connections.
- `ffb-server/src/main/resources/local-schema/002-saved-teams.sql`.
- `ffb-server/src/test/java/com/fumbbl/ffb/server/local/`:
  `BrowserSavedTeamJsonTest.java`, extended `LocalSchemaTest.java`.
- `ffb-server/src/test/java/com/fumbbl/ffb/server/team/`:
  `JdbcSavedTeamRepositoryTest.java`, test-only `SavedTeamJdbcAcceptance.java`.
- `browser-client/src/`: extended `TeamPanel.tsx`, new `saved-team-protocol.ts`,
  `saved-team-ui-state.ts`; `browser-client/saved-team.schema.json`.
- `browser-client/test/`: `saved-team-protocol.test.ts`,
  `saved-team-ui-state.test.ts`, `test-saved-team-schema.ps1`,
  `saved-team-demo.mjs`, `saved-team-mock-form.mjs`.
- Browser README/protocol; container README/migration guide and image identity
  in Dockerfile/Compose; latest client `ChangeList` feature entry; kickoff and
  this evidence directory. Existing M2a files/prompt/evidence were retained.

## Exact checks and actual results

Commands are from the repository root unless marked `browser-client`. Host Java
uses the existing workspace Maven and Java 8 toolchain, with no dependency upgrade.

| Check | Actual result / evidence |
|---|---|
| `./tools/build.ps1 test -Module ffb-server -Test 'BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,LocalSchemaTest,BrowserTeamJsonTest' -Offline` | First sandbox run failed during ClassGraph initialization; host rerun passed 21 tests at that intermediate revision. [Failure](java-focused.log), [host run](java-focused-host.log). |
| `./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,LocalSchemaTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest' -Offline` | Final focused selector: **53 passed**, zero failures/errors/skips (36 server + 17 state tests). [Final log](java-focused-final.log). |
| `./tools/test-tooling.ps1` | **9 passed**. [Log](tooling.log). |
| `./tools/build.ps1 install -Offline` | All eight reactor projects passed. [Log](install.log). |
| `./tools/build.ps1 verify -Offline` | All eight projects passed; **409 tests, zero failures/errors/skips**. [Log](verify.log), [per-suite counts](test-results.csv). |
| `npm test` (`browser-client`) | **17 passed**, zero failures/skips. [Log](browser-tests.log). |
| `npm run build` (`browser-client`) | TypeScript check and Vite production build passed. [Log](browser-build.log). |
| `pwsh -NoProfile -File browser-client/test/test-saved-team-schema.ps1` | Actual JSON Schema validator passed valid import and invalid missing/unknown-field cases. [Log](saved-schema.log). |
| `docker compose -f containers/local/compose.yaml build server` | Final image built and its common/server tests passed. [Log](container-build-final.log). |
| `docker compose -f containers/local/compose.yaml up -d --wait` | Existing schema upgraded; final server/database healthy. [Log](container-up-final.log). |
| JDBC acceptance command below | Actual MariaDB rollback, CAS and ambiguous-commit reconciliation passed. [Log](jdbc-acceptance-final.log). |
| `node test/saved-team-demo.mjs prepare` (`browser-client`) | Live DOM round trip and rejected-write invariance passed. [Log](browser-prepare-passing.log). |
| `docker compose -f containers/local/compose.yaml stop server` then `docker compose -f containers/local/compose.yaml start --wait server` | JVM stopped/restarted, existing MariaDB data retained, schema verified and healthy. [Stop](server-stop.log), [start](server-start.log). |
| `node test/saved-team-demo.mjs check` (`browser-client`) | Full document equals its pre-restart snapshot. [Log](browser-restart-check.log), [summary](browser-summary.json), [synthetic snapshot](saved-team-restart.json). |
| `node test/saved-team-mock-form.mjs` (`browser-client`) | Mounted UI preserves unavailable catalog across close/reconnect and disables editing. [Log](browser-mock-form-final.log). |
| `git diff --check` | Passed after removing one added trailing blank line. [Final log](diff-check.log). |

Docker commands used this existing host PATH prefix:
`$env:PATH = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"`.
The exact container-only acceptance command was:

```powershell
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v 'C:/Users/jaken/Git-Hub/fumbbl-rebuild-chatgpt/ffb-server/target/test-classes:/acceptance:ro' --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.team.SavedTeamJdbcAcceptance
```

The JDBC runner tests real pre-commit rollback after INSERT/UPDATE, duplicate key
and oversized-row constraint failure, exactly one successful concurrent version-1
update, new-connection reload, and a real COMMIT followed by an injected exception.
The final case is correctly *unknown outcome*, and load confirms committed version 3.
It does not claim that a lost acknowledgement means rollback. The runner leaves
one accepted synthetic team; it deletes no rows.

The live Chrome flow creates eleven Linemen, two rerolls and an apothecary for
700,000 gold; adds a reroll and saves revision 2 at 750,000; exports; imports a
tampered total of 1 and receives recomputed revision 3 at 750,000. A stale import
and an over-budget draft are rejected; a direct load proves the entire persisted
revision-3 document is unchanged. After restart, a new browser loads that exact
document. No page errors. Screenshots were visually checked:
[restarted form](saved-team-restart.png), [saved-team page](saved-team-page.png).
Other list rows are masked in the full-page capture. Browser/version and viewport
are recorded in `browser-summary.json`; this is local desktop Chrome evidence.

## Failures, corrections and limitations

- The initial sandbox Java run failed in ClassGraph; the authorized host run
  passed. This is not counted as a passing sandbox run.
- Review found the catalog-to-list callback and stale closure could lose UI state.
  Ref-based current state, direct request chaining and preservation tests fixed it.
  The original roster table/details were restored during review.
- Review found migration interruption and COMMIT acknowledgement ambiguity.
  Exact schema verification/resumption and explicit unknown-outcome handling were
  added and verified. Final independent review: no actionable findings remaining.
- Copying test classes into the running read-only container failed; the attempted
  Java launch could not find its class. [Failure](jdbc-acceptance.log). The separate
  read-only bind-mounted acceptance container passed; no rootfs restriction changed.
- The first browser preparation failed on a test locator matching two status
  elements after its successful stale-import rejection. [Failure](browser-prepare.log).
  The locator was narrowed and the complete flow rerun. That earlier valid synthetic
  team was retained rather than deleted.
- An automatic permission-review attempt timed out before a browser launch. Its
  one retry later found Vite unavailable (`ERR_CONNECTION_REFUSED`), recorded in
  [browser-prepare-final.log](browser-prepare-final.log). Vite was restarted and the
  complete preparation/check passed. These attempts are not passing evidence.
- The initial mounted-form test asserted `fieldset.isDisabled()`, which is not
  the correct Playwright control assertion. It failed; the corrected test checks
  the actual disabled Add player control and passes. [Failure](browser-mock-form.log).
- Windows PowerShell lacked `Test-Json`; the installed `pwsh` runtime provided it.
  No optional dependency was installed. The actual schema test then passed.
- `git diff --check` initially found one new EOF blank line in the container guide;
  it was removed. Existing line-ending conversion warnings are not diff failures.
- Verify's log records an unusually long elapsed interval (14:18 h) during the
  interrupted session. It completed successfully; this is not a performance benchmark.

No disabled tests were added. Local `Maven Verify / Validate` lifecycle is green;
no hosted CI run was triggered without a commit/push. Backup restoration, public
auth/TLS, Java 21/Jetty upgrade, other browsers/mobile, live unavailable catalog
data migration, full match setup, frozen rosters and general match creation are
not claimed. Schema rollback requires paired backup/code; no volume reset or
backup restore was performed. The catalog remains the original M2a subset.

Final image: `sha256:7cff405929f8c83ac1ea2251b782ef9f3065a3e38e3e6750f05fe4e125776957`.
[Image identity](container-identity.txt), [healthy services](container-status.txt).
The local server remains on loopback 22227 and Vite on loopback 5173. Demo browser
contexts are closed. Logs are retained locally under the repository's existing
`*.log` ignore policy; durable summary, schema, scripts and status entry remain
reviewable without relying on those logs being staged.
