package com.lladlam.melox.core.provider.thirdparty

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.network.MeloXHttpClient
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Bounded, cookie-free API traffic; never alters the official providers' HTTP client. */
internal class ResolverApiClient(
    private val http: OkHttpClient = MeloXHttpClient.thirdPartyAndMedia.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(false).build(),
    private val now: () -> Long = System::currentTimeMillis,
    private val report: (String) -> Unit = {},
) {
    private data class Entry(val url: String?, val expires: Long)
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Entry>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > 128
    }
    private val blocked = object : LinkedHashMap<String, Long>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 16
    }
    private val pending = ConcurrentHashMap<String, CompletableFuture<String?>>()

    fun resolve(settings: ResolverSettings, track: MusicTrack, quality: AudioQualityTier): String? {
        val url = resolverRequestUrl(settings, track, quality) ?: return null
        val identity = settings.cacheIdentity()
        val key = "$identity:${track.id.sourceId.value}:${track.id.value}:${quality.name}"
        synchronized(lock) {
            cache[key]?.takeIf { now() < it.expires }?.let { return it.url }
            if (now() < (blocked[identity] ?: 0L)) return null
        }
        val future = CompletableFuture<String?>()
        val existing = pending.putIfAbsent(key, future)
        if (existing != null) return try {
            existing.get(9, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (_: Exception) { null }

        try {
            // Another owner may have completed between our initial cache check
            // and putIfAbsent. Do not issue the same request a second time.
            synchronized(lock) {
                cache[key]?.takeIf { now() < it.expires }?.let {
                    future.complete(it.url)
                    return it.url
                }
                if (now() < (blocked[identity] ?: 0L)) return null
            }
            val request = Request.Builder().url(url).header("User-Agent", "MeloX-Android")
                .header("Accept", "application/json, text/plain, audio/*;q=0.9, */*;q=0.1").build()
            val result = try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code in setOf(401, 403, 429) || response.code >= 500) block(identity)
                        report("${settings.kind.label}: HTTP ${response.code}")
                        null
                    } else readResolverResponse(response)
                }
            } catch (_: IOException) {
                block(identity)
                report("${settings.kind.label}: request failed; cooling down")
                null
            }
            synchronized(lock) {
                cache[key] = Entry(result, now() + if (result == null) 30_000L else 120_000L)
            }
            future.complete(result)
            return result
        } finally {
            // Do not leave concurrent readers hanging if a programming error escapes.
            future.complete(null)
            pending.remove(key, future)
        }
    }

    private fun block(identity: String) = synchronized(lock) { blocked[identity] = now() + 60_000L }
}

internal fun readResolverResponse(response: Response): String? {
    val mime = response.body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty().lowercase()
    if (mime.startsWith("audio/") || mime in setOf(
            "application/octet-stream", "application/vnd.apple.mpegurl", "application/x-mpegurl", "video/mp4",
        )) {
        // Redirects are handled by upstream's isolated media client. Closing the
        // response immediately is essential: never download a song as JSON/text.
        return ResolverAudioUrl.valid(response.request.url.toString())
    }
    if (mime.startsWith("image/")) return null
    val limit = 64L * 1024
    if (response.body.contentLength() > limit || response.body.source().request(limit + 1)) return null
    // PHP APIs sometimes leave the default text/html MIME type even for JSON
    // or a plain URL. Parse the bounded body, not the MIME label; real HTML
    // markup is not a valid resolver payload and will be rejected by the parser.
    return ResolverAudioUrl.parse(response.body.string())
}
