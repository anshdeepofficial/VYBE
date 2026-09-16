# ADR 0001: characterize existing playback before migrating ownership

Status: accepted implementation approach; Phase 0 validation is still open.

## Problem

VYBE needs one canonical playback/queue authority without losing crossfade,
saved queues, local music or platform integrations. Replacing production state
before establishing regression evidence makes failures difficult to attribute.

## Options and evidence

1. Replace service/player/queue together. Rejected: too broad to validate against
   current tests, and incompatible with the user's incremental safety rule.
2. Immediately copy a reference project's player. Rejected: reference state and
   queue semantics are not identical to VYBE's, and source reuse has licensing
   obligations. BitChord's `QueueBuilder` also lacks the requested duration guard.
3. Preserve runtime behavior, add characterization and comparative CI, then
   migrate behind tested boundaries. Selected.

At the SHAs in `PHASE_0_BASELINE.md`, VYBE has a private A/B engine, a separate
unshuffle snapshot and two distinct saved queue formats. BitChord's queue tests
protect repeated entries and restoration; its resolver demonstrates cancellation-
safe coalescing. LastWave's MusicPlayer exposes canonical state and separate
chrome/progress projections, and FeedRepository owns cached feed orchestration.

## Decision

Add tests for existing queue invariants and serialized compatibility; record
source gaps separately from device-reproduced defects. Run baseline and candidate
with the same CI build/test command. Preserve DualPlayerEngine, production
repositories and UI during this step. Future coordinator/resolver/feed decisions
need their own caller/test/reference review and ADRs.

This is the simplest change with low runtime risk: no extra player or state
authority, no new high-frequency Compose flow and no Media3 command changes.
Tests and recorded contracts improve maintainability without claiming scalability
or playback reliability improvements before they are implemented and measured.

## Tradeoffs and gates

Known catalogue/autoplay/metadata gaps remain until their ordered phases. The
additional CI baseline build costs runner time but distinguishes pre-existing
failures. Local Gradle download is blocked; no test-pass or device claim is made.
All five user release gates remain binding. CI success alone is insufficient.

No reference source code is copied into production or tests by this change.
