package com.lladlam.melox.dlna

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.lladlam.melox.lan.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Real sockets and session lifecycle, using a loopback renderer instead of a user's speaker. */
class DlnaDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { start() }
    override fun onStart() {
        val result = Bundle()
        val server = ServerSocket(0)
        val file = File(targetContext.cacheDir, "dlna-regression.mp3").apply { writeBytes(ByteArray(4096) { 0x31 }) }
        var state = "STOPPED"
        var currentUri = ""
        var seek = "00:00:00"
        var volume = "40"
        var requests = 0
        var localPlaying = true
        val host = object : MeloXLanHost {
            override fun snapshot() = MeloXLanPlayback(playing = localPlaying, trackId = "test:1", ready = true)
            override fun queueSnapshot() = emptyList<MeloXLanTrack>()
            override fun currentTrack(): MeloXLanTrack? = null
            override suspend fun dlnaSource() = DlnaMediaSource(file.toURI().toString(), emptyMap(), "Test & <Track>", "Artist", "Album", null, 120000, 0, "test:1", "audio/mpeg")
            override fun control(command: String, positionMs: Long?, volume: Int?): Boolean { if (command == "pause") localPlaying = false; return true }
            override fun transfer(track: MeloXLanTrack, queue: List<MeloXLanTrack>, positionMs: Long) = false
            override fun startCast(format: MeloXLanCastFormat): MeloXLanCastHandshake? = null
            override fun stopCast() {}
            override fun startTogether(queue: List<MeloXLanTrack>, index: Int, positionMs: Long, playing: Boolean, relay: MeloXLanRelay?, sameDevice: Boolean) = false
            override fun stopTogether() {}
            override fun applyTogetherState(positionMs: Long, playing: Boolean, index: Int) {}
            override fun setTogetherBarrier(enabled: Boolean) {}
            override suspend fun prepareTogetherRound(queue: List<MeloXLanTrack>?, index: Int, positionMs: Long, relay: MeloXLanRelay?, sameDevice: Boolean) = false
            override fun commitTogetherRound(startAtLocalMs: Long, positionMs: Long, playing: Boolean) {}
        }
        val worker = thread(name = "FakeDlnaRenderer") {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream()
                    fun line(): String = buildString {
                        while (true) { val next = input.read(); check(next >= 0); if (next == 10) break; if (next != 13) append(next.toChar()) }
                    }
                    line()
                    var length = 0
                    var action = ""
                    while (true) {
                        val header = line(); if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                        if (header.startsWith("SOAPACTION:", true)) action = header.substringAfter('#').trim().trim('"')
                    }
                    val body = ByteArray(length); DataInputStream(input).readFully(body)
                    val arguments = DlnaXml.fields(String(body, Charsets.UTF_8))
                    val values = when (action) {
                        "SetAVTransportURI" -> {
                            currentUri = checkNotNull(arguments["CurrentURI"])
                            check(DlnaXml.fields(checkNotNull(arguments["CurrentURIMetaData"]))["title"] == "Test & <Track>")
                            ""
                        }
                        "Play" -> {
                            val uri = URI(currentUri)
                            Socket(uri.host, uri.port).use { client ->
                                client.soTimeout = 3000
                                client.getOutputStream().write("GET ${uri.rawPath} HTTP/1.1\r\nHost: ${uri.rawAuthority}\r\nRange: bytes=32-63\r\nConnection: close\r\n\r\n".toByteArray())
                                val received = ByteArrayOutputStream().also { client.getInputStream().copyTo(it) }.toByteArray()
                                val text = String(received, Charsets.ISO_8859_1)
                                check(text.startsWith("HTTP/1.1 206"))
                                check(text.contains("Content-Range: bytes 32-63/4096"))
                                check(received.takeLast(32).all { it == 0x31.toByte() })
                                requests++
                            }
                            state = "PLAYING"; ""
                        }
                        "Pause" -> { state = "PAUSED_PLAYBACK"; "" }
                        "Stop" -> { state = "STOPPED"; "" }
                        "Seek" -> { seek = checkNotNull(arguments["Target"]); "" }
                        "SetVolume" -> { volume = checkNotNull(arguments["DesiredVolume"]); "" }
                        "GetVolume" -> "<CurrentVolume>$volume</CurrentVolume>"
                        "GetTransportInfo" -> "<CurrentTransportState>$state</CurrentTransportState>"
                        "GetPositionInfo" -> "<RelTime>$seek</RelTime><TrackDuration>00:02:00</TrackDuration>"
                        else -> error("Unknown action: $action")
                    }
                    val response = "<Envelope><Body><Response>$values</Response></Body></Envelope>".toByteArray()
                    socket.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(response); flush() }
                }
            } catch (_: Exception) { runCatching { server.close() } }
        }
        try {
            runBlocking {
                withContext(Dispatchers.Main) {
                    MeloXLanRuntime.attach(targetContext, host)
                    val transport = DlnaService("urn:schemas-upnp-org:service:AVTransport:1", "http://127.0.0.1:${server.localPort}/av")
                    val renderer = DlnaDevice("uuid:regression", "Test renderer", "Test", "Test", "", transport,
                        DlnaService("urn:schemas-upnp-org:service:RenderingControl:1", "http://127.0.0.1:${server.localPort}/volume"))
                    check(DlnaSession.cast(targetContext, renderer)) { DlnaSession.error.orEmpty() }
                    check(requests > 0 && !localPlaying && DlnaSession.active)
                    result.putBoolean("soapMetadataAndHttpRangePlayback", true)
                    delay(500)
                    check(DlnaSession.active)
                    result.putBoolean("sessionSurvivesWithoutPanel", true)
                    DlnaSession.seek(65_000)
                    withTimeout(5000) { while (seek != "00:01:05") delay(20) }
                    DlnaSession.changeVolume(.55f)
                    withTimeout(5000) { while (volume != "55") delay(20) }
                    result.putBoolean("remoteSeekAndVolume", true)
                    DlnaSession.disconnect(resumeLocal = false)
                    check(!DlnaSession.active && state == "STOPPED")
                    result.putBoolean("explicitDisconnectStopsRenderer", true)
                    MeloXLanRuntime.detach(host)
                }
            }
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("error", failure.stackTraceToString()); finish(Activity.RESULT_CANCELED, result)
        } finally {
            runCatching { server.close() }; worker.join(1000); file.delete()
        }
    }
}
