package com.lladlam.melox.ui.player

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lladlam.melox.R

/** The cold-start entry point must not reference any MediaRouter2 platform types. */
@Composable
internal fun MeloXAudioOutputDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    state: MeloXPlaybackUiState,
) {
    // The root screen composes this even while closed. Do not create routing
    // callbacks, discover nearby devices or resolve new platform classes then.
    if (!visible) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        MeloXModernAudioOutputDialog(visible = true, onDismiss = onDismiss, state = state)
    } else {
        MeloXLegacyAudioOutputDialog(onDismiss, state)
    }
}

@Composable
private fun MeloXLegacyAudioOutputDialog(onDismiss: () -> Unit, state: MeloXPlaybackUiState) {
    val context = LocalContext.current
    val deviceName = remember(context) {
        runCatching {
            context.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.firstOrNull {
                    it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
                        it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                ?.productName?.toString()?.takeIf(String::isNotBlank)
        }.getOrNull()
    }
    MeloXLanOutputPanel(
        visible = true,
        state = state,
        outputName = deviceName ?: stringResource(R.string.player_output_local),
        onLocalOptions = {
            // Android 10/EMUI has no public per-app MediaRouter2 output picker.
            // Let its Bluetooth settings select the local route; LAN/USB remain
            // available in the same in-app output panel.
            runCatching {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }.onSuccess { onDismiss() }
        },
        onDismiss = onDismiss,
    )
}
