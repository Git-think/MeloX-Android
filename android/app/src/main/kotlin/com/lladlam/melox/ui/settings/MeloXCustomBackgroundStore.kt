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

    /**
     * Copies [uri] into the app sandbox and returns the new absolute path, or
     * null when the image cannot be opened, exceeds [MaxImportBytes] or is not
     * decodable. The previously imported file is removed on success.
     */
    fun import(context: Context, uri: Uri, previousPath: String?): String? {
        val directory = File(context.filesDir, DirectoryName).apply { mkdirs() }
        val target = File(directory, "custom-${System.currentTimeMillis()}.img")
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
        delete(previousPath)
        return target.absolutePath
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /** Decodes at most [maxWidth] pixels wide for the lyric-share renderer. */
    fun decodeSampled(path: String?, maxWidth: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth) sampleSize *= 2
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
