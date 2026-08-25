filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt'
import re
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

pattern_library = r'SettingsSubsection\(title = "Artist Preferences"\) \{.*?onClick = \{ navController.navigateSafely\(Screen.BlockedArtists.route\) \}\s*\)\s*\}'
content = re.sub(pattern_library, '', content, flags=re.DOTALL)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
