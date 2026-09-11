import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

// Local test driver. Never copy join credentials into evidence or production code.
const output = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m2a');
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
try {
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.teamTrace = { incoming: [], outgoing: [], socket: null };
    window.WebSocket = class extends Native {
      constructor(...args) {
        super(...args); window.teamTrace.socket = this;
        this.addEventListener('message', event => window.teamTrace.incoming.push(JSON.parse(event.data)));
      }
      send(text) { const value = JSON.parse(text); if (value.type !== 'join') window.teamTrace.outgoing.push(value); super.send(text); }
    };
  });
  await page.goto('http://127.0.0.1:5173/teams');
  const token = (await readFile(resolve('../containers/local/.secrets/browser_home_token'), 'utf8')).trim();
  await page.getByLabel('Local credential', { exact: true }).fill(token);
  await page.getByRole('button', { name: 'Connect to catalog', exact: true }).click();
  await page.getByRole('heading', { name: 'Human · 1,150,000 gold', exact: true }).waitFor();
  assert.equal(await page.locator('canvas').count(), 0, 'roster controls mount no Pixi board');
  const capturedCatalog = await page.evaluate(() => window.teamTrace.incoming.find(message => message.type === 'catalog'));
  const fixture = JSON.parse(await readFile('test/fixtures/catalog-v1.json', 'utf8'));
  assert.deepEqual({ ...capturedCatalog, requestId: 'catalog-fixture' }, fixture);
  const validate = async () => {
    await page.getByRole('button', { name: 'Validate draft on server', exact: true }).click();
    await page.waitForFunction(() => {
      const request = window.teamTrace.outgoing.filter(message => message.type === 'validateTeam').at(-1);
      return window.teamTrace.incoming.some(message => message.requestId === request?.requestId);
    });
    return page.evaluate(() => window.teamTrace.incoming.filter(message => message.type === 'teamValidation').at(-1));
  };
  assert.equal((await validate()).valid, false);
  await page.getByText(/PLAYER_COUNT/).waitFor();
  for (let i = 0; i < 11; i++) await page.getByRole('button', { name: 'Add player', exact: true }).click();
  await page.getByLabel('Team re-rolls', { exact: true }).fill('2');
  await page.getByLabel('Apothecary', { exact: true }).fill('1');
  await page.getByLabel('Team Captain', { exact: true }).selectOption({ label: 'Slot 1' });
  const valid = await validate(); assert.equal(valid.valid, true); assert.equal(valid.total, 700000);
  await page.getByRole('heading', { name: 'Valid draft', exact: true }).waitFor();
  await page.screenshot({ path: resolve(output, 'team-valid.png'), fullPage: true });
  await page.getByLabel('Skill for slot 1', { exact: true }).selectOption('pass');
  assert.equal(await page.getByRole('heading', { name: 'Valid draft', exact: true }).count(), 0, 'edits clear old evaluation');
  assert.equal((await validate()).valid, false);
  await page.getByText(/SKILL_INELIGIBLE/).waitFor();
  await page.getByLabel('Skill for slot 1', { exact: true }).selectOption('');
  const baseline = await page.evaluate(() => structuredClone(window.teamTrace.outgoing.filter(message => message.type === 'validateTeam').at(-1).draft));
  baseline.players[0].skillIds = [];
  const rawCases = [
    ['claimed-total', draft => { draft.total = 1; }, 'MALFORMED_TEAM_REQUEST'],
    ['unknown-version', draft => { draft.catalogVersion = 'unknown'; }, 'CATALOG_VERSION'],
    ['duplicate-id', draft => { draft.players[1].id = draft.players[0].id; }, 'DUPLICATE_PLAYER'],
    ['duplicate-slot', draft => { draft.players[1].slot = draft.players[0].slot; }, 'DUPLICATE_SLOT'],
    ['unknown-position', draft => { draft.players[0].positionId = 'fixture.lineman'; }, 'POSITION'],
    ['unknown-skill', draft => { draft.players[1].skillIds = ['unknown']; }, 'SKILL'],
    ['fraction', draft => { draft.resources.rerolls = 0.5; }, 'MALFORMED_TEAM_REQUEST'],
    ['oversized-draft', draft => { while (draft.players.length < 17) draft.players.push(draft.players[0]); }, 'MALFORMED_TEAM_REQUEST'],
  ];
  for (const [id, mutate, code] of rawCases) {
    const draft = structuredClone(baseline); mutate(draft);
    await page.evaluate(({ id, draft }) => window.teamTrace.socket.send(JSON.stringify({ version: 1, type: 'validateTeam', requestId: id, draft })), { id, draft });
    await page.waitForFunction(id => window.teamTrace.incoming.some(message => message.requestId === id), id);
    const response = await page.evaluate(id => window.teamTrace.incoming.find(message => message.requestId === id), id);
    assert.ok(response.code === code || response.messages?.some(message => message.code === code), JSON.stringify(response));
  }
  assert.equal((await validate()).total, 700000, 'invalid requests did not alter the form draft');
  const after = await page.evaluate(() => window.teamTrace.outgoing.filter(message => message.type === 'validateTeam').at(-1).draft);
  assert.deepEqual(after, baseline);
  for (let i = 0; i < 5; i++) await page.getByRole('button', { name: 'Add player', exact: true }).click();
  await page.getByLabel('Team re-rolls', { exact: true }).fill('8');
  const over = await validate(); assert.equal(over.valid, false); assert.equal(over.total, 1250000);
  await page.getByText(/OVER_BUDGET/).waitFor();
  await page.screenshot({ path: resolve(output, 'team-over-budget.png'), fullPage: true });
  assert.deepEqual(errors, []);
  const trace = await page.evaluate(() => ({ incoming: window.teamTrace.incoming, outgoing: window.teamTrace.outgoing }));
  await writeFile(resolve(output, 'team-wire.json'), JSON.stringify(trace, null, 2));
  await writeFile(resolve(output, 'team-demo.json'), JSON.stringify({ passed: true, browser: browser.version(), validTotal: valid.total, overBudgetTotal: over.total, negativeWireCases: rawCases.map(([id]) => id), noPixi: true, errors }, null, 2));
  console.log('M2a live catalog, DOM validation, recomputation and untrusted request checks passed.');
} finally { await browser.close(); }
