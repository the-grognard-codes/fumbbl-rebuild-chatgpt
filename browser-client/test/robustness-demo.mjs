import { chromium } from 'playwright';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
import { control } from './m1c-local.mjs';

const output = resolve(process.env.M1C_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m1c/robustness');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const evidence = { passed: false, cases: [], errors: [] };
const tokens = {};
for (const actor of ['home', 'away']) tokens[actor] = (await readFile(resolve(`../containers/local/.secrets/browser_${actor}_token`), 'utf8')).trim();
try {
  const pages = {};
  for (const actor of ['home', 'away']) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });
    page.on('pageerror', error => evidence.errors.push(error.message));
    await page.addInitScript(() => {
      const Native = window.WebSocket;
      window.__robust = { incoming: [], outgoing: [], sockets: [], socket: null, closes: [] };
      window.WebSocket = class extends Native {
        constructor(...args) { super(...args); window.__robust.socket = this; window.__robust.sockets.push(this); this.addEventListener('message', event => window.__robust.incoming.push(JSON.parse(event.data))); this.addEventListener('close', event => window.__robust.closes.push({ code: event.code, reason: event.reason })); }
        send(raw) { const message = JSON.parse(raw); if (message.type !== 'join') window.__robust.outgoing.push(message); super.send(raw); }
      };
    });
    await page.goto('http://127.0.0.1:5173'); pages[actor] = page;
  }
  const home = pages.home, away = pages.away;
  async function join(page, actor) { await page.getByLabel('Local session credential').fill(tokens[actor]); await page.getByRole('button', { name: 'Join fixture', exact: true }).click(); await page.getByText(`You are ${actor}`, { exact: true }).waitFor(); }
  const snapshot = page => page.evaluate(() => window.__robust.incoming.filter(m => m.type === 'snapshot').at(-1));
  async function send(page, request) {
    const count = await page.evaluate(() => window.__robust.incoming.length);
    await page.evaluate(request => window.__robust.socket.send(JSON.stringify(request)), request);
    await page.waitForFunction(count => window.__robust.incoming.length > count, count);
    return page.evaluate(count => window.__robust.incoming.slice(count).find(m => m.type === 'result'), count);
  }
  const move = (requestId, expectedRevision, x = 6) => ({ version: 1, type: 'move', requestId, expectedRevision, playerId: 'home-runner', to: { x, y: 7 } });
  await control('reset', 'MOVEMENT'); await join(home, 'home'); await join(away, 'away');
  await home.getByRole('button', { name: 'Submit move', exact: true }).click();
  await home.getByTestId('revision').filter({ hasText: 'Revision: 1' }).waitFor();
  const accepted = await home.evaluate(() => window.__robust.outgoing.at(-1));
  assert.equal((await send(home, move('second', 1, 7))).code, 'MOVED');
  const before = await snapshot(home);
  for (const [name, request, expected] of [
    ['changed-body', { ...accepted, to: { x: 8, y: 7 } }, 'REQUEST_ID_REUSED'],
    ['stale', move('stale', 0), 'STALE_REVISION'],
    ['dice-field', { ...move('dice', 2), dice: [6] }, 'MALFORMED_MESSAGE'],
    ['scenario-field', { ...move('scenario', 2), scenario: 'MOVEMENT' }, 'MALFORMED_MESSAGE'],
    ['actor-field', { ...move('actor', 2), actor: 'away' }, 'MALFORMED_MESSAGE'],
    ['unsupported', { version: 1, type: 'reset', requestId: 'reset' }, 'UNSUPPORTED_MESSAGE'],
    ['version', { ...move('version', 2), version: 2 }, 'UNSUPPORTED_VERSION'],
    ['malformed', { ...move('malformed', 2), to: [] }, 'MALFORMED_MESSAGE']
  ]) { const result = await send(home, request); assert.equal(result.code, expected); assert.deepEqual(await snapshot(home), before); evidence.cases.push({ name, request, result }); }
  const wrong = await send(away, move('wrong', 2)); assert.equal(wrong.code, 'WRONG_TURN'); evidence.cases.push({ name: 'wrong-actor', request: move('wrong', 2), result: wrong });
  // 2 accepted + stale + wrong actor occupy four of the shared 256 records.
  for (let index = 0; index < 252; index++) assert.equal((await send(home, move(`fill-${index}`, 2, 20))).code, 'NOT_ADJACENT');
  const cap = await send(home, move('over-cap', 2, 8)); assert.equal(cap.code, 'REQUEST_HISTORY_LIMIT');
  await home.getByRole('button', { name: 'Disconnect', exact: true }).click(); await join(home, 'home');
  const retry = await send(home, accepted); assert.equal(retry.duplicate, true); assert.equal(retry.revision, 1);
  assert.deepEqual(await snapshot(home), before); assert.deepEqual(await snapshot(away), { ...before, actor: 'away' });
  const changed = await send(home, { ...accepted, to: { x: 8, y: 7 } }); assert.equal(changed.code, 'REQUEST_ID_REUSED');
  evidence.cases.push({ name: 'capacity-new-id', request: move('over-cap', 2, 8), result: cap }, { name: 'retained-after-rejoin', request: accepted, result: retry });
  evidence.historyMetrics = await control('metrics');
  await home.screenshot({ path: resolve(output, 'history-rejoin.png'), fullPage: true });
  // Actual socket callback from the retired connection must not overwrite the new join.
  await home.evaluate(oldSnapshot => window.__robust.sockets.find(socket => socket.url.includes('/browser/v1')).onmessage(new MessageEvent('message', { data: JSON.stringify({ ...oldSnapshot, revision: 99 }) })), before);
  assert.equal(await home.getByTestId('revision').textContent(), 'Revision: 2');
  evidence.cases.push({ name: 'stale-socket-callback', passed: true });
  await home.getByRole('button', { name: 'Disconnect', exact: true }).click(); await join(home, 'away');
  assert.equal(await home.getByRole('button', { name: 'Repeat last request' }).isDisabled(), true);
  await control('reset', 'MOVEMENT'); await home.getByLabel('Local session credential').waitFor(); await join(home, 'home'); await join(away, 'away');
  assert.equal(await home.getByRole('button', { name: 'Repeat last request' }).isDisabled(), true);
  evidence.cases.push({ name: 'cross-actor-and-fixture-cache-disabled', passed: true });
  // Real WebSocket ingress flood. This tests admission under a burst, NOT TCP backpressure.
  evidence.beforeFlood = await control('metrics');
  evidence.hold = await control('hold');
  assert.equal(evidence.hold.phase, 'holding');
  await away.evaluate(() => {
    const socket = window.__robust.socket;
    const raw = JSON.stringify({ version: 1, type: 'unsupported', requestId: 'flood', padding: Array(3500).fill(0) });
    for (let index = 0; index < 3000 && socket.readyState === WebSocket.OPEN; index++) socket.send(raw);
  });
  await away.getByLabel('Local session credential').waitFor({ timeout: 15000 });
  await home.getByRole('button', { name: 'Submit move', exact: true }).click();
  await home.getByTestId('revision').filter({ hasText: 'Revision: 1' }).waitFor();
  await join(away, 'away'); assert.equal((await snapshot(away)).revision, 1);
  evidence.afterFlood = await control('metrics');
  evidence.floodClose = await away.evaluate(() => window.__robust.closes.at(-1));
  assert.ok([1013, 1006].includes(evidence.floodClose.code), 'Close frame may be lost during the burst');
  assert.ok(evidence.afterFlood.transport.ingressOverload > evidence.beforeFlood.transport.ingressOverload, 'Server confirms ingress admission overflow');
  evidence.cases.push({ name: 'live-ingress-burst-healthy-peer-and-resync', passed: true, layer: 'Fixed one-second operator hold of communication worker, then 3000 x approximately 7KB requests over real WebSocket; not TCP backpressure' });
  await away.screenshot({ path: resolve(output, 'overload-rejoined.png'), fullPage: true });
  await control('reset', 'BOTH_DOWN_AWAY');
  await join(home, 'home'); await join(away, 'away');
  const pending = await snapshot(away);
  const invalidOption = { version: 1, type: 'choice', requestId: 'invalid-option', expectedRevision: 0, choiceId: pending.prompt.id, optionId: 'not-an-option' };
  const invalidResult = await send(away, invalidOption);
  assert.equal(invalidResult.code, 'INVALID_OPTION'); assert.deepEqual(await snapshot(away), pending);
  evidence.cases.push({ name: 'invalid-choice-option', request: invalidOption, result: invalidResult, unchangedSnapshot: pending });
  assert.deepEqual(evidence.errors, []); evidence.passed = true;
} catch (error) { evidence.failure = error.stack; throw error; }
finally { await writeFile(resolve(output, 'robustness.json'), JSON.stringify(evidence, null, 2)); await browser.close(); }
