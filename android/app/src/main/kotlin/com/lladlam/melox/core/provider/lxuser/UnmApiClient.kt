package com.lladlam.melox.core.provider.lxuser

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * UNM（UnblockNeteaseMusic / UNM-Server 兼容）解灰客户端。
 *
 * 约定接口：`GET {base}/match?id={neteaseSongId}&server={kuwo,kugou,bilibili}`，
 * 返回 JSON `{code, message, data:{url, proxyUrl?}}`。同时兼容纯文本直链与 302 跳转。
 */
class UnmApiClient(
    private val baseUrlProvider: () -> String,
    private val serversProvider: () -> String = { UnmConfigStore.DefaultServers },
    private val httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) {
    fun resolveNetease(songId: Long): String? {
        val base = baseUrlProvider().trim().trimEnd('/').takeIf(String::isNotBlank) ?: return null
        val requestUrl = buildUrl(base, songId) ?: return null
        val request = Request.Builder().url(requestUrl)
            .header("User-Agent", "MeloX")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "UNM response failed id=$songId code=${response.code}")
                    return@use null
                }
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty()
                val body = response.body.string()
                findUrl(body)
                    ?: body.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    ?: finalUrl.takeIf { it != requestUrl.toString() && contentType.startsWith("audio/") }
            }
        } catch (error: Exception) {
            Log.w(TAG, "UNM resolve failed id=$songId error=${error.javaClass.simpleName}")
            null
        }
    }

    private fun buildUrl(base: String, songId: Long): HttpUrl? = try {
        "$base/match".toHttpUrl().newBuilder()
            .addQueryParameter("id", songId.toString())
            .addQueryParameter("server", serversProvider().trim().ifBlank { UnmConfigStore.DefaultServers })
            .build()
    } catch (error: IllegalArgumentException) {
        Log.w(TAG, "UNM base URL is not a valid HTTP(S) URL: $base")
        null
    }

    private fun findUrl(body: String): String? = runCatching {
        findUrl(JSONObject(body))
    }.getOrNull()

    private fun findUrl(value: Any?): String? = when (value) {
        is JSONObject -> {
            sequenceOf("url", "proxyUrl", "playUrl", "play_url", "downloadUrl", "audioUrl")
                .map(value::optString).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: value.keys().asSequence().mapNotNull { findUrl(value.opt(it)) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findUrl(value.opt(it)) }.firstOrNull()
        else -> null
    }

    private companion object {
        const val TAG = "MeloXUnm"
    }
}
