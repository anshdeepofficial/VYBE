## VYBE 0.11.8

### What's Changed
- **Pure Audio Player**: Completely removed video playback, video surfaces, and floating video toggles from the player. VYBE is now a pure audio player.
- **YouTube Music Songs Only**: Strict filtering prevents YouTube videos and official music videos (OMVs) from appearing in search results or autoplay. All queries strictly hit YouTube Music endpoints with songs filter.
- **1–2s Fast Home Refresh**: Parallelized concurrent requests with bounded 1.8-second timeouts ensure home refresh finishes in 1–2 seconds.
- **Persistent Synced Library Stats**: Real, persistent item counts for Library, Liked, History, and Playlists that persist reliably across app restarts.
- **Accounts Screen Streamlined**: Removed top "Connected Accounts" hero section; "Linked Service" appears first, followed by "Backup".
- **Search Screen Crash Fix**: Resolved SpeechRecognizer and Dagger dependency initialization issues that caused crashes when opening Search.
