package com.lladlam.melox.lan

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import org.json.JSONObject

/** Exercises the production JSON client/server and coordinator, without accounts or a UI. */
internal suspend fun runMeloXLanBarrierRegression(context: Context, result: Bundle) = withContext(Dispatchers.Main) {
    class Host(var prepareDelay: Long) : MeloXLanHost {
        var tracks = listOf(MeloXLanTrack("netease", "101", "First", "Artist", "Album", durationMs = 10_000))
        var state = MeloXLanPlayback(trackId = "netease:101", durationMs = 10_000, ready = true, playing = true, queueLength = 1)
        val commits = CopyOnWriteArrayList<Pair<Long, Long>>()
        var nextCalls = 0
        override fun snapshot() = state.copy(sampleTimeMs = System.currentTimeMillis())
        override fun queueSnapshot() = tracks
        override fun currentTrack() = tracks.firstOrNull()
        override fun control(command: String, positionMs: Long?, volume: Int?): Boolean {
            if (command == "together.next") nextCalls++
            return true
        }
        override fun transfer(track: MeloXLanTrack, queue: List<MeloXLanTrack>, positionMs: Long) = false
        override fun startCast(format: MeloXLanCastFormat): MeloXLanCastHandshake? = null
        override fun stopCast() {}
        override fun startTogether(queue: List<MeloXLanTrack>, index: Int, positionMs: Long, playing: Boolean, relay: MeloXLanRelay?, sameDevice: Boolean) = false
        override fun stopTogether() { state = state.copy(playing = false) }
        override fun applyTogetherState(positionMs: Long, playing: Boolean, index: Int) {}
        override fun setTogetherBarrier(enabled: Boolean) {}
        override suspend fun prepareTogetherRound(queue: List<MeloXLanTrack>?, index: Int, positionMs: Long, relay: MeloXLanRelay?, sameDevice: Boolean): Boolean {
            state = state.copy(ready = false, playing = false)
            delay(prepareDelay)
            if (queue != null) tracks = queue
            state = state.copy(ready = true, index = index, positionMs = positionMs, trackId = "${tracks[index].source}:${tracks[index].id}")
            return true
        }
        override fun commitTogetherRound(startAtLocalMs: Long, positionMs: Long, playing: Boolean) {
            commits += startAtLocalMs to positionMs
            state = state.copy(playing = playing)
        }
    }
    val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val master = Host(20)
    val fast = Host(30)
    val slow = Host(3_400)
    val servers = mutableListOf<MeloXLanServer>()
    val clients = mutableListOf<MeloXLanClient>()
    try {
        MeloXLanRuntime.attach(context, master)
        for ((index, host) in listOf(fast, slow).withIndex()) {
            val isolated = object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String, mode: Int) =
                    context.getSharedPreferences("lan-regression-$index-$name", mode)
            }
            val identity = MeloXLanPreferences(isolated)
            identity.port = ServerSocket(0).use { it.localPort }
            val server = MeloXLanServer(host, identity, jobs)
            servers += server
            val port = server.start()
            check(port > 0)
            val device = MeloXLanDevice("regression-$index", "Peer $index", "test", address = "127.0.0.1", port = port)
            lateinit var client: MeloXLanClient
            client = MeloXLanRuntime.newClient(context, device, onEvent = { MeloXLanRuntime.onPeerEvent(client, it) })
            clients += client
            check(client.connect(identity.pin) == MeloXLanConnectResult.Connected)
            check(MELOX_LAN_BARRIER in client.capabilities)
            check(MeloXLanRuntime.startTogetherSession(context, client, provideAudio = false))
        }
        withTimeout(8_000) { while (master.commits.isEmpty() || fast.commits.isEmpty() || slow.commits.isEmpty()) delay(20) }
        check(kotlin.math.abs(master.commits.last().first - fast.commits.last().first) < 50)
        check(slow.commits.last().second == 0L)
        check(slow.commits.last().first > fast.commits.last().first)
        result.putBoolean("barrierDeadlineAndLateStart", true)
        slow.prepareDelay = 20
        master.tracks = listOf(MeloXLanTrack("netease", "202", "Replacement", "Artist", "Album", durationMs = 10_000))
        master.state = master.state.copy(trackId = "netease:202", positionMs = 0, playing = true)
        MeloXLanRuntime.onPlaybackUpdated()
        withTimeout(5_000) { while (fast.tracks.first().id != "202" || slow.tracks.first().id != "202" || slow.commits.size < 2) delay(20) }
        result.putBoolean("playlistReplacementSameIndex", true)
        val count = fast.commits.size
        withContext(Dispatchers.IO) {
            clients.first().sendEvent(JSONObject().put("type", "event").put("event", "together.commit")
                .put("sessionId", "obsolete-session").put("generation", 0).put("playing", true))
        }
        delay(100)
        check(fast.commits.size == count)
        result.putBoolean("staleCommitIgnored", true)
        fast.state = fast.state.copy(positionMs = 10_000, ended = true, playing = false)
        slow.state = slow.state.copy(positionMs = 9_000, ended = false, playing = true)
        master.state = master.state.copy(positionMs = 10_000, ended = true, playing = false, canNext = true)
        delay(250)
        MeloXLanRuntime.onTogetherTrackEnded()
        delay(300)
        check(master.nextCalls == 0)
        slow.state = slow.state.copy(positionMs = 10_000, ended = true, playing = false)
        withTimeout(2_000) { while (master.nextCalls == 0) delay(20) }
        MeloXLanRuntime.onTogetherTrackEnded()
        delay(100)
        check(master.nextCalls == 1)
        result.putBoolean("endBarrierWaitsAndAdvancesOnce", true)
    } finally {
        MeloXLanTogetherCoordinator.stop()
        clients.forEach { it.close() }
        servers.forEach { it.stop() }
        jobs.cancel()
        MeloXLanRuntime.detach(master)
    }
}
