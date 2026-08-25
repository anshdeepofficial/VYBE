import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\viewmodel\SleepTimerStateHolder.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

timer_coroutine = """
        // Start live countdown coroutine
        sleepTimerJob = scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            while (true) {
                val remaining = _sleepTimerEndTimeMillis.value?.minus(System.currentTimeMillis()) ?: 0L
                if (remaining <= 0) {
                    cancelSleepTimer(suppressDefaultToast = true)
                    break
                }
                val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(remaining)
                val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                _activeTimerValueDisplay.value = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                kotlinx.coroutines.delay(1000)
            }
        }

        // Schedule alarm for reliable triggering
"""

content = content.replace('// Schedule alarm for reliable triggering', timer_coroutine)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
