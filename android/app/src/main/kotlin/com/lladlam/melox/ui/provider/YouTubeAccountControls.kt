package com.lladlam.melox.ui.provider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lladlam.melox.R
import com.lladlam.melox.core.provider.youtubemusic.YouTubeSessionStore
import com.lladlam.melox.ui.glass.*
import com.metrolist.innertube.models.YouTubeChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared by the full music-services page and the settings service sheet. */
@Composable
internal fun YouTubeAccountControls(onRelogin: () -> Unit) {
    val context = LocalContext.current
    val revision by YouTubeSessionStore.revision.collectAsState()
    val expired by YouTubeSessionStore.expired.collectAsState()
    val session = remember(revision) { YouTubeSessionStore.read(context) }
    val scope = rememberCoroutineScope()
    var showChannels by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<YouTubeChannel>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.cookie, session.authUser, session.pageId, session.dataSyncId) {
        YouTubeSessionStore.revalidate(context)
    }
    if (!session.isLoggedIn) return
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        MeloXIosListRow(
            title = stringResource(R.string.youtube_channel),
            subtitle = session.accountName,
            onClick = if (busy) null else ({
                showChannels = true
                channels = emptyList()
                error = null
                busy = true
                scope.launch {
                    try {
                        channels = YouTubeSessionStore.channels(context)
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        error = failure.message
                    } finally { busy = false }
                }
            }),
            showTopSeparator = false,
        )
        MeloXIosListRow(
            title = stringResource(R.string.youtube_refresh_login),
            subtitle = stringResource(if (expired) R.string.youtube_session_expired else R.string.youtube_refresh_login_hint),
            onClick = onRelogin,
        )
    }
    if (showChannels) {
        MeloXGlassDialog(visible = true, onDismiss = { if (!busy) showChannels = false }) {
            Text(stringResource(R.string.youtube_channel), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                if (busy) Text(stringResource(R.string.youtube_channels_loading))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!busy && error == null && channels.isEmpty()) Text(stringResource(R.string.youtube_channels_empty))
                channels.forEach { channel ->
                    val selected = if (session.pageId.isNotBlank()) channel.pageId == session.pageId
                        else channel.selected
                    MeloXIosListRow(
                        title = channel.name,
                        subtitle = channel.handle,
                        detail = if (selected) "✓" else null,
                        onClick = if (busy) null else ({
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    YouTubeSessionStore.useChannel(context, channel)
                                    showChannels = false
                                } catch (failure: Exception) {
                                    if (failure is CancellationException) throw failure
                                    error = failure.message
                                } finally { busy = false }
                            }
                        }),
                    )
                }
            }
            MeloXGlassButton(onClick = { if (!busy) showChannels = false }) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}
