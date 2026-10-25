package com.lladlam.melox.usb

/** Supported first driver profile: full-speed UAC1, stereo PCM16, adaptive/synchronous OUT. */
internal data class UsbAudioStream(
    val interfaceNumber: Int,
    val alternate: Int,
    val endpoint: Int,
    val maxPacketSize: Int,
    val rates: List<Int>,
    val rateRange: IntRange?,
    val frequencyControl: Boolean,
) {
    fun supports(rate: Int): Boolean = rate in rates || rateRange?.contains(rate) == true
}

internal object UsbAudioDescriptors {
    fun parse(raw: ByteArray): List<UsbAudioStream> {
        fun u(at: Int) = raw[at].toInt() and 255
        fun triple(at: Int) = u(at) or (u(at + 1) shl 8) or (u(at + 2) shl 16)
        val result = mutableListOf<UsbAudioStream>()
        var number = -1; var alt = 0; var streaming = false
        var pcm = false; var format = false; var endpoint = 0; var maxPacket = 0
        var rates = emptyList<Int>(); var range: IntRange? = null; var frequencyControl = false
        var configurations = 0
        fun flush() {
            if (streaming && pcm && format && alt > 0 && endpoint != 0 && (rates.isNotEmpty() || range != null)) {
                result += UsbAudioStream(number, alt, endpoint, maxPacket, rates, range, frequencyControl)
            }
        }
        var at = 0
        while (at + 2 <= raw.size) {
            val length = u(at); val type = u(at + 1)
            if (length < 2 || at + length > raw.size) return emptyList()
            when (type) {
                2 -> { // Android's UsbDevice interfaces refer to the first configuration.
                    if (++configurations > 1) break
                }
                4 -> {
                    if (length < 9) return emptyList()
                    flush()
                    number = u(at + 2); alt = u(at + 3)
                    streaming = u(at + 5) == 1 && u(at + 6) == 2 && u(at + 7) == 0
                    pcm = false; format = false; endpoint = 0; maxPacket = 0
                    rates = emptyList(); range = null; frequencyControl = false
                }
                0x24 -> if (streaming && length >= 3) {
                    when (u(at + 2)) {
                        1 -> if (length >= 7) pcm = u(at + 5) == 1 && u(at + 6) == 0
                        2 -> if (length >= 8) {
                            format = u(at + 3) == 1 && u(at + 4) == 2 && u(at + 5) == 2 && u(at + 6) == 16
                            val count = u(at + 7)
                            if (count == 0) {
                                if (length < 14) return emptyList()
                                val low = triple(at + 8); val high = triple(at + 11)
                                if (low <= high && low > 0) range = low..high
                            } else {
                                if (length < 8 + count * 3) return emptyList()
                                rates = List(count) { triple(at + 8 + it * 3) }.filter { it > 0 }
                            }
                        }
                    }
                }
                5 -> if (streaming) {
                    if (length < 7) return emptyList()
                    val attributes = u(at + 3)
                    val sync = (attributes shr 2) and 3
                    // Asynchronous endpoints require a separate feedback clock driver.
                    if (u(at + 2) and 0x80 == 0 && attributes and 3 == 1 && sync in 2..3 &&
                        (attributes shr 4) and 3 == 0 && u(at + 6) == 1 && (length < 9 || u(at + 8) == 0)) {
                        endpoint = u(at + 2)
                        maxPacket = u(at + 4) or (u(at + 5) shl 8)
                        if (maxPacket !in 4..1023) return emptyList()
                    }
                }
                0x25 -> if (endpoint != 0 && length >= 4 && u(at + 2) == 1) {
                    frequencyControl = u(at + 3) and 1 != 0
                }
            }
            at += length
        }
        flush()
        return result
    }
}
