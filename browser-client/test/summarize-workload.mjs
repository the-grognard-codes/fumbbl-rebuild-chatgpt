import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const output = resolve(process.env.M1C_EVIDENCE ?? '../.notes/overhaul-analysis/verification/m1c/workload');
const report = JSON.parse(await readFile(resolve(output, 'workload.json'), 'utf8'));
assert.equal(report.passed, true);
assert.equal(report.samples.length, 1000);
const mib = bytes => Math.round(bytes / 1024 / 1024 * 100) / 100;
const memory = report.memory.map(sample => {
  const browserProcesses = Array.isArray(sample.browserProcessMemory) ? sample.browserProcessMemory : [sample.browserProcessMemory];
  const page = actor => sample.pages[actor].performance.find(metric => metric.name === 'JSHeapUsedSize').value;
  return { phase: sample.phase, jvmHeapMiB: mib(sample.operator.jvmHeapUsedBytes), jvmCommittedMiB: mib(sample.operator.jvmHeapCommittedBytes),
    jvmRssMiB: mib(Number(sample.server.procStatus.match(/VmRSS:\s+(\d+) kB/)[1]) * 1024), containerMemory: sample.server.container.MemUsage,
    browserWorkingSetSumMiB: mib(browserProcesses.reduce((sum, item) => sum + item.WorkingSet64, 0)), browserPrivateBytesSumMiB: mib(browserProcesses.reduce((sum, item) => sum + item.PrivateMemorySize64, 0)),
    homeJsHeapMiB: mib(page('home')), awayJsHeapMiB: mib(page('away')), homeDom: sample.pages.home.dom, awayDom: sample.pages.away.dom,
    jvmThreads: sample.operator.jvmThreads, transport: sample.operator.transport, historyEntries: sample.operator.adapter.historyEntries, authorizedConnections: sample.operator.adapter.authorizedConnections,
    jvmUptimeMs: sample.operator.jvmUptimeMs };
});
const summary = { machine: report.machine, image: report.image, ...report.summary, memory };
await writeFile(resolve(output, 'summary.json'), JSON.stringify(summary, null, 2));
const latencyRow = (label, metric) => `| ${label} | ${metric.count} | ${metric.p50.toFixed(2)} | ${metric.p95.toFixed(2)} | ${metric.max.toFixed(2)} |`;
const markdown = `# Measured workload results\n\n${summary.submissions} measured submissions: ${summary.mutations} engine mutations, ${summary.rejects} rejections, ${summary.duplicates} exact retries; ${summary.measuredLifetimes} measured fixtures and ${summary.reconnects} measured reconnects. Two warm-up lifetimes are excluded. Failures ${summary.failures}; timeouts ${summary.timeouts}.\n\n| Metric (ms) | Samples | p50 | p95 | Maximum |\n|---|---:|---:|---:|---:|\n${latencyRow('Accepted send → CPU authoritative render submission', summary.acceptedRenderMs)}\n${latencyRow('Accepted send → result receipt', summary.acceptedResultMs)}\n${latencyRow('Reconnect → full snapshot + new render submission', summary.reconnectRenderMs)}\n\nSnapshot payload bytes: n=${summary.snapshotBytes.count}, p50=${summary.snapshotBytes.p50}, p95=${summary.snapshotBytes.p95}, max=${summary.snapshotBytes.max}. Result payload bytes: n=${summary.resultBytes.count}, p50=${summary.resultBytes.p50}, p95=${summary.resultBytes.p95}, max=${summary.resultBytes.max}. UTF-8 JSON payload only; WebSocket/TCP overhead excluded.\n\n| Sample | JVM used heap MiB | JVM process RSS MiB | Container memory display | Chrome process working-set sum MiB | Chrome private-byte sum MiB | Home / away JS heap MiB |\n|---|---:|---:|---|---:|---:|---|\n${memory.map(m => `| ${m.phase} | ${m.jvmHeapMiB} | ${m.jvmRssMiB} | ${m.containerMemory} | ${m.browserWorkingSetSumMiB} | ${m.browserPrivateBytesSumMiB} | ${m.homeJsHeapMiB} / ${m.awayJsHeapMiB} |`).join('\n')}\n\nMemory sources are distinct. Summed process working sets can count shared pages more than once; private bytes are private committed memory, not private RSS. CDP page heaps exclude native/GPU allocations. Samples are collected serially while action submission is paused. Compare movement lifetime-25..100 for like-for-like growth; the final sample changes fixture type to Both Down and records retirement/idle without forced GC.\n\nMachine: ${summary.machine.cpu}, ${summary.machine.logicalCpus} logical CPUs, ${mib(summary.machine.ramBytes)} MiB RAM; ${summary.machine.platform} ${summary.machine.release}; Chrome ${summary.machine.browser}; Node ${summary.machine.node}. See the main report for pinned Java/Maven/container versions and limitations.\n\nRaw: [workload.json](workload.json). Recomputed compact data: [summary.json](summary.json).\n`;
await writeFile(resolve(output, 'measurements.md'), markdown);
console.log(JSON.stringify({ ...report.summary, memory }, null, 2));
