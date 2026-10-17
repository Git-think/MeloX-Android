package com.lladlam.melox.core.provider.youtubemusic

import java.net.URI

/** Match supported authentication cookie keys, never substrings inside another value. */
internal fun hasYouTubeAuthCookie(cookie: String): Boolean =
    com.metrolist.innertube.utils.YouTubeAuth.hasAuthCookie(cookie)

/** Preferred music.youtube.com cookies win over parent-domain fallbacks. */
internal fun mergeYouTubeCookies(jars: List<String>): String {
    val values = linkedMapOf<String, String>()
    jars.forEach { jar ->
        jar.split(';').forEach { item ->
            val parts = item.trim().split('=', limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                values.putIfAbsent(parts[0], parts[1])
            }
        }
    }
    return values.entries.joinToString("; ") { (key, value) -> "$key=$value" }
}

internal fun isYouTubeMusicPage(url: String?): Boolean = runCatching {
    val uri = URI(url ?: return false)
    uri.scheme == "https" && uri.host.equals("music.youtube.com", ignoreCase = true)
}.getOrDefault(false)

internal fun normalizeYouTubeContextToken(value: String?): String = value.orEmpty().trim()
    .takeUnless { it == "null" || it == "undefined" }.orEmpty()
