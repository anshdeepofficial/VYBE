filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# I will just remove the whole ARTIST_RECOMMENDATIONS block or the Artist Preferences from LIBRARY?
# In SettingsCategoryScreen, there is `SettingsCategory.ARTIST_RECOMMENDATIONS` which uses `ArtistRecommendationsSettings`.
# But previously we saw it was under LIBRARY. No, it was under `ARTIST_RECOMMENDATIONS`! Wait, let me check where `ArtistRecommendationsSettings` is.

