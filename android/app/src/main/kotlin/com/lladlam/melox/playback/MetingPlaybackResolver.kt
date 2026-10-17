package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.MetingApiClient
import com.lladlam.melox.core.provider.lxuser.MetingConfigStore

internal data class MetingPlaybackResult(val url: String)

/** Resolves tracks through a Meting API server. Empty base URL = disabled. */
class MetingPlaybackResolver(context: Context) {
    private val appContext = context.applicationContext
    private val client = MetingApiClient(
        baseUrlProvider = { MetingConfigStore.readBaseUrl(appContext) },
        authProvider = { MetingConfigStore.readAuth(appContext) },
    )

    fun cacheIdentity(): String = MetingConfigStore.readBaseUrl(appContext).let { base ->
        if (base.isBlank()) "disabled"
        else "configured:${base.hashCode()}:${MetingConfigStore.readAuth(appContext).hashCode()}"
    }

    internal fun resolve(songId: Long, quality: AudioQualityTier): MetingPlaybackResult? =
        client.resolveNetease(songId, quality)?.let(::MetingPlaybackResult)

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier): MetingPlaybackResult? =
        client.resolveTrack(track, quality)?.let(::MetingPlaybackResult)
}
