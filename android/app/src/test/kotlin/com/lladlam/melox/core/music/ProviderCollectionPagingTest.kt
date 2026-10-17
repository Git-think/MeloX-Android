package com.lladlam.melox.core.music

import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.loadAllPlaylistTracks
import com.lladlam.melox.core.music.provider.loadAllMusicPages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCollectionPagingTest {
    @Test
    fun followsGrowingUnknownTotalAndPreservesDistinctPlaylistMemberships() = runBlocking {
        val playlist = MusicPlaylistSummary(MusicResourceId(MusicSource.YouTubeMusic, "playlist"), "playlist")
        val calls = mutableListOf<Int>()
        val provider = object : PlaylistCapability {
            override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int): MusicPlaylistDetail {
                calls += page
                val tracks = (1..2).map { index ->
                    MusicTrack(
                        id = MusicResourceId(MusicSource.YouTubeMusic, "same-video"),
                        title = "track", artists = emptyList(),
                        providerMetadata = ProviderTrackMetadata.YouTube("same-video", setVideoId = "member-$page-$index"),
                    )
                }
                return MusicPlaylistDetail(playlist, tracks, if (page < 4) page * 2L + 1 else 8L)
            }
        }
        val result = provider.loadAllPlaylistTracks(playlist, pageSize = 2)
        assertEquals(listOf(1, 2, 3, 4), calls)
        assertEquals(8, result.tracks.size)
        assertEquals(8L, result.total)
    }

    @Test
    fun followsExplicitContinuationEvenWhenPageIsShort() = runBlocking {
        val calls = mutableListOf<Int>()
        val result = loadAllMusicPages(key = { value: Int -> value }) { page ->
            calls += page
            MusicPage(listOf(page), page, 50, hasMore = page < 3)
        }
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun stopsWhenProviderRepeatsFirstPageDespiteClaimingMore() = runBlocking {
        val calls = mutableListOf<Int>()
        val result = loadAllMusicPages(key = { value: Int -> value }) { page ->
            calls += page
            MusicPage(listOf(1, 2), page, 2, hasMore = true)
        }
        assertEquals(listOf(1, 2), calls)
        assertEquals(listOf(1, 2), result)
    }

    @Test
    fun removesOverlapAndKeepsSourceQualifiedIds() = runBlocking {
        val qq = MusicResourceId(MusicSource.QQMusic, "123")
        val youtube = MusicResourceId(MusicSource.YouTubeMusic, "123")
        val result = loadAllMusicPages(key = { value: MusicResourceId -> value }) { page ->
            MusicPage(if (page == 1) listOf(qq) else listOf(qq, youtube), page, 2, hasMore = page == 1)
        }
        assertEquals(listOf(qq, youtube), result)
    }

    @Test
    fun terminatesOnEmptyPageWithStaleTotal() = runBlocking {
        val calls = mutableListOf<Int>()
        val result = loadAllMusicPages(key = { value: Int -> value }) { page ->
            calls += page
            MusicPage(if (page == 1) listOf(1) else emptyList(), page, 1, total = 999)
        }
        assertEquals(listOf(1, 2), calls)
        assertEquals(listOf(1), result)
    }
}
