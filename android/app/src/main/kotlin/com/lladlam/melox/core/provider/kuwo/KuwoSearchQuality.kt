package com.lladlam.melox.core.provider.kuwo

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.rank
import org.json.JSONObject

internal data class KuwoQualityHint(
    val tier: AudioQualityTier,
    val bitrate: Int?,
)

/**
 * Kuwo encodes its available formats in `N_MINFO`, for example:
 * `level:ff,bitrate:1000,format:flac;level:p,bitrate:320,format:mp3`.
 * Search cards do not expose a separate lossless flag, so this is the only
 * cross-source quality signal before playback resolution.
 */
internal fun kuwoSearchQuality(item: JSONObject): KuwoQualityHint? {
    val info = item.optString("N_MINFO")
        .ifBlank { item.optString("MINFO") }
        .ifBlank { item.optString("n_minfo") }
    var best: KuwoQualityHint? = null

    fun consider(tier: AudioQualityTier, bitrateKbps: Int?) {
        val bitrate = bitrateKbps?.takeIf { it > 0 }?.let { if (it < 10_000) it * 1_000 else it }
        val current = best
        if (current == null || tier.rank() > current.tier.rank() ||
            (tier.rank() == current.tier.rank() && (bitrate ?: 0) > (current.bitrate ?: 0))
        ) {
            best = KuwoQualityHint(tier, bitrate)
        }
    }

    for (entry in info.split(';')) {
        val fields = entry.split(',').mapNotNull { part ->
            val index = part.indexOf(':')
            if (index <= 0) null else part.substring(0, index).trim().lowercase() to part.substring(index + 1).trim()
        }.toMap()
        val format = fields["format"].orEmpty().lowercase()
        val bitrate = fields["bitrate"]?.toIntOrNull()
            ?: fields["br"]?.toIntOrNull()
        val tier = when {
            format.contains("flac") || format.contains("lossless") || (bitrate ?: 0) >= 900 ->
                AudioQualityTier.Lossless
            (bitrate ?: 0) >= 256 -> AudioQualityTier.High
            bitrate != null -> AudioQualityTier.Standard
            else -> null
        } ?: continue
        consider(tier, bitrate)
    }

    if (best == null) {
        val format = item.optString("FORMAT").lowercase()
        val bitrate = item.optInt("BITRATE", 0)
            .takeIf { it > 0 }
            ?: item.optInt("bitrate", 0).takeIf { it > 0 }
        if (format.isNotBlank() || bitrate != null) {
            val tier = when {
                format.contains("flac") || (bitrate ?: 0) >= 900 -> AudioQualityTier.Lossless
                (bitrate ?: 0) >= 256 -> AudioQualityTier.High
                else -> AudioQualityTier.Standard
            }
            consider(tier, bitrate)
        }
    }
    return best
}
