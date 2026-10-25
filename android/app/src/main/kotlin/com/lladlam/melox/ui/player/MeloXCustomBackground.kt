package com.lladlam.melox.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom-image variant of [MeloXBlurredArtworkBackdrop].
 *
 * The picked file is rendered by the exact same blur and scrim pipeline as the
 * blurred-artwork mode, so the lyrics keep the contrast that treatment was
 * tuned for. A missing or unreadable file falls back to the artwork backdrop
 * rather than leaving a black screen. The existence check runs off the main
 * thread; the artwork backdrop is shown until it completes.
 */
@Composable
internal fun MeloXCustomImageBackdrop(
    path: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val file by produceState<File?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            path?.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
        }
    }
    if (file == null) {
        MeloXBlurredArtworkBackdrop(artworkUrl, modifier)
        return
    }
    val context = LocalContext.current
    val model = remember(context, file) {
        ImageRequest.Builder(context)
            .data(file)
            // Bound the decoded bitmap; the 38dp blur hides the downscale.
            .size(720, 1280)
            .crossfade(220)
            .build()
    }
    MeloXBlurredBackdrop(model, modifier)
}
