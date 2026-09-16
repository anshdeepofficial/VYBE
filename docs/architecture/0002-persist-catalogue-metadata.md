# ADR 0002: preserve catalogue metadata at existing transport boundaries

Status: implemented in draft; build, migration execution and device gates open.

## Problem and comparison

VYBE's Song already carries album and artist browse IDs, but saved queues,
MediaItem extras, process-death snapshots and online/download Room rows lose
them. Replacing Song throughout the UI would combine an identity migration with
a much wider runtime rewrite.

Inspected at the pinned baseline revisions:
- VYBE: Song, ArtistRef, SavedPlaybackQueue, PlaybackQueueSnapshot, MediaItemBuilder,
  MediaMapper, MusicService snapshot methods, online/download DAOs and their callers.
- BitChord: Models.kt retains native video/album/artist IDs; PlayerConnection.kt
  serializes them into Media3 extras and reads them back.
- LastWave: PlayableTrack/PersistedPlaybackSession retain video ID and serialize
  queue state, but do not provide VYBE's multi-artist/album browse metadata model.

## Decision

Use one serializable SongCatalogMetadata payload at existing boundaries, with
identity-bound application (song IDs must match). Preserve every artist browse
ID, album browse ID, album artist, release timestamp, version flag and track/disc/
year values. URLs remain outside this payload. Keep existing Song and Media3 IDs.

Saved queue and snapshot fields default to null for older serialized data. Media3
uses a namespaced string extra, avoiding custom Parcelable class-loader issues.
Room migration 45→46 adds nullable metadata columns without rewriting/deleting
existing rows. Download restoration preserves the local path as playback URI.
Unknown JSON fields are tolerated; malformed optional metadata falls back to
the existing playable row rather than making a whole queue unreadable.

This is smaller than replacing every domain model, works with existing Media3
callers and does not introduce Compose state, player ownership or queue changes.
Tradeoff: metadata JSON is repeated per queue item/row and is not queryable as
separate Room columns. Old rows cannot magically recover IDs never saved; fresh
provider metadata must repopulate them. Typed IDs, explicit metadata and the full
catalogue lifecycle audit remain Phase 1 follow-ups.

## Validation

Added seven JVM tests for saved/snapshot/cache/download round trips, wrong-ID
rejection, malformed payload fallback and forward-compatible fields. Added an
instrumented Media3/Bundle round trip and a populated-row 45→46 migration test;
extended the existing migration sweep to 46. Room must generate/export schema 46
and migration tests must pass before merging. No execution pass is claimed yet.

Build distribution now uses the same official services.gradle.org host used by
both reference wrappers, retaining VYBE's version and SHA-256 check. See
[Gradle Wrapper documentation](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
The local environment still reports Network is unreachable; CI is the available
build route. No production source was copied from either reference project.
