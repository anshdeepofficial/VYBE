with open('CHANGELOG.md', 'r', encoding='utf-8') as f:
    content = f.read()

new_fixed = """- **Download Notifications**: Both song downloads and app updates now clearly display in the status bar notifications area.
- **Playback Processing Notification**: Eliminated the persistent "Processing playback action" notification that could get stuck when playback paused.
- **Digital Sleep Timer Clock**: Fixed an issue where the live digital countdown clock was missing from the player controls."""

content = content.replace('### Fixed\n- **Immersive Artwork Refinement', '### Fixed\n' + new_fixed + '\n- **Immersive Artwork Refinement')
with open('CHANGELOG.md', 'w', encoding='utf-8') as f:
    f.write(content)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'r', encoding='utf-8') as f:
    kt_content = f.read()

kt_fixed = """                    "Removed stuck 'Processing playback action' notification.",
                    "Download progress (songs and updates) now clearly visible in status bar.",
                    "Restored the live digital countdown display to the Sleep Timer button.",
                    "Refined Immersive Artwork"""
kt_content = kt_content.replace('"Refined Immersive Artwork', kt_fixed)
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/ChangelogScreen.kt', 'w', encoding='utf-8') as f:
    f.write(kt_content)
