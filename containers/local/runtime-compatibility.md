# R1 runtime compatibility boundary

The accepted local M3d/M3e runtime remains `ffb-server:3.4.0-m3d.1` on Java 8.
R1 target verification is a separate build boundary: Temurin HotSpot
`21.0.11+10-LTS` (`Eclipse Adoptium`), Apache Maven `3.9.9`, the `mockito5`
profile, and Jetty `12.1.13`. The standalone `compose.r1.yaml` uses new R1
database/backup volumes and networks and loopback port 22228. It does not replace
the retained local image or share its mutable storage.

Run the Java 8 characterization reference at frozen revision
`56c19c80070861e89f465f1989be403d3aad0ffd` with `tools/bootstrap.ps1` and
`tools/build.ps1`. For the target runtime, first install the project-local Maven
using that same bootstrap, then run:

```powershell
./tools/target-build.ps1 info
./tools/target-build.ps1 test
./tools/target-build.ps1 install
./tools/target-build.ps1 verify
```

`target-build.ps1` selects
`C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot` on Windows, or accepts
an explicit `-JavaHome`. It rejects another vendor or runtime patch, uses the
project-local Maven installation and `.tools/repository`, and restores `JAVA_HOME` and Maven environment
variables after the process. Use `-Offline` after the target repository is warm.

No active match may be upgraded in place. The paired Java 8 runtime must stay
available until engine, catalog and format parity is demonstrated with live local
acceptance. Current compatibility identifiers are policy engine
`ffb-3.4.0-bb2025-m3d.1`, catalog `bb2025-human-2026-09-08.1`, schema `4`,
browser protocol `/browser/v1`, and the existing replay format. R1 changes none
of those identifiers. There is no durable in-progress recovery artifact yet;
an unfinished engine remains unavailable after JVM loss, while committed results
and replays retain their existing schema-4 behavior. Drain active work before a
future runtime cutover, or retain this compatible Java 8 runtime through match
completion.

Target build success is toolchain evidence only. Runtime parity requires the
relevant reactor, browser-contract, and live local WebSocket checks on the target
runtime, including framing, origin, slow-client queue/resync, timeout, async
callback, retry, and clean-close behavior. Those checks are required before this
policy permits a transition for new matches. Even with parity, R1 cannot move a
resident engine between JVMs: there is no recovery format to carry its state.

```powershell
docker compose -f containers/local/compose.r1.yaml build server
docker compose -f containers/local/compose.r1.yaml up -d --wait
```

Do not combine this file with `compose.yaml`, override its project name, reset
volumes, or point it at the retained database. It starts with its own empty
database using the same existing local secret files (no credential changes).
The Java 8 reference continues at port 22227. Test drivers accept only these two
loopback endpoints through `M4_WS_ENDPOINT`; product browser configuration remains
at the reference endpoint. Do not stop either JVM while it owns unfinished work.

An initial experimental comparison shared the reference database. Its one match
completed and its JVM was drained/stopped before the isolated acceptance run;
those rows and logs are retained. That configuration is not the delivered policy.
