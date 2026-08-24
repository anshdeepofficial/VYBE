package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsSubsection
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
fun ArtistRecommendationsSettings(
    settingsViewModel: SettingsViewModel,
    onManagePreferred: () -> Unit,
    onManageBlocked: () -> Unit
) {
    val preferredArtists by settingsViewModel.preferredArtists.collectAsStateWithLifecycle()
    val blockedArtists by settingsViewModel.blockedArtists.collectAsStateWithLifecycle()

    Column {
        SettingsSubsection(title = "Artist Preferences") {
            SettingsItem(
                title = "Preferred Artists",
                subtitle = "${preferredArtists.size} artists will be boosted in your mixes",
                leadingIcon = { Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.secondary) },
                onClick = onManagePreferred
            )
            SettingsItem(
                title = "Don't Suggest",
                subtitle = "${blockedArtists.size} artists will be excluded from mixes",
                leadingIcon = { Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.secondary) },
                onClick = onManageBlocked
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
