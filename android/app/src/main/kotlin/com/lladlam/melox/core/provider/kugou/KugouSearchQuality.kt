package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.rank
import org.json.JSONObject

internal data class KugouQualityHint(
    val tier: AudioQualityTier,
    val bitrate: Int?,
)

/**
 * KuGou's search card reports the byte size of each offered variant. A
 * non-zero size means the provider advertises that variant; the old parser
 * only kept the default hash and therefore lost this ordering signal.
 */
internal fun kugouSearchQuality(item: JSONObject): KugouQualityHint? {
    val sizes = listOf(
        listOf("ResFileSize", "res_filesize", "SuperFileSize", "viper_atmos_filesize") to AudioQualityTier.HiResolution,
        listOf("SQFileSize", "sq_filesize", "FlacFileSize") to AudioQualityTier.Lossless,
        listOf("HQFileSize", "hq_filesize") to AudioQualityTier.High,
        listOf("FileSize", "filesize") to AudioQualityTier.Standard,
    )
    var best: KugouQualityHint? = null
    for ((keys, tier) in sizes) {
        if (keys.none { key -> (longValue(item, key) ?: 0L) > 0L }) continue
        val bitrate = when (tier) {
            AudioQualityTier.HiResolution -> 1_411_200
            AudioQualityTier.Lossless -> 1_000_000
            AudioQualityTier.High -> 320_000
            else -> 128_000
        }
        if (best == null || tier.rank() > best.tier.rank()) best = KugouQualityHint(tier, bitrate)
    }
    return best
}

private fun longValue(value: JSONObject, key: String): Long? =
    value.optString(key, "").toLongOrNull() ?: value.optLong(key, 0L).takeIf { it != 0L }
