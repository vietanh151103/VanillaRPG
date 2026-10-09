$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$stamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backupDir = Join-Path $PSScriptRoot "backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$out = Join-Path $backupDir "VanillaRPG_$stamp.zip"

$include = @(
    "world",
    "world_nether",
    "world_the_end",
    "plugins",
    "server.properties",
    "ops.json",
    "whitelist.json",
    "banned-players.json",
    "banned-ips.json"
) | Where-Object { Test-Path $_ }

if ($include.Count -eq 0) {
    throw "Nothing to back up yet."
}

Write-Host "Stop the Minecraft server before making a consistent backup." -ForegroundColor Yellow
Compress-Archive -Path $include -DestinationPath $out -CompressionLevel Optimal
Write-Host "Backup created: $out" -ForegroundColor Green
