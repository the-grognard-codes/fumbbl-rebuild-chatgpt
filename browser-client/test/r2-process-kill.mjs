import assert from 'node:assert/strict';
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';

const run = promisify(execFile);
const docker = process.env.DOCKER_EXE ?? (process.platform === 'win32' ? resolve(process.env.LOCALAPPDATA, 'Programs/DockerDesktop/resources/bin/docker.exe') : 'docker');
const server = 'ffb-local-r2b-server-1';
const database = 'ffb-local-r2b-database-1';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const hash = text => createHash('sha256').update(text).digest('hex');

async function sql(query) {
  return new Promise((resolve, reject) => {
    const child = spawn(docker, ['exec', '-i', database, 'sh', '-c', 'exec mariadb -uroot -p"$(cat /run/secrets/db_root_password)" --batch --raw --skip-column-names ffb_local'], { windowsHide: true });
    const chunks = [], errors = [];
    child.stdout.on('data', chunk => chunks.push(chunk)); child.stderr.on('data', chunk => errors.push(chunk));
    child.on('error', reject);
    child.on('close', code => code === 0 ? resolve(Buffer.concat(chunks).toString().trim()) : reject(Error(`SQL failed (${code}): ${Buffer.concat(errors)}`)));
    child.stdin.end(query);
  });
}
export { sql as r2Sql };

export function processKillChecks({ pages, out, matchId, connect, state, raw }) {
  const done = new Set(), results = [];
  let previous;
  const controller = {
    async beforeAction(view, action) {
      if (view.half !== 2 || view.homeTurn !== 8 || view.awayTurn !== 8 || action.kind !== 'endTurn' || done.has('terminal-pending')) return;
      const id = matchId(); assert.match(id, /^[0-9a-f-]{36}$/);
      // Hold only this synthetic match's result row. The separate checkpoint transaction
      // can commit; the terminal result UPDATE then waits, exposing the crash window.
      const child = spawn(docker, ['exec', '-i', database, 'sh', '-c', 'exec mariadb -uroot -p"$(cat /run/secrets/db_root_password)" --batch --raw --skip-column-names --unbuffered ffb_local'], { windowsHide: true });
      const connectionId = await new Promise((resolve, reject) => {
        let output = '';
        child.stdout.on('data', chunk => { output += chunk; const found = /R2_LOCK:(\d+)/.exec(output); if (found) resolve(Number(found[1])); });
        child.on('error', reject); child.stderr.on('data', () => {});
        child.stdin.end(`START TRANSACTION; SELECT match_id FROM ffb_prepared_matches WHERE match_id='${id}' FOR UPDATE; SELECT CONCAT('R2_LOCK:', CONNECTION_ID()); DO SLEEP(90); ROLLBACK;`);
      });
      controller.pending = (async () => {
        try {
          for (let attempt = 0; attempt < 150; attempt++) {
            const checkpoint = JSON.parse(await sql(`SELECT artifact_json FROM ffb_match_recovery WHERE matchid='${id}';`));
            if (checkpoint.payload.pendingTerminal) {
              await controller.inspect(checkpoint.payload.homeView, { lostAcknowledgementAtProcessKill: true, terminalResultRowLocked: true },
                { point: 'terminal-pending', views: [checkpoint.payload.homeView, checkpoint.payload.awayView], release: () => sql(`KILL ${connectionId};`) });
              return;
            }
            await delay(200);
          }
          throw Error('Terminal checkpoint did not reach the locked result boundary');
        } finally { await sql(`KILL ${connectionId};`).catch(() => {}); }
      })();
      // Attach immediately so an assertion failure cannot become an unhandled rejection.
      controller.pending.catch(() => {});
    },
    async inspect(view, extra = {}, forced = {}) {
      const prior = previous; previous = view;
      const active = view.players.find(player => player.id === view.activePlayerId);
      const point = forced.point ?? (view.revision === 0 ? 'pre-match'
        : view.phase === 'SETUP' && view.revision === 3 ? 'placement'
        : active && view.actions.some(action => action.actor !== active.role && ['blockDie', 'apothecary', 'skill', 'interception'].includes(action.kind)) ? 'defending-team-decision'
        : prior && view.half !== prior.half ? 'half-transition'
        : prior && view.drive !== prior.drive ? 'drive-transition'
        : view.phase === 'FULL_TIME' ? 'terminal-committed' : null);
      if (!point || done.has(point)) return;
      done.add(point);
      const id = matchId(); assert.match(id, /^[0-9a-f-]{36}$/);
      if (!forced.views) await Promise.all(pages.map(page => page.waitForFunction(revision => window.__m3b.incoming.filter(value => value.state).at(-1)?.state.revision === revision, view.revision)));
      const views = forced.views ?? await Promise.all(pages.map(state));
      const traces = await Promise.all(pages.map(page => page.evaluate(() => ({ incoming: window.__m3b.incoming, outgoing: window.__m3b.outgoing }))));
      const candidates = traces.flatMap((trace, index) => trace.outgoing.filter(request => request.type === 'setup' && request.operation !== 'load' && request.expectedRevision === view.revision - 1).map(request => ({ request, index })));
      const last = candidates.at(-1);
      const beforeJson = await sql(`SELECT artifact_json FROM ffb_match_recovery WHERE matchid='${id}';`);
      const before = JSON.parse(beforeJson);
      const beforeProcess = JSON.parse((await run(docker, ['inspect', server], { windowsHide: true })).stdout)[0];
      assert.equal(beforeProcess.Config.Image, 'ffb-server:3.4.0-r2.2');
      await run(docker, ['kill', '--signal', 'KILL', server], { windowsHide: true });
      const killed = JSON.parse((await run(docker, ['inspect', server], { windowsHide: true })).stdout)[0];
      assert.equal(killed.State.ExitCode, 137); assert.equal(killed.State.OOMKilled, false);
      await forced.release?.();
      await run(docker, ['start', server], { windowsHide: true });
      let afterProcess;
      for (let attempt = 0; attempt < 90; attempt++) {
        afterProcess = JSON.parse((await run(docker, ['inspect', server], { windowsHide: true })).stdout)[0];
        if (afterProcess.State.Health?.Status === 'healthy') break;
        await delay(1000);
      }
      assert.equal(afterProcess.State.Health.Status, 'healthy');
      assert.equal(afterProcess.Image, beforeProcess.Image, 'restart uses identical runtime image');
      assert.notEqual(afterProcess.State.StartedAt, beforeProcess.State.StartedAt);
      await Promise.all(pages.map((page, index) => connect(page, index, `/setup?matchId=${id}`)));
      for (let index = 0; index < 2; index++) {
        await pages[index].getByTestId('setup-status').waitFor();
        assert.deepEqual(await state(pages[index]), views[index], `${point}: restored participant ${index}`);
      }
      if (last) {
        const reply = await raw(pages[last.index], last.request);
        assert.equal(reply.code, 'ACCEPTED'); assert.equal(reply.duplicate, true);
        assert.deepEqual(reply.state, views[last.index]);
      }
      const afterJson = await sql(`SELECT artifact_json FROM ffb_match_recovery WHERE matchid='${id}';`);
      assert.equal(afterJson, beforeJson, 'load/retry cannot consume dice or modify checkpoint');
      const inspected = structuredClone(before);
      inspected.payload.dice.seed = '[private server seed redacted]';
      const result = { point, matchId: id, revision: view.revision, ...extra, exitCode: killed.State.ExitCode,
        image: afterProcess.Image, previousStartedAt: beforeProcess.State.StartedAt, restartedAt: afterProcess.State.StartedAt,
        checkpointSha256: hash(beforeJson), nativeSha256: hash(JSON.stringify(before.payload.native)),
        diceCounter: before.payload.dice.counter, acceptedRequests: before.payload.history.length,
        twoClientExact: true, exactRetry: Boolean(last), checkpointUnchanged: true };
      results.push(result);
      await writeFile(resolve(out, `kill-${point}.json`), JSON.stringify({ result, beforeViews: views, inspectedArtifact: inspected, traces }, null, 2));
      await writeFile(resolve(out, 'process-kills.json'), JSON.stringify(results, null, 2));
      console.log(JSON.stringify(result));
    },
    results
  };
  return controller;
}
