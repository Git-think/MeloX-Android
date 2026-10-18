package com.lladlam.melox.core.music

import com.metrolist.innertube.utils.YouTubeAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAuthHeadersTest {
    @Test
    fun secureBrowserCookieWinsOverAnOlderLegacyCookie() {
        val headers = YouTubeAuth.headers("SAPISID=legacy;__Secure-3PAPISID=secure-three", 1700000000)
        assertEquals("SAPISIDHASH 1700000000_875f2fdfb57f28b127540632a932b7ee76648920", headers["Authorization"])
        assertEquals("https://music.youtube.com", headers["Origin"])
        assertEquals(headers["Origin"], headers["X-Origin"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("true", headers["X-Youtube-Bootstrap-Logged-In"])
        assertFalse(headers.containsKey("X-Goog-PageId"))
    }

    @Test
    fun selectedGoogleAccountAndBrandChannelTravelTogether() {
        val headers = YouTubeAuth.headers("__Secure-3PAPISID=secure-three", 1700000000, "2", "brand-channel")
        assertEquals("2", headers["X-Goog-AuthUser"])
        assertEquals("brand-channel", headers["X-Goog-PageId"])
    }

    @Test
    fun legacyOnlySessionsStillProduceTheExpectedSignature() {
        val headers = YouTubeAuth.headers("SAPISID=legacy", 1700000000, "undefined")
        assertEquals("SAPISIDHASH 1700000000_b3f9f8987d92b924d092f93faf18b3f86c89f296", headers["Authorization"])
        assertEquals("0", headers["X-Goog-AuthUser"])
    }

    @Test
    fun visitorCookiesAndEmptyAuthValuesAreNotAuthenticated() {
        assertTrue(YouTubeAuth.headers("VISITOR_INFO1_LIVE=visitor; SAPISID=", 1700000000).isEmpty())
        assertFalse(YouTubeAuth.hasAuthCookie("other=SAPISID=not-auth"))
        assertTrue(YouTubeAuth.hasAuthCookie("__Secure-3PAPISID=secure-three"))
    }
}
