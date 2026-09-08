# M1c browser robustness and M1 closeout — 2026-09-07

Status: **M1c complete; overall M1 complete**, 2026-09-07. Implementation,
acceptance and measured workload passed; independent closeout review approved
the evidence and recommendation to proceed toward M2. See [review](review.md).

This work preserves the pre-existing uncommitted M1a/M1b implementation and their
historical evidence. [Baseline status](baseline-status.txt) records the starting
tree. No engine rule was changed, and no commit, push, deployment, credential
replacement, global installation or database/backup-volume deletion occurred.

## Implementation and decisions

- `BrowserMatchDelivery` uses ordered asynchronous writes with at most 64 messages
  and 256 KiB UTF-8 payload per connection, including in-flight payload. Full
  snapshots are conceptually replaceable, correlated results are not; this
  implementation coalesces neither and disconnects/resyncs on overflow.
- `BrowserMatchTransport` admits at most 16 connections and 128 browser requests
  across queued and in-flight work. A semaphore bounds browser contributions to
  the existing communication worker. Legacy engine work and shutdown markers
  retain their existing queue behavior. At most 16 connection-cleanup jobs bypass
  browser admission; a connection permit is held until cleanup completes.
- A shared daemon watchdog checks monotonic write age every 100 ms and terminates
  stalled writes at two seconds (plus scheduling delay). Jetty's two-second async
  timeout is retained as an additional mechanism. Terminal overflow/failure/
  timeout clears buffers, retires authorization immediately, and tolerates late
  callbacks. Socket retirement prevents previously queued frames from touching a
  replacement fixture. Registration is serialized with transport destruction.
- The unchanged 256-entry history fails closed. Original accepted revisions,
  exact retries and changed-body rejection survive disconnect/rejoin. Admission
  rejection before adapter execution occupies no history. A lost connection can
  conceal an already accepted result: reconnect, inspect the full snapshot, then
  explicitly retry the original request in the same match/actor lifetime.
- `BrowserFixtureControl` is an explicitly enabled, bounded container-file
  mailbox under `/tmp`, not a network endpoint. One operator request at a time
  enters the communication worker. Reset retires connections and replaces the
  fixture/history with a new match ID; nothing is registered in legacy game cache,
  JDBC or timers. Old in-memory state becomes collectible. This permits repeated
  legal actions while keeping the JVM running. No gameplay reset/dice/scenario
  command was added.
- The local diagnostic SVG loads, decodes and becomes a Pixi texture/Sprite.
  Missing mapping and unsuccessful load produce `F H` / `F A` tokens and status
  text. One attempt is made per board lifetime. Object URLs, texture resources,
  children, listeners and late-initialized applications are released on teardown.
- Real WebGL initialization rejection is caught. DOM selection, submission,
  authoritative state and request results remain usable. Graphics require a
  supported/enabled WebGL environment and reload; there is no Canvas fallback.
  Runtime context loss has a handler, but this evidence specifically exercises
  initialization failure, not arbitrary GPU/context-loss recovery.

Gameplay fields remain M1b-compatible protocol v1. The executable positive wire
fixtures derive from the M1b image's movement and four choice traces. M1a's older
capture predates M1b's required player `state` field, so it is historical evidence,
not a current decoder fixture. Negative fixtures deliberately contain forbidden
dice/scenario/actor fields to prove rejection; no such fields are server output.
No credentials, internal Java class names or raw engine state are public fixtures.

## Verification and failure-path evidence

Final local server image: `ffb-server:3.4.0-m1c.1`, Linux amd64,
`sha256:b036eed3eef1215369f423218ff527d461f3fe49e28279511d8431b2d9b93deb`.
[Image ID](image-id.txt), [image build](container-build.log),
[container Java](container-java.txt). Host baseline remains Temurin
8u504-b01/Maven 3.9.9; the pinned container runtime is Temurin 8u502-b07.
There is no Java 21 or byte-identical-build claim.

| Check | Result and evidence |
|---|---|
| Focused adapter/choice/block/communication/transport | 31 passed plus one additional engine/blocked-delivery/rejoin check, zero failures; [manifest](test-manifest.md), [log](focused-final.log), [additional check](focused-blocked-engine.log) |
| Required offline clean install | All eight reactor projects passed, 385 reported / 384 passed / one existing disabled; [log](clean-install.log) |
| Required offline clean verify | All eight reactor projects passed, 386 reported / 385 passed / one existing disabled, including the added engine/blocked-delivery/rejoin test; [log](clean-verify.log), [suite results](test-results.csv) |
| Browser decoder/protocol tests | Seven passed; [log](browser-unit.log) |
| TypeScript and Vite production build | Passed; [log](browser-build.log) |
| Movement and all four choice regressions | Passed on final server image; separate M1c traces/screenshots linked below |
| Valid/missing/failed asset | Actual UI move accepted at revision 1 in all three modes, selection redrawn 20 times; one valid load (200 SVG), zero missing-mapping loads, one failed load; [structured evidence](renderer-failure-demo.json), [log](renderer-failure-demo.log) |
| WebGL init failure and reload | DOM-only move accepted; no page errors; reload/rejoin renders authoritative revision 1; same structured evidence |
| Early teardown during pending initialization | Delayed application factory test verifies exactly one disposal and no attached canvas; [log](renderer-lifecycle-demo.log). This test isolates lifecycle with a fake delayed application; the separate failure demo uses actual WebGL initialization |
| History/cross-session/stale callbacks/live burst | [Structured observations](robustness/robustness.json), [driver log](robustness-demo.log) |
| Final-image independence | Fresh startup on internal-only network and two real WebSocket peers resolve a controlled choice; [probe](offline-browser-probe.log), [network](offline-network.json), [internal flag](offline-network-internal.txt) |
| Measured workload | 1,000 submissions passed, no failures/timeouts; [raw measurements](workload/workload.json), [summary](workload/measurements.md), [progress log](workload-demo.log) |
| Whitespace / credential scan | `git diff --check` passed; [log](diff-check.log). Saved text artifacts contain no current local credentials; [scan](sanitization.txt) |

Asset failures tested: absent mapping, and a requested nonexistent SVG route
whose response fails the SVG status/content-type check. No corrupt GPU texture,
network black-hole, general asset catalog or production artwork claim is made.
Screenshots visually inspected: [valid marker](valid-asset.png),
[missing mapping](missing-mapping.png), [failed load](failed-load.png),
[WebGL error with accepted DOM move](webgl-init-failure.png), and
[reload recovery](webgl-recovered.png). Fallback labels, selection ring, ball,
state table and accepted result are legible. The WebGL view has an explicit
error below the pitch and usable controls/state alongside it.

Transport tests block a deterministic asynchronous sink callback. They verify
healthy delivery, FIFO, count/UTF-8 byte overflow, timeout, failed write,
late-callback accounting and cleanup. This is the application write boundary,
not a measured operating-system TCP send-buffer saturation test. The live burst
uses a fixed one-second operator-only communication-worker hold and sends 3,000 approximately 7-KiB JSON-array-bearing messages through a real browser WebSocket. The hold creates reproducible admission pressure; it is excluded from the latency workload. Server
ingress reaches its 128 bound and records overload; the healthy peer then accepts
a move and the affected peer rejoins revision 1. Chrome observed close 1006,
because the close frame was not reliably delivered during the burst; server
policy attempts 1013. No accepted outcome is inferred from either close code.
Pausing JavaScript callbacks is not used as evidence of TCP backpressure.
An additional integration test runs the actual adapter/engine through a blocked
delivery sink: the accepted move still reaches the healthy observer, buffers
overflow closed, a new peer restores revision 1, and the original accepted retry
does not mutate state or broadcast again.

History acceptance fills all 256 entries after two accepted moves, verifies a
new legal ID receives `REQUEST_HISTORY_LIMIT`, rejoins, then gets original
revision 1 with `duplicate:true` while current revision is 2. Changed bodies are
still rejected; observer/state remain unchanged. Java tests compare complete
serialized engine state and choice dice/resources. The live driver also invokes
a retired gameplay socket's callback and verifies it cannot overwrite the new
view, disables cached retries across actor/match changes, and rejects invalid
choice options and unsupported fields.

## Measured 1,000-submission workload

**Passed:** 100 fixture lifetimes × six accepted legal moves, two rejected
submissions and two exact retries = **1,000 measured submissions: 600 mutations,
200 rejections, 200 duplicates, and 100 reconnects**. Two warm-up lifetimes (20
additional submissions, including 12 moves) are excluded. No failures or timeouts.
The JVM and Chrome browser process (PID 35452) stayed running throughout. JVM
uptime increased continuously from 79,572 ms at baseline to 268,283 ms after idle.
Measured movement samples span 20:07:40–20:10:29 UTC; final idle sample at 20:10:49.
The server's lifetime counter ended at 104: startup fixture, two warm-ups, 100
measured movement fixtures and the final fresh Both Down fixture.

| Metric | Samples | p50 ms | p95 ms | Maximum ms |
|---|---:|---:|---:|---:|
| Accepted send → authoritative render submission | 600 | 18.30 | 23.40 | 38.10 |
| Accepted send → correlated result receipt | 600 | 5.20 | 6.60 | 22.10 |
| Reconnect → full snapshot and new render submission | 100 | 39.80 | 50.20 | 72.50 |

Both provisional latency checkpoints passed with the completion definitions below.
Actual received movement snapshots were 623 UTF-8 bytes (600 samples); result
payloads had p50/p95/max 146 bytes (1,000 samples). Compact reserialization of the
four choice traces spans 619–794 bytes for snapshots and 141–155 bytes for results;
[sizes](choice-payload-sizes.csv). These exclude WebSocket/TCP framing.

During the measured workload, ingress and outbound high-water depths were one;
outbound high-water payload was 623 bytes. Every sampled queue depth/byte count
was zero at the measurement pauses and after cleanup. Cumulative ingress queue
delay increased by 135 ms from baseline to final sample (140 ms JVM-lifetime
total); this is an aggregate delay counter, not a queue-delay percentile.
Outbound queue delay sum/max was zero at millisecond resolution. Workload
admission failures, ingress/outbound overloads and slow-client disconnects were
all zero. The separate saturation test reached ingress depth 128 and one overload;
the blocked-writer/timeout exercises are deterministic test-boundary evidence.

JVM used heap varied with collection (80.29, 182.38, 73.48, 173.94 MiB at movement
lifetimes 25/50/75/100). Process RSS increased during warm-up/heap commitment,
then was 422.65 and 425.70 MiB at lifetimes 75 and 100, and 425.71 MiB after idle.
It did not return to the initial 230.01 MiB RSS. This retained process capacity
must not be described as a return to baseline or zero memory cost.

Chrome process working-set sums were 1,435.92, 1,411.54, 1,383.78 and 1,439.71 MiB
at the same comparable samples, falling to 1,170.59 MiB after idle. Home/away JS
heaps ended at 17.77/17.47 MiB after idle, below baseline 20.69/18.12 MiB. DOM node
and listener counts fell after cleanup; JVM threads stayed at 25 after warm-up.
History was eight entries per completed movement lifetime and zero after final
retirement; active/authorized connections were two during play and zero afterward.
The finite run shows collection, a late RSS plateau, bounded queues/history and
cleanup, with no observed unbounded trend. It does not prove absence of all leaks.

Machine: Windows 11 Home 64-bit, build 10.0.26200; AMD Ryzen 7 260 with Radeon 780M,
16 logical CPUs, 16,438,054,912 bytes physical RAM; headless Chrome 152.0.7977.77,
Node 24.19.0, npm 11.17.0. Two independent 1440×1080 browser contexts used the
pinned React/Pixi/Vite dependencies. Java/container identity is recorded above.
Builds and injected holds had finished before timing. Vite development serving,
Playwright/CDP instrumentation and normal host background activity are included;
this is not a production-load or GPU-memory benchmark.

See [raw data](workload/workload.json), [compact data](workload/summary.json),
[complete memory table](workload/measurements.md), [OS](os.json), and
[source hashes](source-hashes.csv). Rerun `node test/summarize-workload.mjs` from
`browser-client` to regenerate the summary. Both final workload screenshots were
visually inspected: [home](workload/home-final.png), [away](workload/away-final.png).

Timing definition: browser `performance.now()` immediately before native
WebSocket send to the matching authoritative snapshot drawn by `BoardView`, after
explicit `app.render()` returns and its revision/match/render-sequence marker is
observed. This measures CPU submission of the Pixi render, not a GPU fence,
display scanout or human-visible pixel timing. Result latency ends at receipt of
the correlated result. Reconnect timing starts after credential fill, before
automated Join click, and requires the fresh join snapshot and a new render
sequence, even when revision is unchanged. Manual credential-entry time is
excluded; automated click and join processing are included. Nearest-rank p50/p95
and maximum are calculated from all successful measured samples.

JVM heap/nonheap values come from memory MXBeans; process RSS from Linux
`/proc/1/status`; Docker container memory from `docker stats`. Browser page JS
heap/DOM counters come from CDP, and Chrome process working-set/private bytes
from Windows process queries for CDP-reported PIDs. These are different scopes,
not interchangeable memory measures. No forced GC or process restarts hide
retention. Sampling occurs after warm-up, after 25/50/75/100 lifetimes, and after
disconnect/fixture retirement plus 15 seconds idle. A finite run can show
observed stability and enforced bounds, not prove the absence of every leak.

## Every original M1 acceptance row

| Original scenario | Specific evidence and artifact |
|---|---|
| Initial sync | [M1a](../m1a/README.md) on m1a.1; refreshed [M1c movement](movement/browser-demo.json) on m1c.1 compares both public views, coordinates, ball, turn and resources; four choice traces cover pending prompt |
| Move | [M1a](../m1a/README.md); [M1c movement](movement/browser-demo.json), [home](movement/home-wire.json), [away](movement/away-wire.json): one accepted update and one broadcast per viewer |
| Invalid action | M1a adapter invariance; M1c focused tests and [live robustness](robustness/robustness.json), wrong actor, illegal/off-board, stale, malformed and invalid choice options |
| Dice-dependent choice | [M1b oracle/evidence](../m1b/README.md) on m1b.1; refreshed [Both Down](BOTH_DOWN/choice-demo.json), [Block](BOTH_DOWN_BLOCK/choice-demo.json), [away](BOTH_DOWN_AWAY/choice-demo.json), [away Block](BOTH_DOWN_AWAY_BLOCK/choice-demo.json) on m1c.1 |
| Duplicate delivery | M1a/M1b engine invariance plus M1c capacity/rejoin historical revision test, [robustness](robustness/robustness.json) and measured workload retries |
| Reconnect at decision | M1b evidence plus all four refreshed M1c choice runs: full pending prompt identity/owner/revision/options and lost-result recovery without repeat mutation |
| Asset failure | M1c real SVG/fallback path, UI moves, request counts and [screenshots/observations](renderer-failure-demo.json) |
| Renderer failure | M1c real WebGL init rejection, DOM gameplay, no page errors and reload recovery; [observations](renderer-failure-demo.json) |
| Independent operation | Historical [M0b](../m0b/README.md), image sha256:66edaea871f43070fe294e568f3adf6664e8d6e122a57a32da3c84cbbf73ac95; M1c changed server transport/lifecycle, so fresh verification was warranted and performed on m1c.1 with only an internal network, empty gateway, and real browser-protocol choice [probe](offline-browser-probe.log) |

The provisional performance checkpoints remain accepted action-to-render p95
below 250 ms, localhost reconnect below two seconds, and no observed unbounded
growth during 1,000 submissions. Results must be compared without changing those
thresholds after measurement.

## Reproduction and corrections

From repository root:

```powershell
./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserMatchAdapterTest,BrowserChoiceTest,BlockTest,ServerCommunicationWorkTest,BrowserMatchDeliveryTest,BrowserMatchTransportTest,BrowserMatchSocketTest' -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
$env:PATH = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin;$env:PATH"
docker compose -f containers/local/compose.yaml build server
docker compose -f containers/local/compose.yaml up -d --wait
cd browser-client
npm test
npm run build
# Existing Vite on 127.0.0.1:5173 was reused; otherwise npm run dev in another terminal.
./test/run-m1c.ps1
```

The script preserves M1a/M1b directories and writes all outputs to M1c. Run
`-SkipWorkload` while build processes are active, then `node test/workload-demo.mjs`
when they finish. To repeat only one part, use commands in [test manifest](test-manifest.md)
and [browser instructions](../../../../browser-client/README.md). No setup script
or dependency changed, so tooling-check changes were unnecessary.

Fresh independent-network test, from root (interrupts local sessions, preserves
volumes, and restores normal routing afterward):

```powershell
docker compose -f containers/local/compose.yaml -f containers/local/compose.offline.yaml up -d --wait
docker inspect ffb-local-m0b-server-1 --format '{{json .NetworkSettings.Networks}}'
docker network inspect ffb-local-m0b_isolated --format '{{.Internal}}'
docker compose -f containers/local/compose.yaml -f containers/local/compose.offline.yaml exec -T server java -cp 'FantasyFootballServer.jar:lib/*' com.fumbbl.ffb.server.local.BrowserLocalProbe
docker compose -f containers/local/compose.yaml up -d --wait
```

Corrections recorded honestly:

- Docker inspection inside the sandbox returned access denied; the installed
  executable worked with authorized host execution. Nothing was installed.
- An early renderer driver ran before the M1c image and its operator reset timed
  out. It did not count as final acceptance.
- Review found and fixed retirement, admission/registration/shutdown and queue
  accounting races; late renderer initialization cleanup was also repaired.
  Focused regressions cover these paths. An earlier passing install preceded
  final review fixes; the required install and verify were rerun afterward.
- First asset seam only fetched bytes; it was corrected to decode and render a
  texture. Vite's inlining then defeated request-count assertions, so the
  diagnostic asset now has an explicit local public URL. An ambiguous Player
  selector was corrected. [Selector attempt](renderer-failure-selector-attempt.log),
  [inlining attempt](renderer-inline-asset-attempt.log).
- A stale-socket test initially selected Vite's development socket; it now selects
  `/browser/v1`. A live burst's 1006 close invalidated a test expecting a delivered
  1013 frame; the corrected test requires server overload evidence plus full
  recovery. [First selector attempt](robustness-socket-selector-attempt.log),
  [close-code observations](robustness-close-code-attempt.json).
- One required verify attempt hit a Windows file lock copying unchanged
  `SwiftvineGlimmershard.png`; full offline verify subsequently passed.
  [Failed environmental attempt](verify-file-lock-attempt.log).
- Repeating a burst against an idle, warmed server did not always saturate
  admission. The original test's unconditional-disconnect expectation was wrong.
  A fixed one-second operator-only queue hold now makes that exercise reproducible.
  It required another final image rebuild and full checks; the earlier logs are
  retained as `*-before-hold-seam.log`. No hold is used in performance sampling.
  [Unsaturated attempt](robustness-unsaturated-burst-attempt.json),
  [parser-intensive unsaturated attempt](robustness-unsaturated-array-attempt.json).

## Limits, final state and closeout

Recommendation: proceed toward M2 with the narrow browser-adapter approach. The
Java engine required no rule changes, controlled decisions retain engine parity,
and measured local transport/render behavior supports continuing this boundary.
Final independent evidence review approved the counts, recomputed latency
percentiles, qualified memory conclusions, all nine acceptance mappings, final
artifact identity and service state. M1c and overall M1 are complete; proceeding
toward M2 is supported within the documented prototype scope.

Final services: the final M1c JVM and existing MariaDB are healthy; JVM published
only at `127.0.0.1:22227`, Vite remains on `127.0.0.1:5173`. Normal Compose routing
was restored after internal-only verification. Database and backup volumes and
all credentials are preserved. The configured/default fixture is `BOTH_DOWN`,
fresh revision 0, pending home choice, zero history/authorized/active browser
connections. The operator mailbox remains explicitly enabled in local server.ini.
All automated browser contexts/processes were closed after the idle sample.
[Final stack](final-stack.log), [fixture/queue state](final-metrics.json),
[container identity/state](final-container-state.txt), [server log](final-server.log).

No required M1 acceptance row remains unverified. Unverified beyond this milestone:
actual OS TCP send-buffer saturation, GPU presentation timing/memory, general
runtime context-loss recovery, extended multi-hour load, public auth/TLS and
durable restart recovery. M2 owns catalog/team validation, frozen roster data, saved teams
and match ownership services. M3 owns a full match loop. M4 owns supported
Java/Jetty modernization, public auth/TLS/routing, durable restart recovery,
production load envelope and broader renderer/asset resilience. M5 owns mobile
and broader content. The local probe is not public-service readiness.

