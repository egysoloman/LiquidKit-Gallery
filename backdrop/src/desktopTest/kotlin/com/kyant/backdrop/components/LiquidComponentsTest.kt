package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.BackdropDrawEpoch
import com.kyant.backdrop.backdrops.drawEpoch
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.isLayerBacked
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.internal.ShapeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LiquidComponentsTest {
    @Test
    fun segmentedPickerMathMapsDragPositionAndRoundTripsIndices() {
        assertEquals(0f, segmentedThumbFraction(50f, 300f, 1), 0f)
        assertEquals(0, segmentedIndexForFraction(0.5f, 1))

        assertEquals(0f, segmentedThumbFraction(50f, 300f, 3), 1e-4f)
        assertEquals(0.5f, segmentedThumbFraction(150f, 300f, 3), 1e-4f)
        assertEquals(1f, segmentedThumbFraction(250f, 300f, 3), 1e-4f)
        assertEquals(0f, segmentedThumbFraction(-40f, 300f, 3), 0f)
        assertEquals(1f, segmentedThumbFraction(400f, 300f, 3), 0f)

        assertEquals(0, segmentedIndexForFraction(0.10f, 3))
        assertEquals(1, segmentedIndexForFraction(0.49f, 3))
        assertEquals(2, segmentedIndexForFraction(0.80f, 3))
        assertEquals(4, segmentedIndexForFraction(1.2f, 5))

        // Rubber-band index preserves exact segment centers and bounds overhang with tanh
        assertEquals(0f, segmentedRubberBandIndex(50f, 300f, 3), 1e-4f)
        assertEquals(1f, segmentedRubberBandIndex(150f, 300f, 3), 1e-4f)
        assertEquals(2f, segmentedRubberBandIndex(250f, 300f, 3), 1e-4f)
        val leftOverhang = segmentedRubberBandIndex(-60f, 300f, 3, overhangLimitSlots = 0.14f)
        assertTrue(leftOverhang < 0f && leftOverhang > -0.14f)
        val rightOverhang = segmentedRubberBandIndex(360f, 300f, 3, overhangLimitSlots = 0.14f)
        assertTrue(rightOverhang > 2f && rightOverhang < 2.14f)

        val (restSx, restSy) = segmentedLensScale(0f, 0f, .12f, .42f, .42f)
        assertEquals(1f, restSx, 1e-4f)
        assertEquals(1f, restSy, 1e-4f)
        val (heldSx, heldSy) = segmentedLensScale(1f, .40f, .12f, .42f, .42f)
        assertTrue(heldSx > 1.2f && heldSy > 1.25f)
    }

    @Test
    fun sheetDetentResolutionHandlesSnapAndDismissal() {
        val detents = listOf(LiquidSheetDetent.Medium, LiquidSheetDetent.Large)
        assertEquals(
            LiquidSheetDetent.Large,
            resolveSheetDetent(0.52f, -0.35f, 0f, 900f, detents, 0.28f),
        )
        assertEquals(
            LiquidSheetDetent.Medium,
            resolveSheetDetent(0.92f, 0.35f, 0f, 900f, detents, 0.28f),
        )
        assertNull(
            resolveSheetDetent(0.52f, 0.30f, 0f, 900f, detents, 0.28f),
        )
        assertNull(
            resolveSheetDetent(0.52f, 0.05f, 1200f, 900f, detents, 0.28f),
        )
        assertEquals(
            LiquidSheetDetent.Medium,
            resolveSheetDetent(0.92f, 0.05f, 1200f, 900f, detents, 0.28f),
        )
        assertEquals(
            LiquidSheetDetent.Large,
            resolveSheetDetent(0.52f, -0.05f, -1200f, 900f, detents, 0.28f),
        )
    }

    @Test
    fun calendarMathHandlesLeapYearsAndMonthLayouts() {
        assertTrue(isLeapYear(2000))
        assertTrue(isLeapYear(2024))
        assertTrue(!isLeapYear(1900))
        assertTrue(!isLeapYear(2026))

        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(30, daysInMonth(2026, 11))
        assertEquals(31, daysInMonth(2026, 12))

        assertEquals(4, firstWeekdayOfMonth(2024, 2)) // Thursday
        assertEquals(0, firstWeekdayOfMonth(2026, 11)) // Sunday

        val novCells = calendarMonthCells(2026, 11)
        assertEquals(0, novCells.size % 7)
        assertEquals(1, novCells.first())
        assertEquals(30, novCells.filterNotNull().last())
        assertEquals(30, novCells.count { it != null })

        assertTrue(runCatching { LiquidCalendarDate(2026, 2, 29) }.isFailure)
        assertEquals(29, LiquidCalendarDate(2024, 2, 29).day)
    }

    @Test
    fun allNewSymbolsResolveViaSystemNames() {
        val expected = listOf(
            "chevron.up.chevron.down",
            "square.on.square",
            "square.fill.on.square.fill",
            "doc.on.clipboard",
            "calendar",
            "slider.horizontal.3",
            "hand.tap",
            "text.cursor",
            "gauge.with.dots.needle.50percent",
            "circle.lefthalf.filled",
            "square.on.circle",
            "menucard",
            "hand.draw",
            "list.bullet.rectangle",
        )
        for (name in expected) {
            assertTrue("Missing symbol for $name", LiquidSymbol.fromSystemName(name) != null)
        }
    }

    @Test
    fun defaultAlertSheetAndControlTokensAreValid() {
        val tokens = LiquidGlassTokens()
        assertTrue(tokens.sheet.corner > 0.dp && tokens.sheet.dismissFraction in 0f..1f)
        assertTrue(tokens.sheet.popoverSpring.stiffness > 0f && tokens.sheet.detentSpring.stiffness > 0f)
        assertTrue(tokens.alert.enterSpring.stiffness > 0f && tokens.alert.exitMillis > 0)
        assertTrue(tokens.controls.segmentedPressScale >= 1f && tokens.controls.segmentedSelection.stiffness > 0f)
    }

    @Test
    fun colorHsvSpectrumAndHexHelpersRoundTrip() {
        val cyan = Color(0f, 195f / 255f, 208f / 255f, 1f)
        assertEquals("00C3D0", formatHexRgb(cyan))
        val hsv = rgbToHsv(cyan.red, cyan.green, cyan.blue)
        val restored = hsvToColor(hsv[0], hsv[1], hsv[2], 0.75f)
        assertTrue(abs(restored.red - cyan.red) < 0.01f)
        assertTrue(abs(restored.green - cyan.green) < 0.01f)
        assertTrue(abs(restored.blue - cyan.blue) < 0.01f)
        assertEquals(0.75f, restored.alpha, 0.001f)

        val white = spectrumPositionToColor(0f, 0.5f)
        assertTrue(white.red > 0.99f && white.green > 0.99f && white.blue > 0.99f)
        val black = spectrumPositionToColor(1f, 0.2f)
        assertTrue(black.red < 0.01f && black.green < 0.01f && black.blue < 0.01f)
        val pos = colorToSpectrumPosition(cyan)
        val fromPos = spectrumPositionToColor(pos.x, pos.y)
        assertTrue(abs(fromPos.red - cyan.red) < 0.02f && abs(fromPos.green - cyan.green) < 0.02f && abs(fromPos.blue - cyan.blue) < 0.02f)
    }

    @Test
    fun calendarDateComparisonAndRangeClamping() {
        val minD = LiquidCalendarDate(2026, 11, 10)
        val maxD = LiquidCalendarDate(2026, 11, 28)
        assertEquals(minD, clampCalendarDate(LiquidCalendarDate(2026, 11, 2), minD, maxD))
        assertEquals(maxD, clampCalendarDate(LiquidCalendarDate(2026, 12, 1), minD, maxD))
        val mid = LiquidCalendarDate(2026, 11, 24)
        assertEquals(mid, clampCalendarDate(mid, minD, maxD))
        assertTrue(minD < mid && mid < maxD)
    }

    @Test
    fun alertTrackedActionReleaseFollowsBoundsAndEnabledState() {
        val bounds = listOf(Rect(0f, 100f, 140f, 150f), Rect(150f, 100f, 290f, 150f), Rect(0f, 160f, 290f, 210f))
        val enabled = listOf(true, true, false)
        assertEquals(0, resolveTrackedActionRelease(Offset(60f, 125f), bounds, enabled))
        assertEquals(1, resolveTrackedActionRelease(Offset(210f, 125f), bounds, enabled))
        assertNull(resolveTrackedActionRelease(Offset(145f, 125f), bounds, enabled))
        assertNull(resolveTrackedActionRelease(Offset(100f, 185f), bounds, enabled))
        assertNull(resolveTrackedActionRelease(Offset(20f, 20f), bounds, enabled))
    }

    @Test
    fun sheetBackgroundDepthAndDetentTransitions() {
        assertEquals(0f, computeSheetBackgroundDepth(0.30f), 0.001f)
        assertEquals(0f, computeSheetBackgroundDepth(0.52f), 0.001f)
        assertEquals(0.5f, computeSheetBackgroundDepth(0.735f), 0.001f)
        assertEquals(1f, computeSheetBackgroundDepth(0.95f), 0.001f)
        assertEquals(1f, computeSheetBackgroundDepth(0.98f), 0.001f)

        val detents = listOf(LiquidSheetDetent.Medium, LiquidSheetDetent.Large)
        assertNull(resolveSheetDetent(0.52f, 0.30f, 0f, 900f, detents, 0.28f))
        assertEquals(LiquidSheetDetent.Medium, resolveSheetDetent(0.95f, 0.10f, 1200f, 900f, detents, 0.28f))
        assertEquals(LiquidSheetDetent.Large, resolveSheetDetent(0.52f, 0f, -1200f, 900f, detents, 0.28f))
    }

    @Test
    fun toggleTrackColorFlipsAtThresholdWithHysteresis() {
        assertFalse(resolveToggleTrackActive(0.0f, currentlyActive = false))
        assertFalse(resolveToggleTrackActive(0.49f, currentlyActive = false))
        assertFalse(resolveToggleTrackActive(0.51f, currentlyActive = false))
        assertTrue(resolveToggleTrackActive(0.53f, currentlyActive = false))
        assertTrue(resolveToggleTrackActive(0.49f, currentlyActive = true))
        assertFalse(resolveToggleTrackActive(0.47f, currentlyActive = true))
    }

    @Test
    fun segmentedPickerTapVsDragCommitSemantics() {
        val (tapCommit, wasTapDrag) = resolveSegmentedCommitIndex(
            claimed = false,
            downIdx = 2,
            fingerIdx = 0f,
            count = 3,
        )
        assertEquals(2, tapCommit)
        assertFalse(wasTapDrag)

        val (dragCommit, wasDrag) = resolveSegmentedCommitIndex(
            claimed = true,
            downIdx = 0,
            fingerIdx = 1.65f,
            count = 3,
        )
        assertEquals(2, dragCommit)
        assertTrue(wasDrag)

        val (widenX, widenY) = segmentedLensScale(1f, 0f, .18f, .52f, .50f, pullAspect = 0f)
        val (squashedX, tallerY) = segmentedLensScale(1f, 0f, .18f, .52f, .50f, pullAspect = 0.6f)
        assertTrue(tallerY > widenY)
        assertTrue(squashedX < widenX)
    }

    @Test
    fun sliderDragTracksContinuouslyAcrossBoundsAndYExcursions() {
        assertEquals(0.8f, resolveSliderDragValue(0.5f, 90f, 300f, 0f..1f, isLtr = true), 0.001f)
        assertEquals(1.0f, resolveSliderDragValue(0.5f, 220f, 300f, 0f..1f, isLtr = true), 0.001f)
        assertEquals(0.0f, resolveSliderDragValue(0.5f, -220f, 300f, 0f..1f, isLtr = true), 0.001f)
        assertEquals(0.2f, resolveSliderDragValue(0.5f, 90f, 300f, 0f..1f, isLtr = false), 0.001f)
    }

    @Test
    fun layerBackdropDefaultsAndCacheCapabilitiesRenderSinglePassSafely() {
        var defaultOnDraw = false
        var customOnDraw = true
        var layerBackedCombined = false
        var nonLayerCombined = true
        var canvasLayerBacked = true
        var emptyLayerBacked = true
        val startEpoch = BackdropDrawEpoch.value
        val scene = ImageComposeScene(width = 64, height = 64) {
            val defaultBackdrop = rememberLayerBackdrop()
            val otherBackdrop = rememberLayerBackdrop()
            val customBackdrop = rememberLayerBackdrop { drawContent() }
            val combinedLayer = rememberCombinedBackdrop(defaultBackdrop, otherBackdrop)
            val combinedMixed = rememberCombinedBackdrop(defaultBackdrop, emptyBackdrop())
            val canvasBackdrop: Backdrop = rememberCanvasBackdrop { }

            defaultOnDraw = defaultBackdrop.isDefaultOnDraw
            customOnDraw = customBackdrop.isDefaultOnDraw
            layerBackedCombined = defaultBackdrop.isLayerBacked() && combinedLayer.isLayerBacked()
            nonLayerCombined = combinedMixed.isLayerBacked()
            canvasLayerBacked = canvasBackdrop.isLayerBacked()
            emptyLayerBacked = emptyBackdrop().isLayerBacked()

            Box(Modifier.size(64.dp).layerBackdrop(defaultBackdrop).background(Color.Red))
        }
        try {
            val image = scene.render()
            val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
            try {
                val centerPixel = bitmap.getColor(32, 32)
                assertEquals(0xFFFF0000.toInt(), centerPixel)
            } finally {
                bitmap.close()
                image.close()
            }
        } finally {
            scene.close()
        }
        assertTrue(defaultOnDraw)
        assertFalse(customOnDraw)
        assertTrue(layerBackedCombined)
        assertFalse(nonLayerCombined)
        assertFalse(canvasLayerBacked)
        assertFalse(emptyLayerBacked)
        assertTrue(BackdropDrawEpoch.value > startEpoch)
    }

    @Test
    fun glassEffectContainerMultiLobeFusionAndBatch4Components() {
        val nearA = Rect(0f, 0f, 66f, 44f)
        val nearB = Rect(70f, 0f, 180f, 44f)
        val nearC = Rect(184f, 0f, 250f, 44f)
        val farC = Rect(240f, 0f, 306f, 44f)
        assertTrue(glassUnionAll(Offset(68f, 22f), listOf(nearA, nearB, nearC), 7f) < 0f)
        assertTrue(glassUnionAll(Offset(182f, 22f), listOf(nearA, nearB, nearC), 7f) < 0f)
        assertTrue(glassUnionAll(Offset(210f, 22f), listOf(nearA, nearB, farC), 7f) > 0f)

        val multiOutline = GlassCapsulesShape(listOf(nearA, nearB, nearC), 7f)
            .createOutline(Size(250f, 44f), LayoutDirection.Ltr, Density(1f))
        assertTrue(multiOutline is androidx.compose.ui.graphics.Outline.Generic)

        assertEquals(listOf(LiquidTextFieldStyle.Plain, LiquidTextFieldStyle.RoundedBorder), LiquidTextFieldStyle.entries)
        assertEquals(
            setOf(
                LiquidActionStyle.Plain,
                LiquidActionStyle.Tinted,
                LiquidActionStyle.Filled,
                LiquidActionStyle.Borderless,
                LiquidActionStyle.Glass,
                LiquidActionStyle.GlassProminent,
            ),
            LiquidActionStyle.entries.toSet(),
        )

        val scene = ImageComposeScene(width = 360, height = 420) {
            val layer = rememberLayerBackdrop()
            LiquidGlassTheme {
                Box(Modifier.size(360.dp, 420.dp)) {
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.size(360.dp, 420.dp).layerBackdrop(layer).background(Color(0xfff2f2f7)),
                    )
                    Column {
                        LiquidFormSection("示例") {
                            row { LiquidActionButton("玻璃", {}, Color.Unspecified, style = LiquidActionStyle.Glass) }
                            row { LiquidTextField("样本", {}, "名称") }
                            row { LiquidTextField("框选", {}, "搜索", style = LiquidTextFieldStyle.RoundedBorder) }
                            row {
                                LiquidControlGroup {
                                    LiquidActionButton("加", {}, Color(0xff007aff), style = LiquidActionStyle.Plain)
                                    LiquidActionButton("减", {}, Color(0xff007aff), style = LiquidActionStyle.Plain)
                                }
                            }
                        }
                        LiquidGlassEffectContainer(layer, spacing = 7.dp) {
                            Row {
                                LiquidToolbarGroup(Modifier.size(66.dp, 44.dp), layer) {}
                                LiquidToolbarGroup(Modifier.size(110.dp, 44.dp), layer) {}
                            }
                        }
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(17_666_667L).close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun indicatorsGaugesOpacitySliderAndGlassVariantsRenderAcrossOpacityLevels() {
        for (dark in listOf(false, true)) {
            val zero = LiquidGlassAppearance(dark, 0f)
            val mid = LiquidGlassAppearance(dark, 0.5f)
            val full = LiquidGlassAppearance(dark, 1f)
            assertTrue(zero.surface.alpha < mid.surface.alpha && mid.surface.alpha < full.surface.alpha)
            assertTrue(zero.blurRadius < mid.blurRadius && mid.blurRadius < full.blurRadius)

            val scene = ImageComposeScene(width = 360, height = 420) {
                val layer = rememberLayerBackdrop()
                val gradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xff5856d6), Color(0xff30b0c7), Color(0xffff9f0a)),
                )
                LiquidGlassTheme(opacity = 0.5f, dark = dark) {
                    Box(Modifier.size(360.dp, 420.dp)) {
                        androidx.compose.foundation.layout.Spacer(
                            Modifier.size(360.dp, 420.dp).layerBackdrop(layer).background(gradient),
                        )
                        Column {
                            LiquidProgressIndicator(0.55f, gradient)
                            LiquidActivityIndicator(label = "正在读取")
                            LiquidLinearGauge(0.65f, Color(0xff007aff), label = "占用", brush = gradient)
                            Row {
                                LiquidGauge(0.5f, Color(0xff007aff), style = LiquidGaugeStyle.CircularCapacity, brush = gradient)
                                LiquidGauge(0.75f, Color(0xff007aff), style = LiquidGaugeStyle.CircularOpen)
                            }
                            LiquidGlassSurface(layer, variant = LiquidGlassVariant.Regular) {
                                LiquidButtonText("Regular")
                            }
                            LiquidGlassSurface(layer, variant = LiquidGlassVariant.Clear) {
                                LiquidButtonText("Clear")
                            }
                            LiquidOpacitySlider(0.5f, {}, "玻璃不透明度", showIcons = true, backdrop = layer)
                            LiquidUnavailableView("空文件夹", LiquidSymbol.Folder, description = "暂无项目")
                        }
                    }
                }
            }
            try {
                scene.render(1_000_000L).close()
                scene.render(17_666_667L).close()
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun shapeProviderAndLayerBackdropEpochAndEnabledGatingWorkAsExpected() {
        val sharedLambda: () -> androidx.compose.ui.graphics.Shape = { androidx.compose.foundation.shape.CircleShape }
        val otherLambda: () -> androidx.compose.ui.graphics.Shape = { androidx.compose.foundation.shape.RoundedCornerShape(12.dp) }
        val p1 = ShapeProvider(sharedLambda)
        val p2 = ShapeProvider(sharedLambda)
        val p3 = ShapeProvider(otherLambda)
        assertEquals(p1, p2)
        assertEquals(p1.hashCode(), p2.hashCode())
        assertNotEquals(p1, p3)

        fun localShapeProvider(rounded: Boolean): () -> androidx.compose.ui.graphics.Shape {
            fun outline(): androidx.compose.ui.graphics.Shape =
                if (rounded) androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                else androidx.compose.foundation.shape.CircleShape
            return ::outline
        }
        assertNotEquals(ShapeProvider(localShapeProvider(false)), ShapeProvider(localShapeProvider(true)))

        var layerA: com.kyant.backdrop.backdrops.LayerBackdrop? = null
        var layerB: com.kyant.backdrop.backdrops.LayerBackdrop? = null
        var disabledLayer: com.kyant.backdrop.backdrops.LayerBackdrop? = null
        var combined: Backdrop? = null
        val scene = ImageComposeScene(width = 64, height = 64) {
            val a = rememberLayerBackdrop().also { layerA = it }
            val b = rememberLayerBackdrop().also { layerB = it }
            val d = rememberLayerBackdrop().also { disabledLayer = it }
            rememberCombinedBackdrop(a, b).also { combined = it }
            Box(Modifier.size(64.dp)) {
                Box(Modifier.size(32.dp).layerBackdrop(a).background(Color.Blue))
                Box(Modifier.size(32.dp).layerBackdrop(d, enabled = false).background(Color.Green))
            }
        }
        try {
            val image = scene.render(1_000_000L)
            val bitmap = org.jetbrains.skia.Bitmap.makeFromImage(image)
            try {
                // Disabled layer draws child content directly onto screen without offscreen FBO recording.
                assertEquals(0xFF00FF00.toInt(), bitmap.getColor(16, 16))
            } finally {
                bitmap.close()
                image.close()
            }
        } finally {
            scene.close()
        }
        val epochA = layerA!!.drawEpoch()
        val epochB = layerB!!.drawEpoch()
        val epochDisabled = disabledLayer!!.drawEpoch()
        assertTrue("Expected drawn layerA epoch > 0, got $epochA", epochA > 0L)
        assertEquals("Undrawn layerB epoch must stay 0", 0L, epochB)
        assertEquals("Gated disabledLayer must not bump epoch", 0L, epochDisabled)
        assertEquals(31L * epochA + epochB, combined!!.drawEpoch())
    }

    @Test
    fun fourLobeCapsulesFuseSmoothlyAndRootMenuUsesLeadingCheckByDefault() {
        val rects4 = listOf(
            Rect(0f, 0f, 44f, 44f),
            Rect(48f, 0f, 92f, 44f),
            Rect(96f, 0f, 140f, 44f),
            Rect(144f, 0f, 188f, 44f),
        )
        // All 3 necks between the 4 lobes fuse smoothly (signed distance < 0) at spacing = 7f.
        assertTrue(glassUnionAll(Offset(46f, 22f), rects4, 7f) < 0f)
        assertTrue(glassUnionAll(Offset(94f, 22f), rects4, 7f) < 0f)
        assertTrue(glassUnionAll(Offset(142f, 22f), rects4, 7f) < 0f)
        // With tiny spacing, the 46px gap stays separated (signed distance > 0).
        assertTrue(glassUnionAll(Offset(46f, 22f), rects4, 0.5f) > 0f)

        val quadOutline = GlassCapsulesShape(rects4, 7f)
            .createOutline(Size(188f, 44f), LayoutDirection.Ltr, Density(1f))
        assertTrue(quadOutline is androidx.compose.ui.graphics.Outline.Generic)

        var menuState: LiquidMenuState? = null
        val scene = ImageComposeScene(width = 320, height = 240) {
            val state = rememberLiquidMenuState { }.also { menuState = it }
            LiquidGlassTheme {
                Box(Modifier.size(320.dp, 240.dp)) {
                    LiquidMenuPanel(
                        state = state,
                        items = listOf(
                            LiquidMenuItem("list", "列表", checked = true),
                            LiquidMenuItem("grid", "网格", checked = false),
                            LiquidMenuItem("col", "分栏", checked = false),
                        ),
                        modifier = Modifier.size(220.dp, 160.dp),
                    )
                }
            }
        }
        val rows: List<MenuRow>
        try {
            scene.render(1_000_000L).close()
            scene.render(17_000_000L).close()
            rows = menuState!!.rows.toList()
        } finally {
            scene.close()
        }
        assertEquals(3, rows.size)
        assertTrue("Root checkable menu items should default to leadingCheck = true", rows.all { it.leadingCheck })
    }

    @Test
    fun ios27SliderTicksIdentityGlassPickersSafeAreaBarAndMinimizedTabBarWork() {
        val ticks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        // Magnetic snap pulls values within threshold onto the nearest tick; leaves mid-gap values smooth.
        assertEquals(0.25f, resolveSliderTickSnap(0.265f, ticks, 0f..1f, snapRadiusFraction = 0.03f), 1e-4f)
        assertEquals(0.50f, resolveSliderTickSnap(0.485f, ticks, 0f..1f, snapRadiusFraction = 0.03f), 1e-4f)
        assertEquals(0.37f, resolveSliderTickSnap(0.37f, ticks, 0f..1f, snapRadiusFraction = 0.03f), 1e-4f)
        // Release snap (forceSnap = true) always locks to the nearest tick.
        assertEquals(0.50f, resolveSliderTickSnap(0.39f, ticks, 0f..1f, forceSnap = true), 1e-4f)
        assertEquals(0.25f, resolveSliderTickSnap(0.35f, ticks, 0f..1f, forceSnap = true), 1e-4f)

        assertEquals(
            listOf(LiquidGlassVariant.Regular, LiquidGlassVariant.Clear, LiquidGlassVariant.Identity),
            LiquidGlassVariant.entries,
        )
        assertEquals(listOf("b", "c", "a"), moveListItem(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("a", "b", "c"), moveListItem(listOf("a", "b", "c"), 1, 1))
        assertEquals(5, LiquidControlSize.entries.size)
        assertEquals(3, LiquidButtonShape.entries.size)

        val tabs = listOf(
            LiquidTab("recent", "最近", LiquidSymbol.Clock),
            LiquidTab("pinned", "固定", LiquidSymbol.Bookmark),
            LiquidTab("browse", "浏览", LiquidSymbol.FilledFolder),
        )
        for (dark in listOf(false, true)) {
            for (minimized in listOf(false, true)) {
                val scene = ImageComposeScene(width = 400, height = 520) {
                    val layer = rememberLayerBackdrop()
                    LiquidGlassTheme(opacity = 0.5f, dark = dark) {
                        Box(Modifier.size(400.dp, 520.dp)) {
                            androidx.compose.foundation.layout.Spacer(
                                Modifier.size(400.dp, 520.dp).layerBackdrop(layer).background(Color(0xff233756)),
                            )
                            Column {
                                LiquidGlassSurface(
                                    backdrop = layer,
                                    variant = LiquidGlassVariant.Regular,
                                    tint = Color(0xff007aff),
                                    interactive = true,
                                    onClick = {},
                                ) { LiquidButtonText("Tinted") }
                                LiquidGlassSurface(backdrop = layer, variant = LiquidGlassVariant.Identity) {
                                    LiquidButtonText("Identity")
                                }
                                LiquidSlider({ 0.5f }, {}, 0f..1f, .001f, layer, tapToSeek = true, ticks = ticks, neutralValue = 0.5f)
                                LiquidToggleButton("粗体", true, {})
                                LiquidBadge("5")
                                LiquidTipCard("新特性", "支持 Liquid Glass 提示卡片", onDismiss = {})
                                LiquidMeshGradient(
                                    width = 2,
                                    height = 2,
                                    colors = listOf(Color.Cyan, Color.Blue, Color.Magenta, Color.Yellow),
                                    modifier = Modifier.size(64.dp),
                                )
                                LiquidFlowLayout {
                                    LiquidBadge("A")
                                    LiquidBadge("B")
                                }
                                LiquidBarChart(
                                    points = listOf(
                                        LiquidChartSeriesPoint("1月", "国内", 120f, Color.Blue),
                                        LiquidChartSeriesPoint("2月", "国内", 180f, Color.Blue),
                                    ),
                                )
                                LiquidPieDonutChart(
                                    slices = listOf(LiquidPieSlice("iOS", 60f, Color.Cyan), LiquidPieSlice("Android", 40f, Color.Green)),
                                )
                                LiquidTabsPicker(
                                    options = listOf(
                                        LiquidTabOption("all", "全部", LiquidSymbol.Grid),
                                        LiquidTabOption("doc", "文档", LiquidSymbol.Document),
                                    ),
                                    selectedId = "all",
                                    onSelect = {},
                                    backdrop = layer,
                                )
                                LiquidPalettePicker(
                                    options = listOf(
                                        LiquidPaletteOption("teal", "青绿", Color(0xff30a3a6)),
                                        LiquidPaletteOption("amber", "琥珀", Color(0xffff9f0a)),
                                    ),
                                    selectedId = "teal",
                                    onSelect = {},
                                )
                                LiquidInlinePicker(
                                    options = listOf("按名称", "按日期"),
                                    selected = "按名称",
                                    onSelect = {},
                                    title = "排序",
                                )
                                LiquidSafeAreaBar(layer, edge = LiquidSafeAreaEdge.Bottom) {
                                    LiquidButtonText("底栏")
                                }
                                LiquidTabBar(
                                    tabs = tabs,
                                    selected = "browse",
                                    onSelect = {},
                                    backdrop = layer,
                                    tint = Color(0xff007aff),
                                    minimized = minimized,
                                    onExpandFromMinimized = {},
                                    searchAction = {},
                                )
                            }
                        }
                    }
                }
                try {
                    scene.render(1_000_000L).close()
                    scene.render(17_666_667L).close()
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun sharedPageBackdropAndCachedControlReplayRenderCleanly() {
        var shared: com.kyant.backdrop.backdrops.LayerBackdrop? = null
        val scene = ImageComposeScene(width = 360, height = 420) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val pageLayer = rememberLayerBackdrop().also { shared = it }
                Box(Modifier.size(360.dp, 420.dp).background(Color.Black)) {
                    LiquidNavigationScaffold(
                        title = "Performance",
                        backdrop = com.kyant.backdrop.backdrops.emptyBackdrop(),
                        modifier = Modifier.size(360.dp, 420.dp),
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                        pageBackdrop = pageLayer,
                    ) {
                        Column(Modifier.size(360.dp, 420.dp)) {
                            LiquidToggle(selected = { true }, onSelect = {}, backdrop = pageLayer)
                            LiquidSlider(
                                value = { 0.5f },
                                onValueChange = {},
                                valueRange = 0f..1f,
                                visibilityThreshold = 0.01f,
                                backdrop = pageLayer,
                            )
                            LiquidSegmentedPicker(
                                options = listOf("A", "B", "C"),
                                selected = "A",
                                select = {},
                                backdrop = pageLayer,
                            )
                            LiquidIcon(LiquidSymbol.Sparkles, Color.White)
                        }
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            val epochAfterFirst = shared!!.drawEpoch()
            assertTrue("Shared pageBackdrop must record on first draw", epochAfterFirst > 0L)
            // Re-render two more frames to exercise ShadowNode/HighlightNode/DrawBackdropNode cached replay paths.
            scene.render(17_666_667L).close()
            scene.render(34_333_334L).close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun modalHostOpacitySliderReorderGridAndHapticsRenderCleanly() {
        assertEquals(4, LiquidHaptic.entries.size)
        val scene = ImageComposeScene(width = 360, height = 520) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val layer = rememberLayerBackdrop()
                LiquidModalHost(Modifier.size(360.dp, 520.dp)) {
                    Column(Modifier.size(360.dp, 520.dp).layerBackdrop(layer).background(Color.Black)) {
                        LiquidOpacitySlider(
                            value = 0.65f,
                            onValueChange = {},
                            label = "透明度",
                            showIcons = true,
                            backdrop = layer,
                        )
                        LiquidReorderableGrid(
                            items = listOf("A", "B", "C", "D"),
                            key = { it },
                            onReorder = {},
                            columns = 2,
                        ) { item, _ ->
                            Box(Modifier.size(80.dp, 60.dp).background(Color(0xff2c2c2e))) {
                                LiquidButtonText(item)
                            }
                        }
                        LiquidSwipeActions(
                            rowKey = "row-1",
                            trailing = listOf(LiquidSwipeAction("删除", LiquidSymbol.Trash, Color.Red) {}),
                        ) {
                            Box(Modifier.size(360.dp, 48.dp).background(Color(0xff1c1c1e)))
                        }
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(17_666_667L).close()
        } finally {
            scene.close()
        }
    }

    @Test
    fun solidColorBackdropAndLiquidAppScaffoldSupportEdgeToEdgeAndCaching() {
        var darkBackdrop: Backdrop? = null
        var lightBackdrop: Backdrop? = null
        val scene = ImageComposeScene(width = 360, height = 520) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val dark = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color.Black)
                val light = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color(0xfff2f2f7))
                darkBackdrop = dark
                lightBackdrop = light
                LiquidAppScaffold(
                    modifier = Modifier.size(360.dp, 520.dp),
                    bottomBar = { barBackdrop ->
                        LiquidSafeAreaBar(
                            backdrop = barBackdrop,
                            edge = LiquidSafeAreaEdge.Bottom,
                            respectSystemBars = true,
                        ) {
                            LiquidButtonText("安全区底栏")
                        }
                    },
                ) { pageBackdrop, _ ->
                    LiquidNavigationScaffold(
                        title = "EdgeToEdge",
                        backdrop = dark,
                        modifier = Modifier.size(360.dp, 520.dp),
                        contentWindowInsets = LiquidSafeAreaDefaults.windowInsets,
                        pageBackdrop = pageBackdrop,
                    ) {
                        Box(Modifier.size(360.dp, 520.dp).background(Color(0xff1c1c1e)))
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(17_666_667L).close()
        } finally {
            scene.close()
        }
        val dark = requireNotNull(darkBackdrop)
        val light = requireNotNull(lightBackdrop)
        assertTrue("SolidColorBackdrop must be cacheable as layer-backed", dark.isLayerBacked())
        assertTrue("SolidColorBackdrop must not depend on layout coordinates", !dark.isCoordinatesDependent)
        assertTrue("SolidColorBackdrop drawEpoch must be non-zero", dark.drawEpoch() != 0L)
        assertTrue("Different colors must yield different drawEpochs", dark.drawEpoch() != light.drawEpoch())
    }

    @Test
    fun layerBackdropEnabledSemanticsAndLiveModalHostAndTabBarSearchSelected() {
        var enabled by mutableStateOf(true)
        var showAlert by mutableStateOf(false)
        var trackedLayer: com.kyant.backdrop.backdrops.LayerBackdrop? = null
        var modalBackdropSeen: Backdrop? = null
        val scene = ImageComposeScene(width = 360, height = 560) {
            LiquidGlassTheme(opacity = .5f, dark = false) {
                LiquidModalHost(Modifier.size(360.dp, 560.dp)) {
                    val solid = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color(0xfff2f2f7))
                    val layer = rememberLayerBackdrop().also { trackedLayer = it }
                    Box(Modifier.size(360.dp, 560.dp).layerBackdrop(layer, enabled = enabled).background(Color(0xfff2f2f7))) {
                        LiquidTabBar(
                            tabs = listOf(
                                LiquidTab("a", "组件库", LiquidSymbol.Grid),
                                LiquidTab("b", "iOS 27", LiquidSymbol.Sparkles),
                            ),
                            selected = "a",
                            onSelect = {},
                            backdrop = solid,
                            tint = Color(0xff007aff),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                            searchAction = {},
                            searchLabel = "搜索组件",
                            searchSelected = true,
                        )
                    }
                    if (showAlert) {
                        LiquidAlert(
                            title = "实时折射弹窗",
                            actions = listOf(LiquidAlertAction("好", LiquidAlertRole.Primary, onClick = { showAlert = false })),
                            onDismissRequest = { showAlert = false },
                            tint = Color(0xff007aff),
                            backdrop = solid,
                            field = {
                                modalBackdropSeen = LocalLiquidModalBackdrop.current
                                BasicText("正文")
                            },
                        )
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            val layer = requireNotNull(trackedLayer)
            assertTrue("LayerBackdrop must be enabled initially", layer.isEnabled)
            val epochWhileEnabled = layer.drawEpoch

            enabled = false
            scene.render(17_666_667L).close()
            assertTrue("LayerBackdrop must mark isEnabled = false when disabled", !layer.isEnabled)
            assertTrue("Disabling LayerBackdrop must bump drawEpoch to invalidate caches", layer.drawEpoch > epochWhileEnabled)

            showAlert = true
            scene.render(34_333_333L).close()
            scene.render(51_000_000L).close()
            val liveModal = requireNotNull(modalBackdropSeen)
            assertTrue("LiquidModalHost must expose a live LayerBackdrop while modal is open", liveModal is com.kyant.backdrop.backdrops.LayerBackdrop)
            assertTrue("Live modal backdrop must be enabled while modal is open", (liveModal as com.kyant.backdrop.backdrops.LayerBackdrop).isEnabled)

            showAlert = false
            scene.render(67_666_667L).close()
            scene.render(84_333_333L).close()
            assertTrue("Live modal backdrop must disable at rest when no modal is open", !liveModal.isEnabled)
        } finally {
            scene.close()
        }
    }

    @Test
    fun clearGlassPreservesSkyHueAndControlsHonorCustomTintAndFormattedStepper() {
        assertEquals("Apple Orange must use crisp white ink", Color.White, liquidContentColorFor(Color(0xffff9500)))
        assertEquals("Apple Green must use crisp white ink", Color.White, liquidContentColorFor(Color(0xff34c759)))
        assertEquals("Pure White must use dark ink", Color(0xff0d0d0e), liquidContentColorFor(Color.White))

        val scene = ImageComposeScene(width = 240, height = 360) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val sky = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color(0xff1759ba))
                Column(Modifier.size(240.dp, 360.dp).background(Color(0xff1759ba)).padding(12.dp)) {
                    LiquidGlassSurface(
                        backdrop = sky,
                        modifier = Modifier.size(160.dp, 80.dp),
                        variant = LiquidGlassVariant.Clear,
                        tint = Color.White.copy(alpha = .08f),
                    ) {
                        BasicText("Clear Sky")
                    }
                    LiquidToggle(selected = { true }, onSelect = {}, backdrop = sky, tint = Color(0xffff9500))
                    LiquidSlider(value = { 0.7f }, onValueChange = {}, valueRange = 0f..1f, visibilityThreshold = .01f, backdrop = sky, tapToSeek = true, tint = Color(0xffff9500))
                    LiquidStepper("空调温度 23°C", 23, {}, 16..30, Color(0xffff9500), showValue = false)
                    LiquidLineAreaChart(
                        labels = listOf("现在", "14时", "15时"),
                        values = listOf(22f, 24f, 25f),
                        lineColor = Color.White,
                        chartHeight = 60.dp,
                        showPoints = false,
                        showGrid = false,
                        labelColor = Color.White,
                        yDomain = 10f..28f,
                    )
                }
            }
        }
        try {
            val img = scene.render(1_000_000L)
            val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
            try {
                // Center of Clear glass card over deep blue sky (#1759BA) must stay distinctly blue (B > R + 60), never desaturated grey.
                val px = bmp.getColor(80, 50)
                val r = (px shr 16) and 0xFF
                val b = px and 0xFF
                assertTrue("Clear glass over blue sky must preserve blue hue (got r=$r, b=$b)", b > r + 60)
            } finally {
                bmp.close()
                img.close()
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun tabBarRefractionStaysAlignedAcrossHorizontalReposition() {
        var shiftRight by mutableStateOf(true)
        val scene = ImageComposeScene(width = 400, height = 180, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = false) {
                val sceneBackdrop = rememberLayerBackdrop()
                Box(Modifier.size(400.dp, 180.dp)) {
                    Row(Modifier.fillMaxSize().layerBackdrop(sceneBackdrop)) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFF1818)))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1848FF)))
                    }
                    LiquidBottomTabsLayout(
                        selectedTabIndex = { 0 },
                        onTabSelected = {},
                        backdrop = sceneBackdrop,
                        tabsCount = 2,
                        modifier = Modifier
                            .offset { IntOffset(if (shiftRight) 160 else 80, 100) }
                            .width(240.dp),
                        tint = Color(0xFF007AFF),
                        height = 60.dp,
                    ) {
                        LiquidBottomTab({}, Modifier) {}
                        LiquidBottomTab({}, Modifier) {}
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(17_000_000L).close()
            shiftRight = false
            scene.render(34_000_000L).close()
            val img = scene.render(51_000_000L)
            val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
            try {
                // After moving to x=80..320 (240dp wide), left tab (80..200) sits strictly over Red and right tab (200..320) over Blue.
                val leftPx = bmp.getColor(125, 130)
                val leftR = (leftPx shr 16) and 0xFF
                val leftB = leftPx and 0xFF
                val rightPx = bmp.getColor(275, 130)
                val rightR = (rightPx shr 16) and 0xFF
                val rightB = rightPx and 0xFF
                assertTrue("Left tab must refract Red backdrop after reposition (got r=$leftR, b=$leftB)", leftR > leftB + 30)
                assertTrue("Right tab must refract Blue backdrop after reposition (got r=$rightR, b=$rightB)", rightB > rightR + 30)
            } finally {
                bmp.close()
                img.close()
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun sharedLayerBackdropSurvivesTabReplacement() {
        var activeTab by mutableIntStateOf(0)
        var trackedBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null
        val scene = ImageComposeScene(width = 320, height = 240, density = Density(1f)) {
            val shared = rememberLayerBackdrop().also { trackedBackdrop = it }
            AnimatedContent(activeTab, label = "TabSwitch") { tab ->
                Box(
                    Modifier
                        .size(320.dp, 240.dp)
                        .layerBackdrop(shared)
                        .background(if (tab == 0) Color.Red else Color.Blue),
                )
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(17_000_000L).close()
            activeTab = 1
            for (ms in 34L..600L step 16L) {
                scene.render(ms * 1_000_000L).close()
            }
            val shared = requireNotNull(trackedBackdrop)
            assertTrue("Shared LayerBackdrop must stay enabled after old destination detaches", shared.isEnabled)
            assertNotNull("Shared LayerBackdrop must keep active coordinates after tab switch", shared.layerCoordinates)
            assertTrue("Active coordinates must remain attached", shared.layerCoordinates!!.isAttached)
        } finally {
            scene.close()
        }
    }

    @Test
    fun lightInteractiveSurfacePressProducesNoDarkArtifacts() {
        val scene = ImageComposeScene(width = 240, height = 180, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = false) {
                val colors = liquidListColors(dark = false)
                val solid = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color(0xFFF2F2F7))
                LiquidNavigationScaffold(
                    title = "Music",
                    backdrop = solid,
                    modifier = Modifier.size(240.dp, 180.dp),
                    colors = colors,
                    bottomBar = { barBackdrop ->
                        LiquidGlassSurface(
                            backdrop = barBackdrop,
                            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().size(180.dp, 54.dp),
                            shape = LiquidCapsule,
                            variant = LiquidGlassVariant.Regular,
                            interactive = true,
                            onClick = {},
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Box(Modifier.weight(1f).size(80.dp).background(Color.White))
                            Box(Modifier.width(12.dp))
                            Box(Modifier.weight(1f).size(80.dp).background(Color.White))
                        }
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Press, Offset(120f, 138f))
            scene.render(20_000_000L).close()
            scene.render(60_000_000L).close()
            val img = scene.render(120_000_000L)
            val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
            try {
                for (y in 120..154 step 4) {
                    for (x in 48..192 step 6) {
                        val px = bmp.getColor(x, y)
                        val r = (px shr 16) and 0xFF
                        val g = (px shr 8) and 0xFF
                        val b = px and 0xFF
                        val minCh = minOf(r, g, b)
                        assertTrue(
                            "Light-mode pressed glass must never show black gap artifacts at ($x,$y): rgb=($r,$g,$b)",
                            minCh >= 180,
                        )
                    }
                }
            } finally {
                bmp.close()
                img.close()
            }
            scene.sendPointerEvent(PointerEventType.Release, Offset(120f, 138f))
        } finally {
            scene.close()
        }
    }

    @Test
    fun toggleProportionsAndTabBarMinimizedOrbsMatchiOS27() {
        var toggleSize = IntSize.Zero
        var isMinimized by mutableStateOf(false)
        val scene = ImageComposeScene(width = 360, height = 160, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = false) {
                val bg = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color(0xFFF2F2F7))
                val tabs = remember {
                    listOf(
                        LiquidTab("lib", "组件库", LiquidSymbol.Grid),
                        LiquidTab("new", "iOS 27", LiquidSymbol.Sparkles),
                    )
                }
                Column(Modifier.size(360.dp, 160.dp).background(Color(0xFFF2F2F7)).padding(16.dp)) {
                    LiquidToggle(
                        selected = { true },
                        onSelect = {},
                        backdrop = bg,
                        modifier = Modifier.onGloballyPositioned { toggleSize = it.size },
                    )
                    LiquidTabBar(
                        tabs = tabs,
                        selected = "lib",
                        onSelect = {},
                        backdrop = bg,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        minimized = isMinimized,
                        onExpandFromMinimized = { isMinimized = false },
                        searchAction = {},
                    )
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            assertEquals("Toggle width must be 60dp for 60x28 track proportions", 60, toggleSize.width)
            assertTrue("Toggle min touch height must be >= 44dp", toggleSize.height >= 44)

            isMinimized = true
            for (ms in 16L..800L step 16L) {
                scene.render(ms * 1_000_000L).close()
            }
            val img = scene.render(816_000_000L)
            val bmp = org.jetbrains.skia.Bitmap.makeFromImage(img)
            try {
                // Scan both 52x52 minimized orb regions (x=[16..68], y=[72..124] on left; x=[276..328], y=[72..124] on right).
                val leftBlue = (76..120).sumOf { y ->
                    (20..64).count { x ->
                        val px = bmp.getColor(x, y)
                        ((px and 0xFF) - ((px shr 16) and 0xFF)) > 30
                    }
                }
                val rightDark = (76..120).sumOf { y ->
                    (276..324).count { x ->
                        val px = bmp.getColor(x, y)
                        val r = (px shr 16) and 0xFF
                        val g = (px shr 8) and 0xFF
                        val b = px and 0xFF
                        r < 190 && g < 190 && b < 190
                    }
                }
                assertTrue("Left minimized orb icon must render inside 52dp left orb (count=$leftBlue)", leftBlue > 15)
                assertTrue("Right search orb icon must render inside 52dp right orb (count=$rightDark)", rightDark > 15)
            } finally {
                bmp.close()
                img.close()
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun landscapeTabBarAnimatesSmoothlyBetweenSingleCapsuleAndMinimizedOrbs() {
        var barSize = IntSize.Zero
        var isMinimized by mutableStateOf(false)
        val scene = ImageComposeScene(width = 720, height = 100, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val bg = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color(0xFF0A0C10))
                val tabs = remember {
                    listOf(
                        LiquidTab("lib", "组件库", LiquidSymbol.Grid),
                        LiquidTab("new", "iOS 27", LiquidSymbol.Sparkles),
                    )
                }
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0C10)), contentAlignment = Alignment.Center) {
                    LiquidTabBar(
                        tabs = tabs,
                        selected = "lib",
                        onSelect = {},
                        backdrop = bg,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned { barSize = it.size },
                        minimized = isMinimized,
                        onExpandFromMinimized = { isMinimized = false },
                        searchAction = {},
                    )
                }
            }
        }
        fun brightPixelCount(bmp: org.jetbrains.skia.Bitmap, xRange: IntRange, yRange: IntRange = 28..72): Int {
            var count = 0
            for (y in yRange) {
                for (x in xRange) {
                    val px = bmp.getColor(x, y)
                    val maxCh = maxOf((px shr 16) and 0xFF, (px shr 8) and 0xFF, px and 0xFF)
                    if (maxCh > 36) count++
                }
            }
            return count
        }
        try {
            val initImg = scene.render(1_000_000L)
            val initBmp = org.jetbrains.skia.Bitmap.makeFromImage(initImg)
            try {
                assertEquals("Landscape tab bar height must stay 48dp", 48, barSize.height)
                assertEquals("Landscape tab bar spans 720dp container", 720, barSize.width)
                assertTrue("Expanded pill must draw tabs near center", brightPixelCount(initBmp, 200..520) > 100)
                assertEquals("Expanded landscape mode must have no left minimized orb", 0, brightPixelCount(initBmp, 4..44))
                assertEquals("Expanded landscape mode must have no right detached search orb", 0, brightPixelCount(initBmp, 676..716))
            } finally {
                initBmp.close()
                initImg.close()
            }

            // Collapse with spring animation and inspect mid-flight at 112ms.
            isMinimized = true
            for (ms in 16L..112L step 16L) {
                scene.render(ms * 1_000_000L).close()
            }
            val midCollapseImg = scene.render(128_000_000L)
            val midCollapseBmp = org.jetbrains.skia.Bitmap.makeFromImage(midCollapseImg)
            try {
                assertEquals("Landscape height must remain 48dp mid-collapse (no flash to portrait 56dp)", 48, barSize.height)
                assertTrue(
                    "Mid-collapse bar must smoothly pass through intermediate x=[60..175]",
                    brightPixelCount(midCollapseBmp, 60..175) > 20,
                )
            } finally {
                midCollapseBmp.close()
                midCollapseImg.close()
            }

            // Settle at minimized state (800ms).
            for (ms in 144L..800L step 16L) {
                scene.render(ms * 1_000_000L).close()
            }
            val minImg = scene.render(816_000_000L)
            val minBmp = org.jetbrains.skia.Bitmap.makeFromImage(minImg)
            try {
                assertEquals(48, barSize.height)
                assertTrue("Minimized left orb must settle at x=[4..44]", brightPixelCount(minBmp, 4..44) > 20)
                assertTrue("Minimized right search orb must settle at x=[676..716]", brightPixelCount(minBmp, 676..716) > 20)
                assertEquals("Center gap must be empty when minimized", 0, brightPixelCount(minBmp, 220..500))
            } finally {
                minBmp.close()
                minImg.close()
            }

            // Expand back with spring animation and verify mid-flight interpolation (no instant teleport).
            isMinimized = false
            for (ms in 832L..928L step 16L) {
                scene.render(ms * 1_000_000L).close()
            }
            val midExpandImg = scene.render(944_000_000L)
            val midExpandBmp = org.jetbrains.skia.Bitmap.makeFromImage(midExpandImg)
            try {
                assertEquals(48, barSize.height)
                assertTrue(
                    "Mid-expand bar must interpolate through x=[60..175] instead of jumping straight to center",
                    brightPixelCount(midExpandBmp, 60..175) > 20,
                )
            } finally {
                midExpandBmp.close()
                midExpandImg.close()
            }

            // Settle back at expanded centered horizontal pill.
            for (ms in 960L..1600L step 16L) {
                scene.render(ms * 1_000_000L).close()
            }
            val settledImg = scene.render(1616_000_000L)
            val settledBmp = org.jetbrains.skia.Bitmap.makeFromImage(settledImg)
            try {
                assertTrue(brightPixelCount(settledBmp, 200..520) > 100)
                assertEquals(0, brightPixelCount(settledBmp, 4..44))
                assertEquals(0, brightPixelCount(settledBmp, 676..716))
            } finally {
                settledBmp.close()
                settledImg.close()
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun wheelPickerRubberBandOverscrollAndFlingBounce() {
        assertEquals(0f, wheelRubberBandOffset(0f, 3f), 1e-4f)
        assertEquals(2.25f, wheelRubberBandOffset(2.25f, 3f), 1e-4f)
        val topOut = wheelRubberBandOffset(-2.0f, 3f)
        assertTrue("Top rubber band must stretch past 0 (got $topOut)", topOut < -0.5f && topOut > -3.2f)
        val botOut = wheelRubberBandOffset(5.5f, 3f)
        assertTrue("Bottom rubber band must stretch past 3 (got $botOut)", botOut > 3.5f && botOut < 6.2f)
        assertEquals(-2.0f, wheelInverseRubberBand(topOut, 3f), 0.05f)
        assertEquals(5.5f, wheelInverseRubberBand(botOut, 3f), 0.05f)

        val centerGeom = wheelCylinderTransform(0f, 40f)
        assertEquals(0f, centerGeom.translationYPx, 1e-4f)
        assertEquals(0f, centerGeom.rotationXDeg, 1e-4f)
        assertEquals(1f, centerGeom.scaleX, 1e-4f)
        assertEquals(1f, centerGeom.alpha, 1e-4f)
        val outerGeom = wheelCylinderTransform(2f, 40f)
        assertTrue(outerGeom.translationYPx > 65f && outerGeom.translationYPx < 80f)
        assertTrue(outerGeom.scaleX < 0.92f && outerGeom.alpha < 0.5f)

        val topFlick = resolveWheelFlingTarget(0.2f, -6.5f, 3f)
        assertEquals(0, topFlick.settleIndex)
        assertNotNull(topFlick.overshootTarget)
        assertTrue(topFlick.overshootTarget!! < 0f)

        val stationaryRelease = resolveWheelFlingTarget(-0.9f, 0f, 3f)
        assertEquals(0, stationaryRelease.settleIndex)
        assertNull(stationaryRelease.overshootTarget)

        val botFlick = resolveWheelFlingTarget(2.8f, 6.5f, 3f)
        assertEquals(3, botFlick.settleIndex)
        assertNotNull(botFlick.overshootTarget)
        assertTrue(botFlick.overshootTarget!! > 3f)
    }

    @Test
    fun toolbarGroupedWidthMatchesIos27AndNavBarSlotDistinguishesMorphVsSwitch() {
        val pairWidth = LiquidToolbarDefaults.width(listOf(LiquidSymbol.Star, LiquidSymbol.Code))
        assertTrue("2-icon grouped toolbar width must align with iOS 27 (103..107dp, got $pairWidth)", pairWidth in 103.dp..107.dp)

        var useTwoButtons by mutableStateOf(false)
        var showGroup by mutableStateOf(true)
        var measuredSize = IntSize.Zero
        val scene = ImageComposeScene(width = 320, height = 80, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val bg = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color.Black)
                val grp = if (!showGroup) null else if (!useTwoButtons) {
                    LiquidNavBarGroup(
                        listOf(LiquidNavBarAction("one", "One", LiquidSymbol.Sun)),
                        transitionGroup = "default",
                    )
                } else {
                    LiquidNavBarGroup(
                        listOf(
                            LiquidNavBarAction("fav", "Fav", LiquidSymbol.Star),
                            LiquidNavBarAction("code", "Code", LiquidSymbol.Code),
                        ),
                        transitionGroup = "default",
                    )
                }
                Box(Modifier.size(320.dp, 80.dp), contentAlignment = Alignment.CenterEnd) {
                    LiquidNavBarSlot(grp, bg, Modifier.onGloballyPositioned { measuredSize = it.size })
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            assertEquals("Single navbar action pill must be 44dp", 44, measuredSize.width)

            useTwoButtons = true
            for (ms in 16L..600L step 16L) scene.render(ms * 1_000_000L).close()
            assertTrue("Morphed 2-button toolbar width must reach ~105dp (got ${measuredSize.width})", measuredSize.width >= 103)

            showGroup = false
            for (ms in 616L..1000L step 16L) scene.render(ms * 1_000_000L).close()
            assertEquals("Null group must completely switch away", 0, measuredSize.width)
        } finally {
            scene.close()
        }
    }

    @Test
    fun embeddedMenuAndCustomContextPreviewPortalToModalHostWithoutClipping() {
        var menuState: LiquidMenuState? = null
        var ctxState: LiquidMenuState? = null
        var panelHeight = 0
        val scene = ImageComposeScene(width = 360, height = 560, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = true) {
                val bg = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color.Black)
                LiquidModalHost(Modifier.size(360.dp, 560.dp)) {
                    Column(Modifier.size(360.dp, 560.dp).padding(16.dp)) {
                        val mState = rememberLiquidMenuState {}.also { menuState = it }
                        val cState = rememberLiquidMenuState {}.also { ctxState = it }
                        val items = listOf(
                            LiquidMenuItem("a", "第一项"),
                            LiquidMenuItem("b", "第二项"),
                            LiquidMenuItem("c", "第三项"),
                            LiquidMenuItem("d", "第四项"),
                        )
                        // Small 44dp embedded container must portal overlay to LiquidModalHost so panel isn't squashed to 44dp.
                        LiquidAnchoredMenuHost(mState, bg, items, Modifier.size(320.dp, 44.dp)) {
                            Box(
                                Modifier.size(120.dp, 44.dp).liquidMenuTrigger(mState),
                            )
                        }
                        LiquidListContextMenuHost(
                            cState,
                            bg,
                            items,
                            Modifier.size(320.dp, 56.dp),
                            preview = { Box(Modifier.size(220.dp, 96.dp)) },
                        ) {
                            Box(Modifier.size(320.dp, 56.dp).liquidListContextMenu(cState) {})
                        }
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            val m = requireNotNull(menuState)
            val trigger = m.triggers.first()
            m.openFrom(trigger)
            for (ms in 16L..400L step 16L) scene.render(ms * 1_000_000L).close()
            panelHeight = m.panels[emptyList()]?.size?.height ?: 0
            assertTrue("Portaled menu panel must not be squashed to 44px card height (got $panelHeight)", panelHeight > 120)
            m.dismiss()
            for (ms in 416L..700L step 16L) scene.render(ms * 1_000_000L).close()

            val c = requireNotNull(ctxState)
            val cTrigger = c.triggers.first()
            c.beginHold(Offset(100f, 80f), cTrigger)
            c.openFrom(cTrigger)
            for (ms in 716L..1100L step 16L) scene.render(ms * 1_000_000L).close()
            assertTrue("Custom preview ContextMenu should remain active with panel > 120px", (c.panels[emptyList()]?.size?.height ?: 0) > 120)
        } finally {
            scene.close()
        }
    }

    @Test
    fun borderlessMenuTriggerReboundsElasticallyOnClose() {
        var menuState: LiquidMenuState? = null
        val scene = ImageComposeScene(width = 360, height = 560, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = false) {
                val bg = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color.White)
                LiquidModalHost(Modifier.size(360.dp, 560.dp)) {
                    Box(Modifier.size(360.dp, 560.dp).padding(24.dp)) {
                        val mState = rememberLiquidMenuState {}.also { menuState = it }
                        val items = listOf(
                            LiquidMenuItem("a", "第一项"),
                            LiquidMenuItem("b", "第二项"),
                        )
                        LiquidAnchoredMenuHost(
                            mState,
                            bg,
                            items,
                            Modifier.size(200.dp, 44.dp),
                            presentation = LiquidMenuPresentation.ButtonTransform,
                        ) {
                            Box(Modifier.size(120.dp, 40.dp).liquidBorderlessMenuTrigger(mState))
                        }
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            val m = requireNotNull(menuState)
            val trigger = m.triggers.first()
            m.openFrom(trigger)
            for (ms in 16L..400L step 16L) scene.render(ms * 1_000_000L).close()
            assertTrue("Menu should be expanded before dismiss", m.expanded)
            m.dismiss()
            var maxScale = 1f
            var minScale = 1f
            for (ms in 416L..1200L step 16L) {
                scene.render(ms * 1_000_000L).close()
                val s = trigger.borderlessReboundScale
                if (s > maxScale) maxScale = s
                if (s < minScale) minScale = s
            }
            assertTrue("Expected elastic rebound overshoot above 1.05f on close, got maxScale=$maxScale", maxScale > 1.05f)
            assertTrue("Expected underdamped spring undershoot below 0.99f on close, got minScale=$minScale", minScale < 0.99f)
            assertTrue("Expected rebound to settle near 1.0f, got ${trigger.borderlessReboundScale}",
                kotlin.math.abs(trigger.borderlessReboundScale - 1f) < 0.02f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun glassEffectContainerMultiLobeOutlineExecutesRapidly() {
        val rects = listOf(
            androidx.compose.ui.geometry.Rect(0f, 0f, 48f, 48f),
            androidx.compose.ui.geometry.Rect(38f, 2f, 82f, 46f),
            androidx.compose.ui.geometry.Rect(110f, 2f, 154f, 46f),
            androidx.compose.ui.geometry.Rect(164f, 2f, 208f, 46f),
        )
        val shape = GlassCapsulesShape(rects, spacing = 14f)
        val startNs = System.nanoTime()
        repeat(60) {
            val outline = shape.createOutline(androidx.compose.ui.geometry.Size(320f, 120f), LayoutDirection.Ltr, Density(3f))
            assertTrue(outline is androidx.compose.ui.graphics.Outline.Generic)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("60 frames of 4-lobe GlassCapsulesShape must run cleanly within 400ms, took ${elapsedMs}ms", elapsedMs < 400L)
    }

    @Test
    fun liquidPopoverScopesSurfaceBackdropToChildrenPreventingPassthrough() {
        var capturedParentBackdrop: com.kyant.backdrop.Backdrop? = null
        var scopedChildBackdrop: com.kyant.backdrop.Backdrop? = null
        val scene = ImageComposeScene(width = 360, height = 480, density = Density(1f)) {
            val parentBackdrop = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color.Red)
            capturedParentBackdrop = parentBackdrop
            LiquidGlassTheme(opacity = .5f, dark = false) {
                LiquidModalHost(Modifier.size(360.dp, 480.dp)) {
                    LiquidPopover(
                        onDismissRequest = {},
                        backdrop = parentBackdrop,
                    ) {
                        scopedChildBackdrop = LocalLiquidSurfaceBackdrop.current
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(17_000_000L).close()
            assertTrue("LiquidPopover must scope a LayerBackdrop to LocalLiquidSurfaceBackdrop", scopedChildBackdrop != null)
            assertTrue("Scoped backdrop inside LiquidPopover must NOT fall back to parentBackdrop", scopedChildBackdrop !== capturedParentBackdrop)
        } finally {
            scene.close()
        }
    }

    @Test
    fun horizontalDragOnSliderTrackInsideSwipeBackMovesSliderWithoutPopping() {
        var sliderVal by mutableStateOf(0.8f)
        var poppedBack = false
        val scene = ImageComposeScene(width = 360, height = 320, density = Density(1f)) {
            LiquidGlassTheme(opacity = .5f, dark = false) {
                val bg = com.kyant.backdrop.backdrops.rememberSolidColorBackdrop(Color.White)
                LiquidSwipeBackNavigation(
                    page = "detail",
                    previousPage = "root",
                    depth = { if (it == "root") 0 else 1 },
                    contentKey = { it },
                    onNavigateBack = { poppedBack = true },
                    modifier = Modifier.size(360.dp, 320.dp),
                ) { _ ->
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        LiquidSlider(
                            value = { sliderVal },
                            onValueChange = { sliderVal = it },
                            valueRange = 0f..1f,
                            visibilityThreshold = 0.01f,
                            backdrop = bg,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Press, Offset(60f, 160f))
            scene.render(20_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(130f, 160f))
            scene.render(36_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(210f, 160f))
            scene.render(52_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Release, Offset(210f, 160f))
            for (ms in 68L..250L step 16L) scene.render(ms * 1_000_000L).close()
            assertFalse("Horizontal drag on slider track must not trigger LiquidSwipeBackNavigation pop", poppedBack)
            assertTrue("Dragging rightward on slider track should update slider value (got $sliderVal)", abs(sliderVal - 0.8f) > 0.05f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun overlappingCapsulesInGlassUnionStayWithinTangentEnvelopeWithoutInflation() {
        val mainCircle = androidx.compose.ui.geometry.Rect(0f, 0f, 48f, 48f)
        val overlapCircles = listOf(
            mainCircle,
            androidx.compose.ui.geometry.Rect(6f, 2f, 50f, 46f),
            androidx.compose.ui.geometry.Rect(14f, 2f, 58f, 46f),
            androidx.compose.ui.geometry.Rect(22f, 2f, 66f, 46f),
        )
        // Stacked N-way smin previously ballooned outside y = 0..48; pairwise bridged union stays strictly inside.
        for (cx in listOf(12f, 24f, 36f, 44f)) {
            assertTrue(
                "Top edge outside tangent envelope at x=$cx must remain exterior (> 0)",
                glassUnionAll(Offset(cx, -1f), overlapCircles, 14f) > 0f,
            )
            assertTrue(
                "Bottom edge outside tangent envelope at x=$cx must remain exterior (> 0)",
                glassUnionAll(Offset(cx, 49f), overlapCircles, 14f) > 0f,
            )
        }

        // Two nearby separated circles within spacing still fuse smoothly across the midpoint.
        val left = androidx.compose.ui.geometry.Rect(0f, 2f, 44f, 46f)
        val right = androidx.compose.ui.geometry.Rect(50f, 2f, 94f, 46f)
        assertTrue(
            "Nearby separated circles within fusion spacing should bridge smoothly at midpoint",
            glassUnionAll(Offset(47f, 24f), listOf(left, right), 14f) < 0f,
        )
    }

    @Test
    fun glassCapsulesShapeFourOverlappingLobesBuildsFastWithoutPathCombine() {
        val rects = listOf(
            Rect(20f, 10f, 164f, 154f),
            Rect(140f, 14f, 272f, 146f),
            Rect(250f, 14f, 382f, 146f),
            Rect(360f, 14f, 492f, 146f),
        )
        val shape = GlassCapsulesShape(rects, spacing = 42f)
        val density = Density(3f)
        var lastPath: androidx.compose.ui.graphics.Path? = null
        // Warm JVM once then time 20 outline evaluations.
        shape.createOutline(Size(600f, 180f), LayoutDirection.Ltr, density)
        val startNs = System.nanoTime()
        repeat(20) {
            val outline = shape.createOutline(Size(600f, 180f), LayoutDirection.Ltr, density)
            assertTrue(outline is androidx.compose.ui.graphics.Outline.Generic)
            lastPath = (outline as androidx.compose.ui.graphics.Outline.Generic).path
        }
        val avgMs = (System.nanoTime() - startNs) / 20_000_000.0
        val builtPath = requireNotNull(lastPath)
        val lastBounds = builtPath.getBounds()
        assertFalse("Multi-lobe outline bounds must be non-empty", lastBounds.isEmpty)
        assertTrue("Expected fast non-boolean 4-lobe outline < 5ms/call, got ${avgMs}ms", avgMs < 5.0)

        // All lobes and fusion necks must share clockwise winding so NonZero never cuts interior holes.
        val skiaPath = builtPath.asSkiaPath()
        for ((idx, r) in rects.withIndex()) {
            assertTrue("Lobe $idx center (${r.center}) must stay filled inside outline", skiaPath.contains(r.center.x, r.center.y))
        }
        for (i in 0 until rects.lastIndex) {
            val midX = (rects[i].center.x + rects[i + 1].center.x) * 0.5f
            val midY = (rects[i].center.y + rects[i + 1].center.y) * 0.5f
            assertTrue("Fusion bridge midpoint $i ($midX,$midY) must stay filled inside outline", skiaPath.contains(midX, midY))
        }

        val verticalCapsule = Rect(10f, 10f, 54f, 120f)
        val sideCircle = Rect(60f, 40f, 104f, 84f)
        val pairOutline = GlassCapsuleShape(verticalCapsule, sideCircle, 14f)
            .createOutline(Size(140f, 140f), LayoutDirection.Ltr, density) as androidx.compose.ui.graphics.Outline.Generic
        val pairSkia = pairOutline.path.asSkiaPath()
        assertTrue("Vertical capsule center must be filled", pairSkia.contains(verticalCapsule.center.x, verticalCapsule.center.y))
        assertTrue("Adjacent circle center must be filled", pairSkia.contains(sideCircle.center.x, sideCircle.center.y))
        assertTrue("Bridge midpoint must be filled", pairSkia.contains(57f, 62f))
    }

    @Test
    fun allSixLiquidListStylesAndCollapsibleSectionRenderCleanly() {
        var expandedState by mutableStateOf(true)
        var rowComposeCount = 0
        for (style in LiquidListStyle.entries) {
            rowComposeCount = 0
            val scene = ImageComposeScene(width = 360, height = 280) {
                LiquidListContainer(style = style) {
                    LiquidListSection(
                        title = "Section-${style.name}",
                        footer = "Footer-${style.name}",
                        expanded = expandedState,
                        onToggleExpanded = { expandedState = !expandedState },
                    ) {
                        navigationRow("RowA", {})
                        navigationRow("RowB", { rowComposeCount++ })
                    }
                }
            }
            try {
                scene.render(1_000_000L).close()
                expandedState = false
                for (ms in 20L..320L step 30L) scene.render(ms * 1_000_000L).close()
                expandedState = true
                for (ms in 350L..650L step 30L) scene.render(ms * 1_000_000L).close()
            } finally {
                scene.close()
            }
        }
        assertEquals(0, rowComposeCount)
    }

    @Test
    fun singlePressHighlightPulsesOnceWithoutDoubleFlashingAfterRelease() {
        val states = mutableListOf<Boolean>()
        val source = androidx.compose.foundation.interaction.MutableInteractionSource()
        val scene = ImageComposeScene(width = 200, height = 100) {
            val active by rememberSinglePressHighlight(source, minFlashMillis = 100L)
            if (states.lastOrNull() != active) states += active
            Box(Modifier.size(200.dp, 100.dp))
        }
        try {
            scene.render(1_000_000L).close()
            val press = androidx.compose.foundation.interaction.PressInteraction.Press(Offset(50f, 50f))
            assertTrue(source.tryEmit(press))
            scene.render(20_000_000L).close()
            Thread.sleep(125L)
            assertTrue(source.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Release(press)))
            for (ms in 155L..260L step 25L) scene.render(ms * 1_000_000L).close()
            // Transition must be strictly false -> true -> false (never true -> false -> true -> false).
            assertEquals(listOf(false, true, false), states)
        } finally {
            scene.close()
        }
    }

    @Test
    fun menuMetricsWidthMatchesNarrowerIosProportionsAndTabsPickerRenders() {
        val metrics = LiquidMenuMetrics(411.dp)
        assertEquals(216f, metrics.width.value, 0.5f)

        var selected by mutableStateOf("overview")
        val scene = ImageComposeScene(width = 360, height = 120) {
            LiquidTabsPicker(
                options = listOf(
                    LiquidTabOption("overview", "概览"),
                    LiquidTabOption("design", "设计"),
                    LiquidTabOption("test", "测试"),
                ),
                selectedId = selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        try {
            scene.render(1_000_000L).close()
            scene.render(16_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Press, Offset(180f, 30f))
            scene.render(32_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Release, Offset(180f, 30f))
            for (ms in 48L..240L step 24L) scene.render(ms * 1_000_000L).close()
            assertEquals("design", selected)
        } finally {
            scene.close()
        }
    }

    @Test
    fun rectangleGlassSurfaceDarkShadowAndMeshGradientRenderCleanly() {
        for (dark in listOf(false, true)) {
            val scene = ImageComposeScene(width = 240, height = 200) {
                LiquidGlassTheme(dark = dark) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        LiquidMeshGradient(
                            width = 2,
                            height = 2,
                            colors = listOf(Color(0xff007aff), Color(0xff00c7be), Color(0xffaf52de), Color(0xffff9500)),
                            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
                        )
                        LiquidGlassSurface(
                            backdrop = backdrop,
                            shape = RectangleShape,
                            tint = Color(0xff00c7be),
                            modifier = Modifier.align(Alignment.Center).size(140.dp, 54.dp),
                        ) {
                            BasicText("Rect")
                        }
                    }
                }
            }
            try {
                scene.render(1_000_000L).close()
                scene.render(32_000_000L).close()
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun sliderLeftwardDragMatchesSymmetricTravelAndSettlesOnRelease() {
        var value by mutableStateOf(0.8f)
        val scene = ImageComposeScene(width = 320, height = 100) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(Color.White))
                LiquidSlider(
                    value = { value },
                    onValueChange = { value = it },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.005f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        try {
            scene.render(1_000_000L).close()
            val startX = 250f
            scene.sendPointerEvent(PointerEventType.Press, Offset(startX, 40f))
            scene.render(16_000_000L).close()
            for (step in 1..6) {
                scene.sendPointerEvent(PointerEventType.Move, Offset(startX - step * 25f, 40f))
                scene.render((16L + step * 16L) * 1_000_000L).close()
            }
            scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 40f))
            for (ms in 120L..320L step 24L) scene.render(ms * 1_000_000L).close()
            assertTrue("Leftward drag must move value below 0.6 (was $value)", value < 0.6f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun toggleYieldsToVerticalScrollWithoutTogglingState() {
        var checked by mutableStateOf(false)
        val scene = ImageComposeScene(width = 200, height = 200) {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(Color.White))
                LiquidToggle(
                    selected = { checked },
                    onSelect = { checked = it },
                    backdrop = backdrop,
                )
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 100f))
            scene.render(16_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(100f, 145f))
            scene.render(32_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(100f, 190f))
            scene.render(48_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 190f))
            scene.render(80_000_000L).close()
            assertFalse("Vertical scroll past touch slop must yield without toggling", checked)
        } finally {
            scene.close()
        }
    }

    @Test
    fun tabsPickerSupportsDarkLightRenderingAndClickSemantics() {
        for (dark in listOf(false, true)) {
            var selected by mutableStateOf("a")
            val scene = ImageComposeScene(width = 300, height = 100) {
                LiquidGlassTheme(dark = dark) {
                    LiquidTabsPicker(
                        options = listOf(
                            LiquidTabOption("a", "First"),
                            LiquidTabOption("b", "Second"),
                        ),
                        selectedId = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }
            try {
                scene.render(1_000_000L).close()
                scene.render(16_000_000L).close()
                scene.sendPointerEvent(PointerEventType.Press, Offset(220f, 32f))
                scene.render(32_000_000L).close()
                scene.sendPointerEvent(PointerEventType.Release, Offset(220f, 32f))
                for (ms in 48L..200L step 24L) scene.render(ms * 1_000_000L).close()
                assertEquals("b", selected)
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun modalHostBlocksBackgroundMenuWhileModalOpenAndAllowsSheetChildMenu() {
        val bgMenu = LiquidMenuState { }
        val sheetMenu = LiquidMenuState { }
        var showSheet by mutableStateOf(true)
        var triggerPos = Offset.Zero
        val scene = ImageComposeScene(width = 360, height = 640) {
            val bg = rememberLayerBackdrop()
            LiquidModalHost(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().layerBackdrop(bg).background(Color.White))
                LiquidAnchoredMenuHost(
                    state = bgMenu,
                    backdrop = bg,
                    items = listOf(LiquidMenuItem("bg", "BackgroundAction")),
                ) {
                    Box(
                        Modifier
                            .size(120.dp, 40.dp)
                            .onGloballyPositioned { coords ->
                                val r = coords.localToRoot(Offset(40f, 20f))
                                triggerPos = r
                            }
                            .liquidMenuTrigger(bgMenu),
                    )
                }
                if (showSheet) {
                    LiquidSheet(
                        onDismissRequest = { showSheet = false },
                        backdrop = bg,
                        title = "Sheet",
                    ) {
                        LiquidAnchoredMenuHost(
                            state = sheetMenu,
                            backdrop = LocalLiquidModalBackdrop.current ?: bg,
                            items = listOf(LiquidMenuItem("sh", "SheetAction")),
                        ) {
                            Box(Modifier.size(120.dp, 40.dp).liquidMenuTrigger(sheetMenu)) {
                                BasicText("OpenSheetMenu")
                            }
                        }
                    }
                }
            }
        }
        try {
            for (ms in 1L..80L step 16L) scene.render(ms * 1_000_000L).close()
            // Tap where background menu trigger sits — modal scrim must block bgMenu from opening.
            scene.sendPointerEvent(PointerEventType.Press, triggerPos)
            scene.render(96_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Release, triggerPos)
            scene.render(120_000_000L).close()
            assertFalse("Background menu must not open while modal sheet is active", bgMenu.expanded)
            sheetMenu.show()
            for (ms in 140L..220L step 20L) scene.render(ms * 1_000_000L).close()
            assertTrue("Sheet child menu must remain openable above the sheet", sheetMenu.expanded)
        } finally {
            scene.close()
        }
    }

    @Test
    fun swipeActionsWithoutLeadingAllowsRightwardSwipePassthrough() {
        var boxOffsetX = 0f
        val scene = ImageComposeScene(width = 360, height = 100) {
            LiquidSwipeActions(
                rowKey = "row1",
                leading = emptyList(),
                trailing = listOf(LiquidSwipeAction("Delete", LiquidSymbol.Trash, Color.Red) {}),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .onGloballyPositioned { coords -> boxOffsetX = coords.localToRoot(Offset.Zero).x },
                ) {
                    BasicText("Row")
                }
            }
        }
        try {
            scene.render(1_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Press, Offset(60f, 30f))
            scene.render(16_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(180f, 30f))
            scene.render(32_000_000L).close()
            scene.sendPointerEvent(PointerEventType.Release, Offset(180f, 30f))
            scene.render(64_000_000L).close()
            assertEquals(0f, boxOffsetX, 0.5f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun horizontalBarChartAndFormSectionMenuPickerRenderCleanly() {
        var picked by mutableStateOf("Auto")
        val scene = ImageComposeScene(width = 380, height = 340) {
            val bg = rememberLayerBackdrop()
            LiquidModalHost(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().layerBackdrop(bg).background(Color.White))
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    LiquidBarChart(
                        points = listOf(
                            LiquidChartSeriesPoint("Jan", "A", 40f, Color(0xff007aff)),
                            LiquidChartSeriesPoint("Feb", "A", 80f, Color(0xff00c7be)),
                        ),
                        horizontal = true,
                    )
                    LiquidFormSection(title = "Options") {
                        row {
                            LiquidMenuPicker(
                                label = "Mode",
                                options = listOf("Auto", "Manual"),
                                selected = picked,
                                onSelect = { picked = it },
                                tint = Color(0xff007aff),
                            )
                        }
                    }
                }
            }
        }
        try {
            for (ms in 1L..80L step 20L) scene.render(ms * 1_000_000L).close()
        } finally {
            scene.close()
        }
    }
}
