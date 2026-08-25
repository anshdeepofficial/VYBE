import re

# Update version in gradle.properties
with open('gradle.properties', 'r') as f:
    gradle = f.read()
gradle = re.sub(r'VERSION_NAME=.*', 'VERSION_NAME=0.8.4', gradle)
with open('gradle.properties', 'w') as f:
    f.write(gradle)

# Update ChangelogScreen.kt
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'r') as f:
    changelog_kt = f.read()

new_changelog_item = """
            item {
                ChangelogVersion(
                    version = "v0.8.4",
                    date = "August 2026",
                    changes = listOf(
                        "Added 'Report Bug or Suggest Feature' in About screen (GitHub Issues)",
                        "Moved Artist Preferences out of Music Settings to Artist Settings",
                        "Enabled full Management Screen (Search/Add/Remove) for Preferred/Blocked Artists"
                    )
                )
            }"""

changelog_kt = changelog_kt.replace('        LazyColumn(\n            contentPadding = PaddingValues(16.dp),\n            verticalArrangement = Arrangement.spacedBy(16.dp)\n        ) {', '        LazyColumn(\n            contentPadding = PaddingValues(16.dp),\n            verticalArrangement = Arrangement.spacedBy(16.dp)\n        ) {' + new_changelog_item)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'w') as f:
    f.write(changelog_kt)

# Update CHANGELOG.md
with open('CHANGELOG.md', 'r') as f:
    changelog_md = f.read()

new_md = """## [0.8.4] - 2026-08-25
### Added
- Added 'Report Bug or Suggest Feature' in About screen which links directly to GitHub Issues.
### Changed
- Moved 'Preferred Artists' and 'Don't Suggest Artists' from Music Settings to Artist Settings.
- You can now properly manage, search, add, and remove your preferred and blocked artists to boost or suppress their recommendations in the app.

"""
changelog_md = new_md + changelog_md
with open('CHANGELOG.md', 'w') as f:
    f.write(changelog_md)

