param(
    [string]$Version = '0.7.9'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$repoRoot = Split-Path -Parent $PSScriptRoot
$serverRoot = Join-Path $repoRoot 'server-template'
$releaseDir = Join-Path $repoRoot 'releases'
$archive = Join-Path $releaseDir "VanillaRPG-$Version-Clean-Server.zip"

if (-not (Test-Path -LiteralPath $serverRoot)) {
    throw "Missing server template: $serverRoot"
}
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}

# Never distribute live server/player state. Keep plugin configuration, content
# and JARs needed for a first launch, but start every downloader with fresh data.
$excludedRoots = @(
    '.paper', 'backups', 'cache', 'libraries', 'logs', 'runtime', 'versions',
    'vanillarpg_terraform_test', 'world', 'world_nether', 'world_the_end'
)
$excludedFiles = @(
    'ops.json', 'paper.jar', 'usercache.json', 'wepif.yml',
    'plugins/VanillaRPG-Core-0.7.8.jar',
    'plugins/VanillaRPG-Core/rpg.db',
    'plugins/VanillaRPG-Core/rpg.db-shm',
    'plugins/VanillaRPG-Core/rpg.db-wal',
    'plugins/EliteMobs/data/player_data.db'
)
$excludedPrefixes = @(
    'plugins/SkinsRestorer/cache/',
    'plugins/SkinsRestorer/skins/'
)

$zip = [System.IO.Compression.ZipFile]::Open($archive, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -LiteralPath $serverRoot -File -Recurse -Force | ForEach-Object {
        $relative = $_.FullName.Substring($serverRoot.Length + 1).Replace('\', '/')
        $firstPart = $relative.Split('/')[0]
        if ($excludedRoots -contains $firstPart) { return }
        if ($excludedFiles -contains $relative) { return }
        if ($excludedPrefixes.Where({ $relative.StartsWith($_, [System.StringComparison]::OrdinalIgnoreCase) }).Count -gt 0) { return }
        if ($relative.EndsWith('.log', [System.StringComparison]::OrdinalIgnoreCase)) { return }

        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $_.FullName, "VanillaRPG-$Version/$relative",
            [System.IO.Compression.CompressionLevel]::Optimal
        ) | Out-Null
    }
}
finally {
    $zip.Dispose()
}

Write-Output "Created clean server release: $archive"
