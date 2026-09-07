#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('info', 'focused', 'test', 'install', 'verify')]
    [string]$Task = 'verify',
    [string]$Test,
    [string]$Module,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $root '.tools'
$manifest = Get-Content (Join-Path $PSScriptRoot 'build-toolchain.json') -Raw | ConvertFrom-Json
$configuration = Join-Path $toolRoot 'java-home.txt'
if (!(Test-Path -LiteralPath $configuration)) { throw 'Run tools/bootstrap.ps1 first.' }
$baselineJava = (Get-Content -LiteralPath $configuration -Raw).Trim()
. (Join-Path $PSScriptRoot 'check-java.ps1')
Assert-BaselineJava $baselineJava $manifest.java
if (($Test -or $Module) -and $Task -ne 'test') { throw '-Test and -Module are only supported with the test task.' }
$windowsHost = [Environment]::OSVersion.Platform -eq 'Win32NT'
$launcher = if ($windowsHost) { 'bin/mvn.cmd' } else { 'bin/mvn' }
$maven = Join-Path (Join-Path $toolRoot $manifest.maven.directory) $launcher
if (!(Test-Path -LiteralPath $maven)) { throw 'Maven is missing. Run tools/bootstrap.ps1.' }
$settings = Join-Path $root '.mvn/settings.xml'
$mavenArguments = @('--batch-mode', '--no-transfer-progress', '--show-version',
    '--settings', $settings, '--global-settings', $settings,
    "-Dmaven.repo.local=$(Join-Path $toolRoot 'repository')")
if ($Offline) { $mavenArguments += '--offline' }
switch ($Task) {
    'info' { $mavenArguments += '--version' }
    'focused' {
        $mavenArguments += @('-pl', 'ffb-statetest', '-am',
            '-Dtest=BlockTest,ShadowingTest,HandOffTurnoverTest,SafePassTest,SwarmingEndTurnTest,RulesTest,ReRollApiEquivalenceTest,SessionTimeoutTaskTest',
            '-Dsurefire.failIfNoSpecifiedTests=false', 'test')
    }
    'test' {
        if ($Module) { $mavenArguments += @('-pl', $Module, '-am') }
        if ($Test) { $mavenArguments += @("-Dtest=$Test", '-Dsurefire.failIfNoSpecifiedTests=false') }
        $mavenArguments += 'test'
    }
    'install' { $mavenArguments += @('clean', 'install') }
    'verify' { $mavenArguments += @('clean', 'verify') }
}

# All environment changes are scoped to this invocation, including when called
# from an interactive PowerShell session. Ignore host Maven startup customizations.
$environmentNames = @('JAVA_HOME', 'MAVEN_SKIP_RC', 'MAVEN_ARGS', 'MAVEN_OPTS')
$previous = @{}
foreach ($name in $environmentNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $root
try {
    $env:JAVA_HOME = $baselineJava
    $env:MAVEN_SKIP_RC = 'true'
    $env:MAVEN_ARGS = ''
    $env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
    if ($windowsHost) { & $maven @mavenArguments }
    else { & sh $maven @mavenArguments }
    $result = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
exit $result
