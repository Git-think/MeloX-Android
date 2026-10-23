package com.lladlam.melox.lan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXLanProtocolTest {
    @Test
    fun `playback state round trips through json`() {
        val state = MeloXLanPlayback(
            playing = true,
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            artworkUrl = "https://example.com/a.jpg",
            positionMs = 31_000L,
            durationMs = 269_000L,
            volume = 72,
            trackId = "netease:186016",
            source = "netease",
            index = 3,
            queueLength = 42,
            canNext = true,
            canPrevious = true,
        )
        val decoded = MeloXLanPlayback.fromJson(JSONObject(state.toJson().toString()))
        assertEquals(state, decoded)
    }

    @Test
    fun `track json keeps netease identity and rejects providers in v1 playback`() {
        val track = MeloXLanTrack(
            source = "netease",
            id = "186016",
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            durationMs = 269_000L,
        )
        val decoded = MeloXLanTrack.fromJson(JSONObject(track.toJson().toString()))!!
        assertEquals(track, decoded)
        assertEquals(186016L, decoded.toSearchSong()?.id)

        val provider = track.copy(source = "qq", id = "abc")
        assertNull(provider.toSearchSong())
        assertTrue(MeloXLanTrack.fromJsonArray(JSONArray().put(provider.toJson())).isNotEmpty())
    }

    @Test
    fun `relay token round trips and requires both parts`() {
        val relay = MeloXLanRelay("http://192.168.1.20:51234", "abc123")
        assertEquals(relay, MeloXLanRelay.fromJson(JSONObject(relay.toJson().toString())))
        assertNull(MeloXLanRelay.fromJson(JSONObject().put("baseUrl", "http://x")))
        assertNull(MeloXLanRelay.fromJson(JSONObject().put("token", "x")))
    }

    @Test
    fun `cast format clamps hostile values`() {
        val format = MeloXLanCastFormat.fromJson(
            JSONObject().put("codec", "pcm_s16le").put("sampleRate", 1_000_000).put("channels", 9),
        )
        assertEquals(192_000, format.sampleRate)
        assertEquals(2, format.channels)
        assertEquals("pcm_s16le", format.codec)
    }

    @Test
    fun `unknown or blank messages do not become tracks`() {
        assertNull(MeloXLanTrack.fromJson(null))
        assertNull(MeloXLanTrack.fromJson(JSONObject().put("source", "").put("id", "1")))
        assertFalse(MeloXLanTrack.fromJson(JSONObject().put("source", "netease").put("id", "1"))!!.id.isBlank())
    }
}
