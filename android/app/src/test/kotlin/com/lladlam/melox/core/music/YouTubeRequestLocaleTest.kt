package com.lladlam.melox.core.music

import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class YouTubeRequestLocaleTest {
    @Test
    fun regionlessAppLanguagesKeepTheirLanguageAndGetAValidBrowseRegion() {
        listOf("zh-Hans", "zh-Hant", "en", "ja", "tr").forEach { language ->
            val locale = YouTubeLocale.fromLocale(Locale.forLanguageTag(language))
            assertEquals("US", locale.gl)
            assertEquals(language, locale.hl)
        }
    }

    @Test
    fun chinaLocaleUsesAWorkingMusicRegionWithoutChangingTheUiLanguage() {
        val locale = YouTubeLocale.fromLocale(Locale.SIMPLIFIED_CHINESE)
        assertEquals("US", locale.gl)
        assertEquals("zh-CN", locale.hl)
    }

    @Test
    fun supportedExplicitRegionsArePreserved() {
        listOf("en-US" to "US", "zh-TW" to "TW", "ja-JP" to "JP").forEach { (tag, region) ->
            assertEquals(region, YouTubeLocale.fromLocale(Locale.forLanguageTag(tag)).gl)
        }
    }

    @Test
    fun everyClientContextNormalizesRawLocaleAssignments() {
        listOf("", "CN", "not-a-country").forEach { region ->
            val context = YouTubeClient.WEB_REMIX.toContext(YouTubeLocale(region, "zh-Hans"), null, null)
            assertEquals("US", context.client.gl)
            assertEquals("zh-Hans", context.client.hl)
        }
        assertEquals("en", YouTubeLocale.fromLocale(Locale.ROOT).hl)
    }
}
