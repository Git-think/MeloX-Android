package com.lladlam.melox.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.lan.MeloXLanClient
import com.lladlam.melox.lan.MeloXLanConnectResult
import com.lladlam.melox.lan.MeloXLanDevice
import com.lladlam.melox.lan.MeloXLanPermissions
import com.lladlam.melox.lan.MeloXLanPlayback
import com.lladlam.melox.lan.MeloXLanRuntime
import com.lladlam.melox.lan.MeloXLanTrack
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun MeloXLanDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<MeloXLanDevice>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<MeloXLanDevice?>(null) }
    var client by remember { mutableStateOf<MeloXLanClient?>(null) }
    var remoteState by remember { mutableStateOf<MeloXLanPlayback?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var pinRequired by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var castActive by remember { mutableStateOf(false) }
    var remoteTrusted by remember { mutableStateOf(false) }
    var togetherRelay by remember { mutableStateOf(true) }
    val lanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) MeloXLanRuntime.ensureStarted(context) }

    fun scan() {
        if (scanning) return
        scanning = true
        error = null
        scope.launch {
            runCatching { MeloXLanRuntime.scan(context) }
                .onSuccess { devices = it }
                .onFailure { error = it.message ?: context.getString(R.string.lan_scan_failed) }
            scanning = false
        }
    }

    fun resetDevice() {
        if (castActive) MeloXLanRuntime.stopCastSender()
        if (MeloXLanRuntime.isHostingTogether()) MeloXLanRuntime.stopHostTogether()
        client?.close()
        client = null
        selected = null
        remoteState = null
        pinRequired = false
        pin = ""
        remoteTrusted = false
        castActive = false
    }

    fun connect(device: MeloXLanDevice, pinValue: String?) {
        connecting = true
        error = null
        scope.launch {
            val created = MeloXLanRuntime.newClient(
                context = context,
                device = device,
                onEvent = { event ->
                    when (event.optString("event")) {
                        "state" -> remoteState = MeloXLanPlayback.fromJson(event.optJSONObject("state"))
                        "together.state" -> remoteState = MeloXLanPlayback.fromJson(
                            event.optJSONObject("state") ?: event,
                        )
                        "cast.stopped" -> castActive = false
                    }
                },
                onRequest = { request ->
                    if (request.optString("action") == "control") {
                        val ok = MeloXLanRuntime.hostControl(
                            command = request.optString("command"),
                            positionMs = request.optLong("positionMs", -1L).takeIf { it >= 0L },
                        )
                        JSONObject().put("ok", ok)
                    } else {
                        null
                    }
                },
            )
            when (val result = created.connect(pinValue)) {
                MeloXLanConnectResult.Connected -> {
                    client?.close()
                    client = created
                    selected = device
                    remoteTrusted = created.trusted || device.trusted
                    pinRequired = false
                    pin = ""
                    created.request("state").let { response ->
                        remoteState = MeloXLanPlayback.fromJson(response?.optJSONObject("state"))
                    }
                }
                MeloXLanConnectResult.PinRequired -> {
                    created.close()
                    selected = device
                    pinRequired = true
                }
                MeloXLanConnectResult.Failed -> {
                    created.close()
                    error = context.getString(R.string.lan_connect_failed)
                }
            }
            connecting = false
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        if (!MeloXLanPermissions.has(context)) {
            lanPermissionLauncher.launch(MeloXLanPermissions.permission())
        } else {
            MeloXLanRuntime.ensureStarted(context)
            scan()
        }
    }

    MeloXGlassDialog(visible = visible, onDismiss = onDismiss) {
        Text(stringResource(R.string.lan_nearby), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(
                R.string.lan_local_summary,
                MeloXLanRuntime.deviceName(context),
                MeloXLanRuntime.port(context),
                MeloXLanRuntime.pin(context),
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (pinRequired) {
                Text(stringResource(R.string.lan_pin_title), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.lan_pin_hint, selected?.name.orEmpty()),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                MeloXGlassTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter(Char::isDigit).take(4) },
                    placeholder = { Text(stringResource(R.string.lan_pin_hint_short)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MeloXGlassButton(
                        onClick = { pinRequired = false; pin = "" },
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Bordered,
                        shape = MeloXShapes.capsule,
                    ) { Text(stringResource(R.string.action_cancel)) }
                    MeloXGlassButton(
                        onClick = { selected?.let { connect(it, pin) } },
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                        shape = MeloXShapes.capsule,
                    ) { Text(stringResource(R.string.lan_connect)) }
                }
            } else if (selected != null) {
                val device = selected!!
                Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(
                    "${device.platform} · ${device.address}:${device.port}" +
                        if (remoteTrusted) " · ${stringResource(R.string.lan_trusted)}" else "",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                    fontSize = 11.sp,
                )
                if (client == null && !connecting) {
                    // The connection was closed (e.g. cast ended); allow retry.
                    MeloXGlassButton(
                        onClick = { connect(device, null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        style = MeloXGlassButtonStyle.BorderedProminent,
                        shape = MeloXShapes.capsule,
                    ) { Text(stringResource(R.string.lan_connect)) }
                }
                if (connecting) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.lan_connecting), modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp)
                    }
                }
                remoteState?.let { state ->
                    Text(
                        "${state.title.ifBlank { stringResource(R.string.lan_idle) }} · ${state.artist}",
                        modifier = Modifier.padding(top = 10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RemoteControlButton(MeloXSymbol.Previous) {
                            scope.launch { client?.control("previous") }
                        }
                        RemoteControlButton(
                            if (state.playing) MeloXSymbol.Pause else MeloXSymbol.Play,
                        ) {
                            scope.launch { client?.control("toggle") }
                        }
                        RemoteControlButton(MeloXSymbol.Next) {
                            scope.launch { client?.control("next") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeloXGlassButton(
                        onClick = {
                            scope.launch {
                                val current = MeloXLanRuntime.currentTrack()
                                if (current == null) {
                                    error = context.getString(R.string.lan_no_track)
                                } else {
                                    val ok = client?.transfer(
                                        current,
                                        MeloXLanRuntime.queueSnapshot(),
                                        MeloXLanRuntime.snapshot()?.positionMs ?: 0L,
                                    ) == true
                                    error = context.getString(
                                        if (ok) R.string.lan_transfer_sent else R.string.lan_transfer_failed,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Bordered,
                        shape = MeloXShapes.capsule,
                    ) { Text(stringResource(R.string.lan_transfer), fontSize = 12.sp) }
                    MeloXGlassButton(
                        onClick = {
                            val c = client ?: return@MeloXGlassButton
                            if (castActive) {
                                MeloXLanRuntime.stopCastSender()
                                castActive = false
                            } else {
                                scope.launch {
                                    val handshake = c.startCast(MeloXLanRuntime.currentCastFormat())
                                    if (handshake == null) {
                                        error = context.getString(R.string.lan_cast_failed)
                                    } else {
                                        castActive = MeloXLanRuntime.startCastSender(
                                            context,
                                            device,
                                            handshake,
                                            c,
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        style = MeloXGlassButtonStyle.Bordered,
                        shape = MeloXShapes.capsule,
                    ) { Text(stringResource(if (castActive) R.string.lan_cast_stop else R.string.lan_cast), fontSize = 12.sp) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { togetherRelay = !togetherRelay }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXSymbolIcon(
                        symbol = if (togetherRelay) MeloXSymbol.CheckCircle else MeloXSymbol.Circle,
                        modifier = Modifier.size(18.dp),
                        color = if (togetherRelay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                    )
                    Text(
                        stringResource(R.string.lan_relay_audio),
                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f),
                    )
                }
                MeloXGlassButton(
                    onClick = {
                        scope.launch {
                            val queue = MeloXLanRuntime.queueSnapshot()
                            val state = MeloXLanRuntime.snapshot() ?: return@launch
                            val relay = if (togetherRelay) {
                                MeloXLanRuntime.issueRelay(context, device.address, queue)
                            } else {
                                null
                            }
                            if (togetherRelay && relay == null) {
                                error = context.getString(R.string.lan_relay_failed)
                                return@launch
                            }
                            val response = client?.startTogether(
                                queue = queue,
                                index = state.index,
                                positionMs = state.positionMs,
                                playing = state.playing,
                                relay = relay,
                            )
                            if (response?.optBoolean("ok") == true) {
                                client?.let { c ->
                                    MeloXLanRuntime.beginHostTogether(c) {
                                        MeloXLanRuntime.snapshot()?.toJson()
                                    }
                                }
                                error = context.getString(R.string.lan_together_started)
                            } else {
                                MeloXLanRuntime.revokeActiveRelay()
                                error = context.getString(R.string.lan_together_failed)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                    shape = MeloXShapes.capsule,
                ) { Text(stringResource(R.string.lan_together), fontSize = 13.sp) }
                MeloXGlassButton(
                    onClick = { resetDevice() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = MeloXGlassButtonStyle.Bordered,
                    shape = MeloXShapes.capsule,
                ) { Text(stringResource(R.string.lan_close_device), fontSize = 12.sp) }
            } else {
                if (devices.isEmpty() && !scanning) {
                    Text(
                        stringResource(R.string.lan_no_devices),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                        modifier = Modifier.padding(vertical = 12.dp),
                        fontSize = 13.sp,
                    )
                }
                devices.forEach { device ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { connect(device, null) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Devices,
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${device.platform} · ${device.playback.title.ifBlank { stringResource(R.string.lan_idle) }}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (device.trusted) {
                            MeloXSymbolIcon(
                                symbol = MeloXSymbol.Check,
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (scanning) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (MeloXLanRuntime.isGuestTogether()) {
            Text(stringResource(R.string.lan_together_guest), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemoteControlButton(MeloXSymbol.Previous) {
                    MeloXLanRuntime.sendGuestTogetherControl(context, "previous")
                }
                RemoteControlButton(MeloXSymbol.Pause) {
                    MeloXLanRuntime.sendGuestTogetherControl(context, "toggle")
                }
                RemoteControlButton(MeloXSymbol.Next) {
                    MeloXLanRuntime.sendGuestTogetherControl(context, "next")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(
                onClick = { scan() },
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.Bordered,
                shape = MeloXShapes.capsule,
            ) { Text(stringResource(if (scanning) R.string.lan_scanning else R.string.lan_scan), fontSize = 13.sp) }
            MeloXGlassButton(
                onClick = { resetDevice(); onDismiss() },
                modifier = Modifier.width(92.dp),
                style = MeloXGlassButtonStyle.BorderedProminent,
                shape = MeloXShapes.capsule,
            ) { Text(stringResource(R.string.action_done), fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RemoteControlButton(symbol: MeloXSymbol, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(symbol, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
    }
}
