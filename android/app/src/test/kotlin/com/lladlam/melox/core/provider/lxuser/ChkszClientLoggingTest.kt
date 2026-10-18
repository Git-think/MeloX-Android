package com.lladlam.melox.core.provider.lxuser

import org.junit.Assert.assertEquals
import org.junit.Test

class ChkszClientLoggingTest {

    @Test
    fun redactedParamsKeepEverythingButTheKey() {
        val redacted = redactedChkszParams(
            mapOf("id" to "12345", "level" to "lossless", "type" to "json", "apikey" to "SECRET"),
        )
        assertEquals("id=12345,level=lossless,type=json", redacted)
    }

    @Test
    fun redactedParamsHandleCaseInsensitiveKeyName() {
        val redacted = redactedChkszParams(mapOf("ApiKey" to "SECRET", "id" to "1"))
        assertEquals("id=1", redacted)
    }
}
