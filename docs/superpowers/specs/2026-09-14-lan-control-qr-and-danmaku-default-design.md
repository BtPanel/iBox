# LAN Control QR and Danmaku Default

## Goal

Add a scannable entry to the existing LAN control dialog on both mobile and TV. A second phone can scan the code to open the device's web control page. Make danmaku disabled by default on both variants without overwriting a saved user choice.

## Existing Flow

Both variants open the shared `RemoteAccessDialog`. It currently shows an enable switch, LAN address, six-digit pairing PIN, paired clients, and credential controls. The local HTTP server already serves a pairing page and requires the PIN before authorizing a browser. The project already has `QRCode.getBitmap(...)` and TV subscription dialogs use it to show a QR code beside an address.

`DanmakuSetting.isShow()` is shared by both variants and currently reads the `danmaku_show` preference with a `true` fallback. Playback views and the mobile control state read this setting.

## QR Design

- Add a QR image to the shared LAN control dialog, visually grouped with the existing address and PIN. Match the established subscription-management code treatment while respecting the dialog's mobile width and TV focus/navigation layout.
- Show the image only while LAN control is enabled and the generated LAN address is usable. Encode `Server.get().getAddress()` only. Keep the address selectable/copyable and the PIN visible as separate fallbacks.
- Scanning opens the existing web pairing page. The browser must still enter the six-digit PIN; the QR must not contain `pin`, access token, stream token, cookie, or other authorization material.
- Refresh the displayed address and QR when the dialog opens or LAN control is enabled. Reopening the dialog after a network address change yields the new code. Disabling LAN control hides the code and address/PIN section.
- Reuse the current QR generator and server. Do not add a TV camera scanner, camera permission, new network endpoint, or new pairing protocol.
- If the address or QR bitmap is unavailable, retain the text address/PIN workflow and do not show a blank code.

## Danmaku Design

Change only the fallback for the existing `danmaku_show` preference from `true` to `false`. A stored `true` or `false` value continues to win. Manual enabling, search/loading, and per-playback UI behavior remain unchanged. No preference migration or forced reset is needed.

## Verification

- Test that the QR payload is only the LAN address, with no credentials, and that disabled/unavailable states do not display an unusable code.
- Test the shared danmaku default and preservation of explicit saved values.
- Run all four flavor/ABI unit-test variants, mobile and TV Lint, and four Release builds. Inspect the dialog on mobile portrait and TV for code size, theme contrast, and focus behavior; verify that a phone scan reaches the PIN page and cannot bypass pairing.
- After implementation is verified, merge the feature branch into `master`, regenerate four Release APKs and SHA-256 checksums, and package the committed source snapshot with the existing release script. Keep previous release archives intact and use a unique new label.

## Out of Scope

No automatic PIN pairing, QR-based app-to-app remote control, TV camera support, or changes to existing users' danmaku preferences.
