package com.lladlam.melox.core.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricAccompanimentTimingTest {
    @Test
    fun ttmlParagraphStartDoesNotDetermineVoiceOrder() {
        val document = TtmlLyricsParser.parse("""
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="00:00:10.000" end="00:00:15.000">
                <span begin="00:00:12.000" end="00:00:14.000">主唱</span>
                <span ttm:role="x-bg" begin="00:00:10.000" end="00:00:12.000">
                  <span begin="00:00:11.000" end="00:00:12.000">提前伴唱</span>
                </span>
                <span ttm:role="x-bg" begin="00:00:10.000" end="00:00:15.000">
                  <span begin="00:00:13.000" end="00:00:15.000">后置伴唱</span>
                </span>
              </p></div></body>
            </tt>
        """.trimIndent())
        val line = document.lines.single()
        assertTrue(line.accompaniment[0].precedesLead(line))
        assertFalse(line.accompaniment[1].precedesLead(line))
    }

    @Test
    fun fallsBackToLineTimesAndKeepsSimultaneousVoicesBelow() {
        val line = LyricLine(timeMs = 12_000, text = "主唱")
        assertTrue(LyricAccompaniment(timeMs = 11_000, text = "提前").precedesLead(line))
        assertFalse(LyricAccompaniment(timeMs = 12_000, text = "同时").precedesLead(line))
        assertFalse(LyricAccompaniment(timeMs = 13_000, text = "之后").precedesLead(line))
    }

    @Test
    fun ignoresBlankAndInvalidTimingMarkers() {
        val line = LyricLine(timeMs = 10_000, text = "主唱", syllables = listOf(
            LyricSyllable(" ", 10_000, 11_000),
            LyricSyllable("无效", -1, 1),
            LyricSyllable("零时长", 10_000, 10_000),
            LyricSyllable("主唱", 12_000, 14_000),
        ))
        assertTrue(LyricAccompaniment(timeMs = 11_000, text = "提前").precedesLead(line))
    }
}
