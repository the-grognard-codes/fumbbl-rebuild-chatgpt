import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Run `npm run dev` first. This delays renderer initialization, destroys the
// board before it resolves, then proves the late-created renderer is disposed.
const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL ?? 'chrome', headless: true });
try {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('http://127.0.0.1:5173/');
  const result = await page.evaluate(async () => {
    const { BoardView } = await import('/src/board.ts');
    const host = document.createElement('div');
    document.body.appendChild(host);
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const fakeApplication = {
      renderer: null,
      init: async () => gate,
      destroyCalls: 0,
      destroy() { this.destroyCalls++; }
    };
    const board = new BoardView('none', () => fakeApplication);
    const mounting = board.mount(host, () => undefined, () => undefined);
    board.destroy();
    fakeApplication.renderer = {};
    release();
    let rejected = false;
    try { await mounting; } catch { rejected = true; }
    return { rejected, destroyCalls: fakeApplication.destroyCalls, hostChildren: host.childElementCount };
  });
  assert.deepEqual(result, { rejected: true, destroyCalls: 1, hostChildren: 0 });
  assert.deepEqual(errors, []);
  console.log('PASS delayed renderer initialization is disposed after early board teardown.');
} finally {
  await browser.close();
}
