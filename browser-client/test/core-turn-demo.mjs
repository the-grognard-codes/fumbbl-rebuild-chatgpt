import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { chromium } from 'playwright';

const out = resolve(process.env.M3B_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3b');
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const contexts = await Promise.all([browser.newContext({ viewport: { width: 1440, height: 1080 } }), browser.newContext({ viewport: { width: 1440, height: 1080 } })]);
const pages = await Promise.all(contexts.map(context => context.newPage()));
const subjects = ['away', 'home']; // The match creator is persisted as home despite using the away credential.
const tokens = await Promise.all(subjects.map(subject => readFile(resolve(`../containers/local/.secrets/browser_${subject}_token`), 'utf8').then(value => value.trim())));
const errors = [];

for (const page of pages) {
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.__m3b = { socket: null, incoming: [], outgoing: [] };
    window.WebSocket = class extends Native {
      constructor(...args) { super(...args); window.__m3b.socket = this; this.addEventListener('message', event => {
        const value = JSON.parse(event.data); if (['preparedMatch', 'setupState'].includes(value.type)) window.__m3b.incoming.push(value);
      }); }
      send(raw) { const value = JSON.parse(raw); if (['preparedMatch', 'setup'].includes(value.type)) window.__m3b.outgoing.push(value); super.send(raw); }
    };
  });
}

const base = operation => ({ version: 1, type: 'preparedMatch', operation, requestId: randomUUID() });
const draft = () => ({ catalogVersion: 'bb2025-human-2026-09-08.1', ruleset: 'BB2025', rosterId: 'human', presetId: 'human-exhibition-1150', captainId: 'p1', players: Array.from({ length: 11 }, (_, index) => ({ id: `p${index + 1}`, slot: index + 1, positionId: 'lineman', skillIds: [] })), resources: { rerolls: 2, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } });
const indexFor = role => role === 'home' ? 0 : 1;

async function connect(page, index, path) {
  await page.goto(`http://127.0.0.1:5173${path}`);
  await page.getByLabel('Local credential', { exact: true }).fill(tokens[index]);
  await page.getByRole('button', { name: path.startsWith('/setup') ? 'Join setup' : 'Connect to saved teams', exact: true }).click();
  await page.getByRole('status').filter({ hasText: /^Connected$/ }).waitFor();
}
async function raw(page, request, type = request.type === 'setup' ? 'setupState' : request.type) {
  return page.evaluate(({ request, type }) => new Promise((resolve, reject) => {
    const socket = window.__m3b.socket;
    const timer = setTimeout(() => { socket.removeEventListener('message', receive); reject(Error('Wire response timeout')); }, 10000);
    const receive = event => { const value = JSON.parse(event.data); if (value.type === type && value.requestId === request.requestId) { clearTimeout(timer); socket.removeEventListener('message', receive); resolve(value); } };
    socket.addEventListener('message', receive); socket.send(JSON.stringify(request));
  }), { request, type });
}
async function saved(page, fields) { return raw(page, { version: 1, type: 'savedTeam', requestId: randomUUID(), ...fields }); }
async function load(page, matchId) { return raw(page, { version: 1, type: 'setup', operation: 'load', requestId: randomUUID(), matchId }); }
async function state(page) { return page.evaluate(() => window.__m3b.incoming.filter(value => value.state).at(-1).state); }
async function waitRevision(expected) {
  await Promise.all(pages.map(page => page.waitForFunction(expected => window.__m3b.incoming.some(value => value.state?.revision === expected), expected)));
  await Promise.all(pages.map(page => page.getByTestId('setup-status').filter({ hasText: `Revision ${expected} ` }).waitFor()));
  const [home, away] = await Promise.all(pages.map(state));
  assert.equal(home.revision, away.revision, 'both browser views must share the authoritative revision');
  return home;
}
async function choose(page, action, expected) {
  await page.getByLabel('Server action', { exact: true }).selectOption(action.id);
  await page.getByRole('button', { name: 'Execute action', exact: true }).click();
  return waitRevision(expected);
}
function playerAction(view, predicate) { return view.actions.find(predicate); }
function safestEvent(view) {
  return view.actions.find(action => action.id.endsWith(':decline-event') || action.id.endsWith(':end-event')) ?? view.actions.find(action => action.kind === 'reroll' && /^Do not /i.test(action.label)) ?? view.actions[0];
}
async function resolveEvents(view, limit = 30) {
  for (let count = 0; count < limit; count++) {
    const gameAction = view.actions.find(action => ['select', 'move', 'endTurn', 'endAction', 'selectBlock', 'block'].includes(action.kind));
    if (gameAction || view.actions.length === 0) return view;
    const selected = safestEvent(view);
    assert.ok(selected, 'an emitted event action must be selectable by its owner');
    view = await choose(pages[indexFor(selected.actor)], selected, view.revision + 1);
  }
  throw Error('Event prompts did not settle within the bounded catalog loop');
}
async function finishPlayerAction(view, limit = 4) {
  for (let count = 0; count < limit; count++) {
    const action = playerAction(view, item => item.kind === 'endAction');
    if (!action) return view;
    view = await choose(pages[indexFor(action.actor)], action, view.revision + 1);
    view = await resolveEvents(view);
  }
  throw Error('Player action did not finish within the bounded confirmation loop');
}
function unmarkedSelect(view) {
  return view.actions.find(action => {
    if (action.kind !== 'select') return false;
    const marker = action.id.indexOf('select-');
    if (marker < 0) return false;
    const player = view.players.find(item => item.id === action.id.slice(marker + 'select-'.length));
    return player && player.x !== null && !view.players.some(other => other.role !== action.actor && other.x !== null
      && Math.max(Math.abs(player.x - other.x), Math.abs(player.y - other.y)) <= 1);
  });
}
function sanitized(trace) {
  const copy = structuredClone(trace);
  for (const message of copy.outgoing) if (message.type === 'join') delete message.token;
  return copy;
}

try {
  await Promise.all(pages.map((page, index) => connect(page, index, '/matches')));
  const teams = [];
  for (let index = 0; index < 2; index++) {
    const result = await saved(pages[index], { operation: 'create', draft: draft() });
    assert.equal(result.code, 'OK'); teams.push(result.document);
  }
  const created = await raw(pages[0], { ...base('create'), teamId: teams[0].teamId, expectedDocumentVersion: 1, intendedOpponent: 'home' });
  assert.equal(created.code, 'ACCEPTED'); assert.equal(created.callerRole, 'home');
  const matchId = created.document.matchId;
  const joined = await raw(pages[1], { ...base('join'), matchId, expectedRevision: 1, teamId: teams[1].teamId, expectedDocumentVersion: 1 });
  assert.equal(joined.code, 'ACCEPTED'); assert.equal(joined.callerRole, 'away');
  await pages[0].getByLabel('Match ID', { exact: true }).fill(matchId);
  await pages[0].getByRole('button', { name: 'Reload authoritative match', exact: true }).click();
  await pages[0].getByRole('button', { name: 'Activate setup', exact: true }).click();
  await pages[0].getByRole('link', { name: 'Open match setup', exact: true }).waitFor();
  await Promise.all(pages.map((page, index) => connect(page, index, `/setup?matchId=${matchId}`)));
  await waitRevision(0);

  let view = await state(pages[0]);
  await pages[indexFor(view.actor)].getByRole('button', { name: 'heads', exact: true }).click(); view = await waitRevision(1);
  await pages[indexFor(view.actor)].getByRole('button', { name: 'receive', exact: true }).click(); view = await waitRevision(2);
  for (let team = 0; team < 2; team++) {
    const owner = indexFor(view.actor), page = pages[owner];
    for (let slot = 1; slot <= 11; slot++) {
      const player = view.players.find(item => item.role === view.actor && item.slot === slot);
      assert.ok(player, `slot ${slot} must be present`);
      await page.getByLabel('Setup player', { exact: true }).selectOption(player.id);
      const x = slot <= 3 ? 12 : 10, y = slot <= 3 ? slot + 5 : slot;
      await page.getByLabel('Setup X', { exact: true }).fill(String(view.actor === 'home' ? x : 25 - x));
      await page.getByLabel('Setup Y', { exact: true }).fill(String(y));
      await page.getByRole('button', { name: 'Place on empty own-half square', exact: true }).click();
      view = await waitRevision(view.revision + 1);
    }
    await page.getByRole('button', { name: 'Confirm legal setup', exact: true }).click();
    view = await waitRevision(view.revision + 1);
  }
  assert.equal(view.phase, 'READY_FOR_KICKOFF');
  await pages[0].getByLabel('Server action', { exact: true }).waitFor();
  const kickoff = playerAction(view, action => action.kind === 'kickoff') ?? view.actions[0];
  assert.ok(kickoff, 'the engine must emit a kickoff action');
  view = await choose(pages[indexFor(kickoff.actor)], kickoff, view.revision + 1);
  view = await resolveEvents(view);

  const selectBlock = playerAction(view, action => action.kind === 'selectBlock');
  assert.ok(selectBlock, 'the post-kickoff active team must expose a native block selection');
  view = await choose(pages[indexFor(selectBlock.actor)], selectBlock, view.revision + 1);
  const block = playerAction(view, action => action.kind === 'block');
  assert.ok(block, 'selecting a block must expose an adjacent engine-derived target');
  view = await choose(pages[indexFor(block.actor)], block, view.revision + 1);

  // The native block-die dialog remains pending while one browser reconnects in the same JVM.
  const pending = playerAction(view, action => action.kind === 'blockDie');
  assert.ok(pending, 'the browser must receive a native block-die prompt');
  const owner = indexFor(pending.actor), observer = 1 - owner;
  const wrongRole = await raw(pages[observer], { version: 1, type: 'setup', operation: 'action', requestId: randomUUID(), matchId, expectedRevision: view.revision, actionId: pending.id });
  assert.equal(wrongRole.code, 'WRONG_ACTOR');
  const stale = await raw(pages[owner], { version: 1, type: 'setup', operation: 'action', requestId: randomUUID(), matchId, expectedRevision: view.revision - 1, actionId: pending.id });
  assert.equal(stale.code, 'STALE_REVISION');
  await pages[owner].getByRole('button', { name: 'Disconnect', exact: true }).click();
  await pages[owner].getByRole('status').filter({ hasText: /^Disconnected$/ }).waitFor();
  await pages[owner].getByLabel('Local credential', { exact: true }).fill(tokens[owner]);
  await pages[owner].getByRole('button', { name: 'Join setup', exact: true }).click();
  await pages[owner].getByTestId('setup-status').filter({ hasText: `Revision ${view.revision} ` }).waitFor();
  const rejoined = await load(pages[owner], matchId);
  assert.deepEqual(rejoined.state.actions, (await load(pages[observer], matchId)).state.actions, 'same JVM reconnect preserves pending native actions');
  view = rejoined.state;
  const retryAction = view.actions.find(action => action.id === pending.id);
  assert.ok(retryAction, 'rejoin must retain the same opaque action id');
  view = await choose(pages[owner], retryAction, view.revision + 1);
  await pages[owner].getByRole('button', { name: 'Repeat last setup request', exact: true }).click();
  await pages[owner].waitForFunction(() => window.__m3b.incoming.some(value => value.duplicate === true));
  assert.equal((await load(pages[owner], matchId)).state.revision, view.revision, 'exact retry must not advance the revision');
  view = await resolveEvents(view);
  view = await finishPlayerAction(view);

  let completedTurns = 0;
  let moves = 0;
  while (completedTurns < 4) {
    view = await resolveEvents(view);
    const select = unmarkedSelect(view);
    assert.ok(select, `turn ${completedTurns + 1} needs an unmarked backfield player`);
    const selectedPlayerId = select.id.slice(select.id.indexOf('select-') + 'select-'.length);
    const before = view.players.find(player => player.id === selectedPlayerId);
    assert.ok(before && before.x !== null, 'selected player must be on the pitch');
    view = await choose(pages[indexFor(select.actor)], select, view.revision + 1);
    view = await resolveEvents(view);
    const move = playerAction(view, action => action.kind === 'move' && !/(dodge|rush)/i.test(action.label));
    assert.ok(move, `turn ${completedTurns + 1} needs a safe engine-issued move`);
    view = await choose(pages[indexFor(move.actor)], move, view.revision + 1);
    const after = view.players.find(player => player.id === selectedPlayerId);
    assert.ok(after && (after.x !== before.x || after.y !== before.y), 'the selected player must move to the server-issued square');
    moves++;
    view = await resolveEvents(view);
    view = await finishPlayerAction(view);
    const endTurn = playerAction(view, action => action.kind === 'endTurn');
    assert.ok(endTurn, `turn ${completedTurns + 1} needs an engine-issued end-turn action`);
    view = await choose(pages[indexFor(endTurn.actor)], endTurn, view.revision + 1);
    completedTurns++;
  }
  const [home, away] = await Promise.all(pages.map(page => load(page, matchId).then(reply => reply.state)));
  assert.equal(home.revision, away.revision); assert.deepEqual({ ...home, callerRole: away.callerRole }, away);
  for (let index = 0; index < 2; index++) {
    await pages[index].screenshot({ path: resolve(out, `core-turn-${subjects[index]}.png`), fullPage: true });
    await writeFile(resolve(out, `core-turn-wire-${subjects[index]}.json`), JSON.stringify(sanitized(await pages[index].evaluate(() => ({ incoming: window.__m3b.incoming, outgoing: window.__m3b.outgoing }))), null, 2));
  }
  assert.deepEqual(errors, []);
  await writeFile(resolve(out, 'core-turn-summary.json'), JSON.stringify({ passed: true, browser: await browser.version(), matchId, kickoffAction: kickoff.kind, pendingPrompt: pending.kind, completedTurns, moves, pendingReconnect: true, exactRetry: true, wrongRoleRejected: true, staleRejected: true, finalRevision: home.revision, errors }, null, 2));
  console.log(`PASS ${matchId}: kickoff, block-die recovery, exact retry, and ${moves} safe moves across ${completedTurns} engine turns.`);
} finally {
  await browser.close();
}
