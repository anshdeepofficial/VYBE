with open('CHANGELOG.md', 'r', encoding='utf-8') as f:
    content = f.read()

new_log = """## [0.8.2]
### Added
- **Library Pull-to-Refresh Metadata Fix**: Refreshing library tabs like Liked Songs and Playlists now actively resolves and restores "Online Track" placeholder metadata permanently.
- **Advanced Timer Controls**: Reinstated full timer controls with dedicated buttons for Custom Time, Cancel, and Apply, giving users maximum flexibility.

### Fixed
- **Immersive Artwork Refinement**: The Immersive Artwork feature has been refined to keep the original album art centered and perfectly visible, while extending a beautifully blurred background with proper top and bottom shading for text readability.

"""
content = content.replace("## [0.8.1]", new_log + "## [0.8.1]")
with open('CHANGELOG.md', 'w', encoding='utf-8') as f:
    f.write(content)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'r', encoding='utf-8') as f:
    kt_content = f.read()

new_kt_log = """        item {
            ChangelogVersionBlock(
                version = "0.8.2",
                added = listOf(
                    "Library pull-to-refresh now actively resolves and fixes 'Online Track' metadata issues.",
                    "Reinstated full timer controls with Custom Time, Cancel, and Apply buttons."
                ),
                fixed = listOf(
                    "Refined Immersive Artwork to keep original art centered with a properly shaded blurred background."
                )
            )
        }
"""
kt_content = kt_content.replace('item {\n            ChangelogVersionBlock(\n                version = "0.8.1",', new_kt_log + '        item {\n            ChangelogVersionBlock(\n                version = "0.8.1",')
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'w', encoding='utf-8') as f:
    f.write(kt_content)

with open('gradle.properties', 'r', encoding='utf-8') as f:
    gradle = f.read()

import re
gradle = re.sub(r'APP_VERSION_NAME=.*', 'APP_VERSION_NAME=0.8.2', gradle)
gradle = re.sub(r'APP_VERSION_CODE=.*', lambda m: f"APP_VERSION_CODE={int(m.group(0).split('=')[1])+1}", gradle)

with open('gradle.properties', 'w', encoding='utf-8') as f:
    f.write(gradle)
