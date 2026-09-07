# M0b startup trace and implementation

Started 2026-09-07 from the existing M0a working tree. Preserved its build scripts,
POM/workflow/README changes and verification evidence. Read the container environment,
accepted ADRs and M0 milestone contract before implementation.

## Required startup inputs

`FantasyFootballServer.run()` creates the logger and loads the JDBC driver before
choosing initialization or normal service startup. Normal startup prepares both
query and update statements, initializes `GameCache`, starts persistence and
communication workers, exposes the Jetty HTTP/WebSocket listener, then starts the
replayer/request processor and timers. `server.test=true` is not an isolation mode.

`DbConnectionManager` uses MariaDB Connector/J 3.5.8. `db.type=mariadb` is required:
the misleading legacy `useMysqlDialect()` branch actually emits identity syntax
unsuitable for MariaDB. The query factory uses auto-commit for generated game IDs;
the update factory commits queued snapshot/metadata transactions separately.

The old `initDb` is destructive: it drops the game, setup, marker and settings
tables, adds coach tables in standalone mode, and seeds legacy named users/password
digests and files from `setups`. It cannot be the normal Compose start command.

Local schema version 1 uses those same table definitions through `initDb(false)`
only after confirming **no tables exist**. It creates `ffb_coaches`,
`ffb_player_markers`, `ffb_user_settings`, `ffb_team_setups`, `ffb_games_info` and
`ffb_games_serialized`, widens the compressed snapshot column to `LONGBLOB`, seeds
two generated-secret fixture identities, and records `ffb_local_schema(version=1)`.
Partial/unversioned/unknown-version databases fail closed and need explicit reset;
this is not a migration for production databases.

The minimal runtime additionally needs `server.port`, `server.base.dir`, both log
file and folder paths, and local backup directory/extension. The original logger
requires a file even when a folder is supplied; the first real startup exposed it.
Standalone `GameCache.init()` scans relative `teams` and `rosters` directories.
No setup files are required when legacy seeding is disabled. Entropy network timers
are disabled, and no FUMBBL/S3 settings enter the local properties.

## Lifecycle paths used

- `AdminServlet.handleSchedule` queues `InternalServerCommandScheduleGame` on the
  real communication worker. `GameCache.createGameState(SCHEDULE_GAME)` allocates
  a JDBC ID and stores a scheduled snapshot. The local scheduling adapter now
  initializes BB2025 before `TeamCache`/`RosterCache` parse and attach the fixtures.
- `ServerCommandHandlerJoin` checks `ffb_coaches` in standalone mode.
  `ServerCommandHandlerJoinApproved` and `UtilServerStartGame` use local teams/options
  and the existing sequence engine. `LocalGameLifecycle` supplies BB2025 without
  old developer options or external pitch URLs.
- Existing mode guards skip FUMBBL create/resume/removal. `StepEndGame` already
  selects local `ServerRequestSaveReplay` outside FUMBBL mode. `UtilBackup` retains
  disk and JDBC recovery but skips its otherwise unconditional S3 fallback locally.
- The server emits UTF-8 JSON in binary WebSocket frames even with compression off.
  The acceptance client handles both text and binary frames; its initial text-only
  attempt timed out after the server had accepted both coach joins.
- SIGTERM uses resource teardown without recursively calling `System.exit`. Workers
  finish queued work on their own thread; JDBC closes after its queue drains. Each
  teardown phase is attempted even if an earlier phase fails. The documented demo
  proves restart of its persisted fixture, not arbitrary crash/pending-choice recovery.

## Scope and evidence

Runtime configuration, fixtures and credentials are separate mounts. The server
image is versioned, built in Linux with Maven/Java 8 and copied into a Java 8 runtime
stage. The separate MariaDB service has a named volume and no published port.
The offline override prevents external routing; the normal profile's only published
listener is `127.0.0.1:22227`. A fresh image build or MariaDB smoke test does not satisfy M0b.

Actual port verification exposed a Docker Desktop constraint: internal-only network
attachments did not create a host port mapping despite the configured port. The
normal profile now adds a publishing bridge for the server, with only a loopback
binding; the offline override removes that bridge and all published ports to prove
network independence with the same image. MariaDB stays internal in both profiles.
See [Docker's network documentation](https://docs.docker.com/engine/network/) and
[internal network behavior](https://docs.docker.com/reference/cli/docker/network/create/).
The normal profile is not an outbound firewall; the offline profile is the no-egress
acceptance environment.

The first real stop also revealed `DbUpdateFactory.closeDbConnection()` sends SQL
`SHUTDOWN` in standalone mode. The local profile now closes only its JDBC connection;
the separate database service requires no SHUTDOWN privilege. The initial host full
build reached state tests but ClassGraph was denied the module directory by the
sandbox; this was classified as an environment failure and rerun outside it.

See [commands and data lifecycle](../../containers/local/README.md) and
[actual acceptance evidence](verification/m0b/README.md). No browser feature,
production artwork, cloud deployment, commit or push is part of this change.
