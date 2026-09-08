import { chromium } from 'playwright';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

const fixture = process.env.M1B_FIXTURE ?? 'BOTH_DOWN_AWAY_BLOCK';
const output = resolve(process.env.M1B_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m1b');
const expectedOwner = fixture.includes('AWAY') ? 'away' : 'home';
const expectedOptions = fixture.includes('AWAY') ? 2 : 1;
if (!['BOTH_DOWN', 'BOTH_DOWN_BLOCK', 'BOTH_DOWN_AWAY', 'BOTH_DOWN_AWAY_BLOCK'].includes(fixture)) throw Error(`Unsupported M1B_FIXTURE: ${fixture}`);
await mkdir(output, { recursive: true });

const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const errors = [];
const observations = [];
try {
  const sessions = {};
  for (const actor of ['home', 'away']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(`${actor}: ${error.message}`));
    await page.addInitScript(() => {
      const NativeSocket = window.WebSocket;
      window.__m1b = { incoming: [], outgoing: [], socket: null, dropResolution: false, dropped: [] };
      window.WebSocket = class extends NativeSocket {
        constructor(...args) {
          super(...args);
          window.__m1b.socket = this;
          this.addEventListener('message', event => {
            const message = JSON.parse(event.data);
            window.__m1b.incoming.push(message);
            if (window.__m1b.dropResolution && (message.type === 'result' || (message.type === 'snapshot' && message.revision >= 1))) {
              window.__m1b.dropped.push(message);
              event.stopImmediatePropagation();
            }
          });
        }
        send(raw) {
          const message = JSON.parse(raw);
          if (message.type !== 'join') window.__m1b.outgoing.push(message);
          super.send(raw);
        }
      };
    });
    await page.goto('http://127.0.0.1:5173');
    const token = (await readFile(resolve(`../containers/local/.secrets/browser_${actor}_token`), 'utf8')).trim();
    await page.getByLabel('Local session credential').fill(token);
    await page.getByRole('button', { name: 'Join fixture' }).click();
    await page.getByText(`You are ${actor}`, { exact: true }).waitFor();
    await page.waitForFunction(() => !!document.querySelector('canvas'));
    sessions[actor] = { context, page, token };
  }
  const snapshot = page => page.evaluate(() => window.__m1b.incoming.filter(message => message.type === 'snapshot').at(-1));
  const resultFor = (page, id) => page.evaluate(requestId => window.__m1b.incoming.find(message => message.type === 'result' && message.requestId === requestId), id);
  const initial = { home: await snapshot(sessions.home.page), away: await snapshot(sessions.away.page) };
  const publicView = ({ actor, ...view }) => view;
  for (const [actor, view] of Object.entries(initial)) {
    assert.equal(view.revision, 0, 'Restart the local stack with a fresh M1b fixture');
    assert.equal(view.actor, actor);
    assert.equal(view.turnOwner, 'home');
    assert.ok(view.prompt, 'Choice fixture must begin paused at its prompt');
    assert.equal(view.prompt.actor, expectedOwner);
    assert.equal(view.prompt.revision, 0);
    assert.equal(view.prompt.options.length, expectedOptions);
    assert.equal(new Set(view.prompt.options.map(option => option.id)).size, expectedOptions);
    assert.ok(view.prompt.options.every(option => option.label === 'Both Down'));
  }
  assert.deepEqual(publicView(initial.home), publicView(initial.away));
  const chooser = sessions[expectedOwner];
  const observer = sessions[expectedOwner === 'home' ? 'away' : 'home'];
  assert.equal(await observer.page.getByRole('button', { name: 'Both Down' }).count(), expectedOptions);
  assert.equal(await chooser.page.getByRole('button', { name: 'Both Down' }).count(), expectedOptions);
  for (let index = 0; index < expectedOptions; index++) {
    assert.equal(await observer.page.getByRole('button', { name: 'Both Down' }).nth(index).isDisabled(), true);
    assert.equal(await chooser.page.getByRole('button', { name: 'Both Down' }).nth(index).isDisabled(), false);
  }

  const wrongRequestId = 'choice-wrong-actor';
  await observer.page.evaluate(({ requestId, prompt }) => window.__m1b.socket.send(JSON.stringify({ version: 1, type: 'choice', requestId, expectedRevision: 0, choiceId: prompt.id, optionId: prompt.options[0].id })), { requestId: wrongRequestId, prompt: initial[expectedOwner] .prompt });
  await observer.page.waitForFunction(id => window.__m1b.incoming.some(message => message.type === 'result' && message.requestId === id), wrongRequestId);
  const wrongActor = await resultFor(observer.page, wrongRequestId);
  assert.equal(wrongActor.status, 'rejected');
  assert.equal(wrongActor.code, 'WRONG_CHOICE_ACTOR');
  assert.equal(wrongActor.revision, 0);
  observations.push({ scenario: 'wrong-choice-owner', result: wrongActor });

  await chooser.page.evaluate(() => window.__m1b.socket.close());
  await chooser.page.getByRole('button', { name: 'Join fixture' }).waitFor();
  await chooser.page.getByLabel('Local session credential').fill(chooser.token);
  await chooser.page.getByRole('button', { name: 'Join fixture' }).click();
  await chooser.page.getByText(`You are ${expectedOwner}`, { exact: true }).waitFor();
  const pendingRejoined = await snapshot(chooser.page);
  assert.deepEqual(pendingRejoined, initial[expectedOwner]);
  await chooser.page.screenshot({ path: resolve(output, 'pending-rejoined.png'), fullPage: true });
  observations.push({ scenario: 'pending-rejoin', snapshot: pendingRejoined });

  await chooser.page.evaluate(() => { window.__m1b.dropResolution = true; });
  await chooser.page.getByRole('button', { name: 'Both Down' }).first().click();
  await observer.page.waitForFunction(() => window.__m1b.incoming.some(message => message.type === 'snapshot' && message.revision === 1));
  const observerResolved = await snapshot(observer.page);
  assert.equal(observerResolved.revision, 1);
  assert.equal(observerResolved.prompt, null);
  await chooser.page.waitForFunction(() => window.__m1b.dropped.some(message => message.type === 'result')
    && window.__m1b.dropped.some(message => message.type === 'snapshot' && message.revision === 1));
  assert.equal(await chooser.page.getByTestId('revision').textContent(), 'Revision: 0');
  const accepted = await chooser.page.evaluate(() => window.__m1b.dropped.find(message => message.type === 'result'));
  assert.equal(accepted.status, 'accepted'); assert.equal(accepted.revision, 1);

  await chooser.page.evaluate(() => { window.__m1b.dropResolution = false; });
  await chooser.page.evaluate(() => window.__m1b.socket.close());
  await chooser.page.getByRole('button', { name: 'Join fixture' }).waitFor();
  await chooser.page.getByLabel('Local session credential').fill(chooser.token);
  await chooser.page.getByRole('button', { name: 'Join fixture' }).click();
  await chooser.page.getByText(`You are ${expectedOwner}`, { exact: true }).waitFor();
  const resolvedRejoined = await snapshot(chooser.page);
  assert.deepEqual(publicView(resolvedRejoined), publicView(observerResolved));
  assert.equal(resolvedRejoined.prompt, null);
  assert.deepEqual(resolvedRejoined.resources, initial[expectedOwner].resources);
  await chooser.page.screenshot({ path: resolve(output, 'resolved-rejoined.png'), fullPage: true });

  const observerRevisionOneCount = await observer.page.evaluate(() => window.__m1b.incoming.filter(message => message.type === 'snapshot' && message.revision === 1).length);
  assert.equal(observerRevisionOneCount, 1);
  const beforeRepeat = await chooser.page.evaluate(() => window.__m1b.incoming.filter(message => message.type === 'result').length);
  await chooser.page.getByRole('button', { name: 'Repeat last request' }).click();
  await chooser.page.waitForFunction(count => window.__m1b.incoming.filter(message => message.type === 'result').length > count, beforeRepeat);
  const duplicate = await chooser.page.evaluate(() => window.__m1b.incoming.filter(message => message.type === 'result').at(-1));
  assert.equal(duplicate.status, 'accepted'); assert.equal(duplicate.duplicate, true); assert.equal(duplicate.revision, 1);
  assert.equal(await observer.page.evaluate(() => window.__m1b.incoming.filter(message => message.type === 'snapshot' && message.revision === 1).length), 1);

  const attacker = observerResolved.players.find(player => player.id === 'home-runner');
  const defender = observerResolved.players.find(player => player.id === 'away-runner');
  assert.equal(attacker.state, fixture.includes('BLOCK') ? 'standing' : 'prone');
  assert.equal(defender.state, 'prone');
  assert.deepEqual(resolvedRejoined.resources, observerResolved.resources);
  observations.push({ scenario: 'choice-resolved', accepted, duplicate, observerSnapshot: observerResolved, chooserSnapshot: resolvedRejoined, states: { attacker: attacker.state, defender: defender.state }, resources: observerResolved.resources });

  for (const [actor, session] of Object.entries(sessions)) {
    const trace = await session.page.evaluate(() => ({ incoming: window.__m1b.incoming, outgoing: window.__m1b.outgoing, dropped: window.__m1b.dropped }));
    await writeFile(resolve(output, `${actor}-wire.json`), JSON.stringify(trace, null, 2));
  }
  assert.deepEqual(errors, []);
  await writeFile(resolve(output, 'choice-demo.json'), JSON.stringify({ passed: true, fixture, browser: await browser.version(), node: process.version, platform: process.platform, observations, errors }, null, 2));
  console.log(`PASS ${fixture}: pending choice, wrong-owner rejection, disconnect recovery, lost acknowledgement recovery, and duplicate replay.`);
} finally {
  await browser.close();
}
