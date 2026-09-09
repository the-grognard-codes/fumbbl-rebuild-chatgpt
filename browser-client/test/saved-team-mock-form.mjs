import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright';

// Mounted UI regression: a saved document from an unavailable catalog survives a
// fake socket close/reconnect and remains locked. Requires only Vite on :5173.
const catalog = JSON.parse(await readFile('test/fixtures/catalog-v1.json', 'utf8'));
const teamId = '12345678-1234-1234-1234-123456789abc';
const draft = { catalogVersion: 'retired-catalog', ruleset: 'BB2025', rosterId: 'human', presetId: 'old', captainId: null, players: [], resources: { rerolls: 0, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } };
const document = { formatVersion: 1, teamId, documentVersion: 1, ruleset: 'BB2025', catalogVersion: 'retired-catalog', owner: { namespace: 'local', subject: 'home' }, draft, validation: { valid: true, total: 0, budget: 1150000, skillPoints: 0, messages: [] } };
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const page = await browser.newPage();
try {
  await page.addInitScript(({ catalog, teamId, document }) => {
    class MockSocket extends EventTarget {
      static OPEN = 1; readyState = 1; onopen; onmessage; onclose;
      constructor() { super(); queueMicrotask(() => this.onopen?.()); window.mockSocket = this; }
      send(raw) { const request = JSON.parse(raw); const reply = value => queueMicrotask(() => this.onmessage?.({ data: JSON.stringify(value) }));
        if (request.type === 'join') reply({ version: 1, type: 'result', requestId: request.requestId, status: 'accepted', code: 'JOINED', revision: 0, duplicate: false });
        else if (request.type === 'catalog') reply({ ...catalog, requestId: request.requestId });
        else if (request.type === 'savedTeam' && request.operation === 'list') reply({ version: 1, type: 'savedTeam', requestId: request.requestId, code: 'OK', document: null, versionStatus: null, validation: null, teams: [{ teamId, documentVersion: 1, catalogVersion: 'retired-catalog' }] });
        else if (request.type === 'savedTeam' && request.operation === 'load') reply({ version: 1, type: 'savedTeam', requestId: request.requestId, code: 'OK', document, versionStatus: 'VERSION_UNAVAILABLE', validation: document.validation, teams: [] });
      }
      close() { this.readyState = 3; this.onclose?.(); }
    }
    window.WebSocket = MockSocket;
  }, { catalog, teamId, document });
  await page.goto('http://127.0.0.1:5173/teams');
  const connect = async () => { await page.getByLabel('Local credential', { exact: true }).fill('test'); await page.getByRole('button', { name: 'Connect to catalog', exact: true }).click(); await page.getByRole('heading', { name: 'Human · 1,150,000 gold', exact: true }).waitFor(); };
  await connect(); await page.locator('li', { hasText: teamId }).getByRole('button', { name: 'Load', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: /VERSION_UNAVAILABLE/ }).waitFor(); assert.equal(await page.getByRole('button', { name: 'Add player', exact: true }).isDisabled(), true);
  await page.evaluate(() => window.mockSocket.close()); await connect();
  await page.getByRole('alert').filter({ hasText: /VERSION_UNAVAILABLE/ }).waitFor(); assert.equal(await page.getByRole('button', { name: 'Add player', exact: true }).isDisabled(), true);
  console.log('Mounted saved-team unavailable-catalog reconnect regression passed.');
} finally { await browser.close(); }
