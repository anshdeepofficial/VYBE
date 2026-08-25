filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# I need to remove the "Artist Preferences" block from LIBRARY
import re

# Remove the block:
# SettingsSubsection(title = "Artist Preferences") {
# ...
# }
pattern_library = r'SettingsSubsection\(title = "Artist Preferences"\) \{.*?SettingsItem\(\s*title = "Don\'t Suggest Artists".*?onClick = \{ navController.navigateSafely\(Screen.BlockedArtists.route\) \}\s*\)\s*\}'
content = re.sub(pattern_library, '', content, flags=re.DOTALL)

# Remove the block in ARTIST_RECOMMENDATIONS
pattern_artist_rec = r'SettingsCategory\.ARTIST_RECOMMENDATIONS -> \{.*?\}'
content = re.sub(pattern_artist_rec, 'SettingsCategory.ARTIST_RECOMMENDATIONS -> {\n                            // Moved to ArtistSettingsScreen\n                        }', content, flags=re.DOTALL)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
