#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('info', 'test', 'install', 'verify')]
    [string]$Task = 'verify',
    # An exact Temurin 21.0.11+10 JDK. The documented Windows installation is
    # selected when this parameter is omitted.
    [string]$JavaHome,
    [string]$Test,
    [string]$Module,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $root '.tools'
$manifest = Get-Content (Join-Path $PSScriptRoot 'target-build-toolchain.json') -Raw | ConvertFrom-Json
$windowsHost = [Environment]::OSVersion.Platform -eq 'Win32NT'
$suffix = if ($windowsHost) { '.exe' } else { '' }

if (!$JavaHome) {
    if (!$windowsHost) {
        throw 'Supply -JavaHome pointing to Temurin JDK 21.0.11+10-LTS on this platform.'
    }
    $JavaHome = $manifest.java.defaultWindowsHome
}
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
foreach ($command in @('java', 'javac')) {
    if (!(Test-Path -LiteralPath (Join-Path $JavaHome "bin/$command$suffix"))) {
        throw "Missing $command in JDK: $JavaHome. Supply an exact Temurin JDK 21.0.11+10-LTS."
    }
}

function Get-JavaProperties([string]$JavaExecutable) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $JavaExecutable
    $start.Arguments = '-XshowSettings:properties -version'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($start)
    $details = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $exitCode = $process.ExitCode
    $process.Dispose()
    if ($exitCode -ne 0) { throw "Unable to inspect JDK: $JavaExecutable" }
    return $details
}

$javaDetails = Get-JavaProperties (Join-Path $JavaHome "bin/java$suffix")
$runtime = [regex]::Escape($manifest.java.runtimeVersion)
$vendor = [regex]::Escape($manifest.java.vendor)
if ($javaDetails -notmatch "java.runtime.version = $runtime(\r?\n)" -or
    $javaDetails -notmatch "java.vendor = $vendor(\r?\n)") {
    throw "Target requires $($manifest.java.vendor) JDK $($manifest.java.runtimeVersion); rejected $JavaHome."
}

$launcher = if ($windowsHost) { 'bin/mvn.cmd' } else { 'bin/mvn' }
$maven = Join-Path (Join-Path $toolRoot $manifest.maven.directory) $launcher
if (!(Test-Path -LiteralPath $maven)) {
    throw "Maven $($manifest.maven.version) is missing. Run tools/bootstrap.ps1 first."
}
$settings = Join-Path $root '.mvn/settings.xml'
$mavenArguments = @('--batch-mode', '--no-transfer-progress', '--show-version',
    '--settings', $settings, '--global-settings', $settings,
    "-Dmaven.repo.local=$(Join-Path $toolRoot $manifest.repositoryDirectory)",
    "-P$($manifest.mavenProfile)")
if ($Offline) { $mavenArguments += '--offline' }
if (($Test -or $Module) -and $Task -ne 'test') { throw '-Test and -Module are only supported with the test task.' }
switch ($Task) {
    'info' { $mavenArguments += '--version' }
    'test' {
        if ($Module) { $mavenArguments += @('-pl', $Module, '-am') }
        if ($Test) { $mavenArguments += @("-Dtest=$Test", '-Dsurefire.failIfNoSpecifiedTests=false') }
        $mavenArguments += 'test'
    }
    'install' { $mavenArguments += @('clean', 'install') }
    'verify' { $mavenArguments += @('clean', 'verify') }
}

# Scope every Maven/JDK setting to this child process invocation and restore the
# caller exactly, including a previously unset variable.
$environmentNames = @('JAVA_HOME', 'MAVEN_SKIP_RC', 'MAVEN_ARGS', 'MAVEN_OPTS')
$previous = @{}
foreach ($name in $environmentNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$result = 1
Push-Location $root
try {
    $env:JAVA_HOME = $JavaHome
    $env:MAVEN_SKIP_RC = 'true'
    $env:MAVEN_ARGS = ''
    $env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
    $mavenIdentity = if ($windowsHost) { (& $maven --version 2>&1 | Out-String) } else { (& sh $maven --version 2>&1 | Out-String) }
    if ($LASTEXITCODE -ne 0 -or $mavenIdentity -notmatch "Apache Maven $([regex]::Escape($manifest.maven.version))") {
        throw "Target requires project-local Apache Maven $($manifest.maven.version); rejected $maven."
    }
    if ($windowsHost) { & $maven @mavenArguments }
    else { & sh $maven @mavenArguments }
    $result = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
exit $result
