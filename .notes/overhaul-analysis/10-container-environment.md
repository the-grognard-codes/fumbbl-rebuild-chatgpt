# Local container environment

Verified following the owner's explicit authorization to install Docker or MariaDB, 2026-09-06. This completes container tooling preparation, not the M0 game-server startup milestone.

## Installed and verified

| Item | Result |
|---|---|
| Host | Windows 11 Home, build 26200, x64; hypervisor present; approximately 16 GB RAM |
| WSL | 2.7.13.0; default distribution version 2; no WSL upgrade or Windows restart required |
| Package manager | Existing Windows Package Manager (`winget`) |
| Docker Desktop | 4.89.0, installed for the current user with WSL 2 backend |
| Docker Engine/CLI | 29.7.2; Linux x86_64 engine running |
| Compose | v5.5.0 available |
| Standard container check | `hello-world` downloaded and ran successfully; temporary container auto-removed |
| MariaDB | Official `11.8` image resolved to 11.8.9-MariaDB-ubu2404 |
| Database checks | `healthcheck.sh --connect --innodb_initialized` passed; `SELECT VERSION(), 1;` returned version and `1` |
| Cleanup | Temporary database stopped and auto-removed; no running containers remained |

MariaDB image digest: `mariadb@sha256:2439dcd7d14010ecd1ff7a4e1c5abe8e208c34fe35290744deeeaac3569043c3`. This verifies image startup, not compatibility with the project's schema or JDBC behavior. Use the digest when reproducing this check; adopt/update the database version through application tests during M0.

Installed location: `%LOCALAPPDATA%\Programs\DockerDesktop`. CLI and credential helper: `%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin`. Docker Desktop remains available/running; the downloaded images remain cached. A native MariaDB Windows service was not installed.

## Installation and checks

The sandbox could not enumerate WSL or host CIM information; authorized execution outside it succeeded. The initial winget `--scope user` selection returned "No applicable installer found". Docker's documented per-user flag succeeded through the package installer override:

```powershell
winget install --id Docker.DockerDesktop --exact --source winget --silent --override 'install --user --quiet --accept-license --backend=wsl-2' --accept-package-agreements --accept-source-agreements --disable-interactivity
```

Winget verified the downloaded installer hash before installation. Package version 4.89.0 installer SHA256: `854626704af28a160d5af68b96b3e32eacf08ab397ce6c12eb02a04788d73681`.

Docker Desktop was started in the background, then engine readiness was checked. The already-open shell initially lacked the newly installed credential helper on PATH; adding the Docker bin directory to that process's PATH resolved it. For an existing terminal:

```powershell
$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
$env:PATH = "$dockerBin;$env:PATH"
docker --version
docker compose version
docker info --format '{{.ServerVersion}} {{.OSType}} {{.Architecture}}'
docker run --rm hello-world
```

The MariaDB test used a unique `ffb-db-smoke-` container name, `--network none`, no published ports, an in-memory `/var/lib/mysql` mount, and a generated root password. No password was printed or saved in the report. Readiness and SQL used the official healthcheck credentials inside the container. A finally block stopped only the created test container, and `--rm` removed it. No application or existing user data was involved.

## Packaging decision and remaining M0 work

Build the game server as a versioned Linux container image, with a Maven build stage and JVM runtime stage. Run MariaDB separately through Compose, with a named volume for development data and explicit reset instructions. Keep runtime configuration and secrets outside images and bind local published ports to loopback. Initially preserve the Java 8 baseline; Java 21 remains the accepted target after compatibility verification.

Remaining: server Dockerfile and build context, exact packaged startup entry point/configuration, database schema/fixtures, application connection test, independent match creation, restart/persistence checks and reproducible commands. Tooling smoke tests do not close M0a or M0b. GCP remains a likely provider with service selection deferred; Docker image portability does not validate a particular GCP service's lifecycle or storage behavior.

Sources: [Docker Windows installation and flags](https://docs.docker.com/desktop/setup/install/windows-install/), [official MariaDB image](https://hub.docker.com/_/mariadb), [MariaDB healthcheck documentation](https://mariadb.com/docs/server/server-management/automated-mariadb-deployment-and-administration/docker-and-mariadb/using-healthcheck-sh).
