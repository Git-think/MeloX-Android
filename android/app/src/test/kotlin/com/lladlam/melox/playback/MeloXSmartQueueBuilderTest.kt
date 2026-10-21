package com.lladlam.melox.playback

import androidx.media3.common.MediaItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MeloXSmartQueueBuilderTest {
    @Before
    fun resetBuilder() {
        MeloXSmartQueueBuilder.reset()
    }

    @Test
    fun needsAnalysisOnlyCoversPendingTargetsOfTheCurrentGeneration() {
        val items = listOf(item("1"), item("2"), item("3"), item("4"))
        MeloXSmartQueueBuilder.begin(items, selectedIndex = 0)
        val generation = generation()
        MeloXSmartQueueBuilder.setAnalysisTargets(generation, setOf("1", "2", "3"))

        assertTrue(MeloXSmartQueueBuilder.needsAnalysis(generation, "1"))
        assertTrue(MeloXSmartQueueBuilder.needsAnalysis(generation, "2"))
        assertFalse(MeloXSmartQueueBuilder.needsAnalysis(generation, "4"))
        assertFalse(MeloXSmartQueueBuilder.needsAnalysis(generation + 1, "1"))
    }

    @Test
    fun recordedOrFailedCandidatesAreNotRequeued() {
        val items = listOf(item("1"), item("2"), item("3"))
        MeloXSmartQueueBuilder.begin(items, selectedIndex = 0)
        val generation = generation()
        MeloXSmartQueueBuilder.setAnalysisTargets(generation, setOf("1", "2"))

        MeloXSmartQueueBuilder.recordAnalysis(generation, "1", analysis(120.0))
        assertFalse(MeloXSmartQueueBuilder.needsAnalysis(generation, "1"))

        MeloXSmartQueueBuilder.recordFailure(generation, "2")
        assertFalse(MeloXSmartQueueBuilder.needsAnalysis(generation, "2"))

        assertFalse(MeloXSmartQueueBuilder.needsAnalysis(generation, "3"))
    }

    @Test
    fun unlimitedTargetsStillTrackAnalyzedCandidates() {
        val items = listOf(item("1"), item("2"))
        MeloXSmartQueueBuilder.begin(items, selectedIndex = 0)
        val generation = generation()
        MeloXSmartQueueBuilder.setAnalysisTargets(generation, setOf("1", "2"))

        MeloXSmartQueueBuilder.recordAnalysis(generation, "2", analysis(90.0))

        assertTrue(MeloXSmartQueueBuilder.needsAnalysis(generation, "1"))
        assertFalse(MeloXSmartQueueBuilder.needsAnalysis(generation, "2"))
    }

    private fun generation(): Long = MeloXSmartQueueBuilder.snapshot().generation

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    private fun analysis(bpm: Double) = MeloXAutoMixTrackAnalysis(
        bpm = bpm,
        confidence = .8,
        firstAudibleMs = 0L,
        lastAudibleMs = 60_000L,
        beatTimesMs = longArrayOf(),
        downbeatTimesMs = longArrayOf(),
        phraseBoundariesMs = longArrayOf(),
        frames = emptyList(),
    )
}
