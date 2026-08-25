import re

with open('app/src/main/java/com/theveloper/pixelplay/data/github/GitHubUpdateService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Need to add imports
imports = """
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.theveloper.pixelplay.R
"""

content = content.replace('import android.content.Context', imports + '\nimport android.content.Context')

download_func_pattern = r'suspend fun download\(\s*context: Context,\s*update: GitHubReleaseUpdate,\s*onProgress: \(Float\) -> Unit,\s*\): Result<File> = withContext\(Dispatchers\.IO\) \{'

replacement = '''suspend fun download(
        context: Context,
        update: GitHubReleaseUpdate,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        
        val channelId = "vybe_update_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val notificationId = 10001
        val notificationManager = NotificationManagerCompat.from(context)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle("Downloading VYBE Update")
            .setContentText(update.apkName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setProgress(100, 0, false)
'''

content = re.sub(download_func_pattern, replacement, content, count=1)

# Now we need to update the progress inside the loop
# We search for:
# while (input.read(buffer).also { bytes = it } >= 0) { ... }

loop_replacement = '''
            var lastUpdatePercent = -1
            while (input.read(buffer).also { bytes = it } >= 0) {
                out.write(buffer, 0, bytes)
                downloaded += bytes
                if (total > 0) {
                    val progress = (downloaded.toFloat() / total) * 100f
                    val currentPercent = progress.toInt()
                    if (currentPercent != lastUpdatePercent) {
                        lastUpdatePercent = currentPercent
                        builder.setProgress(100, currentPercent, false)
                        runCatching { notificationManager.notify(notificationId, builder.build()) }
                    }
                    onProgress(progress)
                }
            }
'''

content = re.sub(r'while \(input\.read\(buffer\)\.also \{ bytes = it \} >= 0\) \{[^\}]+\}', loop_replacement.strip(), content)

# Finally, when it completes or fails, we should dismiss the notification.
# Search for: `target` (returned result)
# We can just dismiss it in a finally block? Wait, we have `runCatching` block.

runCatching_close = r'target\n        \}'
runCatching_replacement = '''target
        }.onSuccess {
            runCatching { notificationManager.cancel(notificationId) }
        }.onFailure {
            runCatching { notificationManager.cancel(notificationId) }
        }'''
content = re.sub(runCatching_close, runCatching_replacement, content)

with open('app/src/main/java/com/theveloper/pixelplay/data/github/GitHubUpdateService.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# Now, we should also update YouTubeDownloadManager.kt to make its notifications IMPORTANCE_DEFAULT and PRIORITY_DEFAULT.
with open('app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YouTubeDownloadManager.kt', 'r', encoding='utf-8') as f:
    yt_content = f.read()

yt_content = yt_content.replace('NotificationManager.IMPORTANCE_LOW,', 'NotificationManager.IMPORTANCE_DEFAULT,')
yt_content = yt_content.replace('.setPriority(NotificationCompat.PRIORITY_LOW)', '.setPriority(NotificationCompat.PRIORITY_DEFAULT)')
with open('app/src/main/java/com/theveloper/pixelplay/data/network/ytmusic/YouTubeDownloadManager.kt', 'w', encoding='utf-8') as f:
    f.write(yt_content)
