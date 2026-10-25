package com.lladlam.melox.lan

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Media3 DataSource for `melox-lan://` relay URLs.
 *
 * Android's network-security policy blocks cleartext HTTP in ExoPlayer's
 * default stacks. The LAN relay is deliberately plaintext (same Wi-Fi, token
 * protected), so this source speaks the minimal HTTP exchange over a raw
 * socket instead of the platform HTTP client. Non-relay URIs are forwarded to
 * the normal cached/resolving source.
 */
internal class MeloXLanRelayDataSource(
    private val fallback: DataSource,
) : BaseDataSource(true) {
    private var active: DataSource? = null
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var remaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri.scheme != "melox-lan") {
            active = fallback
            return fallback.open(dataSpec)
        }
        transferInitializing(dataSpec)
        val host = dataSpec.uri.host ?: throw java.io.IOException("Relay host missing")
        val port = if (dataSpec.uri.port > 0) dataSpec.uri.port else 80
        val path = buildString {
            append(dataSpec.uri.encodedPath?.ifBlank { "/audio" } ?: "/audio")
            dataSpec.uri.encodedQuery?.let { append('?').append(it) }
        }
        val connection = Socket()
        try {
            connection.connect(InetSocketAddress(host, port), 5_000)
            connection.soTimeout = 15_000
            val writer = BufferedWriter(OutputStreamWriter(connection.getOutputStream(), Charsets.UTF_8))
            writer.write("GET $path HTTP/1.1\r\n")
            writer.write("Host: $host:$port\r\n")
            writer.write("Connection: close\r\n")
            if (dataSpec.position > 0L) writer.write("Range: bytes=${dataSpec.position}-\r\n")
            writer.write("\r\n")
            writer.flush()
            val stream = BufferedInputStream(connection.getInputStream())
            val status = readLine(stream) ?: throw java.io.IOException("Empty relay response")
            val statusCode = status.split(' ').getOrNull(1)?.toIntOrNull()
                ?: throw java.io.IOException("Bad relay response: $status")
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(stream) ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }
            if (statusCode !in 200..299) throw java.io.IOException("Relay HTTP $statusCode")
            val contentLength = headers["content-length"]?.toLongOrNull()
            // A 200 response to a ranged request must be skipped manually;
            // the host normally answers 206 for seeks.
            if (statusCode == 200 && dataSpec.position > 0L) {
                var skipped = 0L
                val buffer = ByteArray(8 * 1024)
                while (skipped < dataSpec.position) {
                    val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), dataSpec.position - skipped).toInt())
                    if (read <= 0) throw java.io.IOException("Relay stream ended before seek position")
                    skipped += read
                }
            }
            socket = connection
            input = stream
            uri = dataSpec.uri
            remaining = contentLength?.let {
                (it - (if (statusCode == 206) 0L else dataSpec.position)).coerceAtLeast(0L)
            } ?: C.LENGTH_UNSET.toLong()
            transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) {
            runCatching { connection.close() }
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        active?.let { return it.read(buffer, offset, length) }
        val stream = input ?: return C.RESULT_END_OF_INPUT
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val max = if (remaining > 0L) minOf(length.toLong(), remaining).toInt() else length
        val read = stream.read(buffer, offset, max)
        if (read > 0) {
            if (remaining > 0L) remaining -= read
            bytesTransferred(read)
        }
        return if (read < 0) C.RESULT_END_OF_INPUT else read
    }

    override fun getUri(): Uri? = active?.uri ?: uri

    override fun close() {
        active?.let {
            it.close()
            active = null
            return
        }
        runCatching { input?.close() }
        runCatching { socket?.close() }
        input = null
        socket = null
        uri = null
        remaining = C.LENGTH_UNSET.toLong()
        transferEnded()
    }

    private fun readLine(stream: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = stream.read()
            if (value < 0) return if (builder.isEmpty()) null else builder.toString()
            if (value == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(value.toChar())
            if (builder.length > 8 * 1024) throw java.io.IOException("Relay header too large")
        }
    }

    class Factory(private val fallback: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = MeloXLanRelayDataSource(fallback.createDataSource())
    }
}
