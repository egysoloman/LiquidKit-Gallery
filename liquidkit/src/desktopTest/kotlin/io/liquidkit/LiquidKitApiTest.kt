package io.liquidkit

import io.liquidkit.skip.*
import org.junit.Assert.*
import org.junit.Test

class LiquidKitApiTest {
    @Test
    fun skipBridgeMapsActionStylesAndVariants() {
        assertEquals(LiquidKitActionStyle.Plain, skipLiquidKitActionStyle("plain"))
        assertEquals(LiquidKitActionStyle.Borderless, skipLiquidKitActionStyle("borderless"))
        assertEquals(LiquidKitActionStyle.Tinted, skipLiquidKitActionStyle("bordered"))
        assertEquals(LiquidKitActionStyle.Filled, skipLiquidKitActionStyle("borderedProminent"))
        assertEquals(LiquidKitActionStyle.Glass, skipLiquidKitActionStyle("glass"))
        assertEquals(LiquidKitActionStyle.GlassProminent, skipLiquidKitActionStyle("glassProminent"))

        assertEquals(LiquidKitVariant.Regular, skipLiquidKitVariant("regular"))
        assertEquals(LiquidKitVariant.Clear, skipLiquidKitVariant("clear"))
        assertEquals(LiquidKitVariant.Identity, skipLiquidKitVariant("identity"))

        assertEquals(LiquidKitControlSize.Mini, skipLiquidKitControlSize("mini"))
        assertEquals(LiquidKitControlSize.ExtraLarge, skipLiquidKitControlSize("xl"))
        assertEquals(LiquidKitButtonShape.Circle, skipLiquidKitButtonShape("circle"))
        assertEquals(LiquidKitButtonShape.RoundedRectangle, skipLiquidKitButtonShape("roundedRectangle"))
    }

    @Test
    fun skipBridgeMapsRolesTitleModesAndTransitions() {
        assertEquals(LiquidKitAlertRole.Primary, skipLiquidKitAlertRole("primary"))
        assertEquals(LiquidKitAlertRole.Cancel, skipLiquidKitAlertRole("cancel"))
        assertEquals(LiquidKitAlertRole.Destructive, skipLiquidKitAlertRole("destructive"))
        assertEquals(LiquidKitAlertRole.Default, skipLiquidKitAlertRole("unknown"))

        assertEquals(LiquidKitNavigationTitleMode.Large, skipLiquidKitTitleMode("large"))
        assertEquals(LiquidKitNavigationTitleMode.Inline, skipLiquidKitTitleMode("inline"))
        assertEquals(LiquidKitNavigationTitleMode.Automatic, skipLiquidKitTitleMode("automatic"))

        assertEquals(LiquidKitViewTransitionStyle.CrossFade, skipLiquidKitTransitionStyle("crossFade"))
        assertEquals(LiquidKitViewTransitionStyle.Zoom, skipLiquidKitTransitionStyle("zoom"))
        assertEquals(LiquidKitViewTransitionStyle.Slide, skipLiquidKitTransitionStyle("slide"))
    }

    @Test
    fun skipBridgeResolvesSFSymbolsAndClampsColors() {
        assertNotNull(skipLiquidKitSymbol("folder.badge.plus"))
        assertNotNull(skipLiquidKitSymbol("magnifyingglass"))
        assertNull(skipLiquidKitSymbol(null))
        assertNull(skipLiquidKitSymbol(""))

        val c = skipLiquidKitColor(1.4, -0.2, 0.5, 0.75)
        assertEquals(1f, c.red, 0.005f)
        assertEquals(0f, c.green, 0.005f)
        assertEquals(0.5f, c.blue, 0.005f)
        assertEquals(0.75f, c.alpha, 0.005f)
    }

    @Test
    fun liquidKitSpringMatchesSwiftUIPhysics() {
        val spring = LiquidKitSpring.swiftUI(response = 0.32f, dampingFraction = 0.86f)
        assertEquals(0.32f, spring.response, 0.002f)
        assertEquals(0.86f, spring.dampingFraction, 0.0001f)
    }

    @Test
    fun skipBridgeParsesAndFormatsCalendarDatesAndNewSymbols() {
        val parsed = skipParseCalendarDate("2026-11-25")
        assertNotNull(parsed)
        assertEquals(2026, parsed!!.year)
        assertEquals(11, parsed.month)
        assertEquals(25, parsed.day)
        assertEquals("2026-11-25", skipFormatCalendarDate(parsed))
        assertEquals("2026-01-05", skipFormatCalendarDate(LiquidKitCalendarDate(2026, 1, 5)))
        assertNull(skipParseCalendarDate("bad-date"))

        assertNotNull(skipLiquidKitSymbol("sparkles"))
        assertNotNull(skipLiquidKitSymbol("chart.bar.xaxis"))
        assertNotNull(skipLiquidKitSymbol("curlybraces"))
    }
}
