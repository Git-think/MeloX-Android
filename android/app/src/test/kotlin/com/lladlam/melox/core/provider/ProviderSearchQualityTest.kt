package com.lladlam.melox.core.provider

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.provider.kugou.kugouSearchQuality
import com.lladlam.melox.core.provider.kuwo.kuwoSearchQuality
import com.lladlam.melox.core.provider.qqmusic.qqMusicQualityHint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderSearchQualityTest {
    @Test fun qqLegacyFileSizesChooseTheBestTier() {
        val item = JSONObject().put(
            "file",
            JSONObject()
                .put("size_128mp3", 1_000)
                .put("size_320mp3", 3_000)
                .put("size_flac", 9_000),
        )
        assertEquals(AudioQualityTier.Lossless, qqMusicQualityHint(item)?.tier)
    }

    @Test fun qqNewSizeListUsesFormatAndBitrate() {
        val item = JSONObject().put(
            "file",
            JSONObject().put(
                "size_new",
                org.json.JSONArray(
                    """[{"format":"mp3","bitrate":320},{"format":"flac","bitrate":1000}]""",
                ),
            ),
        )
        assertEquals(AudioQualityTier.Lossless, qqMusicQualityHint(item)?.tier)
        assertEquals(1_000_000, qqMusicQualityHint(item)?.bitrate)
    }

    @Test fun qqNewSizeListRecognizesMasterAndSpatial() {
        val master = JSONObject().put(
            "file",
            JSONObject().put(
                "size_new",
                org.json.JSONArray("""[{"format":"master","bitrate":1411},{"format":"flac","bitrate":1000}]"""),
            ),
        )
        assertEquals(AudioQualityTier.Master, qqMusicQualityHint(master)?.tier)
        val spatial = JSONObject().put(
            "file",
            JSONObject().put(
                "size_new",
                org.json.JSONArray("""[{"format":"atmos","bitrate":768},{"format":"mp3","bitrate":320}]"""),
            ),
        )
        assertEquals(AudioQualityTier.Immersive, qqMusicQualityHint(spatial)?.tier)
    }

    @Test fun qqMissingFileMeansNoClaim() {
        assertNull(qqMusicQualityHint(JSONObject().put("name", "晴天")))
    }

    @Test fun kugouSizeFieldsChooseTheBestTier() {
        val item = JSONObject()
            .put("FileSize", "100")
            .put("HQFileSize", "200")
            .put("SQFileSize", "900")
        assertEquals(AudioQualityTier.Lossless, kugouSearchQuality(item)?.tier)
    }

    @Test fun kugouOnlyDefaultFileStaysStandard() {
        val item = JSONObject().put("FileSize", "100")
        assertEquals(AudioQualityTier.Standard, kugouSearchQuality(item)?.tier)
    }

    @Test fun kuwoMinfoChoosesFlac() {
        val item = JSONObject().put(
            "N_MINFO",
            "level:p,bitrate:128,format:mp3;level:p,bitrate:320,format:mp3;level:ff,bitrate:1000,format:flac",
        )
        assertEquals(AudioQualityTier.Lossless, kuwoSearchQuality(item)?.tier)
        assertEquals(1_000_000, kuwoSearchQuality(item)?.bitrate)
    }

    @Test fun kuwoMissingInfoMeansNoClaim() {
        assertNull(kuwoSearchQuality(JSONObject().put("NAME", "晴天")))
    }
}
