package com.lladlam.melox.lan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXLanTogetherSyncTest {
    @Test
    fun hostHeartbeatsCannotRestartAnUnfinishedBufferingSeek() {
        for (second in 1L..10L) {
            assertFalse(shouldCorrectTogetherPosition(
                ready = false,
                localPositionMs = 10_000L,
                hostPositionMs = 10_000L + second * 1_000L,
                elapsedSinceSeekMs = second * 1_000L,
            ))
        }
    }

    @Test
    fun aCorrectionGetsTimeToSettleButLaterDriftStillCorrects() {
        assertFalse(shouldCorrectTogetherPosition(true, 10_000, 11_000, 1_000))
        assertFalse(shouldCorrectTogetherPosition(true, 10_000, 11_000, 2_000))
        assertTrue(shouldCorrectTogetherPosition(true, 10_000, 11_000, 3_000))
        assertFalse(shouldCorrectTogetherPosition(true, 10_000, 10_250, 5_000))
        assertTrue(shouldCorrectTogetherPosition(true, 10_000, 10_400, 5_000))
        assertTrue(shouldCorrectTogetherPosition(true, 11_000, 10_000, 5_000))
    }

    @Test fun clockOffsetAndPacketAgeAreAppliedWithTheCorrectSign() {
        assertEquals(10_120L, togetherTargetPosition(10_000, true, 50_000, 49_120, 1_000))
        assertEquals(10_000L, togetherTargetPosition(10_000, false, 50_000, 49_120, 1_000))
        assertEquals(10_000L, togetherTargetPosition(10_000, true, 0, 49_120, 1_000))
    }

    @Test fun smallDriftUsesBoundedSpeedInsteadOfRepeatedSeeks() {
        assertEquals(1f, togetherPlaybackRate(35), .0001f)
        assertEquals(1.03f, togetherPlaybackRate(200), .0001f)
        assertEquals(.97f, togetherPlaybackRate(-200), .0001f)
    }

    @Test fun endBarrierUsesTheMostDelayedDeviceAndFiveSecondBoundary() {
        assertTrue(togetherShouldWaitForEnd(100_000, listOf(99_900, 95_000)))
        assertFalse(togetherShouldWaitForEnd(100_000, listOf(99_900, 94_999)))
        assertTrue(togetherShouldWaitForEnd(100_000, emptyList()))
    }
}
