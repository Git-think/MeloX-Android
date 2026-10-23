package com.lladlam.melox.playback

import com.lladlam.melox.core.lyrics.LyricLine
import com.lladlam.melox.core.lyrics.LyricSyllable
import com.lladlam.melox.core.lyrics.LyricsDocument
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsLyricPayloadTest {
    private val document = LyricsDocument(
        lines = listOf(
            LyricLine(
                timeMs = 65_500L,
                text = "first line",
                translation = "第一行",
                syllables = listOf(
                    LyricSyllable("fi", 65_500L, 65_700L),
                    LyricSyllable("rst", 65_700L, 66_000L),
                ),
            ),
            LyricLine(timeMs = 68_000L, text = "second\nline"),
        ),
    )

    @Test
    fun `payload carries lrc raw and translation channels`() {
        val payload = buildColorOsLyricPayload(
            document = document,
            songId = 42L,
            songName = "Song",
            artistName = "Artist",
            albumName = "Album",
            packageName = "com.example.app",
            sessionGeneration = 7L,
        )
        assertNotNull(payload)
        val json = JSONObject(payload!!)
        assertEquals(42L, json.getLong("songId"))
        assertEquals(0, json.getInt("lyricType"))
        assertEquals(7L, json.getLong("sessionGeneration"))
        assertTrue(json.getString("lyric").contains("[01:05.500]first line"))
        assertTrue(json.getString("rawLyric").contains("<01:05.500>fi"))
        assertTrue(json.getString("translationLyric").contains("[01:05.500]第一行"))
        // Lockscreen payloads intentionally flatten embedded newlines.
        assertTrue(json.getString("lyric").contains("[01:08.000]second line"))
    }

    @Test
    fun `over sized payloads are dropped`() {
        val huge = LyricsDocument(
            lines = List(20_000) { LyricLine(timeMs = it * 10L, text = "x".repeat(16)) },
        )
        val payload = buildColorOsLyricPayload(
            document = huge,
            songId = 1L,
            songName = "S",
            artistName = "A",
            albumName = null,
            packageName = "p",
            sessionGeneration = 1L,
        )
        assertTrue(payload == null || payload!!.length <= 128 * 1024)
    }

    @Test
    fun `timestamps use minute second millisecond`() {
        assertEquals("01:05.500", colorOsTimestamp(65_500L))
        assertEquals("00:00.000", colorOsTimestamp(0L))
    }
}
