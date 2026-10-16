param(
    [string]$OutputPath = 'server-template\resourcepacks\VanillaRPG-Pack-v1.8.zip'
)

$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$staging = Join-Path ([System.IO.Path]::GetTempPath()) ('vanillarpg-rp-' + [guid]::NewGuid().ToString('N'))
$basePack = Join-Path $workspace 'server-template\resourcepacks\BladesOfMajestica-PokerPack-v1.3.zip'
$eliteSources = Join-Path $workspace 'server-template\plugins\EliteMobs\resource_pack'
$overrides = Join-Path $workspace 'resourcepack-overrides'

try {
    New-Item -ItemType Directory -Path $staging | Out-Null
    Expand-Archive -LiteralPath $basePack -DestinationPath $staging

    # Include EliteMobs models/textures so custom-model-data icons resolve,
    # but never include minecraft:default. EliteMobs' font is a full GUI
    # compositor and changes menu layout when placed in the server pack.
    Get-ChildItem -LiteralPath $eliteSources -Directory | ForEach-Object {
        Copy-Item -LiteralPath (Join-Path $_.FullName 'assets') -Destination $staging -Recurse -Force
    }
    Copy-Item -LiteralPath (Join-Path $overrides 'assets') -Destination $staging -Recurse -Force
    $eliteFontOverride = Join-Path $staging 'assets\minecraft\font\default.json'
    if (Test-Path -LiteralPath $eliteFontOverride) { Remove-Item -LiteralPath $eliteFontOverride -Force }

    $metadata = @'
{
  "pack": {
    "description": "VanillaRPG Pack - Majestica, Poker and EliteMobs models",
    "supported_formats": [63, 88],
    "pack_format": 88,
    "min_format": 63,
    "max_format": 88
  }
}
'@
    [System.IO.File]::WriteAllText((Join-Path $staging 'pack.mcmeta'), $metadata, [System.Text.UTF8Encoding]::new($false))

    $output = if ([System.IO.Path]::IsPathRooted($OutputPath)) {
        [System.IO.Path]::GetFullPath($OutputPath)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $workspace $OutputPath))
    }
    $outputDirectory = Split-Path -Parent $output
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Force }

    Add-Type -AssemblyName System.IO.Compression
    $zip = [System.IO.Compression.ZipFile]::Open($output, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem -LiteralPath $staging -Recurse -File | ForEach-Object {
            $relative = $_.FullName.Substring($staging.Length).TrimStart('\', '/') -replace '\\', '/'
            $entry = $zip.CreateEntry($relative, [System.IO.Compression.CompressionLevel]::Optimal)
            $input = [System.IO.File]::OpenRead($_.FullName)
            $stream = $entry.Open()
            try { $input.CopyTo($stream) }
            finally { $stream.Dispose(); $input.Dispose() }
        }
    }
    finally { $zip.Dispose() }

    $hash = (Get-FileHash -LiteralPath $output -Algorithm SHA1).Hash
    Write-Output "Built: $output"
    Write-Output "SHA1: $hash"
}
finally {
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
}
