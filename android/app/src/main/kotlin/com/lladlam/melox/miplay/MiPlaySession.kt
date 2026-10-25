package com.lladlam.melox.miplay

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lladlam.melox.dlna.DlnaSession
import com.lladlam.melox.lan.MeloXLanRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class MiPlayDevice(val id: String, val name: String, val room: String)

/** Xiaomi owns the network protocol and audio capture; our player remains the live audio source. */
internal object MiPlaySession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var available by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var devices by mutableStateOf<List<MiPlayDevice>>(emptyList())
        private set
    var device by mutableStateOf<MiPlayDevice?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var volume by mutableStateOf<Float?>(null)
        private set
    val active: Boolean get() = device != null
    private var transport: MiPlayTransport? = null
    private var scanning = false
    private var pending: MiPlayDevice? = null
    private var connection: CompletableDeferred<Boolean>? = null
    private var initTimeout: Job? = null
    private var volumeJob: Job? = null
    private var desiredVolume: Float? = null

    fun setPanelVisible(context: Context, visible: Boolean) {
        scanning = visible
        if (!visible) { if (ready) transport?.scan(false); return }
        available = runCatching { context.packageManager.getPackageInfo(MiPlayTransport.PACKAGE, 0) }.isSuccess
        if (!available) return
        if (transport != null && error != null && !active && !busy) {
            transport?.close(); transport = null; ready = false
        }
        if (transport == null) {
            error = null
            devices = emptyList()
            transport = MiPlayTransport(context, listener)
            transport?.bind()
            initTimeout?.cancel()
            initTimeout = scope.launch {
                delay(15_000)
                if (!ready) error = "等待小米妙播服务初始化超时"
            }
        } else if (ready) transport?.scan(true)
    }

    private val listener = object : MiPlayTransport.Listener {
        override fun onReady() {
            ready = true; error = null; initTimeout?.cancel()
            Log.i("MeloXMiPlay", "Private audio client initialized")
            if (scanning) transport?.scan(true)
        }
        override fun onDevice(id: String, name: String, room: String?) {
            val found = MiPlayDevice(id, name, room.orEmpty())
            devices = devices.filterNot { it.id == id } + found
            if (device?.id == id) device = found
        }
        override fun onLost(id: String?) {
            devices = devices.filterNot { it.id == id }
            // Discovery loss alone does not tear down an established audio route.
        }
        override fun onConnection(id: String?, state: Int) {
            Log.i("MeloXMiPlay", "Audio route connection state=$state")
            if (pending?.id == id) {
                if (state == 1) {
                    device = pending; volume = null
                    transport?.queryVolume(checkNotNull(id))
                    connection?.complete(true)
                } else if (state == 0 || state < 0) {
                    error = "小米妙播设备连接失败（$state）"; connection?.complete(false)
                }
            } else if (device?.id == id && state != 1) {
                device = null; volume = null
                error = "小米妙播设备已断开（$state）"
            }
        }
        override fun onVolume(id: String?, value: Double) {
            if (device?.id == id) volume = (value / 100).toFloat().coerceIn(0f, 1f)
        }
        override fun onError(message: String) {
            error = message
            connection?.complete(false)
            Log.w("MeloXMiPlay", message)
            if (message.contains("服务已断开")) {
                ready = false; device = null; volume = null
            }
        }
    }

    suspend fun cast(target: MiPlayDevice): Boolean = withContext(Dispatchers.Main) {
        if (!ready || busy) return@withContext false
        if (device?.id == target.id) return@withContext true
        busy = true; error = null
        val result = CompletableDeferred<Boolean>()
        try {
            com.lladlam.melox.usb.MeloXUsbAudioSession.stop()
            DlnaSession.disconnect(resumeLocal = true)
            MeloXLanRuntime.stopCastSender()
            MeloXLanRuntime.stopHostTogether()
            if (MeloXLanRuntime.isGuestTogether()) MeloXLanRuntime.stopGuestTogether()
            disconnect()
            pending = target; connection = result
            transport?.play(target.id)
            val connected = withTimeoutOrNull(15_000) { result.await() } == true
            if (!connected) {
                transport?.stop(target.id)
                if (device?.id == target.id) device = null
                if (error == null) error = "小米妙播设备未确认连接"
            }
            connected
        } finally {
            if (!result.isCompleted) transport?.stop(target.id)
            pending = null; connection = null; busy = false
        }
    }

    fun disconnect() {
        pending?.let { transport?.stop(it.id) }
        device?.takeUnless { it.id == pending?.id }?.let { transport?.stop(it.id) }
        pending = null; device = null; volume = null
        connection?.complete(false)
        volumeJob?.cancel(); desiredVolume = null
    }

    fun changeVolume(value: Float) {
        if (!active) return
        volume = value.coerceIn(0f, 1f); desiredVolume = volume
        if (volumeJob?.isActive == true) return
        volumeJob = scope.launch {
            while (desiredVolume != null) {
                val target = device ?: break
                val next = desiredVolume ?: break
                desiredVolume = null
                transport?.volume(target.id, next.toDouble() * 100)
                delay(100)
            }
        }
    }
}
