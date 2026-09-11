import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { chromium } from 'playwright';
import { selectLocalRuntime } from './local-runtime-endpoint.mjs';

const out = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3d/restart');
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const contexts = await Promise.all([browser.newContext({ viewport: { width: 1440, height: 1080 } }), browser.newContext({ viewport: { width: 1440, height: 1080 } })]);
const pages = await Promise.all(contexts.map(context => context.newPage()));
const subjects = ['away', 'home']; // The match creator is persisted as home despite using the away credential.
const tokens = await Promise.all(subjects.map(subject => readFile(resolve(`../containers/local/.secrets/browser_${subject}_token`), 'utf8').then(value => value.trim())));
const errors = [];

for (const page of pages) {
  await selectLocalRuntime(page);
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.__m3b = { socket: null, incoming: [], outgoing: [] };
    window.WebSocket = class extends Native {
      constructor(...args) { super(...args); window.__m3b.socket = this; this.addEventListener('message', event => {
        const value = JSON.parse(event.data); if (['preparedMatch', 'setupState', 'matchResult'].includes(value.type)) window.__m3b.incoming.push(value);
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
  return home;
}
async function choose(page, action, expected) {
  await page.getByLabel('Server action', { exact: true }).selectOption(action.id);
  await page.getByRole('button', { name: 'Execute action', exact: true }).click();
  return waitRevision(expected);
}


try {
  const savedMatch=JSON.parse(await readFile(resolve(process.env.M3_LIVE_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3d/live', 'completed-match.json'),'utf8'));
  const original=JSON.parse(await readFile(resolve(process.env.M3_LIVE_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3d/live', 'gameplay-0.json'),'utf8'));
  const expected=new Map(original.incoming.filter(v=>v.state).map(v=>[v.state.revision,{...v.state,callerRole:'home',actions:[],prompt:null}]));
  const resultId=savedMatch.matchId;
  for(let index=0;index<2;index++) {
    const page=pages[index];
    await page.goto(`http://127.0.0.1:5173/results?matchId=${resultId}`);
    await page.getByLabel('Local credential',{exact:true}).fill(tokens[index]);
    await page.getByRole('button',{name:'Load completed match',exact:true}).click();
    await page.getByRole('heading',{name:'Final score',exact:true}).waitFor();
    const result=await raw(page,{version:1,type:'matchResult',operation:'load',requestId:randomUUID(),matchId:resultId});
    assert.equal(result.code,'ACCEPTED'); assert.deepEqual(result.result,savedMatch.result);
    if(index===0) for(let eventIndex=0;eventIndex<result.result.eventCount;eventIndex++) {
      const response=await raw(page,{version:1,type:'matchResult',operation:'replay',requestId:randomUUID(),matchId:resultId,index:eventIndex});
      assert.equal(response.code,'ACCEPTED'); assert.equal(response.event.revision,eventIndex);
      assert.deepEqual(response.event.state,expected.get(eventIndex),`recorded state ${eventIndex} survives JVM restart unchanged`);
    }
    await page.getByRole('button',{name:'First',exact:true}).click(); await page.getByRole('heading',{name:/Event 1 of/}).waitFor();
    await page.getByRole('button',{name:'Last',exact:true}).click(); await page.getByRole('heading',{name:/FULL_TIME/}).waitFor();
    await page.screenshot({path:resolve(out,`result-${index}.png`),fullPage:true});
    await page.getByRole('button',{name:'Previous',exact:true}).click();
    await page.getByRole('heading',{name:new RegExp(`Event ${savedMatch.result.eventCount-1} of`)}).waitFor();
    await page.screenshot({path:resolve(out,`replay-${index}.png`),fullPage:true});
    await connect(page,index,`/setup?matchId=${resultId}`);
    await page.getByTestId('setup-status').filter({hasText:`Revision ${savedMatch.result.finalRevision} `}).waitFor();
    const terminal=await load(page,resultId); assert.equal(terminal.state.phase,'FULL_TIME');
    const rejected=await raw(page,{version:1,type:'setup',operation:'confirm',requestId:randomUUID(),matchId:resultId,expectedRevision:savedMatch.result.finalRevision});
    assert.equal(rejected.code,'MATCH_COMPLETED');
  }
  assert.deepEqual(errors,[]);
  const summary={pass:true,matchId:resultId,result:savedMatch.result,verifiedEvents:expected.size,browser:browser.version()};
  await writeFile(resolve(out,'summary.json'),JSON.stringify(summary,null,2)); console.log(JSON.stringify(summary));
} finally { await browser.close(); }
