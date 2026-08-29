# VYBE for iPhone

`iosApp` is the native iOS companion to the Android app in `app`. The two
platforms deliberately keep separate UI and platform code while sharing the
same product name, design language, release version, and GitHub release.

## Requirements

- macOS with Xcode 16 or newer
- iOS 17 or newer
- An Apple Developer team for device/App Store distribution

## Run locally

1. Open `iosApp/VYBE.xcodeproj` in Xcode.
2. Select the `VYBE` scheme and an iPhone simulator.
3. Set your development team under **Signing & Capabilities** when running on
   a physical iPhone.
4. Build and run.

The app imports audio through the iOS document picker and copies chosen files
into its sandbox. This is the iOS equivalent of Android's device-library scan:
iOS does not allow arbitrary filesystem scanning. Imported tracks remain
available offline and can play in the background with lock-screen controls.

## Release IPA

The repository workflow `.github/workflows/mobile-release.yml` builds Android
APKs and a signed iOS IPA on version tags (`v*`). Configure the Apple signing
secrets described in `docs/IOS_RELEASE.md` before running a release.

