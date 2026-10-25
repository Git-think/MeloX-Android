package com.lladlam.melox.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Keeps the player's custom background inside the app sandbox.
 *
 * The photo picker only grants a transient read permission, so the selected
 * image is copied into `filesDir` and the picker's URI is never persisted.
 */
internal object MeloXCustomBackgroundStore {
    private const val DirectoryName = "player-backgrounds"
    private const val MaxImportBytes = 48L * 1024 * 1024

    private fun directory(context: Context): File =
        File(context.filesDir, DirectoryName).apply { mkdirs() }

    /**
     * Copies [uri] into the app sandbox and returns the new absolute path, or
     * null when the image cannot be opened, exceeds [MaxImportBytes] or is not
     * decodable. On success every older file in the sandbox is pruned.
     */
    fun import(context: Context, uri: Uri): String? {
        val dir = directory(context)
        val target = File(dir, "custom-${System.currentTimeMillis()}.img")
        val copied = runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("the selected image cannot be opened")
            input.use { source ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MaxImportBytes) error("the selected image is too large")
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }.isSuccess
        if (!copied || !isDecodable(target)) {
            target.delete()
            return null
        }
        // The new file is the only one that may survive: this removes the
        // previous image plus any orphan left by an interrupted import.
        prune(context, target.absolutePath)
        return target.absolutePath
    }

    /**
     * Deletes [path] only when it lives in the app-private background folder.
     * Preferences are writable by the app itself, so this is defence in depth
     * against a stale/corrupt value pointing at an unrelated file.
     */
    fun delete(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        val directory = directory(context)
        val target = File(path)
        val parent = runCatching { target.canonicalFile.parentFile }.getOrNull() ?: return
        if (!parent.canonicalPath.equals(directory.canonicalPath)) return
        runCatching { target.delete() }
    }

    /** Removes every background file except [keepPath]. */
    fun prune(context: Context, keepPath: String?) {
        val directory = directory(context)
        if (!directory.isDirectory) return
        val keep = keepPath?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalPath }.getOrNull() }
        directory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            if (keep != null && runCatching { file.canonicalPath }.getOrNull() == keep) return@forEach
            runCatching { file.delete() }
        }
    }

    /** Decodes at most [maxWidth] pixels wide for the lyric-share renderer. */
    fun decodeSampled(path: String?, maxWidth: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) > maxWidth) sampleSize *= 2
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    private fun isDecodable(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }
}
