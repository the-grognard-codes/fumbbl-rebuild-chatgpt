import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomUUID, createHash } from 'node:crypto';
import { chromium } from 'playwright';
import { selectLocalRuntime } from './local-runtime-endpoint.mjs';
import { r2Sql } from './r2-process-kill.mjs';

assert.equal(process.env.M4_WS_ENDPOINT, 'ws://127.0.0.1:22230/browser/v1');
const docker = process.env.DOCKER_EXE ?? (process.platform === 'win32' ? resolve(process.env.LOCALAPPDATA, 'Programs/DockerDesktop/resources/bin/docker.exe') : 'docker');
const run = promisify(execFile), server = 'ffb-local-r2b-server-1';
const out = resolve(process.env.M3_EVIDENCE ?? '../.notes/overhaul-analysis/verification/r2/rejections');
await mkdir(out, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const pages = await Promise.all([browser.newPage(), browser.newPage()]);
const tokens = await Promise.all(['home', 'away'].map(role => readFile(resolve(`../containers/local/.secrets/browser_${role}_token`), 'utf8').then(value => value.trim())));
const fixtures = [];
const request = (type, operation, fields = {}) => ({ version: 1, type, operation, requestId: randomUUID(), ...fields });
for (const page of pages) {
  await selectLocalRuntime(page);
  await page.addInitScript(() => {
    const Native = window.WebSocket;
    window.WebSocket = class extends Native { constructor(...args) { super(...args); window.__r2Socket = this; } };
  });
}
async function connect(index, path = '/matches') {
  await pages[index].goto(`http://127.0.0.1:5173${path}`);
  await pages[index].getByLabel('Local credential', { exact: true }).fill(tokens[index]);
  await pages[index].getByRole('button', { name: path.startsWith('/setup') ? 'Join setup' : 'Connect to saved teams', exact: true }).click();
  await pages[index].getByRole('status').filter({ hasText: /^Connected$/ }).waitFor();
}
async function raw(index, request) {
  return pages[index].evaluate(request => new Promise((resolve, reject) => {
    const socket = window.__r2Socket;
    const timeout = setTimeout(() => { socket.removeEventListener('message', receive); reject(Error('Response timeout')); }, 10000);
    function receive(event) { const reply = JSON.parse(event.data); if (reply.requestId === request.requestId) { clearTimeout(timeout); socket.removeEventListener('message', receive); resolve(reply); } }
    socket.addEventListener('message', receive); socket.send(JSON.stringify(request));
  }), request);
}
try {
  await Promise.all([connect(0), connect(1)]);
  const teams = [];
  for (let index = 0; index < 2; index++) {
    const draft = { catalogVersion: 'bb2025-human-2026-09-08.1', ruleset: 'BB2025', rosterId: 'human', presetId: 'human-exhibition-1150', captainId: null,
      players: Array.from({ length: 11 }, (_, n) => ({ id: `p${n + 1}`, slot: n + 1, positionId: 'lineman', skillIds: [] })),
      resources: { rerolls: 0, assistantCoaches: 0, cheerleaders: 0, apothecary: 0, dedicatedFans: 0 } };
    const reply = await raw(index, request('savedTeam', 'create', { draft })); assert.equal(reply.code, 'OK'); teams.push(reply.document);
  }
  for (const kind of ['unsupported-version', 'corrupt-checksum']) {
    const created = await raw(0, request('preparedMatch', 'create', { teamId: teams[0].teamId, expectedDocumentVersion: teams[0].documentVersion, intendedOpponent: 'away' }));
    assert.equal(created.code, 'ACCEPTED'); const matchId = created.document.matchId; assert.match(matchId, /^[0-9a-f-]{36}$/);
    assert.equal((await raw(1, request('preparedMatch', 'join', { matchId, expectedRevision: 1, teamId: teams[1].teamId, expectedDocumentVersion: teams[1].documentVersion }))).code, 'ACCEPTED');
    assert.equal((await raw(0, request('preparedMatch', 'activate', { matchId, expectedRevision: 2 }))).code, 'ACCEPTED');
    const original = await r2Sql(`SELECT artifact_json FROM ffb_match_recovery WHERE matchid='${matchId}';`);
    await writeFile(resolve(`../.tools/r2-rejection-original-${matchId}.json`), original, { flag: 'wx' });
    const changed = JSON.parse(original);
    if (kind === 'unsupported-version') { changed.payload.recoveryVersion = 99; changed.sha256 = createHash('sha256').update(JSON.stringify(changed.payload)).digest('hex'); }
    else changed.payload.revision = 99; // Deliberately retain the original checksum.
    const modified = JSON.stringify(changed), hex = Buffer.from(modified).toString('hex');
    assert.equal(await r2Sql(`UPDATE ffb_match_recovery SET artifact_json=CONVERT(0x${hex} USING utf8mb4) WHERE matchid='${matchId}' AND generation=1; SELECT ROW_COUNT();`), '1');
    fixtures.push({ kind, matchId, modified, originalSha256: createHash('sha256').update(original).digest('hex') });
  }
  const before = JSON.parse((await run(docker, ['inspect', server], { windowsHide: true })).stdout)[0];
  await run(docker, ['kill', '--signal', 'KILL', server], { windowsHide: true });
  const killed = JSON.parse((await run(docker, ['inspect', server], { windowsHide: true })).stdout)[0]; assert.equal(killed.State.ExitCode, 137);
  await run(docker, ['start', server], { windowsHide: true });
  let after;
  for (let attempt = 0; attempt < 90; attempt++) {
    after = JSON.parse((await run(docker, ['inspect', server], { windowsHide: true })).stdout)[0];
    if (after.State.Health?.Status === 'healthy') break;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  assert.equal(after.State.Health.Status, 'healthy'); assert.equal(before.Image, after.Image);
  const results = [];
  for (const fixture of fixtures) {
    const expected = fixture.kind === 'unsupported-version' ? 'RECOVERY_UNSUPPORTED' : 'RECOVERY_CORRUPT';
    const replies = [];
    for (let index = 0; index < 2; index++) {
      await connect(index, `/setup?matchId=${fixture.matchId}`);
      const reply = await raw(index, request('setup', 'load', { matchId: fixture.matchId }));
      assert.equal(reply.code, expected); assert.equal(reply.state, null); replies.push(reply);
      await pages[index].getByText(new RegExp(expected)).waitFor();
      await pages[index].screenshot({ path: resolve(out, `${fixture.kind}-${index}.png`), fullPage: true });
    }
    assert.equal(await r2Sql(`SELECT artifact_json FROM ffb_match_recovery WHERE matchid='${fixture.matchId}';`), fixture.modified);
    results.push({ kind: fixture.kind, matchId: fixture.matchId, originalSha256: fixture.originalSha256, rejectedSha256: createHash('sha256').update(fixture.modified).digest('hex'), replies, storedArtifactUnchanged: true });
  }
  await writeFile(resolve(out, 'rejections.json'), JSON.stringify({ pass: true, exitCode: killed.State.ExitCode, image: after.Image, browser: browser.version(), results }, null, 2), { flag: 'wx' });
  console.log(JSON.stringify({ pass: true, cases: results.map(result => ({ kind: result.kind, matchId: result.matchId })), twoClientFailClosed: true }));
} finally { await browser.close(); }
