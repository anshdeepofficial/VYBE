# VYBE implementation rules

Read `docs/architecture/VYBE_2_MASTER_PLAN.md` and
`docs/architecture/PHASE_0_BASELINE.md` before changing the Android application.
The user's VYBE master implementation plan is the product specification; these
files are its repository execution guide, not permission to weaken it.

- Implement phases 0–15 in order, in small reviewable changes. Never mark a
  phase complete from compilation alone. Record build, unit, instrumentation,
  regression and real-device evidence separately; unavailable checks remain open.
- Before changing an important component, search every usage, read dependent
  classes and tests, and identify compatibility requirements.
- Resolve technical uncertainties by inspecting VYBE, then the relevant actual
  BitChord and LastWave implementations. Compare correctness, reliability,
  simplicity, performance, Media3/Compose compatibility, maintainability and
  VYBE requirements. If insufficient, consult official Android, Media3, Compose
  or upstream documentation. Record major decisions in an ADR. Ask the user
  only about unresolved product choices, not routine technical choices.
- YouTube Music is the online catalogue authority. Preserve native entity IDs
  through search, playback, persistence, downloads and integrations. Do not
  rediscover by title/artist when a video or browse ID is available. Signed
  stream URLs are transport, never identity. Local music retains local identity.
- Preserve provider search ordering. Recommendations start with provider
  candidates and only gentle local adjustments. Do not silently substitute
  Saavn, Netease, QQ or Spotify catalogue entries into normal discovery.
- Preserve DualPlayerEngine and existing VYBE features. One canonical playback
  and queue authority must sit above its private physical players. Manual queue
  edits must survive shuffle, autoplay refresh and crossfade.
- Use real provider data in production. Deterministic isolated test fixtures
  are test inputs, never a substitute for live validation or production data.
- Do not suppress errors, disable difficult features or perform giant rewrites
  to satisfy checks. Compile, test, inspect warnings and exercise relevant flows
  after major changes. Keep secrets, cookies and signed URLs out of diagnostics.
- Study reference architecture; do not blindly copy source. Any direct reuse
  requires checking and preserving licensing and attribution obligations.

Reference repositories:
- https://github.com/kushagrasinghx/BitChord
- https://github.com/Clash-Projects/LastWave-Native
