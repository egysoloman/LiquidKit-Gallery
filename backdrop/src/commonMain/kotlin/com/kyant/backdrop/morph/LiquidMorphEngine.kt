package com.kyant.backdrop.morph

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.components.GlassMenuContourTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Shared corner/edge alignment matching SwiftUI `Alignment` for `GlassEffectContainer` morphs.
 */
enum class LiquidMorphAlignment {
    TopTrailing,
    BottomLeading,
    TopLeading,
    BottomTrailing,
}

@Immutable
data class LiquidMorphConfig(
    val alignment: LiquidMorphAlignment = LiquidMorphAlignment.TopTrailing,
    val triggerBiasX: Float = 0.04f,
    val blendStrength: Float = 1.0f,
    val openResponse: Float = 0.30f,
    val openDamping: Float = 0.86f,
    val closeResponse: Float = 0.22f,
    val closeDamping: Float = 0.87f,
    val arcBend: Float = 0.12f,
    val refraction: Float = 1.0f,
    val bevelWidthDp: Float = 14f,
    val refractionAmountDp: Float = 26f,
    val dispersion: Float = 0.65f,
    val blurRadiusDp: Float = 2.5f,
    val specular: Float = 1.25f,
    val tintAlpha: Float = 0.05f,
    val collapsedSurfaceColor: Color = Color.Transparent,
    val expandedSurfaceColor: Color = Color.Transparent,
    val expandedOffsetXDp: Float = 0f,
    val expandedOffsetYDp: Float = 0f,
    val menuOpeningRebound: Boolean = true,
    val showsCollapsedSurface: Boolean = true,
)

@Immutable
data class SpringScalar(
    val value: Float,
    val velocity: Float = 0f,
)

/**
 * Positions and velocities are retained when a gesture reverses the transition.
 */
@Immutable
data class LiquidMorphPhysicsState(
    val morph: SpringScalar,
    val sizeSpring: SpringScalar,
    val shapeLag: SpringScalar,
    val closeBias: SpringScalar,
    val pressGlow: Float,
    val targetExpanded: Boolean,
    val dockLift: SpringScalar = SpringScalar(0f),
    val widthSpring: SpringScalar = sizeSpring,
    val openingContour: SpringScalar = SpringScalar(if (targetExpanded) 1f else 0f),
) {
    val isSettled: Boolean
        get() {
            val target = if (targetExpanded) 1f else 0f
            val biasTarget = if (targetExpanded) 0f else 1f
            return abs(morph.value - target) < 0.0005f &&
                abs(morph.velocity) < 0.001f &&
                abs(sizeSpring.value - target) < 0.0005f &&
                abs(sizeSpring.velocity) < 0.001f &&
                abs(widthSpring.value - target) < 0.0005f &&
                abs(widthSpring.velocity) < 0.001f &&
                abs(openingContour.value - target) < 0.001f &&
                abs(shapeLag.value - target) < 0.0005f &&
                abs(shapeLag.velocity) < 0.001f &&
                abs(closeBias.value - biasTarget) < 0.001f &&
                abs(dockLift.value) < 0.0005f &&
                abs(dockLift.velocity) < 0.001f &&
                pressGlow < 0.002f
        }

    companion object {
        fun initial(expanded: Boolean): LiquidMorphPhysicsState {
            val v = if (expanded) 1f else 0f
            return LiquidMorphPhysicsState(
                morph = SpringScalar(v, 0f),
                sizeSpring = SpringScalar(v, 0f),
                shapeLag = SpringScalar(v, 0f),
                closeBias = SpringScalar(if (expanded) 0f else 1f, 0f),
                pressGlow = 0f,
                targetExpanded = expanded,
            )
        }
    }
}

@Immutable
data class SdfBoxPrimitive(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val r: Float,
    val active: Boolean,
    val radiusY: Float = r,
    val tipX: Float = 0f,
    val tipAtBottom: Boolean = false,
    val farRadiusY: Float = radiusY,
    val crownBias: Float = 0f,
) {
    internal val hasUniformCorners get() = radiusY == r && farRadiusY == r && tipX == 0f && crownBias == 0f
}

@Immutable
data class RectBounds(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val r: Float,
) {
    val cx: Float get() = x + w * 0.5f
    val cy: Float get() = y + h * 0.5f
}

@Immutable
data class LiquidMorphFrame(
    val pill: SdfBoxPrimitive,
    val body: SdfBoxPrimitive,
    val boundCx: Float,
    val boundCy: Float,
    val boundHalfW: Float,
    val boundHalfH: Float,
    val smoothK: Float,
    val pressGlow: Float,
    val pressGlowX: Float,
    val pressGlowY: Float,
    val pressGlowRadius: Float,
    val collapsedAlpha: Float,
    val collapsedBlurPx: Float,
    val collapsedScale: Float,
    val expandedAlpha: Float,
    val expandedBlurPx: Float,
    val expandedScaleX: Float,
    val expandedScaleY: Float,
    val startRect: RectBounds,
    val endRect: RectBounds,
    val sourceContent: SdfBoxPrimitive = pill,
    val materialProgress: Float = expandedAlpha,
    val contentRefraction: Float = 0f,
    val showsCollapsedSurface: Boolean = true,
)

/** Both edges and the label have to land before the live toolbar can take over. */
internal fun LiquidMorphFrame.sourceAligned(tolerancePx: Float): Boolean {
    if (body.active || collapsedAlpha < .999f) return false
    if (!showsCollapsedSurface) return !pill.active
    if (!pill.active) return false
    val xError = abs(pill.cx - startRect.cx) + .5f * abs(pill.w - startRect.w)
    val yError = abs(pill.cy - startRect.cy) + .5f * abs(pill.h - startRect.h)
    return max(xError, yError) <= tolerancePx
}

private fun clamp(v: Float, lo: Float = 0f, hi: Float = 1f): Float = v.coerceIn(lo, hi)
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp((x - edge0) / max(1e-5f, edge1 - edge0))
    return t * t * (3f - 2f * t)
}

private fun smootherstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = clamp((x - edge0) / max(1e-5f, edge1 - edge0))
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

/**
 * Exact 2nd-order SwiftUI `Spring(response, dampingFraction)` 4-substep RK4 integrator.
 */
fun stepSpring(
    state: SpringScalar,
    target: Float,
    response: Float,
    dampingFraction: Float,
    dtSec: Float,
): SpringScalar {
    val r = max(0.06f, response)
    val z = max(0.25f, dampingFraction)
    val wn = (2.0 * PI / r).toFloat()
    val k = wn * wn
    val c = 2f * z * wn

    val steps = 4
    val h = dtSec.coerceIn(0f, 0.05f) / steps
    var x = state.value
    var v = state.velocity

    repeat(steps) {
        val k1v = k * (target - x) - c * v
        val k1x = v

        val x2 = x + 0.5f * h * k1x
        val v2 = v + 0.5f * h * k1v
        val k2v = k * (target - x2) - c * v2
        val k2x = v2

        val x3 = x + 0.5f * h * k2x
        val v3 = v + 0.5f * h * k2v
        val k3v = k * (target - x3) - c * v3
        val k3x = v3

        val x4 = x + h * k3x
        val v4 = v + h * k3v
        val k4v = k * (target - x4) - c * v4
        val k4x = v4

        x += (h / 6f) * (k1x + 2f * k2x + 2f * k3x + k4x)
        v += (h / 6f) * (k1v + 2f * k2v + 2f * k3v + k4v)
    }

    return if (abs(target - x) < 0.0003f && abs(v) < 0.0006f) {
        SpringScalar(target, 0f)
    } else {
        SpringScalar(x, v)
    }
}

fun advanceLiquidMorphPhysics(
    state: LiquidMorphPhysicsState,
    config: LiquidMorphConfig,
    dtSec: Float,
): LiquidMorphPhysicsState {
    val target = if (state.targetExpanded) 1f else 0f
    val response = if (state.targetExpanded) config.openResponse else config.closeResponse
    val damping = if (state.targetExpanded) config.openDamping else config.closeDamping

    val menuOpening = state.targetExpanded && config.menuOpeningRebound
    val sizeResp = response * when {
        menuOpening -> .79f
        state.targetExpanded -> .94f
        else -> .93f
    }
    val sizeDamp = if (menuOpening) (damping - .13f).coerceAtLeast(.62f) else min(.96f, damping + .02f)

    val lagResp = if (state.targetExpanded) response * 1.24f else response * 0.68f
    val lagDamp = min(0.98f, damping + 0.06f)

    val nextMorph = stepSpring(state.morph, target, response, damping, dtSec)
    val nextSize = stepSpring(state.sizeSpring, target, sizeResp, sizeDamp, dtSec)
    // On opening, the far vertical edge turns back slightly before the side edge.
    val nextWidth = if (config.menuOpeningRebound) stepSpring(
        state.widthSpring, target, if (menuOpening) response * .94f else sizeResp, sizeDamp, dtSec,
    ) else nextSize
    val nextLag = stepSpring(state.shapeLag, target, lagResp, lagDamp, dtSec)
    // Both contours coincide at rest. Cross over continuously when a gesture reverses mid-flight.
    val nextContour = if (abs(state.morph.value - target) > .9995f && abs(state.morph.velocity) < .01f) {
        SpringScalar(target)
    } else if (state.openingContour.value == target && state.openingContour.velocity == 0f) {
        state.openingContour
    } else stepSpring(state.openingContour, target, .16f, 1f, dtSec)
    val nextCloseBias = stepSpring(
        state.closeBias,
        if (state.targetExpanded) 0f else 1f,
        0.22f,
        0.95f,
        dtSec,
    )
    // Closing momentum carries the glass past its resting edge. The spring keeps
    // returning after the droplet has disappeared, including on a quick reversal.
    val inwardSpeed = max(0f, -(state.morph.velocity + nextMorph.velocity) * 0.5f)
    val distanceClosed = 1f - (state.morph.value + nextMorph.value) * 0.5f
    val liftTarget = 0.175f * config.closeResponse * inwardSpeed *
        smoothstep(0.12f, 0.72f, distanceClosed)
    val nextDockLift = stepSpring(
        state.dockLift, liftTarget.coerceAtMost(0.45f), config.closeResponse * 0.58f, 1f, dtSec,
    )
    val nextGlow = if (state.pressGlow > 0.002f) {
        state.pressGlow * exp(-dtSec * 7.5f)
    } else {
        0f
    }

    return LiquidMorphPhysicsState(
        morph = nextMorph,
        sizeSpring = nextSize,
        shapeLag = nextLag,
        closeBias = nextCloseBias,
        pressGlow = nextGlow,
        targetExpanded = state.targetExpanded,
        dockLift = nextDockLift,
        widthSpring = nextWidth,
        openingContour = nextContour,
    )
}

private val DefaultMenuContour = GlassMenuContourTokens()

/** Evaluates the opening outline in pixels. A borderless source supplies its laid-out hit bounds. */
fun evaluateLiquidMenuOpening(
    state: LiquidMorphPhysicsState,
    source: RectBounds,
    menu: RectBounds,
    alignment: LiquidMorphAlignment,
    triggerX: Float = source.cx,
    verticalMargin: Float = min(source.h, menu.h) * .5f,
    allowRebound: Boolean = true,
    contour: GlassMenuContourTokens = DefaultMenuContour,
): SdfBoxPrimitive {
    if (minOf(source.w, source.h, menu.w, menu.h) <= 0f) {
        return SdfBoxPrimitive(source.cx, source.cy, 0f, 0f, 0f, false)
    }
    val facing = if (alignment == LiquidMorphAlignment.TopTrailing || alignment == LiquidMorphAlignment.BottomTrailing) 1f else -1f
    val bottom = alignment == LiquidMorphAlignment.BottomLeading || alignment == LiquidMorphAlignment.BottomTrailing
    val widthProgress = if (allowRebound) state.widthSpring.value else state.widthSpring.value.coerceIn(0f, 1f)
    val p = widthProgress.coerceIn(0f, 1f)
    val lag = state.shapeLag.value.coerceIn(0f, 1f)
    val seed = min(source.w, source.h)
    val tall = smoothstep(1.05f, 1.65f, menu.h / menu.w)
    val barRemaining = 1f - smootherstep(0f, lerp(.34f, .20f, tall), p)
    val width = (lerp(seed, menu.w, widthProgress) + (source.w - seed) * barRemaining).coerceAtLeast(2f)
    val sourceEdge = source.cx + facing * source.w * .5f
    val finalEdge = menu.cx + facing * menu.w * .5f
    val retreat = 1.45f * (1f - exp(-p / .075f)) * (1f - p).pow(1.35f)
    val slotInset = (facing * (sourceEdge - triggerX) - seed * .5f).coerceAtLeast(0f)
    val edge = lerp(sourceEdge, finalEdge, p) - facing * (
        min(seed * .29f, menu.w * .052f) * retreat + slotInset * (1f - barRemaining) * (1f - p)
    )

    val direction = if (bottom) -1f else 1f
    val sourceDock = if (bottom) source.y + source.h else source.y
    val finalDock = if (bottom) menu.y + menu.h else menu.y
    val travel = (menu.h - source.h).coerceAtLeast(0f)
    val drop = travel * .63f * (state.sizeSpring.value - state.shapeLag.value)
    val dock = lerp(sourceDock, finalDock, p) + direction * drop
    // A source wider than the destination also contracts sideways as the far edge advances.
    val lostWidth = (source.w - menu.w).coerceAtLeast(0f)
    val reachFromHeight = min(verticalMargin * .5f, travel * .065f)
    val reachFromWidth = minOf(verticalMargin * .5f, travel * .15f, lostWidth / 12f)
    val extraReach = if (allowRebound) (reachFromHeight + reachFromWidth) *
        4f * lag * (1f - lag) * smootherstep(.50f, .94f, p) else 0f
    val sizeProgress = if (allowRebound) state.sizeSpring.value else state.sizeSpring.value.coerceIn(0f, 1f)
    val farEdge = lerp(source.h, menu.h, sizeProgress) + extraReach
    // A tall panel first gathers into a drop sized to its width. The extra length arrives with the shoulders.
    val compactHeight = lerp(source.h, menu.w, p) * lerp(1f, contour.detachedAspect, smoothstep(0f, .18f, p))
    val headHeight = min(lerp(source.h, menu.h, p), compactHeight)
    val corners = smootherstep(contour.cornerStart, contour.cornerEnd, lag)
    val shoulderGrowth = .28f * smoothstep(.30f, .58f, lag)
    val tallFill = lerp(shoulderGrowth, 1f, corners)
    val heightFill = lerp(smootherstep(.25f, .81f, p), tallFill, tall)
    val height = lerp(headHeight, farEdge - drop, heightFill).coerceAtLeast(2f)

    val tail = (1f - barRemaining) * (1f - corners)
    val shoulderFill = smoothstep(.18f, .66f, p)
    val roundRadius = (width - (source.w - 2f * source.r) * barRemaining) * .5f
    val rx = lerp(roundRadius, menu.r, corners).coerceIn(1f, width * .5f)
    val cap = (height - (source.h - 2f * source.r) * barRemaining) * .5f
    val nearCap = cap * (1f - .27f * tall * shoulderFill)
    val farCap = cap * (1f - .25f * tall * smoothstep(.35f, .78f, p))
    val ry = lerp(nearCap, menu.r, corners).coerceIn(1f, height * .5f)
    val farRy = lerp(farCap, menu.r, corners).coerceIn(1f, height * .5f)

    val cx = edge - facing * width * .5f
    val leanLimit = contour.maximumLean * width
    val lean = (triggerX - cx).coerceIn(-leanLimit, leanLimit)
    val pull = lerp(.48f, 1f, smoothstep(.16f, .42f, p)) * (1f - .80f * smoothstep(.45f, .83f, p))
    val bias = contour.crownBias * lerp(.55f, 1f, tall) * (1f - .74f * smoothstep(.28f, .65f, p))

    return SdfBoxPrimitive(cx, dock + direction * height * .5f, width, height, rx, true,
        radiusY = ry, tipX = lean * pull * tail, tipAtBottom = bottom, farRadiusY = farRy, crownBias = bias * tail)
}

private fun SdfBoxPrimitive.blendWith(other: SdfBoxPrimitive, fraction: Float): SdfBoxPrimitive {
    if (fraction <= 0f) return this
    if (fraction >= 1f) return other
    return SdfBoxPrimitive(
        lerp(cx, other.cx, fraction), lerp(cy, other.cy, fraction),
        lerp(w, other.w, fraction), lerp(h, other.h, fraction), lerp(r, other.r, fraction), true,
        lerp(radiusY, other.radiusY, fraction), lerp(tipX, other.tipX, fraction), other.tipAtBottom,
        lerp(farRadiusY, other.farRadiusY, fraction), lerp(crownBias, other.crownBias, fraction),
    )
}

/**
 * Evaluates the exact 2-SDF geometry (`pill` A, `body` B, UnionAABB, `smoothK`, content transforms)
 * in pixel coordinates relative to the `LiquidMorphContainer` canvas.
 */
fun evaluateLiquidMorphFrame(
    phys: LiquidMorphPhysicsState,
    config: LiquidMorphConfig,
    containerWidthPx: Float,
    containerHeightPx: Float,
    collapsedWidthPx: Float,
    collapsedHeightPx: Float,
    collapsedRadiusPx: Float,
    expandedWidthPx: Float,
    expandedHeightPx: Float,
    expandedRadiusPx: Float,
    density: Float,
    sourceAnchorX: Float? = null,
    contour: GlassMenuContourTokens = DefaultMenuContour,
): LiquidMorphFrame {
    val isRightAligned =
        config.alignment == LiquidMorphAlignment.TopTrailing ||
            config.alignment == LiquidMorphAlignment.BottomTrailing
    val isBottomAligned =
        config.alignment == LiquidMorphAlignment.BottomLeading ||
            config.alignment == LiquidMorphAlignment.BottomTrailing

    val sw = max(24f, collapsedWidthPx)
    val sh = max(24f, collapsedHeightPx)
    val sr = clamp(collapsedRadiusPx, 4f, min(sw, sh) * 0.5f)

    val ew = max(48f, expandedWidthPx)
    val eh = max(48f, expandedHeightPx)
    val er = clamp(expandedRadiusPx, 6f, min(ew, eh) * 0.5f)

    val padX = max(0f, (containerWidthPx - max(sw, ew)) * 0.5f)
    val padY = max(0f, (containerHeightPx - max(sh, eh)) * 0.5f)

    val sx = if (isRightAligned) containerWidthPx - padX - sw else padX
    val sy = if (isBottomAligned) containerHeightPx - padY - sh else padY
    val startRect = RectBounds(sx, sy, sw, sh, sr)

    val ex = (if (isRightAligned) sx + sw - ew else sx) + config.expandedOffsetXDp * density
    val ey = (if (isBottomAligned) sy + sh - eh else sy) + config.expandedOffsetYDp * density
    val endRect = RectBounds(ex, ey, ew, eh, er)

    val minStart = min(sw, sh)
    val minEnd = min(ew, eh)
    val triggerX = clamp(
        sourceAnchorX ?: (startRect.cx + config.triggerBiasX * sw),
        startRect.x + minStart * 0.45f,
        startRect.x + sw - minStart * 0.45f,
    )
    val triggerY = startRect.cy

    val m = phys.morph.value
    val m01 = clamp(m, 0f, 1f)
    val sz01 = clamp(phys.sizeSpring.value, 0f, 1f)
    val lag01 = clamp(phys.shapeLag.value, 0f, 1f)
    val cBias = clamp(phys.closeBias.value, 0f, 1f)
    val vel = phys.morph.velocity

    val overOpen = (phys.sizeSpring.value - 1f).coerceIn(0f, if (config.menuOpeningRebound) .036f else .025f)
    val overPos = clamp(m - 1f, 0f, 0.02f)
    val overClose = clamp(-m, 0f, 0.02f)

    val velNorm = tanh((vel * 0.18f).toDouble()).toFloat()
    val travelStretch = abs(velNorm) * sin(m01 * PI.toFloat())
    val dx = endRect.cx - triggerX
    val dy = endRect.cy - triggerY
    val dist = max(1f, hypot(dx, dy))
    val perpX = -dy / dist
    val perpY = dx / dist
    val arcMag = min(dist * 0.05f, 14f * density) * config.arcBend

    val stretchX = 1f + (if (abs(dx) > abs(dy)) 0.028f else -0.016f) * travelStretch
    val stretchY = 1f + (if (abs(dy) >= abs(dx)) 0.030f else -0.016f) * travelStretch

    val sharedTop = startRect.y
    val sharedBottom = startRect.y + startRect.h

    // 1. Target view B
    val bPresence = lerp(
        smoothstep(0.04f, 0.15f, m01),
        smoothstep(0.015f, 0.10f, m01),
        cBias,
    )
    val bodyActive = bPresence > 0.01f

    val isWideBarSource = sw > ew * 1.1f
    val roundLauncher = abs(sw - sh) < 1f && sr >= minStart * 0.49f && ew >= sw
    val single = phys.openingContour.value.coerceIn(0f, 1f)
    val returningGroup = if (config.showsCollapsedSurface && sw > sh * 1.12f && !isWideBarSource) 1f - single else 0f
    val seedDia = clamp(minStart * lerp(0.74f, if (isWideBarSource) 1.15f else 0.90f, cBias), 16f, minEnd * 0.62f)
    // A short history can be shallower than the enlarged source circle.
    val lensCircleDia = min(max(minEnd * 0.76f, minStart * 1.5f), minEnd * 0.84f)
    val isoDia = lerp(seedDia, lensCircleDia, smoothstep(0.04f, 0.56f, sz01))
    val widthValue = if (config.menuOpeningRebound) phys.widthSpring.value else phys.sizeSpring.value
    val widthOver = (widthValue - 1f).coerceIn(0f, if (config.menuOpeningRebound) .036f else .025f)
    val extraWidth = widthOver * if (config.menuOpeningRebound) .70f else .45f
    val targetW = lerp(seedDia * if (isWideBarSource) 1.35f else 1f, ew, widthValue.coerceIn(0f, 1f)) * (1f + extraWidth)
    val targetH = lerp(seedDia * if (isWideBarSource) 0.92f else 1f, eh, sz01) * (1f + overOpen * 0.65f)

    // A circle already starts at the seed's aspect ratio. Blending through a
    // capped intermediate diameter makes it pause before the menu grows again.
    val aspectMix = if (roundLauncher) 1f else smootherstep(
        if (isWideBarSource) 0.02f else 0.14f,
        if (isWideBarSource) 0.36f else 0.58f,
        lag01,
    )
    // At the dock the remaining volume drains into the tapped slot. Keep the drop visible
    // after the two ends of the source have spread back into place.
    val pendant = returningGroup * (1f - smootherstep(.20f, .62f, m01))
    val drain = smootherstep(0f, .25f, m01)
    val bodyW = max(2f, lerp(lerp(isoDia, targetW, aspectMix) * bPresence, sh * .93f * drain, pendant) * stretchX)
    val bodyH = max(2f, lerp(lerp(isoDia, targetH, aspectMix) * bPresence, sh * 1.35f * drain, pendant) * stretchY)
    val facing = if (isRightAligned) 1f else -1f

    val maxRoundFrac = lerp(0.50f, lerp(0.50f, 0.40f, smoothstep(0.18f, 0.48f, m01)), cBias)
    val radiusTighten = smootherstep(0.56f, 0.94f, lag01)
    val bodyR = clamp(
        lerp(min(bodyW, bodyH) * maxRoundFrac, er, radiusTighten),
        1f,
        min(bodyW, bodyH) * 0.5f,
    )
    val closeHangEnv = cBias * sin(clamp(m01 / 0.52f) * PI.toFloat())
    val vDir = if (isBottomAligned) -1f else 1f
    val pillHangWeight = clamp(ew / max(1f, sw) - 0.9f, 0f, 1f)

    val dockEdgeY = if (isBottomAligned) sharedBottom else sharedTop
    val dockOffsetY = -vDir * min(minStart * phys.dockLift.value, padY)
    val anchorCenterY = dockEdgeY + vDir * bodyH * 0.5f
    val anchorCenterX = lerp(triggerX, lerp(startRect.cx, triggerX, 0.35f), cBias * (1f - pendant))
    val pearOffsetY = vDir * closeHangEnv * min(minStart * 0.36f, 17f * density) * pillHangWeight

    val travelT = smoothstep(0f, 1f, m01)
    val arcWave = sin(m01 * PI.toFloat()) * arcMag
    // A round trigger and its menu share an outside edge. Derive the center from
    // the live width so a second timing curve cannot slide the growing glass sideways.
    val bodyCx = if (roundLauncher) {
        val growth = (bodyW - sw).coerceAtLeast(0f) * 0.5f
        startRect.cx - facing * growth + config.expandedOffsetXDp * density * travelT
    } else {
        val excess = if (config.menuOpeningRebound) (bodyW - ew).coerceAtLeast(0f) * .5f else 0f
        lerp(anchorCenterX, endRect.cx, travelT + overPos * 0.25f) + perpX * arcWave +
            if (isRightAligned) -excess else excess
    }
    val travelY = lerp(anchorCenterY, endRect.cy, travelT + overPos * 0.35f) + perpY * arcWave + pearOffsetY
    // The far edge leads the slower corner/shape spring, then settles back toward the trigger.
    val openingLead = if (config.menuOpeningRebound) {
        val lead = (phys.sizeSpring.value - phys.shapeLag.value).coerceAtLeast(0f)
        val room = (padY * .92f - (bodyH - eh).coerceAtLeast(0f) * .5f).coerceAtLeast(0f)
        min(room, lead * (1f - cBias) * (eh - sh).coerceAtLeast(0f) * .29f)
    } else 0f
    val bodyDepth = vDir * (travelY - dockEdgeY) + openingLead
    // The last lobe drains into the inside edge of the pill while the head spreads.
    val docking = smoothstep(0f, 0.10f, phys.dockLift.value) *
        (1f - smoothstep(sh * 0.55f, sh * 1.4f, bodyDepth))
    val attachedDepth = lerp(sh * .55f, sh + bodyH * .22f, drain)
    val bodyCy = dockEdgeY + dockOffsetY + vDir * lerp(lerp(bodyDepth, sh * .96f, docking), attachedDepth, pendant)

    // 2. Source view A (two-stage: compact round head rising to edge -> horizontal spread)
    val aVanish = smootherstep(lerp(0.14f, 0.18f, cBias), lerp(0.62f, 0.60f, cBias), m01)
    val aScale = 1f - aVanish
    val aContract = lerp(smootherstep(.02f, .28f, m01), smootherstep(.11f, .52f, m01), returningGroup)
    val pillActive = config.showsCollapsedSurface && aScale > .015f

    val pressSwell = 1f + 0.025f * phys.pressGlow
    val settleRippleX = 1f + overClose * 0.9f
    val settleRippleY = 1f - overClose * 0.55f

    val tailW = lerp(sw, minStart, aContract)
    val tailH = lerp(sh, minStart * 0.98f, aContract)
    val scalePow = aScale.pow(0.36f)
    val pillW = max(2f, tailW * scalePow * pressSwell * settleRippleX)
    val pillH = max(2f, tailH * scalePow * pressSwell * settleRippleY)
    val pillR = clamp(lerp(sr, min(pillW, pillH) * 0.5f, aContract), 1f, min(pillW, pillH) * 0.5f)

    val cornerInset = bodyR * 0.48f
    val shoulderX = bodyCx + (if (isRightAligned) 1f else -1f) * max(0f, bodyW * 0.5f - cornerInset)
    val shoulderY = bodyCy + (if (isBottomAligned) 1f else -1f) * max(0f, bodyH * 0.5f - cornerInset)

    val restPillCx = lerp(startRect.cx, triggerX, aContract)
    val restPillCy = dockEdgeY + dockOffsetY + vDir * pillH * 0.5f

    val attachBlend = smootherstep(0.16f, 0.52f, m01) * if (isWideBarSource) 0.55f else 1f
    val pillCx = if (roundLauncher) startRect.cx else lerp(restPillCx, shoulderX, attachBlend)
    val pillCy = lerp(restPillCy, shoulderY, attachBlend)

    val smoothK = if (pillActive && bodyActive) {
        // Blend locally at the neck. Coincident circles should converge to one circle.
        val smallDiameter = min(min(pillW, pillH), min(bodyW, bodyH))
        val separation = hypot(pillCx - bodyCx, pillCy - bodyCy)
        min(smallDiameter * .70f, separation) * config.blendStrength * sqrt(aScale * bPresence)
    } else {
        0f
    }

    val source = SdfBoxPrimitive(pillCx, pillCy, pillW, pillH, pillR, pillActive)
    val returningBody = SdfBoxPrimitive(bodyCx, bodyCy, bodyW, bodyH, bodyR, bodyActive)
    val openBody = if (single == 0f) null else evaluateLiquidMenuOpening(
        phys, startRect, endRect, config.alignment, triggerX, padY, config.menuOpeningRebound, contour,
    )
    // Do not blend a decaying openingContour into an inactive 2-px returning placeholder,
    // or a mid-close reversal collapses sourceContent before reopening finishes.
    val body = when {
        openBody == null -> returningBody
        !returningBody.active || (phys.targetExpanded && returningBody.w < seedDia) ->
            if (phys.targetExpanded) openBody.copy(active = true) else returningBody
        else -> returningBody.blendWith(openBody, single)
    }
    val pill = if (single == 0f || !body.active) source else source.copy(
        cx = lerp(source.cx, body.cx, single), cy = lerp(source.cy, body.cy, single),
        w = source.w * (1f - single), h = source.h * (1f - single), r = source.r * (1f - single),
        active = source.active && single < 1f,
    )
    val followOpenY = if (body.active && single > 0f) single * smoothstep(.02f, .25f, m01) else 0f
    val followOpenX = if (!isWideBarSource) followOpenY * (1f - returningGroup) else 0f
    val sourceContent = SdfBoxPrimitive(
        cx = lerp(startRect.cx, body.cx, followOpenX),
        cy = lerp(restPillCy, body.cy, followOpenY),
        w = sw,
        h = sh,
        r = sr,
        active = config.showsCollapsedSurface && (pill.active || (phys.targetExpanded && body.active)),
    )
    val a = if (pill.active) pill else body
    val b = if (body.active) body else a
    val minX = min(a.cx - a.w * .5f, b.cx - b.w * .5f)
    val maxX = max(a.cx + a.w * .5f, b.cx + b.w * .5f)
    val minY = min(a.cy - a.h * .5f, b.cy - b.h * .5f)
    val maxY = max(a.cy + a.h * .5f, b.cy + b.h * .5f)

    val sourceDissolve = lerp(smoothstep(.05f, .22f, m01), smoothstep(.065f, .40f, m01), returningGroup)
    val collapsedAlpha = 1f - sourceDissolve
    val collapsedBlurPx = 6f * density * sourceDissolve
    val collapsedScale = lerp(1f, .88f, sourceDissolve)

    val expandedAlpha = smoothstep(.14f, .62f, lag01) * smoothstep(.16f, .54f, m01)
    val expandedBlurPx = lerp(6f * density, 0f, smoothstep(.18f, .74f, lag01))
    val fitScaleX = clamp(body.w / ew, 0.58f, 1.04f)
    val fitScaleY = clamp(body.h / eh, 0.56f, 1.04f)
    val expandedScaleX = min(lerp(0.66f, 1f, smoothstep(0.36f, 0.90f, m01)) * (1f + widthOver * .55f), fitScaleX)
    val expandedScaleY = min(lerp(0.62f, 1f, smoothstep(0.36f, 0.90f, m01)) * (1f + overOpen * 0.85f), fitScaleY)

    val pressGlowRadius = (min(maxX - minX, maxY - minY) * 0.92f)
        .coerceIn(34f * density, 82f * density)

    return LiquidMorphFrame(
        pill = pill,
        body = body,
        sourceContent = sourceContent,
        materialProgress = smoothstep(.12f, .93f, sz01),
        contentRefraction = 1f - smootherstep(.36f, .82f, lag01),
        boundCx = (minX + maxX) * 0.5f,
        boundCy = (minY + maxY) * 0.5f,
        boundHalfW = (maxX - minX) * 0.5f,
        boundHalfH = (maxY - minY) * 0.5f,
        smoothK = smoothK * (1f - single),
        pressGlow = phys.pressGlow,
        pressGlowX = triggerX,
        pressGlowY = triggerY,
        pressGlowRadius = pressGlowRadius,
        collapsedAlpha = collapsedAlpha,
        collapsedBlurPx = collapsedBlurPx,
        collapsedScale = collapsedScale,
        expandedAlpha = expandedAlpha,
        expandedBlurPx = expandedBlurPx,
        expandedScaleX = expandedScaleX,
        expandedScaleY = expandedScaleY,
        startRect = startRect,
        endRect = endRect,
        showsCollapsedSurface = config.showsCollapsedSurface,
    )
}
