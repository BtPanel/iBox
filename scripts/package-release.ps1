param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$Label
)

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseRoot = Join-Path $sourceRoot 'Release'
$archivePath = Join-Path $releaseRoot "iBox-$Label-source.zip"
$stageRoot = Join-Path ([IO.Path]::GetTempPath()) ("iBox-package-" + [Guid]::NewGuid().ToString('N'))

if (Test-Path -LiteralPath $archivePath) {
    throw "Archive already exists: $archivePath"
}

$apks = @(Get-ChildItem (Join-Path $releaseRoot 'apk') -Filter '*.apk' -File)
$checksumPath = Join-Path $releaseRoot 'SHA256SUMS.txt'
if ($apks.Count -ne 4 -or -not (Test-Path $checksumPath)) {
    throw 'Expected four Release APKs and SHA256SUMS.txt before packaging.'
}
$checksums = @{}
foreach ($line in (Get-Content $checksumPath)) {
    if ($line -notmatch '^([0-9a-fA-F]{64})  apk/([^/\\]+\.apk)$') {
        throw "Invalid checksum entry: $line"
    }
    $checksums[$Matches[2]] = $Matches[1]
}
if ($checksums.Count -ne 4) { throw 'Expected four distinct APK checksums.' }
foreach ($apk in $apks) {
    if (-not $checksums.ContainsKey($apk.Name) -or
        (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash -ne $checksums[$apk.Name]) {
        throw "Missing or stale checksum: $($apk.Name)"
    }
}
if (-not (Test-Path (Join-Path $sourceRoot 'vendor/media3/settings.gradle.kts'))) {
    throw 'vendor/media3 source is missing.'
}

New-Item -ItemType Directory -Path $stageRoot | Out-Null
try {
    $tracked = @(git -C $sourceRoot -c core.quotePath=false ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'git ls-files failed.' }
    foreach ($relative in $tracked) {
        $from = Join-Path $sourceRoot $relative
        if (-not (Test-Path -LiteralPath $from -PathType Leaf)) {
            throw "Tracked file is missing: $relative"
        }
        $to = Join-Path $stageRoot $relative
        $parent = Split-Path -Parent $to
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        Copy-Item -LiteralPath $from -Destination $to
    }

    $vendorTarget = Join-Path $stageRoot 'vendor/media3'
    robocopy (Join-Path $sourceRoot 'vendor/media3') $vendorTarget /E /R:1 /W:1 `
        /XD .git .gradle .kotlin .idea build buildout out `
        /XF local.properties *.jks *.keystore *.p12 *.pem *.key *.log *.tmp | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Media3 source copy failed: $LASTEXITCODE" }

    $stageRelease = Join-Path $stageRoot 'Release'
    $stageApk = Join-Path $stageRelease 'apk'
    New-Item -ItemType Directory -Path $stageApk -Force | Out-Null
    foreach ($apk in $apks) { Copy-Item -LiteralPath $apk.FullName -Destination $stageApk }
    Copy-Item -LiteralPath $checksumPath -Destination $stageRelease

    $commit = git -C $sourceRoot rev-parse HEAD
    $changes = @(git -C $sourceRoot status --short)
    @(
        "Label: $Label"
        "Git HEAD: $commit"
        "Packaged at: $(Get-Date -Format o)"
        'Working-tree changes at packaging:'
        $(if ($changes.Count) { $changes } else { '(none)' })
        'Media3: local source snapshot (build outputs excluded)'
        'APK checksums: Release/SHA256SUMS.txt'
    ) | Set-Content -LiteralPath (Join-Path $stageRoot 'SOURCE-MANIFEST.txt') -Encoding UTF8

    Compress-Archive -Path (Join-Path $stageRoot '*') -DestinationPath $archivePath -CompressionLevel Optimal
    Write-Output $archivePath
}
finally {
    if (Test-Path -LiteralPath $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}
