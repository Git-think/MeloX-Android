package com.lladlam.melox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXGlassSurface
import com.lladlam.melox.ui.glass.meloXLiquidButton

/** Compact artwork-led output panel; the glass renderer remains shared with the player. */
@Composable
internal fun MeloXAudioOutputPlaybackPanel(
    visible: Boolean,
    state: MeloXPlaybackUiState,
    outputName: String,
    localSelected: Boolean,
    onOutputClick: () -> Unit,
    onDismiss: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    devices: @Composable ColumnScope.() -> Unit = {},
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.safeDrawingPadding().padding(horizontal = 28.dp)
                        .widthIn(max = 360.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(38.dp))
                        .meloXGlassSurface(
                            shape = RoundedCornerShape(38.dp),
                            tint = Color.Black.copy(alpha = .10f),
                            surfaceColor = Color.Black.copy(alpha = .28f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = state.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = .12f)),
                        )
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.title.ifBlank { stringResource(R.string.player_now_playing) },
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(state.artist, state.album).filter(String::isNotBlank).distinct().joinToString(" – "),
                                color = Color.White.copy(alpha = .58f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutputTransportButton(MeloXSymbol.Previous, stringResource(R.string.player_output_previous), state.hasPrevious, state::previous)
                                OutputTransportButton(
                                    if (state.isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play,
                                    stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                                    state.hasMedia,
                                    state::togglePlayPause,
                                    large = true,
                                )
                                OutputTransportButton(MeloXSymbol.Next, stringResource(R.string.player_output_next), state.hasNext, state::next)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SceneProgressControl(state = state, onShowQuality = {}, showQuality = false)
                    Spacer(Modifier.height(18.dp))
                    OutputPanelSlider(
                        value = state.volume,
                        label = stringResource(R.string.player_output_volume),
                        onChange = state::changeVolume,
                        onFinish = state::endVolumeDrag,
                        onStart = state::beginVolumeDrag,
                        volume = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    actions()
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(22.dp))
                            .meloXLiquidButton(
                                shape = RoundedCornerShape(22.dp),
                                surfaceColor = if (localSelected) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f),
                            )
                            .clickable(role = Role.Button, onClick = onOutputClick)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), if (localSelected) Color(0xFF4786FF) else Color.White)
                        Text(outputName, color = if (localSelected) Color.Black else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp))
                    }
                    devices()
                }
            }
        }
    }
}

@Composable
private fun OutputTransportButton(symbol: MeloXSymbol, label: String, enabled: Boolean, onClick: () -> Unit, large: Boolean = false) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol, Modifier.size(if (large) 32.dp else 26.dp),
            Color.White.copy(alpha = if (enabled) 1f else .35f),
            iconSize = if (large) 32.sp else 26.sp,
            contentDescription = label,
        )
    }
}

@Composable
private fun OutputPanelSlider(
    value: Float,
    label: String,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
    onStart: () -> Unit = {},
    enabled: Boolean = true,
    volume: Boolean = false,
) {
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val start by rememberUpdatedState(onStart)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { start(); change(it.coerceIn(0f, 1f)); finish(); true }
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { offset ->
                    start(); change((offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)); finish()
                }
            }
            .pointerInput(enabled) {
                if (enabled) detectDragGestures(
                    onDragStart = { start(); change((it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)) },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { event, _ ->
                    event.consume()
                    change((event.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(if (volume) 30.dp else 6.dp)
                .clip(CircleShape)
                .meloXGlassSurface(CircleShape, surfaceColor = Color.White.copy(alpha = .20f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(Color.White.copy(alpha = .95f), CircleShape))
        }
        if (volume) {
            MeloXSymbolIcon(
                MeloXSymbol.Volume,
                Modifier.padding(start = 12.dp).size(18.dp),
                if (value >= .13f) Color.Black.copy(alpha = .48f) else Color.White.copy(alpha = .78f),
                iconSize = 18.sp,
            )
        }
    }
}
