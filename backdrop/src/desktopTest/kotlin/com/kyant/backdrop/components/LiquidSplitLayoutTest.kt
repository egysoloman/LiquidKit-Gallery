package com.kyant.backdrop.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class LiquidSplitLayoutTest {
    @Test fun delayedPointerUpKeepsAQuickForwardSwipeButAllowsHoldingOrReversing() {
        val motion = GlassNavigationTokens()
        fun commits(endDelta: Float, elapsed: Long, speed: Float) = motion.commitsBackSwipe(
            fraction = .295f, distance = 340f, endDelta = endDelta, durationMillis = elapsed,
            releaseSpeed = speed, flingSpeed = 1890f, slop = 21f,
        )
        // Three releases of the same 340 px swipe, captured at 2.625 px/dp.
        assertTrue(commits(166f, 134, 1824f))
        assertTrue(commits(102f, 116, 1789f))
        assertTrue(commits(12f, 163, 148f))
        assertFalse(commits(0f, 163, 0f))
        assertFalse(commits(12f, 660, 148f))
        assertFalse(commits(-20f, 130, -2300f))
    }

    @Test fun aSidebarPreferenceDoesNotReplaceAnActiveCompactPage() {
        val state = LiquidNavigationSplitState(listOf(LiquidSplitColumn.Sidebar, LiquidSplitColumn.Content, LiquidSplitColumn.Detail))
        for (preference in LiquidSplitVisibility.entries) {
            val compact = state.layoutFor(599.dp, preference)
            assertFalse(compact.supportsSidebar)
            assertEquals(listOf(LiquidSplitColumn.Detail), compact.columns)
        }
        assertTrue(state.back())
        assertEquals(listOf(LiquidSplitColumn.Content), state.layoutFor(411.dp).columns)
        assertEquals(listOf(LiquidSplitColumn.Content), state.layoutFor(411.dp, LiquidSplitVisibility.DetailOnly).columns)
        assertEquals(listOf(LiquidSplitColumn.Detail), state.layoutFor(800.dp, LiquidSplitVisibility.DetailOnly).columns)
        assertTrue(state.back())
        assertEquals(listOf(LiquidSplitColumn.Sidebar), state.layoutFor(411.dp).columns)
    }

    @Test fun revealingTheDirectoryKeepsTheSelectedDetailAndHistory() {
        val (sidebar, content, detail) = LiquidSplitColumn.entries
        val state = LiquidNavigationSplitState(listOf(sidebar, content, detail))
        for (width in listOf(600, 800, 999, 1000, 1120)) {
            val visible = state.layoutFor(width.dp, LiquidSplitVisibility.ShowSidebar)
            assertTrue(visible.supportsSidebar)
            assertEquals(if (width < 1000) listOf(sidebar, detail) else listOf(sidebar, content, detail), visible.columns)
            assertEquals(listOf(content, detail), state.layoutFor(width.dp, LiquidSplitVisibility.HideSidebar).columns)
            assertEquals(detail, visible.focusedColumn)
            assertEquals(listOf(content, sidebar), state.ancestors)
        }
    }
}
