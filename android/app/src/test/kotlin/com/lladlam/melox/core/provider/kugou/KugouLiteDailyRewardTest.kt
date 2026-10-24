package com.lladlam.melox.core.provider.kugou

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouLiteDailyRewardTest {
    private val day = "2026-10-25"

    @Test
    fun autoCheckInRequiresLoginAndEnabledSwitch() {
        assertFalse(run(autoEnabled = true, loggedIn = false))
        assertFalse(run(autoEnabled = false, loggedIn = true))
        assertTrue(run(autoEnabled = true, loggedIn = true))
    }

    @Test
    fun manualCheckInIgnoresAutoSwitch() {
        assertTrue(run(autoEnabled = false, loggedIn = true, requireAuto = false))
    }

    @Test
    fun alreadyCompletedTodayIsSkipped() {
        assertFalse(run(successDay = day))
    }

    @Test
    fun failedAttemptCoolsDownBeforeRetrying() {
        val now = 1_000_000L
        assertFalse(
            run(
                attemptDay = day,
                attemptAtMs = now - 60_000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun failedAttemptRetriesAfterCooldown() {
        val now = 1_000_000L
        assertTrue(
            run(
                attemptDay = day,
                attemptAtMs = now - 11 * 60 * 1000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun newDayRunsAgain() {
        assertTrue(run(successDay = "2026-10-24", attemptDay = "2026-10-24"))
    }

    @Test
    fun manualForceIgnoresTodayStateAndCooldown() {
        assertTrue(run(successDay = day, attemptDay = day, force = true))
    }

    private fun run(
        autoEnabled: Boolean = true,
        requireAuto: Boolean = true,
        loggedIn: Boolean = true,
        successDay: String? = null,
        attemptDay: String? = null,
        attemptAtMs: Long = 0L,
        nowMs: Long = 1_000_000L,
        force: Boolean = false,
    ): Boolean = KugouLiteDailyCheckIn.shouldRun(
        autoEnabled = autoEnabled,
        requireAutoEnabled = requireAuto,
        loggedIn = loggedIn,
        successDay = successDay,
        attemptDay = attemptDay,
        attemptAtMs = attemptAtMs,
        day = day,
        nowMs = nowMs,
        force = force,
    )
}
