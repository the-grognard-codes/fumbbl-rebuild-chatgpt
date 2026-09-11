import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { selectLocalRuntime } from './local-runtime-endpoint.mjs';

const out = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m3e');
const matchId = process.env.M4_MATCH_ID ?? JSON.parse(await readFile(resolve(out, 'setup/setup-restart.json'), 'utf8')).matchId;
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const results = [];
try {
  const page = await browser.newPage();
  await selectLocalRuntime(page);
  await page.goto('http://127.0.0.1:5173/matches');
  const run = async (token, requests) => page.evaluate(({ token, requests }) => new Promise((resolve, reject) => {
    const socket = new WebSocket('ws://127.0.0.1:22227/browser/v1');
    const replies = []; let index = 0;
    const timer = setTimeout(() => { socket.close(); reject(Error('Authorization test timed out')); }, 10000);
    const send = () => socket.send(JSON.stringify({ ...requests[index], requestId: `auth-check-${index}` }));
    socket.onopen = () => token ? socket.send(JSON.stringify({ version: 1, type: 'join', requestId: 'authenticate', token })) : send();
    socket.onmessage = event => {
      const value = JSON.parse(event.data);
      if (value.type === 'snapshot') { send(); return; }
      if (value.requestId !== `auth-check-${index}`) return;
      replies.push(value); index++;
      if (index < requests.length) send(); else { clearTimeout(timer); socket.close(); resolve(replies); }
    };
  }), { token, requests });
  const base = { version: 1, type: 'setup', matchId };
  const mutations = [
    { ...base, operation: 'choice', expectedRevision: 26, promptId: 'not-current', optionId: 'heads' },
    { ...base, operation: 'place', expectedRevision: 26, playerId: 'not-owned', to: null },
    { ...base, operation: 'confirm', expectedRevision: 26 },
    { ...base, operation: 'action', expectedRevision: 26, actionId: 'not-current' },
    { version: 1, type: 'preparedMatch', operation: 'activate', matchId, expectedRevision: 2 },
    { version: 1, type: 'matchResult', operation: 'load', matchId },
    { version: 1, type: 'matchResult', operation: 'replay', matchId, index: 0 },
  ];
  for (const reply of await run(null, mutations)) assert.equal(reply.code, 'AUTHENTICATION_REQUIRED');
  results.push({ unauthenticatedOperations: mutations.map(item => `${item.type}/${item.operation}`), denied: true });
  for (const subject of ['away', 'home']) {
    const token = (await readFile(resolve(`../containers/local/.secrets/browser_${subject}_token`), 'utf8')).trim();
    const load = { ...base, operation: 'load' };
    const probes = [{ ...mutations[3], role: 'home' }, { ...mutations[3], dice: [6] }, { ...base, operation: 'fixture' }, { ...load, matchId: '00000000-0000-0000-0000-000000000001' }];
    const replies = await run(token, [load, ...probes, load]);
    assert.equal(replies[0].code, 'ACCEPTED');
    assert.deepEqual(replies.slice(1, -1).map(item => item.code), ['INVALID_REQUEST', 'INVALID_REQUEST', 'INVALID_REQUEST', 'NOT_FOUND']);
    assert.deepEqual(replies.at(-1).state, replies[0].state, 'authorization/schema probes cannot change the engine');
    results.push({ subject, callerRole: replies[0].state.callerRole, forbiddenRoleDiceFixture: true, unknownMatch: true, unchanged: true });
  }
  await mkdir(out, { recursive: true });
  await writeFile(resolve(out, 'authorization-summary.json'), JSON.stringify({ pass: true, matchId, browser: browser.version(), results }, null, 2));
  console.log('PASS: unauthenticated setup/activation/result/replay denied; forged roles, dice and fixture operations rejected with unchanged two-subject states.');
} finally { await browser.close(); }
