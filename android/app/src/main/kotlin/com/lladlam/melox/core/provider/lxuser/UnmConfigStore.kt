package com.lladlam.melox.core.provider.lxuser

import android.content.Context

/**
 * UNM（UnblockNeteaseMusic 兼容解灰服务）配置。
 * 服务地址为空表示未启用；全部内容只保存在本机，不属于 MeloX 云控。
 */
object UnmConfigStore {
    /** /match 请求默认尝试的音源顺序。 */
    const val DefaultServers = "kuwo,kugou,bilibili"

    private const val PreferencesName = "melox_unm_api"
    private const val KeyBaseUrl = "baseurl"
    private const val KeyServers = "servers"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun readBaseUrl(context: Context): String = prefs(context)
        .getString(KeyBaseUrl, "").orEmpty()
        .trim()

    fun writeBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KeyBaseUrl, value.trim()).apply()
    }

    fun readServers(context: Context): String = prefs(context)
        .getString(KeyServers, "").orEmpty()
        .trim()
        .ifBlank { DefaultServers }

    fun writeServers(context: Context, value: String) {
        prefs(context).edit().putString(KeyServers, value.trim()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KeyBaseUrl).remove(KeyServers).apply()
    }
}
