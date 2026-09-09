import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const id = '12345678-1234-1234-1234-123456789abc';
const fixture = JSON.parse(await readFile('test/fixtures/wire-v1.json', 'utf8'));
const snapshot = fixture[0].messages.find(item => item.type === 'snapshot');
const team = { teamId: id, documentVersion: 1, catalogVersion: 'catalog' };
const member = role => ({ role, sourceTeamId: id, sourceDocumentVersion: 1, ruleset: 'BB2025', catalogVersion: 'catalog', rosterId: 'human', presetId: 'preset', presetVersion: '1', validation: { valid: true, total: 1, budget: 2, skillPoints: 0, messages: [] }, roster: { captainId: null, resources: { rerolls: 0 }, players: [] } });
const document = { formatVersion: 1, matchId: id, documentVersion: 1, lifecycle: 'WAITING_FOR_OPPONENT', invitation: { intendedOpponent: 'away' }, home: member('home'), away: null };
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const page = await browser.newPage();
try {
  await page.addInitScript(({ team, document, snapshot }) => {
    class MockSocket {
      static OPEN = 1;
      readyState = 1;
      constructor() { window.mockSocket = this; queueMicrotask(() => this.onopen?.()); }
      close() { this.readyState = 3; this.onclose?.(); }
      send(raw) {
        const request = JSON.parse(raw);
        const reply = item => queueMicrotask(() => this.onmessage?.({ data: JSON.stringify(item) }));
        const prepared = (code, duplicate = false) => ({ version: 1, type: 'preparedMatch', requestId: request.requestId, code, duplicate,
          callerRole: code === 'ACCEPTED' ? 'home' : null, recoveryMatchId: code === 'MATCH_OUTCOME_UNKNOWN' ? document.matchId : null,
          document: code === 'ACCEPTED' ? document : null });
        if (request.type === 'join') {
          reply({ version: 1, type: 'result', requestId: request.requestId, status: 'accepted', code: 'JOINED', revision: 0, duplicate: false });
          reply({ ...snapshot, actor: sessionStorage.getItem('mockSubject') ?? 'home' });
        } else if (request.type === 'savedTeam') {
          reply({ version: 1, type: 'savedTeam', requestId: request.requestId, code: 'OK', document: null, versionStatus: null, validation: null, teams: [team] });
        } else if (request.type === 'preparedMatch' && request.operation === 'create') {
          const persisted = JSON.parse(sessionStorage.getItem('ffb.prepared-match.pending.v1'));
          if (!persisted || JSON.stringify(persisted.request) !== raw) throw Error('Mutation sent before durable retry metadata');
          const creates = JSON.parse(sessionStorage.getItem('mockCreates') ?? '[]'); creates.push(request);
          sessionStorage.setItem('mockCreates', JSON.stringify(creates));
          reply(prepared(creates.length === 1 ? 'MATCH_OUTCOME_UNKNOWN' : 'ACCEPTED', creates.length > 1));
        } else if (request.type === 'preparedMatch' && request.operation === 'join') {
          const codes = JSON.parse(sessionStorage.getItem('mockJoinCodes') ?? '["AUTHORIZATION","STALE_TEAM_REVISION","CONFLICT"]');
          const code = codes.shift(); sessionStorage.setItem('mockJoinCodes', JSON.stringify(codes)); reply(prepared(code));
        } else if (request.type === 'preparedMatch' && request.operation === 'load') reply(prepared('ACCEPTED'));
      }
    }
    window.WebSocket = MockSocket;
  }, { team, document, snapshot });
  const connect = async () => {
    await page.getByLabel('Local credential', { exact: true }).fill('credential');
    await page.getByRole('button', { name: 'Connect to saved teams', exact: true }).click();
    await page.getByRole('status').filter({ hasText: 'Connected' }).waitFor();
  };
  await page.goto('http://127.0.0.1:5173/matches'); await connect();
  await page.getByRole('button', { name: 'Create match and freeze team', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: /acknowledgement was lost/ }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Create match and freeze team', exact: true }).isDisabled(), true);
  const first = await page.evaluate(() => JSON.parse(sessionStorage.mockCreates)[0]);
  await page.evaluate(() => sessionStorage.setItem('mockSubject', 'away'));
  await page.reload(); await connect();
  await page.getByRole('alert').filter({ hasText: /different local credential/ }).waitFor();
  assert.equal(await page.evaluate(() => JSON.parse(sessionStorage.mockCreates).length), 1, 'another identity must not replay');
  assert.equal(await page.evaluate(() => JSON.parse(sessionStorage.getItem('ffb.prepared-match.pending.v1')).subject), 'home');
  await page.evaluate(() => sessionStorage.setItem('mockSubject', 'home'));
  await page.reload(); await connect();
  await page.getByText(/waiting for opponent/i).waitFor();
  const second = await page.evaluate(() => JSON.parse(sessionStorage.mockCreates)[1]);
  assert.deepEqual(second, first, 'reload retries the exact create request');
  await page.getByRole('button', { name: 'Retry exact request', exact: true }).waitFor({ state: 'detached' });
  assert.equal(await page.evaluate(() => sessionStorage.getItem('ffb.prepared-match.pending.v1')), null);
  const selected = await page.getByLabel('Saved team', { exact: true }).inputValue();
  const match = await page.getByLabel('Match ID', { exact: true }).inputValue();
  for (const text of ['You are not a match participant.', 'The saved team changed; refresh saved teams before trying again.', 'Another request already changed this match.']) {
    await page.getByRole('button', { name: 'Join vacant role with saved team', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: text }).waitFor();
    assert.equal(await page.getByLabel('Saved team', { exact: true }).inputValue(), selected);
    assert.equal(await page.getByLabel('Match ID', { exact: true }).inputValue(), match);
  }
  const before = await page.getByRole('alert').innerText();
  const unrelated = { version: 1, type: 'preparedMatch', requestId: 'unrelated', code: 'AUTHORIZATION', duplicate: false, callerRole: null, recoveryMatchId: null, document: null };
  await page.evaluate(message => window.mockSocket.onmessage({ data: JSON.stringify(message) }), unrelated);
  assert.equal(await page.getByRole('alert').innerText(), before, 'unrelated response cannot replace status');
  await page.evaluate(() => { window.retiredSocket = window.mockSocket; window.mockSocket.close(); });
  await connect(); await page.getByRole('alert').waitFor({ state: 'detached' });
  await page.evaluate(message => window.retiredSocket.onmessage({ data: JSON.stringify(message) }), unrelated);
  assert.equal(await page.getByRole('alert').count(), 0, 'retired response cannot replace state');
  assert.equal(await page.getByLabel('Match ID', { exact: true }).inputValue(), match);
  console.log('Mounted M2c retry/error/identity/correlation/retired-socket regression passed.');
} finally { await browser.close(); }
