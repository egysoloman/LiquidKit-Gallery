package com.kyant.backdrop.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/** Continuous material opacity. Drag, tap, accessibility and keyboard all update the same value. */
@Composable
fun LiquidOpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    showIcons: Boolean = false,
    backdrop: Backdrop? = null,
) {
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val appearance = liquidGlassAppearance()
    val dark = appearance.dark
    val accent = if (dark) Color(0xff0a84ff) else Color(0xff007aff)
    var pressed by remember { mutableStateOf(false) }
    val tokens = LocalLiquidGlassTokens.current
    val controls = tokens.controls
    val effectiveBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop
    val liftState = animateFloatAsState(if (pressed) 1f else 0f, controls.sliderThumbPress.spec, label = "Opacity thumb press")
    val isGlassActive by remember { derivedStateOf { liftState.value > 0.001f } }
    val haptics = rememberLiquidHaptics()
    val colors = liquidListColors(dark)

    @Composable
    fun SliderTrack(trackModifier: Modifier) {
        var widthPx by remember { mutableIntStateOf(0) }
        var heightPx by remember { mutableIntStateOf(0) }
        val latestValue by rememberUpdatedState(value)
        val trackLayer = rememberLayerBackdrop()
        val fallbackThumbPath = remember { Path() }
        val combinedBackdrop = effectiveBackdrop?.let { base ->
            rememberCombinedBackdrop(
                base,
                rememberBackdrop(trackLayer) { drawBackdrop ->
                    val p = liftState.value.coerceIn(0f, 1f)
                    val s = lerp(2f / 3f, 1.06f, p)
                    scale(s, s) { drawBackdrop() }
                },
            )
        }
        val density = LocalDensity.current
        val rim = rememberLiquidRimHighlight(dark, layeredEdge = false)
        Box(
            trackModifier.height(48.dp).onSizeChanged { widthPx = it.width; heightPx = it.height }
                .progressSemantics(value, 0f..1f, steps = 0).semantics {
                    contentDescription = label
                    setProgress { change(it.coerceIn(0f, 1f)); finish(); true }
                }.onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) false else {
                        val delta = when (it.key) { Key.DirectionRight -> .01f; Key.DirectionLeft -> -.01f; else -> return@onKeyEvent false }
                        change((value + if (rtl) -delta else delta).coerceIn(0f, 1f)); finish(); true
                    }
                }.focusable().pointerInput(rtl) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                        if (down.isConsumed) return@awaitEachGesture
                        val inset = 19.dp.toPx()
                        val trackSpan = (size.width - 2 * inset).coerceAtLeast(1f)
                        val curCenterX = inset + trackSpan * (if (rtl) 1f - latestValue else latestValue)
                        val hitThumb = abs(down.position.x - curCenterX) <= 26.dp.toPx()
                        fun edgeOf(v: Float) = when { v <= 0.004f -> -1; v >= 0.996f -> 1; else -> 0 }
                        var lastEdge = edgeOf(latestValue)
                        fun update(x: Float) {
                            val at = ((x - inset) / trackSpan).coerceIn(0f, 1f)
                            val v = if (rtl) 1f - at else at
                            val edge = edgeOf(v)
                            if (edge != 0 && edge != lastEdge) haptics(LiquidHaptic.ThresholdDetent)
                            lastEdge = edge
                            change(v)
                        }
                        var claimed = hitThumb
                        var dragging = false
                        if (claimed) {
                            pressed = true
                            down.consume()
                        }
                        try {
                            while (true) {
                                val p = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                val delta = p.position - down.position
                                val heldLongEnough = p.uptimeMillis - down.uptimeMillis >= 95L
                                if (!claimed) {
                                    if (!p.pressed) {
                                        if (abs(delta.x) <= slop && abs(delta.y) <= slop) {
                                            haptics(LiquidHaptic.SelectionTick)
                                            update(p.position.x)
                                            finish()
                                            p.consume()
                                        }
                                        break
                                    }
                                    if (!heldLongEnough && abs(delta.y) > slop && abs(delta.y) > abs(delta.x) * 1.15f) {
                                        break
                                    }
                                    if ((abs(delta.x) > slop && abs(delta.x) >= abs(delta.y)) || heldLongEnough) {
                                        claimed = true
                                        pressed = true
                                        dragging = true
                                        update(p.position.x)
                                        p.consume()
                                    }
                                } else {
                                    dragging = dragging || abs(delta.x) > slop
                                    update(p.position.x)
                                    p.consume()
                                    if (!p.pressed) {
                                        if (!dragging) finish()
                                        break
                                    }
                                }
                            }
                        } finally {
                            pressed = false
                            if (dragging) finish()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(trackLayer, enabled = effectiveBackdrop != null && isGlassActive)) {
                val inset = 19.dp.toPx()
                val left = inset
                val right = (size.width - inset).coerceAtLeast(left)
                val x = left + (right - left) * (if (rtl) 1f - value else value)
                val y = center.y
                val thickness = 4.5.dp.toPx()
                drawLine(if (dark) Color(0xff434446) else Color(0xffdbdce0), Offset(left, y), Offset(right, y), thickness, StrokeCap.Round)
                drawLine(accent, Offset(if (rtl) right else left, y), Offset(x, y), thickness, StrokeCap.Round)
                if (effectiveBackdrop == null) {
                    val lift = liftState.value
                    val scale = 1f + (HeldThumbScale - 1f) * lift
                    val halfHeight = 12.dp.toPx() * scale
                    val halfWidth = 18.5.dp.toPx() * scale
                    val middle = y
                    fallbackThumbPath.reset()
                    fallbackThumbPath.addLiquidCapsule(Rect(x - halfWidth, middle - halfHeight, x + halfWidth, middle + halfHeight))
                    val pressedFraction = lift.coerceIn(0f, 1f)
                    translate(top = 1.5.dp.toPx()) { drawPath(fallbackThumbPath, Color.Black.copy(alpha = .13f * (1f - pressedFraction))) }
                    drawPath(fallbackThumbPath, Color.White.copy(alpha = 1f - .68f * pressedFraction))
                    drawPath(fallbackThumbPath, Color.White.copy(alpha = .64f * pressedFraction), style = Stroke(.7.dp.toPx()))
                }
            }
            if (combinedBackdrop != null && widthPx > 0 && heightPx > 0) {
                val insetPx = with(density) { 19.dp.toPx() }
                val leftPx = insetPx
                val rightPx = (widthPx.toFloat() - insetPx).coerceAtLeast(leftPx)
                val xPx = leftPx + (rightPx - leftPx) * (if (rtl) 1f - value else value)
                val thumbW = with(density) { 37.dp.toPx() }
                val thumbH = with(density) { 24.dp.toPx() }
                Box(
                    Modifier
                        .matchParentSize()
                        .layout { measurable, constraints ->
                            val pressedFraction = liftState.value.coerceIn(0f, 1f)
                            val curScale = 1f + (HeldThumbScale - 1f) * pressedFraction
                            val w = (thumbW * curScale).roundToInt().coerceAtLeast(1)
                            val h = (thumbH * curScale).roundToInt().coerceAtLeast(1)
                            val yPx = heightPx / 2f
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.place((xPx - w * .5f).roundToInt(), (yPx - h * .5f).roundToInt())
                            }
                        }
                        .drawBackdrop(
                            backdrop = if (isGlassActive) combinedBackdrop else emptyBackdrop(),
                            shape = { LiquidCapsule },
                            effects = {
                                val p = liftState.value.coerceIn(0f, 1f)
                                if (p > 0.001f) {
                                    blur(appearance.blurRadius.toPx() * (1f - p) * .5f)
                                    lens(
                                        refractionHeight = 9.0.dp.toPx() * p,
                                        refractionAmount = 20.dp.toPx() * p,
                                        depthEffect = true,
                                        chromaticAberration = true,
                                    )
                                }
                            },
                            highlight = {
                                val p = liftState.value.coerceIn(0f, 1f)
                                rim.copy(
                                    width = 1.0.dp,
                                    alpha = ((if (!dark) .92f else 1f) * tokens.highlightIntensity * p).coerceIn(0f, 1f),
                                )
                            },
                            shadow = {
                                Shadow(radius = 4.dp, offset = DpOffset(0.dp, 1.5.dp), color = Color.Black.copy(alpha = if (dark) .22f else .06f))
                            },
                            innerShadow = if (!dark) null else {
                                {
                                    val p = liftState.value.coerceIn(0f, 1f)
                                    InnerShadow(radius = 2.dp * p, alpha = .16f * p)
                                }
                            },
                            onDrawSurface = {
                                val p = liftState.value.coerceIn(0f, 1f)
                                drawRect(Color.White.copy(alpha = 1f - p))
                                if (p > 0.02f) {
                                    drawRect(Color.White.copy(alpha = (if (dark) .06f else .12f) * p))
                                }
                            },
                        ),
                )
            }
        }
    }

    if (showIcons) {
        Row(
            modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidIcon(LiquidSymbol.SquareOnSquare, colors.ink.copy(alpha = .75f), Modifier.size(19.dp))
            SliderTrack(Modifier.weight(1f))
            LiquidIcon(LiquidSymbol.SquareFillOnSquareFill, colors.ink.copy(alpha = .85f), Modifier.size(19.dp))
        }
    } else {
        SliderTrack(modifier.fillMaxWidth())
    }
}
