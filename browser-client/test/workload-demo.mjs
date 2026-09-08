import { chromium } from 'playwright';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { control, serverMemory, dockerCall } from './m1c-local.mjs';

const output = resolve(process.env.M1C_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m1c/workload');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const samples = [], reconnects = [], memory = [], errors = [];
const sessions = {};
const report = { passed: false, samples, reconnects, memory, errors, machine: { platform: os.platform(), release: os.release(), cpu: os.cpus()[0].model, logicalCpus: os.cpus().length, ramBytes: os.totalmem(), node: process.version, browser: await browser.version() } };
const browserCdp = await browser.newBrowserCDPSession();
async function captureMemory(phase) {
  const pages = {};
  for (const [actor, { cdp }] of Object.entries(sessions)) pages[actor] = { performance: (await cdp.send('Performance.getMetrics')).metrics, dom: await cdp.send('Memory.getDOMCounters') };
  const processes = (await browserCdp.send('SystemInfo.getProcessInfo')).processInfo;
  const ids = processes.map(p => Number(p.id));
  assert.ok(ids.every(Number.isSafeInteger));
  const { stdout } = await promisify(execFile)('powershell.exe', ['-NoProfile', '-Command', `Get-Process -Id ${ids.join(',')} -ErrorAction SilentlyContinue | Select-Object Id,WorkingSet64,PrivateMemorySize64 | ConvertTo-Json -Compress`]);
  memory.push({ phase, at: new Date().toISOString(), operator: await control('metrics'), server: await serverMemory(), pages, browserProcesses: processes, browserProcessMemory: JSON.parse(stdout) });
}
async function join(actor) {
  const { page, token } = sessions[actor];
  await page.getByLabel('Local session credential').fill(token);
  const elapsed = await page.evaluate(() => { window.__measure.joinStart = performance.now(); return 0; });
  await page.getByRole('button', { name: 'Join fixture', exact: true }).click();
  await page.waitForFunction(() => window.__measure.joinRender !== null);
  return page.evaluate(() => window.__measure.joinRender - window.__measure.joinStart);
}
async function current(page) { return page.evaluate(() => window.__measure.snapshot); }
async function submit(page, request, ui = false) {
  if (ui) {
    await page.getByLabel('Destination X', { exact: true }).fill(String(request.to.x));
    await page.getByLabel('Destination Y', { exact: true }).fill(String(request.to.y));
    await page.getByRole('button', { name: 'Submit move', exact: true }).click();
  } else await page.evaluate(request => window.__measure.socket.send(JSON.stringify(request)), request);
  await page.waitForFunction(() => window.__measure.lastCompleted && !window.__measure.active, null, { timeout: 10000 });
  return page.evaluate(() => { const value = window.__measure.lastCompleted; window.__measure.lastCompleted = null; return value; });
}
try {
  report.image = await dockerCall(['image', 'inspect', 'ffb-server:3.4.0-m1c.1', '--format', '{{.Id}}']);
  for (const actor of ['home', 'away']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      const Native = window.WebSocket;
      const m = window.__measure = { socket: null, snapshot: null, active: null, lastCompleted: null, joinStart: 0, joinRender: null, snapshots: 0 };
      const finish = () => {
        const host = document.querySelector('[data-rendered-revision]');
        if (m.snapshot && host?.dataset.renderedMatchId === m.snapshot.matchId && Number(host.dataset.renderedRevision) === m.snapshot.revision) {
          if (m.joinRender === null && Number(host.dataset.renderSequence) > m.joinSequence) m.joinRender = performance.now();
          if (m.active?.result?.status === 'accepted' && !m.active.result.duplicate && m.snapshot.revision === m.active.result.revision) {
            m.active.renderMs = performance.now() - m.active.start; m.lastCompleted = m.active; m.active = null;
          }
        }
      };
      new MutationObserver(finish).observe(document, { subtree: true, attributes: true, childList: true });
      window.WebSocket = class extends Native {
        constructor(...args) { super(...args); if (!this.url.includes('/browser/v1')) return; m.socket = this; m.joinRender = null; m.snapshot = null; m.joinSequence = Number(document.querySelector('[data-render-sequence]')?.dataset.renderSequence ?? 0);
          this.addEventListener('message', event => {
            const message = JSON.parse(event.data);
            if (message.type === 'snapshot') { m.snapshot = message; m.snapshots++; if (m.active) m.active.snapshotBytes = new TextEncoder().encode(event.data).length; }
            if (message.type === 'result' && m.active?.request.requestId === message.requestId) {
              m.active.result = message; m.active.resultMs = performance.now() - m.active.start; m.active.resultBytes = new TextEncoder().encode(event.data).length;
              if (message.status === 'rejected' || message.duplicate) { m.lastCompleted = m.active; m.active = null; }
            }
            finish();
          });
        }
        send(raw) { if (!this.url.includes('/browser/v1')) return super.send(raw); const request = JSON.parse(raw); if (request.type !== 'join') { m.lastCompleted = null; m.active = { request, start: performance.now() }; } super.send(raw); }
      };
    });
    await page.goto('http://127.0.0.1:5173');
    const cdp = await context.newCDPSession(page); await cdp.send('Performance.enable');
    sessions[actor] = { context, page, cdp, token: (await readFile(resolve(`../containers/local/.secrets/browser_${actor}_token`), 'utf8')).trim() };
  }
  const home = sessions.home.page, away = sessions.away.page;
  // Two warm-up lifetimes excluded, then 100 lifetimes × (6 legal moves + 2 rejects + 2 retries).
  for (let lifetime = -2; lifetime < 100; lifetime++) {
    await control('reset', 'MOVEMENT');
    for (const actor of ['home', 'away']) { await sessions[actor].page.getByLabel('Local session credential').waitFor(); await join(actor); }
    if (lifetime === 0) await captureMemory('baseline-after-warmup');
    const initial = await current(home);
    let first;
    for (let action = 0; action < 6; action++) {
      const sample = await submit(home, { to: { x: action % 2 ? 5 : 6, y: 7 } }, true);
      assert.equal(sample.result.status, 'accepted'); assert.equal(sample.result.duplicate, false); assert.equal(sample.result.revision, action + 1);
      if (!first) first = sample.request;
      if (lifetime >= 0) samples.push({ lifetime, kind: 'mutation', ...sample });
      await away.waitForFunction(revision => window.__measure.snapshot?.revision === revision && Number(document.querySelector('[data-rendered-revision]')?.dataset.renderedRevision) === revision, action + 1);
    }
    for (const [kind, page, request] of [
      ['duplicate', home, first],
      ['rejected', away, { ...first, requestId: `wrong-${lifetime}`, expectedRevision: 6 }],
      ['rejected', home, { ...first, requestId: `stale-${lifetime}` }]
    ]) {
      const sample = await submit(page, request); assert.equal(sample.result.status, kind === 'duplicate' ? 'accepted' : 'rejected');
      if (kind === 'duplicate') assert.equal(sample.result.duplicate, true);
      if (lifetime >= 0) samples.push({ lifetime, kind, ...sample });
    }
    await home.getByRole('button', { name: 'Disconnect', exact: true }).click();
    const elapsed = await join('home');
    assert.equal((await current(home)).matchId, initial.matchId); assert.equal((await current(home)).revision, 6);
    const retry = await submit(home, first); assert.equal(retry.result.duplicate, true); assert.equal(retry.result.revision, 1);
    if (lifetime >= 0) { reconnects.push(elapsed); samples.push({ lifetime, kind: 'duplicate', ...retry }); }
    if ([24, 49, 74, 99].includes(lifetime)) { await captureMemory(`lifetime-${lifetime + 1}`); console.log(`Completed ${lifetime + 1} lifetimes / ${samples.length} measured submissions`); }
  }
  await home.screenshot({ path: resolve(output, 'home-final.png'), fullPage: true });
  await away.screenshot({ path: resolve(output, 'away-final.png'), fullPage: true });
  for (const { page } of Object.values(sessions)) await page.getByRole('button', { name: 'Disconnect', exact: true }).click();
  await control('reset', 'BOTH_DOWN');
  await new Promise(done => setTimeout(done, 15000));
  await captureMemory('idle-15s-retired');
  const stats = values => { const sorted = [...values].sort((a,b) => a-b); return { count: sorted.length, p50: sorted[Math.ceil(sorted.length * .5)-1], p95: sorted[Math.ceil(sorted.length * .95)-1], max: sorted.at(-1) }; };
  report.summary = { submissions: samples.length, mutations: samples.filter(s => s.kind === 'mutation').length, rejects: samples.filter(s => s.kind === 'rejected').length, duplicates: samples.filter(s => s.kind === 'duplicate').length, measuredLifetimes: 100, warmupLifetimes: 2, reconnects: reconnects.length, acceptedRenderMs: stats(samples.filter(s => s.kind === 'mutation').map(s => s.renderMs)), acceptedResultMs: stats(samples.filter(s => s.kind === 'mutation').map(s => s.resultMs)), reconnectRenderMs: stats(reconnects), snapshotBytes: stats(samples.filter(s => s.snapshotBytes).map(s => s.snapshotBytes)), resultBytes: stats(samples.map(s => s.resultBytes)), failures: errors.length, timeouts: 0 };
  assert.equal(samples.length, 1000); assert.deepEqual(errors, []); report.passed = true;
} catch (error) { report.failure = error.stack; throw error; }
finally { await writeFile(resolve(output, 'workload.json'), JSON.stringify(report, null, 2)); await browser.close(); }
