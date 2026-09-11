# Isolated local server — M0b / M1a / M1b

This is the Java 8 development profile, using the existing JVM, JDBC and legacy
HTTP/WebSocket commands. M1a/M1b add a [local browser movement and choice client](../../browser-client/README.md)
and a separate in-memory two-token fixture. The two synthetic Human teams
are startup fixtures: eleven identical linemen each, not a validated roster-builder
catalog or production artwork. BB2025 is selected explicitly.

## Start and demonstrate

Run from the repository root in PowerShell, with Docker Desktop's Linux engine:

```powershell
$env:PATH = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
./containers/local/setup.ps1
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
docker compose -f containers/local/compose.yaml exec -T server java -cp 'FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.local.LocalAcceptanceDemo create
```

Every command must exit successfully. The last command prints `PASS gameId=N` only
after the live server schedules the fixture, both fixture coaches receive BB2025
game states through real WebSocket sessions, and its JDBC snapshot can be read
back through the authenticated game-state endpoint. Save `N` for restart checks.
A healthy database, healthy listener or successful image build alone is not this
demo. This does not demonstrate a completed match or browser movement.

The current versioned image is `ffb-server:3.4.0-m2b.1` (earlier evidence retains its historical image IDs). Its Maven 3.9.9 build stage runs
common/server tests and packages the existing server distribution; the runtime
stage contains its JAR and dependency libraries. All three base images and MariaDB
are pinned by digest. The resolved Java runtime is Temurin `1.8.0_502-b07`; the host
M0a toolchain stays `1.8.0_504-b01`. Exact `8u504-b01` container tags were unavailable.
This retains Java 8, not an assertion of identical patch-level runtimes.

To repeat acceptance with external routing physically unavailable to the JVM:

```powershell
docker compose -f containers/local/compose.yaml -f containers/local/compose.offline.yaml up -d --wait
docker compose -f containers/local/compose.yaml -f containers/local/compose.offline.yaml exec -T server java -cp 'FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.local.LocalAcceptanceDemo create
```

The offline override removes the server's publishing network and published port.
The same JVM and database remain on the internal network, and the client runs inside
the server container. Docker Desktop cannot publish a host port from an internal-only
network; the normal profile adds a publishing network and binds only loopback.
Return to normal host access with `docker compose -f containers/local/compose.yaml
up -d --wait`. This changes network attachment, preserves the volumes and does not
rebuild the image. The normal profile permits outbound routing; it still supplies
no live credentials/endpoints, and the offline demo proves none are required.

## Stop, start and persistence

```powershell
docker compose -f containers/local/compose.yaml stop
docker compose -f containers/local/compose.yaml start --wait
# Replace N with the ID printed by create:
docker compose -f containers/local/compose.yaml exec -T server java -cp 'FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.local.LocalAcceptanceDemo check N
```

Normal SIGTERM stops the listener and workers, drains queued JDBC updates, then
closes connections. Look for `database queue drained` in server logs. Compose
allows 30 seconds before forced termination. A forced kill/power loss is a different
failure mode: only committed database state is durable. Pending-choice recovery,
crash consistency and completed-match replay are later acceptance checks.
Shutdown no longer issues the legacy standalone SQL `SHUTDOWN` against this separate
MariaDB service. A replay-delete callback arriving after the communication queue
has stopped is logged and rejected, leaving the database copy in place; completed
replay cleanup during shutdown is not claimed by this fixture demo.

`stop` retains containers and named volumes. `down` removes this Compose project's
containers/network but retains its `database` and `backup` volumes. `up -d --wait`
recreates containers against those volumes. Runtime logs under `/tmp` are disposable.
The JDBC snapshots and local replay backups use separate volumes.

## Explicit disposable reset

The following intentionally deletes **this Compose project's database and backup
volumes**, including all its matches. Do not use it to stop a stack you want to keep.

```powershell
docker compose -f containers/local/compose.yaml down --volumes
docker compose -f containers/local/compose.yaml up -d --wait
```

The new database starts with schema version 2, an empty saved-team table and two fixture identities.
An old game ID will no longer load until a new game reuses that numeric ID. The
`create` command can then demonstrate a fresh match. Reset preserves external
secrets. To rotate coach/database credentials, reset the disposable volumes as well;
changing secret files alone does not update existing database accounts.

## Startup inputs and boundaries

- `server.ini` is a read-only mount. `LocalServerMain` permits only the dedicated
  `jdbc:mariadb://database:3306/ffb_local` URL, standalone mode and `server.local=true`.
  It rejects FUMBBL and S3 configuration. There are no live FUMBBL credentials.
- `setup.ps1` generates six secrets once under ignored `.secrets/`, including distinct
  browser home/away bearer tokens. They are mounted
  as files, excluded from the image context, and never printed. Coach/admin digests
  follow the existing development protocol and are password-equivalent secrets.
- `/browser/v1` is an additional local-only text WebSocket route. It binds roles
  from the distinct browser tokens, projects the two-token fixture, and validates
  correlated/versioned movement requests. Fixture setup is JVM-local; there is no
  browser reset/scenario-loading command. The fixture/revision/history reset on
  server restart; existing JDBC matches and volumes remain separate.
- `LocalSchema` initializes only a database with **no tables**. It reuses the Java
  DDL without legacy coaches/setups, widens compressed snapshots to `LONGBLOB`, seeds
  `FixtureHome`/`FixtureAway`, then records version 1. Existing unversioned, partial
  or unknown-version schemas fail startup. The legacy `initDb` remains destructive
  and is never used as a routine container startup command.
- Team/roster XML is mounted into the paths expected by `GameCache`. No downloaded
  live teams, legacy coach accounts, setup fixtures or asset server are required.
  Explicit log-file and log-folder paths are required by `DebugLog`.
- The existing standalone lifecycle handles local authentication, scheduling,
  create/resume and local result/replay storage. `LocalGameLifecycle` replaces old
  development game options; `UtilBackup` suppresses the S3 fallback locally.
- Only `127.0.0.1:22227` is published in the normal profile. MariaDB has **no published
  port** and only an internal network. The offline override also removes external
  routing from the JVM. No host filesystem data other than
  the named fixture/config/secret mounts is exposed to the server. The runtime uses
  UID 10001, a read-only root filesystem and dropped capabilities.

Useful diagnostics:

```powershell
docker compose -f containers/local/compose.yaml ps
docker compose -f containers/local/compose.yaml logs --tail 60 server
docker compose -f containers/local/compose.yaml exec -T server cat /tmp/ffb-server.log
docker network inspect ffb-local-m0b_isolated --format '{{.Internal}}'
```

Run application verification with `./tools/build.ps1 install` and
`./tools/build.ps1 verify -Offline`. Focused change tests include `LocalSchemaTest`,
`LocalGameLifecycleTest`, `UtilBackupTest` and `DbUpdaterShutdownTest`; lifecycle
worker tests accompany the shutdown implementation. The actual M0b run record is
in [the verification report](../../.notes/overhaul-analysis/verification/m0b/README.md).

## M2b saved-team persistence

Schema 2 adds durable validated saved teams without changing existing match data.
See [migration, rollback/reset boundaries and save-load-restart commands](saved-team-migration.md).
The older schema-1 description above describes the initial M0b seed step; current
startup then applies migration 002 and validates the complete saved-team table.

## M2c durable match preparation

Current image `ffb-server:3.4.0-m2c.1` applies schema migration 003 after the saved-team
migration. `/matches` creates and joins intended-opponent matches with frozen owned
teams and persisted roles. Authentication labels are local identity subjects;
match roles come from membership, so either identity can be the creator/home side.
Both participants finish in `AWAITING_SETUP`. No product engine session or regular
play is initialized. The separate diagnostic board remains a synthetic fixture.

See [match migration, retry recovery and routine restart demonstration](prepared-match-migration.md)
and [M2c actual verification](../../.notes/overhaul-analysis/verification/m2c/README.md).
Current fresh startup reaches schema 3. Routine upgrades preserve saved teams,
legacy games, backup/database volumes and existing credentials.

## M3a activation and setup

The current local image is `ffb-server:3.4.0-m3a.1`. Schema remains 3. See [activation durability and restart behavior](setup-activation-migration.md): prepared teams can complete genuine engine setup, but activated in-memory sessions cannot recover after JVM restart. No volume reset or credential change is needed.

## M3d completion and replay

Current image `ffb-server:3.4.0-m3d.1` upgrades to schema 4 without resetting data.
[Migration and rollback boundaries](completed-match-migration.md) describe the
expanded match document bound and atomic terminal artifact. Completed results
and bounded private replay remain readable after JVM restart; activated
in-progress engines still do not recover.
