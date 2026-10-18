package com.lladlam.melox.ui.layout

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lladlam.melox.ui.settings.MeloXSplitChromePosition
import com.lladlam.melox.ui.settings.meloXChromeScrollCoupled
import org.junit.Assert.*
import org.junit.Test

class MeloXSplitChromeLayoutTest {
    private val viewport = IntSize(800, 320)

    @Test
    fun switchOverridesLandscapeDetection() {
        assertTrue(meloXUsesListDetail(IntSize(800, 360)))
        assertFalse(meloXUsesListDetail(IntSize(800, 360), enabled = false))
        assertFalse(meloXUsesListDetail(IntSize(360, 800)))
    }

    @Test
    fun leftAndRightMeanVerticalRailsButCornersRemainHorizontal() {
        assertTrue(MeloXSplitChromePosition.Left.isSideRail)
        assertTrue(MeloXSplitChromePosition.Right.isSideRail)
        assertFalse(MeloXSplitChromePosition.BottomLeft.isSideRail)
        assertFalse(MeloXSplitChromePosition.TopRight.isSideRail)
    }

    @Test
    fun sharedCornerStacksMiniAboveOrBelowWithoutCrossingDivider() {
        val nav = IntSize(384, 72)
        val mini = IntSize(384, 68)
        val bottom = meloXChromeOffsets(viewport, nav, mini,
            MeloXSplitChromePosition.BottomLeft, MeloXSplitChromePosition.BottomLeft, 8)
        assertEquals(IntOffset(8, 240), bottom.navigation)
        assertEquals(IntOffset(8, 164), bottom.miniPlayer)
        val top = meloXChromeOffsets(viewport, nav, mini,
            MeloXSplitChromePosition.TopRight, MeloXSplitChromePosition.TopRight, 8)
        assertEquals(IntOffset(408, 8), top.navigation)
        assertEquals(IntOffset(408, 88), top.miniPlayer)
    }

    @Test
    fun twoSideRailsUseAdjacentColumnsInsideTheirOwnPane() {
        val result = meloXChromeOffsets(viewport, IntSize(72, 304), IntSize(72, 236),
            MeloXSplitChromePosition.Right, MeloXSplitChromePosition.Right, 8)
        assertEquals(IntOffset(720, 8), result.navigation)
        assertEquals(IntOffset(647, 42), result.miniPlayer)
    }

    @Test
    fun separatePlacementsNeverEnableScrollCollapse() {
        for (nav in MeloXSplitChromePosition.entries) {
            for (mini in MeloXSplitChromePosition.entries) {
                assertEquals(nav == mini, meloXChromeScrollCoupled(true, nav, mini))
                assertTrue(meloXChromeScrollCoupled(false, nav, mini))
            }
        }
    }

    @Test
    fun compactSideMiniJoinsTheMiddleSlotOfItsOwnDock() {
        val result = meloXChromeOffsets(viewport, IntSize(72, 200), IntSize(72, 64),
            MeloXSplitChromePosition.Right, MeloXSplitChromePosition.Right, 8, coupledProgress = 1f)
        assertEquals(IntOffset(720, 60), result.navigation)
        assertEquals(IntOffset(720, 128), result.miniPlayer)
    }

    @Test
    fun everyCombinationFitsItsOwnPaneWithOddWindowSizesAndAsymmetricInsets() {
        for (size in listOf(viewport, IntSize(793, 360), IntSize(1201, 701))) {
            for (navPosition in MeloXSplitChromePosition.entries) {
                for (miniPosition in MeloXSplitChromePosition.entries) {
                    val navArea = meloXChromeArea(size, navPosition, miniPosition, false, 8, 72, 44, 12, 24)
                    val miniArea = meloXChromeArea(size, miniPosition, navPosition, true, 8, 72, 44, 12, 24)
                    val nav = IntSize(if (navPosition.isSideRail) 72 else navArea.width,
                        if (navPosition.isSideRail) navArea.height else 72)
                    val mini = IntSize(if (miniPosition.isSideRail) 72 else miniArea.width,
                        if (miniPosition.isSideRail) 236 else 68)
                    val result = meloXChromeOffsets(size, nav, mini, navPosition, miniPosition, 8, 72, 44, 12, 24)
                    val label = "$size: $navPosition / $miniPosition"
                    for ((offset, control, position) in listOf(
                        Triple(result.navigation, nav, navPosition), Triple(result.miniPlayer, mini, miniPosition),
                    )) {
                        assertTrue(label, offset.y >= 24 && offset.y + control.height <= size.height - 8)
                        if (position.right) {
                            assertTrue(label, offset.x >= size.width / 2 + 8)
                            assertTrue(label, offset.x + control.width <= size.width - 12)
                        } else {
                            assertTrue(label, offset.x >= 44)
                            assertTrue(label, offset.x + control.width <= size.width / 2 - 8)
                        }
                    }
                    assertTrue(label,
                        result.navigation.x + nav.width <= result.miniPlayer.x ||
                        result.miniPlayer.x + mini.width <= result.navigation.x ||
                        result.navigation.y + nav.height <= result.miniPlayer.y ||
                        result.miniPlayer.y + mini.height <= result.navigation.y)
                }
            }
        }
    }

    @Test
    fun absentMiniDoesNotReserveASecondColumn() {
        val area = meloXChromeArea(viewport, MeloXSplitChromePosition.Right, null, false, 8, 72)
        assertEquals(392, area.right - 400)
        val result = meloXChromeOffsets(viewport, IntSize(72, 304), IntSize.Zero,
            MeloXSplitChromePosition.Right, MeloXSplitChromePosition.Right, 8)
        assertEquals(IntOffset(720, 8), result.navigation)
        assertEquals(MeloXSplitChromePosition.BottomLeft, MeloXSplitChromePosition.fromPreference("unknown"))
    }
}
