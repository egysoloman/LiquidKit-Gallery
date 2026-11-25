package com.kyant.backdrop.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.interaction.DampedDragAnimation
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

// The native 37 × 24 thumb grows to roughly 60 × 39 during a stationary hold.
internal const val HeldThumbScale = 1.63f

/** Pure slider drag tracking math: keeps relative grab offset and X continuous regardless of Y excursion. */
internal fun resolveSliderDragValue(
    startValue: Float,
    deltaXPx: Float,
    trackWidthPx: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isLtr: Boolean,
): Float {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
    if (trackWidthPx <= 0f) return startValue.coerceIn(valueRange)
    val fracDelta = (deltaXPx / trackWidthPx) * if (isLtr) 1f else -1f
    return (startValue + span * fracDelta).coerceIn(valueRange)
}

/** Snaps a slider value to the nearest iOS 26/27 `SliderTick` within [snapRadiusFraction] (or unconditionally when [forceSnap] is true). */
internal fun resolveSliderTickSnap(
    value: Float,
    ticks: List<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    snapRadiusFraction: Float = 0.045f,
    forceSnap: Boolean = false,
): Float {
    val clamped = value.coerceIn(valueRange)
    if (ticks.isEmpty()) return clamped
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
    val nearest = ticks.minByOrNull { abs(it - clamped) }?.coerceIn(valueRange) ?: return clamped
    return if (forceSnap || abs(nearest - clamped) <= span * snapRadiusFraction) nearest else clamped
}

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) = LiquidSlider(value, onValueChange, valueRange, visibilityThreshold, backdrop, modifier, tapToSeek = true)

/** A thumb-only slider lets scrolling gestures begin on the unoccupied track. Supports iOS 26/27 [ticks] and [neutralValue]. */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tapToSeek: Boolean,
    ticks: List<Float> = emptyList(),
    snapToTicks: Boolean = ticks.isNotEmpty(),
    neutralValue: Float? = null,
    tint: Color = Color.Unspecified,
) {
    require(valueRange.endInclusive > valueRange.start)
    val current by rememberUpdatedState(value)
    val commit by rememberUpdatedState(onValueChange)
    val appearance = liquidGlassAppearance()
    val tokens = LocalLiquidGlassTokens.current
    val effectiveBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop
    val isLightTheme = !appearance.dark
    val rim = rememberLiquidRimHighlight(!isLightTheme, layeredEdge = false)
    val accentColor = when {
        tint.isSpecified -> tint
        isLightTheme -> Color(0xFF0088FF)
        else -> Color(0xFF0091FF)
    }
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.fillMaxWidth().heightIn(min = 44.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(current().coerceIn(valueRange), valueRange)
            setProgress { commit(it.coerceIn(valueRange)); true }
        },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.coerceAtLeast(1)
        val density = LocalDensity.current
        val thumbBaseW = with(density) { 37.dp.toPx() }
        val thumbBaseH = with(density) { 24.dp.toPx() }
        val thumbHitHalfW = with(density) { 26.dp.toPx() }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val haptics = rememberLiquidHaptics()
        val controls = tokens.controls
        val dampedDragAnimation = remember(animationScope, trackWidth, valueRange, isLtr, visibilityThreshold, controls) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = current().coerceIn(valueRange),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = HeldThumbScale,
                controls = controls,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { current().coerceIn(valueRange) }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        val span = valueRange.endInclusive - valueRange.start
        val normalizedTicks = remember(ticks, valueRange) {
            ticks.map { it.coerceIn(valueRange) }.distinct().sorted()
        }
        fun valueAt(posX: Float): Float {
            val norm = (posX / trackWidth.toFloat()).coerceIn(0f, 1f)
            val frac = if (isLtr) norm else 1f - norm
            val raw = (valueRange.start + span * frac).coerceIn(valueRange)
            return if (snapToTicks) resolveSliderTickSnap(raw, normalizedTicks, valueRange, forceSnap = true) else raw
        }

        val isGlassActive by remember(dampedDragAnimation) {
            derivedStateOf { dampedDragAnimation.pressProgress > 0.001f }
        }
        val activeThumbBackdrop = rememberCombinedBackdrop(
            effectiveBackdrop,
            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                val progress = dampedDragAnimation.pressProgress
                val scaleVal = lerp(2f / 3f, 1.06f, progress)
                scale(scaleVal, scaleVal) {
                    drawBackdrop()
                }
            },
        )

        // Handle pointer input on the non-translating Box so thumb translation never introduces feedback jitter,
        // and acquire Initial pass once grabbed so vertical Y excursions (+/- 200dp) track X without breaking.
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).layerBackdrop(trackBackdrop, enabled = isGlassActive)
                .pointerInput(dampedDragAnimation, valueRange, trackWidth, isLtr, tapToSeek, normalizedTicks, snapToTicks) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                        if (down.isConsumed) return@awaitEachGesture
                        val curProgress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                        val visualCenterX = (trackWidth * curProgress)
                            .coerceIn(thumbBaseW * .25f, trackWidth - thumbBaseW * .25f)
                        val thumbHitCenter = if (isLtr) visualCenterX else trackWidth - visualCenterX
                        val hitThumb = abs(down.position.x - thumbHitCenter) <= thumbHitHalfW
                        var claimed = hitThumb
                        var startValue = dampedDragAnimation.targetValue
                        val edgeEps = span * 0.004f
                        fun edgeZone(v: Float): Int = when {
                            v <= valueRange.start + edgeEps -> -1
                            v >= valueRange.endInclusive - edgeEps -> 1
                            else -> 0
                        }
                        var lastEdge = edgeZone(startValue)
                        var lastTick: Float? = if (snapToTicks) normalizedTicks.firstOrNull { abs(it - startValue) <= edgeEps } else null
                        var lastNeutral = neutralValue?.let { if (abs(startValue - it) <= span * 0.02f) 0 else if (startValue < it) -1 else 1 } ?: 0
                        fun emitSliderDetent(v: Float) {
                            val edge = edgeZone(v)
                            if (edge != 0 && edge != lastEdge) {
                                haptics(LiquidHaptic.ThresholdDetent)
                            } else if (snapToTicks && normalizedTicks.isNotEmpty()) {
                                val hitTick = normalizedTicks.firstOrNull { abs(it - v) <= edgeEps }
                                if (hitTick != null && hitTick != lastTick) {
                                    haptics(LiquidHaptic.SelectionTick)
                                }
                                lastTick = hitTick
                            } else if (neutralValue != null) {
                                val side = if (abs(v - neutralValue) <= span * 0.02f) 0 else if (v < neutralValue) -1 else 1
                                if (side == 0 && lastNeutral != 0) haptics(LiquidHaptic.SelectionTick)
                                lastNeutral = side
                            }
                            lastEdge = edge
                        }
                        if (claimed) {
                            down.consume()
                            dampedDragAnimation.press()
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val p = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dx = p.position.x - down.position.x
                                val dy = p.position.y - down.position.y
                                if (!claimed) {
                                    if (!p.pressed) {
                                        if (tapToSeek && abs(dx) <= slop && abs(dy) <= slop) {
                                            val target = valueAt(p.position.x)
                                            dampedDragAnimation.animateToValue(target)
                                            haptics(LiquidHaptic.SelectionTick)
                                            commit(target)
                                            p.consume()
                                        }
                                        break
                                    }
                                    val heldLongEnough = p.uptimeMillis - down.uptimeMillis >= 95L
                                    if (!heldLongEnough && abs(dy) > slop && abs(dy) > abs(dx) * 1.15f) {
                                        break
                                    }
                                    if (tapToSeek && ((abs(dx) > slop && abs(dx) >= abs(dy)) || heldLongEnough)) {
                                        claimed = true
                                        startValue = valueAt(down.position.x)
                                        val raw = resolveSliderDragValue(
                                            startValue, dx, trackWidth.toFloat(), valueRange, isLtr,
                                        )
                                        val next = if (snapToTicks) resolveSliderTickSnap(raw, normalizedTicks, valueRange) else raw
                                        dampedDragAnimation.updateValue(next)
                                        emitSliderDetent(next)
                                        commit(next)
                                        dampedDragAnimation.press()
                                        p.consume()
                                    }
                                } else {
                                    p.consume()
                                    val raw = resolveSliderDragValue(
                                        startValue, dx, trackWidth.toFloat(), valueRange, isLtr,
                                    )
                                    val next = if (snapToTicks) resolveSliderTickSnap(raw, normalizedTicks, valueRange) else raw
                                    if (next != dampedDragAnimation.targetValue) {
                                        dampedDragAnimation.updateValue(next)
                                        emitSliderDetent(next)
                                        commit(next)
                                    }
                                    if (!p.pressed) {
                                        val finalVal = if (snapToTicks) {
                                            resolveSliderTickSnap(dampedDragAnimation.targetValue, normalizedTicks, valueRange, forceSnap = true)
                                        } else {
                                            dampedDragAnimation.targetValue
                                        }
                                        if (finalVal != dampedDragAnimation.targetValue) {
                                            dampedDragAnimation.animateToValue(finalVal)
                                        }
                                        commit(finalVal)
                                        break
                                    }
                                }
                            }
                        } finally {
                            if (claimed) dampedDragAnimation.release()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .clip(LiquidCapsule)
                    .background(trackColor)
                    .height(4.5.dp)
                    .fillMaxWidth()
            )

            val neutralFraction = neutralValue?.let {
                ((it - valueRange.start) / span.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            }
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val progress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                        val startFrac = if (neutralFraction != null) minOf(neutralFraction, progress) else 0f
                        val endFrac = if (neutralFraction != null) maxOf(neutralFraction, progress) else progress
                        val barStart = (constraints.maxWidth * startFrac).fastRoundToInt()
                        val barWidth = ((constraints.maxWidth * (endFrac - startFrac)).fastRoundToInt()).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = barWidth, maxWidth = barWidth),
                        )
                        layout(constraints.maxWidth, placeable.height) {
                            val x = if (isLtr) barStart else constraints.maxWidth - barStart - barWidth
                            placeable.place(x, 0)
                        }
                    }
                    .clip(LiquidCapsule)
                    .background(accentColor)
                    .height(4.5.dp)
            )

            if (neutralFraction != null) {
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val drawFrac = if (isLtr) neutralFraction else 1f - neutralFraction
                    val cx = drawFrac * size.width
                    val notchColor = if (isLightTheme) Color.White.copy(alpha = .92f) else Color(0xff1c1c1e).copy(alpha = .92f)
                    drawCircle(notchColor, radius = 2.6.dp.toPx(), center = Offset(cx, size.height * .5f))
                }
            }

            if (normalizedTicks.isNotEmpty()) {
                val tickInactive = if (isLightTheme) Color(0xFF3C3C43).copy(alpha = .32f) else Color.White.copy(alpha = .34f)
                Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                    val progress = dampedDragAnimation.progress.coerceIn(0f, 1f)
                    val activeMin = if (neutralFraction != null) minOf(neutralFraction, progress) else 0f
                    val activeMax = if (neutralFraction != null) maxOf(neutralFraction, progress) else progress
                    val tickW = 2.dp.toPx()
                    val tickH = 5.5.dp.toPx()
                    val y = size.height * .5f + 6.5.dp.toPx()
                    val corner = CornerRadius(tickW / 2f, tickW / 2f)
                    for (tick in normalizedTicks) {
                        val frac = ((tick - valueRange.start) / span.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
                        val drawFrac = if (isLtr) frac else 1f - frac
                        val x = (drawFrac * (size.width - tickW)).coerceIn(0f, (size.width - tickW).coerceAtLeast(0f))
                        val passed = frac >= activeMin - .005f && frac <= activeMax + .005f
                        drawRoundRect(
                            color = if (passed) accentColor.copy(alpha = .85f) else tickInactive,
                            topLeft = Offset(x, y),
                            size = Size(tickW, tickH),
                            cornerRadius = corner,
                        )
                    }
                }
            }
        }

        // Measure the thumb at its live expanded bounds so the optical refraction ring (~4.8dp shoulder)
        // matches iOS 27.1 crystal-clear center + tight perimeter refraction rather than over-scaling a small buffer.
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val curScale = dampedDragAnimation.scale.coerceAtLeast(1f)
                    val velocity = dampedDragAnimation.velocity / 10f
                    val velX = 1f / (1f - (velocity * 0.45f).fastCoerceIn(-0.18f, 0.18f))
                    val velY = 1f - (velocity * 0.18f).fastCoerceIn(-0.16f, 0.16f)
                    val w = (thumbBaseW * curScale * velX).roundToInt().coerceAtLeast(1)
                    val h = (thumbBaseH * curScale * velY).roundToInt().coerceAtLeast(1)
                    val p = measurable.measure(Constraints.fixed(w, h))
                    val rawCenter = trackWidth * dampedDragAnimation.progress
                    val minCenter = thumbBaseW * .25f
                    val maxCenter = (trackWidth - thumbBaseW * .25f).coerceAtLeast(minCenter)
                    val center = rawCenter.fastCoerceIn(minCenter, maxCenter)
                    val physicalCenterX = if (isLtr) center else trackWidth - center
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val lx = (physicalCenterX - w * .5f).roundToInt()
                        val ly = ((constraints.maxHeight - h) * .5f).roundToInt()
                        p.place(lx, ly)
                    }
                }
                .drawBackdrop(
                    backdrop = if (isGlassActive) activeThumbBackdrop else emptyBackdrop(),
                    shape = { LiquidCapsule },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (progress > 0.001f) {
                            blur(appearance.blurRadius.toPx() * (1f - progress) * .5f)
                            lens(
                                refractionHeight = 9.0.dp.toPx() * progress,
                                refractionAmount = 20.dp.toPx() * progress,
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        rim.copy(
                            width = 1.0.dp,
                            alpha = ((if (isLightTheme) .92f else 1f) * tokens.highlightIntensity * progress).coerceIn(0f, 1f),
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            offset = DpOffset(0.dp, 1.5.dp),
                            color = Color.Black.copy(alpha = if (isLightTheme) 0.06f else 0.22f)
                        )
                    },
                    innerShadow = if (isLightTheme) null else {
                        {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 2f.dp * progress,
                                alpha = .16f * progress
                            )
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = (1f - progress * 1.15f).coerceIn(0f, 1f)))
                    }
                )
        )
    }
}
