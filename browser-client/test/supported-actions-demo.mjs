import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

// Mounted UI evidence from native Java engine/SetupSession traces. No product fixture route or dice.
const frames = JSON.parse(await readFile('test/fixtures/supported-actions-v1.json', 'utf8'));
const wire = JSON.parse(await readFile('test/fixtures/wire-v1.json', 'utf8'));
const join = wire[0].messages.find(message => message.type === 'snapshot');
const out = resolve('../.notes/overhaul-analysis/verification/m3c');
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const contexts = await Promise.all([browser.newContext({ viewport: { width: 1440, height: 1080 } }), browser.newContext({ viewport: { width: 1440, height: 1080 } })]);
let pages = await Promise.all(contexts.map(context => context.newPage()));
const errors = [], results = [];
try {
  for (const page of pages) page.on('pageerror', error => errors.push(error.message));
  for (let index = 0; index < frames.length; index++) {
    if (index > 0) {
      await Promise.all(pages.map(page => page.close()));
      pages = await Promise.all(contexts.map(context => context.newPage()));
      for (const page of pages) page.on('pageerror', error => errors.push(error.message));
    }
    const frame = frames[index], owner = frame.before.state.callerRole;
    const roles = [owner, owner === 'home' ? 'away' : 'home'];
    for (let side = 0; side < 2; side++) {
      await pages[side].addInitScript(({ frame, role, join }) => {
        window.m3c = { frame, role, current: frame.before, submitted: [], applied: 0, last: null };
        class Socket extends EventTarget {
          static OPEN = 1; readyState = 1;
          constructor() { super(); window.m3c.socket = this; queueMicrotask(() => this.onopen?.()); }
          emit(value) { this.onmessage?.({ data: JSON.stringify(value) }); }
          send(raw) {
            const request = JSON.parse(raw), store = window.m3c;
            if (request.type === 'join') { queueMicrotask(() => this.emit({ ...join, actor: role })); return; }
            if (request.operation === 'load') { queueMicrotask(() => this.emit({ ...store.current, requestId: request.requestId, state: { ...store.current.state, callerRole: role } })); return; }
            store.submitted.push(request);
            const duplicate = store.last?.requestId === request.requestId;
            if (!duplicate) { store.last = request; store.current = frame.after; store.applied++; }
            // Deliberately lose first acknowledgement. A load recovers the native post-action snapshot.
            if (duplicate) queueMicrotask(() => this.emit({ ...frame.after, requestId: request.requestId, duplicate: true, state: { ...frame.after.state, callerRole: role } }));
          }
          close() { this.readyState = 3; this.onclose?.(); }
        }
        window.WebSocket = Socket;
      }, { frame, role: roles[side], join });
      await pages[side].goto(`http://127.0.0.1:5173/setup?matchId=${frame.before.state.matchId}`);
      await pages[side].getByLabel('Local credential', { exact: true }).fill('synthetic-test-only');
      await pages[side].getByRole('button', { name: 'Join setup', exact: true }).click();
      await pages[side].getByTestId('setup-status').filter({ hasText: `Revision ${frame.before.state.revision} ` }).waitFor();
    }
    assert.equal(await pages[1].getByRole('button', { name: 'Execute action', exact: true }).isDisabled(), true);
    assert.equal(await pages[0].getByLabel('Server action', { exact: true }).locator('option').count(), frame.before.state.actions.filter(a => a.actor === owner).length + 1);
    // Reconnect before resolution must restore every offered option and allow the authorized owner to act.
    const reconnect = async page => {
      await page.getByRole('button', { name: 'Disconnect', exact: true }).click();
      assert.equal(await page.getByRole('button', { name: 'Execute action', exact: true }).isDisabled(), true);
      await page.getByLabel('Local credential', { exact: true }).fill('synthetic-test-only');
      await page.getByRole('button', { name: 'Join setup', exact: true }).click();
      await page.getByRole('status').filter({ hasText: /^Connected$/ }).waitFor();
    };
    await reconnect(pages[0]);
    const offered = frame.before.state.actions.filter(action => action.actor === owner);
    if (offered.length > 12) {
      const selected = offered.find(action => action.id === frame.request.actionId);
      await pages[0].getByLabel('Find an action or target', { exact: true }).fill(selected.label);
      assert.equal(await pages[0].getByLabel('Server action', { exact: true }).locator('option').count(),
        offered.filter(action => `${action.label} ${action.kind}`.toLowerCase().includes(selected.label.toLowerCase())).length + 1);
    }
    await pages[0].getByLabel('Server action', { exact: true }).selectOption(frame.request.actionId);
    await pages[0].getByRole('button', { name: 'Execute action', exact: true }).click();
    await pages[0].waitForFunction(() => window.m3c.submitted.length === 1);
    const sent = await pages[0].evaluate(() => window.m3c.submitted[0]);
    assert.deepEqual({ ...sent, requestId: frame.request.requestId }, frame.request);
    await reconnect(pages[0]);
    await pages[0].getByTestId('setup-status').filter({ hasText: `Revision ${frame.after.state.revision} ` }).waitFor();
    if (await pages[0].getByLabel('Find an action or target', { exact: true }).count())
      assert.equal(await pages[0].getByLabel('Find an action or target', { exact: true }).inputValue(), '');
    await pages[0].getByRole('button', { name: 'Repeat last setup request', exact: true }).click();
    await pages[0].waitForFunction(() => window.m3c.submitted.length === 2);
    const observed = await pages[0].evaluate(() => ({ applied: window.m3c.applied, submitted: window.m3c.submitted }));
    assert.equal(observed.applied, 1); assert.deepEqual(observed.submitted[0], observed.submitted[1]);
    await pages[1].evaluate(after => { window.m3c.current = after; window.m3c.socket.emit({ ...after, requestId: null, state: { ...after.state, callerRole: window.m3c.role } }); }, frame.after);
    await pages[1].getByTestId('setup-status').filter({ hasText: `Revision ${frame.after.state.revision} ` }).waitFor();
    results.push({ index, capability: frame.capability, action: frame.request.actionId, owner, reconnectBefore: true, lostAcknowledgementRetry: true, observerSynchronized: true });
    if (['pass','interception','apothecary'].includes(frame.capability) && !results.slice(0, -1).some(r => r.capability === frame.capability)) await pages[0].screenshot({ path: resolve(out, `mounted-${frame.capability}.png`), fullPage: true });
  }
  assert.deepEqual(errors, []);
  await writeFile(resolve(out, 'mounted-action-summary.json'), JSON.stringify({ kind: 'mounted-native-wire-fixtures', browser: browser.version(), frames: results, errors }, null, 2));
  console.log(`PASS: ${results.length} native action traces in two isolated browser contexts, reconnect before choices and lost acknowledgement retries.`);
} finally { await browser.close(); }
