package com.rykocollection.app.video

import java.net.URI
import java.net.URLDecoder

/**
 * Strict YouTube URL parser shared by thumbnail generation and playback.
 * Accepts canonical, short, mobile, no-cookie, Shorts, live and embed URLs.
 */
object VideoLinkParser {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val supportedHosts = setOf(
        "youtube.com",
        "m.youtube.com",
        "www.youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
        "m.youtube-nocookie.com"
    )

    fun youtubeVideoId(rawUrl: String): String? {
        val value = rawUrl.trim()
        if (value.isEmpty()) return null
        val normalized = if (value.startsWith("www.", true) ||
            value.startsWith("youtube.com", true) ||
            value.startsWith("youtu.be", true) ||
            value.startsWith("m.youtube.com", true) ||
            value.startsWith("youtube-nocookie.com", true)
        ) "https://$value" else value
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host !in supportedHosts.map { it.removePrefix("www.") }) return null

        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val candidate = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> queryParameter(uri.rawQuery, "v")
            segments.firstOrNull() in setOf("shorts", "embed", "live", "v") -> segments.getOrNull(1)
            else -> null
        }?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it }

        return candidate?.takeIf(idPattern::matches)
    }

    private fun queryParameter(rawQuery: String?, name: String): String? =
        rawQuery.orEmpty().split('&').firstNotNullOfOrNull { part ->
            if (part.substringBefore('=') == name) part.substringAfter('=', "").takeIf(String::isNotBlank)
            else null
        }

    fun youtubeThumbnail(rawUrl: String): String? =
        youtubeVideoId(rawUrl)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
}
