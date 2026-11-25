package com.kyant.backdrop.morph

import androidx.compose.ui.geometry.Offset
import com.kyant.backdrop.components.GlassMenuContourTokens
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidMenuOpeningTest {
    private val source = RectBounds(248f, 70f, 128f, 44f, 22f)
    private val menu = RectBounds(132f, 70f, 244f, 510f, 32f)
    private val config = LiquidMorphConfig()

    private fun opening(capture: (LiquidMorphPhysicsState) -> Unit) {
        var state = LiquidMorphPhysicsState.initial(false).copy(targetExpanded = true)
        repeat(410) {
            capture(state)
            state = advanceLiquidMorphPhysics(state, config, .004f)
        }
    }

    @Test fun samePathMirrorsInEitherAxisAndReturnsToTheTarget() {
        fun mirror(rect: RectBounds, x: Boolean, y: Boolean) = rect.copy(
            x = if (x) 800f - rect.x - rect.w else rect.x,
            y = if (y) 1000f - rect.y - rect.h else rect.y,
        )
        var lastWidth = 0f
        var farthest = 0f
        opening { state ->
            val original = evaluateLiquidMenuOpening(state, source, menu, LiquidMorphAlignment.TopTrailing, source.x + 32f)
            for (alignment in LiquidMorphAlignment.entries) {
                val x = alignment == LiquidMorphAlignment.TopLeading || alignment == LiquidMorphAlignment.BottomLeading
                val y = alignment == LiquidMorphAlignment.BottomLeading || alignment == LiquidMorphAlignment.BottomTrailing
                val slot = source.x + 32f
                val body = evaluateLiquidMenuOpening(state, mirror(source, x, y), mirror(menu, x, y), alignment, if (x) 800f - slot else slot)
                assertEquals(original.w, body.w, .001f)
                assertEquals(original.h, body.h, .001f)
                assertEquals(if (x) 800f - original.cx else original.cx, body.cx, .001f)
                assertEquals(if (y) 1000f - original.cy else original.cy, body.cy, .001f)
                assertEquals(if (x) -original.tipX else original.tipX, body.tipX, .001f)
            }
            farthest = maxOf(farthest, original.cy + original.h / 2f)
            lastWidth = original.w
        }
        assertEquals(menu.w, lastWidth, .01f)
        assertTrue(farthest > menu.y + menu.h + 1f)
    }

    @Test fun toolbarTakesOverOnlyAfterTheReturningRimLands() {
        for (alignment in LiquidMorphAlignment.entries) {
            val config = LiquidMorphConfig(alignment = alignment)
            var physics = LiquidMorphPhysicsState.initial(true).copy(targetExpanded = false)
            var stillMovingAfterMerge = false
            var lastAligned = false
            repeat(200) {
                physics = advanceLiquidMorphPhysics(physics, config, .008f)
                val frame = evaluateLiquidMorphFrame(physics, config, 880f, 1300f, 290f, 115f, 57.5f,
                    650f, 750f, 84f, 2.625f)
                lastAligned = frame.sourceAligned(.35f)
                if (!frame.body.active && frame.collapsedAlpha >= .99f && !lastAligned) stillMovingAfterMerge = true
            }
            assertTrue(stillMovingAfterMerge)
            assertTrue(lastAligned)
        }
    }

    private fun section(body: SdfBoxPrimitive, depth: Float): IntRange {
        val y = body.cy + (depth - .5f) * body.h
        val inside = (0..500).filter { x ->
            body.containsPoint(Offset(body.cx + (x / 500f - .5f) * body.w, y), 0f)
        }
        return inside.first()..inside.last()
    }

    private fun span(body: SdfBoxPrimitive, depth: Float) = section(body, depth).count() / 501f

    @Test fun detachedCrownIsRoundWithAFullerBellyEvenAtZeroLean() {
        val states = mutableListOf<LiquidMorphPhysicsState>()
        opening { states += it }
        val target = .33f * menu.w
        fun shape(state: LiquidMorphPhysicsState, tokens: GlassMenuContourTokens) =
            evaluateLiquidMenuOpening(state, source, menu, config.alignment, source.x + 32f, contour = tokens)
        val tokens = GlassMenuContourTokens()
        val growing = states.drop(states.indices.minBy { shape(states[it], tokens).w })
        val state = growing.minBy { abs(shape(it, tokens).w - target) }
        val body = shape(state, tokens)
        // Cross-sections at the same body width as frame 37 in the Photos capture.
        assertEquals(.33f, body.w / menu.w, .025f)
        assertEquals(1.34f, body.h / body.w, .08f)
        assertEquals(.35f, span(body, .05f), .045f)
        assertEquals(.75f, span(body, .25f), .045f)
        assertEquals(.94f, span(body, .75f), .035f)
        val crown = section(body, .03f)
        assertEquals(.57f, (crown.first + crown.last) / 1000f, .04f)
        val centered = shape(state, tokens.copy(maximumLean = 0f))
        assertEquals(0f, centered.tipX, 0f)
        assertTrue(span(centered, .75f) > span(centered, .25f) + .10f)
    }

    @Test fun residualTailDrainsOverTheSameIntervalAsTheCorners() {
        val tokens = GlassMenuContourTokens(cornerStart = .39f, cornerEnd = .87f)
        // Hold the advancing edge still so only corner opening affects the outline.
        fun shape(lag: Float) = evaluateLiquidMenuOpening(
            LiquidMorphPhysicsState.initial(true).copy(widthSpring = SpringScalar(.84f), shapeLag = SpringScalar(lag)),
            source, menu, config.alignment, source.x + 32f, contour = tokens,
        )
        val round = shape(tokens.cornerStart)
        val half = shape((tokens.cornerStart + tokens.cornerEnd) * .5f)
        val almost = shape(tokens.cornerEnd - .05f)
        val flat = shape(tokens.cornerEnd)
        assertTrue(round.crownBias > 0f && round.tipX > 0f)
        assertEquals((round.r - menu.r) * .5f, half.r - menu.r, .001f)
        assertEquals(round.crownBias * .5f, half.crownBias, .0001f)
        assertEquals(round.tipX * .5f, half.tipX, .001f)
        assertTrue(almost.crownBias > 0f && almost.tipX > 0f && almost.r > menu.r)
        assertTrue(span(round, .02f) < span(half, .02f) && span(half, .02f) < span(flat, .02f))
        assertEquals(menu.r, flat.r, .001f)
        assertTrue(flat.hasUniformCorners)
    }

    @Test fun reversingFromTheDropletRetainsItsCrownOnTheNextStep() {
        var state = LiquidMorphPhysicsState.initial(false).copy(targetExpanded = true)
        repeat(18) { state = advanceLiquidMorphPhysics(state, config, .004f) }
        fun frame() = evaluateLiquidMorphFrame(state, config, 310f, 570f, source.w, source.h,
            source.r, menu.w, menu.h, menu.r, 1f)
        val outgoing = frame().body
        state = advanceLiquidMorphPhysics(state.copy(targetExpanded = false), config, .001f)
        val incoming = frame().body
        assertTrue(outgoing.crownBias > .05f)
        assertEquals(outgoing.crownBias, incoming.crownBias, .015f)
        assertEquals(outgoing.tipX, incoming.tipX, 1f)
        assertEquals(outgoing.cy, incoming.cy, 4f)
        repeat(400) { state = advanceLiquidMorphPhysics(state, config, .004f) }
        assertTrue(frame().sourceAligned(.35f))
    }

    @Test fun lowerHalfFillsOutAsTheUpperTipMerges() {
        val bodies = mutableListOf<SdfBoxPrimitive>()
        opening { bodies += evaluateLiquidMenuOpening(it, source, menu, LiquidMorphAlignment.TopTrailing, source.x + 32f) }
        val growing = bodies.drop(bodies.indices.minBy { bodies[it].w })
        fun nearest(fraction: Float) = growing.minBy { abs(it.w / menu.w - fraction) }
        val early = nearest(.467f)
        val late = nearest(.787f)
        // Cross-sections from frames 86 and 89 of the tall-menu reference, normalized by each frame's width.
        assertEquals(.923f, span(early, .75f), .035f)
        assertEquals(.967f, span(late, .75f), .035f)
        assertTrue(early.tipX / early.w > late.tipX / late.w)
    }

    @Test fun wideBarOpeningUpwardUsesTheSameReboundAtDifferentDensities() {
        // Source and menu measured from the settled rims of the upward-opening reference.
        for (scale in listOf(1f, 1.75f, 3f)) {
            val bar = RectBounds(0f, 79f * scale, 356f * scale, 48f * scale, 24f * scale)
            val popup = RectBounds(0f, 0f, 228f * scale, 127f * scale, 28f * scale)
            var minimum = bar.y
            var top = bar.y
            opening { state ->
                val frame = evaluateLiquidMenuOpening(state, bar, popup, LiquidMorphAlignment.BottomLeading)
                top = frame.cy - frame.h / 2f
                minimum = minOf(minimum, top)
            }
            // The iOS top rim reaches 23 px above rest for a 211 px settled panel.
            assertEquals(23f / 211f, (popup.y - minimum) / popup.h, .015f)
            assertEquals(popup.y, top, .01f)
        }
    }

    @Test fun rapidOpenCloseReopenNeverCollapsesSourceContentOrSpawnsDeadBody() {
        val bar = RectBounds(759f, 143f, 289f, 116f, 58f)
        val panel = RectBounds(390f, 143f, 658f, 920f, 84f)
        var state = LiquidMorphPhysicsState.initial(false).copy(targetExpanded = true)
        fun eval(s: LiquidMorphPhysicsState) = evaluateLiquidMorphFrame(
            s, config, 800f, 1100f, bar.w, bar.h, bar.r, panel.w, panel.h, panel.r, 2.625f,
            sourceAnchorX = 980f,
        )
        // Open partway, close almost all the way, then reopen before settling.
        repeat(35) { state = advanceLiquidMorphPhysics(state, config, .008f) }
        state = state.copy(targetExpanded = false)
        repeat(80) {
            state = advanceLiquidMorphPhysics(state, config, .008f)
            val f = eval(state)
            if (f.collapsedAlpha > 0.05f) {
                assertTrue("sourceContent shrank during interrupted close: ${f.sourceContent.w}", f.sourceContent.w >= bar.w - .1f)
                assertTrue("sourceContent height shrank: ${f.sourceContent.h}", f.sourceContent.h >= bar.h - .1f)
            }
            assertTrue("collapsed envelope shrank below height: ${f.boundHalfW * 2}", f.boundHalfW * 2f >= bar.h - .1f)
        }
        state = state.copy(targetExpanded = true)
        repeat(50) {
            state = advanceLiquidMorphPhysics(state, config, .008f)
            val f = eval(state)
            if (f.collapsedAlpha > 0.05f) {
                assertTrue("sourceContent shrank on reopen: ${f.sourceContent.w}", f.sourceContent.w >= bar.w - .1f)
                assertTrue("sourceContent missing on reopen", f.sourceContent.active)
            }
            if (f.body.active) assertTrue("dead small body on reopen: ${f.body.w}", f.body.w >= bar.h * .5f)
        }
    }

    @Test fun borderlessPopupNeverSpawnsCollapsedPillAndFitsContentInsideHeight() {
        val borderless = LiquidMorphConfig(
            alignment = LiquidMorphAlignment.TopLeading,
            openResponse = .27f,
            closeResponse = .31f,
            showsCollapsedSurface = false,
        )
        var state = LiquidMorphPhysicsState.initial(false).copy(targetExpanded = true)
        fun eval(s: LiquidMorphPhysicsState) = evaluateLiquidMorphFrame(
            s, borderless, 780f, 1450f, 111f, 48f, 24f, 650f, 1340f, 84f, 2.625f,
        )
        repeat(120) {
            state = advanceLiquidMorphPhysics(state, borderless, .008f)
            val f = eval(state)
            assertTrue("borderless should never activate pill", !f.pill.active)
            assertTrue("borderless should never activate sourceContent", !f.sourceContent.active)
            if (f.expandedAlpha > 0.01f && f.body.active && f.body.h >= 1340f * .58f) {
                assertTrue("scaled expanded content overflowed body height: scaleY=${f.expandedScaleY} bodyH=${f.body.h}",
                    1340f * f.expandedScaleY <= f.body.h + 1f)
            }
        }
        state = state.copy(targetExpanded = false)
        repeat(150) {
            state = advanceLiquidMorphPhysics(state, borderless, .008f)
            val f = eval(state)
            assertTrue("borderless close should not protrude a separate pill ear", !f.pill.active)
        }
        assertTrue(eval(state).sourceAligned(.35f))
    }

    @Test fun menuClosePhysicsSettlesFasterThanLegacyBaseline() {
        fun closeMillis(cfg: LiquidMorphConfig): Int {
            var state = LiquidMorphPhysicsState.initial(true).copy(targetExpanded = false)
            var step = 0
            while (step < 150) {
                state = advanceLiquidMorphPhysics(state, cfg, .008f)
                step++
                if (abs(state.morph.value) < 0.01f && abs(state.sizeSpring.value) < 0.01f) {
                    return step * 8
                }
            }
            return step * 8
        }
        val legacy = LiquidMorphConfig(closeResponse = .40f, closeDamping = .90f)
        val tuned = LiquidMorphConfig()
        assertTrue("default closeResponse=${tuned.closeResponse} should be < legacy 0.40", tuned.closeResponse < .30f)
        assertTrue(closeMillis(tuned) < closeMillis(legacy))
        assertTrue("tuned menu close should settle under 360ms, got ${closeMillis(tuned)}", closeMillis(tuned) <= 360)
    }

    @Test fun menuOpeningClearsBlurPromptlyAndClosesCrisply() {
        var openState = LiquidMorphPhysicsState.initial(false).copy(targetExpanded = true)
        var blurAt360ms = Float.MAX_VALUE
        repeat(45) {
            openState = advanceLiquidMorphPhysics(openState, config, .008f)
            val f = evaluateLiquidMorphFrame(openState, config, 800f, 1000f, source.w, source.h,
                source.r, menu.w, menu.h, menu.r, 1f)
            blurAt360ms = f.expandedBlurPx
        }
        assertTrue("expandedBlurPx should clear by 360ms, got $blurAt360ms", blurAt360ms <= 0.05f)

        var closeState = LiquidMorphPhysicsState.initial(true).copy(targetExpanded = false)
        var closeLandedMillis = Int.MAX_VALUE
        for (step in 1..60) {
            closeState = advanceLiquidMorphPhysics(closeState, config, .008f)
            val f = evaluateLiquidMorphFrame(closeState, config, 800f, 1000f, source.w, source.h,
                source.r, menu.w, menu.h, menu.r, 1f)
            if (f.sourceAligned(.35f) || (!f.body.active && f.collapsedAlpha >= .99f)) {
                closeLandedMillis = step * 8
                break
            }
        }
        assertTrue("menu close should land by 420ms, got ${closeLandedMillis}ms", closeLandedMillis <= 420)
    }
}
