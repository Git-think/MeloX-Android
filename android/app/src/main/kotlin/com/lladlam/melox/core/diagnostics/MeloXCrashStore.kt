package com.lladlam.melox.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last uncaught crash on disk. A process that dies cannot be read
 * back from logcat by pid, which is why exported logs used to stop at the
 * last audio-effect line.
 */
object MeloXCrashStore {
    private const val Tag = "MeloXCrash"
    private const val FileName = "last-crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(appContext, thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        crashFile(context).takeIf(File::isFile)?.readText().takeIf { !it.isNullOrBlank() }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val body = buildString {
            appendLine("time=${System.currentTimeMillis()}")
            appendLine("pid=${android.os.Process.myPid()}")
            appendLine("thread=${thread.name}")
            appendLine(trace)
        }
        runCatching { crashFile(context).writeText(body) }
            .onFailure { Log.e(Tag, "Unable to persist crash", it) }
        Log.e(Tag, body)
    }

    private fun crashFile(context: Context): File = File(context.filesDir, FileName)
}
