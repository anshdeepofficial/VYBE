import re

# Update version in gradle.properties
with open('gradle.properties', 'r') as f:
    gradle = f.read()
gradle = re.sub(r'VERSION_NAME=.*', 'VERSION_NAME=0.8.3', gradle)
with open('gradle.properties', 'w') as f:
    f.write(gradle)

# Update ChangelogScreen.kt
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'r') as f:
    changelog_kt = f.read()

new_changelog_item = """
            item {
                ChangelogVersion(
                    version = "v0.8.3",
                    date = "August 2026",
                    changes = listOf(
                        "Fixed Audio Focus auto-resume bug when manually paused",
                        "Updated VYBE share link format to standard https://music.vybe.app",
                        "Minor stability and performance fixes"
                    )
                )
            }"""

changelog_kt = changelog_kt.replace('        LazyColumn(\n            contentPadding = PaddingValues(16.dp),\n            verticalArrangement = Arrangement.spacedBy(16.dp)\n        ) {', '        LazyColumn(\n            contentPadding = PaddingValues(16.dp),\n            verticalArrangement = Arrangement.spacedBy(16.dp)\n        ) {' + new_changelog_item)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'w') as f:
    f.write(changelog_kt)

# Update CHANGELOG.md
with open('CHANGELOG.md', 'r') as f:
    changelog_md = f.read()

new_md = """## [0.8.3] - 2026-08-25
### Fixed
- Fixed Audio Focus auto-resume bug (app will no longer auto-resume if you manually pause it before a phone call/notification).
- Updated VYBE Share Link format to use `https://music.vybe.app/watch?v=ID` layout with backwards compatibility.

"""
changelog_md = new_md + changelog_md
with open('CHANGELOG.md', 'w') as f:
    f.write(changelog_md)

