// One-time, fail-closed schema-4 copy for the isolated R2 project. Never resets storage.
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const run = promisify(execFile);
const docker = process.env.DOCKER_EXE ?? (process.platform === 'win32' ? resolve(process.env.LOCALAPPDATA, 'Programs/DockerDesktop/resources/bin/docker.exe') : 'docker');
const options = { windowsHide: true, maxBuffer: 128 * 1024 * 1024 };
const evidence = resolve('.notes/overhaul-analysis/verification/r2/migration-format2');
const sql = (container, input) => new Promise((resolve, reject) => {
  const child = spawn(docker, ['exec', '-i', container, 'sh', '-c', 'exec mariadb -uroot -p"$(cat /run/secrets/db_root_password)" --batch --raw --skip-column-names ffb_local'], { windowsHide: true });
  const chunks = []; child.stdout.on('data', chunk => chunks.push(chunk)); child.stderr.on('data', () => {});
  child.on('error', reject); child.on('close', code => code === 0 ? resolve(Buffer.concat(chunks).toString().trim()) : reject(Error(`SQL exited ${code}`)));
  child.stdin.end(input);
});
const fingerprintSql = 'SELECT match_id,document_version,SHA2(document_json,256) FROM ffb_prepared_matches ORDER BY match_id; SELECT team_id,document_version,SHA2(document_json,256) FROM ffb_saved_teams ORDER BY team_id;';

if (process.argv[2] === 'verify') {
  const { readFile } = await import('node:fs/promises');
  const before = JSON.parse(await readFile(resolve(evidence, 'schema4-copy.json'), 'utf8'));
  assert.equal(await sql('ffb-local-r2b-database-1', 'SELECT version FROM ffb_local_schema;'), '5');
  assert.equal(await sql('ffb-local-r2b-database-1', fingerprintSql), before.documentFingerprints);
  assert.equal(await sql('ffb-local-r2b-database-1', 'SELECT COUNT(*) FROM ffb_match_recovery;'), '0');
  await writeFile(resolve(evidence, 'schema5-verified.json'), JSON.stringify({ pass: true, marker: 5, documentFingerprintsUnchanged: true, recoveryRows: 0, databaseVersion: await sql('ffb-local-r2b-database-1', 'SELECT VERSION();') }, null, 2), { flag: 'wx' });
  console.log('PASS schema 4 -> 5; frozen preparation and completed result bytes unchanged');
} else {
  assert.equal((await run(docker, ['volume', 'ls', '--filter', 'name=ffb-local-r2b', '--format', '{{.Name}}'], options)).stdout.trim(), '', 'R2 storage already exists; refusing to overwrite or recreate it');
  assert.equal(await sql('ffb-local-r1-database-1', 'SELECT version FROM ffb_local_schema;'), '4');
  const dump = (await run(docker, ['exec', 'ffb-local-r1-database-1', 'sh', '-c', 'exec mariadb-dump -uroot -p"$(cat /run/secrets/db_root_password)" --single-transaction --skip-lock-tables --skip-add-drop-table --skip-add-locks --hex-blob ffb_local'], options)).stdout;
  assert.ok(!/^\s*(DROP|TRUNCATE|DELETE)\b/im.test(dump), 'Unexpected destructive statement in snapshot');
  await writeFile(resolve('.tools/r2-schema4-format2-copy.sql'), dump, { flag: 'wx' });
  await run(docker, ['compose', '-f', 'containers/local/compose.r2.yaml', 'up', '-d', 'database'], options);
  for (let count = 0; count < 90; count++) {
    const state = JSON.parse((await run(docker, ['inspect', 'ffb-local-r2b-database-1'], options)).stdout)[0];
    if (state.State.Health?.Status === 'healthy') break;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  assert.equal(await sql('ffb-local-r2b-database-1', 'SHOW TABLES;'), '', 'Copy destination is not empty');
  await sql('ffb-local-r2b-database-1', dump);
  assert.equal(await sql('ffb-local-r2b-database-1', 'SELECT version FROM ffb_local_schema;'), '4');
  await mkdir(evidence, { recursive: true });
  await writeFile(resolve(evidence, 'schema4-copy.json'), JSON.stringify({ source: 'ffb-local-r1-database-1', destination: 'ffb-local-r2b-database-1', marker: 4,
    snapshotSha256: createHash('sha256').update(dump).digest('hex'), snapshotBytes: Buffer.byteLength(dump), documentFingerprints: await sql('ffb-local-r2b-database-1', fingerprintSql) }, null, 2), { flag: 'wx' });
  console.log('PASS isolated schema-4 copy loaded; source runtime and volumes unchanged');
}
