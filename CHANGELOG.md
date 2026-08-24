## [0.8.0] - 2026-08-24

### Added
- **Spotify Integration**: Connect your Spotify account to import playlists directly into VYBE.
- Added dedicated Spotify Account dashboard in Settings.

### Fixed
- **Privacy**: Removed personal developer domains from share links; reverted to reliable app links fallback.
- **Metadata**: Resolved issue where Online Track placeholder data was being overwritten over legitimate local metadata during caching.
- **Lyrics**: Fixed a 400 Bad Request network crash when searching lyrics with empty queries (Suggestions OFF).

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.7.22] - 2026-08-24
### Added
- **Artwork**: Added loading indicators when fetching album artwork.
### Fixed
- **Playback**: Fixed an issue where original metadata was overwritten.
- **Lyrics**: Fixed a network crash (400 Bad Request) when Lyrics Suggestions were turned off.
- **Sleep Timer**: Extracted Sleep Timer into its own dedicated menu button in the main player.
- **Recommendations**: Moods are now dynamically curated based on your listening habits.
- **Recommendations**: Blocked Artists now correctly filter out of generated daily mixes.
- **Search**: Improved typo-tolerance search ranking to heavily prioritize exact matches.
- **Settings**: Refined category icon colors for a richer, premium look.

## [0.7.21] - 2026-08-23
### Added
- **Search Enhancements**: YouTube Music search results are now cleanly categorized, with a dedicated section and tab just for "Videos", separating them from standard songs.
### Removed
- **UI Architecture**: Completely removed the experimental "Liquid Glass" App UI Style setting as it caused performance and layout issues. The app now strictly uses the polished Material UI.

## [0.7.20] - 2026-08-23
### Fixed
- **Library Crash Fix**: Fixed a critical crash (`IllegalArgumentException: Key was already used`) that occurred when switching between tabs (like Folders/Albums) in the Library after playing music, which was caused by duplicate unique IDs in the listening history grid.

## [0.7.19] - 2026-08-23
### Changed
- **Onboarding Improvements**: Made critical permissions (Media, Notifications, and Battery Optimization) strictly mandatory. Users can no longer skip these during the initial setup, preventing unexpected crashes later. Optional preferences (Theme, Backup, Layouts) remain skippable.
### Fixed
- **Notification Fix**: Fixed an issue where tapping the media notification in the status bar would unnecessarily trigger the player sheet to slide open. It now correctly returns you to your previous state in the app without unwanted actions.

## [0.7.18] - 2026-08-23
### Changed
- **UI Enhancements**: Upgraded the Playlist and Artist detail screens by replacing standard rectangular buttons with a sleek row of three floating action buttons (Play, Shuffle, Loop) shaped with `RoundedStarShape`.
### Fixed
- **App Launch/Setup Crash**: Fixed a critical race condition that caused the app to crash or freeze on many devices immediately after completing the onboarding setup. Navigation to the Home screen now safely waits for DataStore commits.
- **Release Stability**: Added comprehensive ProGuard and R8 rules to prevent aggressive code stripping for Hilt, Room DAOs, ExoPlayer/Media3, and WorkManager, which caused invisible crashes on fresh installs.
- **Sync Safety**: Wrapped initial library full sync in a safety block so that localized sync failures do not block app setup.

## [0.7.17] - 2026-08-23
### Added
- **Search History**: Added a Search History UI that displays previous search queries when the search bar is empty.
### Changed
- **Branding Updates**: Removed the old Beta clean-install disclaimer on startup. Replaced the Beta button in the top bar with "Smoothy Play" text.
### Fixed
- **Queue Deletion Crash**: Fixed a `NumberFormatException` crash when attempting to delete online songs from the queue by safely filtering non-local IDs.
- **Song Info Sheet**: Online songs no longer display the "Delete from device" option, preventing unsupported actions.

## [0.7.16] - 2026-08-23
### Fixed
- **Keystore Crash Fix**: Fixed crashes on devices with broken MasterKey/EncryptedSharedPreferences by safely falling back to standard SharedPreferences.

## [0.7.15] - 2026-08-23
### Fixed
- **Immersive UI Fix**: Fixed the visibility of the immersive artwork gradient behind the top action bar on album screens.

## [0.7.10] - 2026-08-22

### Fixed
- Added release-safe recovery for legacy, incompatible, or corrupted local databases that could trap the app in an immediate startup crash loop.
- Added automatic recovery for a corrupted settings DataStore instead of crashing before the first screen appears.
- Release upgrades now rebuild only when local persistence cannot be opened safely; healthy databases and settings remain untouched.

## [0.7.9] - 2026-08-22

### Fixed
- Existing installations now migrate safely from database version 42 instead of crashing during startup.
- Added automated migration coverage through the current database version to prevent release-only upgrade failures.

## [0.7.8] - 2026-08-22

### Fixed
- Search and Home song menus now open online album and artist pages instead of disabling Go to Album.
- Missing YouTube Music album browse IDs are resolved on demand from the song metadata.
- Podcast and episode results are excluded while legitimate music audio results remain available.

### Changed
- Download notifications are silent and appear only after a real audio transfer starts, followed by a completion notification after the file is saved.

## [0.7.7] - 2026-08-22

### Added
- Live in-app and notification download progress, with retry feedback for failures.
- List/grid switching across Downloads, Songs, Albums, Artists, Playlists, and Liked.
- Listening-history fallbacks for Songs, Albums, and Artists when the device library is empty.

### Changed
- Search now filters podcast episodes, shows artist profiles, preserves relevant related songs, and expands complete artist catalogues.
- YouTube Music landscape artwork is presented in a square frame with a blurred background fill.
- Player title and artist labels now navigate to their album and artist pages, including an artist picker for collaborations.

## [0.7.6] - 2026-08-22

### Added
- In-app GitHub Releases update checks, APK download progress, and Android installer handoff.
- Volume-up resume after playback was automatically paused at zero media volume.
- A dedicated in-app Changelog screen under About.

### Changed
- Scrollbars now default to off while remaining user-configurable.
- Synchronized lyric scrolling now reacts quickly instead of visually lagging behind the audio.
- Spotify public-playlist imports show live processed/total progress and support paginated large playlists.

### Fixed
- Online artist search results retain their real YouTube Music browse IDs.
- Artist profiles now display and play their Top Songs instead of showing only a song count.
- Spotify public-link parsing no longer exposes the raw `No value for state` error.
- Spotify account sign-in was removed while public URL import remains available.

## [0.7.5-beta] - 2026-06-13

### Added
- **Google Drive:** Added Google Drive support and improved player lifecycle management.
- **AI Lyrics:** Integrated AI lyrics translation logic in `LyricsStateHolder` and user preferences.
- **Gemma:** Deleted old Gemini model IDs and integrated Gemma model support.
- **Wear OS:** Added wear lyrics translation/romanization preferences and album art background.
- **Diagnostics:** Added a lag diagnostic tool.
- **Search:** Added multi-selection support to the Search screen.
- **UI:** Added outlined button style for AOD screen.
- **Connectivity:** Added support for HTTP URLs on local-network Navidrome and Jellyfin hosts.
- **Localization:** Added Arabic and Turkish language support, and unrecognized languages.

### Changed
- **Battery Optimization:** Drastically reduced battery consumption via audio offload and adaptive UI polling.
- **Queue System:** Refactored shuffle, queue reordering, and playback orchestration to `QueueStateHolder` using explicit queue indices.
- **Transitions & Animations:** Implemented Material 3 Expressive motion curves for player, queue sheet, and screen transitions.
- **Architecture:** Decomposed `MusicService` and modularized `PlayerViewModel` state listeners.
- **Library Sync:** Optimized library sync with throttled scans and faster artwork loading.
- **Database:** Migrated database to version 42 and updated Navidrome schema.
- **Equalizer:** Added "Save New" action and improved layout.
- **Localization:** Refactored app localization, resource cleanup, and UI text wrapping.
- **Dependencies:** Bumped dependencies including `kotlinx-collections-immutable`, `okhttp`, and Gradle plugins.

### Fixed
- **Playback:** Resolved buffering issues, song skipping lags, and unnecessary recompositions during playback.
- **Media Store Sync:** Improved MediaStore URI resolution, external song deletion, and Android 11+ storage volume resolution.
- **Lyrics & Metadata:** Fixed Chinese lyrics detection, pinyin tone suffixes, and batch metadata/artwork editing consistency.
- **Wear OS:** Resolved memory issues and state persistence.
- **UI:** Fixed marquee text fade glitches, navigation bar corner behavior, blur issues, scrollbar bugs, and layout/padding insets.
- **Other:** Fixed backup playlist update issues and startup AI provider errors.

### New Contributors
- @YtMechnij made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2106
- @juinc made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2109
- @ZL114514 made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2159
- @aliabbasov99 made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2262
- @Hisham-Alzamzami made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2335


## [0.7.0-beta] - 2026-05-25

### Added
- **Wear OS:** Music transfer, local playback, queue synchronization, and remote control from the watch.
- **AI:** Groq AI and OpenRouter (experimental) with token optimization and AI-powered playlist generation.
- **Cloud & Streaming:** Jellyfin support.
- Direct song synchronization from server albums in Navidrome.
- Standardized branding for NetEase Music.
- **Lyrics:** Synchronized translation with a dedicated toggle and Kugou LRC format support.
- Text alignment customization and improvements to TTML parsing.
- Advanced romanization for Japanese characters.
- **UI/UX:** Redesigned queue sheet and "Recently Played" pills with a dynamic palette.
- Marquee support for long titles and a compact mode for the navigation bar.
- New horizontal timeline for monthly statistics and multi-artist support.
- **Telegram:** Native support for topics, playlist display, and reactive updates.

### Changed
- **Audio Engine:** Complete overhaul with support for MIDI, improvements to ALAC/M4A/Opus, and decoder optimization (including Samsung-specific decoders).
- **Energy Efficiency:** Drastically reduced battery consumption and thermal optimization through UI task gates.
- **Database and Cache:** Massive optimizations to queries, cover art cache controller v3, and support for Scoped Storage.
- **Startup:** Improved load times through optimized generation of Baseline Profiles.
- Project license changed from MIT to Proprietary License.

### Fixed
- **Playback:** Fixed stuttering in Opus/MP3, errors in ReplayGain during crossfades, and flickering during album art changes.
- **Navigation:** Fixed navigation loops in Telegram and improved screen entry/exit animations.
- **Stability:** Eliminated crashes on Android 12+, fixed memory leaks (ANRs), and improved exception handling in background services.
- **Security:** CI hardening, encryption of cloud storage credentials, and media server access control.

### Localization
- 🇪🇸 **Spanish** | 🇫🇷 **French** | 🇷🇺 **Russian**
- 🇨🇳 **Simplified Chinese** | 🇮🇩 **Indonesian** | 🇮🇹 **Italian** | 🇩🇪 **German**

## [0.6.0-beta] - 2026-03-05

### Added
- Added Android Auto support through Media3 `MediaLibraryService`.
- Added Wear OS companion support, including watch transfer and playback controls.
- Added cloud provider expansions: Telegram playlist management, NetEase sync improvements, QQ Music integration, Subsonic/Navidrome, and Google Drive streaming (WIP).
- Added a modernized backup/restore system (v3), account management, and persistent queue restoration.
- Added smarter lyrics workflows (manual fallback search + storage refactor), Recently Played, and new multi-selection flows (songs/albums/playlists).
- Added home and UI customization features: collage patterns, quick settings tiles, expressive scrollbar refinements, and new widget styles.

### Changed
- Reworked player architecture and interaction model (unified player sheet refactors, predictive back handling, gesture tuning).
- Redesigned key surfaces including Lyrics, Cast, Artist, Genre, and Daily Mix experiences.
- Refined library/search/navigation behavior with safer navigation APIs and better state restoration.
- Improved audio compatibility and metadata handling (JAudioTagger fallback, URI handling, surround/noisy behavior).
- Expanded integration UX across Telegram/NetEase/QQ login and sync flows.

### Fixed
- Fixed multiple queue/shuffle edge cases (anchored shuffle, start-at-zero shuffle, queue synchronization).
- Fixed playback interruption behavior when headphones disconnect and resolved foreground service start restrictions.
- Fixed Cast-related crash cases and improved cast reliability.
- Fixed Sleep Timer UI issues, files tab navigation, album artist crash, and state-sync regressions in settings/reorder flows.
- Fixed release build stability (`R8`) and numerous UI polish issues across bottom sheets and controls.

### Performance
- Reduced recompositions and state overhead across Player, Library, Queue, and detail screens.
- Improved startup behavior (eliminated blank flash and deferred heavy Telegram native loading off main thread).
- Optimized folder/genre/artist loading, bottom navigation responsiveness, and gesture fluidity.
- Reduced CPU/main-thread pressure and improved service/widget runtime efficiency.
- Reduced APK size using ABI splits, downloadable fonts, and SDK cleanup.

### New Contributors
- @ThatOneCalculator
- @ryan7zoom
- @LarveyOfficial
- @Dv1101
- @Sincere-Bhattarai

## [0.5.0-beta] - 2026-01-14

### Added
- Implemented 10-band Equalizer and effects suite (feat: @theovilardo)
- Added M3U playlist import/export support (feat/fix: @lostf1sh, @theovilardo)
- Integrated Deezer API for artist images (feat: @lostf1sh)
- Added Gemini AI model selection, system prompt settings, and AI playlist entry point (feat: @lostf1sh, @theovilardo)
- Added sync offset support for lyrics and multi-strategy remote search (feat/fix: @lostf1sh, @theovilardo)
- Added Baseline Profiles for improved performance (feat/fix: @theovilardo, @google-labs-julesbot)
- Added support for custom playlist covers

### Changed
- **Material 3 Expressive UI**: Modernized Settings, Stats, Player, Bottom Sheets, and dialogs (refactor: @theovilardo, @lostf1sh)
- **Library Sync**: Rebuilt initial sync flow with phase-based progress reporting and linear indicators (feat: @lostf1sh)
- **Settings Architecture**: Introduced category sub-screens and improved navigation handling (refactor/fix: @theovilardo)
- **Queue & Player**: Decoupled queue updates from scroll animations, added animated queue scrolling (feat/fix: @lostf1sh, @theovilardo)
- Improved widget previews and case-insensitive sorting logic (feat/fix: @lostf1sh, @google-labs-julesbot)

### Fixed
- Fixed casting stability, queue transitions, and reduced latency (fix: @theovilardo)
- Fixed delayed content rendering and unwanted collapses in Player Sheet (fix/refactor: @theovilardo)
- Fixed reordering issues in queue
- General crash fixes and minor UX improvements (fix: @lostf1sh, @theovilardo)

## [0.4.0-beta] - 2025-12-15

### Added
- Major navigation redesign
- New file explorer for choosing source directories
- Landscape mode (thanks to "leave this blank for now")
- New Connectivity and casting functionalities
- Seamless continuity between remote devices
- Gapless transition between songs
- Crossfade
- New Custom Transitions feature (only for playlists)
- Keep playing after closed the app
- UI Optimizations
- Improved stats feature
- Redesigned Queue control with more features
- Improved different filetypes support for playing and metadata editing
- Improved permission controller
- Minor bug fixes

## [0.3.0-beta] - 2025-10-28

### What's new
- Introduced a richer listening stats hub with deeper insights into your sessions.
- Launched a floating quick player to instantly open and preview local files.
- Added a folders tab with a tree-style navigator and playlist-ready view.

### Improvements
- Refined the overall Material 3 UI for a cleaner and more cohesive experience.
- Smoothed out animations and transitions across the app for more fluid navigation.
- Enhanced the artist screen layout with richer details and polish.
- Upgraded DailyMix and YourMix generation with smarter, more diverse selections.
- Strengthened the AI assistant to deliver more relevant playback suggestions.
- Improved search relevance and presentation for faster discovery.
- Expanded support for a broader range of audio file formats.

### Fixes
- Resolved metadata quirks so song details stay accurate everywhere.
- Restored notification shortcuts so they reliably jump back into playback.

## [0.2.0-beta] - 2024-09-15

### Added
- Chromecast support for casting audio from your device (temporarily disabled).
- In-app changelog to keep you updated on the latest features.
- Improved lyrics search
- Support for .LRC files, both embedded and external.
- Offline lyrics support.
- Synchronized lyrics (synced with the song).
- New screen to view the full queue.
- Reorder and remove songs from the queue.
- Mini-player gestures (swipe down to close).
- Added more material animations.
- New settings to customize the look and feel.
- New settings to clear the cache.

### Changed
- Complete redesign of the user interface.
- Complete redesign of the player.
- Performance improvements in the library.
- Improved application startup speed.
- The AI now provides better results.

### Fixed
- Fixed various bugs in the tag editor.
- Fixed a bug where the playback notification was not clearing.
- Fixed several bugs that caused the app to crash.

## [0.1.0-beta] - 2024-08-30

### Added
- Initial beta release of PixelPlayer Music Player.
- Local music scanning and playback (MP3, FLAC, AAC).
- Background playback using a foreground service and Media3.
- Modern UI with Jetpack Compose, Material 3, and Dynamic Color support.
- Music library organization by songs, albums, and artists.
- Home screen widget for music control.
- Real-time audio waveform visualization.
- Built-in tag editor for song metadata.
- AI-powered features using Gemini.
- Smooth in-app permission handling.
# VYBE 0.7.12 — 23 August 2026

- Fixed the duplicate artist-history key crash.
- Preserved square artwork and centered landscape thumbnails over a blurred fill.
- Restored original album title, artist, header artwork, and track thumbnails.
- Added automatic GitHub update alerts with 1-hour, tomorrow, and skip-version controls.
