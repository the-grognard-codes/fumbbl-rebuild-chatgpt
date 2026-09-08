# M1b verification — 2026-09-07

**M1b decisions/reconnect acceptance passed. M1c and overall M1 remain open.**
This work builds on the uncommitted M1a working tree and preserves its historical
[evidence](../m1a/README.md). No commit, push, deployment, database reset or change
to local credentials was performed.

## Separate M1b acceptance

All four controlled fixtures passed the Java adapter/oracle checks and an actual
two-browser run on `ffb-server:3.4.0-m1b.1`. The engine starts each fixture paused
at the pending block dialog; no browser command rolls dice or loads scenarios.

| Local fixture | Choice owner / options | Engine and browser result | Browser evidence |
|---|---|---|---|
| `BOTH_DOWN` | home / 1 | both prone; away turn | [JSON](BOTH_DOWN/choice-demo.json), [home wire](BOTH_DOWN/home-wire.json), [away wire](BOTH_DOWN/away-wire.json) |
| `BOTH_DOWN_BLOCK` | home / 1 | attacker standing, defender prone; home turn | [JSON](BOTH_DOWN_BLOCK/choice-demo.json), [home wire](BOTH_DOWN_BLOCK/home-wire.json), [away wire](BOTH_DOWN_BLOCK/away-wire.json) |
| `BOTH_DOWN_AWAY` | away / 2 | both prone; away turn | [JSON](BOTH_DOWN_AWAY/choice-demo.json), [home wire](BOTH_DOWN_AWAY/home-wire.json), [away wire](BOTH_DOWN_AWAY/away-wire.json) |
| `BOTH_DOWN_AWAY_BLOCK` | away / 2 | attacker standing, defender prone; home turn | [JSON](BOTH_DOWN_AWAY_BLOCK/choice-demo.json), [home wire](BOTH_DOWN_AWAY_BLOCK/home-wire.json), [away wire](BOTH_DOWN_AWAY_BLOCK/away-wire.json) |

Each run used two independent Chrome 152.0.7977.77 contexts on Windows, Node
24.19.0, viewport 1440×1080, with distinct home/away credentials read from ignored
local secret files. Credentials are excluded from the recorded wire traces.
Both clients observed the same public snapshot apart from caller `actor`.

Every variant demonstrated:

1. Revision 0 contains the complete server-owned prompt: stable ID, block type,
   choosing actor, revision and all option IDs/labels. The engine owns the choice;
   away can choose while home owns the turn. The observer's buttons are disabled;
   a raw wrong-owner choice is rejected without revision change.
2. Disconnecting the chooser and rejoining through the credential form restores
   the **identical full snapshot**, including the same pending prompt and actor.
3. The chooser submits Both Down through the rendered button. The test intercepts
   and withholds its accepted result and revision-1 snapshot from the application.
   Its rendered view remains at revision 0 while the observer receives exactly
   one resolved revision-1 snapshot.
4. After another disconnect/rejoin, the chooser receives the full resolved state
   at revision 1 with `prompt:null`. Both views agree on player states and turn
   resources; no reroll resource is consumed.
5. **Repeat last request** resends the original ID/body and returns
   `CHOICE_APPLIED`, original revision 1, `duplicate:true`. It produces no second
   observer snapshot. Java tests additionally compare the entire serialized
   engine state before/after the retry and verify that no controlled rolls remain
   or are consumed again.

The Java oracle independently recreates the `BlockTest` sequence with the same
stats, optional Block skill and controlled Both Down/2+2 armor rolls. Assertions
compare both player bases, current step, turn mode and turn owner, plus explicit
expected prone/standing results. Away variants use strength 2 against 3 and zero
rerolls to put the negative two-dice choice directly with the defender. All
fixtures use zero rerolls so the DTO includes every available decision.

## Verification records

Affected-test manifest: `BrowserChoiceTest` (four variants),
`BrowserMatchAdapterTest`, existing `BlockTest`, `ServerCommunicationWorkTest`,
`browser-client/test/protocol.test.ts`, `choice-demo.mjs`, and the original
movement `browser-demo.mjs` as a regression run.

| Check | Evidence |
|---|---|
| Focused Java tests | 17 passed, zero failures/errors: [log](focused-host.log) |
| Java 8 clean install | All eight reactor projects passed, 371 reported / 370 passed / 1 existing disabled, zero failures/errors, 2m39s: [log](clean-install.log) |
| Java 8 clean verify | All eight reactor projects passed, same 371/370/1, zero failures/errors, 2m17s: [log](clean-verify.log) |
| Browser decoder tests | 5 passed: [log](browser-unit.log) |
| TypeScript / Vite build | Passed: [log](browser-build.log) |
| Four live two-browser choice runs | All passed: [home](browser-BOTH_DOWN.log), [home Block](browser-BOTH_DOWN_BLOCK.log), [away](browser-BOTH_DOWN_AWAY.log), [away Block](browser-BOTH_DOWN_AWAY_BLOCK.log) |
| Existing browser movement regression | Passed on M1b image: [log](movement-demo.log), [JSON](movement/browser-demo.json) |
| Container build / final health | [build](container-build.log), [image ID](image-id.txt), [healthy stack](final-stack.log) |

Representative screenshots were visually inspected: [home pending after rejoin](BOTH_DOWN/pending-rejoined.png),
[Block result after rejoin](BOTH_DOWN_BLOCK/resolved-rejoined.png),
[away-owned pending choice](BOTH_DOWN_AWAY/pending-rejoined.png), and
[away resolved Both Down](BOTH_DOWN_AWAY/resolved-rejoined.png). The complete
prompt, role, revision, option controls and resulting player states are legible.
The board uses neutral tokens; the DOM table displays the authoritative state.
Transient engine states such as moving/blocked project as `other`; this is a
deliberately small public projection, not a raw engine snapshot.

An independent read-only review found no actionable ownership, revision,
idempotency or reconnect issues. Its shell lacked Maven on PATH; execution
evidence above comes from the repository's configured `tools/build.ps1` runner.

## Reproduction

From the repository root, use the configured Java 8 toolchain:

```powershell
./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserChoiceTest,BrowserMatchAdapterTest,BlockTest,ServerCommunicationWorkTest' -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
$env:PATH = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
```

Set `local.browser.fixture` in `containers/local/server.ini` to one of the four
table values, then `docker compose -f containers/local/compose.yaml restart server`
and `docker compose -f containers/local/compose.yaml up -d --wait`. These operator
steps replace only the in-memory fixture/history and interrupt local sessions;
the database and backup volumes are preserved.

With `npm run dev` serving `browser-client` on loopback, run from that directory:

```powershell
npm test
npm run build
$env:M1B_FIXTURE = 'BOTH_DOWN'
$env:M1B_EVIDENCE = '../.notes/overhaul-analysis/verification/m1b/BOTH_DOWN'
npm run demo:choice
```

Repeat with the matching environment/configuration value and evidence directory
for each variant. The movement regression instead selects `MOVEMENT`, restarts,
sets `M1A_EVIDENCE=../.notes/overhaul-analysis/verification/m1b/movement`, and runs
`npm run demo`. See [browser instructions](../../../../browser-client/README.md)
and [protocol](../../../../browser-client/protocol.md).

## Corrections made during verification

- The sandboxed Java run failed in ClassGraph initialization, as in M1a. The
  authorized host run used the same configured Java 8/Maven dependencies.
- This module lacks JUnit parameterized-test dependencies; the initial test
  scaffold was changed to four ordinary JUnit 5 tests without adding dependencies.
- The integer `addTestRoll` overload did not represent the intended armor value.
  Fixture setup now uses `addTestRoll("2", game, home)`, exactly as the existing
  `TestRolls` string path does. All four variants then passed.
- Review corrected the browser driver's expected option count to one for home
  and two for away, and added explicit dropped-message synchronization before
  the live runs. No failed live acceptance run was counted as evidence.
- A second attempt to start Vite found port 5173 already occupied; the existing
  local development server was used successfully.

## Limits / next milestone

Only these one-choice local block fixtures and M1a movement are supported. They
are not general playable matches. Each JVM startup generates a new match ID.
Recovery/idempotency is in-memory within that lifetime, with the unchanged
fail-closed 256-request cap. No durable restart recovery is claimed. Same-page
retry retains the original request; page reload loses that cache but still
restores any server-pending decision. Unexpected engine errors quarantine the
disposable fixture. Slow-client delivery policy remains M1c work.

The default configuration was restored to `BOTH_DOWN` and the local server
restarted to a fresh pending revision 0 after all demos. JVM/database are healthy;
the existing loopback Vite server remains available. Automated browser contexts
were closed. No credentials or persistent game data were replaced.

**Next: M1c**, separately evidenced asset/renderer failures, bounded repeat-action
measurements, queue/history/slow-client behavior and protocol closeout. Overall
M1 completion must wait for that acceptance and its measured report.
