package com.lladlam.melox.core.network

import java.io.File
import okio.Buffer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseCloudUploadProgressTest {
    @Test
    fun `progress tracks the whole temporary file`() {
        val file = File.createTempFile("melox-cloud-test", ".mp3")
        try {
            file.writeBytes(ByteArray(300 * 1024) { (it % 251).toByte() })
            var reported = 0L
            val body = CountingRequestBody(file, "audio/mpeg".toMediaType()) { reported = it }
            body.contentLength().also { length -> assertEquals(file.length(), length) }
            body.writeTo(Buffer())
            assertEquals(file.length(), reported)
            assertTrue(reported >= 64L * 1024L)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `nos object key encodes slashes and spaces`() {
        assertEquals("a%2Fb%20c", encodeCloudNosObjectKey("a/b c"))
    }
}
