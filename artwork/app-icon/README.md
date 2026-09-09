# iBox app icon assets

Approved three-color film-frame icon for the mobile and Android TV variants.

## Source files

- `ibox-app-icon.svg`: full-color square master.
- `ibox-mark.svg`: transparent editable mark.
- `ibox-monochrome.svg`: single-color editable mark for themed treatments.
- `ibox-tv-banner.svg`: editable 320 x 180 TV banner.

## Raster originals

- `ibox-app-icon-1024.png`: full-size app icon.
- `ibox-foreground-1024.png`: transparent foreground.
- `ibox-monochrome-1024.png`: transparent monochrome mark.
- `ibox-play-store-512.png`: Play Store export.
- `ibox-tv-banner-320x180.png`: TV launcher banner.
- `ibox-favicon.ico`: embedded web favicon.
- `preview.png`: large and actual-size comparison.

Run `GenerateAppIconAssets.java` from the repository root to regenerate the PNG exports and Android legacy launcher images. Run `package-assets.ps1` to rebuild the distributable ZIP, then run `test-assets.ps1` to validate the palette, hashes, package inventory, Android resources, and raster dimensions.
