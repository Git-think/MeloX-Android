package androidx.media3.session;

import androidx.media3.common.util.UnstableApi;

/**
 * Access to the existing platform session for player-owned lyric metadata.
 * Kept in Media3's package because Media3 1.10.1 exposes only the platform
 * token internally. Direct calls remain R8-safe because the whole chain is
 * resolved at compile time (ported from Mei_MeloX_Android).
 */
@UnstableApi
public final class PlatformLyricsSessionAccess {
    private PlatformLyricsSessionAccess() {}

    public static android.media.session.MediaSession get(MediaSession session) {
        return (android.media.session.MediaSession) session.getImpl()
                .getMediaSessionLegacyStub().getSessionCompat().getMediaSession();
    }
}
