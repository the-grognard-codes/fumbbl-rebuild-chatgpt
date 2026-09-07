#Requires -Version 5.1
# Offline integration checks in an isolated throwaway project, including paths
# with spaces. Requires a completed bootstrap; adds no test-framework dependency.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$manifest = Get-Content (Join-Path $PSScriptRoot 'build-toolchain.json') -Raw | ConvertFrom-Json
$javaHome = (Get-Content (Join-Path $root '.tools/java-home.txt') -Raw).Trim()
$fixture = Join-Path $root ('.tools/tooling checks/' + [Guid]::NewGuid().ToString('N'))
$fixtureTools = Join-Path $fixture 'tools'
New-Item -ItemType Directory -Force -Path $fixtureTools, (Join-Path $fixture '.mvn') | Out-Null
foreach ($file in @('bootstrap.ps1', 'build.ps1', 'check-java.ps1', 'build-toolchain.json')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $fixtureTools
}
Copy-Item -LiteralPath (Join-Path $root '.mvn/settings.xml') -Destination (Join-Path $fixture '.mvn/settings.xml')
$shell = (Get-Process -Id $PID).Path
$bootstrap = Join-Path $fixtureTools 'bootstrap.ps1'
$build = Join-Path $fixtureTools 'build.ps1'
$checks = 0
function Assert-Result($Condition, $Message) {
    if (!$Condition) { throw $Message }
    Write-Host "PASS: $Message"
    $script:checks++
}
function Invoke-CheckProcess([string]$Script, [string[]]$Arguments, [string]$Log) {
    # Expected failures write native stderr. Windows PowerShell turns redirected
    # stderr into ErrorRecords; inspect the process exit code instead.
    $ErrorActionPreference = 'Continue'
    $PSNativeCommandUseErrorActionPreference = $false
    & $shell -NoProfile -File $Script @Arguments *> (Join-Path $fixture $Log)
    return $LASTEXITCODE
}

$checkExit = Invoke-CheckProcess $build @('info') 'missing-setup.log'
Assert-Result ($checkExit -ne 0) 'Build rejects missing setup'
$checkExit = Invoke-CheckProcess $bootstrap @('-JavaHome', $javaHome, '-Offline') 'offline-missing.log'
Assert-Result ($checkExit -ne 0) 'Offline bootstrap rejects a missing archive'
$archive = Join-Path $fixture ('.tools/downloads/' + $manifest.maven.archive)
Set-Content -LiteralPath $archive -Value 'invalid archive' -Encoding Ascii
$checkExit = Invoke-CheckProcess $bootstrap @('-JavaHome', $javaHome, '-Offline') 'bad-hash.log'
Assert-Result ($checkExit -ne 0 -and !(Test-Path (Join-Path $fixture ('.tools/' + $manifest.maven.directory)))) 'Checksum failure prevents extraction'
Copy-Item -LiteralPath (Join-Path $root ('.tools/downloads/' + $manifest.maven.archive)) -Destination $archive -Force
$checkExit = Invoke-CheckProcess $bootstrap @('-JavaHome', $javaHome, '-Offline') 'bootstrap.log'
Assert-Result ($checkExit -eq 0) 'Offline setup accepts the pinned archive and existing JDK in a path with spaces'
$checkExit = Invoke-CheckProcess $bootstrap @('-JavaHome', $javaHome, '-Offline') 'bootstrap-repeat.log'
Assert-Result ($checkExit -eq 0) 'Repeated setup succeeds'

$savedJava = $env:JAVA_HOME
$savedArguments = $env:MAVEN_ARGS
$savedLocation = (Get-Location).Path
try {
    $env:JAVA_HOME = 'intentionally invalid ambient JAVA_HOME'
    $env:MAVEN_ARGS = 'intentionally invalid ambient Maven arguments'
    & $build info *> (Join-Path $fixture 'info.log')
    Assert-Result ($LASTEXITCODE -eq 0) 'Build uses configured tools instead of ambient Java and Maven arguments'
    Assert-Result ($env:JAVA_HOME -eq 'intentionally invalid ambient JAVA_HOME' -and
        $env:MAVEN_ARGS -eq 'intentionally invalid ambient Maven arguments' -and
        (Get-Location).Path -eq $savedLocation) 'Build restores caller environment and working directory'
} finally {
    $env:JAVA_HOME = $savedJava
    $env:MAVEN_ARGS = $savedArguments
}

. (Join-Path $PSScriptRoot 'check-java.ps1')
$wrongVersion = [PSCustomObject]@{ runtimeVersion = '21.0.11+10-LTS'; vendor = $manifest.java.vendor }
$rejected = $false
try { Assert-BaselineJava $javaHome $wrongVersion } catch { $rejected = $true }
Assert-Result $rejected 'JDK version mismatch is rejected'
# A malformed project exercises Maven failure propagation without fetching dependencies.
Set-Content -LiteralPath (Join-Path $fixture 'pom.xml') -Value '<invalid' -Encoding Ascii
$checkExit = Invoke-CheckProcess $build @('verify', '-Offline') 'failed-build.log'
Assert-Result ($checkExit -ne 0) 'Maven failure reaches the caller as a nonzero exit code'
Write-Host "$checks tooling checks passed. Diagnostic files: $fixture"
exit 0
