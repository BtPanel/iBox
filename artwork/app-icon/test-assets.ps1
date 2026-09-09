$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$errors = [System.Collections.Generic.List[string]]::new()

$required = @(
    'artwork/app-icon/ibox-app-icon.svg',
    'artwork/app-icon/ibox-mark.svg',
    'artwork/app-icon/ibox-monochrome.svg',
    'artwork/app-icon/ibox-app-icon-1024.png',
    'artwork/app-icon/ibox-foreground-1024.png',
    'artwork/app-icon/ibox-monochrome-1024.png',
    'artwork/app-icon/ibox-play-store-512.png',
    'artwork/app-icon/ibox-tv-banner-320x180.png',
    'artwork/app-icon/ibox-favicon.ico',
    'artwork/app-icon/preview.png',
    'artwork/app-icon/export-manifest.json'
)
foreach ($relative in $required) {
    if (-not (Test-Path (Join-Path $root $relative))) { $errors.Add("missing $relative") }
}

$masterPath = Join-Path $root 'artwork/app-icon/ibox-app-icon.svg'
if (Test-Path $masterPath) {
    $master = Get-Content -Raw $masterPath
    [xml]$master | Out-Null
    $colors = [regex]::Matches($master, '#[0-9A-Fa-f]{6}') | ForEach-Object { $_.Value.ToUpperInvariant() } | Sort-Object -Unique
    if ($colors.Count -ne 3) { $errors.Add("master must contain exactly 3 colors, found $($colors.Count)") }
    if ($master -match '<linearGradient|<radialGradient|<filter') { $errors.Add('master must not contain gradients or filters') }
}
$brandSvg = Join-Path $root 'other/image/logo-1.svg'
$monoSvg = Join-Path $root 'other/image/logo-2.svg'
if (Test-Path $brandSvg) {
    $brandColors = [regex]::Matches((Get-Content -Raw $brandSvg), '#[0-9A-Fa-f]{6}') | ForEach-Object { $_.Value.ToUpperInvariant() } | Sort-Object -Unique
    if (($brandColors -join ',') -ne '#B93F3B,#F26455,#FFF0CE') { $errors.Add('other/image/logo-1.svg must use the approved three-color palette') }
}
if (Test-Path $monoSvg) {
    $monoSource = Get-Content -Raw $monoSvg
    $monoColors = [regex]::Matches($monoSource, '#[0-9A-Fa-f]{6}') | ForEach-Object { $_.Value.ToUpperInvariant() } | Sort-Object -Unique
    if (($monoColors -join ',') -ne '#000000') { $errors.Add('other/image/logo-2.svg must be the new monochrome mark') }
    if (([regex]::Matches($monoSource, 'A27 27')).Count -ne 24) { $errors.Add('monochrome SVG film perforations must use six rounded capsule paths') }
}

$resourceFiles = @(
    'app/src/main/res/drawable/ic_launcher_background.xml',
    'app/src/main/res/drawable/ic_launcher_foreground.xml',
    'app/src/main/res/drawable/ic_launcher_monochrome.xml',
    'app/src/leanback/res/drawable/ic_banner_foreground.xml',
    'app/src/leanback/res/mipmap-anydpi/ic_banner.xml',
    'app/src/main/res/drawable/ic_brand_logo.xml',
    'other/image/logo-1.svg',
    'other/image/logo-2.svg'
)
$legacyColors = '#B39DDB|#4A148C|#FF2D8A|#881337|#FF5A1F|#E91E63|#FFEB3B|#E53935|#F23838|#20242B|#FFF7F3'
foreach ($relative in $resourceFiles) {
    $path = Join-Path $root $relative
    if (-not (Test-Path $path)) { $errors.Add("missing Android resource $relative"); continue }
    $source = Get-Content -Raw $path
    if ($source -match $legacyColors) { $errors.Add("legacy icon color remains in $relative") }
}

$generator = Get-Content -Raw (Join-Path $root 'artwork/app-icon/GenerateAppIconAssets.java')
if ($generator -notmatch '204,272,616,526,184,184' -or $generator -notmatch '204,244,616,526,184,184') {
    $errors.Add('Java2D frame radius must match the SVG rx=92 geometry')
}

$packagedFavicon = Join-Path $root 'artwork/app-icon/ibox-favicon.ico'
$appFavicon = Join-Path $root 'app/src/main/assets/favicon.ico'
if ((Test-Path $packagedFavicon) -and (Test-Path $appFavicon)) {
    if ((Get-FileHash $packagedFavicon).Hash -ne (Get-FileHash $appFavicon).Hash) { $errors.Add('app favicon does not match packaged favicon') }
}

$manifestPath = Join-Path $root 'artwork/app-icon/export-manifest.json'
if (Test-Path $manifestPath) {
    $manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
    $masterFile = Join-Path (Split-Path $manifestPath) $manifest.source_master.file
    if (-not (Test-Path $masterFile)) {
        $errors.Add("source master missing: $($manifest.source_master.file)")
    } elseif ((Get-FileHash $masterFile -Algorithm SHA256).Hash -ne $manifest.source_master.sha256) {
        $errors.Add("source master hash mismatch: $($manifest.source_master.file)")
    }
    foreach ($export in $manifest.exports) {
        $path = Join-Path (Split-Path $manifestPath) $export.file
        if (-not (Test-Path $path)) { $errors.Add("manifest export missing: $($export.file)"); continue }
        $actual = (Get-FileHash $path -Algorithm SHA256).Hash
        if ($actual -ne $export.sha256) { $errors.Add("manifest hash mismatch: $($export.file)") }
    }
}

$zipPath = Join-Path $root 'artwork/app-icon/ibox-app-icon-assets.zip'
if (-not (Test-Path $zipPath)) {
    $errors.Add('missing distributable ZIP')
} else {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entries = $archive.Entries.FullName
        foreach ($entry in @('artwork/app-icon/ibox-app-icon.svg','artwork/app-icon/ibox-favicon.ico','app/src/main/assets/favicon.ico','app/src/main/res/drawable/ic_brand_logo.xml','other/image/logo-1.svg')) {
            if ($entry -notin $entries) { $errors.Add("ZIP missing $entry") }
        }
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -match '(?i)i' + 'media') { $errors.Add("ZIP contains legacy brand in path $($entry.FullName)") }
        }
        foreach ($entry in $archive.Entries | Where-Object { $_.FullName -match '\.(xml|svg|json|md|ps1|java)$' }) {
            $reader = [IO.StreamReader]::new($entry.Open())
            try { $source = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($source -match '(?i)i' + 'media') { $errors.Add("ZIP contains legacy brand in $($entry.FullName)") }
        }
        foreach ($export in $manifest.exports) {
            $entryName = "artwork/app-icon/$($export.file)"
            $entry = $archive.GetEntry($entryName)
            if ($null -eq $entry) { $errors.Add("ZIP missing manifest export $entryName"); continue }
            $stream = $entry.Open()
            try {
                $sha = [System.Security.Cryptography.SHA256]::Create()
                try { $zippedHash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '') } finally { $sha.Dispose() }
            } finally { $stream.Dispose() }
            if ($zippedHash -ne $export.sha256) { $errors.Add("ZIP contains stale export $entryName") }
        }
    } finally { $archive.Dispose() }
}

$expectedPngs = @{
    'app/src/main/res/mipmap-mdpi/ic_launcher.png'=48; 'app/src/main/res/mipmap-mdpi/ic_launcher_round.png'=48
    'app/src/main/res/mipmap-hdpi/ic_launcher.png'=72; 'app/src/main/res/mipmap-hdpi/ic_launcher_round.png'=72
    'app/src/main/res/mipmap-xhdpi/ic_launcher.png'=96; 'app/src/main/res/mipmap-xhdpi/ic_launcher_round.png'=96
    'app/src/main/res/mipmap-xxhdpi/ic_launcher.png'=144; 'app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png'=144
    'app/src/main/res/mipmap-xxxhdpi/ic_launcher.png'=192; 'app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png'=192
    'app/src/main/ic_launcher-playstore.png'=1024
}
Add-Type -AssemblyName System.Drawing
foreach ($entry in $expectedPngs.GetEnumerator()) {
    $path = Join-Path $root $entry.Key
    if (-not (Test-Path $path)) { $errors.Add("missing $($entry.Key)"); continue }
    $image = [System.Drawing.Image]::FromFile($path)
    if ($image.Width -ne $entry.Value -or $image.Height -ne $entry.Value) { $errors.Add("wrong dimensions for $($entry.Key)") }
    $image.Dispose()
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Output "FAIL: $_" }
    exit 1
}
Write-Output 'PASS: icon sources, palette, Android resources, and raster dimensions'
