package com.lladlam.melox.lan

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MeloXCast"

/**
 * Process-wide tap into Media3's decoded PCM. Attached only to the main
 * playback player, so cast sends exactly what the local user hears.
 */
internal object MeloXCastAudioTap {
    @Volatile private var listener: ((ByteArray) -> Unit)? = null
    @Volatile private var formatListener: ((MeloXLanCastFormat) -> Unit)? = null
    @Volatile var format: MeloXLanCastFormat = MeloXLanCastFormat()
        private set

    fun attach(listener: (ByteArray) -> Unit) {
        this.listener = listener
    }

    fun detach(listener: (ByteArray) -> Unit) {
        if (this.listener === listener) this.listener = null
    }

    fun isActive(): Boolean = listener != null

    fun onFormatChanged(format: MeloXLanCastFormat) {
        if (this.format == format) return
        this.format = format
        formatListener?.invoke(format)
    }

    fun setFormatListener(listener: ((MeloXLanCastFormat) -> Unit)?) {
        formatListener = listener
    }

    internal fun offer(bytes: ByteArray) {
        listener?.invoke(bytes)
    }
}

@UnstableApi
internal class MeloXCastAudioProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            MeloXCastAudioTap.onFormatChanged(
                MeloXLanCastFormat(
                    sampleRate = inputAudioFormat.sampleRate,
                    channels = inputAudioFormat.channelCount.coerceIn(1, 2),
                ),
            )
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return
        val output = replaceOutputBuffer(remaining)
        if (MeloXCastAudioTap.isActive()) {
            val data = ByteArray(remaining)
            inputBuffer.duplicate().get(data)
            output.put(data)
            inputBuffer.position(inputBuffer.limit())
            MeloXCastAudioTap.offer(data)
        } else {
            output.put(inputBuffer)
        }
        output.flip()
    }
}

internal class MeloXLanCastReceiver(
    private val scope: CoroutineScope,
) {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    @Volatile private var audioTrack: AudioTrack? = null

    fun start(format: MeloXLanCastFormat): MeloXLanCastHandshake? {
        stop()
        val socket = runCatching { ServerSocket(0) }.getOrElse {
            Log.w(TAG, "Unable to open cast receiver port", it)
            return null
        }
        serverSocket = socket
        job = scope.launch(Dispatchers.IO) {
            try {
                val client = socket.accept()
                client.use { receive(it, format) }
            } catch (error: Exception) {
                if (serverSocket != null) Log.w(TAG, "Cast receiver stopped", error)
            } finally {
                releaseTrack()
            }
        }
        return MeloXLanCastHandshake(socket.localPort, format)
    }

    private fun receive(socket: Socket, format: MeloXLanCastFormat) {
        val track = buildTrack(format) ?: return
        audioTrack = track
        track.play()
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        while (scope.isActive && serverSocket != null) {
            val length = input.readInt()
            if (length <= 0 || length > 64 * 1024) break
            val data = ByteArray(length)
            input.readFully(data)
            track.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun buildTrack(format: MeloXLanCastFormat): AudioTrack? = runCatching {
        val channelMask = if (format.channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(format.sampleRate)
            .setChannelMask(channelMask)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            format.sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(format.sampleRate * format.channels * 2 / 5)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }.onFailure { Log.w(TAG, "Unable to create cast AudioTrack", it) }.getOrNull()

    fun stop() {
        val socket = serverSocket
        serverSocket = null
        runCatching { socket?.close() }
        job?.cancel()
        job = null
        releaseTrack()
    }

    private fun releaseTrack() {
        val track = audioTrack ?: return
        audioTrack = null
        runCatching { track.stop() }
        runCatching { track.release() }
    }
}

internal class MeloXLanCastSender(
    private val device: MeloXLanDevice,
    private val handshake: MeloXLanCastHandshake,
    private val scope: CoroutineScope,
    private val onStopped: (reason: String?) -> Unit = {},
) {
    private var socket: Socket? = null
    private var job: Job? = null
    private val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
    private val queue = ArrayBlockingQueue<ByteArray>(64)
    private val tapListener: (ByteArray) -> Unit = { bytes -> offer(bytes) }

    fun start(): Boolean {
        val target = runCatching {
            Socket().apply { connect(InetSocketAddress(device.address, handshake.port), 3_000) }
        }.getOrElse {
            onStopped(it.message ?: "connect_failed")
            return false
        }
        socket = target
        MeloXCastAudioTap.attach(tapListener)
        MeloXCastAudioTap.setFormatListener { newFormat ->
            if (newFormat != handshake.format) {
                // v1 does not resample mid-session; stop and let the UI start a
                // fresh cast with the new format.
                stop("format_changed")
            }
        }
        job = scope.launch(Dispatchers.IO) {
            try {
                val output = DataOutputStream(target.getOutputStream())
                while (scope.isActive) {
                    val packet = queue.take()
                    output.writeInt(packet.size)
                    output.write(packet)
                    output.flush()
                }
            } catch (error: Exception) {
                if (socket != null) onStopped(error.message)
            } finally {
                closeSocket()
            }
        }
        return true
    }

    private fun offer(bytes: ByteArray) {
        if (!queue.offer(bytes)) {
            queue.poll()
            queue.offer(bytes)
        }
    }

    fun stop(reason: String? = null) {
        if (!stopped.compareAndSet(false, true)) return
        val wasRunning = socket != null || job != null
        closeSocket()
        if (wasRunning) onStopped(reason)
    }

    private fun closeSocket() {
        MeloXCastAudioTap.detach(tapListener)
        MeloXCastAudioTap.setFormatListener(null)
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
        queue.clear()
    }
}
