package com.lladlam.melox.core.lyrics

import org.junit.Assert.*
import org.junit.Test

class JapaneseRubyAlignmentTest {
    @Test fun kanaAnchorsSplitReadingRatherThanCountingSpaces() {
        val parts = checkNotNull(JapaneseRubyAlignment.align("君のこと", "kimi no koto"))
        assertEquals(listOf("君", "の", "こ", "と"), parts.map { it.original })
        assertEquals(listOf("kimi", "no", "ko", "to"), parts.map { it.reading })
    }
    @Test fun contractedSoundsStayTogetherAndKatakanaUsesTheSameAlignment() {
        val parts = checkNotNull(JapaneseRubyAlignment.align("キャット", "kyatto"))
        assertEquals(listOf("キャ", "ッ", "ト"), parts.map { it.original })
        assertEquals(listOf("kya", "t", "to"), parts.map { it.reading })
    }
    @Test fun kanjiCompoundsAreNotDividedIntoInventedCharacterReadings() {
        val parts = checkNotNull(JapaneseRubyAlignment.align("世界へ", "sekai e"))
        assertEquals(listOf("世界", "へ"), parts.map { it.original })
        assertEquals(listOf("sekai", "e"), parts.map { it.reading })
    }
    @Test fun punctuationDoesNotConsumeASoundAndOriginalTextIsPreserved() {
        val text = "ね、君の！"
        val parts = checkNotNull(JapaneseRubyAlignment.align(text, "ne, kimi no!"))
        assertEquals(text, parts.joinToString("") { it.original })
        assertEquals(listOf("ne", null, "kimi", "no", null), parts.map { it.reading })
    }
    @Test fun incompatibleReadingIsNotFalselyMatched() {
        assertNull(JapaneseRubyAlignment.align("こんにちは", "good morning"))
        assertNull(JapaneseRubyAlignment.align("hello", "hello"))
    }
}
