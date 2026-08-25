filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if 'SettingsSubsection(title = "Artist Preferences")' in line:
        skip = True
    if skip and 'SettingsSubsection(title = stringResource(R.string.settings_library_structure_section))' in line:
        skip = False
    
    if 'SettingsCategory.ARTIST_RECOMMENDATIONS -> {' in line:
        new_lines.append(line)
        new_lines.append('                            // Moved to ArtistSettingsScreen\n')
        skip = True
        continue
    
    if skip and 'SettingsCategory.DEVELOPER -> {' in line:
        skip = False

    if not skip:
        new_lines.append(line)

with open(filepath, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
