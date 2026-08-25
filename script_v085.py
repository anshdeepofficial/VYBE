import re

# Update version in gradle.properties
with open('gradle.properties', 'r') as f:
    gradle = f.read()
gradle = re.sub(r'APP_VERSION_NAME=.*', 'APP_VERSION_NAME=0.8.5', gradle)
with open('gradle.properties', 'w') as f:
    f.write(gradle)

# Update ChangelogScreen.kt
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'r') as f:
    changelog_kt = f.read()

new_changelog_item = """
            item {
                ChangelogVersion(
                    version = "v0.8.5",
                    date = "August 2026",
                    changes = listOf(
                        "Fixed Update service strictly checking version codes (fixes 'update not newer' bug)",
                        "Fixed Settings Screen crash"
                    )
                )
            }"""

changelog_kt = changelog_kt.replace('        LazyColumn(\n            contentPadding = PaddingValues(16.dp),\n            verticalArrangement = Arrangement.spacedBy(16.dp)\n        ) {', '        LazyColumn(\n            contentPadding = PaddingValues(16.dp),\n            verticalArrangement = Arrangement.spacedBy(16.dp)\n        ) {' + new_changelog_item)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'w') as f:
    f.write(changelog_kt)

# Update CHANGELOG.md
with open('CHANGELOG.md', 'r') as f:
    changelog_md = f.read()

new_md = """## [0.8.5] - 2026-08-25
### Fixed
- Fixed the 'update is not newer than installed version' error which happens when a small hotfix is deployed without a major version bump.
- Fixed a crash in Settings category screen.

"""
changelog_md = new_md + changelog_md
with open('CHANGELOG.md', 'w') as f:
    f.write(changelog_md)

