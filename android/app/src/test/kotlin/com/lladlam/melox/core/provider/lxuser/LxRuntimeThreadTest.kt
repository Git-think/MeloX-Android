package com.lladlam.melox.core.provider.lxuser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LxRuntimeThreadTest {
    @Test
    fun differentCallersShareOneOwnerAndNeverExecuteConcurrently() {
        val lane = LxRuntimeThread()
        val callers = Executors.newFixedThreadPool(8)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val concurrent = AtomicInteger()
        try {
            val results = (0 until 32).map {
                callers.submit<Thread> {
                    release.await()
                    lane.call {
                        if (active.incrementAndGet() != 1) concurrent.incrementAndGet()
                        try {
                            // Reentrant access, e.g. a native callback, stays on the owner.
                            assertSame(Thread.currentThread(), lane.call { Thread.currentThread() })
                            Thread.currentThread()
                        } finally {
                            active.decrementAndGet()
                        }
                    }
                }
            }
            release.countDown()
            val threads = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, threads.distinct().size)
            assertEquals(0, concurrent.get())
        } finally {
            callers.shutdownNow()
            lane.close {}
        }
    }

    @Test
    fun closeWaitsForActiveCallAndRunsCleanupOnOwner() {
        val lane = LxRuntimeThread()
        val callers = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        try {
            val call = callers.submit<Thread> {
                lane.call {
                    entered.countDown()
                    assertTrue(unblock.await(5, TimeUnit.SECONDS))
                    Thread.currentThread()
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            var cleanupThread: Thread? = null
            lane.close {
                cleanupThread = Thread.currentThread()
                cleaned.countDown()
            }
            assertFalse(cleaned.await(50, TimeUnit.MILLISECONDS))
            unblock.countDown()
            val owner = call.get(5, TimeUnit.SECONDS)
            assertTrue(cleaned.await(5, TimeUnit.SECONDS))
            assertSame(owner, cleanupThread)
            assertTrue(runCatching { lane.call { Unit } }.exceptionOrNull() is IllegalStateException)
        } finally {
            unblock.countDown()
            callers.shutdownNow()
            lane.close {}
        }
    }

    @Test
    fun actionExceptionPropagatesWithoutKillingOwner() {
        val lane = LxRuntimeThread()
        try {
            val owner = lane.call { Thread.currentThread() }
            val expected = IllegalArgumentException("fixture")
            assertSame(expected, runCatching { lane.call { throw expected } }.exceptionOrNull())
            assertSame(owner, lane.call { Thread.currentThread() })
        } finally {
            lane.close {}
        }
    }
}
