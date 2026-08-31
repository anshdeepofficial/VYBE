package com.theveloper.pixelplay.data.sharing

import android.net.Uri

object MusicLinkParser {
    fun parseExternalMusicLink(text: String): String? {
        val trimmed = text.trim()
        val url = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed.substringBefore(' ').substringBefore('\n')
        } else {
            Regex("""https?://[^\s<>]+""", RegexOption.IGNORE_CASE).find(text)?.value
        } ?: return null

        return runCatching {
            val uri = Uri.parse(url)
            when (uri.host?.lowercase()) {
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                    uri.getQueryParameter("v")?.trim()?.takeIf { it.isNotBlank() }?.take(64)?.let { "yt_$it" }
                "youtu.be" ->
                    uri.lastPathSegment?.trim()?.takeIf { it.isNotBlank() }?.take(64)?.let { "yt_$it" }
                else -> null
            }
        }.getOrNull()
    }
}
