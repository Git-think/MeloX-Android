package com.lladlam.melox.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXEvaLayoutTest {
    @Test
    fun familyClassificationFollowsCharacterCountAndScript() {
        assertEquals(EvaLayoutFamily.Solo, EvaLayoutEngine.family("光"))
        assertEquals(EvaLayoutFamily.Short, EvaLayoutEngine.family("夜に駆ける"))
        assertEquals(EvaLayoutFamily.Long, EvaLayoutEngine.family("この長い歌詞は十四文字を超えている"))
        assertEquals(EvaLayoutFamily.Latin, EvaLayoutEngine.family("Fly me to the moon"))
    }

    @Test
    fun compositionIsDeterministicForTheSameSessionAndSequence() {
        val first = EvaLayoutEngine.composition("夜に駆ける", 42L, 7)
        val second = EvaLayoutEngine.composition("夜に駆ける", 42L, 7)
        assertEquals(first, second)
    }

    @Test
    fun everyBlockStaysInsideTheCanvas() {
        val samples = listOf(
            "光",
            "夜に駆ける",
            "この長い歌詞は十四文字を超えているけれども画面に収まる",
            "Fly me to the moon and let me play among the stars",
            "、",
        )
        for (text in samples) {
            for (seed in listOf(0L, 1L, -7L, Long.MAX_VALUE)) {
                for (sequence in 0..20) {
                    val composition = EvaLayoutEngine.composition(text, seed, sequence)
                    assertTrue("empty composition for $text", composition.blocks.isNotEmpty())
                    composition.blocks.forEach { block ->
                        assertTrue("x ${block.x}", block.x >= 0f && block.x <= 1f)
                        assertTrue("y ${block.y}", block.y >= 0f && block.y <= 1f)
                        assertTrue("width ${block.width}", block.width > 0f && block.width <= 1f)
                        assertTrue("height ${block.height}", block.height > 0f && block.height <= 1f)
                        assertTrue("right edge", block.x + block.width <= 1.001f)
                        assertTrue("bottom edge", block.y + block.height <= 1.001f)
                        assertTrue("text", block.text.isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun latinCompositionSplitsLongLines() {
        val composition = EvaLayoutEngine.composition(
            "Fly me to the moon and let me play among the stars",
            3L,
            1,
        )
        assertTrue(composition.blocks.size >= 2)
        assertTrue(composition.blocks.all { it.text == it.text.uppercase() })
    }
}
