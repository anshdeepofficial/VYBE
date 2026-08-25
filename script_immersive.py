import re

with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove the main artwork part from ImmersiveArtworkBackground
main_artwork_pattern = r'AnimatedContent\(\s*targetState = song,\s*contentKey = \{ "main_\$\{it.id\}" \}.*?label = "ImmersiveMainArtworkTransition",\s*\) \{ current ->.*?\}\s*\}\s*Box\(\s*modifier = Modifier\s*\.fillMaxSize\(\)'

replacement = 'Box(\n            modifier = Modifier\n                .fillMaxSize()'
content = re.sub(main_artwork_pattern, replacement, content, flags=re.DOTALL)

# 2. Fix alpha in FullPlayerAlbumCoverSection
alpha_pattern = r'alpha = if \(immersiveArtworkEnabled\) 0f else 1f'
# Remove this line by replacing it with nothing or replacing the scaleX, scaleY part to just drop alpha
content = re.sub(r'scaleY = albumArtScale\s+alpha = if \(immersiveArtworkEnabled\) 0f else 1f', r'scaleY = albumArtScale', content)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt', 'w', encoding='utf-8') as f:
    f.write(content)
