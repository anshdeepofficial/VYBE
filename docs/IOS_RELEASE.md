# Building and releasing the iPhone app

## GitHub Actions secrets

Add these Actions secrets to the repository:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded official Android release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Android keystore password |
| `ANDROID_KEY_ALIAS` | Android release-key alias |
| `ANDROID_KEY_PASSWORD` | Android release-key password |
| `APPLE_CERTIFICATE_P12_BASE64` | Base64-encoded Apple Distribution `.p12` |
| `APPLE_CERTIFICATE_PASSWORD` | Password used when exporting the `.p12` |
| `APPLE_PROVISIONING_PROFILE_BASE64` | Base64-encoded App Store/Ad Hoc `.mobileprovision` for `com.vybe.musicplayer` |
| `APPLE_TEAM_ID` | Ten-character Apple Developer team identifier |

On macOS, encode files without line wrapping:

```bash
base64 -i vybe-release.jks | pbcopy
base64 -i Distribution.p12 | pbcopy
base64 -i VYBE.mobileprovision | pbcopy
```

## Tagged release

Push a semantic version tag such as `v1.0.0`. The `Mobile Release` workflow:

1. Builds and verifies the signed ARM64 and ARMv7 Android APKs.
2. Imports the temporary Apple signing key and provisioning profile.
3. Archives the iOS app with the version taken from the tag.
4. Exports `VYBE-iOS-<version>.ipa`.
5. Creates or updates the matching GitHub Release and attaches all artifacts.

The workflow also supports manual dispatch. A signed IPA cannot be produced
without Apple-issued credentials; this is an Apple platform requirement, not a
source-code limitation.

## App Store preparation

Before public submission, create the bundle ID `com.vybe.musicplayer` in the
Apple Developer portal, register the app in App Store Connect, provide privacy
answers for imported/local media, and test the archive through TestFlight.
