package com.lladlam.melox.core.provider.qqmusic

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.rank
import org.json.JSONObject

internal data class QQMusicQualityHint(
    val tier: AudioQualityTier,
    val bitrate: Int?,
)

/**
 * QQ Music search cards expose available file variants differently across API
 * revisions. Older payloads use `file.size_flac`/`size_320mp3`; newer ones use
 * `file.size_new` entries with an explicit bitrate. Read both and keep the
 * highest tier instead of relying on a fixed provider preference.
 */
internal fun qqMusicQualityHint(item: JSONObject): QQMusicQualityHint? {
    val file = item.optJSONObject("file")
        ?: item.optJSONObject("fileInfo")
        ?: item.optJSONObject("songFile")
        ?: return directQuality(item)
    var best: QQMusicQualityHint? = null

    fun consider(tier: AudioQualityTier, bitrate: Int?) {
        val normalized = bitrate?.takeIf { it > 0 }
        val current = best
        if (current == null || tier.rank() > current.tier.rank() ||
            (tier.rank() == current.tier.rank() && (normalized ?: 0) > (current.bitrate ?: 0))
        ) {
            best = QQMusicQualityHint(tier, normalized)
        }
    }

    val legacy = listOf(
        "size_hires" to AudioQualityTier.HiResolution,
        "size_flac" to AudioQualityTier.Lossless,
        "size_320mp3" to AudioQualityTier.High,
        "size_128mp3" to AudioQualityTier.Standard,
        "size_96aac" to AudioQualityTier.Standard,
        "size_48aac" to AudioQualityTier.Standard,
    )
    for ((key, tier) in legacy) {
        if (file.optLong(key, 0L) > 0L) {
            consider(
                tier,
                when (tier) {
                    AudioQualityTier.HiResolution -> 1_411_200
                    AudioQualityTier.Lossless -> 1_000_000
                    AudioQualityTier.High -> 320_000
                    else -> null
                },
            )
        }
    }

    val newValues = mutableListOf<JSONObject>()
    file.optJSONArray("size_new")?.let { array ->
        for (index in 0 until array.length()) array.optJSONObject(index)?.let(newValues::add)
    }
    file.optJSONObject("size_new")?.let { objectValue ->
        objectValue.keys().forEach { key -> objectValue.optJSONObject(key)?.let(newValues::add) }
    }
    file.optJSONArray("size")?.let { array ->
        for (index in 0 until array.length()) array.optJSONObject(index)?.let(newValues::add)
    }
    newValues.forEach { value ->
        val format = listOf(
            value.optString("format"),
            value.optString("file_type"),
            value.optString("type"),
            value.optString("ext"),
        ).joinToString(" ").lowercase()
        val rawBitrate = value.optInt("bitrate", value.optInt("br", 0))
        val bitrate = when {
            rawBitrate <= 0 -> null
            rawBitrate < 10_000 -> rawBitrate * 1_000
            else -> rawBitrate
        }
        val tier = when {
            format.contains("master") -> AudioQualityTier.Master
            format.contains("atmos") || format.contains("spatial") -> AudioQualityTier.Immersive
            format.contains("hires") || format.contains("hi_res") || format.contains("hi-res") ->
                AudioQualityTier.HiResolution
            format.contains("flac") || format.contains("lossless") -> AudioQualityTier.Lossless
            format.contains("320") || (bitrate ?: 0) >= 256_000 -> AudioQualityTier.High
            else -> AudioQualityTier.Standard
        }
        consider(tier, bitrate)
    }

    return best ?: directQuality(file)
}

private fun directQuality(value: JSONObject): QQMusicQualityHint? {
    val bitrate = value.optInt("bitrate", value.optInt("br", 0)).takeIf { it > 0 }?.let {
        if (it < 10_000) it * 1_000 else it
    }
    val format = value.optString("format").lowercase()
    val tier = when {
        format.contains("master") -> AudioQualityTier.Master
        format.contains("atmos") || format.contains("spatial") -> AudioQualityTier.Immersive
        format.contains("hires") -> AudioQualityTier.HiResolution
        format.contains("flac") -> AudioQualityTier.Lossless
        (bitrate ?: 0) >= 256_000 -> AudioQualityTier.High
        bitrate != null -> AudioQualityTier.Standard
        else -> return null
    }
    return QQMusicQualityHint(tier, bitrate)
}
