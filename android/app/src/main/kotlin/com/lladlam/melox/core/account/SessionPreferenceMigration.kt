package com.lladlam.melox.core.account

import android.content.SharedPreferences

/** Returns true only after the durable copy and legacy cleanup both succeed. */
internal fun migrateSessionPreferences(
    legacy: SharedPreferences,
    encrypted: SharedPreferences,
    durableDestination: Boolean,
): Boolean {
    val legacyValues = legacy.all
    // The committed marker prevents stale plaintext from restoring an old login
    // after the encrypted credentials have changed or the user has signed out.
    if (!encrypted.getBoolean("_migration_complete", false)) {
        val editor = encrypted.edit()
        legacyValues.forEach { (key, value) ->
            if (!encrypted.contains(key)) when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    require(value.all { it is String })
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                null -> Unit
                else -> error("Unsupported session preference type")
            }
        }
        if (!editor.putBoolean("_migration_complete", true).commit()) return false
    } else if (legacyValues.isNotEmpty() && !encrypted.edit().commit()) {
        // commit(false) can still update SharedPreferences' in-process values.
        // Reconfirm persistence before treating an in-memory marker as durable.
        return false
    }
    // A successful in-memory commit is not a durable copy. Preserve the source
    // across process death if the keystore could not be opened on this device.
    if (!durableDestination) return false
    if (legacyValues.isEmpty()) return true
    return legacy.edit().clear().commit()
}
