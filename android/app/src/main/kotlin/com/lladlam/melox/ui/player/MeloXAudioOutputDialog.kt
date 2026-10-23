package com.lladlam.melox.ui.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXShapes
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon

private fun localOutputTypes(): Set<Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
    return setOf(
        MediaRoute2Info.TYPE_BUILTIN_SPEAKER,
        MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
        MediaRoute2Info.TYPE_BLE_HEADSET,
        MediaRoute2Info.TYPE_WIRED_HEADSET,
        MediaRoute2Info.TYPE_WIRED_HEADPHONES,
        MediaRoute2Info.TYPE_USB_HEADSET,
        MediaRoute2Info.TYPE_USB_DEVICE,
        MediaRoute2Info.TYPE_HEARING_AID,
        MediaRoute2Info.TYPE_DOCK,
        MediaRoute2Info.TYPE_HDMI,
        MediaRoute2Info.TYPE_HDMI_ARC,
        MediaRoute2Info.TYPE_HDMI_EARC,
        MediaRoute2Info.TYPE_LINE_ANALOG,
        MediaRoute2Info.TYPE_LINE_DIGITAL,
        MediaRoute2Info.TYPE_AUX_LINE,
    )
}

private data class MeloXAudioOutputOption(
    val id: String,
    val name: String,
    val description: String?,
    val type: Int,
    val selected: Boolean,
    val route: MediaRoute2Info?,
)

/**
 * In-app version of the system output switcher.
 *
 * Android 12+ exposes the system routing controller through [MediaRouter2], so
 * we can list the same local routes (phone speaker, Bluetooth, wired, USB…)
 * and select one without leaving the player. When the platform refuses the
 * selection (OEM policy / no routing service) we fall back to the system
 * output switcher, then to Bluetooth settings.
 */
@Composable
internal fun MeloXAudioOutputDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLanDevices by remember { mutableStateOf(false) }
    val router = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { MediaRouter2.getInstance(context) }.getOrNull()
        } else {
            null
        }
    }
    var bluetoothPermissionAsked by remember { mutableStateOf(false) }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }
    LaunchedEffect(visible) {
        if (!visible || bluetoothPermissionAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return@LaunchedEffect
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Route names for Bluetooth outputs are hidden without this
            // permission; ask once, only after the user opens the picker.
            bluetoothPermissionAsked = true
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    DisposableEffect(router, visible) {
        if (router == null || !visible) return@DisposableEffect onDispose {}
        val executor = ContextCompat.getMainExecutor(context)
        val routeCallback = object : MediaRouter2.RouteCallback() {
            override fun onRoutesUpdated(routes: MutableList<MediaRoute2Info>) { refreshTick++ }
        }
        val transferCallback = object : MediaRouter2.TransferCallback() {
            override fun onTransfer(
                newController: MediaRouter2.RoutingController,
                oldController: MediaRouter2.RoutingController,
            ) {
                refreshTick++
            }

            override fun onTransferFailure(route: MediaRoute2Info) {
                error = context.getString(R.string.player_output_switch_failed)
                refreshTick++
            }
        }
        runCatching {
            router.registerRouteCallback(
                executor,
                routeCallback,
                RouteDiscoveryPreference.Builder(
                    listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO),
                    false,
                ).build(),
            )
            router.registerTransferCallback(executor, transferCallback)
        }
        onDispose {
            runCatching { router.unregisterRouteCallback(routeCallback) }
            runCatching { router.unregisterTransferCallback(transferCallback) }
        }
    }

    val options = remember(router, refreshTick) { router?.audioOutputOptions(context).orEmpty() }
    val selectedName = options.firstOrNull { it.selected }?.name

    MeloXGlassDialog(visible = visible, onDismiss = onDismiss) {
        Text(
            stringResource(R.string.player_output_devices),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        selectedName?.let {
            Text(
                stringResource(R.string.player_output_current, it),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (options.isEmpty()) {
            Text(
                stringResource(R.string.player_output_none),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            options.forEach { option ->
                val selectedColor = if (option.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            error = null
                            if (option.route == null) {
                                // Synthetic "this phone" row: prefer the real
                                // built-in speaker route, otherwise let the
                                // platform pick the local output.
                                val activeRouter = router
                                val builtin = activeRouter?.routes?.firstOrNull {
                                    it.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER
                                }
                                if (activeRouter != null && builtin != null &&
                                    selectAudioOutput(activeRouter, builtin)
                                ) {
                                    onDismiss()
                                } else if (showSystemAudioOutputSwitcher(context)) {
                                    onDismiss()
                                } else {
                                    error = context.getString(R.string.player_output_unavailable)
                                }
                                return@clickable
                            }
                            val activeRouter = router
                            if (activeRouter != null && selectAudioOutput(activeRouter, option.route)) {
                                onDismiss()
                            } else {
                                error = context.getString(R.string.player_output_switch_failed)
                                if (showSystemAudioOutputSwitcher(context)) onDismiss()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.Devices,
                        modifier = Modifier.size(22.dp),
                        color = selectedColor.copy(alpha = if (option.selected) 1f else .72f),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            option.name,
                            color = selectedColor,
                            fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        option.description?.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (option.selected) {
                        MeloXSymbolIcon(
                            symbol = MeloXSymbol.Check,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        MeloXGlassButton(
            onClick = { showLanDevices = true },
            modifier = Modifier.fillMaxWidth(),
            style = MeloXGlassButtonStyle.Bordered,
            shape = MeloXShapes.capsule,
        ) {
            Text(stringResource(R.string.lan_nearby), fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeloXGlassButton(
                onClick = {
                    if (!showSystemAudioOutputSwitcher(context)) {
                        error = context.getString(R.string.player_output_unavailable)
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f),
                style = MeloXGlassButtonStyle.Bordered,
                shape = MeloXShapes.capsule,
            ) {
                Text(stringResource(R.string.player_output_open_system), fontSize = 13.sp)
            }
            MeloXGlassButton(
                onClick = onDismiss,
                modifier = Modifier.width(92.dp),
                style = MeloXGlassButtonStyle.BorderedProminent,
                shape = MeloXShapes.capsule,
            ) {
                Text(stringResource(R.string.action_done), fontSize = 13.sp)
            }
        }
    }
    MeloXLanDialog(visible = showLanDevices, onDismiss = { showLanDevices = false })
}

private fun MediaRouter2.audioOutputOptions(context: Context): List<MeloXAudioOutputOption> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
    val controller = runCatching { systemController }.getOrNull() ?: return emptyList()
    val selectedIds = runCatching {
        controller.selectedRoutes.mapTo(mutableSetOf()) { it.id }
    }.getOrDefault(mutableSetOf())
    val routes = buildList {
        addAll(runCatching { controller.selectableRoutes }.getOrDefault(emptyList()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            addAll(runCatching { controller.transferableRoutes }.getOrDefault(emptyList()))
        }
        addAll(runCatching { controller.selectedRoutes }.getOrDefault(emptyList()))
        addAll(runCatching { this@audioOutputOptions.routes.filter { it.isSystemRoute } }.getOrDefault(emptyList()))
    }.distinctBy { it.id }
        .filter { it.isSystemRoute || it.type in localOutputTypes() }
        .sortedWith(
            compareByDescending<MediaRoute2Info> { it.id in selectedIds }
                .thenBy { it.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER }
                .thenBy { it.name.toString() },
        )
    val options = routes.map { route ->
        MeloXAudioOutputOption(
            id = route.id,
            name = route.name.toString().ifBlank { route.description?.toString().orEmpty() }
                .ifBlank { defaultOutputName(context, route.type) },
            description = route.description?.toString()?.takeIf { it.isNotBlank() && it != route.name.toString() },
            type = route.type,
            selected = route.id in selectedIds,
            route = route,
        )
    }
    val hasSelected = options.any { it.selected }
    val builtinIndex = options.indexOfFirst { it.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER }
    // Android may report no selected route while the phone speaker is the
    // implicit default. Always keep an explicit "this phone" row so the user
    // can switch back after connecting Bluetooth.
    if (builtinIndex >= 0) {
        return options.mapIndexed { index, option ->
            if (!hasSelected && index == builtinIndex) option.copy(selected = true) else option
        }
    }
    return listOf(
        MeloXAudioOutputOption(
            id = "__this_phone__",
            name = context.getString(R.string.player_output_this_phone),
            description = null,
            type = MediaRoute2Info.TYPE_BUILTIN_SPEAKER,
            selected = !hasSelected,
            route = null,
        ),
    ) + options
}

private fun selectAudioOutput(router: MediaRouter2, route: MediaRoute2Info): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    return runCatching {
    val controller = router.systemController
    when {
        runCatching { controller.selectableRoutes.any { it.id == route.id } }.getOrDefault(false) -> {
            controller.selectRoute(route)
            true
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            runCatching { controller.transferableRoutes.any { it.id == route.id } }.getOrDefault(false) -> {
            router.transferTo(route)
            true
        }
        // `transferTo` itself has existed since API 30; trying it for a route
        // that is not listed as transferable is harmless (it throws and the
        // caller falls back to the system switcher).
        else -> {
            router.transferTo(route)
            true
        }
    }
    }.getOrDefault(false)
}

private fun defaultOutputName(context: Context, type: Int): String = when (type) {
    MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.player_output_this_phone)
    MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
    MediaRoute2Info.TYPE_BLE_HEADSET,
    MediaRoute2Info.TYPE_HEARING_AID -> context.getString(R.string.player_output_bluetooth)
    MediaRoute2Info.TYPE_WIRED_HEADSET,
    MediaRoute2Info.TYPE_WIRED_HEADPHONES -> context.getString(R.string.player_output_wired)
    MediaRoute2Info.TYPE_USB_HEADSET,
    MediaRoute2Info.TYPE_USB_DEVICE -> context.getString(R.string.player_output_usb)
    else -> context.getString(R.string.player_output_audio_device)
}

/**
 * Opens the platform output switcher; returns false when neither the system
 * switcher nor Bluetooth settings can be opened (the caller shows a message).
 */
internal fun showSystemAudioOutputSwitcher(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val router = runCatching { MediaRouter2.getInstance(context) }.getOrNull()
        if (router != null && runCatching { router.showSystemOutputSwitcher() }.getOrDefault(false)) {
            return true
        }
    }
    return runCatching {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
