package com.lladlam.melox.core.provider.lxuser

import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Meting API（metowolf/Meting 及其兼容实现）解析客户端。
 *
 * 约定接口：`GET {base}/api?server=netease&type=url&id={songId}&level={level}`，
 * 服务端要求鉴权时附带 `auth` 口令。响应兼容三种形式：302 跳转到直链、
 * JSON（url/playUrl/… 嵌套结构）、纯文本直链；服务端直接回源音频流时
 * 使用请求地址本身串流。
 */
class MetingApiClient(
    private val baseUrlProvider: () -> String,
    private val authProvider: () -> String = { "" },
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    // 302 → 直链：关闭自动跟随，避免把整个音频流读进内存。
    private val streamingClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun resolveNetease(songId: Long, quality: AudioQualityTier): String? =
        resolveNetease(songId.toString(), quality)

    fun resolveTrack(track: MusicTrack, quality: AudioQualityTier): String? = when (val metadata = track.providerMetadata) {
        is ProviderTrackMetadata.Netease -> resolveNetease(metadata.numericId.toString(), quality)
        is ProviderTrackMetadata.QQMusic -> request("tencent", metadata.songMid, null)
        is ProviderTrackMetadata.Kugou -> request("kugou", metadata.hash, null)
        else -> null
    }

    private fun resolveNetease(songId: String, quality: AudioQualityTier): String? =
        request("netease", songId, quality.toMetingLevel())

    private fun request(server: String, id: String, level: String?): String? {
        val base = baseUrlProvider().trim().trimEnd('/').takeIf(String::isNotBlank) ?: return null
        val url = buildMetingUrl(base, server, id, level, authProvider().trim())
        if (url == null) {
            Log.w(TAG, "Meting base URL is not a valid HTTP(S) URL: $base")
            return null
        }
        val request = Request.Builder().url(url)
            .header("User-Agent", "MeloX")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        // Meting 是 best-effort 兜底：服务不可用时直接返回 null，不影响官方播放。
        return try {
            streamingClient.newCall(request).execute().use { response ->
                when {
                    response.isRedirect -> redirectTarget(response)
                    response.isSuccessful -> urlFromResponse(url, response)
                    else -> {
                        Log.w(TAG, "Meting response failed server=$server code=${response.code}")
                        null
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Meting resolve failed server=$server error=${error.javaClass.simpleName}")
            null
        }
    }

    private fun redirectTarget(response: Response): String? =
        response.header("Location")?.let { location ->
            response.request.url.resolve(location)?.toString()
        }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun urlFromResponse(requestUrl: HttpUrl, response: Response): String? {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.startsWith("audio/")) {
            // 服务端直接回源音频流：播放器用请求地址串流。
            return requestUrl.toString()
        }
        val body = response.body.string()
        val fromJson = runCatching { findMetingUrl(JSONObject(body)) }.getOrNull()
        return fromJson
            ?: body.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private companion object {
        const val TAG = "MeloXMeting"
    }
}

/** 拼接 Meting 请求地址：用户填写服务器根地址，`/api` 由客户端按需补齐。 */
internal fun buildMetingUrl(
    base: String,
    server: String,
    id: String,
    level: String?,
    auth: String,
): HttpUrl? = try {
    val path = if (base.endsWith("/api")) "" else "/api"
    "$base$path".toHttpUrl().newBuilder()
        .addQueryParameter("server", server)
        .addQueryParameter("type", "url")
        .addQueryParameter("id", id)
        .apply { level?.takeIf(String::isNotBlank)?.let { addQueryParameter("level", it) } }
        .apply { auth.takeIf(String::isNotBlank)?.let { addQueryParameter("auth", it) } }
        .build()
} catch (error: IllegalArgumentException) {
    null
}

/** 从 Meting 的 JSON 响应里递归寻找播放地址（兼容 data/url/playUrl 等嵌套结构）。 */
internal fun findMetingUrl(value: Any?): String? = when (value) {
    is JSONObject -> {
        sequenceOf("url", "playUrl", "play_url", "proxyUrl", "downloadUrl", "audioUrl")
            .map(value::optString).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: value.keys().asSequence().mapNotNull { findMetingUrl(value.opt(it)) }.firstOrNull()
    }
    is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findMetingUrl(value.opt(it)) }.firstOrNull()
    is String -> value.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    else -> null
}

/** Meting 的音质参数使用网易云同名等级（standard/exhigh/lossless/hires/jyeffect/jymaster）。 */
internal fun AudioQualityTier.toMetingLevel(): String = when (this) {
    AudioQualityTier.Standard -> "standard"
    AudioQualityTier.High -> "exhigh"
    AudioQualityTier.Lossless -> "lossless"
    AudioQualityTier.HiResolution -> "hires"
    AudioQualityTier.Immersive -> "jyeffect"
    AudioQualityTier.Master -> "jymaster"
}
