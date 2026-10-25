package com.lladlam.melox.core.music.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeloXTrackJsonTest {
    private val album = MusicAlbumRef(
        id = MusicResourceId(MusicSource.QQMusic, "album-mid"),
        name = "叶惠美",
        artworkUrl = "https://example.com/cover.jpg",
    )

    private fun track(metadata: ProviderTrackMetadata, source: MusicSource = MusicSource.QQMusic) = MusicTrack(
        id = MusicResourceId(source, "track-id"),
        title = "晴天",
        artists = listOf(
            MusicArtistRef(MusicResourceId(MusicSource.QQMusic, "artist-mid"), "周杰伦"),
            MusicArtistRef(name = "费玉清"),
        ),
        album = album,
        artworkUrl = "https://example.com/cover.jpg",
        durationMs = 269_000L,
        availability = TrackAvailability.Playable,
        providerMetadata = metadata,
        bestQuality = AudioQualityTier.Lossless,
        bestBitrate = 1_000_000,
    )

    @Test fun roundTripsEveryProviderMetadataShape() {
        val values = listOf(
            track(ProviderTrackMetadata.Empty),
            track(ProviderTrackMetadata.Netease(123456L), MusicSource.Netease),
            track(ProviderTrackMetadata.QQMusic("song-mid", "media-mid", 42L)),
            track(ProviderTrackMetadata.Kugou("HASH", 7L, "album-id"), MusicSource.Kugou),
            track(ProviderTrackMetadata.Kuwo(99L), MusicSource.Kuwo),
            track(ProviderTrackMetadata.AppleMusic("apple-id", "cn", "https://preview"), MusicSource.AppleMusic),
            track(ProviderTrackMetadata.Bilibili("BV1xx", 123L, 456L, 2), MusicSource.Bilibili),
            track(ProviderTrackMetadata.Spotify("spotify-id", "ISRC"), MusicSource.Spotify),
            track(ProviderTrackMetadata.YouTube("video-id", "set-id"), MusicSource.YouTubeMusic),
            track(
                ProviderTrackMetadata.Local("content://media/1", "file-key"),
                MusicSource.Local,
            ),
        )
        values.forEach { original ->
            assertEquals(original, MeloXTrackJson.decode(MeloXTrackJson.encode(original)))
        }
    }

    @Test fun malformedOrUnknownMetadataFallsBackWithoutDroppingTheTrack() {
        val value = MeloXTrackJson.encode(track(ProviderTrackMetadata.Empty)).put(
            "metadata",
            org.json.JSONObject().put("type", "future-provider"),
        )
        val decoded = MeloXTrackJson.decode(value)
        assertEquals(ProviderTrackMetadata.Empty, decoded?.providerMetadata)
        assertEquals("晴天", decoded?.title)
    }

    @Test fun invalidCoreFieldsAreRejected() {
        assertNull(MeloXTrackJson.decode(org.json.JSONObject().put("source", "unknown")))
        assertNull(
            MeloXTrackJson.decode(
                org.json.JSONObject()
                    .put("source", "qq_music")
                    .put("id", "")
                    .put("title", "晴天"),
            ),
        )
    }
}
