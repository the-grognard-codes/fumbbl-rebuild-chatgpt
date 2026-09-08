# M1c affected checks

Preserve the Java BB2025 engine. Focused selectors run before the full reactor:

```powershell
./tools/build.ps1 test -Module ffb-statetest -Test 'BrowserMatchAdapterTest,BrowserChoiceTest,BlockTest,ServerCommunicationWorkTest,BrowserMatchDeliveryTest,BrowserMatchTransportTest,BrowserMatchSocketTest' -Offline
./tools/build.ps1 install -Offline
./tools/build.ps1 verify -Offline
git diff --check
```

- `BrowserMatchAdapterTest`: movement, complete engine state around rejections/retries, fail-closed history, disconnect/rejoin at capacity, original accepted revision, actor/lifetime isolation, executable negative wire fixtures.
- `BrowserChoiceTest`, existing `BlockTest`: all four controlled engine choices, resource/dice invariance and prompt ownership.
- `ServerCommunicationWorkTest`: existing serialized worker ordering, exception survival, shutdown drain.
- `BrowserMatchDeliveryTest`: ordered writes, blocked peer isolation, count/byte overflow, failed/timed-out writes and late callbacks.
- `BrowserMatchTransportTest`, `BrowserMatchSocketTest`: bounded ingress, legacy work survival, overload, shutdown and retired/admission-rejected socket races.
- `browser-client/test/protocol.test.ts`, `wire-fixtures.test.ts`: decoder, complete prompts, captured wire fixtures, monotonic views and historical results.
- `browser-demo.mjs`, `choice-demo.mjs`: original two-browser regressions, output redirected to M1c only.
- `renderer-failure-demo.mjs`: actual asset Sprite/fallback rendering and UI movement, forced WebGL initialization rejection and reload recovery.
- `robustness-demo.mjs`: live history, negative protocol, stale socket callbacks, cross-session cache, real ingress burst, healthy peer and reconnect.
- `workload-demo.mjs`: 1,000 measured submissions across 100 fixture lifetimes; 600 mutations, 200 rejections, 200 retries and 100 reconnects, plus two excluded warm-up lifetimes. The final successful run and raw counts are recorded in the report.

Browser commands run from `browser-client`: `npm test`, `npm run build`, `npm run demo`, `npm run demo:choice`, `npm run demo:renderer-failures`, `node test/robustness-demo.mjs`, `node test/workload-demo.mjs`.

No build/setup script changed; existing pinned toolchain and browser dependencies are reused. Final server image must be rebuilt and acceptance rerun after server source changes.
