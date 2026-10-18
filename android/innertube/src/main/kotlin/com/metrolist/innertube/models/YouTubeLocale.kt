package com.metrolist.innertube.models

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class YouTubeLocale(
    val gl: String, // geolocation
    val hl: String, // host language
) {
    /** Music browse rejects an empty region and CN with INVALID_ARGUMENT. */
    fun normalized(): YouTubeLocale {
        val region = gl.trim().uppercase(Locale.ROOT)
        val language = hl.trim().takeUnless { it.isEmpty() || it == "und" } ?: "en"
        return copy(
            gl = region.takeIf { it in isoCountries && it != "CN" } ?: "US",
            hl = language,
        )
    }

    companion object {
        private val isoCountries = Locale.getISOCountries().toSet()

        fun fromLocale(locale: Locale = Locale.getDefault()): YouTubeLocale =
            YouTubeLocale(gl = locale.country, hl = locale.toLanguageTag()).normalized()
    }
}
