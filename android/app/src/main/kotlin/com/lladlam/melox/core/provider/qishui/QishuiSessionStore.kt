package com.lladlam.melox.core.provider.qishui

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

data class QishuiSession(
    val sessionId: String = "",
    val userId: String = "",
    val nickname: String = "",
    val deviceId: String = "",
    val installId: String = "",
) {
    val isLoggedIn: Boolean get() = sessionId.isNotBlank()
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
            prefs.getString("sessionid", "").orEmpty(), prefs.getString("user_id", "").orEmpty(),
            prefs.getString("nickname", "").orEmpty(), identity("device_id"), identity("install_id"),
        )
    }

    fun save(context: Context, sessionId: String, userId: String, nickname: String) {
        require(sessionId.isNotBlank() && sessionId.none { it.isWhitespace() || it == ';' })
        preferences(context).edit().putString("sessionid", sessionId).putString("user_id", userId)
            .putString("nickname", nickname).apply()
    }

    fun clear(context: Context) {
        preferences(context).edit().remove("sessionid").remove("user_id").remove("nickname").apply()
    }
}
