# M3b kickoff and core turn evidence

M3b implemented for the existing frozen Human catalog on Java 8/Maven,
MariaDB/JDBC and React/Vite. The existing BB2025 engine executes all native rules
and dice. Persisted membership authorizes every action; no fixture/dice contract,
catalog expansion, infrastructure upgrade or public service was introduced.
Existing M3a working-tree changes were preserved. No commit, push, deployment,
volume reset or credential change occurred.

## Behavior and scope

[Protocol and explicit remaining controls](../../../../browser-client/core-turns.md)
describes kickoff, touchback, Quick Snap, High Kick, Charge and Solid Defence;
movement, standing, dodge/rush decisions, blocks/dice, pushes/follow-up, reachable
blitz targets, team/skill rerolls, turnovers and end turn. Native optional skill
and apothecary decisions are also projected. Action IDs are revision-bound;
wrong-role, stale, unknown/illegal actions and changed request-ID reuse fail before
native execution. Exact retries do not execute or roll again. Reconnect restores
authoritative state and pending decisions in the resident JVM.

The live demonstration exercises a subset; focused Java tests additionally cover
all eleven kickoff totals, selected kickoff branches, away orientation, dodge/rush
rerolls, skull turnover, stand/blitz and prompt payloads. This is not certification
of every skill combination or complete-match gameplay. Passing, hand-off, fouls,
scoring/drive/half/completion UX, results, replay and durable in-progress recovery
remain later M3 work. Unknown decisions expose no guessed actions. Resident limits
are 32 sessions and 8,192 retained request identities per match, without eviction.

## Reproduction and final checks

Run from the repository root unless stated otherwise. Java commands use the pinned
local Java 8/Maven toolchain and offline repository. Process-only PowerShell policy
bypass was necessary; no machine/user execution policy was altered.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-tooling.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 test -Offline -Module ffb-statetest -Test CoreTurnActionsTest,SetupSessionTest,MatchServiceTest,FrozenTeamEngineConverterTest,JdbcMatchRepositoryTest,LocalSchemaTest,BrowserPreparedMatchAdapterTest,BrowserSavedTeamJsonTest,JdbcSavedTeamRepositoryTest,BrowserTeamJsonTest,BrowserTeamAdapterTest,BrowserMatchAdapterTest,BrowserChoiceTest,BrowserMatchDeliveryTest,BrowserMatchSocketTest,BrowserMatchTransportTest
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 install -Offline
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build.ps1 verify -Offline
# In browser-client:
npm.cmd test
npm.cmd run build
npm.cmd run dev -- --host 127.0.0.1
node test/core-turn-demo.mjs
# Back in repository root, Docker CLI on PATH:
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
git diff --check
```

- `tooling.log`: 9 checks passed.
- `focused-complete.log`: 98 Java tests passed (61 server + 37 statetest).
- `install-final.log`: clean offline install passed all eight reactor projects.
- `verify-final.log` and `test-suites.csv`: final clean offline verification totals below.
- `browser-tests-final.log`: 29 tests passed.
- `browser-build-final.log`: production build passed; existing >500 KiB chunk advisory remains.
- `container-final-build.log`, `container-final-up.log`: final local image built;
  server/database healthy, server published only at 127.0.0.1:22227, DB unpublished.
- `browser-demo-complete.log`, `core-turn-summary.json`, two wire records and PNGs:
  two isolated Chrome contexts with reversed credential labels and persisted roles,
  setup through kickoff/block, block-die reconnect, wrong-role/stale rejection,
  exact retry, four actual safe moves over four completed engine turns, equal
  authoritative final state. No deterministic dice were used in this demonstration.
- `review.md`: independent final core review approved.

Image `ffb-server:3.4.0-m3b.1`:
`sha256:b32a0b2c6c40a7225a8ade8239aa07042e9d492e1da9e452bfec3134b9ef592b`.
The browser runs separately through local Vite. Screenshots were visually inspected;
a low-contrast action panel was corrected and the browser checks/demo repeated.

## Failures and corrections

Intermediate logs are retained, not counted as final acceptance. Initial Java
sandbox execution failed ClassGraph initialization; rerunning with authorized
process access worked. Initial native movement-step projection was incomplete and
was corrected. Early test failures came from prone/inactive or marked-player test
assumptions, rush state setup, and misuse of DiceRoller.addTestRoll(int); tests now
use the test-only TestRolls helper. No production deterministic dice were added.
An all-kickoff test exposed retained consumed Charge dialog data; current-step
guards fixed the projection. The event resolver initially toggled indefinitely;
it now explicitly declines/finishes optional events. An assumed On the Ball
thrower test was removed after checking the frozen catalog (Pass/Sure Hands);
no catalog was changed. The initial live script selected and ended turns without
moving; it was strengthened to require coordinate changes on all four moves.
`initial-demo/` is historical evidence only. Earlier successful demos preceded
final kickoff changes and are superseded by `browser-demo-complete.log`.
Docker initially was stopped; starting installed Docker Desktop allowed the local
build. Existing database/backup volumes and credentials were retained.

## Changed source responsibility

M3b adds CoreTurnActions, CorePromptActions and KickoffActions; extends M3a
SetupSession/SetupApplication with bounded action projection/history and selection;
adds trusted application role routing in ReceivedCommand/UtilServerSteps without
changing legacy persistence routing. Browser SetupPanel and strict setup protocol
render typed server options and restore snapshots. CoreTurnActionsTest and
SetupSessionTest cover native decisions/rejection invariants; core-turn-demo.mjs
provides the two-browser acceptance. ChangeList, local image tag and protocol,
setup/migration/readme/kickoff documents are updated. The full working tree also
contains pre-existing M3a files; it must not be interpreted as an M3b-only diff.

Final verification: 454 tests, 0 failures, 0 errors, 0 skipped. Final live match `5a40bd14-a820-30be-bebe-27ee5988ea65`, revision 48, Chrome 152.0.7977.83.
