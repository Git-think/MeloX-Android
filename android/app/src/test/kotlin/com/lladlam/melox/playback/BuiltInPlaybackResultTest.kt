package com.lladlam.melox.playback

import com.lladlam.melox.core.audio.MeloXPlaybackUnavailableException
import com.lladlam.melox.core.audio.NeteasePlaybackUnavailableException
import com.lladlam.melox.core.music.model.PlaybackResolution
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BuiltInPlaybackResultTest {
    @Test fun terminalNoSourceEntersExistingFallbackStage() = runBlocking {
        assertEquals(PlaybackResolution.SubscriptionRequired,
            builtInPlaybackResult { throw NeteasePlaybackUnavailableException("no full URL") })
        val result = builtInPlaybackResult { throw MeloXPlaybackUnavailableException("unavailable") }
        assertTrue(result is PlaybackResolution.Unavailable)
    }

    @Test fun officialSuccessAndLoginResultsAreUnchanged() = runBlocking {
        val result = PlaybackResolution.Playable("https://official.test/a.mp3", com.lladlam.melox.core.music.model.AudioQualityTier.High)
        assertSame(result, builtInPlaybackResult { result })
        assertEquals(PlaybackResolution.LoginRequired, builtInPlaybackResult { PlaybackResolution.LoginRequired })
    }

    @Test fun networkAndCancellationAreNotMisclassifiedAsNoSource() {
        for (error in listOf(IOException("offline"), CancellationException("cancelled"), IllegalArgumentException("invalid track"))) {
            val result = runCatching { runBlocking { builtInPlaybackResult { throw error } } }
            assertSame(error, result.exceptionOrNull())
        }
    }
}
