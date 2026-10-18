package com.lladlam.melox.core.music

import com.lladlam.melox.core.provider.youtubemusic.hasYouTubeAuthCookie
import com.lladlam.melox.core.provider.youtubemusic.isYouTubeMusicPage
import com.lladlam.melox.core.provider.youtubemusic.mergeYouTubeCookies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLoginContextTest {
    @Test
    fun authRequiresAnExactNonEmptySapisidCookie() {
        assertTrue(hasYouTubeAuthCookie("__Secure-3PAPISID=other; SAPISID="))
        assertFalse(hasYouTubeAuthCookie("__Secure-3PAPISID=; SAPISID="))
        assertFalse(hasYouTubeAuthCookie("other=SAPISID=value"))
        assertTrue(hasYouTubeAuthCookie("VISITOR_INFO1_LIVE=visitor; SAPISID=value"))
    }

    @Test
    fun musicHostCookieWinsWithoutLosingParentDomainAuth() {
        assertEquals(
            "SID=music; VISITOR_INFO1_LIVE=visitor; SAPISID=auth=with=equals",
            mergeYouTubeCookies(listOf("SID=music; VISITOR_INFO1_LIVE=visitor", "SID=parent; SAPISID=auth=with=equals")),
        )
    }

    @Test
    fun onlyTheActualHttpsMusicHostProvidesLoginContext() {
        assertTrue(isYouTubeMusicPage("https://music.youtube.com/library"))
        assertFalse(isYouTubeMusicPage("https://accounts.google.com/?continue=https://music.youtube.com"))
        assertFalse(isYouTubeMusicPage("https://music.youtube.com.example.org"))
        assertFalse(isYouTubeMusicPage("http://music.youtube.com"))
        assertFalse(isYouTubeMusicPage(null))
    }
}
