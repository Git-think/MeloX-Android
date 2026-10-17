package com.lladlam.melox.core.provider.lxuser

import com.lladlam.melox.core.music.model.AudioQualityTier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetingApiClientTest {

    @Test
    fun urlBuildsServerTypeIdAndLevel() {
        val url = buildMetingUrl("https://meting.example.com", "netease", "123", "lossless", "")!!
        assertEquals("meting.example.com", url.host)
        assertEquals("/api", url.encodedPath)
        assertEquals("netease", url.queryParameter("server"))
        assertEquals("url", url.queryParameter("type"))
        assertEquals("123", url.queryParameter("id"))
        assertEquals("lossless", url.queryParameter("level"))
        assertNull(url.queryParameter("auth"))
    }

    @Test
    fun urlKeepsExplicitApiPathAndAddsAuth() {
        val url = buildMetingUrl("https://meting.example.com/api", "tencent", "abc", null, "secret")!!
        assertEquals("/api", url.encodedPath)
        assertEquals("secret", url.queryParameter("auth"))
        assertNull(url.queryParameter("level"))
    }

    @Test
    fun invalidBaseReturnsNull() {
        assertNull(buildMetingUrl("not a url", "netease", "1", null, ""))
    }

    @Test
    fun findsUrlInMetingJsonShapes() {
        assertEquals(
            "https://cdn.example.com/a.mp3",
            findMetingUrl(JSONObject("""{"url":"https://cdn.example.com/a.mp3"}""")),
        )
        assertEquals(
            "https://cdn.example.com/b.mp3",
            findMetingUrl(JSONObject("""{"data":[{"url":"https://cdn.example.com/b.mp3"}]}""")),
        )
        assertEquals(
            "https://cdn.example.com/c.mp3",
            findMetingUrl(JSONObject("""{"data":{"playUrl":"https://cdn.example.com/c.mp3"}}""")),
        )
        assertNull(findMetingUrl(JSONObject("""{"url":"not-a-link"}""")))
    }

    @Test
    fun mapsTiersToNeteaseLevels() {
        assertEquals("standard", AudioQualityTier.Standard.toMetingLevel())
        assertEquals("exhigh", AudioQualityTier.High.toMetingLevel())
        assertEquals("lossless", AudioQualityTier.Lossless.toMetingLevel())
        assertEquals("hires", AudioQualityTier.HiResolution.toMetingLevel())
        assertEquals("jyeffect", AudioQualityTier.Immersive.toMetingLevel())
        assertEquals("jymaster", AudioQualityTier.Master.toMetingLevel())
    }
}
