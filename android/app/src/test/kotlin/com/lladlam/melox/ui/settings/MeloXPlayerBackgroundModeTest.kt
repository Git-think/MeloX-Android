package com.lladlam.melox.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXPlayerBackgroundModeTest {
    @Test
    fun `flowing backdrop is only enabled for procedural backgrounds`() {
        assertTrue(MeloXPlayerBackgroundMode.FlowingLight.usesFlowingBackdrop)
        assertTrue(MeloXPlayerBackgroundMode.AppleLyrics.usesFlowingBackdrop)
        assertFalse(MeloXPlayerBackgroundMode.BlurredArtwork.usesFlowingBackdrop)
        assertFalse(MeloXPlayerBackgroundMode.CustomImage.usesFlowingBackdrop)
    }
}
