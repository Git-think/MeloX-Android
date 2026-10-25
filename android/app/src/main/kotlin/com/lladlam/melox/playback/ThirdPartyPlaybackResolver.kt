package com.lladlam.melox.playback

import android.content.Context
import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack

internal data class ThirdPartyTrack(
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val sourceLabel: String,
)

/** One configured resolver source or an imported LX script source. */
internal fun interface ThirdPartySource {
    fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack?

    fun cacheIdentity(): String = ""
}

/**
 * User-configured resolvers run in order: Meting, CHKSZ, UNM, then LX scripts.
 * The membership-only option keeps them after the current provider and the
 * cross-provider stage; otherwise this fork tries them before the provider.
 * Unconfigured sources return null without making network requests.
 */
class ThirdPartyPlaybackResolver private constructor(
    private val enabledProvider: () -> Boolean,
    private val membershipOnlyProvider: () -> Boolean,
    sourceProvider: () -> List<ThirdPartySource>,
) {
    private val sources by lazy(sourceProvider)

    constructor(
        context: Context,
        enabledProvider: () -> Boolean,
        membershipOnlyProvider: () -> Boolean = { false },
    ) : this(
        enabledProvider = enabledProvider,
        membershipOnlyProvider = membershipOnlyProvider,
        sourceProvider = {
            listOf(
                MetingThirdPartySource(MetingPlaybackResolver(context)),
                ChkszThirdPartySource(ChkszPlaybackResolver(context)),
                UnmThirdPartySource(UnmPlaybackResolver(context)),
                LxThirdPartySource(LxUserPlaybackResolver(context)),
            )
        },
    )

    internal constructor(
        enabledProvider: () -> Boolean,
        membershipOnlyProvider: () -> Boolean,
        sources: List<ThirdPartySource>,
    ) : this(
        enabledProvider = enabledProvider,
        membershipOnlyProvider = membershipOnlyProvider,
        sourceProvider = { sources },
    )

    fun cacheIdentity(): String = if (!enabledProvider()) {
        "disabled"
    } else {
        "membershipOnly=${membershipOnlyProvider()}:" + sources.joinToString("|") { it.cacheIdentity() }
    }

    internal fun shouldResolveFirst(): Boolean = enabledProvider() && !membershipOnlyProvider()

    /**
     * @param membershipFailure whether the current source failed because of
     *   membership/rights. Used by the "only for membership tracks" option.
     */
    internal fun resolve(
        track: MusicTrack,
        quality: AudioQualityTier,
        membershipFailure: Boolean,
    ): ThirdPartyTrack? {
        if (!enabledProvider()) return null
        if (track.id.source == MusicSource.Jellyfin || track.id.source == MusicSource.Local) return null
        if (membershipOnlyProvider() && !membershipFailure) return null
        for (source in sources) {
            val result = runCatching { source.resolve(track, quality) }
                .onFailure { Log.w(TAG, "Third-party source failed: ${it.javaClass.simpleName} ${it.message}") }
                .getOrNull()
                ?: continue
            if (result.url.startsWith("http://") || result.url.startsWith("https://")) {
                Log.i(TAG, "Resolved via ${result.sourceLabel} source=${track.id.source.storageValue}")
                return result
            }
        }
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
    }
}

private class MetingThirdPartySource(
    private val resolver: MetingPlaybackResolver,
) : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        resolver.resolve(track, quality)?.let { ThirdPartyTrack(it.url, sourceLabel = "meting") }

    override fun cacheIdentity(): String = "meting:${resolver.cacheIdentity()}"
}

private class UnmThirdPartySource(
    private val resolver: UnmPlaybackResolver,
) : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        resolver.resolve(track)?.let { ThirdPartyTrack(it.url, sourceLabel = "unm") }

    override fun cacheIdentity(): String = "unm:${resolver.cacheIdentity()}"
}

private class ChkszThirdPartySource(
    private val resolver: ChkszPlaybackResolver,
) : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        resolver.resolve(track, quality)?.let { ThirdPartyTrack(it.url, sourceLabel = "chksz") }

    override fun cacheIdentity(): String = "chksz:${resolver.cacheIdentity()}"
}

private class LxThirdPartySource(
    private val resolver: LxUserPlaybackResolver,
) : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? =
        resolver.resolve(track, quality)?.let {
            ThirdPartyTrack(it.url, it.requestHeaders, "lx:${it.sourceId}")
        }

    override fun cacheIdentity(): String = "lx:${resolver.cacheIdentity()}"
}
