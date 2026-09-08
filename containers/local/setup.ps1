#Requires -Version 5.1
$ErrorActionPreference = 'Stop'
$secretPath = Join-Path $PSScriptRoot '.secrets'
New-Item -ItemType Directory -Force $secretPath | Out-Null
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
$md5 = [Security.Cryptography.MD5]::Create()
try {
    foreach ($name in @('db_password', 'db_root_password', 'admin_password', 'coach_password', 'browser_home_token', 'browser_away_token')) {
        $path = Join-Path $secretPath $name
        if (Test-Path -LiteralPath $path) { continue }
        $bytes = New-Object byte[] 32
        $random.GetBytes($bytes)
        $value = if ($name -in @('admin_password', 'coach_password')) {
            ([BitConverter]::ToString($md5.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
        } else { [Convert]::ToBase64String($bytes) }
        [IO.File]::WriteAllText($path, $value, (New-Object Text.UTF8Encoding($false)))
    }
} finally { $random.Dispose(); $md5.Dispose() }
Write-Output 'Local secrets ready; existing secrets preserved.'
