// Test-only routing: the product endpoint and the retained Java 8 JVM stay intact.
export async function selectLocalRuntime(page) {
  const endpoint = process.env.M4_WS_ENDPOINT ?? 'ws://127.0.0.1:22227/browser/v1';
  if (!['ws://127.0.0.1:22227/browser/v1', 'ws://127.0.0.1:22228/browser/v1', 'ws://127.0.0.1:22229/browser/v1', 'ws://127.0.0.1:22230/browser/v1'].includes(endpoint)) {
    throw new Error('Runtime comparisons require a declared loopback endpoint');
  }
  await page.addInitScript(endpoint => {
    const Native = window.WebSocket;
    window.WebSocket = class extends Native {
      constructor(url, protocols) {
        super(url === 'ws://127.0.0.1:22227/browser/v1' ? endpoint : url, protocols);
      }
    };
  }, endpoint);
}
