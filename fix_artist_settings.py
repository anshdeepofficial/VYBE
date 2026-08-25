filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/ArtistSettingsScreen.kt'
import re
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

new_block = """            // Artist Preferences
            item {
                Spacer(modifier = Modifier.height(16.dp))
                com.theveloper.pixelplay.presentation.screens.SettingsSubsection(title = "Artist Preferences") {
                    com.theveloper.pixelplay.presentation.screens.SettingsItem(
                        title = "Preferred Artists",
                        subtitle = "Prioritize these artists in Mixes and Autoplay",
                        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Person, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary) },
                        trailingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, "Open", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { navController.navigateSafely(com.theveloper.pixelplay.presentation.navigation.Screen.PreferredArtists.route) }
                    )
                    com.theveloper.pixelplay.presentation.screens.SettingsItem(
                        title = "Don't Suggest Artists",
                        subtitle = "Exclude these artists from recommendations",
                        leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Block, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary) },
                        trailingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, "Open", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { navController.navigateSafely(com.theveloper.pixelplay.presentation.navigation.Screen.BlockedArtists.route) }
                    )
                }
            }

"""

if "Artist Preferences" not in content:
    content = content.replace('            item {', new_block + '            item {', 1)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
