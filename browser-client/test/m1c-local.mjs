import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
const exec = promisify(execFile);
const docker = process.env.DOCKER_EXE ?? resolve(process.env.LOCALAPPDATA, 'Programs/DockerDesktop/resources/bin/docker.exe');
const compose = ['compose', '-f', resolve('../containers/local/compose.yaml')];
export async function dockerCall(args) {
  return (await exec(docker, args, { timeout: 20000, maxBuffer: 1024 * 1024 })).stdout.trim();
}
export async function control(operation, fixture) {
  const id = randomUUID();
  await new Promise((resolveDone, reject) => {
    const child = spawn(docker, [...compose, 'exec', '-T', 'server', 'sh', '-c',
      'cat > /tmp/ffb-browser-control.input && mv /tmp/ffb-browser-control.input /tmp/ffb-browser-control.request'], { stdio: ['pipe', 'pipe', 'pipe'] });
    const timer = setTimeout(() => { child.kill(); reject(Error('Operator write timeout')); }, 20000);
    child.on('error', reject);
    child.on('close', code => { clearTimeout(timer); code === 0 ? resolveDone() : reject(Error(`Operator write exit ${code}`)); });
    child.stdin.end(JSON.stringify({ id, operation, fixture }));
  });
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    try {
      const result = JSON.parse(await dockerCall([...compose, 'exec', '-T', 'server', 'cat', '/tmp/ffb-browser-control.response']));
      if (result.id === id) { if (!result.ok) throw Error(result.error); return result; }
    } catch (error) { if (error.message === 'Operator command failed') throw error; }
    await new Promise(done => setTimeout(done, 100));
  }
  throw Error('Operator response timeout');
}
export async function serverMemory() {
  return {
    procStatus: await dockerCall([...compose, 'exec', '-T', 'server', 'cat', '/proc/1/status']),
    container: JSON.parse(await dockerCall(['stats', '--no-stream', '--format', '{{json .}}', 'ffb-local-m0b-server-1']))
  };
}
if (process.argv[1]?.endsWith('m1c-local.mjs')) console.log(JSON.stringify(await control(process.argv[2] ?? 'metrics', process.argv[3]), null, 2));
