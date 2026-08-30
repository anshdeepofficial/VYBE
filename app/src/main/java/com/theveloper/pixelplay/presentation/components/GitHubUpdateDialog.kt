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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var showReminderMenu by remember { mutableStateOf(false) }
    var showCustomReminder by remember { mutableStateOf(false) }
    var customHours by remember { mutableStateOf("1") }
    val totalSize = formatBytes(update.apkSizeBytes)
    val downloadedSize = formatBytes((update.apkSizeBytes * downloadProgress.coerceIn(0f, 1f)).toLong())
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
                    Text("Downloading $downloadedSize of $totalSize (${(downloadProgress * 100).toInt()}%)")
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!isDownloading && update.apkSizeBytes > 0L) Text("APK size: $totalSize")
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
                androidx.compose.foundation.layout.Box {
                    TextButton(onClick = { showReminderMenu = true }, enabled = !isDownloading) { Text("Remind in 1h") }
                    DropdownMenu(expanded = showReminderMenu, onDismissRequest = { showReminderMenu = false }) {
                        listOf(1, 2, 3, 4, 6, 12, 24).forEach { hours ->
                            DropdownMenuItem(
                                text = { Text(if (hours == 1) "1 hour" else "$hours hours") },
                                onClick = { showReminderMenu = false; onRemindLater(hours * 60L * 60L * 1_000L) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Custom…") },
                            onClick = { showReminderMenu = false; showCustomReminder = true },
                        )
                    }
                }
                TextButton(onClick = onSkipVersion, enabled = !isDownloading) {
                    Text("Skip")
                }
            }
        },
    )
    if (showCustomReminder) {
        AlertDialog(
            onDismissRequest = { showCustomReminder = false },
            title = { Text("Custom reminder") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = customHours,
                    onValueChange = { value -> customHours = value.filter(Char::isDigit).take(3) },
                    label = { Text("Hours") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val hours = customHours.toLongOrNull()?.coerceIn(1L, 720L) ?: 1L
                    showCustomReminder = false
                    onRemindLater(hours * 60L * 60L * 1_000L)
                }) { Text("Set reminder") }
            },
            dismissButton = { TextButton(onClick = { showCustomReminder = false }) { Text("Cancel") } },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "Unknown"
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}
