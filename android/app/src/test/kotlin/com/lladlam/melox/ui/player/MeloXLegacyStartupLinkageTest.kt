package com.lladlam.melox.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class MeloXLegacyStartupLinkageTest {
    @Test
    fun alwaysComposedOutputEntryDoesNotLinkToAndroid11RoutingTypes() {
        // Inspect compiled bytecode without loading the Compose implementation.
        // A nullable MediaRouter2 inside remember still emits a platform class
        // cast, even when the initializer returns null on API 29. Keep those
        // types (and callback superclasses) in the API-gated implementation.
        val name = "com/lladlam/melox/ui/player/MeloXAudioOutputDialogKt.class"
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
        assertNotNull("Missing compiled startup entry", stream)
        val bytecode = stream!!.use { it.readBytes().toString(Charsets.ISO_8859_1) }
        listOf("android/media/MediaRouter2", "android/media/MediaRoute2Info", "android/media/RouteDiscoveryPreference").forEach { type ->
            assertFalse("Legacy cold-start entry must not resolve $type", bytecode.contains(type))
        }
    }
}
