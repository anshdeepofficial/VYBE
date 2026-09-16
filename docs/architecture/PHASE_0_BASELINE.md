# Phase 0 baseline — open, not release-approved

Inspected 2026-09-16. No runtime behavior or UI is changed by this baseline PR.

## Reproducible source snapshots

| Project | Inspected revision |
|---|---|
| VYBE | `2afa33920908abe5a9f770da64a6eb08b713fef5` |
| BitChord | `e43429ff305b249bc775bc23ea363e25fb6b5bf0` |
| LastWave-Native | `0f875dbce2375b07cc27adbd3ba4b43ab592e236` |

VYBE has no pre-existing root/nested AGENTS.md at this snapshot. Its Android
namespace remains `com.theveloper.pixelplay`; application ID is
`com.vybe.musicplayer`. Do not combine an identity migration with package renames.
The app configures SDK 37, minimum SDK 26, NDK 27.0.12077973 and a Java 21 daemon
toolchain. Gradle wrapper pins 9.5.1 and its distribution checksum.

## Observed implementation gaps

These are source findings, **not claims of reproduced device failures**.
Paths below are relative to `app/src/main/java/com/theveloper/pixelplay/`.

| Finding | Evidence | Intended phase |
|---|---|---|
| Structured search can return Saavn songs when YT has no song/album/artist results | `data/repository/OnlineMusicRepository.kt`, `searchMusicStructured` | 5; catalogue policy applies throughout |
| Radio candidates undergo language/style heuristics and preferred-artist sorting | Same file: `getAutoplayQueue`, `keepSeedStyle`, `filterAndPrioritizeRecommendations` | 4, 7 |
| Saved queues lose remote album browse ID during Song conversion | `data/model/SavedPlaybackQueue.kt` omits `Song.remoteAlbumBrowseId` in both mappings | 1 |
| Saved queues also omit multi-artist refs, album artist, release timestamp and music-video flag | Compare `SavedQueueSong` to `data/model/Song.kt` | 1; design backward-compatible defaults |
| Process-death queue snapshot lacks remote album/artist identity | `data/model/PlaybackQueueSnapshot.kt` | 1, 2 |
| Home batches remote results before publishing and collapses some 1.8-second timeouts to empty lists | `presentation/viewmodel/DailyMixStateHolder.kt`, `RemoteHomeData` construction | 6 |
| Placeholder metadata recovery still exists; full lifecycle correctness is unverified | `data/service/MusicService.kt`, `resolveMediaItemsByIds` | 1 |
| Shuffle restore snapshot is held separately from player state | `presentation/viewmodel/QueueStateHolder.kt`; callers in `PlayerViewModel.kt` and `QueueUndoStateHolder.kt` | 2, 4 |
| Reorder forwards displayed indices to a pre-shuffle snapshot; differing orders need occurrence-aware investigation | `PlayerViewModel.kt` → `onQueueItemMoved`; not established as a reproduced bug | 2, 4 |
| Existing phone debug workflow targets master, although repository default is main; it builds but does not run unit tests | `.github/workflows/phone-debug.yml` | 0 |

Previous user reports (pending fresh reproduction): generic Online Track text,
missing search albums/artwork, queue add/remove/reorder issues, irrelevant or
duplicate Up Next, idle processing notification, dead settings search, Home and
library loading issues. Do not assume an old report remains reproducible on this SHA.

## Compatibility surfaces to preserve

- `DualPlayerEngine` already keeps physical A/B fields private and owns a
  windowed queue/crossfade implementation. Do not assume it needs replacement.
- `MusicService` publishes platform/session, Cast and Wear state; migrate
  callers in small steps, retaining commands and event behavior.
- `QueueStateHolder` tracks an unshuffle restore snapshot; removing it requires
  preserving add/remove/move behavior, including repeated manual entries.
- Saved queues are serialized separately from process-death playback snapshots.
  A fix to one does not prove the other migration complete.
- Search/Home parsing already has tests for provider shelves and entity IDs.
  Existing stats, Cast, trusted media-item, decoder, focus and buffer policy
  tests must stay in the regression suite.

## Reference implementations actually inspected

- BitChord `playback/QueueBuilder.kt` preserves candidate traversal order after
  selecting non-video songs and applies recording/artist cleanup. Its title
  normalization drops all square-bracket content and does not use duration;
  copying it verbatim would not satisfy VYBE's version/duration safeguards.
- BitChord `playback/QueueHistory.kt` and `QueueShuffleTest.kt` separate history
  rules and verify duplicate retention. Use behavioral contracts as inspiration,
  not wholesale replacement of VYBE queue semantics.
- BitChord `data/innertube/StreamResolver.kt`, `coalescedResolve`, uses a lazy
  Deferred, atomic registration and completion-owned removal. The important
  lesson is that one cancelled waiter must not unregister active shared work.
- LastWave `playback/MusicPlayer.kt` exposes a canonical `MusicPlayerState` plus
  distinct `chromeState` and `progressState` projections. It also has secondary
  and outgoing players: do not reduce its architecture to "one physical player."
- LastWave `data/feed/FeedRepository.kt` has an account-scoped cache, an update
  callback and concurrent source loads. VYBE should adopt the ownership/cache
  boundary; independently loading shelves still need their own design/tests.

These are targeted baseline reads, not a completed full architecture audit.
Each later technical decision must inspect the relevant implementation in all
three projects again as required by the decision protocol.

## Added characterization coverage

Eight additional tests exercise existing production classes without adding
runtime substitute data:

- Repeated queue entries survive anchored shuffle and source list is unchanged.
- Stale anchor indices preserve the selected clamped anchor and all entries.
- Empty/single-entry synchronous and suspend shuffle remain valid.
- Removing one occurrence preserves another intentional repeated track.
- Missing removals and invalid moves leave the restore snapshot intact.
- Old saved-queue JSON lacking optional fields decodes with stable identity.
- Saved-queue serialization preserves ordering, repeats and selected track ID.
- Local queue restore preserves the local URI/path/type without inventing YT IDs.

This does not test crossfade/session event ownership, Bluetooth, live YT
ordering, network recovery, canonical autoplay provenance or missing browse-ID
propagation. Those are explicit remaining gaps, not implied passes.

## Verification evidence and release gates

Command attempted on the unchanged baseline:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Result: failed **before compilation**. The configured
`https://mirrors.cloud.tencent.com/gradle/gradle-9.5.1-bin.zip` distribution could
not be downloaded (`java.net.SocketException: Network is unreachable`, all four
attempts). This environment has no `adb` on PATH; no connected Android device
or emulator was established. Do not infer compiler/test results from this failure.

The same command was attempted on the candidate and hit the same distribution
download failure before compilation. Both need rerunning once dependencies are accessible.
`vybe-baseline.yml` runs the full app unit suite and debug build for both PR base
and candidate merge on GitHub Actions. It uploads available test reports even
on failure. A failure before reports exist is still a failure. The network
NewPipe integration test is opt-in (`PIXELPLAY_RUN_NETWORK_TESTS=true`); a default
green unit run does not prove live stream resolution works.

| Gate | Evidence/status |
|---|---|
| Baseline compile + unit suite | Blocked before compilation by distribution download |
| Candidate compile + unit suite | Also blocked before compilation; pending CI |
| Critical instrumented flows | Not run |
| Previous behavior regression | Coverage added; passing execution not yet established |
| Real-device test | Not run |
| Phase 0 complete | **No** |

## Device sign-off worksheet

Record tested commit/APK, device/Android version, account signed-in/out state,
network and results. Never record cookies, tokens or signed stream URLs.

1. Search a known song, album and artist; record native IDs and provider order.
2. Play, lock device, seek, next and previous; verify title/artwork and audio.
3. Open queue, add next/add queue, remove and reorder (including a repeated song).
4. Toggle shuffle/repeat and exercise crossfade; verify no resurrection, identity
   switch or duplicate listening event.
5. Background app, use Bluetooth controls, return, switch Wi-Fi/mobile and resume.
6. Restart process and restore queue; inspect metadata, current occurrence and position.
7. Check idle notification disappearance and offline download playback.

Record pass/fail per step with reproducible failures. Phase 1 is not release-ready
until Phase 0's gates are closed; keep later phases pending, not silently waived.
