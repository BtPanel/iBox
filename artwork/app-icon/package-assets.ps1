$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$zipPath = Join-Path $PSScriptRoot 'ibox-app-icon-assets.zip'

if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = @()
    $files += Get-ChildItem $PSScriptRoot -File | Where-Object { $_.Name -notin @('ibox-app-icon-assets.zip', 'GenerateAppIconAssets.class') }
    $relativeFiles = @(
        'app/src/main/ic_launcher-playstore.png', 'app/src/main/assets/favicon.ico',
        'app/src/main/res/drawable/ic_launcher_background.xml', 'app/src/main/res/drawable/ic_launcher_foreground.xml',
        'app/src/main/res/drawable/ic_launcher_monochrome.xml', 'app/src/main/res/drawable/ic_brand_logo.xml',
        'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml', 'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
        'app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml', 'app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml',
        'app/src/leanback/res/drawable/ic_banner_foreground.xml', 'app/src/leanback/res/mipmap-anydpi/ic_banner.xml',
        'app/src/leanback/res/mipmap-anydpi-v26/ic_banner.xml', 'app/src/main/res/values/colors.xml',
        'other/image/logo-1.png', 'other/image/logo-1.svg', 'other/image/logo-2.svg'
    )
    $files += $relativeFiles | ForEach-Object { Get-Item (Join-Path $root $_) }
    $files += Get-ChildItem (Join-Path $root 'app/src/main/res/mipmap-*') -Filter 'ic_launcher*.png'
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($root.Path.Length).TrimStart('\').Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $archive.Dispose()
}

$hash = (Get-FileHash $zipPath -Algorithm SHA256).Hash
Write-Output "PASS: packaged $zipPath"
Write-Output "SHA256: $hash"
