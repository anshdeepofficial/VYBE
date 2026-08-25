filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/ArtistSettingsScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'androidx.compose.material.icons.Icons.Outlined.Person',
    'androidx.compose.material.icons.Icons.Outlined.Person'
)

# wait, I can just replace `androidx.compose.material.icons.Icons.Outlined.Block` with `androidx.compose.material.icons.Icons.Rounded.Block`? No, Block is not an object, it's a property extension on Outlined.
# So I must add imports at the top:
imports = """import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.rounded.ChevronRight
"""

if 'import androidx.compose.material.icons.outlined.Block' not in content:
    content = content.replace('import androidx.compose.material.icons.Icons', imports + 'import androidx.compose.material.icons.Icons')
    
content = content.replace('androidx.compose.material.icons.Icons.Outlined.Person', 'Icons.Outlined.Person')
content = content.replace('androidx.compose.material.icons.Icons.Outlined.Block', 'Icons.Outlined.Block')
content = content.replace('androidx.compose.material.icons.Icons.Rounded.ChevronRight', 'Icons.Rounded.ChevronRight')

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

