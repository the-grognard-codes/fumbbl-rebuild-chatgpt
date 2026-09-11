import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { chromium } from 'playwright';

const out = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3d/live');
const integrated = process.env.M3_INTEGRATED === '1';
const recoveryChecks = [];
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const contexts = await Promise.all([browser.newContext({ viewport: { width: 1440, height: 1080 } }), browser.newContext({ viewport: { width: 1440, height: 1080 } })]);
const pages = await Promise.all(contexts.map(context => context.newPage()));
const subjects = process.env.M3_CREATOR === 'home' ? ['home', 'away'] : ['away', 'home'];
const tokens = await Promise.all(subjects.map(subject => readFile(resolve(`../containers/local/.secrets/browser_${subject}_token`), 'utf8').then(value => value.trim())));
const errors = [];

for (const page of pages) {
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.__m3b = { socket: null, incoming: [], outgoing: [] };
    window.WebSocket = class extends Native {
      constructor(...args) { super(...args); window.__m3b.socket = this; this.addEventListener('message', event => {
        const value = JSON.parse(event.data); if (['preparedMatch', 'setupState', 'matchResult'].includes(value.type)) window.__m3b.incoming.push(value);
        if (window.__m3b.dropSetup && value.type === 'setupState') event.stopImmediatePropagation();
      }); }
      send(raw) { const value = JSON.parse(raw); if (['preparedMatch', 'setup', 'matchResult'].includes(value.type)) window.__m3b.outgoing.push(value); super.send(raw); }
    };
  });
}

const base = operation => ({ version: 1, type: 'preparedMatch', operation, requestId: randomUUID() });
const draft = () => ({ catalogVersion: 'bb2025-human-2026-09-08.1', ruleset: 'BB2025', rosterId: 'human', presetId: 'human-exhibition-1150', captainId: null, players: Array(11).fill('lineman').map((positionId, index) => ({ id: `p${index + 1}`, slot: index + 1, positionId, skillIds: [] })), resources: { rerolls: 4, assistantCoaches: 0, cheerleaders: 0, apothecary: 1, dedicatedFans: 0 } });
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
  assert.deepEqual({ ...home, callerRole: 'home' }, { ...away, callerRole: 'home' }, 'full projected states agree except persisted caller role');
  return home;
}
async function reconnect(page) {
  const index = pages.indexOf(page);
  await page.getByRole('button', { name: 'Disconnect', exact: true }).press('Enter');
  await page.getByRole('status').filter({ hasText: /^Disconnected$/ }).waitFor();
  await page.evaluate(() => { window.__m3b.dropSetup = false; });
  await page.getByLabel('Local credential', { exact: true }).fill(tokens[index]);
  await page.getByRole('button', { name: 'Join setup', exact: true }).press('Enter');
  await page.getByTestId('setup-status').waitFor();
}
async function submit(page, button, expected, fault = integrated) {
  if (fault) await page.evaluate(() => { window.__m3b.dropSetup = true; });
  await button.press('Enter');
  if (fault) {
    await page.waitForFunction(expected => window.__m3b.incoming.some(value => value.state?.revision === expected), expected);
    const request = await page.evaluate(() => window.__m3b.outgoing.filter(value => value.type === 'setup' && value.operation !== 'load').at(-1));
    const observer = pages[1 - pages.indexOf(page)];
    await observer.getByTestId('setup-status').filter({ hasText: `Revision ${expected} ` }).waitFor();
    const broadcasts = await observer.evaluate(expected => window.__m3b.incoming.filter(value => value.requestId === null && value.state?.revision === expected).length, expected);
    await reconnect(page);
    await page.getByRole('button', { name: 'Repeat last setup request', exact: true }).press('Enter');
    await page.waitForFunction(id => window.__m3b.incoming.some(value => value.requestId === id && value.duplicate), request.requestId);
    assert.equal(await observer.evaluate(expected => window.__m3b.incoming.filter(value => value.requestId === null && value.state?.revision === expected).length, expected), broadcasts, 'retry cannot broadcast a second mutation');
    const stale = await raw(page, { ...request, requestId: randomUUID() });
    assert.ok(['STALE_REVISION', 'MATCH_COMPLETED'].includes(stale.code), stale.code);
    recoveryChecks.push({ operation: request.operation, revision: expected, lostAcknowledgement: true, reconnect: true, exactRetry: true, staleCode: stale.code });
  }
  return waitRevision(expected);
}
async function choose(page, action, expected) {
  if (integrated) {
    const rejected = await raw(pages[1 - pages.indexOf(page)], { version: 1, type: 'setup', operation: 'action', requestId: randomUUID(), matchId, expectedRevision: expected - 1, actionId: action.id });
    assert.equal(rejected.code, 'WRONG_ACTOR');
  }
  await page.getByLabel('Server action', { exact: true }).selectOption(action.id);
  return submit(page, page.getByRole('button', { name: 'Execute action', exact: true }), expected);
}

async function arrange(view) {
  const role = view.actor, page = pages[indexFor(role)];
  for (const player of view.players.filter(p => p.role === role && p.x !== null)) {
    await page.getByLabel('Setup player', { exact: true }).selectOption(player.id);
    await page.getByRole('button', { name: 'Return selected player to reserve', exact: true }).click();
    view = await waitRevision(view.revision + 1);
  }
  const players = view.players.filter(p => p.role === role && !/knocked out|casualty|dead|sent off/i.test(p.state)).slice(0, 11);
  for (let i = 0; i < players.length; i++) {
    await page.getByLabel('Setup player', { exact: true }).selectOption(players[i].id);
    const x = i < 3 ? 12 : 10, y = i < 3 ? 6 + i : 1 + i;
    await page.getByLabel('Setup X', { exact: true }).fill(String(role === 'home' ? x : 25 - x));
    await page.getByLabel('Setup Y', { exact: true }).fill(String(y));
    view = await submit(page, page.getByRole('button', { name: 'Place on empty own-half square', exact: true }), view.revision + 1, integrated && i === 0);
  }
  return submit(page, page.getByRole('button', { name: 'Confirm legal setup', exact: true }), view.revision + 1);
}
const distance = (a,b) => Math.max(Math.abs(a.x-b.x), Math.abs(a.y-b.y));
const attemptedTurns = new Set();
function selectAction(view) {
  if (view.phase === 'READY_FOR_KICKOFF') return view.actions.find(a => a.id.endsWith(view.actor === 'home' ? 'kick-17-7' : 'kick-8-7'));
  for (const suffix of [':decline-event', ':end-event', ':reroll:team', ':skill:true']) {
    const action = view.actions.find(a => a.id.endsWith(suffix)); if (action) return action;
  }
  const regular = view.actions.some(a => a.kind === 'endTurn');
  if (regular && view.homeScore + view.awayScore === 0 && view.ball) {
    const owner = view.actor;
    const carrier = view.players.find(p => p.x === view.ball.x && p.y === view.ball.y);
    if (!carrier || carrier.role === owner) {
      const select = view.actions.filter(a => a.kind === 'select').map(action => ({ action, p: view.players.find(p => action.id.endsWith(p.id)) })).filter(v => v.p && v.p.x !== null);
      select.sort((a,b) => distance(a.p, view.ball) - distance(b.p, view.ball));
      const active = view.players.find(p => p.id === view.activePlayerId);
      const turnKey = `${view.half}:${view.drive}:${view.homeTurn}:${view.awayTurn}:${owner}`;
      if (!active && select.length && !attemptedTurns.has(turnKey)) { attemptedTurns.add(turnKey); return select[0].action; }
      if (active) {
        const hasBall = carrier?.id === active.id;
        const target = hasBall ? { x: owner === 'home' ? 25 : 0, y: active.y } : view.ball;
        const moves = view.actions.filter(a => a.kind === 'move' && !/rush/i.test(a.label)).map(action => { const m = /move-(\d+)-(\d+)$/.exec(action.id); return { action, x: +m[1], y: +m[2] }; });
        const occupied = new Set(view.players.filter(p=>p.id!==active.id && p.x!==null).map(p=>`${p.x},${p.y}`));
        const distances = new Map([[`${target.x},${target.y}`,0]]), queue=[target];
        for (let q=0;q<queue.length;q++) { const at=queue[q], depth=distances.get(`${at.x},${at.y}`);
          for(let dx=-1;dx<=1;dx++) for(let dy=-1;dy<=1;dy++) { const x=at.x+dx,y=at.y+dy,key=`${x},${y}`;
            if(x>=0&&x<26&&y>=0&&y<15&&!occupied.has(key)&&!distances.has(key)){ distances.set(key,depth+1); queue.push({x,y}); }
          }
        }
        const pathDistance=p=>distances.get(`${p.x},${p.y}`)??1000;
        const improving = moves.filter(m => pathDistance(m) < pathDistance(active));
        improving.sort((a,b) => (/(dodge)/i.test(a.action.label) ? 10 : 0) - (/(dodge)/i.test(b.action.label) ? 10 : 0) + pathDistance(a)-pathDistance(b));
        if (improving.length) return improving[0].action;
      }
    }
  }
  return view.actions.find(a => a.kind === 'endAction') ?? view.actions.find(a => a.kind === 'endTurn') ?? view.actions.find(a => a.kind === 'reroll' && /^Do not/i.test(a.label)) ?? view.actions[0];
}
let matchId;
try {
  await Promise.all(pages.map((page,index) => connect(page,index,'/matches')));
  const teams = [];
  for (let index=0; index<2; index++) {
    const listed = await saved(pages[index], { operation:'list' });
    let selected;
    if (integrated) {
      const created = await saved(pages[index], { operation: 'create', draft: draft() });
      assert.equal(created.code, 'OK', 'integrated demo requires capacity for its own synthetic source team');
      selected = created.document;
    }
    for (const team of listed.teams) {
      if (selected) break;
      const result = await saved(pages[index], { operation:'load', teamId:team.teamId });
      const d = result.document?.draft;
      if (d && d.players.length === 11 && d.players.every(p => p.positionId === 'lineman' && p.skillIds.length === 0) && d.captainId === null) { selected=result.document; break; }
    }
    if (!selected) { const result=await saved(pages[index],{operation:'create',draft:draft()}); assert.equal(result.code,'OK'); selected=result.document; }
    teams.push(selected);
    await pages[index].getByRole('button',{name:'Refresh saved teams',exact:true}).click();
    await pages[index].getByLabel('Saved team',{exact:true}).selectOption(selected.teamId);
  }
  await pages[0].getByLabel('Invite intended opponent',{exact:true}).selectOption(subjects[1]);
  await pages[0].getByRole('button',{name:'Create match and freeze team',exact:true}).click();
  await pages[0].getByRole('region',{name:'Authoritative prepared match'}).waitFor();
  matchId = await pages[0].getByLabel('Match ID',{exact:true}).inputValue();
  await pages[1].getByLabel('Match ID',{exact:true}).fill(matchId);
  await pages[1].getByRole('button',{name:'Reload authoritative match',exact:true}).click();
  await pages[1].getByRole('button',{name:'Join vacant role with saved team',exact:true}).click();
  await pages[1].getByRole('button',{name:'Activate setup',exact:true}).waitFor();
  await pages[0].getByRole('button',{name:'Reload authoritative match',exact:true}).click();
  await pages[0].getByRole('button',{name:'Activate setup',exact:true}).click();
  await pages[0].getByRole('link',{name:'Open match setup',exact:true}).waitFor();
  if (integrated) {
    const frozen = await raw(pages[0], { ...base('load'), matchId });
    for (let index = 0; index < 2; index++) {
      const changed = structuredClone(teams[index].draft); changed.resources.rerolls = 1;
      const updated = await saved(pages[index], { operation: 'update', teamId: teams[index].teamId, expectedDocumentVersion: teams[index].documentVersion, draft: changed });
      assert.equal(updated.code, 'OK');
      const imported = structuredClone(updated.document); imported.draft.resources.rerolls = 2;
      assert.equal((await saved(pages[index], { operation: 'import', document: imported })).code, 'OK');
    }
    assert.deepEqual((await raw(pages[0], { ...base('load'), matchId })).document, frozen.document, 'active frozen rosters remain byte-for-byte stable after source edits');
  }
  await Promise.all(pages.map((page,index) => connect(page,index,`/setup?matchId=${matchId}`)));
  let view = await waitRevision(0);
  if (integrated) { await reconnect(pages[0]); await reconnect(pages[1]); view = await waitRevision(0); }
  const checkpoints = []; console.log(`Started match ${matchId}`);
  for (let i=0; i<600 && view.phase !== 'FULL_TIME'; i++) {
    const before = view; if(i%50===0) console.log(JSON.stringify({actions:i,revision:view.revision,half:view.half,homeTurn:view.homeTurn,awayTurn:view.awayTurn,score:[view.homeScore,view.awayScore]}));
    if (view.prompt) {
      const chooser = pages[indexFor(view.prompt.actor)];
      view = await submit(chooser, chooser.getByRole('button',{name:view.prompt.kind==='coin'?'heads':'receive',exact:true}), view.revision + 1);
    } else if (view.phase==='SETUP') view=await arrange(view);
    else { const action=selectAction(view); assert.ok(action, `No action at ${view.phase}/${view.turnMode}`); view=await choose(pages[indexFor(action.actor)],action,view.revision+1); }
    if (before.half !== view.half || before.homeScore !== view.homeScore || before.awayScore !== view.awayScore || view.phase==='FULL_TIME') {
      checkpoints.push({ revision:view.revision,half:view.half,drive:view.drive,phase:view.phase,homeScore:view.homeScore,awayScore:view.awayScore });
      const last = await pages[indexFor(before.actor)].evaluate(() => window.__m3b.outgoing.filter(v=>v.type==='setup' && v.operation!=='load').at(-1));
      if (!integrated && last?.expectedRevision === before.revision) {
        const repeated=await raw(pages[indexFor(before.actor)],last); assert.equal(repeated.code,'ACCEPTED'); assert.equal(repeated.duplicate,true); assert.equal(repeated.state.revision,view.revision);
      }
      await pages[0].screenshot({path:resolve(out,`transition-${view.revision}.png`),fullPage:true});
    }
  }
  assert.equal(view.phase,'FULL_TIME'); assert.ok(view.homeScore+view.awayScore>0,'live match includes a touchdown');
  assert.ok(checkpoints.some(c=>c.half===2),'second half reached');
  const metadata=[];
  for (let index=0;index<2;index++) {
    const trace = await pages[index].evaluate(()=>window.__m3b);
    await writeFile(resolve(out,`gameplay-${index}.json`),JSON.stringify({incoming:trace.incoming,outgoing:trace.outgoing},null,2));
    await pages[index].getByRole('link', { name: 'Open final result and replay', exact: true }).press('Enter');
    assert.equal(new URL(pages[index].url()).searchParams.get('matchId'), matchId);
    await pages[index].getByLabel('Local credential',{exact:true}).fill(tokens[index]);
    await pages[index].getByRole('button',{name:'Load completed match',exact:true}).click();
    await pages[index].getByRole('heading',{name:'Final score',exact:true}).waitFor();
    const result=await raw(pages[index],{version:1,type:'matchResult',operation:'load',requestId:randomUUID(),matchId}); assert.equal(result.code,'ACCEPTED'); metadata.push(result.result);
    const terminal=await raw(pages[index],{version:1,type:'matchResult',operation:'replay',requestId:randomUUID(),matchId,index:result.result.eventCount-1}); assert.equal(terminal.code,'ACCEPTED'); assert.equal(terminal.event.kind,'FULL_TIME');
    await pages[index].getByRole('button',{name:'First',exact:true}).click();
    await pages[index].getByRole('heading',{name:/Event 1 of/}).waitFor();
    await pages[index].getByRole('button',{name:'Last',exact:true}).click();
    await pages[index].getByRole('heading',{name:/FULL_TIME/}).waitFor();
    await pages[index].screenshot({path:resolve(out,`result-${index}.png`),fullPage:true});
  }
  assert.deepEqual(metadata[0],metadata[1]);
  await writeFile(resolve(out,'completed-match.json'),JSON.stringify({matchId,subjects,result:metadata[0],checkpoints,recoveryChecks},null,2));
  assert.deepEqual(errors,[]);
  console.log(JSON.stringify({pass:true,matchId,result:metadata[0],checkpoints,browser:browser.version()}));
} finally {
  for (let i=0;i<pages.length;i++) { const trace=await pages[i].evaluate(()=>window.__m3b).catch(()=>null); if(trace) await writeFile(resolve(out,`wire-${i}.json`),JSON.stringify({incoming:trace.incoming,outgoing:trace.outgoing},null,2)); }
  await browser.close();
}
