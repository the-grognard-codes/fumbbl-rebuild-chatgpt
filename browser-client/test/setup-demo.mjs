import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { chromium } from 'playwright';

const mode = process.argv[2] ?? 'prepare';
const out = resolve('../.notes/overhaul-analysis/verification/m3a');
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const contexts = await Promise.all([browser.newContext({ viewport: { width: 1440, height: 1080 } }), browser.newContext({ viewport: { width: 1440, height: 1080 } })]);
const pages = await Promise.all(contexts.map(context => context.newPage()));
const subjects = ['away', 'home']; // creator is credential away, persisted match home
const tokens = await Promise.all(subjects.map(subject => readFile(resolve(`../containers/local/.secrets/browser_${subject}_token`), 'utf8').then(value => value.trim())));
const errors = [];
for (const page of pages) {
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.__m3 = { socket: null, incoming: [], outgoing: [] };
    window.WebSocket = class extends Native {
      constructor(...args) { super(...args); window.__m3.socket = this; this.addEventListener('message', event => {
        const value = JSON.parse(event.data); if (['preparedMatch', 'setupState'].includes(value.type)) window.__m3.incoming.push(value);
      }); }
      send(raw) { const value = JSON.parse(raw); if (['preparedMatch', 'setup'].includes(value.type)) window.__m3.outgoing.push(value); super.send(raw); }
    };
  });
}
const base = operation => ({ version: 1, type: 'preparedMatch', operation, requestId: randomUUID() });
const draft = () => ({ catalogVersion: 'bb2025-human-2026-09-08.1', ruleset: 'BB2025', rosterId: 'human', presetId: 'human-exhibition-1150', captainId: 'p1', players: Array.from({ length: 11 }, (_, index) => ({ id: `p${index + 1}`, slot: index + 1, positionId: 'lineman', skillIds: [] })), resources: { rerolls: 2, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } });
async function connect(page, index, path) {
  await page.goto(`http://127.0.0.1:5173${path}`);
  await page.getByLabel('Local credential', { exact: true }).fill(tokens[index]);
  await page.getByRole('button', { name: path.startsWith('/setup') ? 'Join setup' : 'Connect to saved teams', exact: true }).click();
  await page.getByRole('status').filter({ hasText: /^Connected$/ }).waitFor();
}
async function raw(page, request, type = request.type === 'setup' ? 'setupState' : request.type) {
  return page.evaluate(({ request, type }) => new Promise((resolve, reject) => {
    const socket = window.__m3.socket;
    const timer = setTimeout(() => { socket.removeEventListener('message', receive); reject(Error('Wire response timeout')); }, 10000);
    const receive = event => { const value = JSON.parse(event.data); if (value.type === type && value.requestId === request.requestId) { clearTimeout(timer); socket.removeEventListener('message', receive); resolve(value); } };
    socket.addEventListener('message', receive); socket.send(JSON.stringify(request));
  }), { request, type });
}
async function saved(page, fields) { return raw(page, { version: 1, type: 'savedTeam', requestId: randomUUID(), ...fields }); }
async function load(page, matchId) { return raw(page, { version: 1, type: 'setup', operation: 'load', requestId: randomUUID(), matchId }); }
async function revision(page, expected) { await page.waitForFunction(expected => window.__m3.incoming.some(value => value.state?.revision === expected), expected); await page.getByTestId('setup-status').filter({ hasText: `Revision ${expected} ` }).waitFor(); }
async function state(page) { return page.evaluate(() => window.__m3.incoming.filter(value => value.state).at(-1).state); }
function indexFor(role) { return role === 'home' ? 0 : 1; }
async function action(page, button, expected) { await page.getByRole('button', { name: button, exact: true }).click(); await Promise.all(pages.map(peer => revision(peer, expected))); }
try {
  if (mode === 'check') {
    const artifact = JSON.parse(await readFile(resolve(out, 'setup-restart.json'), 'utf8'));
    for (let i = 0; i < 2; i++) {
      await connect(pages[i], i, `/setup?matchId=${artifact.matchId}`);
      await pages[i].getByRole('alert').filter({ hasText: 'SESSION_UNAVAILABLE' }).waitFor();
      const unavailable = await load(pages[i], artifact.matchId); assert.equal(unavailable.code, 'SESSION_UNAVAILABLE'); assert.equal(unavailable.state, null);
      await pages[i].screenshot({ path: resolve(out, `restart-${subjects[i]}.png`) });
    }
    await connect(pages[0], 0, '/matches');
    const retry = await raw(pages[0], artifact.activation);
    assert.equal(retry.code, 'ACCEPTED'); assert.equal(retry.duplicate, true); assert.deepEqual(retry.document, artifact.document);
    await connect(pages[0], 0, `/setup?matchId=${artifact.matchId}`);
    assert.equal((await load(pages[0], artifact.matchId)).code, 'SESSION_UNAVAILABLE');
    await writeFile(resolve(out, 'restart-summary.json'), JSON.stringify({ passed: true, matchId: artifact.matchId, bothUnavailable: true, activationRetryDuplicate: true, documentUnchanged: true, errors }, null, 2));
    console.log('Restart check passed: frozen document retained; both sessions unavailable; exact activation retry cannot initialize.');
  } else {
    await Promise.all(pages.map((page, i) => connect(page, i, '/matches')));
    const drafts = [draft(), draft()];
    const teams = [];
    for (let i = 0; i < 2; i++) { const result = await saved(pages[i], { operation: 'create', draft: drafts[i] }); assert.equal(result.code, 'OK'); teams.push(result.document); }
    const created = await raw(pages[0], { ...base('create'), teamId: teams[0].teamId, expectedDocumentVersion: 1, intendedOpponent: 'home' });
    assert.equal(created.code, 'ACCEPTED'); assert.equal(created.callerRole, 'home');
    const matchId = created.document.matchId;
    const joined = await raw(pages[1], { ...base('join'), matchId, expectedRevision: 1, teamId: teams[1].teamId, expectedDocumentVersion: 1 });
    assert.equal(joined.code, 'ACCEPTED'); assert.equal(joined.callerRole, 'away');
    await pages[0].getByLabel('Match ID', { exact: true }).fill(matchId);
    await pages[0].getByRole('button', { name: 'Reload authoritative match', exact: true }).click();
    await pages[0].getByRole('button', { name: 'Activate setup', exact: true }).click();
    await pages[0].getByRole('link', { name: 'Open match setup', exact: true }).waitFor();
    const activation = await pages[0].evaluate(() => window.__m3.outgoing.findLast(value => value.operation === 'activate'));
    const activated = await raw(pages[0], activation); assert.equal(activated.duplicate, true);
    const frozen = activated.document;
    for (let i = 0; i < 2; i++) {
      const edited = structuredClone(drafts[i]); edited.resources.rerolls = 3;
      const result = await saved(pages[i], { operation: 'update', teamId: teams[i].teamId, expectedDocumentVersion: 1, draft: edited }); assert.equal(result.code, 'OK');
    }
    const afterEdit = await raw(pages[0], { ...base('load'), matchId }); assert.deepEqual(afterEdit.document, frozen);
    await Promise.all(pages.map((page, i) => connect(page, i, `/setup?matchId=${matchId}`)));
    await Promise.all(pages.map(page => revision(page, 0)));
    let view = await state(pages[0]);
    assert.equal(view.callerRole, 'home'); assert.equal((await state(pages[1])).callerRole, 'away');
    const actorIndex = indexFor(view.actor), otherIndex = 1 - actorIndex;
    const bad = { version: 1, type: 'setup', operation: 'choice', requestId: randomUUID(), matchId, expectedRevision: view.revision, promptId: view.prompt.id, optionId: 'heads' };
    assert.equal((await raw(pages[otherIndex], bad)).code, 'WRONG_ACTOR');
    assert.equal((await raw(pages[actorIndex], { ...bad, requestId: randomUUID(), expectedRevision: 999 })).code, 'STALE_REVISION');
    await action(pages[actorIndex], 'heads', 1);
    await pages[actorIndex].getByRole('button', { name: 'Repeat last setup request', exact: true }).click();
    await pages[actorIndex].waitForFunction(() => window.__m3.incoming.some(value => value.duplicate === true));
    assert.equal((await load(pages[0], matchId)).state.revision, 1);
    // Reconnect during the pending receive choice, then restore the exact prompt.
    view = await state(pages[0]);
    const pendingPrompt = view.prompt; const receiver = indexFor(view.actor);
    await pages[receiver].getByRole('button', { name: 'Disconnect', exact: true }).click();
    await pages[receiver].getByRole('status').filter({ hasText: /^Disconnected$/ }).waitFor();
    await pages[receiver].getByLabel('Local credential', { exact: true }).fill(tokens[receiver]);
    await pages[receiver].getByRole('button', { name: 'Join setup', exact: true }).click();
    await revision(pages[receiver], 1);
    assert.deepEqual((await load(pages[receiver], matchId)).state.prompt, pendingPrompt);
    await action(pages[receiver], 'receive', 2);
    for (let team = 0; team < 2; team++) {
      view = await state(pages[0]); const index = indexFor(view.actor), page = pages[index];
      if (team === 0) {
        await page.getByRole('button', { name: 'Confirm legal setup', exact: true }).click();
        await page.getByRole('alert').filter({ hasText: 'ILLEGAL_SETUP' }).waitFor();
        assert.equal((await load(page, matchId)).state.revision, 2);
      }
      for (let slot = 1; slot <= 11; slot++) {
        const player = view.players.find(player => player.role === view.actor && player.slot === slot);
        await page.getByLabel('Setup player', { exact: true }).selectOption(player.id);
        const x = slot <= 3 ? 12 : 10, y = slot <= 3 ? slot + 5 : slot;
        await page.getByLabel('Setup X', { exact: true }).fill(String(view.actor === 'home' ? x : 25 - x));
        await page.getByLabel('Setup Y', { exact: true }).fill(String(y));
        const before = (await load(page, matchId)).state.revision;
        await action(page, 'Place on empty own-half square', before + 1);
      }
      const before = (await load(page, matchId)).state.revision;
      await action(page, 'Confirm legal setup', before + 1);
    }
    const final = (await load(pages[0], matchId)).state, other = (await load(pages[1], matchId)).state;
    assert.equal(final.phase, 'READY_FOR_KICKOFF'); assert.equal(final.revision, 26);
    assert.deepEqual({ ...final, callerRole: 'away' }, other);
    assert.equal(final.homeRerolls, 2); assert.equal(final.awayRerolls, 2);
    for (let i = 0; i < 2; i++) {
      await pages[i].screenshot({ path: resolve(out, `setup-ready-${i === 0 ? 'home' : 'away'}.png`), fullPage: true });
      await writeFile(resolve(out, `setup-wire-${subjects[i]}.json`), JSON.stringify(await pages[i].evaluate(() => ({ incoming: window.__m3.incoming, outgoing: window.__m3.outgoing })), null, 2));
    }
    assert.deepEqual(errors, []);
    await writeFile(resolve(out, 'setup-restart.json'), JSON.stringify({ matchId, activation, document: frozen }, null, 2));
    await writeFile(resolve(out, 'prepare-summary.json'), JSON.stringify({ passed: true, browser: browser.version(), matchId, reversedCreator: true, sourceEditsIsolated: true, activatedRerollsUnchanged: true, exactRetry: true, pendingReconnect: true, wrongRoleRejected: true, staleRejected: true, final, errors }, null, 2));
    console.log(`Setup demonstration passed: ${matchId}, both legally set up at revision 26.`);
  }
} finally { await browser.close(); }
