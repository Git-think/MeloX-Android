package com.lladlam.melox.core.provider.qishui

import com.lladlam.melox.core.lyrics.LrcLyricsParser
import com.lladlam.melox.core.lyrics.LyricsDocument
import com.lladlam.melox.core.music.model.*
import com.lladlam.melox.core.music.provider.*
import okhttp3.OkHttpClient

class QishuiProvider(
    sessionProvider: () -> QishuiSession,
    httpClient: OkHttpClient = com.lladlam.melox.core.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, PlaybackCapability, LyricsCapability, HomeFeedCapability, PlaylistCapability, UserLibraryCapability {
    override val source = MusicSource.Qishui
    override val displayName = source.displayName
    override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback, MusicCapability.Lyrics,
        MusicCapability.HomeRecommendations, MusicCapability.Playlists, MusicCapability.Library)
    private val api = QishuiApiClient(sessionProvider, httpClient)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int) = api.search(query, page, pageSize)
    override suspend fun homeFeed(playlistLimit: Int, newSongLimit: Int, rankingLimit: Int) = api.home(playlistLimit)
    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) = api.playlist(playlist, page, pageSize)
    override suspend fun accountSummary() = api.profile()
    override suspend fun userPlaylists(page: Int, pageSize: Int) = api.userPlaylists(page, pageSize)

    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        val body = api.seo(track.id)
        return LrcLyricsParser.parse(QishuiParsing.text(body.optJSONObject("lyric"), "content"))
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        val body = api.playbackBody(track, quality)
        val fullDuration = body.optJSONObject("seo_track")?.optJSONObject("track")?.optLong("duration", 0)?.takeIf { it > 0 } ?: track.durationMs
        val audio = QishuiParsing.audio(body, fullDuration, quality)
            ?: return PlaybackResolution.Unavailable("汽水音乐未返回可播放的明文音频，或当前账号没有对应权限")
        if (audio.preview) return PlaybackResolution.Preview(audio.url, audio.durationMs)
        return PlaybackResolution.Playable(
            audio.url, mapOf("User-Agent" to QishuiApiClient.WebAgent), quality,
            actualQuality = if (audio.bitrate >= 256_000) AudioQualityTier.High else AudioQualityTier.Standard,
            bitrate = audio.bitrate.takeIf { it > 0 }, format = audio.format, expiresAtEpochMs = audio.expiresAtMs,
        )
    }
}
