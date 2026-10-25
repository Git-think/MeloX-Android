package com.lladlam.melox.core.provider.qishui

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

data class QishuiSession(
    val sessionId: String = "",
    /** Full passport cookie header (sessionid + sid_tt/uid_tt/...), when available. */
    val cookie: String = "",
    val userId: String = "",
    val nickname: String = "",
    val deviceId: String = "",
    val installId: String = "",
) {
    val isLoggedIn: Boolean get() = sessionId.isNotBlank() || cookie.isNotBlank()
}

/** Qishui credentials never share another provider's cookie jar or preferences. */
object QishuiSessionStore {
    private fun preferences(context: Context) = EncryptedSharedPreferences.create(
        context.applicationContext, "melox_qishui_session",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun read(context: Context): QishuiSession {
        val prefs = preferences(context)
        fun identity(key: String): String = prefs.getString(key, null)?.takeIf(String::isNotBlank) ?: run {
            val random = SecureRandom()
            buildString { repeat(16) { append(random.nextInt(10)) } }.also { prefs.edit().putString(key, it).apply() }
        }
        return QishuiSession(
            sessionId = prefs.getString("sessionid", "").orEmpty(),
            cookie = prefs.getString("cookie", "").orEmpty(),
            userId = prefs.getString("user_id", "").orEmpty(),
            nickname = prefs.getString("nickname", "").orEmpty(),
            deviceId = identity("device_id"),
            installId = identity("install_id"),
        )
    }

    fun save(
        context: Context,
        sessionId: String,
        cookie: String,
        userId: String,
        nickname: String,
    ) {
        require(sessionId.isNotBlank() && sessionId.none { it.isWhitespace() || it == ';' })
        val safeCookie = cookie.takeIf { it.isNotBlank() && cookie.none { ch -> ch == '\n' || ch == '\r' } }.orEmpty()
        preferences(context).edit()
            .putString("sessionid", sessionId)
            .putString("cookie", safeCookie)
            .putString("user_id", userId)
            .putString("nickname", nickname)
            .apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().remove("sessionid").remove("cookie").remove("user_id").remove("nickname").apply()
    }
}
