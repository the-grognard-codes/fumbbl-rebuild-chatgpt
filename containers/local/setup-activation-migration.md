# M3a activation durability and rollback boundary

M3a retains schema 3 and the existing `ffb_prepared_matches` InnoDB table. No DDL,
volume reset, credential change, or infrastructure upgrade is required. The paired
local image is `ffb-server:3.4.0-m3a.1`.

The version-1 match interpreter now supports revision 3 / `ACTIVATED`, with both
frozen members and a third accepted request entry. The transition is one atomic
revision compare-and-swap. Existing revision-1/2 documents remain readable. Reads
continue to validate frozen data without selecting newer source-team revisions.

Only a fresh activation, or exact reconciliation of a pending reservation in the
same JVM, may initialize an engine. The reservation becomes attempted before any
engine initialization. On failure it stays unavailable. After server restart,
revision-3 documents and activation retries remain durable but their setup engines
cannot be reconstructed. `/setup` reports `SESSION_UNAVAILABLE`; prepare another
match. This is explicitly not in-progress persistence or recovery.

M2c uses the same schema but its older decoder cannot interpret `ACTIVATED` rows.
Do not downgrade rows to revision 2, remove activation records, or replay activation
to emulate recovery. A data-retaining rollback that requires all matches to be
readable needs the paired pre-M3a database backup and M2c code/image, restored by an
operator after stopping services. Backup restoration was not performed or certified
in this task. Other schema-3 data is unchanged; no automatic repair is offered.

## Local reproduction

From repository root, with Docker Desktop on PATH:

```powershell
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
```

With Vite running, from `browser-client`:

```powershell
node test/setup-demo.mjs prepare
```

Then restart only the JVM from repository root:

```powershell
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
```

From `browser-client`, `node test/setup-demo.mjs check` verifies the retained
document, unavailable sessions on both browsers, and exact activation retry that
cannot initialize. Run `prepare` again to leave a fresh match legally ready for
kickoff. These commands retain generated teams/matches and existing volumes.

The container-only `MatchJdbcAcceptance` runner now also tests real activation
rollback, racing activations, lost COMMIT acknowledgement, and exact reconciliation.
Use the same read-only test-class mount documented for M2c. It does not test durable
engine recovery and never deletes existing data. Saved-team capacity remains 50
per subject; evidence fixtures are retained rather than removing prior records.

## M3b extension

M3b uses the same schema-3 ACTIVATED documents and frozen inputs. The paired image is ffb-server:3.4.0-m3b.1. No DDL or credential change is needed. Gameplay remains resident in the JVM; a restart retires it just like M3a setup. Pair the browser snapshot decoder with the server version. See ../../browser-client/core-turns.md.
