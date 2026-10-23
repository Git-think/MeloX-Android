package com.lladlam.melox.lan

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MeloXLanServer"

/**
 * The playback-related operations a LAN peer may invoke. Implemented by the
 * playback service so the network layer never touches ExoPlayer directly.
 */
internal interface MeloXLanHost {
    fun snapshot(): MeloXLanPlayback
    fun queueSnapshot(): List<MeloXLanTrack>
    fun currentTrack(): MeloXLanTrack?
    fun control(command: String, positionMs: Long?, volume: Int?): Boolean
    fun transfer(track: MeloXLanTrack, queue: List<MeloXLanTrack>, positionMs: Long): Boolean
    fun startCast(format: MeloXLanCastFormat): MeloXLanCastHandshake?
    fun stopCast()
    fun startTogether(
        queue: List<MeloXLanTrack>,
        index: Int,
        positionMs: Long,
        playing: Boolean,
        relay: MeloXLanRelay?,
    ): Boolean
    fun stopTogether()
    fun applyTogetherState(positionMs: Long, playing: Boolean, index: Int)
}

internal class MeloXLanServer(
    private val host: MeloXLanHost,
    val identity: MeloXLanPreferences,
    private val scope: CoroutineScope,
    private val onTogetherEvent: ((JSONObject) -> Unit)? = null,
    private val onTogetherChanged: ((Boolean) -> Unit)? = null,
) {
    @Volatile private var controlPort: Int = identity.port
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var eventJob: Job? = null
    private var discovery: MeloXLanDiscoveryResponder? = null
    private val clients = CopyOnWriteArraySet<Client>()
    @Volatile private var togetherClient: Client? = null
    @Volatile private var togetherQueue: List<MeloXLanTrack> = emptyList()
    @Volatile private var togetherIndex: Int = 0

    fun start(): Int {
        if (running) return controlPort
        running = true
        val socket = bindControlSocket()
        if (socket == null) {
            running = false
            Log.w(TAG, "Unable to bind a LAN control port")
            return -1
        }
        controlPort = socket.localPort
        identity.port = controlPort
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        discovery = MeloXLanDiscoveryResponder(
            identity = identity,
            controlPort = { controlPort },
            snapshot = { host.snapshot() },
            isTrusted = { identity.isTrusted(it) },
        ).also { it.start() }
        eventJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                if (running && clients.isNotEmpty()) broadcastState()
            }
        }
        Log.i(TAG, "LAN server listening on $controlPort")
        return controlPort
    }

    fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null
        eventJob?.cancel()
        eventJob = null
        discovery?.stop()
        discovery = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.toList().forEach { it.close() }
        clients.clear()
        if (togetherClient != null) {
            togetherClient = null
            onTogetherChanged?.invoke(false)
        }
    }

    fun port(): Int = controlPort

    fun hasTogether(): Boolean = togetherClient != null

    fun sendTogetherControl(command: String, positionMs: Long?): Boolean {
        val client = togetherClient ?: return false
        return client.sendRequest(
            JSONObject()
                .put("type", "request")
                .put("action", "control")
                .put("command", command)
                .put("positionMs", positionMs ?: 0L),
        ) != null
    }

    private fun bindControlSocket(): ServerSocket? {
        for (offset in 0..9) {
            val candidate = identity.port + offset
            if (candidate > 65535) break
            val socket = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(candidate))
                }
            }.getOrNull()
            if (socket != null) return socket
        }
        return null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val clientSocket = runCatching { socket.accept() }.getOrNull() ?: break
            if (clients.size >= 8) {
                runCatching { clientSocket.close() }
                continue
            }
            val client = Client(clientSocket)
            if (client.handshake()) {
                clients += client
                client.startReader()
                client.send(
                    JSONObject()
                        .put("type", "event")
                        .put("event", "state")
                        .put("state", host.snapshot().toJson()),
                )
            } else {
                client.close()
            }
        }
    }

    private fun broadcastState() {
        val state = host.snapshot().toJson()
        clients.forEach { client ->
            client.send(
                JSONObject()
                    .put("type", "event")
                    .put("event", "state")
                    .put("state", state),
            )
        }
        togetherClient?.let { client ->
            client.send(
                JSONObject()
                    .put("type", "event")
                    .put("event", "together.state")
                    .put("queue", JSONArray(togetherQueue.map { it.toJson() }))
                    .put("index", togetherIndex)
                    .put("positionMs", host.snapshot().positionMs)
                    .put("playing", host.snapshot().playing)
                    .put("serverTimeMs", System.currentTimeMillis()),
            )
        }
    }

    private inner class Client(private val socket: Socket) {
        private val closed = AtomicBoolean(false)
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        var deviceId: String = ""
            private set
        var deviceName: String = ""
            private set
        var platform: String = ""
            private set
        var trusted: Boolean = false
            private set

        fun handshake(): Boolean {
            socket.soTimeout = 10_000
            return try {
                var message = readJson() ?: return false
                if (message.optString("type") != "hello") return false
                deviceId = message.optString("deviceId")
                deviceName = message.optString("deviceName").ifBlank { "MeloX" }
                platform = message.optString("platform")
                if (deviceId.isBlank()) return false
                trusted = identity.isTrusted(deviceId)
                var authenticated = trusted
                var attempts = 0
                while (!authenticated && attempts < 5) {
                    if (message.optString("pin") == identity.pin) {
                        authenticated = true
                        break
                    }
                    send(
                        JSONObject()
                            .put("type", "auth_required")
                            .put("pinRequired", true)
                            .put("attemptsLeft", 4 - attempts),
                    )
                    attempts++
                    message = readJson() ?: return false
                    if (message.optString("type") != "hello") return false
                    deviceId = message.optString("deviceId")
                    deviceName = message.optString("deviceName").ifBlank { deviceName }
                    platform = message.optString("platform").ifBlank { platform }
                }
                if (!authenticated) {
                    send(JSONObject().put("type", "auth_failed").put("reason", "pin"))
                    close()
                    return false
                }
                identity.rememberPeer(
                    deviceId = deviceId,
                    name = deviceName,
                    platform = platform,
                    address = runCatching { socket.inetAddress.hostAddress.orEmpty() }.getOrDefault(""),
                )
                send(
                    JSONObject()
                        .put("type", "auth_ok")
                        .put("trusted", trusted)
                        .put("deviceId", identity.deviceId)
                        .put("deviceName", identity.deviceName)
                        .put("platform", "android")
                        .put("port", controlPort),
                )
                socket.soTimeout = 0
                true
            } catch (error: Exception) {
                Log.w(TAG, "LAN handshake failed", error)
                close()
                false
            }
        }

        fun startReader() {
            scope.launch(Dispatchers.IO) {
                try {
                    while (running && !closed.get()) {
                        val request = readJson() ?: break
                        if (request.optString("type") == "event") {
                            if (togetherClient === this@Client) onTogetherEvent?.invoke(request)
                        } else {
                            handle(request)
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // Read timeout is only used during handshake.
                } catch (error: Exception) {
                    Log.w(TAG, "LAN client disconnected", error)
                } finally {
                    close()
                }
            }
        }

        private fun handle(request: JSONObject) {
            val id = request.optString("id")
            val type = request.optString("type")
            if (type == "response") return
            if (type != "request") return
            fun respond(builder: JSONObject.() -> Unit) {
                val response = JSONObject().put("type", "response").put("id", id).apply(builder)
                send(response)
            }
            when (val action = request.optString("action")) {
                "state" -> respond {
                    put("ok", true)
                    put("state", host.snapshot().toJson())
                }
                "control" -> {
                    val ok = host.control(
                        command = request.optString("command"),
                        positionMs = request.optLong("positionMs", -1L).takeIf { it >= 0L },
                        volume = request.optInt("volume", -1).takeIf { it in 0..100 },
                    )
                    respond { put("ok", ok) }
                    if (ok) broadcastState()
                }
                "transfer" -> {
                    val track = MeloXLanTrack.fromJson(request.optJSONObject("track"))
                    if (track == null) {
                        respond { put("ok", false).put("error", "invalid_track") }
                    } else {
                        val ok = host.transfer(
                            track = track,
                            queue = MeloXLanTrack.fromJsonArray(request.optJSONArray("queue")),
                            positionMs = request.optLong("positionMs", 0L).coerceAtLeast(0L),
                        )
                        respond { put("ok", ok).apply { if (!ok) put("error", "unsupported_track") } }
                        if (ok) broadcastState()
                    }
                }
                "cast.start" -> {
                    val handshake = host.startCast(
                        MeloXLanCastFormat.fromJson(request.optJSONObject("format")),
                    )
                    if (handshake == null) {
                        respond { put("ok", false).put("error", "cast_unavailable") }
                    } else {
                        respond {
                            put("ok", true)
                            put("castPort", handshake.port)
                            put("format", handshake.format.toJson())
                        }
                        host.control("pause", null, null)
                        clients.forEach {
                            it.send(
                                JSONObject()
                                    .put("type", "event")
                                    .put("event", "cast.started")
                                    .put("port", handshake.port)
                                    .put("format", handshake.format.toJson()),
                            )
                        }
                    }
                }
                "cast.stop" -> {
                    host.stopCast()
                    respond { put("ok", true) }
                    clients.forEach {
                        it.send(JSONObject().put("type", "event").put("event", "cast.stopped"))
                    }
                }
                "together.start" -> {
                    val queue = MeloXLanTrack.fromJsonArray(request.optJSONArray("queue"))
                    val index = request.optInt("index", 0)
                    val position = request.optLong("positionMs", 0L).coerceAtLeast(0L)
                    val ok = host.startTogether(
                        queue = queue,
                        index = index,
                        positionMs = position,
                        playing = request.optBoolean("playing", true),
                        relay = MeloXLanRelay.fromJson(request.optJSONObject("relay")),
                    )
                    if (ok) {
                        togetherClient = this
                        togetherQueue = queue
                        togetherIndex = index
                        onTogetherChanged?.invoke(true)
                    }
                    respond {
                        put("ok", ok)
                        put("hostAddress", localIpv4Address().orEmpty())
                        put("hostPort", controlPort)
                        put("state", host.snapshot().toJson())
                    }
                    if (ok) broadcastState()
                }
                "together.stop" -> {
                    host.stopTogether()
                    togetherClient = null
                    onTogetherChanged?.invoke(false)
                    respond { put("ok", true) }
                    broadcastState()
                }
                "ping" -> respond {
                    put("ok", true)
                    put("t0", request.optLong("t0", 0L))
                    put("serverTimeMs", System.currentTimeMillis())
                }
                else -> respond { put("ok", false).put("error", "unknown_action:$action") }
            }
        }

        fun sendRequest(value: JSONObject): String? {
            val id = value.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }
            value.put("id", id).put("type", "request")
            return if (send(value)) id else null
        }

        fun send(value: JSONObject): Boolean = synchronized(writer) {
            if (closed.get()) return false
            runCatching {
                writer.write(value.toString())
                writer.write("\n")
                writer.flush()
                true
            }.getOrDefault(false)
        }

        private fun readJson(): JSONObject? {
            val line = reader.readLine() ?: return null
            if (line.length > MELOX_LAN_MAX_MESSAGE_BYTES) return null
            return runCatching { JSONObject(line) }.getOrNull()
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            clients.remove(this)
            if (togetherClient === this) {
                togetherClient = null
                onTogetherChanged?.invoke(false)
            }
            runCatching { socket.close() }
        }
    }
}
