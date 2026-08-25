with open('CHANGELOG.md', 'r', encoding='utf-8') as f:
    content = f.read()

new_log = """## [0.8.1]
### Added
- **Sleep Timer Countdown**: The sleep timer now displays a live digital countdown on the player screen.
- **Spotify Importer UI**: Replaced the Spotify account login with a simpler Playlist Importer interface.

### Fixed
- **Sleep Timer Options**: Replaced the complex Play Count feature with a straightforward Apply Timer button in the timer options.
- **Spotify Login Error**: Removed the `SPOTIFY_CLIENT_ID` requirement that was causing login errors.

"""
content = content.replace("## [0.8.0]", new_log + "## [0.8.0]")
with open('CHANGELOG.md', 'w', encoding='utf-8') as f:
    f.write(content)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'r', encoding='utf-8') as f:
    kt_content = f.read()

new_kt_log = """        item {
            ChangelogVersionBlock(
                version = "0.8.1",
                added = listOf(
                    "Sleep Timer now displays a live digital countdown.",
                    "Replaced Spotify Account login with a simpler Playlist Importer UI."
                ),
                fixed = listOf(
                    "Removed complex Play Count feature in favor of a straightforward Apply Timer button.",
                    "Removed the SPOTIFY_CLIENT_ID requirement that caused login errors."
                )
            )
        }
"""
kt_content = kt_content.replace('item {\n            ChangelogVersionBlock(\n                version = "0.8.0",', new_kt_log + '        item {\n            ChangelogVersionBlock(\n                version = "0.8.0",')
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'w', encoding='utf-8') as f:
    f.write(kt_content)
