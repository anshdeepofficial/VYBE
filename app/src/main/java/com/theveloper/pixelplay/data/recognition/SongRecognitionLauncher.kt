package com.theveloper.pixelplay.data.recognition

import android.content.Intent
import android.speech.RecognizerIntent

/** Returns recognition text to VYBE instead of opening a Google results page. */
object SongRecognitionLauncher {
    fun createIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the song title or artist")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }

    fun results(intent: Intent?): List<String> =
        intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
}
