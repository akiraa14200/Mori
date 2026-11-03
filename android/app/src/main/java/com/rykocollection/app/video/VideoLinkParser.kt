package com.rykocollection.app.video

import java.net.URI
import java.net.URLDecoder

object VideoLinkParser {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    fun youtubeVideoId(rawUrl: String): String? {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != "youtube.com" && host != "m.youtube.com" && host != "youtu.be") return null

        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val candidate = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> uri.rawQuery.orEmpty()
                .split('&')
                .firstOrNull { it.startsWith("v=") }
                ?.substringAfter("v=")
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            segments.firstOrNull()?.let { it in setOf("shorts", "embed", "live") } == true -> segments.getOrNull(1)
            else -> null
        }
        return candidate?.takeIf(idPattern::matches)
    }

    fun youtubeThumbnail(rawUrl: String): String? =
        youtubeVideoId(rawUrl)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
}
