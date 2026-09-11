import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const frames = JSON.parse(await readFile('test/fixtures/supported-actions-v1.json', 'utf8'));
const frame = frames.find(item => item.before.state.actions.length > 0);
const join = JSON.parse(await readFile('test/fixtures/wire-v1.json', 'utf8'))[0].messages.find(item => item.type === 'snapshot');
const out = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3e/recovery');
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
try {
  await page.addInitScript(({ frame, join }) => {
    window.recovery = { frame, sent: [], socket: null };
    class Socket {
      static OPEN = 1; readyState = 1;
      constructor() { window.recovery.socket = this; queueMicrotask(() => this.onopen?.()); }
      emit(value) { this.onmessage?.({ data: JSON.stringify(value) }); }
      send(raw) {
        const request = JSON.parse(raw);
        if (request.type === 'join') {
          this.subject = request.token === 'other' ? 'home' : 'away';
          queueMicrotask(() => this.emit({ ...join, actor: this.subject })); return;
        }
        window.recovery.sent.push(request);
        if (request.operation === 'load') queueMicrotask(() => this.emit({ ...frame.before, requestId: request.requestId }));
        // Test controls explicitly supply response/failure. No automatic action acknowledgement.
      }
      close() { this.readyState = 3; this.onclose?.(); }
    }
    window.WebSocket = Socket;
  }, { frame, join });
  const connect = async (credential = 'original') => {
    await page.getByLabel('Local credential', { exact: true }).fill(credential);
    await page.getByRole('button', { name: 'Join setup', exact: true }).press('Enter');
    await page.getByTestId('setup-status').waitFor();
  };
  const emit = value => page.evaluate(value => window.recovery.socket.emit(value), value);
  const last = () => page.evaluate(() => window.recovery.sent.filter(item => item.operation !== 'load').at(-1));
  const locked = async () => assert.equal(await page.getByLabel('Server action', { exact: true }).isDisabled(), true);
  await page.goto(`http://127.0.0.1:5173/setup?matchId=${frame.before.state.matchId}`);
  await connect();
  const pitch = page.getByRole('group', { name: 'Pitch grid', exact: true });
  assert.equal(await pitch.locator('button[tabindex="0"]').count(), 1);
  await pitch.locator('button[tabindex="0"]').focus();
  await page.keyboard.press('ArrowRight'); await page.keyboard.press('ArrowDown');
  assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label').startsWith('Square 1, 1')), true);
  await page.keyboard.press('Tab');
  assert.equal(await page.evaluate(() => document.activeElement.closest('.setup-grid') === null), true, 'Tab leaves the pitch');
  assert.equal(await page.getByRole('link', { name: 'Match results', exact: true }).getAttribute('href'), `/results?matchId=${frame.before.state.matchId}`);
  await page.getByLabel('Server action', { exact: true }).selectOption(frame.request.actionId);
  await page.getByRole('button', { name: 'Execute action', exact: true }).press('Enter');
  const original = await last();
  await locked();
  assert.equal(await page.evaluate(() => sessionStorage.getItem('ffb.setup.pending.v1')?.includes('original')), false, 'credential is absent from retry storage');
  for (const code of ['PERSISTENCE_FAILED', 'MATCH_OUTCOME_UNKNOWN', 'COMPLETION_PENDING']) {
    await emit({ version: 1, type: 'setupState', requestId: original.requestId, code, duplicate: false, state: null });
    await page.getByRole('alert').filter({ hasText: code }).waitFor(); await locked();
  }
  await emit({ ...frame.after, requestId: original.requestId, state: { ...frame.after.state, matchId: '12345678-1234-1234-1234-123456789abc' } });
  await page.getByRole('alert').filter({ hasText: 'Invalid server response' }).waitFor();
  await page.getByRole('status').filter({ hasText: /^Disconnected$/ }).waitFor();
  assert.ok(await page.evaluate(() => sessionStorage.getItem('ffb.setup.pending.v1')), 'a foreign correlated state cannot erase recovery');
  await page.reload(); await connect('other'); await locked();
  await page.getByRole('alert').filter({ hasText: 'other local credential' }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Repeat last setup request', exact: true }).isDisabled(), true);
  await emit({ version: 1, type: 'setupState', requestId: await page.evaluate(() => window.recovery.sent.at(-1).requestId), code: 'NOT_FOUND', duplicate: false, state: null });
  assert.ok(await page.evaluate(() => sessionStorage.getItem('ffb.setup.pending.v1')), 'unauthorized load must not erase the original action');
  await page.getByRole('button', { name: 'Disconnect', exact: true }).press('Enter'); await connect();
  const retired = await page.evaluate(() => { window.retired = window.recovery.socket; return true; }); assert.ok(retired);
  await page.getByRole('button', { name: 'Disconnect', exact: true }).press('Enter'); await connect();
  await page.evaluate(value => window.retired.emit(value), { ...frame.after, requestId: original.requestId });
  await locked();
  await page.getByRole('button', { name: 'Repeat last setup request', exact: true }).press('Enter');
  assert.deepEqual(await last(), original, 'reload retry preserves exact identifier and body');
  await emit({ ...frame.after, requestId: original.requestId, duplicate: true });
  await page.getByTestId('setup-status').filter({ hasText: `Revision ${frame.after.state.revision} ` }).waitFor();
  assert.equal(await page.evaluate(() => sessionStorage.getItem('ffb.setup.pending.v1')), null);
  // At 200% text size the DOM controls remain reachable and retain visible focus.
  await page.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
  await page.getByRole('button', { name: 'Reload setup snapshot', exact: true }).focus();
  await page.screenshot({ path: resolve(out, 'reconciled-keyboard.png'), fullPage: true });
  assert.deepEqual(errors, []);
  await writeFile(resolve(out, 'summary.json'), JSON.stringify({ pass: true, browser: browser.version(), uncertainCodes: 3, reload: true, wrongIdentity: true, retiredSocket: true, exactRetry: true, keyboardSubmission: true, errors }, null, 2));
  console.log('PASS mounted recovery: unknown outcomes, page reload, wrong identity, unauthorized load, retired socket, exact retry and keyboard controls.');
} finally { await browser.close(); }
