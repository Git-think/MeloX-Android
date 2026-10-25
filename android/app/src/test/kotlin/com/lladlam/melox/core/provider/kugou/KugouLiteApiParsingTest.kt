package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouLiteApiParsingTest {
    @Test
    fun parsesFlatSearchTrack() {
        val item = JSONObject(
            """
            {
              "FileHash":"B9B3A55FFB5B95F165839A26120E9FEC",
              "SongName":"恒星不忘",
              "SingerName":"周杰伦、五月天",
              "AlbumID":"212310356",
              "AlbumName":"今夜让我带你高飞远走",
              "MixSongID":"977636900",
              "Duration":285,
              "Image":"http://imge.kugou.com/stdmusic/{size}/cover.jpg"
            }
            """.trimIndent(),
        )
        val parsed = KugouLiteParsing.parseTrack(item)
        assertNotNull(parsed)
        val track = parsed!!
        assertEquals(MusicSource.KugouLite, track.id.source)
        assertEquals("B9B3A55FFB5B95F165839A26120E9FEC", track.id.value)
        assertEquals("恒星不忘", track.title)
        assertEquals("周杰伦 / 五月天", track.artistText)
        assertEquals(285_000L, track.durationMs)
        assertEquals(
            "https://imge.kugou.com/stdmusic/400/cover.jpg",
            track.artworkUrl,
        )
        val metadata = track.providerMetadata as ProviderTrackMetadata.Kugou
        assertEquals(977636900L, metadata.albumAudioId)
        assertEquals("212310356", metadata.albumId)
    }

    @Test
    fun parsesFreeListenNestedTrack() {
        val item = JSONObject(
            """
            {
              "album_audio_id":"920474385",
              "mixsongid":"920474385",
              "songname":"甲乙丙丁 (你我怎么两清)",
              "author_name":"李佳薇",
              "filename":"李佳薇 - 甲乙丙丁 (你我怎么两清)",
              "album_info":{"album_id":"197648995","album_name":"甲乙丙丁","sizable_cover":"http://imge.kugou.com/stdmusic/{size}/a.jpg"},
              "audio_info":{"audio_id":"1106816298","hash":"213D580CA0BDCC28A5FDBA995FFDA106","timelength":"210000","extname":"mp3"}
            }
            """.trimIndent(),
        )
        val track = KugouLiteParsing.parseTrack(item)!!
        assertEquals("213D580CA0BDCC28A5FDBA995FFDA106", track.id.value)
        assertEquals("甲乙丙丁 (你我怎么两清)", track.title)
        assertEquals("李佳薇", track.artistText)
        assertEquals("甲乙丙丁", track.album?.name)
        assertEquals(210_000L, track.durationMs)
        assertEquals(920474385L, (track.providerMetadata as ProviderTrackMetadata.Kugou).albumAudioId)
    }

    @Test
    fun treatsFreeCopyrightPrivilegeAsFreeAndKeepsPaidQualitiesLocked() {
        val item = JSONObject(
            """
            {
              "hash":"B9B3A55FFB5B95F165839A26120E9FEC",
              "name":"恒星不忘",
              "singername":"周杰伦",
              "albumname":"专辑",
              "trans_param":{"ogg_128_hash":"7FE88CEBDCDBBBDCA966E5459272F42F","ogg_128_filesize":3339878},
              "relate_goods":[
                {"quality":"128","level":2,"privilege":8,"status":1,"pay_type":0,"_msg":"Allow: the audio is free(copyright).","info":{"bitrate":128,"extname":"mp3"}},
                {"quality":"320","level":4,"privilege":10,"status":0,"pay_type":3,"price":200,"info":{"bitrate":320,"extname":"mp3"}},
                {"quality":"flac","level":5,"privilege":10,"status":0,"pay_type":3,"price":200,"info":{"bitrate":1004,"extname":"flac"}}
              ]
            }
            """.trimIndent(),
        )
        val privilege = KugouLiteParsing.parsePrivilege(item, "fallback")
        val standard = privilege.qualities.first { it.quality == "128" }
        assertFalse(standard.needVip)
        assertFalse(standard.freeListen)
        assertTrue(standard.playableFor(KugouLiteVip.NONE))
        assertEquals("ogg", standard.extname)
        assertEquals("7FE88CEBDCDBBBDCA966E5459272F42F", standard.hash)

        val high = privilege.qualities.first { it.quality == "flac" }
        assertTrue(high.needVip)
        assertFalse(high.playableFor(KugouLiteVip.NONE))
        assertEquals("concept", high.requireVipType)
    }

    @Test
    fun conceptFreeListenUnlocksOnlyStandardQualityWithoutVip() {
        val item = JSONObject(
            """
            {
              "hash":"HASH",
              "relate_goods":[
                {"quality":"128","level":2,"privilege":10,"status":1,"pay_type":3,"price":200,"info":{"bitrate":128,"extname":"mp3"}},
                {"quality":"320","level":4,"privilege":10,"status":1,"pay_type":3,"price":200,"info":{"bitrate":320,"extname":"mp3"}}
              ]
            }
            """.trimIndent(),
        )
        val privilege = KugouLiteParsing.parsePrivilege(item, "HASH")
        val standard = privilege.qualities.first { it.quality == "128" }
        val high = privilege.qualities.first { it.quality == "320" }
        assertTrue(standard.freeListen)
        assertTrue(standard.playableFor(KugouLiteVip.NONE))
        assertFalse(high.freeListen)
        assertFalse(high.playableFor(KugouLiteVip.NONE))
    }

    @Test
    fun parsesSuperVipAndOrdinaryVip() {
        val superVip = KugouLiteParsing.parseVip(
            JSONObject("""{"data":{"vip_type":6,"is_vip":1,"vip_end_time":4102444800}}"""),
        )
        assertTrue(superVip.isVip)
        assertTrue(superVip.superVip)
        assertEquals(6, superVip.vipType)
        assertEquals(4_102_444_800L, superVip.expiresAtEpochSeconds)

        val ordinary = KugouLiteParsing.parseVip(
            JSONObject("""{"data":{"vip_type":2,"vip_end_time":4102444800000}}"""),
        )
        assertTrue(ordinary.isVip)
        assertFalse(ordinary.superVip)
    }

    @Test
    fun masterCandidatesAvoidServerTranscodeAndDsd() {
        val values = AudioQualityTier.Master.liteQualityCandidates().map { it.apiValue }
        assertFalse("viper_tape" in values)
        assertFalse("super" in values)
        assertTrue("viper_clear" in values)
        assertTrue("128" in values)
    }

    @Test
    fun findsFirstPlaybackUrlInKugouArrayResponse() {
        val response = JSONObject(
            """
            {
              "status":1,
              "url":["https://fs.example/a.mp3","https://fs.example/b.mp3"],
              "backupUrl":["https://fs.example/c.mp3"],
              "extName":"mp3"
            }
            """.trimIndent(),
        )
        assertEquals("https://fs.example/a.mp3", KugouLiteParsing.findPlaybackUrl(response))
    }

    @Test
    fun keepsLiteAndStandardSigningSecretsSeparate() {
        assertEquals(3116, KugouPlatform.Lite.appId)
        assertEquals(1005, KugouPlatform.Standard.appId)
        assertTrue(KugouPlatform.Lite.isLite)
        assertFalse(KugouPlatform.Standard.isLite)
        assertFalse(KugouPlatform.Lite.androidSignatureSalt == KugouPlatform.Standard.androidSignatureSalt)
    }
}
