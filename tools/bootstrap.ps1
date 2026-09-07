#Requires -Version 5.1
[CmdletBinding()]
param(
    # Use an existing exact Temurin JDK (required on non-Windows hosts).
    [string]$JavaHome,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $root '.tools'
$manifest = Get-Content (Join-Path $PSScriptRoot 'build-toolchain.json') -Raw | ConvertFrom-Json
$windowsHost = [Environment]::OSVersion.Platform -eq 'Win32NT'
New-Item -ItemType Directory -Force -Path (Join-Path $toolRoot 'downloads') | Out-Null

function Install-Archive($Specification, $Algorithm, $Hash, $AuditArchive, $Directory) {
    $archive = Join-Path $toolRoot ('downloads/' + $Specification.archive)
    if (!(Test-Path -LiteralPath $archive)) {
        if (Test-Path -LiteralPath $AuditArchive) {
            Write-Host "Reusing audit archive: $AuditArchive"
            Copy-Item -LiteralPath $AuditArchive -Destination $archive
        } elseif ($Offline) {
            throw "Offline setup needs $archive. Run bootstrap once online or supply the pinned archive."
        } else {
            Write-Host "Downloading $($Specification.url)"
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -UseBasicParsing -Uri $Specification.url -OutFile $archive
        }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $Algorithm).Hash -ne $Hash) {
        throw "Checksum mismatch: $archive. Remove this archive and retry; nothing was extracted."
    }
    Write-Host "Verified $Algorithm for $($Specification.archive)"
    $destination = Join-Path $toolRoot $Directory
    $marker = Join-Path $destination '.bootstrap-complete'
    if (!(Test-Path -LiteralPath $marker)) {
        if (Test-Path -LiteralPath $destination) {
            throw "Incomplete tool directory: $destination. Move it aside and rerun bootstrap."
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot
        Set-Content -LiteralPath $marker -Value $Hash -Encoding Ascii
    }
}

$audit = Join-Path $root '.notes/overhaul-analysis/verification'
Install-Archive $manifest.maven 'SHA512' $manifest.maven.sha512 `
    (Join-Path $audit $manifest.maven.archive) $manifest.maven.directory

if (!$JavaHome) {
    if (!$windowsHost -or ![Environment]::Is64BitOperatingSystem) {
        throw 'Supply -JavaHome pointing to Temurin JDK 8u504-b01 for this platform. Automatic JDK setup supports Windows x64.'
    }
    Install-Archive $manifest.java.windowsX64 'SHA256' $manifest.java.windowsX64.sha256 `
        (Join-Path $audit 'java8.zip') $manifest.java.directory
    $JavaHome = Join-Path $toolRoot $manifest.java.directory
}
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
# Validate before saving configuration; the build also checks on every run.
. (Join-Path $PSScriptRoot 'check-java.ps1')
Assert-BaselineJava $JavaHome $manifest.java
Set-Content -LiteralPath (Join-Path $toolRoot 'java-home.txt') -Value $JavaHome -Encoding UTF8
Write-Host "Ready. Run ./tools/build.ps1 info, focused, install or verify."
