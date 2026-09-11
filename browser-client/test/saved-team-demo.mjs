import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

// Run `prepare`, restart the local server, then run `check`. Evidence contains
// only this driver's generated document and identifier, never credentials/list data.
const mode = process.argv[2] ?? 'prepare';
if (!['prepare', 'check'].includes(mode)) throw Error('Usage: node test/saved-team-demo.mjs prepare|check');
const output = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m2b');
const artifact = resolve(output, 'saved-team-restart.json');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
try {
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.m2bTrace = { incoming: [], outgoing: [], socket: null };
    window.WebSocket = class extends Native {
      constructor(...args) { super(...args); window.m2bTrace.socket = this; this.addEventListener('message', event => { const item = JSON.parse(event.data); if (item.type === 'savedTeam') window.m2bTrace.incoming.push(item); }); }
      send(text) { const item = JSON.parse(text); if (item.type === 'savedTeam') window.m2bTrace.outgoing.push(item); super.send(text); }
    };
  });
  await page.goto('http://127.0.0.1:5173/teams');
  const token = (await readFile(resolve('../containers/local/.secrets/browser_home_token'), 'utf8')).trim();
  await page.getByLabel('Local credential', { exact: true }).fill(token);
  await page.getByRole('button', { name: 'Connect to catalog', exact: true }).click();
  await page.getByRole('heading', { name: 'Human · 1,150,000 gold', exact: true }).waitFor();
  const waitFor = async request => {
    await page.waitForFunction(id => window.m2bTrace.incoming.some(item => item.requestId === id), request.requestId);
    return page.evaluate(id => window.m2bTrace.incoming.find(item => item.requestId === id), request.requestId);
  };
  const invoke = async (operation, click) => {
    const before = await page.evaluate(() => window.m2bTrace.outgoing.length);
    await click();
    await page.waitForFunction(({ before, operation }) => window.m2bTrace.outgoing.slice(before).some(item => item.operation === operation), { before, operation });
    const request = await page.evaluate(({ before, operation }) => window.m2bTrace.outgoing.slice(before).find(item => item.operation === operation), { before, operation });
    return waitFor(request);
  };
  if (mode === 'check') {
    const expected = JSON.parse(await readFile(artifact, 'utf8'));
    await page.locator('li', { hasText: expected.teamId }).getByRole('button', { name: 'Load', exact: true }).click();
    const load = await page.evaluate(() => window.m2bTrace.outgoing.filter(item => item.operation === 'load').at(-1));
    const response = await waitFor(load);
    assert.equal(response.code, 'OK'); assert.deepEqual(response.document, expected.document, 'restart preserves the complete authoritative document');
    await page.locator('fieldset').screenshot({ path: resolve(output, 'saved-team-restart.png') });
    await page.screenshot({ path: resolve(output, 'saved-team-page.png'), fullPage: true,
      mask: [page.locator('section[aria-label="Saved teams"] li').filter({ hasNotText: expected.teamId })] });
    await writeFile(resolve(output, 'browser-summary.json'), JSON.stringify({ passed: true, browser: browser.version(),
      viewport: { width: 1440, height: 1080 }, restartDocumentVersion: response.document.documentVersion,
      total: response.document.validation.total, completeDocumentEqual: true, errors }, null, 2));
    assert.deepEqual(errors, []); console.log(`M2b restart check passed for ${expected.teamId}.`); process.exitCode = 0;
  } else {
    for (let index = 0; index < 11; index++) await page.getByRole('button', { name: 'Add player', exact: true }).click();
    await page.getByLabel('Team re-rolls', { exact: true }).fill('2'); await page.getByLabel('Apothecary', { exact: true }).fill('1'); await page.getByLabel('Team Captain', { exact: true }).selectOption({ label: 'Slot 1' });
    const create = await invoke('create', () => page.getByRole('button', { name: 'Create saved team', exact: true }).click());
    assert.equal(create.code, 'OK'); assert.equal(create.document.validation.total, 700000); const teamId = create.document.teamId;
    const list = await invoke('list', () => page.getByRole('button', { name: 'Refresh list', exact: true }).click());
    assert.ok(list.teams.some(team => team.teamId === teamId), 'created document appears in metadata list');
    await page.getByRole('button', { name: 'Create new draft', exact: true }).click();
    const load = await invoke('load', () => page.locator('li', { hasText: teamId }).getByRole('button', { name: 'Load', exact: true }).click());
    assert.deepEqual(load.document, create.document, 'load returns the created authoritative document');
    await page.getByLabel('Team re-rolls', { exact: true }).fill('3');
    const update = await invoke('update', () => page.getByRole('button', { name: 'Save changes', exact: true }).click());
    assert.equal(update.code, 'OK'); assert.equal(update.document.documentVersion, 2); assert.equal(update.document.validation.total, 750000);
    const downloadPromise = page.waitForEvent('download'); await page.getByRole('button', { name: 'Download JSON export', exact: true }).click();
    const download = await downloadPromise; const exported = JSON.parse(await readFile(await download.path(), 'utf8')); assert.deepEqual(exported, update.document, 'download is the authoritative document');
    const importClaim = structuredClone(exported); importClaim.validation.total = 1;
    await page.getByLabel('Import saved-team JSON', { exact: true }).fill(JSON.stringify(importClaim));
    const imported = await invoke('import', () => page.getByRole('button', { name: 'Import JSON', exact: true }).click());
    assert.equal(imported.code, 'OK'); assert.equal(imported.document.documentVersion, 3); assert.equal(imported.document.validation.total, 750000, 'server recomputes imported claims');
    await page.getByLabel('Team re-rolls', { exact: true }).fill('2');
    await page.getByLabel('Import saved-team JSON', { exact: true }).fill(JSON.stringify(exported));
    const conflict = await invoke('import', () => page.getByRole('button', { name: 'Import JSON', exact: true }).click());
    assert.equal(conflict.code, 'CONFLICT'); assert.match(await page.getByRole('status').filter({ hasText: 'Saved team: CONFLICT' }).innerText(), /unsaved edits/); assert.equal(await page.getByLabel('Team re-rolls', { exact: true }).inputValue(), '2');
    for (let index = 0; index < 5; index++) await page.getByRole('button', { name: 'Add player', exact: true }).click();
    await page.getByLabel('Team re-rolls', { exact: true }).fill('8');
    const invalid = await invoke('update', () => page.getByRole('button', { name: 'Save changes', exact: true }).click());
    assert.equal(invalid.code, 'VALIDATION_FAILED');
    await page.getByText(/OVER_BUDGET/).waitFor();
    const rawLoad = { version: 1, type: 'savedTeam', requestId: `verify-${crypto.randomUUID()}`, operation: 'load', teamId };
    await page.evaluate(request => window.m2bTrace.socket.send(JSON.stringify(request)), rawLoad); const persisted = await waitFor(rawLoad);
    assert.deepEqual(persisted.document, imported.document, 'invalid write and stale import leave stored document unchanged');
    await page.locator('fieldset').screenshot({ path: resolve(output, 'saved-team-live.png') });
    await writeFile(artifact, JSON.stringify({ teamId, document: imported.document }, null, 2));
    assert.deepEqual(errors, []); console.log(`M2b prepare passed for ${teamId}; restart the local server, then run check.`);
  }
} finally { await browser.close(); }
