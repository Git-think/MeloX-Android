package com.lladlam.melox.lan

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Owns rounds independently of the UI; a generation fences every asynchronous operation. */
internal object MeloXLanTogetherCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class Peer(
        val client: MeloXLanClient, val provideAudio: Boolean,
        var offsetMs: Long = 0L, var state: MeloXLanPlayback = MeloXLanPlayback(),
        var ready: Boolean = false, var late: Boolean = false, var relay: MeloXLanRelay? = null,
        var calibratedAt: Long = 0L,
    )
    private val peers = linkedMapOf<String, Peer>()
    private var app: Context? = null
    private var sessionId = ""
    private var generation = 0L
    private var queueKey = ""
    private var round: Job? = null
    private var monitor: Job? = null
    private var ending: Job? = null
    private var preparing = false
    private var localPending = false
    private var roundPlayIntent = false
    private var explicitSeek = false
    private var completedGeneration = -1L
    val active: Boolean get() = peers.isNotEmpty()
    fun contains(client: MeloXLanClient): Boolean = peers[client.device.deviceId]?.client === client

    suspend fun join(context: Context, client: MeloXLanClient, provideAudio: Boolean): Boolean = withContext(Dispatchers.Main) {
        if (MeloXLanRuntime.queueSnapshot().isEmpty()) return@withContext false
        app = context.applicationContext
        if (peers.isEmpty()) sessionId = UUID.randomUUID().toString()
        peers[client.device.deviceId] = Peer(client, provideAudio)
        queueKey = ""
        if (monitor == null) monitor = scope.launch {
            while (isActive && peers.isNotEmpty()) {
                update()
                delay(200)
            }
        }
        update()
        true
    }

    fun changed() { if (active) scope.launch { update() } }
    fun seek() { explicitSeek = true }
    fun event(client: MeloXLanClient, event: JSONObject) {
        scope.launch {
            val peer = peers[client.device.deviceId] ?: return@launch
            if (peer.client !== client) return@launch
            if (event.optString("event") == "together.stopped") {
                remove(client)
                return@launch
            }
            if (event.optString("event") == "state") peer.state = MeloXLanPlayback.fromJson(event.optJSONObject("state"))
            if (event.optString("event") == "together.status" &&
                event.optString("sessionId") == sessionId && event.optLong("generation") == generation) {
                peer.state = MeloXLanPlayback.fromJson(event.optJSONObject("state"))
            }
        }
    }

    fun remove(client: MeloXLanClient) { scope.launch {
        peers.remove(client.device.deviceId)?.relay?.let { MeloXLanRuntime.revokePeerRelay(it) }
        if (peers.isEmpty()) stop()
    } }

    fun stop(notifyRemote: Boolean = true) {
        scope.launch {
            round?.cancel(); round = null
            ending?.cancel(); ending = null
            monitor?.cancel(); monitor = null
            val old = peers.values.toList()
            peers.clear(); preparing = false; queueKey = ""
            generation++
            MeloXLanRuntime.playbackHost()?.setTogetherBarrier(false)
            old.forEach { peer ->
                peer.relay?.let { MeloXLanRuntime.revokePeerRelay(it) }
                if (notifyRemote) launch { runCatching { peer.client.stopTogether() } }
            }
        }
    }

    private fun update() {
        if (peers.isEmpty()) return
        val host = MeloXLanRuntime.playbackHost() ?: return
        val state = host.snapshot()
        val queue = host.queueSnapshot()
        if (queue.isEmpty()) return
        val key = queue.joinToString("|") { "${it.source}:${it.id}" } + "/${state.index}/${state.trackId}"
        if (key != queueKey) {
            queueKey = key
            round?.cancel(); ending?.cancel(); ending = null
            val current = ++generation
            preparing = true
            round = scope.launch { prepare(current, queue, state) }
            return
        }
        if (preparing || ending != null) return
        val seek = explicitSeek
        explicitSeek = false
        val sample = if (localPending) state.copy(playing = roundPlayIntent) else state
        peers.values.filter { it.ready && it.client.isConnected }.forEach { peer ->
            if (SystemClock.elapsedRealtime() - peer.calibratedAt >= 30_000L) {
                peer.calibratedAt = SystemClock.elapsedRealtime()
                scope.launch {
                    peer.client.ping()?.let { peer.offsetMs = it }
                }
            }
            val packet = JSONObject().put("type", "event").put("event", "together.state")
                .put("sessionId", sessionId).put("generation", generation)
                .put("state", sample.toJson()).put("index", sample.index)
                .put("positionMs", sample.positionMs).put("playing", sample.playing)
                .put("serverTimeMs", state.sampleTimeMs).put("clockOffsetMs", -peer.offsetMs)
                .put("late", peer.late).put("seek", seek)
            scope.launch(Dispatchers.IO) { peer.client.sendEvent(packet) }
        }
    }

    private suspend fun prepare(current: Long, queue: List<MeloXLanTrack>, state: MeloXLanPlayback) = coroutineScope {
        val host = MeloXLanRuntime.playbackHost() ?: return@coroutineScope
        host.setTogetherBarrier(true)
        val startedAt = SystemClock.elapsedRealtime()
        var committed = false
        var localReady = false
        localPending = true
        roundPlayIntent = state.playing
        val startPosition = state.positionMs
        val local = launch {
            localReady = host.prepareTogetherRound(null, state.index, startPosition, null, false)
            if (localReady && committed && generation == current) {
                localPending = false
                host.commitTogetherRound(System.currentTimeMillis(), startPosition, state.playing)
            }
        }
        peers.values.toList().forEach { peer ->
            peer.ready = false; peer.late = false
            launch {
                try {
                    // Keep the lowest-RTT calibration; every round refreshes it.
                    var bestRtt = Long.MAX_VALUE
                    repeat(3) {
                        val t0 = SystemClock.elapsedRealtime()
                        val offset = peer.client.ping()
                        val rtt = SystemClock.elapsedRealtime() - t0
                        if (offset != null && rtt < bestRtt) { bestRtt = rtt; peer.offsetMs = offset }
                    }
                    peer.calibratedAt = SystemClock.elapsedRealtime()
                    peer.relay?.let { MeloXLanRuntime.revokePeerRelay(it) }
                    peer.relay = if (peer.provideAudio) MeloXLanRuntime.issuePeerRelay(checkNotNull(app), peer.client.device.address, queue) else null
                    if (peer.provideAudio && peer.relay == null) return@launch
                    val payload = JSONObject().put("sessionId", sessionId).put("generation", current)
                        .put("queueRevision", queueKey).put("queue", JSONArray(queue.map { it.toJson() }))
                        .put("index", state.index).put("positionMs", startPosition).put("playing", state.playing)
                    peer.relay?.let { payload.put("relay", it.toJson()) }
                    val barrier = MELOX_LAN_BARRIER in peer.client.capabilities
                    val response = peer.client.request(if (barrier) "together.prepare" else "together.start",
                        payload.apply { if (!barrier) put("playing", state.playing) }, 35_000L)
                    if (generation != current || peers[peer.client.device.deviceId] !== peer) return@launch
                    peer.ready = response?.optBoolean("ok") == true && (!barrier ||
                        response.optString("sessionId") == sessionId && response.optLong("generation") == current)
                    if (!barrier) Log.i("MeloXTogether", "Legacy peer: ${peer.client.device.name}; no prepare barrier")
                    if (peer.ready && committed) {
                        peer.late = true
                        commit(peer, current, System.currentTimeMillis(), startPosition, state.playing)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w("MeloXTogether", "Peer preparation failed", error) }
            }
        }
        while (generation == current && SystemClock.elapsedRealtime() - startedAt < 3_000L &&
            (!localReady || peers.values.any { !it.ready && it.client.isConnected })) delay(10)
        if (generation != current) return@coroutineScope
        committed = true
        val startAt = System.currentTimeMillis() + 200L
        if (!localReady) peers.values.forEach { it.late = true }
        peers.values.filter { it.ready }.forEach { commit(it, current, startAt, startPosition, state.playing) }
        if (localReady) {
            localPending = false
            host.commitTogetherRound(startAt, startPosition, state.playing)
        }
        // Do not broadcast paused PREPARE snapshots before the scheduled start.
        delay(220)
        if (generation == current) preparing = false
        Log.i("MeloXTogether", "Round $current committed, ready=${peers.values.count { it.ready }}/${peers.size}, localReady=$localReady")
        local.join()
    }

    private fun commit(peer: Peer, current: Long, atMs: Long, positionMs: Long, playing: Boolean) {
        if (MELOX_LAN_BARRIER !in peer.client.capabilities) return
        val packet = JSONObject().put("type", "event").put("event", "together.commit")
            .put("sessionId", sessionId).put("generation", current).put("startAtMs", atMs)
            .put("positionMs", positionMs).put("playing", playing).put("late", peer.late)
            .put("clockOffsetMs", -peer.offsetMs)
        scope.launch(Dispatchers.IO) { peer.client.sendEvent(packet) }
    }

    fun ended() { scope.launch {
        if (!active || preparing || ending != null || completedGeneration == generation) return@launch
        val host = MeloXLanRuntime.playbackHost() ?: return@launch
        val current = generation
        ending = scope.launch(start = CoroutineStart.LAZY) {
            val state = host.snapshot()
            val positions = peers.values.filter { it.ready && it.client.isConnected }.map {
                togetherTargetPosition(it.state.positionMs, it.state.playing && it.state.ready, it.state.sampleTimeMs,
                    System.currentTimeMillis(), it.offsetMs)
            }
            if (togetherShouldWaitForEnd(state.positionMs, positions)) {
                val deadline = SystemClock.elapsedRealtime() + 5_000L
                while (SystemClock.elapsedRealtime() < deadline && peers.values.any {
                    it.ready && it.client.isConnected && !it.state.ended
                }) delay(20)
            }
            if (generation == current && host.snapshot().canNext) {
                completedGeneration = current
                host.control("together.next", null, null)
            }
            ending = null
        }
        ending?.start()
    } }
}
