package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.provider.lxuser.UnmApiClient
import com.lladlam.melox.core.provider.lxuser.UnmConfigStore

internal data class UnmPlaybackResult(val url: String)

/** Resolves NetEase tracks through a UNM (UnblockNeteaseMusic compatible) server. */
class UnmPlaybackResolver(context: Context) {
    private val appContext = context.applicationContext
    private val client = UnmApiClient(
        baseUrlProvider = { UnmConfigStore.readBaseUrl(appContext) },
        serversProvider = { UnmConfigStore.readServers(appContext) },
    )

    fun cacheIdentity(): String = UnmConfigStore.readBaseUrl(appContext).let { base ->
        if (base.isBlank()) "disabled"
        else "configured:${base.hashCode()}:${UnmConfigStore.readServers(appContext).hashCode()}"
    }

    internal fun resolve(songId: Long): UnmPlaybackResult? =
        client.resolveNetease(songId)?.let(::UnmPlaybackResult)

    internal fun resolve(track: MusicTrack): UnmPlaybackResult? = when (val metadata = track.providerMetadata) {
        is ProviderTrackMetadata.Netease -> resolve(metadata.numericId)
        else -> null
    }
}
