import { chromium } from 'playwright';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

// Local test driver only: credentials never appear in the app bundle or evidence.
// Restart the local server before running; this asserts a fresh revision-zero fixture.
const output = resolve(process.env.M1A_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m1a');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const observations = [];
const errors = [];
try {
  const sessions = {};
  for (const actor of ['home', 'away']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } });
    const page = await context.newPage();
    page.on('pageerror', e => errors.push(`${actor}: ${e.message}`));
    await page.addInitScript(() => {
      const NativeSocket = window.WebSocket;
      window.__m1a = { incoming: [], outgoing: [], socket: null };
      window.WebSocket = class extends NativeSocket {
        constructor(...args) {
          super(...args); window.__m1a.socket = this;
          this.addEventListener('message', event => window.__m1a.incoming.push(JSON.parse(event.data)));
        }
        send(raw) {
          const message = JSON.parse(raw);
          if (message.type !== 'join') window.__m1a.outgoing.push(message);
          super.send(raw);
        }
      };
    });
    await page.goto('http://127.0.0.1:5173');
    const token = (await readFile(resolve(`../containers/local/.secrets/browser_${actor}_token`), 'utf8')).trim();
    await page.getByLabel('Local session credential').fill(token);
    await page.getByRole('button', { name: 'Join fixture' }).click();
    await page.getByText(`You are ${actor}`, { exact: true }).waitFor();
    await page.waitForFunction(() => !!document.querySelector('canvas'));
    sessions[actor] = { context, page };
  }
  const home = sessions.home.page, away = sessions.away.page;
  const snapshot = page => page.evaluate(() => window.__m1a.incoming.filter(m => m.type === 'snapshot').at(-1));
  const initialHome = await snapshot(home), initialAway = await snapshot(away);
  assert.equal(initialHome.revision, 0, 'Restart local server before the demo');
  assert.equal(initialHome.actor, 'home'); assert.equal(initialAway.actor, 'away');
  const publicView = ({ actor, ...view }) => view;
  assert.deepEqual(publicView(initialHome), publicView(initialAway));
  const runner = initialHome.players.find(p => p.id === 'home-runner');
  assert.deepEqual([runner.x, runner.y, runner.movementUsed], [5, 7, 0]);
  await home.screenshot({ path: resolve(output, 'home-before.png'), fullPage: true });
  async function submit(page, x, y) {
    const before = await page.evaluate(() => window.__m1a.incoming.filter(m => m.type === 'result').length);
    await page.getByLabel('Destination X', { exact: true }).fill(String(x));
    await page.getByLabel('Destination Y', { exact: true }).fill(String(y));
    await page.getByRole('button', { name: 'Submit move', exact: true }).click();
    await page.waitForFunction(n => window.__m1a.incoming.filter(m => m.type === 'result').length > n, before);
    return page.evaluate(() => window.__m1a.incoming.filter(m => m.type === 'result').at(-1));
  }
  const wrongRole = await submit(away, 6, 7);
  assert.equal(wrongRole.status, 'rejected');
  assert.equal(wrongRole.revision, 0);
  observations.push({ scenario: 'wrong-role', result: wrongRole });
  for (const [x, y] of [[26, 7], [8, 7], [5, 7]]) {
    const result = await submit(home, x, y);
    assert.equal(result.status, 'rejected'); assert.equal(result.revision, 0);
    observations.push({ scenario: `illegal-${x}-${y}`, result });
  }
  assert.deepEqual(await snapshot(home), initialHome);
  const accepted = await submit(home, 6, 7);
  assert.equal(accepted.status, 'accepted'); assert.equal(accepted.revision, 1);
  for (const page of [home, away]) await page.waitForFunction(() => window.__m1a.incoming.some(m => m.type === 'snapshot' && m.revision === 1));
  const afterHome = await snapshot(home), afterAway = await snapshot(away);
  assert.deepEqual(publicView(afterHome), publicView(afterAway));
  const moved = afterHome.players.find(p => p.id === 'home-runner');
  assert.deepEqual([moved.x, moved.y, moved.movementUsed], [6, 7, 1]);
  assert.deepEqual(afterHome.resources, initialHome.resources);
  observations.push({ scenario: 'accepted-move', result: accepted, home: afterHome, away: afterAway });
  const count = await home.evaluate(() => window.__m1a.incoming.length);
  await home.getByRole('button', { name: 'Repeat last request' }).click();
  await home.waitForFunction(n => window.__m1a.incoming.length > n, count);
  const duplicate = await home.evaluate(() => window.__m1a.incoming.filter(m => m.type === 'result').at(-1));
  assert.equal(duplicate.status, 'accepted'); assert.equal(duplicate.duplicate, true); assert.equal(duplicate.revision, 1);
  observations.push({ scenario: 'duplicate', result: duplicate });
  // Test-only raw wire submission through the real authenticated browser socket.
  const staleId = 'demo-stale-1';
  await home.evaluate(id => window.__m1a.socket.send(JSON.stringify({ version: 1, type: 'move', requestId: id, expectedRevision: 0, playerId: 'home-runner', to: { x: 7, y: 7 } })), staleId);
  await home.waitForFunction(id => window.__m1a.incoming.some(m => m.requestId === id), staleId);
  const stale = await home.evaluate(id => window.__m1a.incoming.find(m => m.requestId === id), staleId);
  assert.equal(stale.status, 'rejected'); assert.equal(stale.code, 'STALE_REVISION'); assert.equal(stale.revision, 1);
  observations.push({ scenario: 'stale', result: stale });
  assert.deepEqual(await snapshot(home), afterHome); assert.deepEqual(await snapshot(away), afterAway);
  for (const [actor, { page }] of Object.entries(sessions)) {
    await page.screenshot({ path: resolve(output, `${actor}-after.png`), fullPage: true });
    const trace = await page.evaluate(() => ({ incoming: window.__m1a.incoming, outgoing: window.__m1a.outgoing }));
    assert.equal(trace.incoming.filter(m => m.type === 'snapshot' && m.revision === 1).length, 1, 'Exactly one broadcast per accepted action');
    await writeFile(resolve(output, `${actor}-wire.json`), JSON.stringify(trace, null, 2));
  }
  assert.deepEqual(errors, []);
  await writeFile(resolve(output, 'browser-demo.json'), JSON.stringify({ passed: true, browser: await browser.version(), node: process.version, platform: process.platform, viewport: '1440x1080', sessions: 'Two independent Playwright BrowserContexts with distinct local credentials', observations, errors }, null, 2));
  console.log('PASS two independent browser contexts: initial sync, wrong-role/illegal rejection, one legal engine move, duplicate no-op, stale rejection; both revision 1.');
} finally { await browser.close(); }
