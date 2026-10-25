package com.lladlam.melox.playback

import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricsDocument
import java.util.Locale
import org.json.JSONObject

/**
 * Builds the `lyricInfo` JSON ColorOS lockscreen lyrics consume. Ported from
 * Mei's `SystemLyricTimeline.toColorOsLyricInfo`.
 */
internal fun buildColorOsLyricPayload(
    document: LyricsDocument,
    songId: Long,
    songName: String,
    artistName: String,
    albumName: String?,
    packageName: String,
    sessionGeneration: Long,
): String? {
    val lines = document.lines.sortedBy { it.timeMs }
    val lrc = buildString {
        lines.forEach { line ->
            append('[').append(colorOsTimestamp(line.timeMs)).append(']')
            appendLine(colorOsSingleLine(line.text))
        }
    }
    val raw = if (lines.any { it.syllables.isNotEmpty() }) buildString {
        lines.forEach { line ->
            append('[').append(colorOsTimestamp(line.timeMs)).append(']')
            val words = line.syllables.filter { it.text.isNotBlank() }
            if (words.isEmpty()) {
                appendLine(colorOsSingleLine(line.text))
            } else {
                words.forEach { word ->
                    append('<').append(colorOsTimestamp(word.startTimeMs)).append('>')
                    append(colorOsSingleLine(word.text))
                }
                append('<').append(colorOsTimestamp(words.last().endTimeMs)).appendLine('>')
            }
        }
    } else {
        null
    }
    val translation = buildString {
        lines.forEach { line ->
            line.translation?.takeIf { it.isNotBlank() }?.let {
                append('[').append(colorOsTimestamp(line.timeMs)).append(']')
                appendLine(colorOsSingleLine(it))
            }
        }
    }
    return JSONObject()
        .put("songName", songName)
        .put("artist", artistName)
        .put("songId", songId)
        .put("lyricType", 0)
        .put("lyric", lrc)
        .put("noLyric", lines.isEmpty())
        .put("provider", packageName)
        .put("sessionGeneration", sessionGeneration)
        .put("album", albumName.orEmpty())
        .apply {
            if (raw != null) put("rawLyric", raw)
            if (translation.isNotEmpty()) put("translationLyric", translation)
        }
        .toString()
        .takeIf { it.length <= 128 * 1024 }
}

internal fun colorOsTimestamp(timeMs: Long): String = String.format(
    Locale.ROOT,
    "%02d:%02d.%03d",
    timeMs / 60_000L,
    timeMs / 1_000L % 60L,
    timeMs % 1_000L,
)

private fun colorOsSingleLine(text: String?): String =
    text.orEmpty().replace('\r', ' ').replace('\n', ' ')
