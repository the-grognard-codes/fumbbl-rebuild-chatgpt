# Run from browser-client after final server image startup and Vite readiness.
param([switch]$SkipWorkload)
$ErrorActionPreference = 'Stop'
$evidence = '../.notes/overhaul-analysis/verification/m1c'
function Invoke-CheckedNode([string[]]$Arguments, [string]$Log) {
    & node @Arguments *> "$evidence/$Log"
    if ($LASTEXITCODE -ne 0) { throw "Failed: node $Arguments; see $Log" }
}
Invoke-CheckedNode @('test/m1c-local.mjs', 'reset', 'MOVEMENT') 'movement-reset.log'
$env:M1A_EVIDENCE = "$evidence/movement"
Invoke-CheckedNode @('test/browser-demo.mjs') 'movement-demo.log'
foreach ($fixture in @('BOTH_DOWN', 'BOTH_DOWN_BLOCK', 'BOTH_DOWN_AWAY', 'BOTH_DOWN_AWAY_BLOCK')) {
    Invoke-CheckedNode @('test/m1c-local.mjs', 'reset', $fixture) "reset-$fixture.log"
    $env:M1B_FIXTURE = $fixture
    $env:M1B_EVIDENCE = "$evidence/$fixture"
    Invoke-CheckedNode @('test/choice-demo.mjs') "choice-$fixture.log"
}
Remove-Item Env:M1C_EVIDENCE -ErrorAction SilentlyContinue
Invoke-CheckedNode @('test/renderer-failure-demo.mjs') 'renderer-failure-demo.log'
Invoke-CheckedNode @('test/renderer-lifecycle-demo.mjs') 'renderer-lifecycle-demo.log'
Invoke-CheckedNode @('test/robustness-demo.mjs') 'robustness-demo.log'
if (!$SkipWorkload) { Invoke-CheckedNode @('test/workload-demo.mjs') 'workload-demo.log' }
