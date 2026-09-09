# M2c prepared-match schema and durability

The paired local image is `ffb-server:3.4.0-m2c.1`. Startup retains the existing
MariaDB database and applies `003-prepared-matches.sql` after migration 002.
Schema 3 adds only `ffb_prepared_matches`: a stable UUID primary key, optimistic
document revision and one complete versioned JSON document. Membership, lifecycle,
both frozen rosters and accepted request replay metadata are written together.
Saved-team records, legacy games and external credentials are unchanged.

MariaDB DDL commits implicitly. Startup uses `CREATE TABLE IF NOT EXISTS`, then
verifies exact columns, collations, engine, indexes, check constraints and absence
of triggers before changing the schema marker from 2 to 3. A restart after DDL
but before marker advancement repeats verification and resumes. Schema-3 startup
verifies both saved-team and prepared-match tables. Incompatible tables and unknown
schema versions fail closed; no automatic drop, repair or reset is performed.

Create uses a stable server-derived UUID tied to the authenticated subject and
request ID. The persisted canonical request fingerprint identifies exact retries.
Join uses an atomic revision compare-and-swap on the complete document. Retry
metadata is never evicted and cannot outlive its match document. There are only
two successful preparation mutations per match. Each selected saved-team revision
is read as one immutable document, checked against the requested revision and
validated again; later source edits cannot substitute another document version.
No transaction writes the saved-team source.

A pre-COMMIT failure rolls back. Once COMMIT is attempted, a failure is an unknown
outcome, including a failed acknowledgement or connection close. Reconnect and
retry the exact request ID and choices to reconcile. Never convert an unknown
outcome into a fresh create. The protocol returns no attempted document as an
authoritative success. Normal reads and exact retries return the current match.

Frozen format 1 is interpreted explicitly. An unavailable/unsupported persisted
format or mapping returns incompatibility without rewriting the stored bytes.
Retain the paired compatible code/image for inspection; there is no automatic
catalog or frozen-document migration. These rows contain preparation only, not
an in-progress engine session or recoverable setup/gameplay state.

## Routine restart and acceptance

From repository root, with Docker Desktop's CLI on PATH:

```powershell
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
```

With Vite on loopback port 5173, run from `browser-client`:

```powershell
node test/prepared-match-demo.mjs prepare
```

Then from repository root:

```powershell
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
```

Finally from `browser-client`:

```powershell
node test/prepared-match-demo.mjs check
```

The browser driver uses generated synthetic saved teams and retains its match
rows as evidence. It does not reset database volumes or change credentials.
After compiling Java tests, the container-only JDBC acceptance runner is:

```powershell
$testClasses = (Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
```

This runner injects real transaction failures, races joins and loses COMMIT
acknowledgements using new database connections. Test classes are mounted read-only
and are not part of the shipped application. Actual results, including any failed
attempts, belong in [M2c evidence](../../.notes/overhaul-analysis/verification/m2c/README.md).

## Rollback and reset boundaries

M2b code expects schema 2 and must not run unchanged against schema 3. A data-retaining
rollback requires stopping services and restoring a known pre-M2c database backup
with the paired M2b code/image. M2c match documents have no M2b representation.
Do not downgrade the marker, drop the table in place, or reinterpret frozen teams
through a newer catalog. Backup restore is an operator action and is not certified
by a successful routine JVM restart.

The parent guide's explicit disposable reset removes all database and backup
volumes, including saved teams and prepared matches. It is not a migration repair
or ordinary restart procedure. No reset, deletion or backup restoration is part
of this implementation run. Credentials remain external and unchanged.
