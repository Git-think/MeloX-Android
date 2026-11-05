package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.thirdparty.ResolverApiClient
import com.lladlam.melox.core.provider.thirdparty.ResolverKind
import com.lladlam.melox.core.provider.thirdparty.ResolverSettingsStore

/** The only adapter between the new API clients and upstream's fallback stage. */
internal class ConfiguredResolverSource(context: Context, private val kind: ResolverKind) : ThirdPartySource {
    private val app = context.applicationContext
    private val client = ResolverApiClient(report = { Log.i("MeloXResolvers", it) })

    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        client.resolve(ResolverSettingsStore.read(app, kind), track, quality)?.let { url ->
            ThirdPartyTrack(url, sourceLabel = kind.label, expiresAtEpochMs = System.currentTimeMillis() + 120_000L)
        }

    override fun cacheIdentity(): String = ResolverSettingsStore.read(app, kind).cacheIdentity()
}
