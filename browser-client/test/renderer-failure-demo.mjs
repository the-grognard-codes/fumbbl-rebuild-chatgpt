import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
import { control } from './m1c-local.mjs';

const output = resolve(process.env.M1C_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m1c');
const token = (await readFile(resolve('../containers/local/.secrets/browser_home_token'), 'utf8')).trim();
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
const results = [];

async function joinAndMove(page, requireRender) {
  await page.getByLabel('Local session credential').fill(token);
  await page.getByRole('button', { name: 'Join fixture', exact: true }).click();
  await page.getByText('You are home', { exact: true }).waitFor();
  if (requireRender) await page.waitForFunction(() => document.querySelector('.pitch')?.dataset.renderedRevision === '0');
  await page.getByLabel('Destination X', { exact: true }).fill('6');
  await page.getByLabel('Destination Y', { exact: true }).fill('7');
  await page.getByRole('button', { name: 'Submit move', exact: true }).click();
  await page.getByText('Revision: 1', { exact: true }).waitFor();
  await page.getByRole('log').getByText('accepted · MOVED', { exact: true }).waitFor();
  await page.getByRole('row').filter({ hasText: 'home-runner' }).filter({ hasText: '6, 7' }).waitFor();
  if (requireRender) await page.waitForFunction(() => document.querySelector('.pitch')?.dataset.renderedRevision === '1');
  for (let index = 0; index < 20; index++) await page.getByLabel('Player', { exact: true }).selectOption(index % 2 ? 'home-runner' : 'away-runner');
  if (requireRender) await page.waitForFunction(() => document.querySelector('.pitch')?.dataset.renderedRevision === '1');
}

try {
  for (const scenario of [
    { name: 'valid-asset', query: '', expected: null },
    { name: 'missing-mapping', query: '?rendererFault=missing-asset-mapping', expected: 'Token marker asset mapping is unavailable; using vector fallback.' },
    { name: 'failed-load', query: '?rendererFault=asset-load-failure', expected: 'Token marker asset could not load; using vector fallback.' }
  ]) {
    await control('reset', 'MOVEMENT');
    const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } });
    const page = await context.newPage();
    const errors = [];
    let markerRequests = 0;
    const markerResponses = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('request', request => {
      if (request.url().includes(scenario.name === 'failed-load' ? 'renderer-test-missing-marker.svg' : 'token-marker.svg')) markerRequests++;
    });
    page.on('response', response => {
      if (response.url().includes(scenario.name === 'failed-load' ? 'renderer-test-missing-marker.svg' : 'token-marker.svg')) {
        markerResponses.push({ status: response.status(), contentType: response.headers()['content-type'] });
      }
    });
    await page.goto(`http://127.0.0.1:5173/${scenario.query}`);
    await page.waitForSelector('canvas');
    if (scenario.expected) await page.getByRole('status').filter({ hasText: scenario.expected }).waitFor();
    else assert.equal(await page.getByRole('status').count(), 0);
    await joinAndMove(page, true);
    assert.equal(markerRequests, scenario.name === 'missing-mapping' ? 0 : 1);
    if (scenario.name === 'valid-asset') assert.deepEqual(markerResponses, [{ status: 200, contentType: 'image/svg+xml' }]);
    assert.deepEqual(errors, []);
    await page.screenshot({ path: resolve(output, `${scenario.name}.png`), fullPage: true });
    results.push({ scenario: scenario.name, fallback: scenario.expected, revision: 1, markerRequests, markerResponses, pageErrors: errors });
    await context.close();
  }

  await control('reset', 'MOVEMENT');
  const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } });
  await context.addInitScript(() => {
    window.__blockWebgl = sessionStorage.getItem('m1c-webgl-fault') !== 'recovered';
    const getContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, ...args) {
      return window.__blockWebgl && typeof type === 'string' && type.startsWith('webgl') ? null : getContext.call(this, type, ...args);
    };
  });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('http://127.0.0.1:5173/');
  await page.getByRole('alert').filter({ hasText: 'The pitch could not initialize WebGL. Reload this page' }).waitFor();
  assert.equal(await page.locator('canvas').count(), 0);
  await joinAndMove(page, false);
  await page.screenshot({ path: resolve(output, 'webgl-init-failure.png'), fullPage: true });
  await page.evaluate(() => { sessionStorage.setItem('m1c-webgl-fault', 'recovered'); window.__blockWebgl = false; });
  await page.reload();
  await page.waitForSelector('canvas');
  await page.getByLabel('Local session credential').fill(token);
  await page.getByRole('button', { name: 'Join fixture', exact: true }).click();
  await page.getByText('You are home', { exact: true }).waitFor();
  await page.waitForFunction(() => document.querySelector('.pitch')?.dataset.renderedRevision === '1');
  await page.getByRole('row').filter({ hasText: 'home-runner' }).filter({ hasText: '6, 7' }).waitFor();
  await page.screenshot({ path: resolve(output, 'webgl-recovered.png'), fullPage: true });
  assert.deepEqual(errors, []);
  results.push({ scenario: 'webgl-init-failure-and-recovery', fallback: 'WebGL error caught; DOM move accepted; reload restored rendered revision 1.', pageErrors: errors });
  await context.close();

  await writeFile(resolve(output, 'renderer-failure-demo.json'), JSON.stringify({ passed: true, browser: await browser.version(), results }, null, 2));
  console.log('PASS authenticated asset fallback and WebGL recovery demonstrations rendered accepted gameplay.');
} finally {
  await browser.close();
}

