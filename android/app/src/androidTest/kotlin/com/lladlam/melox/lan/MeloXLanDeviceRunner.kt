package com.lladlam.melox.lan

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.StrictMode
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Real Android socket regression, with a loopback peer and no music/account requests. */
class MeloXLanDeviceRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { start() }

    override fun onStart() {
        val result = Bundle()
        val control = ServerSocket(0)
        val audio = ServerSocket(0)
        val audioBytes = CompletableFuture<Int>()
        var accepted: Socket? = null
        val peer = thread(name = "LanRegressionPeer") {
            try {
                // First connection asks for PIN; the second authenticates.
                repeat(2) { attempt ->
                    control.accept().use { socket ->
                        accepted = socket
                        socket.soTimeout = 15_000
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream().bufferedWriter()
                        fun send(value: JSONObject) {
                            output.write(value.toString()); output.newLine(); output.flush()
                        }
                        val hello = JSONObject(checkNotNull(input.readLine()))
                        check(hello.optString("type") == "hello")
                        if (attempt == 0) {
                            send(JSONObject().put("type", "auth_required"))
                            return@use
                        }
                        check(hello.optString("pin") == "1234")
                        send(JSONObject().put("type", "auth_ok").put("deviceId", "loopback-peer"))
                        while (true) {
                            val request = input.readLine()?.let(::JSONObject) ?: break
                            if (request.optString("type") != "request") continue
                            val response = JSONObject().put("type", "response")
                                .put("id", request.optString("id")).put("ok", true)
                            if (request.optString("action") == "cast.start") {
                                response.put("castPort", audio.localPort).put("format", request.optJSONObject("format"))
                            }
                            send(response)
                        }
                    }
                }
            } catch (error: Exception) {
                audioBytes.completeExceptionally(error)
            }
        }
        val receiver = thread(name = "LanRegressionAudio") {
            try {
                audio.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val input = DataInputStream(socket.getInputStream())
                    var received = 0
                    while (received < 4096) {
                        val count = input.readInt()
                        check(count in 1..65536)
                        val bytes = ByteArray(count)
                        input.readFully(bytes)
                        check(bytes.all { it == 0x25.toByte() })
                        received += count
                    }
                    audioBytes.complete(received)
                    while (input.read() >= 0) { /* Keep stream open until sender stops. */ }
                }
            } catch (error: Exception) { audioBytes.completeExceptionally(error) }
        }
        try {
            runBlocking {
                val device = MeloXLanDevice(
                    deviceId = "loopback-peer", name = "Loopback", platform = "test", model = "test",
                    appVersion = "test", address = "127.0.0.1", port = control.localPort,
                    pinRequired = true, trusted = false, capabilities = emptySet(), playback = MeloXLanPlayback(),
                )
                withContext(Dispatchers.Main) {
                    val previousPolicy = StrictMode.getThreadPolicy()
                    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
                    try {
                        check(MeloXLanSession.connect(targetContext, device, null) == MeloXLanConnectResult.PinRequired)
                        check(MeloXLanSession.client == null)
                        check(MeloXLanSession.connect(targetContext, device, "1234") == MeloXLanConnectResult.Connected)
                        val client = checkNotNull(MeloXLanSession.client)
                        check(client.request("ping")?.optBoolean("ok") == true)
                        result.putBoolean("mainThreadRequest", true)

                        // Stopping a Together session must not close its reusable control connection.
                        repeat(2) {
                            MeloXLanRuntime.beginHostTogether(client) { JSONObject().put("playing", true) }
                            MeloXLanRuntime.stopHostTogether(notifyRemote = false)
                            check(MeloXLanSession.client === client && client.isConnected)
                            check(client.request("ping")?.optBoolean("ok") == true)
                        }
                        result.putBoolean("togetherStopKeepsConnection", true)

                        val handshake = checkNotNull(client.startCast())
                        check(MeloXLanRuntime.startCastSender(targetContext, device, handshake, client))
                        val processor = MeloXCastAudioProcessor()
                        processor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(
                            handshake.format.sampleRate, handshake.format.channels,
                            androidx.media3.common.C.ENCODING_PCM_16BIT,
                        ))
                        processor.flush()
                        val pcm = ByteArray(4096) { 0x25 }
                        processor.queueInput(ByteBuffer.allocateDirect(pcm.size).put(pcm).apply { flip() })
                        val muted = processor.output
                        check(muted.remaining() == pcm.size)
                        while (muted.hasRemaining()) check(muted.get() == 0.toByte())
                        result.putBoolean("castMutesLocalOutput", true)
                        check(withContext(Dispatchers.IO) { audioBytes.get(10, TimeUnit.SECONDS) } == 4096)
                        MeloXLanRuntime.stopCastSender(notifyRemote = false)
                        processor.queueInput(ByteBuffer.allocateDirect(pcm.size).put(pcm).apply { flip() })
                        val restored = ByteArray(pcm.size)
                        processor.output.get(restored)
                        check(restored.contentEquals(pcm))
                        processor.reset()
                        result.putBoolean("castStopRestoresLocalOutput", true)
                        check(client.isConnected && client.request("ping")?.optBoolean("ok") == true)
                        result.putBoolean("castAudioAndReusableConnection", true)
                        MeloXLanSession.disconnect()
                        check(MeloXLanSession.client == null && !client.isConnected)
                        result.putBoolean("explicitDisconnect", true)
                    } finally { StrictMode.setThreadPolicy(previousPolicy) }
                }
            }
            runBlocking { runMeloXLanBarrierRegression(targetContext, result) }
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("error", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            MeloXLanSession.disconnect()
            runCatching { accepted?.close() }
            control.close()
            audio.close()
            peer.join(1_000)
            receiver.join(1_000)
        }
    }
}
