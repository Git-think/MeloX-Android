package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicAccountSummary
import com.lladlam.melox.core.music.model.MusicAlbumDetail
import com.lladlam.melox.core.music.model.MusicAlbumSummary
import com.lladlam.melox.core.music.model.MusicArtistDetail
import com.lladlam.melox.core.music.model.MusicArtistSummary
import com.lladlam.melox.core.music.model.MusicHomeFeed
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicPlaylistDetail
import com.lladlam.melox.core.music.model.MusicPlaylistSummary
import com.lladlam.melox.core.music.model.MusicRankingSummary
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.provider.AlbumCapability
import com.lladlam.melox.core.music.provider.ArtistCapability
import com.lladlam.melox.core.music.provider.CatalogSearchCapability
import com.lladlam.melox.core.music.provider.HomeFeedCapability
import com.lladlam.melox.core.music.provider.LyricsCapability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.PlaylistCapability
import com.lladlam.melox.core.music.provider.PlaylistWriteCapability
import com.lladlam.melox.core.music.provider.RankingCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.UserLibraryCapability
import java.time.LocalDate
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * 酷狗概念版 (KuGou lite) provider.
 *
 * This is an independent [MusicSource] rather than a mode of the standard
 * [KugouProvider]: the two apps have different appids, signature salts, login
 * tokens and quality entitlements, and the standard token must never be sent to
 * a lite endpoint (or vice versa).
 */
class KugouLiteProvider(
    private val sessionProvider: () -> KugouSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
    persistAuth: (String) -> Unit = {},
    persistVip: (KugouLiteVip) -> Unit = {},
    persistDfid: (String) -> Unit = {},
    onAuthExpired: () -> Unit = {},
) : MusicProvider,
    SearchCapability,
    CatalogSearchCapability,
    LyricsCapability,
    PlaybackCapability,
    HomeFeedCapability,
    UserLibraryCapability,
    PlaylistCapability,
    PlaylistWriteCapability,
    RankingCapability,
    AlbumCapability,
    ArtistCapability {
    override val source: MusicSource = MusicSource.KugouLite
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(
        MusicCapability.Search,
        MusicCapability.Playback,
        MusicCapability.Lyrics,
        MusicCapability.Library,
        MusicCapability.Playlists,
        MusicCapability.PlaylistWrite,
        MusicCapability.Albums,
        MusicCapability.Artists,
        MusicCapability.HomeRecommendations,
        MusicCapability.Rankings,
    )

    private val api = KugouLiteApiClient(
        sessionProvider = sessionProvider,
        httpClient = httpClient,
        persistAuth = persistAuth,
        persistVip = persistVip,
        persistDfid = persistDfid,
        onAuthExpired = onAuthExpired,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        api.searchSongs(query, page, pageSize)

    override suspend fun searchPlaylists(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicPlaylistSummary> = api.searchPlaylists(query, page, pageSize)

    override suspend fun searchAlbums(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicAlbumSummary> = api.searchAlbums(query, page, pageSize)

    override suspend fun searchArtists(
        query: String,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicArtistSummary> = api.searchArtists(query, page, pageSize)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument = api.lyricDocument(track)

    override suspend fun resolvePlayback(
        track: MusicTrack,
        quality: AudioQualityTier,
    ): PlaybackResolution = api.resolvePlayback(track, quality)

    override suspend fun homeFeed(
        playlistLimit: Int,
        newSongLimit: Int,
        rankingLimit: Int,
    ): MusicHomeFeed {
        val newSongs = runCatching { api.homeFeedNewSongs(newSongLimit) }.getOrDefault(emptyList())
        val playlists = runCatching { api.recommendedPlaylists(playlistLimit) }.getOrDefault(emptyList())
        val rankings = runCatching { api.rankings(rankingLimit) }.getOrDefault(emptyList())
        return MusicHomeFeed(
            recommendedPlaylists = playlists,
            newSongs = newSongs,
            rankings = rankings,
        )
    }

    override suspend fun accountSummary(): MusicAccountSummary? = api.accountSummary()

    override suspend fun userPlaylists(page: Int, pageSize: Int): MusicPage<MusicPlaylistSummary> =
        api.userPlaylists(page, pageSize)

    override suspend fun playlistDetail(
        playlist: MusicPlaylistSummary,
        page: Int,
        pageSize: Int,
    ): MusicPlaylistDetail = api.playlistDetail(playlist, page, pageSize)

    override suspend fun writablePlaylists(
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicPlaylistSummary> = api.writablePlaylists(page, pageSize)

    override suspend fun addTrackToPlaylist(
        track: MusicTrack,
        playlist: MusicPlaylistSummary,
    ) {
        api.addTrackToPlaylist(track, playlist)
    }

    override suspend fun rankingTracks(
        ranking: MusicRankingSummary,
        page: Int,
        pageSize: Int,
    ): MusicPage<MusicTrack> = api.rankingTracks(ranking, page, pageSize)

    override suspend fun albumDetail(
        album: MusicAlbumSummary,
        page: Int,
        pageSize: Int,
    ): MusicAlbumDetail = api.albumDetail(album, page, pageSize)

    override suspend fun artistDetail(
        artist: MusicArtistSummary,
        page: Int,
        pageSize: Int,
    ): MusicArtistDetail = api.artistDetail(artist, page, pageSize)

    suspend fun songDetail(track: MusicTrack): MusicTrack = api.songDetail(track)

    suspend fun vipStatus(force: Boolean = false): KugouLiteVip = api.vipStatus(force)

    suspend fun claimDailyVip(date: LocalDate = LocalDate.now()): JSONObject = api.claimDailyVip(date)
}
