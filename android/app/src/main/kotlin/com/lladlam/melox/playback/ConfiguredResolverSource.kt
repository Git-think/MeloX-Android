package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.thirdparty.ResolverApiClient
import com.lladlam.melox.core.provider.thirdparty.ResolverKind
import com.lladlam.melox.core.provider.thirdparty.ResolverSettingsStore
import com.lladlam.melox.core.provider.thirdparty.ResolverUrlTtlMs

/** The only adapter between the new API clients and upstream's fallback stage. */
internal class ConfiguredResolverSource(context: Context, private val kind: ResolverKind) : ThirdPartySource {
    private val app = context.applicationContext
    private val client = ResolverApiClient(report = { Log.i("MeloXResolvers", it) })

    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        client.resolve(ResolverSettingsStore.read(app, kind), track, quality)?.let { url ->
            ThirdPartyTrack(url, sourceLabel = kind.label)
        }

    override fun cacheIdentity(): String = ResolverSettingsStore.read(app, kind).cacheIdentity()
}

/** Fork URL-cache policy stays outside the upstream-owned result model. */
internal fun ThirdPartyTrack.configuredResolverExpiry(nowMs: Long = System.currentTimeMillis()): Long? =
    if (ResolverKind.entries.any { it.label == sourceLabel }) nowMs + ResolverUrlTtlMs else null

/** Keep upstream's LX result mapping unchanged, including newly added quality fields. */
internal fun withInstalledLxGuard(source: ThirdPartySource): ThirdPartySource = object : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        if (source.cacheIdentity() == "lx:") null else source.resolve(track, quality)

    override fun cacheIdentity(): String = source.cacheIdentity()
}
