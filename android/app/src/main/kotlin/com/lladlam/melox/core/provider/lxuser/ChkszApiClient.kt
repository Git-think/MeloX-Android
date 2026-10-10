package com.lladlam.melox.core.provider.lxuser

import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/** Optional third-party resolver backed by a CHKSZ-compatible API; defaults to api.chksz.com. */
class ChkszApiClient(
    private val apiKeyProvider: () -> String,
    private val baseUrlProvider: () -> String = { ChkszConfigStore.DefaultBaseUrl },
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    fun resolveNetease(songId: Long, quality: AudioQualityTier): String? = request(
        "163_music",
        mapOf(
            "id" to songId.toString(),
            "level" to quality.toChkszLevel(),
            "type" to "json",
        ),
    )

    fun resolveTrack(track: MusicTrack, quality: AudioQualityTier): String? = when (val metadata = track.providerMetadata) {
        is ProviderTrackMetadata.Netease -> resolveNetease(metadata.numericId, quality)
        is ProviderTrackMetadata.QQMusic -> request("qq_music", mapOf(
            "mid" to metadata.songMid,
            "size" to quality.toChkszSize(),
            "type" to "json",
        ))
        is ProviderTrackMetadata.Kugou -> request("kugou_music", mapOf(
            "id" to metadata.hash,
            "size" to quality.toChkszSize(),
            "type" to "json",
        ))
        else -> null
    }

    private fun request(endpoint: String, params: Map<String, String>): String? {
        val key = apiKeyProvider().trim().takeIf(String::isNotBlank) ?: return null
        val url = buildUrl(endpoint, params, key) ?: return null
        val request = Request.Builder().url(url)
            .header("User-Agent", "MeloX/0.4.5")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        // CHKSZ is a best-effort fallback: a failing base URL must not break playback.
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "CHKSZ response failed endpoint=$endpoint code=${response.code}")
                    return@use null
                }
                findUrl(JSONObject(response.body.string()))
            }
        } catch (error: Exception) {
            Log.w(TAG, "CHKSZ resolve failed endpoint=$endpoint error=${error.javaClass.simpleName}")
            null
        }
    }

    private fun buildUrl(endpoint: String, params: Map<String, String>, key: String): HttpUrl? {
        val base = baseUrlProvider().trim().trimEnd('/')
        val path = if (base.endsWith("/api")) "/$endpoint" else "/api/$endpoint"
        return try {
            "$base$path".toHttpUrl().newBuilder()
                .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
                .addQueryParameter("apikey", key)
                .build()
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "CHKSZ base URL is not a valid HTTP(S) URL: $base")
            null
        }
    }

    private fun findUrl(value: Any?): String? = when (value) {
        is JSONObject -> {
            sequenceOf("url", "playUrl", "play_url", "downloadUrl", "audioUrl")
                .map(value::optString).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: value.keys().asSequence().mapNotNull { findUrl(value.opt(it)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findUrl(value.opt(it)) }.firstOrNull()
        is String -> value.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        else -> null
    }

    private companion object {
        const val TAG = "MeloXChksz"
    }
}

private fun AudioQualityTier.toChkszLevel(): String = when (this) {
    AudioQualityTier.Standard -> "standard"
    AudioQualityTier.High -> "exhigh"
    AudioQualityTier.Lossless -> "lossless"
    AudioQualityTier.HiResolution -> "hires"
    AudioQualityTier.Immersive -> "jyeffect"
    AudioQualityTier.Master -> "jymaster"
}

private fun AudioQualityTier.toChkszSize(): String = when (this) {
    AudioQualityTier.Standard -> "128k"
    AudioQualityTier.High -> "320k"
    AudioQualityTier.Lossless -> "flac"
    AudioQualityTier.HiResolution -> "hires"
    AudioQualityTier.Immersive, AudioQualityTier.Master -> "master"
}
