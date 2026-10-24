package com.lladlam.melox.dlna

import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class DlnaProtocolTest {
    @Test fun embeddedRenderersKeepTheirOwnServicesAndResolveUrlBase() {
        val devices = DlnaXml.devices("""
            <root xmlns="urn:schemas-upnp-org:device-1-0"><URLBase>http://192.168.1.2:5000/base/</URLBase>
            <device><friendlyName>Gateway</friendlyName><deviceList>
              <device><UDN>uuid:speaker</UDN><friendlyName>小爱音箱</friendlyName><serviceList>
                <service><serviceType>urn:schemas-upnp-org:service:AVTransport:2</serviceType><controlURL>av</controlURL></service>
              </serviceList></device>
              <device><UDN>uuid:tv</UDN><friendlyName>TV</friendlyName><serviceList>
                <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/tv/av</controlURL></service>
                <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>volume</controlURL></service>
              </serviceList></device>
            </deviceList></device></root>
        """.trimIndent(), "http://192.168.1.2:80/description.xml")
        assertEquals(2, devices.size)
        assertEquals("http://192.168.1.2:5000/base/av", devices[0].transport.controlUrl)
        assertNull(devices[0].rendering)
        assertEquals("http://192.168.1.2:5000/tv/av", devices[1].transport.controlUrl)
        assertEquals("http://192.168.1.2:5000/base/volume", devices[1].rendering?.controlUrl)
    }

    @Test fun ssdpHeaderNamesAreCaseInsensitiveAndPreserveLocationPort() {
        assertEquals("http://192.168.1.2:1234/desc.xml", DlnaDiscovery.location(
            "HTTP/1.1 200 OK\r\nlOcAtIoN: http://192.168.1.2:1234/desc.xml\r\nUSN: uuid:1\r\n\r\n"))
        assertNull(DlnaDiscovery.location("NOTIFY * HTTP/1.1\r\nLOCATION: http://192.168.1.2/x"))
    }

    @Test fun rangeRequestsSupportSuffixAndRejectUnsatisfiableRanges() {
        assertEquals(100L..199L, DlnaHttpServer.byteRange("bytes=100-199", 1000))
        assertEquals(900L..999L, DlnaHttpServer.byteRange("bytes=-100", 1000))
        assertEquals(900L..999L, DlnaHttpServer.byteRange("bytes=900-", 1000))
        assertNull(DlnaHttpServer.byteRange("bytes=1000-", 1000))
        assertNull(DlnaHttpServer.byteRange("bytes=0-1,4-5", 1000))
    }

    @Test fun metadataSurvivesBothXmlEscapingLayersAndSoapUsesAdvertisedServiceVersion() {
        ServerSocket(0).use { server ->
            val captured = CompletableFuture<String>()
            val worker = thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val input = socket.getInputStream()
                        fun line(): String = buildString {
                            while (true) { val next = input.read(); check(next >= 0); if (next == 10) break; if (next != 13) append(next.toChar()) }
                        }
                        val request = StringBuilder(line())
                        var length = 0
                        while (true) {
                            val line = line(); if (line.isEmpty()) break
                            request.append('\n').append(line)
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        }
                        val body = ByteArray(length)
                        java.io.DataInputStream(input).readFully(body)
                        captured.complete(request.append('\n').append(String(body, Charsets.UTF_8)).toString())
                        val response = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><PlayResponse/></s:Body></s:Envelope>".toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(response); flush()
                        }
                    }
                } catch (error: Exception) { captured.completeExceptionally(error) }
            }
            val source = DlnaMediaSource("https://example.com/song.mp3?a=1&b=2", emptyMap(), "歌 & <曲>", "歌手", "专辑", null, 225000, 0, "1", "audio/mpeg")
            val service = DlnaService("urn:schemas-upnp-org:service:AVTransport:2", "http://127.0.0.1:${server.localPort}/av")
            val metadata = DlnaController.metadata(source, source.uri)
            DlnaController.call(service, "SetAVTransportURI", mapOf("CurrentURI" to source.uri, "CurrentURIMetaData" to metadata))
            val request = captured.get(3, TimeUnit.SECONDS)
            assertTrue(request.contains("SOAPACTION: \"${service.type}#SetAVTransportURI\""))
            val outer = DlnaXml.fields(request.substringAfter("<?xml").let { "<?xml$it" })
            assertEquals(source.uri, outer["CurrentURI"])
            assertEquals(source.title, DlnaXml.fields(checkNotNull(outer["CurrentURIMetaData"]))["title"])
            worker.join(3000)
        }
    }

    @Test fun descriptionCannotLoadExternalEntities() {
        assertThrows(IllegalArgumentException::class.java) {
            DlnaXml.parse("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><root>&x;</root>")
        }
    }
}
