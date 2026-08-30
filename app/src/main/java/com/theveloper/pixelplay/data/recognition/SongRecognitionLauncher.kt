package com.theveloper.pixelplay.data.recognition

import android.content.Context
import android.content.Intent

/** Uses the device's installed music-recognition provider; VYBE needs no user API key. */
object SongRecognitionLauncher {
    private val actions = listOf(
        "com.google.android.googlequicksearchbox.MUSIC_SEARCH",
        "android.intent.action.MUSIC_SEARCH",
    )

    fun launch(context: Context): Boolean {
        for (action in actions) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // Try the next provider available on this device.
            }
        }
        return false
    }
}
