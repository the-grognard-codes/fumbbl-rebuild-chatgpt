# M1a verification — 2026-09-07

Started at `4fd75d0d0` with a clean working tree. No commit, push, deployment or
database reset. Retained Java 8 rules and desktop behavior; added only the local
browser adapter, queue work seam, two-token fixture, client, tests, local secrets
configuration, image version and documentation/change-list entry.

## Actual acceptance

`npm run demo` passed against the final locally built
`ffb-server:3.4.0-m1a.1` image. [Image ID](image-id.txt), [build log](container-build.log),
[stack health](stack-status.log), [browser result](browser-demo.json),
[home wire](home-wire.json), [away wire](away-wire.json).

Two independent Chrome 152.0.7977.77 browser contexts on Windows used distinct
home/away credentials. Node was 24.19.0; viewport was 1440×1080. Both joined through
the rendered UI and initially saw home (5,7), away (20,7), ball (12,7), home turn,
no pending choice, movement 0/6 and three rerolls each at revision 0.

Away submitted a move and received `WRONG_TURN`. Home attempted (26,7), (8,7), and
(5,7), receiving off-board/nonadjacent rejections without a new snapshot/revision.
Home then submitted (6,7), receiving `MOVED` at revision 1. Both views reached that
same revision/coordinate with movement 1/6 and unchanged turn resources. Exactly one
revision-1 snapshot arrived per browser. Repeating the accepted request returned
its original accepted revision with `duplicate:true`, without another snapshot or
movement. A new request with expected revision 0 then returned `STALE_REVISION`
at revision 1. No browser page errors were observed. Credentials were excluded
from saved traces; the test driver reads them directly from ignored local files.

[Home before](home-before.png) · [Home after](home-after.png) · [Away after](away-after.png).
Both final screenshots were visually inspected: the neutral-token board, role,
revision, coordinates, movement/resources and request results render correctly.

## Exact successful commands

PowerShell, repository root unless indicated. Network/Docker and JVM tests used
authorized execution outside the sandbox when needed; no global software install.

```powershell
$env:PATH = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
./containers/local/setup.ps1
docker compose -f containers/local/compose.yaml up -d --wait
# After adapter implementation (rebuilt once after final source cleanup):
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
docker compose -f containers/local/compose.yaml ps
docker image inspect ffb-server:3.4.0-m1a.1 --format '{{.Id}}'
docker compose -f containers/local/compose.yaml exec -T server java -cp 'FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.local.LocalAcceptanceDemo ready

./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserMatchAdapterTest,ServerCommunicationWorkTest,ShadowingTest' -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
./tools/test-tooling.ps1
git diff --check

# Browser dependency resolution/installation, in browser-client:
node --version
npm --version
npm view react version
npm view pixi.js version
npm view vite version
npm view typescript version
npm view @types/react version
npm view @types/react-dom version
npm install --no-audit --no-fund
npm install --save-dev --save-exact playwright@1.62.1 --no-audit --no-fund
npm test
npm run build
npm run dev
# Second terminal, also in browser-client; fresh fixture after final image refresh:
npm run demo
```

Final logs were redirected with `*> .notes/overhaul-analysis/verification/m1a/<name>.log`
from the root (or `*> ../.notes/...` from browser-client). `npm run dev` remains
running on loopback; the JVM/database remain healthy, with the browser fixture at
revision 1. The automated browser contexts were closed after screenshots. No
changes to existing database/backup volumes or fixture coach secrets were made.

| Check | Result/evidence |
|---|---|
| Focused engine + adapter + queue | 11 passed: adapter 8, communication queue 1, existing Shadowing 2; [run record](focused-adapter.log) |
| Java 8 clean install | All 8 reactor projects succeeded, 367 reported / 366 passed / 1 existing disabled, 0 failures/errors, 2m37s; [log](clean-install.log) |
| Java 8 clean verify | All 8 reactor projects succeeded, same 367/366/1, 0 failures/errors, 2m25s; [log](clean-verify.log) |
| Tooling | 9 checks passed; [log](tooling.log) |
| TypeScript + Vite production build | Passed; [log](browser-build.log) |
| Browser decoder/revision tests | 4 passed; includes malformed resource projection; [log](browser-unit.log) |
| Two-context browser acceptance | Passed on final image; [console](browser-demo.log), [structured observations](browser-demo.json) |
| Local image and health | Build included common/server suites; JVM/database healthy, loopback publication; [build](container-build.log), [status](stack-status.log) |
| Whitespace check | `git diff --check` passed; only normal Windows LF/CRLF notices |

Affected-test manifest: `BrowserMatchAdapterTest`, `ServerCommunicationWorkTest`,
existing BB2025 `ShadowingTest`, and `browser-client/test/protocol.test.ts`.
Adapter tests compare full serialized `GameState` around rejected/duplicate
requests, test accepted engine movement, actor/player authority, occupied/bounds/
adjacency/allowance, stale revisions, malformed/version/unknown/dice input,
unauthenticated/invalid-token joins, identity rebinding, semantic dedupe and
fail-closed 256-request history retention. Queue test verifies FIFO, exception
survival and shutdown rejection. Browser demo checks resources plus both views.

## Failed attempts and corrections

- Sandbox Docker lookup reported command unavailable; direct installed executable
  returned access denied. The same documented startup worked with authorized
  host execution. Docker was already installed; no reinstall was performed.
- Sandbox `npm view` calls failed with EACCES fetching registry data/writing npm
  cache. Authorized resolution/installation succeeded with exact versions pinned.
- Focused JVM tests initially failed before test setup in ClassGraph FileUtils
  initialization under the sandbox. Authorized execution ran the tests.
- A malformed `version:"one"` exposed parsing outside the rejection boundary;
  parsing now completes before any mutation. Initial test expectations for malformed
  destination and a ball-square exhaustion attempt were corrected; movement
  exhaustion now takes priority before dice gating. Accepted-result tests were
  corrected to distinguish the response from the following snapshot.
- First frontend build hit TS2882 for the CSS side-effect import; added the Vite
  client type reference, then build passed.
- Review found missing resource DTO validation in the browser; added typed home/
  away resources, runtime validation and negative decoder tests before final demo.

## Limits and next action

This proves **M1a only**. The two-token in-memory fixture is constructed at local
startup; it is not a persisted/complete match or validated roster. Game ID -1 is
never registered in the legacy game cache/session manager/JDBC. It uses trusted
internal home commands only after browser authorization. Java BB2025 Select/Move
sequences and `GameState.handleCommand` execute movement unchanged. Browser
request work uses the existing single communication worker.

Supported intents exclude dice-dependent movement/pickup, blocks and decisions.
Protocol is v1, text JSON, maximum text message 16 KiB; strict field allowlists
exclude fixture/dice controls. History is in memory, capped at 256 IDs without
evicting accepted requests; new IDs fail closed when full. JVM restart resets the
fixture/revision/history, so no durable idempotency or restart/reconnect claim.
Unexpected engine failures quarantine the disposable fixture and close the socket;
there is no transactional recovery claim. Current fixed fixture has no legacy
sessions or timers mutating it. M1b must revisit audience/ownership for decisions.

The browser has a WebGL error message, but forced renderer/asset failures, a 1,000
action run, latency/memory/payload envelope, slow-client policies and protocol
closeout belong to M1c. This run did not validate public security, TLS, supported
Jetty modernization, Java 21, a hosted CI run, offline browser delivery or a cloud
deployment. Normal Compose allows outbound networking; no new FUMBBL dependency
was added, and all client requests in this demo used localhost. M0b offline network
evidence remains its separate historical proof.

**Next:** M1b — use the existing `BlockTest` Both Down variants and local test dice
as the oracle; add server-owned pending choices and full resync after disconnect.
Define prompt owner and revision, protect duplicate choices from consuming twice,
keep scenario/dice controls in local setup, then demonstrate home/away decision
parity and reconnect. Read kickoff, protocol, this evidence, and `BlockTest` first.
