package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.ChkszApiClient
import com.lladlam.melox.core.provider.lxuser.ChkszConfigStore

internal data class ChkszPlaybackResult(val url: String)

class ChkszPlaybackResolver(context: Context) {
    private val appContext = context.applicationContext
    private val client = ChkszApiClient(
        apiKeyProvider = { ChkszConfigStore.readApiKey(appContext) },
        baseUrlProvider = { ChkszConfigStore.readBaseUrl(appContext) },
    )

    fun cacheIdentity(): String = if (!ChkszConfigStore.readEnabled(appContext)) {
        "disabled"
    } else {
        ChkszConfigStore.readApiKey(appContext).let { key ->
            if (key.isBlank()) "disabled"
            else "configured:${key.hashCode()}:${ChkszConfigStore.readBaseUrl(appContext).hashCode()}"
        }
    }

    internal fun resolve(songId: Long, quality: AudioQualityTier): ChkszPlaybackResult? {
        if (!ChkszConfigStore.readEnabled(appContext)) return null
        return client.resolveNetease(songId, quality)?.let(::ChkszPlaybackResult)
    }

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier): ChkszPlaybackResult? {
        if (!ChkszConfigStore.readEnabled(appContext)) return null
        return client.resolveTrack(track, quality)?.let(::ChkszPlaybackResult)
    }
}
