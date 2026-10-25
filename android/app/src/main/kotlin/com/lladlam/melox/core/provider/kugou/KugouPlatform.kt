package com.lladlam.melox.core.provider.kugou

/**
 * Wire-level identity for the two KuGou Android clients.
 *
 * The standard and "lite" (酷狗概念版) apps use different appids, client
 * versions, request salts and login tokens. Tokens are deliberately not
 * interchangeable, so the platform value travels with [KugouSession] instead of
 * being a process-wide flag.
 */
enum class KugouPlatform(
    val appId: Int,
    val clientVersion: Int,
    val androidSignatureSalt: String,
    val keySignatureSalt: String,
    val userAgent: String,
    val isLite: Boolean,
) {
    Standard(
        appId = 1005,
        clientVersion = 20489,
        androidSignatureSalt = "OIlwieks28dk2k092lksi2UIkp",
        keySignatureSalt = "57ae12eb6890223e355ccfcb74edf70d",
        userAgent = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi",
        isLite = false,
    ),
    Lite(
        appId = 3116,
        clientVersion = 11440,
        androidSignatureSalt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA",
        keySignatureSalt = "185672dd44712f60bb1736df5a377e82",
        userAgent = "Android15-1070-11440-46-0-DiscoveryDRADProtocol-wifi",
        isLite = true,
    ),
}
