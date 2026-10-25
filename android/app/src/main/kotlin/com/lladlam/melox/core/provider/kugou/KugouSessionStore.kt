package com.lladlam.melox.core.provider.kugou

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class KugouSession(
    val token: String,
    val userId: Long,
    val vipToken: String,
    val vipType: Int,
    val dfid: String,
    val mid: String,
    val guid: String,
    val dev: String,
    val mac: String,
    val webGl: String,
    val platform: KugouPlatform = KugouPlatform.Standard,
    val auth: String = "",
    val vipExpiresAtEpochSeconds: Long = 0L,
    val superVip: Boolean = false,
    val vipActive: Boolean = false,
    val freeListenEligible: Boolean = false,
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank() && userId > 0L

    fun asCookieMap(): Map<String, String> = buildMap {
        put("KUGOU_API_MID", mid)
        put("KUGOU_API_GUID", guid)
        put("KUGOU_API_DEV", dev)
        put("KUGOU_API_MAC", mac)
        put("KUGOU_API_WEBGL", webGl)
        if (dfid.isNotBlank()) put("dfid", dfid)
        if (token.isNotBlank()) put("token", token)
        if (userId > 0L) put("userid", userId.toString())
        if (vipToken.isNotBlank()) put("vip_token", vipToken)
        if (auth.isNotBlank()) put("auth", auth)
        put("vip_type", vipType.toString())
    }
}

/**
 * Keeps Kugou's device identity separate from its user login state. Clearing an
 * account therefore does not silently turn the phone into a new Kugou device.
 */
object KugouSessionStore {
    private const val PreferencesName = "melox_kugou_session"
    private const val PlaybackPreferencesName = "melox_kugou_playback_session"
    private const val LitePreferencesName = "melox_kugou_lite_session"
    private const val LitePlaybackPreferencesName = "melox_kugou_lite_playback_session"
    private const val Token = "token"
    private const val UserId = "userid"
    private const val VipToken = "vip_token"
    private const val VipType = "vip_type"
    private const val Dfid = "dfid"
    private const val Guid = "guid"
    private const val Mid = "mid"
    private const val Dev = "dev"
    private const val Mac = "mac"
    private const val WebGl = "webgl"
    private const val Auth = "auth"
    private const val VipExpiresAt = "vip_expires_at"
    private const val SuperVip = "super_vip"
    private const val VipActive = "vip_active"
    private const val FreeListen = "free_listen"

    fun read(context: Context, playback: Boolean = false): KugouSession {
        val preferences = preferences(context, playback)
        val identity = if (playback) read(context).let { DeviceIdentity(it.guid, it.mid, it.dev, it.mac, it.webGl) }
        else ensureIdentity(context)
        return KugouSession(
            token = preferences.getString(Token, "").orEmpty(),
            userId = preferences.getLong(UserId, 0L),
            vipToken = preferences.getString(VipToken, "").orEmpty(),
            vipType = preferences.getInt(VipType, 0),
            dfid = preferences.getString(Dfid, "-").orEmpty().ifBlank { "-" },
            mid = identity.mid,
            guid = identity.guid,
            dev = identity.dev,
            mac = identity.mac,
            webGl = identity.webGl,
            platform = KugouPlatform.Standard,
        )
    }

    /**
     * Reads the 酷狗概念版 account. The token, vip token and auth cache live in a
     * preference file that is never shared with standard KuGou; otherwise the
     * two clients would overwrite each other's incompatible login token.
     */
    fun readLite(context: Context, playback: Boolean = false): KugouSession {
        val preferences = preferences(context, playback, lite = true)
        val identity = if (playback) {
            readLite(context).let { DeviceIdentity(it.guid, it.mid, it.dev, it.mac, it.webGl) }
        } else {
            ensureIdentity(context, lite = true)
        }
        return KugouSession(
            token = preferences.getString(Token, "").orEmpty(),
            userId = preferences.getLong(UserId, 0L),
            vipToken = preferences.getString(VipToken, "").orEmpty(),
            vipType = preferences.getInt(VipType, 0),
            dfid = preferences.getString(Dfid, "-").orEmpty().ifBlank { "-" },
            mid = identity.mid,
            guid = identity.guid,
            dev = identity.dev,
            mac = identity.mac,
            webGl = identity.webGl,
            platform = KugouPlatform.Lite,
            auth = preferences.getString(Auth, "").orEmpty(),
            vipExpiresAtEpochSeconds = preferences.getLong(VipExpiresAt, 0L),
            superVip = preferences.getBoolean(SuperVip, false),
            vipActive = preferences.getBoolean(VipActive, false),
            freeListenEligible = preferences.getBoolean(FreeListen, false),
        )
    }

    fun updateLogin(
        context: Context,
        token: String,
        userId: Long,
        vipToken: String = "",
        vipType: Int = 0,
        dfid: String? = null,
        playback: Boolean = false,
    ): KugouSession {
        context.applicationContext
            .let { preferences(context, playback) }
            .edit()
            .putString(Token, token)
            .putLong(UserId, userId)
            .putString(VipToken, vipToken)
            .putInt(VipType, vipType)
            .apply {
                dfid?.takeIf(String::isNotBlank)?.let { putString(Dfid, it) }
            }
            .apply()
        return read(context, playback)
    }

    fun updateLiteLogin(
        context: Context,
        token: String,
        userId: Long,
        vipToken: String = "",
        vipType: Int = 0,
        dfid: String? = null,
    ): KugouSession {
        context.applicationContext
            .let { preferences(context, lite = true) }
            .edit()
            .putString(Token, token)
            .putLong(UserId, userId)
            .putString(VipToken, vipToken)
            .putInt(VipType, vipType)
            .apply {
                dfid?.takeIf(String::isNotBlank)?.let { putString(Dfid, it) }
            }
            .apply()
        return readLite(context)
    }

    fun updateLiteEntitlements(
        context: Context,
        auth: String? = null,
        vipType: Int? = null,
        vipExpiresAtEpochSeconds: Long? = null,
        superVip: Boolean? = null,
        vipActive: Boolean? = null,
        freeListenEligible: Boolean? = null,
        vipToken: String? = null,
    ) {
        val editor = context.applicationContext
            .let { preferences(context, lite = true) }
            .edit()
        auth?.let { editor.putString(Auth, it) }
        vipType?.let { editor.putInt(VipType, it) }
        vipExpiresAtEpochSeconds?.let { editor.putLong(VipExpiresAt, it) }
        superVip?.let { editor.putBoolean(SuperVip, it) }
        vipActive?.let { editor.putBoolean(VipActive, it) }
        freeListenEligible?.let { editor.putBoolean(FreeListen, it) }
        vipToken?.let { editor.putString(VipToken, it) }
        editor.apply()
    }

    fun clearLiteLogin(context: Context, playback: Boolean = false) {
        context.applicationContext
            .let { preferences(context, playback, lite = true) }
            .edit()
            .remove(Token)
            .remove(UserId)
            .remove(VipToken)
            .remove(VipType)
            .remove(Auth)
            .remove(VipExpiresAt)
            .remove(SuperVip)
            .remove(VipActive)
            .remove(FreeListen)
            .apply()
    }

    fun updateLiteDfid(context: Context, value: String) {
        if (value.isBlank()) return
        context.applicationContext
            .let { preferences(context, lite = true) }
            .edit()
            .putString(Dfid, value)
            .apply()
    }

    fun updateDfid(context: Context, value: String) {
        if (value.isBlank()) return
        context.applicationContext
            .let { preferences(context) }
            .edit()
            .putString(Dfid, value)
            .apply()
    }

    fun clearLogin(context: Context, playback: Boolean = false) {
        context.applicationContext
            .let { preferences(context, playback) }
            .edit()
            .remove(Token)
            .remove(UserId)
            .remove(VipToken)
            .remove(VipType)
            .apply()
    }

    private fun ensureIdentity(
        context: Context,
        lite: Boolean = false,
        playback: Boolean = false,
    ): DeviceIdentity {
        val preferences = context.applicationContext
            .let { preferences(context, lite = lite) }
        val existingGuid = preferences.getString(Guid, null)
        val guid = existingGuid?.takeIf(String::isNotBlank)
            ?: if (lite) UUID.randomUUID().toString() else md5Hex(UUID.randomUUID().toString())
        val mid = preferences.getString(Mid, null)?.takeIf(String::isNotBlank)
            ?: calculateMid(guid)
        val dev = preferences.getString(Dev, null)?.takeIf(String::isNotBlank)
            ?: randomUppercase(10)
        val mac = preferences.getString(Mac, null)?.takeIf(String::isNotBlank)
            ?: "02:00:00:00:00:00"
        val webGl = preferences.getString(WebGl, null)?.takeIf(String::isNotBlank)
            ?: BigInteger(64, SecureRandom()).toString()
        preferences.edit()
            .putString(Guid, guid)
            .putString(Mid, mid)
            .putString(Dev, dev)
            .putString(Mac, mac)
            .putString(WebGl, webGl)
            .apply()
        return DeviceIdentity(guid, mid, dev, mac, webGl)
    }

    private fun calculateMid(guid: String): String =
        BigInteger(md5Hex(guid), 16).toString(10)

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun randomUppercase(length: Int): String {
        val alphabet = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private data class DeviceIdentity(
        val guid: String,
        val mid: String,
        val dev: String,
        val mac: String,
        val webGl: String,
    )

    private fun preferences(
        context: Context,
        playback: Boolean = false,
        lite: Boolean = false,
    ) = com.lladlam.melox.core.account.SecureSessionPreferences.open(
        context,
        when {
            lite && playback -> LitePlaybackPreferencesName
            lite -> LitePreferencesName
            playback -> PlaybackPreferencesName
            else -> PreferencesName
        },
    )
}
