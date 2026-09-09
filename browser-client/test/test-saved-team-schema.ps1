$ErrorActionPreference = 'Stop'
$schema = Join-Path $PSScriptRoot '..\saved-team.schema.json'
$draft = @{catalogVersion='retired';ruleset='BB2025';rosterId='human';presetId='old';captainId=$null;players=@();resources=@{rerolls=0;assistantCoaches=0;cheerleaders=0;apothecary=0;dedicatedFans=0}}
$document = @{formatVersion=1;teamId='12345678-1234-1234-1234-123456789abc';documentVersion=1;ruleset='BB2025';catalogVersion='retired';owner=@{namespace='local';subject='home'};draft=$draft;validation=@{valid=$true;total=0;budget=1150000;skillPoints=0;messages=@()}}
$valid = @{version=1;type='savedTeam';requestId='import-1';operation='import';document=$document} | ConvertTo-Json -Depth 10 -Compress
if (-not ($valid | Test-Json -SchemaFile $schema -ErrorAction SilentlyContinue)) { throw 'valid saved-team import rejected' }
$bad = @{version=1;type='savedTeam';requestId='import-1';operation='import';document=@{}} | ConvertTo-Json -Depth 10 -Compress
if ($bad | Test-Json -SchemaFile $schema -ErrorAction SilentlyContinue) { throw 'incomplete document accepted' }
$extra = @{version=1;type='savedTeam';requestId='import-1';operation='import';document=($document + @{extra='no'})} | ConvertTo-Json -Depth 10 -Compress
if ($extra | Test-Json -SchemaFile $schema -ErrorAction SilentlyContinue) { throw 'unknown document field accepted' }
Write-Output 'saved-team schema valid/invalid cases passed'
