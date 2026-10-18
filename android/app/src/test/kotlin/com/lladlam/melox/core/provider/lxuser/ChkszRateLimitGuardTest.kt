package com.lladlam.melox.core.provider.lxuser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChkszRateLimitGuardTest {

    @Test
    fun requestsPassBeforeAnyRateLimit() {
        val guard = ChkszRateLimitGuard(cooldownMs = 1_000L)
        assertFalse(guard.shouldSkip(nowMs = 10_000L))
    }

    @Test
    fun a429BlocksRequestsForTheCooldownWindow() {
        val guard = ChkszRateLimitGuard(cooldownMs = 1_000L)
        guard.recordRateLimited(nowMs = 10_000L)
        assertTrue(guard.shouldSkip(nowMs = 10_500L))
        assertFalse(guard.shouldSkip(nowMs = 11_000L))
    }

    @Test
    fun aLater429ExtendsTheWindow() {
        val guard = ChkszRateLimitGuard(cooldownMs = 1_000L)
        guard.recordRateLimited(nowMs = 10_000L)
        guard.recordRateLimited(nowMs = 10_900L)
        assertTrue(guard.shouldSkip(nowMs = 11_800L))
        assertFalse(guard.shouldSkip(nowMs = 11_900L))
    }
}
