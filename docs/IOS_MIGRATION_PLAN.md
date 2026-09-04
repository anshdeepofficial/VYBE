# VYBE iPhone migration plan

## Architecture decision

The Android application contains more than 600 Kotlin files and relies heavily
on Android-only APIs (Media3, MediaStore, Room, Hilt, WorkManager, widgets,
Wear OS, Cast, Android credentials and services). Converting the existing app
module into Kotlin Multiplatform would require rewriting the Android app while
simultaneously building iOS, putting the stable Android release at risk.

VYBE for iPhone is therefore a separate native SwiftUI application in
`iosApp/`. It uses AVFoundation, MediaPlayer, SwiftUI and Codable persistence.
The Android tree remains unchanged. Product behavior and design are mirrored
at the screen/component level, while platform services use native adapters.

## Delivery phases

### Phase 1 — iPhone foundation (implemented here)

- Xcode project, iPhone target, app icon and launch screen
- Expressive VYBE theme and navigation matching Android screenshots
- First-run setup and Files-based local music import
- Persistent songs, favorites, playlists and recent-play history
- Home, Search, Library, Settings, Now Playing, Queue and Lyrics screens
- AVFoundation queue playback, shuffle/repeat, seeking and background audio
- Lock-screen/Control Center metadata and remote transport controls
- One tagged GitHub release workflow for Android APK and signed or sideloadable IPA

### Phase 2 — metadata and advanced playback

- Editable tags and embedded synchronized lyrics
- Gapless playback/crossfade and ReplayGain parity
- Equalizer/DSP implementation using AVAudioEngine
- Rich album/artist/genre detail actions and listening-stat visualizations
- iOS widgets, Shortcuts and CarPlay

### Phase 3 — online accounts

- Shared API contracts for Navidrome, Jellyfin and supported cloud providers
- Provider-specific OAuth/keychain storage
- Offline download manager using background URL sessions
- Import/export format compatibility with Android backups and playlists

### Phase 4 — release hardening

- Snapshot parity tests against the Android reference screens
- Unit/UI tests on the supported iPhone matrix
- Accessibility, localization and Dynamic Type audit
- TestFlight rollout, privacy manifest, App Store metadata and review

## Platform mappings

| Android | iPhone |
| --- | --- |
| Jetpack Compose | SwiftUI |
| Media3 + MediaSession | AVPlayer + MPRemoteCommandCenter |
| MediaStore scan | Files document picker + sandboxed media library |
| Room | Codable application-support store (Core Data migration-ready) |
| DataStore | AppStorage/UserDefaults |
| Foreground playback service | Background audio mode |
| Notification controls | Lock Screen / Control Center Now Playing |
| Material dynamic color | Artwork-derived VYBE palette |
| Android widgets | WidgetKit (Phase 2) |
| Wear OS | watchOS (future independent target) |

## Definition of complete parity

Parity means the same information architecture, content hierarchy, actions,
visual tokens and user-visible behavior. Native permission dialogs, file
selection, background execution and system media surfaces intentionally follow
Apple's platform rules instead of copying Android system behavior.

