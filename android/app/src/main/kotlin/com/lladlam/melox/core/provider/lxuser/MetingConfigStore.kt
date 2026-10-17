package com.lladlam.melox.core.provider.lxuser

import android.content.Context

/**
 * Meting API（兼容 metowolf/Meting）解析服务配置。
 * 服务器地址为空表示未启用；全部内容只保存在本机，不属于 MeloX 云控。
 */
object MetingConfigStore {
    private const val PreferencesName = "melox_meting_api"
    private const val KeyBaseUrl = "baseurl"
    private const val KeyAuth = "auth"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun readBaseUrl(context: Context): String = prefs(context)
        .getString(KeyBaseUrl, "").orEmpty()
        .trim()

    fun writeBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KeyBaseUrl, value.trim()).apply()
    }

    fun readAuth(context: Context): String = prefs(context)
        .getString(KeyAuth, "").orEmpty()
        .trim()

    fun writeAuth(context: Context, value: String) {
        prefs(context).edit().putString(KeyAuth, value.trim()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KeyBaseUrl).remove(KeyAuth).apply()
    }
}
