package com.lladlam.melox.dlna

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal data class DlnaService(val type: String, val controlUrl: String)
internal data class DlnaDevice(
    val id: String, val name: String, val manufacturer: String, val model: String,
    val location: String, val transport: DlnaService, val rendering: DlnaService?,
)
data class DlnaMediaSource(
    val uri: String, val headers: Map<String, String>, val title: String, val artist: String,
    val album: String, val artwork: String?, val durationMs: Long, val positionMs: Long,
    val mediaId: String, val mimeType: String,
)

internal object DlnaXml {
    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    fun parse(xml: String): Element {
        require(xml.length <= 512 * 1024 && !xml.contains("<!DOCTYPE", true)) { "Invalid device XML" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            ).forEach { feature -> runCatching { setFeature(feature, false) } }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
    }

    fun children(element: Element, name: String): List<Element> = buildList {
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if ((node.localName ?: node.tagName.substringAfter(':')) == name) add(node)
        }
    }
    fun text(element: Element, name: String): String = children(element, name).firstOrNull()?.textContent?.trim().orEmpty()
    fun fields(xml: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        fun visit(element: Element) {
            if ((0 until element.childNodes.length).none { element.childNodes.item(it) is Element }) {
                result[element.localName ?: element.tagName.substringAfter(':')] = element.textContent.trim()
            }
            val nodes = element.childNodes
            for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let(::visit)
        }
        visit(parse(xml))
        return result
    }

    fun devices(xml: String, location: String): List<DlnaDevice> {
        val root = parse(xml)
        val base = URI(text(root, "URLBase").ifBlank { location })
        val devices = mutableListOf<DlnaDevice>()
        fun visit(device: Element) {
            val services = children(device, "serviceList").flatMap { children(it, "service") }.mapNotNull {
                val type = text(it, "serviceType")
                val control = text(it, "controlURL")
                if (control.isBlank()) null else DlnaService(type, base.resolve(control).toString())
            }
            val transport = services.firstOrNull { it.type.startsWith("urn:schemas-upnp-org:service:AVTransport:") }
            if (transport != null) devices += DlnaDevice(
                text(device, "UDN").ifBlank { transport.controlUrl },
                text(device, "friendlyName").ifBlank { "DLNA" }, text(device, "manufacturer"), text(device, "modelName"),
                location, transport, services.firstOrNull { it.type.startsWith("urn:schemas-upnp-org:service:RenderingControl:") },
            )
            children(device, "deviceList").flatMap { children(it, "device") }.forEach(::visit)
        }
        children(root, "device").forEach(::visit)
        return devices.distinctBy { it.id }
    }
}

/** LAN-only HTTP for dynamic renderer IPs; keeps the app's global HTTPS policy intact. */
internal object DlnaHttp {
    data class Response(val code: Int, val body: String)
    fun request(url: String, action: String? = null, body: String = ""): Response {
        val uri = URI(url)
        require(uri.scheme == "http" && uri.userInfo == null) { "Renderer must expose a LAN HTTP endpoint" }
        val address = InetAddress.getByName(uri.host)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) { "Renderer address is not local" }
        val port = if (uri.port > 0) uri.port else 80
        return Socket().use { socket ->
            socket.soTimeout = 4_000
            socket.connect(InetSocketAddress(address, port), 3_000)
            val bytes = body.toByteArray(Charsets.UTF_8)
            val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
            val headers = buildString {
                append(if (action == null) "GET" else "POST").append(" $path HTTP/1.1\r\n")
                append("Host: ${uri.rawAuthority}\r\nConnection: close\r\n")
                if (action != null) {
                    require(!action.contains('\r') && !action.contains('\n'))
                    append("Content-Type: text/xml; charset=\"utf-8\"\r\nSOAPACTION: \"$action\"\r\nContent-Length: ${bytes.size}\r\n")
                }
                append("\r\n")
            }
            socket.getOutputStream().apply { write(headers.toByteArray(Charsets.US_ASCII)); if (action != null) write(bytes); flush() }
            val input = socket.getInputStream().buffered()
            val status = line(input).split(' ').getOrNull(1)?.toIntOrNull() ?: error("Invalid renderer HTTP response")
            val responseHeaders = mutableMapOf<String, String>()
            var headerBytes = 0
            while (true) {
                val value = line(input)
                headerBytes += value.length
                require(headerBytes < 64 * 1024)
                if (value.isEmpty()) break
                responseHeaders[value.substringBefore(':').lowercase()] = value.substringAfter(':').trim()
            }
            val output = ByteArrayOutputStream()
            fun copy(count: Int) {
                require(count >= 0 && output.size() + count <= 512 * 1024)
                repeat(count) { val next = input.read(); check(next >= 0); output.write(next) }
            }
            if (responseHeaders["transfer-encoding"]?.contains("chunked", true) == true) {
                while (true) {
                    val count = line(input).substringBefore(';').trim().toInt(16)
                    if (count == 0) break
                    copy(count); check(line(input).isEmpty())
                }
            } else {
                val length = responseHeaders["content-length"]?.toIntOrNull()
                if (length != null) copy(length) else {
                    while (true) {
                        val next = input.read(); if (next < 0) break
                        require(output.size() < 512 * 1024); output.write(next)
                    }
                }
            }
            Response(status, output.toString("UTF-8"))
        }
    }
    private fun line(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            check(next >= 0) { "Renderer closed the response" }
            if (next == 10) break
            require(bytes.size() < 8192)
            if (next != 13) bytes.write(next)
        }
        return bytes.toString("US-ASCII")
    }
}

internal object DlnaController {
    fun call(service: DlnaService, method: String, arguments: Map<String, String> = emptyMap()): Map<String, String> {
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$method xmlns:u="${DlnaXml.escape(service.type)}"><InstanceID>0</InstanceID>""" +
            arguments.entries.joinToString("") { (key, value) -> "<$key>${DlnaXml.escape(value)}</$key>" } + "</u:$method></s:Body></s:Envelope>"
        val response = DlnaHttp.request(service.controlUrl, "${service.type}#$method", body)
        val fields = if (response.body.isBlank()) emptyMap() else DlnaXml.fields(response.body)
        check(response.code in 200..299 && "errorCode" !in fields) {
            "${fields["errorDescription"] ?: "DLNA $method failed"} (${fields["errorCode"] ?: response.code})"
        }
        return fields
    }
    fun time(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    fun millis(value: String?): Long {
        val parts = value?.split(':') ?: return 0
        if (parts.size != 3) return 0
        return ((parts[0].toLongOrNull() ?: 0) * 3600 + (parts[1].toLongOrNull() ?: 0) * 60 +
            (parts[2].toDoubleOrNull() ?: 0.0).toLong()) * 1000
    }
    fun metadata(source: DlnaMediaSource, url: String): String =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>${DlnaXml.escape(source.title)}</dc:title><upnp:artist>${DlnaXml.escape(source.artist)}</upnp:artist><upnp:album>${DlnaXml.escape(source.album)}</upnp:album><upnp:class>object.item.audioItem.musicTrack</upnp:class>""" +
            (source.artwork?.let { "<upnp:albumArtURI>${DlnaXml.escape(it)}</upnp:albumArtURI>" } ?: "") +
            "<res protocolInfo=\"http-get:*:${DlnaXml.escape(source.mimeType)}:*\" duration=\"${time(source.durationMs)}\">${DlnaXml.escape(url)}</res></item></DIDL-Lite>"
}
