# M3d completed matches: schema 4

M3e retains this server image/schema and adds browser integration/recovery
hardening. No new DDL, format conversion or engine change is needed. Its
[acceptance/M4 handoff](../../.notes/overhaul-analysis/verification/m3e/README.md)
separates browser reconnect from unavailable in-progress JVM recovery.

Pair `ffb-server:3.4.0-m3d.1` with the M3d browser. Java 8/Maven, MariaDB/JDBC,
React/Vite, credentials and catalog are unchanged. Startup applies migration
004 to `ffb_prepared_matches`: widen `document_json` from MEDIUMTEXT to LONGTEXT
and replace its 65,536-byte check with 16,842,752 bytes (16 MiB replay plus a
64 KiB frozen-document envelope). Existing rows are preserved. No other table,
source-team data, volume or credential is replaced.

The migration first verifies either the exact schema-3 table or the complete
schema-4 table. For schema 3, it discovers the verified original size-constraint
name and performs one atomic MariaDB ALTER containing both changes. The new table
is verified before advancing the marker from 3 to 4. A restart after ALTER but
before marker advancement verifies the new shape and advances without repeating
DDL. Incompatible columns, checks, indexes, triggers or engines fail closed.
Schema-4 starts verify both saved-team and completed-match tables. No data repair,
drop, truncate, reset, or marker downgrade is automatic.

Completion remains a short JDBC transaction with one revision compare-and-swap.
The same row contains membership, frozen teams, result and replay, so a rollback
cannot leave a completion without its replay. Exact completion reconciliation
compares the canonical entire artifact; conflicting terminal content fails with
`COMPLETION_CONFLICT`. Unknown commit outcomes must be reconciled by load or exact
retry while the engine remains available. Successfully committed results survive
JVM restart; this does not establish in-progress restart recovery.

From the root, with Docker Desktop's CLI on PATH:

```powershell
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait server
```

With Vite on loopback 5173, from `browser-client`:

```powershell
node test/full-match-demo.mjs
node test/results-mock.mjs
# After stopping/starting only the JVM:
node test/result-restart-demo.mjs
```

The live driver selects saved teams through the DOM, creates/joins a match,
plays native setup/kickoff, scores, reaches halftime and full time, retrieves the
same result as both participants, and displays first/last replay events. Dice
remain native random rolls. Test-only drivers may create synthetic saved teams
if no suitable existing team is available; fixtures/dice are not product fields.

After Java compilation, the actual MariaDB transaction acceptance command is:

```powershell
$testClasses = (Resolve-Path ffb-server/target/test-classes).Path
docker compose -f containers/local/compose.yaml run --rm --no-deps -T -v "${testClasses}:/acceptance:ro" --entrypoint java server -cp '/acceptance:FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.match.MatchJdbcAcceptance
```

It tests completion rollback, concurrent CAS, lost commit acknowledgement and
exact reconciliation using retained synthetic rows. Test classes are mounted
read-only and are not part of the runtime image.

Schema-3 code must not run unchanged against schema 4. A data-retaining rollback
requires compatible code plus a pre-M3d database backup restored by the operator
after stopping services. Completed result artifacts have no old-client format.
Do not shrink the column, downgrade the marker, or remove completion metadata to
simulate rollback. Backup restore is not certified by this migration or ordinary
restart checks. No volume reset or credential changes are part of these commands.
