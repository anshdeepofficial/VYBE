package com.theveloper.pixelplay.data.sharing

import android.net.Uri

object MusicLinkParser {
    
    fun parseExternalMusicLink(text: String): String? {
        val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
        val url = urlRegex.find(text)?.value ?: return null
        
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return null
            
            if (host.contains("youtube.com") || host.contains("music.youtube.com")) {
                val videoId = uri.getQueryParameter("v")
                if (!videoId.isNullOrBlank()) {
                    return "yt_$videoId"
                }
            } else if (host == "youtu.be") {
                val videoId = uri.lastPathSegment
                if (!videoId.isNullOrBlank()) {
                    return "yt_$videoId"
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
