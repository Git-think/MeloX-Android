package com.lladlam.melox.core.provider.lxuser

import android.content.Context

object ChkszConfigStore {
    const val DefaultBaseUrl = "https://api.chksz.com"
    private const val PreferencesName = "melox_chksz_api"
    private const val KeyEnabled = "enabled"
    private const val KeyApiKey = "apikey"
    private const val KeyBaseUrl = "baseurl"

    /**
     * CHKSZ 默认隐藏：只有用户显式打开开关后，设置项才会显示，
     * 解析器也才会参与播放链路。
     */
    /**
     * 是否启用。标志位存在时以它为准；不存在时（旧版本只存过 API Key 的数据）
     * 以“是否已配置 Key”为准，避免升级后已配置的用户被静默停用。
     * 全新安装既没有标志位也没有 Key，仍然保持默认隐藏。
     */
    fun readEnabled(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val stored = if (prefs.contains(KeyEnabled)) prefs.getBoolean(KeyEnabled, false) else null
        return chkszEnabledState(stored, prefs.getString(KeyApiKey, "").orEmpty().isNotBlank())
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putBoolean(KeyEnabled, enabled).apply()
    }

    fun readApiKey(context: Context): String = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getString(KeyApiKey, "").orEmpty()

    fun writeApiKey(context: Context, value: String) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putString(KeyApiKey, value.trim()).apply()
    }

    fun readBaseUrl(context: Context): String = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getString(KeyBaseUrl, "").orEmpty()
        .trim()
        .ifEmpty { DefaultBaseUrl }

    fun writeBaseUrl(context: Context, value: String) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putString(KeyBaseUrl, value.trim()).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().remove(KeyApiKey).remove(KeyBaseUrl).apply()
    }
}

/**
 * 「enabled」标志位存在时以它为准；不存在时（旧版本只存过 API Key 的数据）
 * 回退为“是否已配置 Key”，保持升级用户继续可用。
 */
internal fun chkszEnabledState(storedEnabled: Boolean?, hasApiKey: Boolean): Boolean =
    storedEnabled ?: hasApiKey
