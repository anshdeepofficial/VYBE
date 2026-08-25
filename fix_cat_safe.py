filepath = 'app/src/main/java/com/theveloper/pixelplay/presentation/screens/SettingsCategoryScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

bad1 = """                            SettingsSubsection(title = "Artist Preferences") {
                                SettingsItem(
                                    title = "Preferred Artists",
                                    subtitle = "Prioritize these artists in Mixes and Autoplay",
                                    leadingIcon = { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.settings_cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely(Screen.PreferredArtists.route) }
                                )
                                SettingsItem(
                                    title = "Blocked Artists",
                                    subtitle = "Exclude these artists from recommendations",
                                    leadingIcon = { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.settings_cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely(Screen.BlockedArtists.route) }
                                )
                            }"""

bad2 = """                        SettingsCategory.ARTIST_RECOMMENDATIONS -> {
                            com.theveloper.pixelplay.presentation.screens.settings.ArtistRecommendationsSettings(
                                settingsViewModel = settingsViewModel,
                                onManagePreferred = {
                                    // TODO: Navigate to Preferred Artists selection screen
                                },
                                onManageBlocked = {
                                    // TODO: Navigate to Blocked Artists selection screen
                                }
                            )
                        }"""

good2 = """                        SettingsCategory.ARTIST_RECOMMENDATIONS -> {
                            // Moved to ArtistSettingsScreen
                        }"""

content = content.replace(bad1, "")
content = content.replace(bad2, good2)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

