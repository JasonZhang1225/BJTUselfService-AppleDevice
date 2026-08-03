# Apple app icon source

`AppIconMaster-v1.png` is the first 1254 × 1254 opaque master produced with the
built-in image generation editor from the repository's existing Android
launcher artwork. A Computer Use review on the iPadOS Home Screen showed that
its mascot was too small, so `AppIconMaster-v2.png` enlarges the same mascot
while preserving a safe white margin for Apple's system-applied icon mask.

The iOS target consumes the 1024 × 1024 `AppIcon-v2.png` copy from
`iosApp/iosApp/Assets.xcassets`. The macOS `BJTUselfServiceKMP-v2.icns` is
compiled from `MacIcon-v2.xcassets` and supplied to the Compose Desktop native
distribution. The v1 files remain only as visual-audit provenance. Do not
replace the original Android launcher resources from this directory.
