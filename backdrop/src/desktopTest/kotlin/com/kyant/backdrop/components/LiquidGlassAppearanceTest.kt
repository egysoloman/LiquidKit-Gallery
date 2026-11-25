package com.kyant.backdrop.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassAppearanceTest {
    @Test fun tabBackingIsContinuousAndKeepsTheArtworkAtMaximumDensity() {
        val tab = GlassTabTokens()
        for (dark in listOf(false, true)) {
            val samples = (0..100).map { tab.backingFraction(LiquidGlassAppearance(dark, it / 100f)) }
            assertTrue(samples.zipWithNext().all { (a, b) -> b - a in 0f..0.016f })
            assertEquals(0f, samples.first(), 0f)
            assertEquals(if (dark) 0f else tab.lightBackingAtMidpoint, samples[50], 0.0001f)
            assertEquals(if (dark) tab.darkBackingAtMaximum else tab.lightBackingAtMaximum, samples.last(), 0.0001f)
            assertTrue(samples[63] > samples[62] && samples.last() < 1f)
        }
    }

    @Test fun clearControlsAndOpenMenusHaveDifferentBackdropDiffusion() {
        for (dark in listOf(true, false)) {
            val samples = (0..100).map { LiquidGlassAppearance(dark, it / 100f) }
            assertTrue(samples.first().controlBlurRadius < 1.dp)
            assertTrue(samples.first().menuBlurRadius >= 7.dp)
            assertTrue(samples.zipWithNext().all { (a, b) ->
                a.menuBlurRadius < b.menuBlurRadius && a.controlBlurRadius <= b.controlBlurRadius
            })
            assertTrue(samples.all { it.menuBlurRadius > it.controlBlurRadius && it.menuOverlay(Color.Unspecified).alpha > 0f })
        }
    }
    @Test fun opacityTracksIntermediateValuesInBothPalettes() {
        for (dark in listOf(true, false)) {
            val levels = (0..100).map { LiquidGlassAppearance(dark, it / 100f) }
            assertTrue(levels.all { it.contentColor == if (dark) Color.White else Color.Black })
            assertTrue(levels.zipWithNext().all { (a, b) -> b.surface.alpha > a.surface.alpha && b.blurRadius > a.blurRadius })
            val left = levels[50].surface.alpha - levels[49].surface.alpha
            val right = levels[51].surface.alpha - levels[50].surface.alpha
            assertEquals(left, right, .005f)
        }
    }

    @Test fun mixedToolbarsRespectTheOpticalWidthAndRoleOfTheirSymbols() {
        fun width(vararg symbols: LiquidSymbol) = LiquidToolbarDefaults.width(symbols.toList()).value
        assertEquals(44f, width(LiquidSymbol.Sidebar), .01f)
        assertEquals(96.dp, LiquidToolbarDefaults.width(listOf(LiquidSymbol.Back, LiquidSymbol.Forward), LiquidToolbarSpacing.Navigation))
        assertEquals(97f, width(LiquidSymbol.Add, LiquidSymbol.Help), .6f)
        assertEquals(106f, width(LiquidSymbol.Link, LiquidSymbol.Filter), .6f)
        assertEquals(160f, width(LiquidSymbol.Bell, LiquidSymbol.Like, LiquidSymbol.DownloadCircle), 1f)
    }

    @Test fun darkToolbarSurfacePreservesLiftedBackingOverBlack() {
        val mid = LiquidGlassAppearance(dark = true, opacity = 0.5f).surface
        val premulRgb = mid.red * mid.alpha * 255f
        assertTrue("Expected dark mid surface over black near #202020 (28..38), got $premulRgb", premulRgb in 28f..38f)
    }

    @Test fun lightGlassSurfaceIsNeutralCoolWithoutBeigeCast() {
        val light = LiquidGlassAppearance(dark = false, opacity = 0.5f)
        assertTrue("Expected cool neutral white light glass surface, got red=${light.surface.red} blue=${light.surface.blue}",
            light.surface.blue >= light.surface.red - 0.01f)
        assertTrue("Expected open menu overlay brighter than resting surface",
            light.menuOverlay(Color.Unspecified).alpha > light.surface.alpha)
    }

    @Test fun customTintOnlyIgnoresDefaultSentinelsAndMenuDensityScalesMonotonically() {
        for (dark in listOf(false, true)) {
            val appearance = LiquidGlassAppearance(dark = dark, opacity = 0.5f)
            assertNull(appearance.customTintOnly(Color.Unspecified))
            assertNull(appearance.customTintOnly(Color.Transparent))
            assertNull(appearance.customTintOnly(LiquidButtonDefaults.SurfaceColor))
            val accent = Color(0xff007aff).copy(alpha = 0.4f)
            assertEquals(accent, appearance.customTintOnly(accent))

            val menu0 = LiquidGlassAppearance(dark = dark, opacity = 0f).menuOverlay(Color.Unspecified)
            val menu50 = LiquidGlassAppearance(dark = dark, opacity = 0.5f).menuOverlay(Color.Unspecified)
            val menu100 = LiquidGlassAppearance(dark = dark, opacity = 1f).menuOverlay(Color.Unspecified)
            assertTrue("Expected menu overlay alpha to rise monotonically: $menu0 -> $menu50 -> $menu100",
                menu0.alpha < menu50.alpha && menu50.alpha < menu100.alpha)
        }
    }
}
