package com.lladlam.melox.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NeteaseCloudUploadTest {
    @Test
    fun nosObjectKeyMatchesIosWholePathPercentEncoding() {
        assertEquals(
            "jd-musicrep-privatecloud-audio-public%2F2026%2Ftrack%20one.mp3",
            encodeCloudNosObjectKey("jd-musicrep-privatecloud-audio-public/2026/track one.mp3"),
        )
    }

    @Test
    fun nosObjectKeyDoesNotLeaveRawSlashesOrSpaces() {
        val encoded = encodeCloudNosObjectKey("a/b c?d#e")
        assertFalse(encoded.contains('/'))
        assertFalse(encoded.contains(' '))
        assertFalse(encoded.contains('?'))
        assertFalse(encoded.contains('#'))
    }
}
