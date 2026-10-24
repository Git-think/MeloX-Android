package com.lladlam.melox.usb

internal object UsbPcm {
    /** Full volume preserves the tap bytes; lower volume is explicitly software attenuation. */
    fun scale(bytes: ByteArray, gain: Float): ByteArray {
        if (gain >= 1f) return bytes
        val result = bytes.copyOf()
        for (offset in 0 until bytes.size - 1 step 2) {
            val sample = ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8))
            val scaled = (sample * gain.coerceIn(0f, 1f)).toInt().coerceIn(-32768, 32767)
            result[offset] = scaled.toByte(); result[offset + 1] = (scaled shr 8).toByte()
        }
        return result
    }
}
