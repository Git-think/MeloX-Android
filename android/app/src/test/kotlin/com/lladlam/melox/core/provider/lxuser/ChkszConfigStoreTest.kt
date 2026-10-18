package com.lladlam.melox.core.provider.lxuser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChkszConfigStoreTest {

    @Test
    fun keyConfiguredBeforeTheToggleExistsCountsAsEnabled() {
        // 旧版本只存过 API Key，没有 enabled 标志位：升级后不能被静默停用。
        assertTrue(chkszEnabledState(storedEnabled = null, hasApiKey = true))
    }

    @Test
    fun freshInstallWithoutKeyStaysHidden() {
        assertFalse(chkszEnabledState(storedEnabled = null, hasApiKey = false))
    }

    @Test
    fun explicitToggleOffWinsOverAnExistingKey() {
        assertFalse(chkszEnabledState(storedEnabled = false, hasApiKey = true))
    }

    @Test
    fun explicitToggleOnStaysOn() {
        assertTrue(chkszEnabledState(storedEnabled = true, hasApiKey = true))
        assertTrue(chkszEnabledState(storedEnabled = true, hasApiKey = false))
    }
}
