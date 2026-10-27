package com.lladlam.melox.core.provider.lxuser

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes a native QuickJS context's entire lifetime onto its creating thread. */
internal class LxRuntimeThread {
    private val closing = AtomicBoolean(false)
    @Volatile private var owner: Thread? = null
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread({
            owner = Thread.currentThread()
            task.run()
        }, "melox-lx-js").apply { isDaemon = true }
    }

    fun <T> call(block: () -> T): T {
        check(!closing.get()) { "LX runtime is closed" }
        if (Thread.currentThread() === owner) return block()
        val future = try {
            executor.submit<T> {
                check(!closing.get()) { "LX runtime is closed" }
                block()
            }
        } catch (error: RejectedExecutionException) {
            throw IllegalStateException("LX runtime is closed", error)
        }
        return try {
            future.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } catch (error: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw error
        }
    }

    /** Queue cleanup on the owner; do not block UI callers waiting for a script. */
    fun close(cleanup: () -> Unit) {
        if (!closing.compareAndSet(false, true)) return
        if (Thread.currentThread() === owner) {
            try { cleanup() } finally { executor.shutdown() }
        } else {
            try { executor.execute(cleanup) } finally { executor.shutdown() }
        }
    }
}
