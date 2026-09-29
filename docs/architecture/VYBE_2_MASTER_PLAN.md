# VYBE 2.0 — implementation contract

Source: Anshdeep Singh's 54-section master implementation plan supplied on
2026-09-16. This is a condensed execution copy. The full user specification
governs if more detail is needed; no requirement is relaxed by condensation.

## Objective and invariants (1–3)

Retain VYBE's identity, design and features; learn from BitChord reliability and
LastWave state/feed architecture without cloning either app. Preserve crossfade,
local music, Spotify import, Wear, Cast, Android Auto, ReplayGain and customization.

YouTube Music owns the online catalogue and initial discovery/recommendation
candidate pool. Songs retain `videoId`, albums/artists `browseId`, playlists
`playlistId`/`browseId` throughout their lifecycle. Never use a signed URL as
identity or search title/artist when the native ID exists. Normalize metadata
without replacing available titles with `Online Track`. Introduce validated
identity types where practical without breaking old stored data.

Search retains provider relevance order after objective invalid-result and
duplicate cleanup. Personalization is a small recommendation adjustment; the
provider rank remains a strong prior.

Target: UI → ViewModels → domain repositories → PlaybackCoordinator →
DualPlayerEngine → StreamResolver. Domain repositories cover YouTube Music,
Home, recommendations, Library and playback. MusicService becomes the platform
bridge, not the feature implementation hub. Exactly one canonical playback and
queue state sits above private physical players A/B.

Canonical models carry track title, artists, album/browse ID, duration, artwork,
explicit flag and source; albums carry native ID, artists, year and artwork;
artists and playlists carry their native IDs, names/titles and artwork.

## Discovery and reliable playback (4–13)

4. Search: debounced YT suggestions plus a few mixed instant media results;
   avoid account-history pollution where possible. Submitted search uses YT
   filters, immediately shows page one, and lazily loads continuations near the
   bottom. UI: Search VYBE, recent searches, suggestions, then Top result,
   Songs, Albums, Artists, Playlists. Categories use full vertical paginated pages.
5. No silent Saavn/Netease/QQ catalogue substitution. Explicit integrations may
   remain. Spotify imports metadata → strict recording match → YT video ID.
6. One StreamResolver accepts video ID and returns a validated stream. Check
   usable cache, coalesce in-flight requests, try existing client strategies,
   probe candidates where appropriate, cache successes and track strategy
   health. Track last success, failures per video/network, expiry, cooldown,
   permanent restrictions and diagnostics. Preserve useful NewPipe fallback;
   manage strategies before adding more. Cancellation of one waiter must not
   create duplicate walks or cancel another waiter's resolution.
7. PlaybackCoordinator owns current track, queue/index, playback/position/duration,
   buffering, repeat/shuffle, autoplay, sleep timer, radio seed and errors.
8. Preserve DualPlayerEngine; A/B swaps must not change session identity, duplicate
   queue/stat events, corrupt metadata/artwork or create position jumps.
9. Queue supports play now/next, append, remove, reorder, clear, shuffle, repeat
   one/all and autoplay extension. Keep played/current/manual/generated items
   distinguishable. Autoplay never discards manual items.
10. Autoplay uses current video ID → YT watch-next/radio → light cleanup → queue.
    Remove current/queued recordings, audio/video duplicates, blocked artists and
    extreme artist repetition. Language/genre/title heuristics are fallback only
    when provider radio fails; never normal provider reordering.
11. Recording identity considers normalized title, shared/primary artists,
    duration tolerance and album when available. Preserve remix/live/cover
    distinctions; different video IDs can represent the same recording.
12. One HomeFeedRepository owns YT Home, Quick Picks, Listen Again, history,
    releases, mixes, albums/artists, signals and cached feed. ViewModels display it.
13. Home publishes cached content first, then independently updated shelves.
    A slow endpoint cannot blank Home; a short timeout is not proof of absence.

## Experience and entity pages (14–23)

14. Home uses Material 3, expressive motion, artwork color, restrained blur and
    readable light/dark themes. Greeting/logo/profile, then relevant Quick Picks,
    Listen Again, Your Mixes, Latest Releases, Made for You, Albums for You,
    Artists You Like and Recently Played. Show only useful available shelves.
15. Primary navigation: Home, Search, Library; profile/settings from avatar.
    Mini-player above navigation; downloads/local/entities inside Library.
16. Mini-player: artwork/title/artist/play-pause, optional next. Tap transitions
    continuously to full player; next/previous gestures where appropriate.
17. Full player: close/handle, device/source, square uncropped artwork, title/artist,
    progress/times, transport, like/lyrics/queue and device/quality. Menu includes
    artist/album, playlist, play next/queue, download, timer, share, details and
    speed. No redundant Radio action. Only backgrounds may blur/distort artwork.
18. Lyrics transition shrinks/moves artwork and deepens blur. Support synced
    lines, available word sync, unsynced fallback, auto/manual scroll, highlighting
    and supported tap-to-seek. Lyrics never block audio.
19. Queue sheet/page separates Playing Now, Up Next and Autoplay. Drag reorder,
    remove, play next, playlist and clear-manual actions persist across refresh.
20. Native artist IDs, cached artwork with placeholder/fallback/retry, available
    follow state, play/shuffle and only real Popular/Releases/Albums/Singles/
    Featured On/Related sections.
21. Albums load by browse ID, never title search when ID exists. Cover, title,
    artist, year, count/duration, play/shuffle/download and track list.
22. Library unifies likes, playlists, albums, artists, downloads, local and
    recently added; explicit source identity remains internal.
23. Visible account state. Anonymous YT works signed out; connected YT Home,
    history, likes, playlists, subscriptions, mixes and recommendations take
    precedence signed in.

## Signals, storage and integrations (24–39)

24. Provider-first candidates with bounded reranking using actual listening,
    completion, early skips, likes/dislikes, repeats, recency, artist affinity,
    blocks and decay. Frequent artists cannot overwhelm provider relevance.
25. Record play count, listened milliseconds, last play, completion, skips/early
    skips and likes. Three-second accidents are not equivalent to full listens.
26. Cold start uses YT Home/regional shelves, song radio and optional onboarding
    choices; do not fabricate personalization from insufficient evidence.
27. Latest Releases uses actual release feeds, favoring related/followed artists
    when signed in. Never relabel generic trending as new releases.
28. Downloads preserve IDs, title/artists/album/browse ID, artwork, duration,
    codec and download date. Local files outlive stream expiry; download state
    is separate from playback cache.
29. Local tracks remain first-class local entities. Local radio needs an explicit
    verified YT recording match before requesting radio.
30. Spotify import/sync strictly matches to YT IDs and displays unmatched tracks
    separately; no silent wrong-recording selection.
31. Optional lossless comes after reliable YT playback, matches title/artist/
    album/duration/version strictly, and falls back to the canonical YT recording.
32. Future quality upgrade starts YT quickly, searches in parallel and upgrades
    only an exact recording safely. Optional quality must not delay playback.
33. Separate metadata, artwork, signed-stream, playback-byte and download storage.
34. Prefetch next stream, optionally next-next and artwork; never resolve the
    entire long queue and waste expiring URLs/requests.
35. Extract coordinator, resolver, queue, session persistence, sleep timer, stats,
    widget publisher and Cast coordinator incrementally. Service retains session,
    notification, system commands, Auto and lifecycle responsibilities.
36. Foreground state corresponds to actual playback/activity; no idle
    `VYBE — Processing playback action...` notification.
37. Auto shares repositories/queue and cached Home, Quick Picks, likes, playlists,
    recently played and Library; no separate recommendation universe.
38. Wear clearly separates phone control from standalone playback; watch state
    must not become phone playback authority accidentally.
39. Cast/device handoff preserves canonical track, queue, position, repeat and
    shuffle where supported.

## Quality and release requirements (40–49)

40. Reusable VYBE artwork/rows/cards/shelves/surfaces/buttons/empty/loading/error
    components with shared spacing, radii, typography, timing, elevation, blur
    and artwork dimensions.
41. Purposeful motion for player/lyrics/entities/queue/like/transport/download;
    animation never delays interaction.
42. Meaningful cache/skeleton/suggestion/image placeholders, not blank loading pages.
43. Distinct offline, API, stream, expired account, regional/private restriction,
    parsing and timeout errors, with actionable recovery.
44. Separate low-frequency playback chrome from progress updates; ticks must not
    rebuild large Home/queue/player trees.
45. TalkBack, touch targets, contrast, dynamic text, appropriate landscape,
    tablets and practical reduced motion before release.
46. Settings sections: Account; Playback (crossfade, gapless, quality, ReplayGain,
    autoplay, data saver, timer defaults); Downloads (quality, Wi-Fi, storage);
    Appearance (theme, colors, player, motion); Library (local/rescan);
    Integrations (Spotify, optional Last.fm, Cast); Advanced (diagnostics/cache/
    logs); About (version/changelog/update/licenses). Search navigates to controls.
47. Optional diagnostics: video ID, resolver/client, codec/bitrate/sample rate,
    cache state, resolution time, stream age, buffer and network. No cookies or
    secrets, including sensitive signed URL query parameters.
48. Tests: song/album/artist and Punjabi/Hindi/Tamil/international search IDs and
    ordering; duplicate recordings; every queue mutation, autoplay, shuffle,
    repeat, crossfade and restore; provider prior/blocks/duplicates/skip/like
    effects; 403, expiry, timeout, rejection, NewPipe, network switch and expired
    URL seeking. Simulated failures belong in tests, not production data.
49. Every phase requires a successful build, unit tests, critical instrumented
    flows, regression checks and real-device evidence. Device flow: search → play
    → lock → seek → next/previous → queue/reorder → background → Bluetooth
    → return → network switch → resume. Compilation is not completion.

## Ordered delivery (50)

| Phase | Scope | Status |
|---|---|---|
| 0 | Freeze baseline, document issues, add critical characterization tests | In progress; gates open |
| 1 | Canonical identities and metadata propagation | In progress; persistence boundaries implemented, validation open |
| 2 | PlaybackCoordinator above preserved DualPlayerEngine | Not started |
| 3 | StreamResolver single-flight, health, validation, retries and diagnostics | Not started |
| 4 | Queue mutations and provider-first autoplay | Not started |
| 5 | Provider-ordered search, pagination and identities | Not started |
| 6 | Central progressive HomeFeedRepository | Not started |
| 7 | Provider-first signals and gentle personalization | Not started |
| 8 | UI system/navigation/loading/motion | Not started |
| 9 | Mini/full player, lyrics, queue and transitions | Not started |
| 10 | Artist/album/playlist/Library | Not started |
| 11 | Account synchronization | Not started |
| 12 | Local/download/Spotify import | Not started |
| 13 | Cast/Wear/Auto/widgets/voice | Not started |
| 14 | Optional lossless and quality upgrade | Not started |
| 15 | Performance/accessibility/tablets/final diagnostics | Not started |

## Decision protocol, safety and philosophy (51–54)

For technical uncertainty inspect VYBE and its callers/tests first, then the
exact BitChord and LastWave implementations. Compare correctness, reliability,
simplicity, performance, Media3 compatibility, Compose behavior, maintainability,
requirements and scalability. Prefer the simplest compatible pattern. Consult
official upstream guidance if none suffices. Resolve technical choices without
unnecessary user questions; ask only genuine ambiguous product decisions.

Major ADRs record problem, options, each project's actual behavior, choice,
reasons and tradeoffs. Search usages/dependencies before changes; compile, test,
inspect warnings and run real flows after major changes. No giant uncontrolled
rewrites, hidden errors, mock production data or silently disabled features.
Direct reuse requires licensing/attribution review; keep naming coherent.

YouTube Music knows what belongs together. VYBE plays that exact music quickly
and reliably, learns from real listening without fighting the provider graph,
and supplies a beautiful, powerful, integrated Android experience.
