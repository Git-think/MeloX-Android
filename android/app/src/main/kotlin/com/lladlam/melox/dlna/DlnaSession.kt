package com.lladlam.melox.dlna

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lladlam.melox.lan.MeloXLanRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URI

internal object DlnaSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Mutex()
    var device by mutableStateOf<DlnaDevice?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var volume by mutableStateOf<Float?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val active: Boolean get() = device != null
    private var source: DlnaMediaSource? = null
    private var requestedMediaId: String? = null
    private var proxy: DlnaHttpServer? = null
    private var polling: Job? = null
    private var switching: Job? = null
    private var app: Context? = null
    private var sampledAt = 0L
    private var desiredVolume: Float? = null
    private var settingVolume: Job? = null

    fun displayedPosition(): Long = (positionMs + if (playing) (SystemClock.elapsedRealtime() - sampledAt).coerceIn(0, 3_000) else 0)
        .coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)

    suspend fun cast(context: Context, target: DlnaDevice): Boolean = withContext(Dispatchers.Main) {
        commands.withLock {
            busy = true
            error = null
            try {
                val media = MeloXLanRuntime.playbackHost()?.dlnaSource() ?: error("当前歌曲没有可投放的音频源")
                requestedMediaId = media.mediaId
                app = context.applicationContext
                // Outputs are mutually exclusive; authenticated MeloX control sockets stay open.
                com.lladlam.melox.usb.MeloXUsbAudioSession.stop()
                com.lladlam.melox.miplay.MiPlaySession.disconnect()
                MeloXLanRuntime.stopCastSender()
                MeloXLanRuntime.stopHostTogether()
                if (MeloXLanRuntime.isGuestTogether()) MeloXLanRuntime.stopGuestTogether()
                if (device?.id != target.id) closeCurrent(stop = true)
                install(target, media)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = failure.message ?: "DLNA 投放失败"
                Log.w("MeloXDlna", "Renderer cast failed: ${target.name}", failure)
                false
            } finally { busy = false }
        }
    }

    private suspend fun install(target: DlnaDevice, media: DlnaMediaSource) {
        var nextProxy: DlnaHttpServer? = null
        val direct = media.uri.startsWith("http") && media.headers.isEmpty()
        try {
            suspend fun start(useProxy: Boolean) {
                val url = if (useProxy) {
                    nextProxy?.close()
                    withContext(Dispatchers.IO) {
                        nextProxy = DlnaHttpServer(checkNotNull(app), scope, media, InetAddress.getByName(URI(target.transport.controlUrl).host))
                        checkNotNull(nextProxy).start()
                    }
                } else media.uri
                withContext(Dispatchers.IO) {
                    runCatching { DlnaController.call(target.transport, "Stop") }
                    DlnaController.call(target.transport, "SetAVTransportURI", mapOf(
                        "CurrentURI" to url, "CurrentURIMetaData" to DlnaController.metadata(media, url),
                    ))
                }
                delay(350)
                withContext(Dispatchers.IO) { DlnaController.call(target.transport, "Play", mapOf("Speed" to "1")) }
                var confirmed = false
                repeat(6) {
                    if (!confirmed) {
                        delay(300)
                        val state = withContext(Dispatchers.IO) { DlnaController.call(target.transport, "GetTransportInfo") }
                        confirmed = state["CurrentTransportState"] == "PLAYING"
                    }
                }
                check(confirmed) { "设备未开始播放，可能不支持此音频格式或音频地址" }
            }
            try { start(useProxy = !direct) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (direct) start(useProxy = true) else throw failure }
            proxy?.close(); proxy = nextProxy; nextProxy = null
            source = media; device = target
            durationMs = media.durationMs; positionMs = 0; sampledAt = SystemClock.elapsedRealtime(); playing = true
            MeloXLanRuntime.hostControl("pause")
            if (media.positionMs > 1_000) runCatching {
                withContext(Dispatchers.IO) { DlnaController.call(target.transport, "Seek", mapOf("Unit" to "REL_TIME", "Target" to DlnaController.time(media.positionMs))) }
                positionMs = media.positionMs
            }
            polling?.cancel()
            polling = scope.launch { poll(target) }
            Log.i("MeloXDlna", "Playback confirmed: ${target.name}, proxy=${proxy != null}")
        } finally { nextProxy?.close() }
    }

    private suspend fun poll(target: DlnaDevice) {
        var failures = 0
        while (device?.id == target.id) {
            delay(2_000)
            try {
                commands.withLock {
                    proxy?.failure?.let { error(it) }
                    val wasPlaying = playing
                    val values = withContext(Dispatchers.IO) {
                        DlnaController.call(target.transport, "GetTransportInfo") to DlnaController.call(target.transport, "GetPositionInfo")
                    }
                    playing = values.first["CurrentTransportState"] == "PLAYING"
                    positionMs = DlnaController.millis(values.second["RelTime"])
                    DlnaController.millis(values.second["TrackDuration"]).takeIf { it > 0 }?.let { durationMs = it }
                    sampledAt = SystemClock.elapsedRealtime()
                    target.rendering?.let { service ->
                        runCatching {
                            val response = withContext(Dispatchers.IO) { DlnaController.call(service, "GetVolume", mapOf("Channel" to "Master")) }
                            volume = response["CurrentVolume"]?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
                        }
                    }
                    failures = 0
                    if (wasPlaying && !playing && values.first["CurrentTransportState"] == "STOPPED" &&
                        durationMs > 0 && positionMs >= durationMs - 3_000) next()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (++failures >= 3) {
                    error = "设备连接已断开：${failure.message.orEmpty()}"
                    scope.launch { disconnect(resumeLocal = true) }
                    return
                }
            }
        }
    }

    fun localTrackChanged(id: String?) {
        if (!active || busy || id == null || id == source?.mediaId || id == requestedMediaId) return
        requestedMediaId = id
        MeloXLanRuntime.hostControl("pause")
        switching?.cancel()
        switching = scope.launch { app?.let { context -> device?.let { cast(context, it) } } }
    }
    fun toggle() = command { target ->
        DlnaController.call(target.transport, if (playing) "Pause" else "Play", if (playing) emptyMap() else mapOf("Speed" to "1"))
        positionMs = displayedPosition(); sampledAt = SystemClock.elapsedRealtime()
        playing = !playing
    }
    fun seek(position: Long) = command { target ->
        DlnaController.call(target.transport, "Seek", mapOf("Unit" to "REL_TIME", "Target" to DlnaController.time(position)))
        positionMs = position; sampledAt = SystemClock.elapsedRealtime()
    }
    fun next() { if (active) MeloXLanRuntime.hostControl("next") }
    fun previous() {
        if (!active) return
        if (displayedPosition() >= 5_000) seek(0) else MeloXLanRuntime.hostControl("dlna.previous")
    }
    fun changeVolume(value: Float) {
        val target = device ?: return
        if (target.rendering == null) return
        volume = value.coerceIn(0f, 1f); desiredVolume = volume
        if (settingVolume?.isActive == true) return
        settingVolume = scope.launch {
            while (desiredVolume != null) {
                val next = desiredVolume ?: break
                desiredVolume = null
                commands.withLock {
                    runCatching { withContext(Dispatchers.IO) {
                        DlnaController.call(target.rendering, "SetVolume", mapOf("Channel" to "Master", "DesiredVolume" to (next * 100).toInt().toString()))
                    } }.onFailure { error = it.message }
                }
                delay(100)
            }
        }
    }
    private fun command(block: suspend (DlnaDevice) -> Unit) {
        scope.launch {
            commands.withLock {
                val target = device ?: return@withLock
                try { withContext(Dispatchers.IO) { block(target) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message }
            }
        }
    }
    suspend fun disconnect(resumeLocal: Boolean = true) = withContext(Dispatchers.Main) {
        commands.withLock {
            val resume = playing; val position = displayedPosition()
            closeCurrent(stop = true)
            if (resumeLocal) {
                MeloXLanRuntime.hostControl("seek", position)
                if (resume) MeloXLanRuntime.hostControl("play")
            }
        }
    }
    private suspend fun closeCurrent(stop: Boolean) {
        polling?.cancel(); polling = null
        val old = device
        device = null; source = null; playing = false; volume = null
        if (stop && old != null) runCatching { withContext(Dispatchers.IO) { DlnaController.call(old.transport, "Stop") } }
        proxy?.close(); proxy = null
    }
}
