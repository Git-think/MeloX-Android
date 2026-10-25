package com.lladlam.melox.core.music.model

/** Higher is better. Immersive is treated like hi-res for cross-source matching. */
fun AudioQualityTier.rank(): Int = when (this) {
    AudioQualityTier.Standard -> 1
    AudioQualityTier.High -> 2
    AudioQualityTier.Lossless -> 3
    AudioQualityTier.HiResolution -> 4
    AudioQualityTier.Immersive -> 4
    AudioQualityTier.Master -> 5
}

/**
 * Normalizes provider-specific search hints. [bitrate] must be bits/s; callers
 * with kbps values multiply before calling. Explicit flags win because some
 * APIs advertise lossless without a reliable bitrate.
 */
fun audioQualityForBitrate(
    bitrate: Int?,
    lossless: Boolean = false,
    hiResolution: Boolean = false,
    immersive: Boolean = false,
): AudioQualityTier? {
    if (immersive) return AudioQualityTier.Immersive
    if (hiResolution) return AudioQualityTier.HiResolution
    if (lossless) return AudioQualityTier.Lossless
    val bps = bitrate?.takeIf { it > 0 } ?: return null
    return when {
        bps >= 900_000 -> AudioQualityTier.Lossless
        bps >= 256_000 -> AudioQualityTier.High
        else -> AudioQualityTier.Standard
    }
}
