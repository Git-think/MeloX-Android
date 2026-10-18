package com.metrolist.innertube.utils

import com.metrolist.innertube.models.YouTubeClient

/** Browser-session authentication shared by account, library and write requests. */
object YouTubeAuth {
    fun hasAuthCookie(cookie: String): Boolean = signingCookie(parseCookieString(cookie)) != null

    fun headers(cookie: String, timestampSeconds: Long, authUser: String = "0", pageId: String? = null): Map<String, String> {
        val secret = signingCookie(parseCookieString(cookie)) ?: return emptyMap()
        val origin = YouTubeClient.ORIGIN_YOUTUBE_MUSIC
        return buildMap {
            put("Cookie", cookie)
            put("Origin", origin)
            put("X-Origin", origin)
            put("Authorization", "SAPISIDHASH ${timestampSeconds}_${sha1("$timestampSeconds $secret $origin")}")
            put("X-Goog-AuthUser", authUser.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: "0")
            put("X-Youtube-Bootstrap-Logged-In", "true")
            pageId?.takeIf(String::isNotBlank)?.let { put("X-Goog-PageId", it) }
        }
    }

    private fun signingCookie(cookies: Map<String, String>): String? =
        listOf("__Secure-3PAPISID", "SAPISID", "__Secure-1PAPISID")
            .firstNotNullOfOrNull { cookies[it]?.takeIf(String::isNotBlank) }
}
