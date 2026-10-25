package com.lladlam.melox.dlna

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object DlnaDiscovery {
    fun location(response: String): String? {
        if (!response.startsWith("HTTP/1.1 200", true)) return null
        return response.lineSequence().firstOrNull { it.substringBefore(':').equals("location", true) }
            ?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http://", true) }
    }

    suspend fun scan(context: Context): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("MeloX-DLNA")?.apply { setReferenceCounted(false) }
        try {
            lock?.acquire()
            val locations = linkedSetOf<String>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                val address = InetAddress.getByName("239.255.255.250")
                listOf("urn:schemas-upnp-org:device:MediaRenderer:1", "ssdp:all").forEach { st ->
                    val bytes = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\nUSER-AGENT: MeloX/1.0 UPnP/1.1\r\n\r\n").toByteArray(Charsets.US_ASCII)
                    repeat(2) { socket.send(DatagramPacket(bytes, bytes.size, address, 1900)) }
                }
                val until = android.os.SystemClock.elapsedRealtime() + 3_000
                while (android.os.SystemClock.elapsedRealtime() < until && locations.size < 48) {
                    coroutineContext.ensureActive()
                    val packet = DatagramPacket(ByteArray(16 * 1024), 16 * 1024)
                    try {
                        socket.receive(packet)
                        location(String(packet.data, 0, packet.length, Charsets.UTF_8))?.let(locations::add)
                    } catch (_: SocketTimeoutException) { }
                }
            }
            coroutineScope {
                // Bound concurrent description fetches so an ssdp:all response burst cannot exhaust sockets.
                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                locations.map { location -> async {
                    semaphore.acquire()
                    try {
                        runCatching {
                            val response = DlnaHttp.request(location)
                            if (response.code == 200) DlnaXml.devices(response.body, location) else emptyList()
                        }.getOrDefault(emptyList())
                    } finally { semaphore.release() }
                } }.awaitAll().flatten().distinctBy { it.id }
            }
        } finally { if (lock?.isHeld == true) lock.release() }
    }
}
