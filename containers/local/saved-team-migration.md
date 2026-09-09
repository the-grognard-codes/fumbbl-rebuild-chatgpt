# M2b saved-team schema and durability

The paired image is `ffb-server:3.4.0-m2b.1`. Startup upgrades the dedicated
`ffb_local` database from schema 1 to 2 using
`ffb-server/src/main/resources/local-schema/002-saved-teams.sql`.
It creates only `ffb_saved_teams`, an InnoDB table containing a single canonical
JSON document per team plus its indexed owner, identifier and optimistic revision.
No legacy team/game rows, tokens or credentials are converted or modified.
Existing database/backup volumes are retained. Schema 2 is required for saved teams.

MariaDB DDL commits implicitly. The migration uses `CREATE TABLE IF NOT EXISTS`
and verifies the exact expected columns, types, collations, indexes, checks,
InnoDB engine and absence of triggers before advancing the schema marker. Thus
a restart after table creation but before marker advancement resumes without
discarding data. Schema-2 startup also verifies the table. Unknown schema versions
or an incompatible table fail startup; startup never drops, truncates or repairs
foreign data. Do not change the schema marker to bypass verification.

Application writes use short separate JDBC transactions. One row contains the
entire snapshot, so there are no dependent rows to orphan. Update checks the
owner, team ID and expected document revision in its SQL predicate. Create
serializes its 50-document-per-owner capacity check using the schema row lock.
Normal pre-commit failures roll back. A lost COMMIT acknowledgement has an unknown
outcome and must be reconciled by loading the returned attempted team ID; see the
[protocol's explicit outcome policy](../../browser-client/protocol.md).

## Routine stop/start and acceptance

From the repository root, with Docker on PATH:

```powershell
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
```

With Vite running, from `browser-client`:

```powershell
node test/saved-team-demo.mjs prepare
```

Then from the root:

```powershell
docker compose -f containers/local/compose.yaml stop server
docker compose -f containers/local/compose.yaml start --wait server
```

Finally from `browser-client`:

```powershell
node test/saved-team-demo.mjs check
node test/saved-team-mock-form.mjs
```

The first script creates only synthetic accepted saved-team data and leaves it
in the database. `check` compares the complete saved document after the JVM
restart. It does not use/reset the synthetic match or claim match recovery.

After a Java build has produced `ffb-server/target/test-classes`, the separate
container-only JDBC acceptance runner exercises actual transaction rollback,
constraints, concurrent CAS and a lost COMMIT acknowledgement. It is test code,
not part of the packaged application or any browser contract. In PowerShell at
the repository root:

```powershell
$testClasses = (Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.team.SavedTeamJdbcAcceptance
```

This creates one valid synthetic saved team and keeps it as evidence. The
read-only server root filesystem prevents copying test classes into the running
container; the read-only bind mount above preserves that restriction. No secret
value is printed. The MariaDB driver's expected constraint warnings include only
the synthetic row ID/constraint name, not document contents.

## Rollback/reset boundary

M2a code expects schema 1 and cannot be rolled back onto schema 2 unchanged.
To retain data, stop the services and restore a known pre-M2b database backup
together with the paired M2a code/image. New M2b saved teams are not representable
in that old schema: export any wanted documents first and keep them separately.
Do not merely downgrade the marker or delete the team table while the server runs.
Backup creation/restore is an explicit operator action; this implementation run
did not create a full database backup or certify backup restoration.

For intentionally disposable data only, the explicit reset in the parent
[container guide](README.md#explicit-disposable-reset) deletes **all** this
Compose project's database and backup volumes, including existing matches and
saved teams. It is not a normal restart or migration repair operation. A fresh
M2b startup creates schema 2. No reset or volume deletion was performed in M2b.
Credentials remain external and are unchanged by migration or restart.

Saved teams remain local credential-scoped documents, not match-ready teams.
M2c adds match creation, frozen roster data and role ownership. No automatic
catalog migration, public authentication, Java/Jetty upgrade or deployment is included.

## M2c successor

Current M2c code applies schema 003 after this historical M2b migration and accepts
saved-team capacity checks on schema 3. See [prepared-match migration](prepared-match-migration.md)
for paired-image rollback boundaries. Frozen match copies are independent of later
saved-team edits/imports. This guide's schema-2 image/results remain historical M2b evidence.
