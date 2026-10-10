package com.lladlam.melox.core.provider.lxuser

import android.content.Context

object ChkszConfigStore {
    const val DefaultBaseUrl = "https://api.chksz.com"
    private const val PreferencesName = "melox_chksz_api"
    private const val KeyApiKey = "apikey"
    private const val KeyBaseUrl = "baseurl"

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
