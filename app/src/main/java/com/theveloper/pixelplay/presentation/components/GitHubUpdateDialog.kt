package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.github.GitHubReleaseUpdate
import java.io.File

@Composable
fun GitHubUpdateDialog(
    update: GitHubReleaseUpdate,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadedFile: File?,
    message: String?,
    onDownloadOrInstall: () -> Unit,
    onDismiss: () -> Unit,
    onRemindLater: (Long) -> Unit = { onDismiss() },
    onSkipVersion: () -> Unit = onDismiss,
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text("VYBE update available", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${update.title} (${update.tagName})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (update.notes.isNotBlank()) {
                    Text(
                        text = update.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                    )
                }
                if (isDownloading) {
                    Text("Downloading ${(downloadProgress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                message?.let {
                    Text(
                        text = it,
                        color = if (downloadedFile == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadOrInstall,
                enabled = !isDownloading,
            ) {
                Text(if (downloadedFile == null) "Download update" else "Install update")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onRemindLater(60L * 60L * 1_000L) }, enabled = !isDownloading) {
                    Text("Remind in 1h")
                }
                TextButton(onClick = { onRemindLater(24L * 60L * 60L * 1_000L) }, enabled = !isDownloading) {
                    Text("Tomorrow")
                }
                TextButton(onClick = onSkipVersion, enabled = !isDownloading) {
                    Text("Skip")
                }
            }
        },
    )
}
