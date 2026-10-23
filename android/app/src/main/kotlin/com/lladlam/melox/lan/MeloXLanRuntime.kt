package com.lladlam.melox.lan

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Process-wide LAN hub. The playback service attaches the host implementation
 * and owns the server; UI code only needs this object.
 */
internal object MeloXLanRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var host: MeloXLanHost? = null
    @Volatile private var prefs: MeloXLanPreferences? = null
    @Volatile private var server: MeloXLanServer? = null
    @Volatile private var castSender: MeloXLanCastSender? = null
    @Volatile private var castClient: MeloXLanClient? = null
    @Volatile private var hostTogetherClient: MeloXLanClient? = null
    @Volatile private var hostTogetherJob: kotlinx.coroutines.Job? = null
    @Volatile private var audioProxy: MeloXLanAudioProxy? = null
    @Volatile private var activeRelayToken: String? = null

    private val serverListeners = CopyOnWriteArraySet<(Boolean) -> Unit>()
    internal var guestTogether by mutableStateOf(false)
        private set

    fun attach(context: Context, host: MeloXLanHost) {
        if (this.host === host && prefs != null) return
        detach()
        val app = context.applicationContext
        val identity = MeloXLanPreferences(app)
        this.host = host
        prefs = identity
        if (!identity.enabled || !MeloXLanPermissions.has(app)) return
        startServer(host, identity)
    }

    private fun startServer(host: MeloXLanHost, identity: MeloXLanPreferences) {
        if (server != null) return
        server = MeloXLanServer(
            host = host,
            identity = identity,
            scope = scope,
            onTogetherEvent = { event -> handleTogetherEvent(event) },
            onTogetherChanged = { guestTogether = it },
        ).also { it.start() }
    }

    fun ensureStarted(context: Context) {
        val h = host ?: return
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        if (!identity.enabled || !MeloXLanPermissions.has(context)) return
        startServer(h, identity)
    }

    fun detach(host: MeloXLanHost? = null) {
        if (host != null && this.host !== host) return
        stopCastSender()
        stopHostTogether()
        audioProxy?.stop()
        audioProxy = null
        guestTogether = false
        server?.stop()
        server = null
        prefs = null
        this.host = null
    }

    fun isAvailable(): Boolean = prefs != null && host != null

    fun isEnabled(context: Context): Boolean =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        identity.enabled = enabled
        if (enabled) {
            host?.let { h ->
                if (MeloXLanPermissions.has(context)) startServer(h, identity)
            }
        } else {
            server?.stop()
            server = null
            stopCastSender()
            hostTogetherJob?.cancel()
            hostTogetherJob = null
            hostTogetherClient?.close()
            hostTogetherClient = null
        }
    }

    fun deviceId(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).deviceId

    fun deviceName(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).deviceName

    fun port(context: Context): Int =
        server?.port() ?: (prefs ?: MeloXLanPreferences(context.applicationContext)).port

    fun setPort(context: Context, port: Int): Int {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        val wasRunning = server != null
        if (wasRunning) {
            server?.stop()
            server = null
        }
        identity.port = port
        if (wasRunning) {
            host?.let { h -> if (MeloXLanPermissions.has(context)) startServer(h, identity) }
        }
        return this.port(context)
    }

    fun pin(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).pin

    fun regeneratePin(context: Context): String =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).regeneratePin()

    fun trustedDevices(context: Context): List<JSONObject> =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).trustedDevices()

    fun recentPeers(context: Context): List<JSONObject> =
        (prefs ?: MeloXLanPreferences(context.applicationContext)).recentPeers()

    fun trustPeer(context: Context, deviceId: String, name: String, platform: String) {
        (prefs ?: MeloXLanPreferences(context.applicationContext))
            .addTrusted(deviceId, name, platform)
    }

    fun removeTrusted(context: Context, deviceId: String) {
        (prefs ?: MeloXLanPreferences(context.applicationContext)).removeTrusted(deviceId)
    }

    suspend fun scan(context: Context, timeoutMs: Long = 1_500L): List<MeloXLanDevice> {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        return MeloXLanDiscovery.scan(identity, timeoutMs)
    }

    fun newClient(
        context: Context,
        device: MeloXLanDevice,
        onEvent: (JSONObject) -> Unit = {},
        onRequest: ((JSONObject) -> JSONObject?)? = null,
    ): MeloXLanClient {
        val identity = prefs ?: MeloXLanPreferences(context.applicationContext).also { prefs = it }
        return MeloXLanClient(device, identity, scope, onEvent, onRequest)
    }

    fun currentCastFormat(): MeloXLanCastFormat = MeloXCastAudioTap.format

    fun queueSnapshot(): List<MeloXLanTrack> = host?.queueSnapshot().orEmpty()

    fun currentTrack(): MeloXLanTrack? = host?.currentTrack()

    fun snapshot(): MeloXLanPlayback? = host?.snapshot()

    fun hostControl(command: String, positionMs: Long? = null): Boolean =
        host?.control(command, positionMs, null) == true

    fun startCastSender(
        context: Context,
        device: MeloXLanDevice,
        handshake: MeloXLanCastHandshake,
        client: MeloXLanClient?,
    ): Boolean {
        stopCastSender()
        val sender = MeloXLanCastSender(device, handshake, scope) { reason ->
            val client = castClient
            castSender = null
            castClient = null
            if (client != null) scope.launch { runCatching { client.stopCast() } }
            if (reason != null) Log.i("MeloXLanRuntime", "Cast stopped: $reason")
        }
        if (!sender.start()) return false
        castSender = sender
        castClient = client
        return true
    }

    fun stopCastSender() {
        castSender?.stop()
        castSender = null
        val client = castClient
        castClient = null
        if (client != null) {
            scope.launch { runCatching { client.stopCast() } }
        }
    }

    fun beginHostTogether(
        client: MeloXLanClient,
        previewState: () -> JSONObject?,
    ) {
        hostTogetherJob?.cancel()
        hostTogetherClient?.close()
        hostTogetherClient = client
        hostTogetherJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000L)
                val state = previewState() ?: continue
                val sent = client.sendEvent(
                    JSONObject()
                        .put("type", "event")
                        .put("event", "together.state")
                        .put("state", state),
                )
                if (!sent) {
                    revokeActiveRelay()
                    break
                }
            }
        }
    }

    fun stopHostTogether() {
        hostTogetherJob?.cancel()
        hostTogetherJob = null
        hostTogetherClient?.close()
        hostTogetherClient = null
        revokeActiveRelay()
    }

    fun issueRelay(
        context: Context,
        guestAddress: String,
        queue: List<MeloXLanTrack>,
    ): MeloXLanRelay? {
        revokeActiveRelay()
        val proxy = audioProxy ?: MeloXLanAudioProxy(context.applicationContext, scope).also {
            audioProxy = it
        }
        val relay = proxy.issue(guestAddress, queue) ?: return null
        activeRelayToken = relay.token
        return relay
    }

    fun revokeActiveRelay() {
        audioProxy?.revoke(activeRelayToken)
        activeRelayToken = null
    }

    fun sendGuestTogetherControl(context: Context, command: String, positionMs: Long? = null): Boolean =
        server?.sendTogetherControl(command, positionMs) == true

    fun isHostingTogether(): Boolean = hostTogetherClient != null

    fun isGuestTogether(): Boolean = guestTogether

    fun addServerListener(listener: (Boolean) -> Unit) {
        serverListeners += listener
    }

    fun removeServerListener(listener: (Boolean) -> Unit) {
        serverListeners -= listener
    }

    private fun handleTogetherEvent(event: JSONObject) {
        if (event.optString("event") != "together.state") return
        val state = event.optJSONObject("state") ?: return
        host?.applyTogetherState(
            positionMs = state.optLong("positionMs", 0L),
            playing = state.optBoolean("playing"),
            index = state.optInt("index", -1),
        )
    }
}
