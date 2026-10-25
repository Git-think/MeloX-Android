package com.lladlam.melox.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.dlna.DlnaDevice
import com.lladlam.melox.dlna.DlnaDiscovery
import com.lladlam.melox.dlna.DlnaSession
import com.lladlam.melox.miplay.MiPlaySession
import com.lladlam.melox.usb.MeloXUsbAudioSession
import androidx.compose.runtime.DisposableEffect
import com.lladlam.melox.lan.MeloXLanConnectResult
import com.lladlam.melox.lan.MeloXLanDevice
import com.lladlam.melox.lan.MeloXLanOutputMode
import com.lladlam.melox.lan.MeloXLanPermissions
import com.lladlam.melox.lan.MeloXLanRuntime
import com.lladlam.melox.lan.MeloXLanSession
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.glass.meloXLiquidButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Always composed by the app host; only discovery is scoped to panel visibility. */
@Composable
internal fun MeloXLanOutputPanel(
    visible: Boolean,
    state: MeloXPlaybackUiState,
    outputName: String,
    onLocalOptions: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<MeloXLanDevice>>(emptyList()) }
    var renderers by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var scanningDlna by remember { mutableStateOf(false) }
    var pinTarget by remember { mutableStateOf<MeloXLanDevice?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var provideAudio by remember { mutableStateOf(true) }
    var permissionGranted by remember { mutableStateOf(MeloXLanPermissions.has(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    DisposableEffect(visible) {
        if (visible) MeloXUsbAudioSession.refreshDevices(context)
        MiPlaySession.setPanelVisible(context, visible)
        onDispose { MiPlaySession.setPanelVisible(context, false) }
    }
    LaunchedEffect(visible) {
        if (visible) {
            permissionGranted = MeloXLanPermissions.has(context)
            if (!permissionGranted) permissionLauncher.launch(MeloXLanPermissions.permission())
        } else {
            pinTarget = null
            pin = ""
        }
    }
    LaunchedEffect(visible, permissionGranted) {
        if (!visible || !permissionGranted) return@LaunchedEffect
        MeloXLanRuntime.ensureStarted(context)
        while (true) {
            try {
                devices = MeloXLanRuntime.scan(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = context.getString(R.string.lan_scan_failed)
            }
            delay(2_000L)
        }
    }

    LaunchedEffect(visible, permissionGranted) {
        if (!visible || !permissionGranted) return@LaunchedEffect
        val known = linkedMapOf<String, Pair<DlnaDevice, Long>>()
        try {
            while (true) {
                scanningDlna = true
                try {
                    val found = DlnaDiscovery.scan(context)
                    val now = android.os.SystemClock.elapsedRealtime()
                    found.forEach { known[it.id] = it to now }
                    known.entries.removeAll { now - it.value.second > 180_000L }
                    renderers = known.values.map { it.first }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message }
                scanningDlna = false
                delay(5_000)
            }
        } finally { scanningDlna = false }
    }

    fun connect(device: MeloXLanDevice, suppliedPin: String? = null) {
        if (MeloXLanSession.busy || MiPlaySession.busy || DlnaSession.busy) return
        error = null
        scope.launch {
            try {
                when (MeloXLanSession.connect(context, device, suppliedPin)) {
                    MeloXLanConnectResult.Connected -> { pinTarget = null; pin = "" }
                    MeloXLanConnectResult.PinRequired -> { pinTarget = device; pin = "" }
                    MeloXLanConnectResult.Failed -> { error = context.getString(R.string.lan_connect_failed) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { error = context.getString(R.string.lan_connect_failed) }
        }
    }

    fun startAction(failure: Int, action: suspend () -> Boolean) {
        if (MeloXLanSession.busy || MiPlaySession.busy || DlnaSession.busy) return
        error = null
        scope.launch {
            try {
                if (action()) onDismiss() else error = context.getString(failure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { error = context.getString(failure) }
        }
    }

    val connection = MeloXLanSession.client
    val peer = MeloXLanSession.device
    val mode = MeloXLanSession.mode
    val guestTogether = MeloXLanRuntime.guestTogether
    val connected = connection != null && peer != null && !DlnaSession.active && !MiPlaySession.active && !MeloXUsbAudioSession.active
    val shownDevices = (MeloXLanSession.connections.map { it.device } + devices).distinctBy { it.deviceId }
    MeloXAudioOutputPlaybackPanel(
        visible = visible,
        state = state,
        outputName = MeloXUsbAudioSession.device?.productName ?: MiPlaySession.device?.name ?: DlnaSession.device?.name ?: outputName,
        localSelected = mode == MeloXLanOutputMode.Local && !DlnaSession.active && !MiPlaySession.active && !MeloXUsbAudioSession.active,
        onOutputClick = {
            if (MeloXUsbAudioSession.active) scope.launch { MeloXUsbAudioSession.stop() }
            else if (MiPlaySession.active) MiPlaySession.disconnect()
            else if (DlnaSession.active) scope.launch { DlnaSession.disconnect() }
            else if (mode == MeloXLanOutputMode.Local) onLocalOptions()
            else scope.launch { MeloXLanSession.useLocal() }
        },
        onDismiss = onDismiss,
        actions = {
            if (MiPlaySession.available) {
                Text(stringResource(R.string.miplay_devices), color = Color.White.copy(alpha = .60f),
                    fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                val xiaomiDevices = (listOfNotNull(MiPlaySession.device) + MiPlaySession.devices).distinctBy { it.id }
                xiaomiDevices.forEach { renderer ->
                    val active = MiPlaySession.device?.id == renderer.id
                    val foreground = if (active) Color.Black else Color.White
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp))
                            .meloXLiquidButton(shape = RoundedCornerShape(22.dp),
                                surfaceColor = if (active) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f))
                            .semantics { selected = active }
                            .clickable(enabled = MiPlaySession.ready && !MiPlaySession.busy && !MeloXLanSession.busy && !DlnaSession.busy, role = Role.Button) {
                                scope.launch { if (MiPlaySession.cast(renderer)) onDismiss() }
                            }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), if (active) MeloXSystemColors.Red else foreground)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(renderer.name, color = foreground, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(renderer.room.ifBlank { "MiPlay" }, color = foreground.copy(alpha = .55f), fontSize = 10.sp)
                        }
                    }
                }
                if (xiaomiDevices.isEmpty()) Text(stringResource(R.string.miplay_searching),
                    color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
                if (MiPlaySession.active) LanOutputAction(stringResource(R.string.miplay_disconnect), !MiPlaySession.busy, Modifier.fillMaxWidth()) {
                    MiPlaySession.disconnect()
                }
                MiPlaySession.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            if (connected) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanOutputAction(stringResource(R.string.lan_transfer), !MeloXLanSession.busy, Modifier.weight(1f), textColor = Color.Black) {
                        startAction(R.string.lan_transfer_failed) { MeloXLanSession.transfer() }
                    }
                    LanOutputAction(stringResource(R.string.lan_cast), !MeloXLanSession.busy, Modifier.weight(1f), textColor = if (mode == MeloXLanOutputMode.Cast) MeloXSystemColors.Red else Color.Black) {
                        startAction(R.string.lan_cast_failed) { MeloXLanSession.cast(context) }
                    }
                    LanOutputAction(stringResource(R.string.lan_together), !MeloXLanSession.busy, Modifier.weight(1f), textColor = if (mode == MeloXLanOutputMode.Together) MeloXSystemColors.Red else Color.Black) {
                        startAction(R.string.lan_together_failed) { MeloXLanSession.together(context, provideAudio) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(MeloXShapes.capsule)
                        .clickable(enabled = !MeloXLanSession.busy) { provideAudio = !provideAudio }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXSymbolIcon(
                        if (provideAudio) MeloXSymbol.CheckCircle else MeloXSymbol.Circle,
                        Modifier.size(18.dp), Color.White.copy(alpha = .75f),
                    )
                    Text(stringResource(R.string.lan_relay_audio), color = Color.White.copy(alpha = .75f), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (mode == MeloXLanOutputMode.Together || guestTogether) {
                LanOutputAction(stringResource(R.string.lan_together_stop), !MeloXLanSession.busy, Modifier.fillMaxWidth()) {
                    if (guestTogether) MeloXLanRuntime.stopGuestTogether()
                    else scope.launch { MeloXLanSession.useLocal() }
                }
                Spacer(Modifier.height(12.dp))
            }
        },
        devices = {
            UsbAudioOutputRows(onDismiss)
            shownDevices.forEach { device ->
                Spacer(Modifier.height(8.dp))
                val active = peer?.deviceId == device.deviceId && mode != MeloXLanOutputMode.Local
                val foreground = if (active) Color.Black else Color.White
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .meloXLiquidButton(
                            shape = RoundedCornerShape(22.dp),
                            surfaceColor = if (active) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f),
                        )
                        .semantics { selected = active }
                        .clickable(enabled = !MeloXLanSession.busy, role = Role.Button) {
                            if (MeloXLanSession.connections.any { it.device.deviceId == device.deviceId && it.isConnected }) {
                                startAction(R.string.lan_cast_failed) {
                                    MeloXLanSession.connect(context, device, null)
                                    MeloXLanSession.mode == MeloXLanOutputMode.Cast || MeloXLanSession.cast(context)
                                }
                            } else connect(device)
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), if (active) Color(0xFF4786FF) else foreground)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(device.name, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${device.address}:${device.port}" + if (peer?.deviceId == device.deviceId) " · ${stringResource(R.string.lan_connected)}" else "",
                            color = foreground.copy(alpha = .55f), fontSize = 10.sp,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.dlna_devices), color = Color.White.copy(alpha = .60f),
                fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            val dlnaDevices = (listOfNotNull(DlnaSession.device) + renderers).distinctBy { it.id }
            dlnaDevices.forEach { renderer ->
                val active = DlnaSession.device?.id == renderer.id
                val foreground = if (active) Color.Black else Color.White
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp))
                        .meloXLiquidButton(shape = RoundedCornerShape(22.dp),
                            surfaceColor = if (active) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f))
                        .semantics { selected = active }
                        .clickable(enabled = !DlnaSession.busy && !MeloXLanSession.busy && !MiPlaySession.busy, role = Role.Button) {
                            scope.launch {
                                if (active || DlnaSession.cast(context, renderer)) onDismiss()
                            }
                        }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), if (active) MeloXSystemColors.Red else foreground)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(renderer.name, color = foreground, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(renderer.manufacturer, renderer.model).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                            color = foreground.copy(alpha = .55f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (dlnaDevices.isEmpty()) Text(
                stringResource(if (scanningDlna) R.string.dlna_searching else R.string.dlna_empty),
                color = Color.White.copy(alpha = .45f), fontSize = 11.sp,
            )
            if (DlnaSession.active) LanOutputAction(stringResource(R.string.dlna_disconnect), !DlnaSession.busy, Modifier.fillMaxWidth()) {
                scope.launch { DlnaSession.disconnect() }
            }
            DlnaSession.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lan_pairing_pin, MeloXLanRuntime.pin(context)), color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
                if (connected) {
                    Text(
                        stringResource(R.string.lan_close_device), color = Color.White.copy(alpha = .75f), fontSize = 11.sp,
                        modifier = Modifier.clip(MeloXShapes.capsule).clickable(enabled = !MeloXLanSession.busy) { MeloXLanSession.disconnect() }.padding(10.dp),
                    )
                }
            }
            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        },
    )

    MeloXGlassDialog(visible = visible && pinTarget != null, onDismiss = { pinTarget = null; pin = "" }) {
        Text(stringResource(R.string.lan_pin_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.lan_pin_hint, pinTarget?.name.orEmpty()), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        MeloXGlassTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4) },
            placeholder = { Text(stringResource(R.string.lan_pin_hint_short)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanOutputAction(stringResource(R.string.action_cancel), !MeloXLanSession.busy, Modifier.weight(1f)) { pinTarget = null; pin = "" }
            LanOutputAction(stringResource(R.string.lan_connect), !MeloXLanSession.busy && pin.length == 4, Modifier.weight(1f)) { pinTarget?.let { connect(it, pin) } }
        }
        error?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun LanOutputAction(label: String, enabled: Boolean, modifier: Modifier, textColor: Color = Color.Unspecified, onClick: () -> Unit) {
    MeloXGlassButton(onClick = onClick, enabled = enabled, modifier = modifier, style = MeloXGlassButtonStyle.Bordered, shape = MeloXShapes.capsule) {
        Text(label, color = textColor, fontSize = 12.sp, maxLines = 1)
    }
}
