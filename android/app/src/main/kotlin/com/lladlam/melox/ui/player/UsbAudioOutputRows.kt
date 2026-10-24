package com.lladlam.melox.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXSymbol
import com.lladlam.melox.ui.glass.MeloXSymbolIcon
import com.lladlam.melox.ui.glass.meloXLiquidButton
import com.lladlam.melox.usb.MeloXUsbAudioSession
import kotlinx.coroutines.launch

@Composable
internal fun UsbAudioOutputRows(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usb = MeloXUsbAudioSession
    if (usb.devices.isEmpty() && usb.error == null) return
    Text(stringResource(R.string.usb_exclusive_title), color = Color.White.copy(alpha = .60f),
        fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    usb.devices.forEach { target ->
        val active = usb.device?.deviceId == target.deviceId
        val color = if (active) Color.Black else Color.White
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp))
            .meloXLiquidButton(shape = RoundedCornerShape(22.dp), surfaceColor = if (active) Color(0xFFFFFDEB) else Color.Black.copy(alpha = .40f))
            .semantics { selected = active }
            .clickable(enabled = !usb.busy && !com.lladlam.melox.dlna.DlnaSession.busy && !com.lladlam.melox.miplay.MiPlaySession.busy && !com.lladlam.melox.lan.MeloXLanSession.busy, role = Role.Button) {
                scope.launch { if (usb.start(context, target)) onDismiss() }
            }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXSymbolIcon(MeloXSymbol.Devices, Modifier.size(24.dp), if (active) Color(0xFFFF3B30) else color)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(target.productName ?: "USB DAC", color = color, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (active) usb.description else stringResource(R.string.usb_exclusive_profile), color = color.copy(alpha = .55f), fontSize = 10.sp)
            }
        }
    }
    if (usb.active) Text(stringResource(R.string.usb_exclusive_stop), color = Color.Black,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .meloXLiquidButton(shape = RoundedCornerShape(18.dp), surfaceColor = Color.White.copy(alpha = .8f))
            .clickable(enabled = !usb.busy) { scope.launch { usb.stop() } }.padding(12.dp))
    usb.error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
}
