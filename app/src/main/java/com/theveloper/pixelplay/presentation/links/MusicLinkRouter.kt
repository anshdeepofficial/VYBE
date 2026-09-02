package com.theveloper.pixelplay.presentation.links

import android.content.Intent
import com.theveloper.pixelplay.data.recognition.SocialReelAudioRecognizer
import com.theveloper.pixelplay.data.sharing.MusicLinkParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicLinkRouter @Inject constructor(
    private val socialReelAudioRecognizer: SocialReelAudioRecognizer,
) {

    fun parseIntent(intent: Intent): String? {
        val action = intent.action
        val data = intent.data

        if (action == Intent.ACTION_VIEW && data != null) {
            val url = data.toString()
            return MusicLinkParser.parseExternalMusicLink(url)
        }

        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                return MusicLinkParser.parseExternalMusicLink(text)
            }
        }

        return null
    }

    fun parseSocialReelUrl(intent: Intent): String? {
        val action = intent.action
        val data = intent.data

        if (action == Intent.ACTION_VIEW && data != null) {
            val url = data.toString()
            if (socialReelAudioRecognizer.isSocialReelUrl(url)) return url
        }

        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank() && socialReelAudioRecognizer.isSocialReelUrl(text)) {
                return text
            }
        }

        return null
    }
}
