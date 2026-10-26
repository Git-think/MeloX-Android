package com.lladlam.melox.core.account

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPreferenceMigrationTest {
    @Test
    fun successfulMigrationPreservesNewerCredentialsAndAllPreferenceTypes() {
        val legacy = InMemorySharedPreferences().apply {
            edit().putString("cookie", "old").putBoolean("enabled", true)
                .putInt("count", 7).putLong("expiry", 900L).putFloat("ratio", .5f)
                .putStringSet("scopes", mutableSetOf("read", "write")).commit()
        }
        val encrypted = InMemorySharedPreferences().apply { edit().putString("cookie", "new").commit() }

        assertTrue(migrateSessionPreferences(legacy, encrypted, durableDestination = true))
        assertEquals("new", encrypted.getString("cookie", null))
        assertTrue(encrypted.getBoolean("enabled", false))
        assertEquals(7, encrypted.getInt("count", 0))
        assertEquals(900L, encrypted.getLong("expiry", 0L))
        assertEquals(.5f, encrypted.getFloat("ratio", 0f), 0f)
        assertEquals(setOf("read", "write"), encrypted.getStringSet("scopes", null))
        assertTrue(legacy.all.isEmpty())
    }

    @Test
    fun inMemoryFallbackNeverDeletesPersistentCredentials() {
        val legacy = legacySession()
        val fallback = InMemorySharedPreferences()

        repeat(2) {
            assertFalse(migrateSessionPreferences(legacy, fallback, durableDestination = false))
        }
        assertEquals("old", legacy.getString("cookie", null))
        assertEquals("old", fallback.getString("cookie", null))
    }

    @Test
    fun failedDiskWriteCannotClearLegacyEvenWhenMarkerIsVisibleInMemory() {
        val legacy = legacySession()
        val backing = InMemorySharedPreferences()
        val unavailableDisk = failedCommits(backing, applyInMemory = true)

        repeat(2) {
            assertFalse(migrateSessionPreferences(legacy, unavailableDisk, durableDestination = true))
            assertEquals("old", legacy.getString("cookie", null))
        }
        // Android can expose a changed marker despite commit() returning false.
        assertTrue(backing.getBoolean("_migration_complete", false))
        assertTrue(migrateSessionPreferences(legacy, backing, durableDestination = true))
        assertTrue(legacy.all.isEmpty())
    }

    @Test
    fun cleanupFailureCanRetryWithoutResurrectingSignedOutAccount() {
        val legacy = legacySession()
        val encrypted = InMemorySharedPreferences()
        assertFalse(migrateSessionPreferences(failedCommits(legacy), encrypted, durableDestination = true))
        assertEquals("old", legacy.getString("cookie", null))

        // The user logs out after the durable copy but before cleanup succeeds.
        encrypted.edit().remove("cookie").commit()
        assertTrue(migrateSessionPreferences(legacy, encrypted, durableDestination = true))
        assertFalse(encrypted.contains("cookie"))
        assertTrue(legacy.all.isEmpty())
    }

    private fun legacySession() = InMemorySharedPreferences().apply {
        edit().putString("cookie", "old").commit()
    }

    private fun failedCommits(
        backing: SharedPreferences,
        applyInMemory: Boolean = false,
    ): SharedPreferences = object : SharedPreferences by backing {
        override fun edit(): SharedPreferences.Editor {
            val editor = backing.edit()
            // Keep fluent calls on the wrapper rather than returning the backing editor.
            return object : SharedPreferences.Editor by editor {
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    editor.putBoolean(key, value)
                    return this
                }

                override fun clear(): SharedPreferences.Editor {
                    editor.clear()
                    return this
                }

                override fun commit(): Boolean {
                    if (applyInMemory) editor.commit()
                    return false
                }
            }
        }
    }
}
