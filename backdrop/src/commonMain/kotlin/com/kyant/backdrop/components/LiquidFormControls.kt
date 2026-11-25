package com.kyant.backdrop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atanh
import kotlin.math.roundToInt
import kotlin.math.tanh

internal fun segmentedThumbFraction(dragX: Float, trackWidth: Float, count: Int): Float {
    if (count <= 1 || trackWidth <= 0f) return 0f
    val slotWidth = trackWidth / count.toFloat()
    val centerIndex = (dragX - slotWidth * 0.5f) / slotWidth
    return (centerIndex / (count - 1).toFloat()).coerceIn(0f, 1f)
}

internal fun segmentedIndexForFraction(fraction: Float, count: Int): Int {
    if (count <= 1) return 0
    return (fraction.coerceIn(0f, 1f) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/**
 * Maps pointer position inside a segmented track to logical index space `[0, count - 1]`,
 * preserving `tanh` rubber-band overhang past first and last segments during live dragging.
 */
internal fun segmentedRubberBandIndex(
    dragX: Float,
    trackWidth: Float,
    count: Int,
    overhangLimitSlots: Float = 0.14f,
): Float {
    if (count <= 1 || trackWidth <= 0f) return 0f
    val slotWidth = trackWidth / count.toFloat()
    val rawIndex = (dragX - slotWidth * 0.5f) / slotWidth
    val clamped = rawIndex.coerceIn(0f, (count - 1).toFloat())
    val limit = overhangLimitSlots.coerceAtLeast(0.001f)
    return clamped + limit * tanh((rawIndex - clamped) / limit)
}

/** Computes horizontal & vertical scale factors for the active segmented glass lens. */
internal fun segmentedLensScale(
    activeGlass: Float,
    travelStrain: Float,
    widthGain: Float,
    heightGain: Float,
    travelStretch: Float,
    pullAspect: Float = 0f,
    pullStretch: Float = .12f,
): Pair<Float, Float> {
    val stretch = abs(travelStrain).coerceIn(0f, .85f)
    val sx = (1f + widthGain * activeGlass + travelStretch * stretch - .32f * pullStretch * pullAspect).coerceAtLeast(.9f)
    val sy = (1f + heightGain * activeGlass - .20f * stretch + pullStretch * pullAspect).coerceAtLeast(.85f)
    return sx to sy
}

/** Returns `(commitIndex, wasDrag)` so plain taps always keep the discrete tap-lift animation enabled. */
internal fun resolveSegmentedCommitIndex(
    claimed: Boolean,
    downIdx: Int,
    fingerIdx: Float?,
    count: Int,
): Pair<Int, Boolean> {
    if (count <= 0) return 0 to claimed
    val last = count - 1
    val index = if (claimed) {
        (fingerIdx ?: downIdx.toFloat()).roundToInt().coerceIn(0, last)
    } else {
        downIdx.coerceIn(0, last)
    }
    return index to claimed
}

/**
 * SwiftUI `Picker(.segmented)` calibrated to iOS 27.1 Liquid Glass:
 * compact 32dp track, unified tap/drag spring physics, resting pill that lifts above/beyond
 * track bounds as a clean luminous refractive lens magnifying underlying labels without doubling.
 */
@Composable
fun LiquidSegmentedPicker(
    options: List<String>,
    selected: String,
    select: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    backdrop: Backdrop? = null,
) {
    if (options.isEmpty()) return
    val tokens = LocalLiquidGlassTokens.current
    val controls = tokens.controls
    val appearance = liquidGlassAppearance()
    val dark = appearance.dark
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentSelect by rememberUpdatedState(select)
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val count = options.size

    var trackWidthPx by remember { mutableIntStateOf(0) }
    var finger by remember(options, rtl) { mutableStateOf<Float?>(null) }
    var tracking by remember(options, rtl) { mutableStateOf(false) }
    var pull by remember(options, rtl) { mutableStateOf(Offset.Zero) }

    val destination = finger ?: selectedIndex.toFloat()
    val travel = updateTransition(destination, label = "Segmented edges")
    val startEdge by travel.animateFloat(
        transitionSpec = {
            val leading = targetState < initialState
            when {
                tracking -> (if (leading) controls.segmentedTracking else controls.segmentedTrackingTrailing).spec
                leading -> controls.segmentedSelection.spec
                else -> controls.segmentedTrailingEdge.spec
            }
        },
        label = "Segmented start edge",
    ) { it }
    val endEdge by travel.animateFloat(
        transitionSpec = {
            val leading = targetState > initialState
            when {
                tracking -> (if (leading) controls.segmentedTracking else controls.segmentedTrackingTrailing).spec
                leading -> controls.segmentedSelection.spec
                else -> controls.segmentedTrailingEdge.spec
            }
        },
        label = "Segmented end edge",
    ) { it }

    val yOffset by animateFloatAsState(
        pull.y,
        if (tracking) tokens.tabs.hoverPullSpring.spec else tokens.press.centerRelease.spec,
        label = "Segmented vertical pull",
    )
    val aspect by animateFloatAsState(
        abs(pull.y) - abs(pull.x),
        if (tracking) tokens.tabs.hoverPullSpring.spec else tokens.press.release.spec,
        label = "Segmented aspect",
    )

    val tapLift = remember { Animatable(0f) }
    var prevIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragCommitIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selectedIndex) {
        val tapped = selectedIndex != prevIndex && dragCommitIndex != selectedIndex
        prevIndex = selectedIndex
        dragCommitIndex = null
        if (tapped) {
            tapLift.animateTo(.94f, tween(60))
            delay(65)
            tapLift.animateTo(0f, controls.segmentedSelection.spec)
        } else {
            tapLift.snapTo(0f)
        }
    }

    val heldStrength by animateFloatAsState(
        if (finger != null) 1f else 0f,
        if (finger != null) tokens.press.engage.spec else tokens.press.centerRelease.spec,
        label = "Segmented held strength",
    )
    val edgeStrain = (endEdge - startEdge).coerceIn(-.70f, .70f)
    val activeGlass = maxOf(heldStrength, tapLift.value, abs(edgeStrain) * 1.65f).coerceIn(0f, 1f)

    // Layer 1 (trackLayer) holds ONLY track fill + resting pill + dividers (no text labels, preventing ghosting).
    val trackLayer = rememberLayerBackdrop()
    val fallbackSurface = rememberSolidColorBackdrop(colors.surface)
    val cardOrSceneBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop ?: fallbackSurface
    val lensBackdrop = rememberCombinedBackdrop(cardOrSceneBackdrop, trackLayer)
    val rim = rememberLiquidRimHighlight(dark, layeredEdge = false)

    val trackFill = if (dark) Color(0xFF767680).copy(alpha = .24f) else Color(0xFF767680).copy(alpha = .10f)
    val restingThumbFill = if (dark) Color(0xFF636366) else Color.White

    val density = LocalDensity.current
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val haptics = rememberLiquidHaptics()

    // Do NOT clip outer Box so the lifted glass lens can expand vertically above/below the 32dp track.
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .onSizeChanged { trackWidthPx = it.width }
            .pointerInput(options, rtl, trackWidthPx) {
                if (trackWidthPx <= 0 || options.isEmpty()) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    fun logicalX(posX: Float) = if (rtl) trackWidthPx - posX else posX
                    val downIdx = segmentedRubberBandIndex(
                        logicalX(down.position.x),
                        trackWidthPx.toFloat(),
                        options.size,
                    ).roundToInt().coerceIn(0, options.lastIndex)
                    // Grabbing the currently selected segment locks immediately and tracks X regardless of Y excursion;
                    // tapping another segment waits for movement or 115ms hold so quick vertical flicks still scroll.
                    var claimed = downIdx == currentSelectedIndex
                    var lastHoverIdx = downIdx
                    tracking = claimed
                    if (claimed) {
                        finger = downIdx.toFloat()
                        down.consume()
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val p = event.changes.firstOrNull { it.id == down.id } ?: break
                            val dx = abs(p.position.x - down.position.x)
                            val dy = abs(p.position.y - down.position.y)
                            val heldLongEnough = p.uptimeMillis - down.uptimeMillis >= 115L
                            if (!claimed && !heldLongEnough && dy > slop && dy > dx * 1.15f) {
                                break
                            }
                            if (!claimed && ((dx > slop && dx >= dy) || heldLongEnough)) {
                                claimed = true
                                tracking = true
                            }
                            if (claimed) {
                                val lx = logicalX(p.position.x)
                                val slotWidth = trackWidthPx.toFloat() / options.size.toFloat()
                                val rawIdx = (lx - slotWidth * 0.5f) / slotWidth
                                val clamped = rawIdx.coerceIn(0f, options.lastIndex.toFloat())
                                val banded = segmentedRubberBandIndex(lx, trackWidthPx.toFloat(), options.size)
                                val hoverIdx = banded.roundToInt().coerceIn(0, options.lastIndex)
                                if (hoverIdx != lastHoverIdx) {
                                    lastHoverIdx = hoverIdx
                                    haptics(LiquidHaptic.SelectionTick)
                                }
                                finger = banded
                                pull = Offset(
                                    tanh(rawIdx - clamped),
                                    tanh((p.position.y - down.position.y) / size.height.coerceAtLeast(1)),
                                )
                                p.consume()
                            }
                            if (!p.pressed) {
                                val (endIdx, wasDrag) = resolveSegmentedCommitIndex(
                                    claimed = claimed,
                                    downIdx = downIdx,
                                    fingerIdx = finger,
                                    count = options.size,
                                )
                                if (wasDrag && endIdx != currentSelectedIndex) {
                                    dragCommitIndex = endIdx
                                } else if (!wasDrag && endIdx != currentSelectedIndex) {
                                    haptics(LiquidHaptic.SelectionTick)
                                }
                                currentSelect(options[endIdx])
                                p.consume()
                                break
                            }
                        }
                    } finally {
                        tracking = false
                        finger = null
                        pull = Offset.Zero
                    }
                }
            },
    ) {
        val centerIdx = ((startEdge + endEdge) * .5f).coerceIn(-.18f, (count - 1).toFloat() + .18f)
        val innerPad = 2.dp
        val innerPadPx = with(density) { innerPad.toPx() }
        val innerWidthPx = (trackWidthPx.toFloat() - innerPadPx * 2f).coerceAtLeast(0f)
        val slotPx = if (count > 0) innerWidthPx / count.toFloat() else 0f
        val restingThumbWidthPx = slotPx.coerceAtLeast(1f)
        val restingTravelPx = (innerWidthPx - restingThumbWidthPx).coerceAtLeast(0f)
        val normalizedPos = if (count <= 1) 0f else centerIdx / (count - 1).toFloat()
        val logicalOffset = normalizedPos * restingTravelPx
        val thumbOffsetX = if (rtl) innerPadPx + restingTravelPx - logicalOffset else innerPadPx + logicalOffset

        val (sx, sy) = segmentedLensScale(
            activeGlass = activeGlass,
            travelStrain = edgeStrain,
            widthGain = controls.segmentedLensWidthGain,
            heightGain = controls.segmentedLensHeightGain,
            travelStretch = controls.segmentedTravelStretch,
            pullAspect = aspect,
        )
        val lensW = (restingThumbWidthPx * sx).roundToInt().coerceAtLeast(1)
        val lensH = (with(density) { 28.dp.toPx() } * sy).roundToInt().coerceAtLeast(1)
        val lensCenterX = thumbOffsetX + restingThumbWidthPx * .5f
        val lensCenterY = with(density) { 16.dp.toPx() } + yOffset * with(density) { 2.5.dp.toPx() }

        // Layer 1: Track + resting pill (fades out cleanly as floating glass takes over) + dividers (NO text!).
        Box(
            Modifier.fillMaxSize().layerBackdrop(trackLayer, enabled = activeGlass > .01f)
                .clip(LiquidCapsule).background(trackFill),
        ) {
            if (trackWidthPx > 0) {
                val restingAlpha = (1f - activeGlass * 1.85f).coerceIn(0f, 1f)
                Spacer(
                    Modifier
                        .offset { IntOffset(thumbOffsetX.roundToInt(), innerPadPx.roundToInt()) }
                        .width(with(density) { restingThumbWidthPx.toDp() })
                        .height(28.dp)
                        .graphicsLayer { alpha = restingAlpha }
                        .drawBackdrop(
                            backdrop = emptyBackdrop(),
                            shape = { LiquidCapsule },
                            effects = {},
                            highlight = null,
                            shadow = {
                                Shadow(
                                    radius = 2.5.dp,
                                    offset = DpOffset(0.dp, 0.8.dp),
                                    color = Color.Black.copy(alpha = if (dark) .18f else .032f),
                                )
                            },
                            innerShadow = null,
                            onDrawSurface = { drawRect(restingThumbFill) },
                        ),
                )
            }

            Row(
                Modifier.fillMaxSize().padding(horizontal = innerPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { i, _ ->
                    Spacer(Modifier.weight(1f).fillMaxHeight())
                    if (i < options.lastIndex) {
                        val dividerAlpha = ((minOf(abs(centerIdx - i), abs(centerIdx - (i + 1))) - .18f) / .45f)
                            .coerceIn(0f, 1f)
                        Spacer(
                            Modifier.width(1.dp).height(14.dp)
                                .background(colors.separator.copy(alpha = .58f * dividerAlpha)),
                        )
                    }
                }
            }
        }

        // Layer 2: Luminous Floating Liquid Glass Lens rendered OVER the track during press / drag / tap travel.
        if (trackWidthPx > 0 && activeGlass > .01f) {
            Spacer(
                Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        val p = measurable.measure(Constraints.fixed(lensW, lensH))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val lx = (lensCenterX - lensW * .5f).roundToInt()
                            val ly = (lensCenterY - lensH * .5f).roundToInt()
                            p.place(lx, ly)
                        }
                    }
                    .drawBackdrop(
                        backdrop = lensBackdrop,
                        shape = { LiquidCapsule },
                        effects = {
                            val g = activeGlass
                            blur(appearance.controlBlurRadius.toPx() * .30f * (1f - g * .5f))
                            lens(
                                refractionHeight = 8.5.dp.toPx() * g,
                                refractionAmount = 18.dp.toPx() * g,
                                depthEffect = true,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            if (activeGlass > .01f) {
                                rim.copy(
                                    width = 1.0.dp,
                                    alpha = ((if (!dark) .94f else 1f) * tokens.highlightIntensity * activeGlass).coerceIn(0f, 1f),
                                )
                            } else {
                                Highlight.Ambient.copy(alpha = 0f)
                            }
                        },
                        shadow = {
                            Shadow(
                                radius = 7.dp,
                                offset = DpOffset(0.dp, 2.5.dp),
                                color = Color.Black.copy(alpha = (if (dark) .24f else .08f) * activeGlass),
                            )
                        },
                        innerShadow = null,
                        onDrawSurface = {
                            val g = activeGlass
                            if (!dark) drawRect(Color.White.copy(alpha = .05f * g))
                        },
                    ),
            )
        }

        // Layer 3: Labels rendered cleanly ONCE (unscaled outside lens, refracted + chromatic inside lens).
        val inkLayer = rememberGraphicsLayer()
        val lensInk = rememberGraphicsLayer()
        val supportsRuntimeShader = remember { isRuntimeShaderSupported() }
        val inkShaderHolder = remember { arrayOfNulls<RuntimeShader>(1) }
        val tabTokens = tokens.tabs
        Row(
            Modifier.fillMaxSize().drawWithCache {
                val lensPath = Path()
                val inkMargin = kotlin.math.ceil((6.dp + 1.8.dp + 4.dp).toPx()).toInt()
                onDrawWithContent {
                    if (trackWidthPx <= 0 || activeGlass <= .01f) {
                        drawContent()
                        return@onDrawWithContent
                    }
                    inkLayer.record {
                        this@onDrawWithContent.drawContent()
                    }
                    val lensRect = Rect(
                        left = lensCenterX - lensW * .5f,
                        top = lensCenterY - lensH * .5f,
                        right = lensCenterX + lensW * .5f,
                        bottom = lensCenterY + lensH * .5f,
                    )
                    lensPath.reset()
                    lensPath.addLiquidCapsule(lensRect)
                    clipPath(lensPath, ClipOp.Difference) {
                        drawLayer(inkLayer)
                    }
                    clipPath(lensPath) {
                        val inkShader = if (supportsRuntimeShader) {
                            inkShaderHolder[0] ?: SharedRenderEffectShaderCache.obtainRuntimeShader("TabInkShader", TabInkShader).also { inkShaderHolder[0] = it }
                        } else null
                        if (inkShader != null && count > 0) {
                            val cropW = kotlin.math.ceil(restingThumbWidthPx * 1.80f + 2f * inkMargin).toInt().coerceAtLeast(1)
                            val cropH = kotlin.math.ceil(with(density) { 28.dp.toPx() } * 1.95f + 2f * inkMargin).toInt().coerceAtLeast(1)
                            val cropLeft = (lensCenterX - cropW * .5f).roundToInt().toFloat()
                            val cropTop = (lensCenterY - cropH * .5f).roundToInt().toFloat()
                            val firstSlot = innerPadPx + slotPx * .5f
                            inkShader.setFloatUniform(
                                "rect",
                                lensCenterX - cropLeft,
                                lensCenterY - cropTop,
                                lensW * .5f,
                                lensH * .5f,
                            )
                            inkShader.setFloatUniform("strength", activeGlass)
                            inkShader.setFloatUniform("density", density.density)
                            inkShader.setFloatUniform(
                                "grid",
                                firstSlot - cropLeft,
                                size.height * .5f - cropTop,
                                slotPx,
                                controls.segmentedLensMagnification * activeGlass,
                            )
                            inkShader.setFloatUniform("dispersionPx", tabTokens.dispersion.toPx())
                            inkShader.setFloatUniform("bevelWidth", 8.dp.toPx())
                            inkShader.setFloatUniform("refractionPx", 5.dp.toPx())
                            inkShader.setFloatUniform("darkSurface", if (dark) 1f else 0f)
                            inkShader.setFloatUniform("sceneOffset", 0f)
                            inkShader.setFloatUniform("vibrancy", 0f)
                            inkShader.setFloatUniform("backing", 0f)
                            inkShader.setColorUniform("ink", colors.ink)
                            lensInk.renderEffect = RuntimeShaderEffect(inkShader, "content")
                            lensInk.record(size = IntSize(cropW, cropH)) {
                                translate(-cropLeft, -cropTop) { drawLayer(inkLayer) }
                            }
                            translate(cropLeft, cropTop) { drawLayer(lensInk) }
                        } else {
                            val mag = 1f + controls.segmentedLensMagnification * activeGlass
                            withTransform({
                                scale(mag, mag, Offset(lensCenterX, lensCenterY))
                            }) {
                                drawLayer(inkLayer)
                            }
                        }
                    }
                }
            }.padding(horizontal = innerPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { i, item ->
                val active = i == selectedIndex
                val highlighted = if (finger != null) i == centerIdx.roundToInt().coerceIn(0, options.lastIndex) else active
                Box(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                        role = Role.RadioButton
                        this.selected = active
                        onClick { currentSelect(item); true }
                    }.padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        item,
                        style = TextStyle(
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (i < options.lastIndex) {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}

/**
 * SwiftUI `Stepper` calibrated to iOS 27.1:
 * `94x32dp` segmented capsule with `colors.ink` `-` / `+` vectors, per-half press highlight,
 * hold-to-repeat stepping, and disabled boundary alpha.
 */
@Composable
fun LiquidStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    tint: Color,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    showValue: Boolean = true,
) {
    val dark = liquidGlassAppearance().dark
    val controls = LocalLiquidGlassTokens.current.controls
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)
    val pillFill = if (dark) Color(0xFF767680).copy(alpha = .24f) else Color(0xFF767680).copy(alpha = .12f)
    val pressColor = if (dark) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .09f)

    var minusHeld by remember { mutableStateOf(false) }
    var plusHeld by remember { mutableStateOf(false) }
    val haptics = rememberLiquidHaptics()

    LaunchedEffect(minusHeld, range) {
        if (!minusHeld) return@LaunchedEffect
        delay(controls.stepperRepeatDelayMillis)
        while (minusHeld && currentValue > range.first) {
            currentOnChange((currentValue - 1).coerceAtLeast(range.first))
            haptics(LiquidHaptic.SelectionTick)
            delay(controls.stepperRepeatIntervalMillis)
        }
    }
    LaunchedEffect(plusHeld, range) {
        if (!plusHeld) return@LaunchedEffect
        delay(controls.stepperRepeatDelayMillis)
        while (plusHeld && currentValue < range.last) {
            currentOnChange((currentValue + 1).coerceAtMost(range.last))
            haptics(LiquidHaptic.SelectionTick)
            delay(controls.stepperRepeatIntervalMillis)
        }
    }

    val motion = rememberControlPress()
    val minusHighlight by animateFloatAsState(if (minusHeld) 1f else 0f, tween(80), label = "Stepper -")
    val plusHighlight by animateFloatAsState(if (plusHeld) 1f else 0f, tween(80), label = "Stepper +")

    Row(modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(if (showValue) "$label  $value" else label, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
        Row(
            Modifier.size(width = 94.dp, height = 32.dp)
                .graphicsLayer { motion.apply(this) }
                .clip(LiquidCapsule)
                .background(pillFill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val decEnabled = value > range.first
            val incEnabled = value < range.last
            StepperHalfButton(
                symbol = "减少",
                plus = false,
                enabled = decEnabled,
                ink = colors.ink,
                highlightColor = pressColor,
                highlightAlpha = minusHighlight,
                onHeldChange = { minusHeld = it },
                onTap = {
                    if (currentValue > range.first) {
                        haptics(LiquidHaptic.SelectionTick)
                        currentOnChange(currentValue - 1)
                    }
                },
                motion = motion,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Spacer(
                Modifier.width(1.dp).height(18.dp)
                    .background(if (dark) Color.White.copy(alpha = .24f) else Color.Black.copy(alpha = .18f)),
            )
            StepperHalfButton(
                symbol = "增加",
                plus = true,
                enabled = incEnabled,
                ink = colors.ink,
                highlightColor = pressColor,
                highlightAlpha = plusHighlight,
                onHeldChange = { plusHeld = it },
                onTap = {
                    if (currentValue < range.last) {
                        haptics(LiquidHaptic.SelectionTick)
                        currentOnChange(currentValue + 1)
                    }
                },
                motion = motion,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun StepperHalfButton(
    symbol: String,
    plus: Boolean,
    enabled: Boolean,
    ink: Color,
    highlightColor: Color,
    highlightAlpha: Float,
    onHeldChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    motion: com.kyant.backdrop.interaction.LiquidPressMotion,
    modifier: Modifier = Modifier,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnHeldChange by rememberUpdatedState(onHeldChange)
    Box(
        modifier
            .background(highlightColor.copy(alpha = highlightColor.alpha * highlightAlpha))
            .pointerInput(motion) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentEnabled) return@awaitEachGesture
                    currentOnHeldChange(true)
                    motion.begin()
                    var triggered = false
                    var completed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val p = event.changes.firstOrNull { it.id == down.id } ?: break
                            val inside = p.position.x in 0f..size.width.toFloat() &&
                                p.position.y in 0f..size.height.toFloat()
                            currentOnHeldChange(p.pressed && inside)
                            if (!p.pressed) {
                                completed = inside
                                if (inside && p.uptimeMillis - down.uptimeMillis < 420L) {
                                    currentOnTap()
                                    triggered = true
                                }
                                p.consume()
                                break
                            }
                            val delta = p.position - down.position
                            if (abs(delta.y) > viewConfiguration.touchSlop * 2f && !inside) break
                            motion.drag(delta * .35f)
                        }
                    } finally {
                        currentOnHeldChange(false)
                        motion.release(completed = completed || triggered)
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = symbol
                if (!enabled) disabled()
                onClick {
                    if (enabled) {
                        currentOnTap()
                        true
                    } else false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val glyphAlpha = if (enabled) 1f else .30f
        Canvas(Modifier.size(14.dp)) {
            val stroke = 1.65.dp.toPx()
            val cy = size.height * .5f
            val cx = size.width * .5f
            val halfSpan = 6.dp.toPx()
            val c = ink.copy(alpha = glyphAlpha)
            drawLine(c, Offset(cx - halfSpan, cy), Offset(cx + halfSpan, cy), strokeWidth = stroke, cap = StrokeCap.Round)
            if (plus) {
                drawLine(c, Offset(cx, cy - halfSpan), Offset(cx, cy + halfSpan), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

enum class LiquidGaugeStyle { CircularCapacity, CircularOpen }

@Composable
fun LiquidGauge(
    value: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    style: LiquidGaugeStyle = LiquidGaugeStyle.CircularCapacity,
    brush: Brush? = null,
    trackColor: Color = Color.Unspecified,
) {
    val fraction = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    val startAngle = if (style == LiquidGaugeStyle.CircularCapacity) -90f else 135f
    val sweepTotal = if (style == LiquidGaugeStyle.CircularCapacity) 360f else 270f
    val activeBrush = brush ?: SolidColor(tint)
    val resolvedTrack = if (trackColor.isSpecified) trackColor
    else colors.ink.copy(alpha = if (liquidGlassAppearance().dark) .20f else .12f)
    Box(modifier.size(64.dp).semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = 6.dp.toPx()
            val origin = Offset(stroke / 2, stroke / 2)
            val extent = Size(size.width - stroke, size.height - stroke)
            val pen = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(resolvedTrack, startAngle, sweepTotal, false, origin, extent, style = pen)
            if (fraction > 0f) drawArc(activeBrush, startAngle, sweepTotal * fraction, false, origin, extent, style = pen)
        }
        BasicText("${(fraction * 100).roundToInt()}%", style = TextStyle(colors.ink, 13.sp, FontWeight.SemiBold))
    }
}

/** Linear capsule gauge matching SwiftUI `Gauge(value:) { Text(...) } currentValueLabel: { ... }`. */
@Composable
fun LiquidLinearGauge(
    value: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueLabel: String? = null,
    colors: LiquidListColors = liquidListColors(),
    brush: Brush? = null,
    trackHeight: Dp = 8.dp,
    trackColor: Color = Color.Unspecified,
) {
    val fraction = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    val shownValue = valueLabel ?: "${(fraction * 100).roundToInt()}%"
    val activeBrush = brush ?: SolidColor(tint)
    val resolvedTrack = if (trackColor.isSpecified) trackColor
    else colors.ink.copy(alpha = if (liquidGlassAppearance().dark) .20f else .12f)
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!label.isNullOrEmpty()) {
            BasicText(label, style = TextStyle(colors.ink, 15.sp))
        }
        Box(Modifier.fillMaxWidth().height(trackHeight).clip(LiquidCapsule).background(resolvedTrack)) {
            if (fraction > 0f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(LiquidCapsule).background(activeBrush))
            }
        }
        if (shownValue.isNotEmpty()) {
            BasicText(shownValue, style = TextStyle(colors.ink, 15.sp, FontWeight.Medium))
        }
    }
}

private val WheelEdgeFadeBrush = Brush.verticalGradient(
    0f to Color.Transparent,
    .06f to Color.White,
    .94f to Color.White,
    1f to Color.Transparent,
)

internal fun wheelRubberBandOffset(
    rawPosition: Float,
    maxIndex: Float,
    limitRows: Float = 3.2f,
    coefficient: Float = 0.55f,
): Float {
    val limit = limitRows.coerceAtLeast(0.1f)
    val clamped = rawPosition.coerceIn(0f, maxIndex.coerceAtLeast(0f))
    val excess = rawPosition - clamped
    return if (excess == 0f) clamped else clamped + limit * tanh(excess * coefficient / limit)
}

internal fun wheelInverseRubberBand(
    offset: Float,
    maxIndex: Float,
    limitRows: Float = 3.2f,
    coefficient: Float = 0.55f,
): Float {
    val limit = limitRows.coerceAtLeast(0.1f)
    val clamped = offset.coerceIn(0f, maxIndex.coerceAtLeast(0f))
    val excess = offset - clamped
    if (abs(excess) < 1e-4f) return clamped
    val ratio = (excess / limit).coerceIn(-0.98f, 0.98f)
    return clamped + (limit / coefficient.coerceAtLeast(0.01f)) * atanh(ratio)
}

internal data class WheelRowGeometry(
    val translationYPx: Float,
    val rotationXDeg: Float,
    val scaleX: Float,
    val alpha: Float,
)

internal fun wheelCylinderTransform(signedDistance: Float, rowHeightPx: Float): WheelRowGeometry {
    val d = signedDistance.coerceIn(-3.4f, 3.4f)
    val ad = abs(d)
    val yPx = d * rowHeightPx * (1f - 0.045f * ad)
    val rotX = -d * 21f
    val scale = (1f - 0.055f * ad).coerceAtLeast(0.75f)
    val alpha = when {
        ad < 0.35f -> 1f
        ad < 1.35f -> 1f - 0.50f * (ad - 0.35f).coerceIn(0f, 1f)
        else -> (0.50f - 0.16f * (ad - 1.35f)).coerceIn(0.28f, 1f)
    }
    return WheelRowGeometry(yPx, rotX, scale, alpha)
}

internal data class WheelFlingPlan(val overshootTarget: Float?, val settleIndex: Int)

internal fun resolveWheelFlingTarget(
    currentPos: Float,
    velocityRowsPerSec: Float,
    maxIndex: Float,
    friction: Float = 2.6f,
): WheelFlingPlan {
    val upper = maxIndex.coerceAtLeast(0f)
    val projected = currentPos + velocityRowsPerSec / friction.coerceAtLeast(0.1f)
    return when {
        projected < -0.04f -> {
            val overshoot = if (velocityRowsPerSec < -0.5f) {
                wheelRubberBandOffset(projected, upper).coerceAtLeast(-2.6f)
            } else {
                null
            }
            WheelFlingPlan(overshoot, 0)
        }
        projected > upper + 0.04f -> {
            val overshoot = if (velocityRowsPerSec > 0.5f) {
                wheelRubberBandOffset(projected, upper).coerceAtMost(upper + 2.6f)
            } else {
                null
            }
            WheelFlingPlan(overshoot, upper.roundToInt().coerceAtLeast(0))
        }
        else -> WheelFlingPlan(null, projected.roundToInt().coerceIn(0, upper.roundToInt()))
    }
}

/** Vertically snapping picker matching iOS 27.1 UIPickerView rubber-band overscroll, spring rebound, cylindrical rotation, and edge fade. */
@Composable
fun LiquidWheelPicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    showSelectionBar: Boolean = true,
) {
    if (options.isEmpty()) return
    val dark = liquidGlassAppearance().dark
    val density = LocalDensity.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position = remember { Animatable(index.toFloat()) }
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onSelect)
    val currentOptions by rememberUpdatedState(options)
    val currentSelectedIndex by rememberUpdatedState(index)
    val haptics = rememberLiquidHaptics()
    val rowHeight = maxOf(32.dp, with(density) { 20.sp.toDp() + 8.dp })
    val rowHeightPx = with(density) { rowHeight.toPx() }.coerceAtLeast(1f)
    val barFill = if (dark) Color(0xFF767680).copy(alpha = .24f) else Color(0xFF767680).copy(alpha = .12f)
    val settleSpring = remember { spring<Float>(dampingRatio = 0.72f, stiffness = 240f, visibilityThreshold = 0.002f) }
    val snapSpring = remember { spring<Float>(dampingRatio = 0.86f, stiffness = 340f, visibilityThreshold = 0.002f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(index, options.size) {
        if (!isDragging && !position.isRunning && abs(position.value - index.toFloat()) > 0.01f) {
            position.snapTo(index.toFloat())
        }
    }

    LaunchedEffect(options) {
        snapshotFlow { position.value.roundToInt().coerceIn(0, (options.size - 1).coerceAtLeast(0)) }
            .drop(1)
            .collect { haptics(LiquidHaptic.SelectionTick) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(rowHeight * 6)
            .selectableGroup()
            .pointerInput(options.size, rowHeightPx) {
                val maxIdx = (currentOptions.size - 1).toFloat().coerceAtLeast(0f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { position.stop() }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var rawDrag = wheelInverseRubberBand(position.value, maxIdx)
                    var dragged = false
                    val slop = viewConfiguration.touchSlop
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val vyPx = tracker.calculateVelocity().y
                                // Dragging UP (negative y) moves items up and increases selected index.
                                val velRows = -vyPx / rowHeightPx
                                val curPos = position.value
                                val plan = resolveWheelFlingTarget(curPos, velRows, maxIdx)
                                isDragging = false
                                scope.launch {
                                    if (plan.overshootTarget != null) {
                                        position.animateTo(
                                            plan.overshootTarget,
                                            tween(130, easing = FastOutSlowInEasing),
                                        )
                                        position.animateTo(plan.settleIndex.toFloat(), settleSpring)
                                    } else {
                                        val isOverscrolled = curPos < -0.02f || curPos > maxIdx + 0.02f
                                        position.animateTo(
                                            plan.settleIndex.toFloat(),
                                            if (isOverscrolled) settleSpring else snapSpring,
                                            initialVelocity = velRows.coerceIn(-12f, 12f),
                                        )
                                    }
                                    currentOptions.getOrNull(plan.settleIndex)?.let { chosen ->
                                        if (plan.settleIndex != currentSelectedIndex) callback(chosen)
                                    }
                                }
                                if (dragged) change.consume()
                                break
                            }
                            val dy = change.position.y - change.previousPosition.y
                            if (!dragged && abs(change.position.y - down.position.y) > slop) {
                                dragged = true
                            }
                            if (dragged) {
                                tracker.addPosition(change.uptimeMillis, change.position)
                                rawDrag -= dy / rowHeightPx
                                val visualPos = wheelRubberBandOffset(rawDrag, maxIdx)
                                scope.launch(start = CoroutineStart.UNDISPATCHED) { position.snapTo(visualPos) }
                                change.consume()
                            }
                        }
                    } finally {
                        if (isDragging) {
                            isDragging = false
                            val settle = position.value.roundToInt().coerceIn(0, maxIdx.roundToInt())
                            scope.launch {
                                position.animateTo(settle.toFloat(), settleSpring)
                                currentOptions.getOrNull(settle)?.let { chosen ->
                                    if (settle != currentSelectedIndex) callback(chosen)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showSelectionBar) {
            Spacer(Modifier.fillMaxWidth().height(32.dp).clip(LiquidCapsule).background(barFill))
        }
        Box(
            Modifier.fillMaxSize()
                .clip(androidx.compose.ui.graphics.RectangleShape)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(WheelEdgeFadeBrush, blendMode = BlendMode.DstIn)
                },
            contentAlignment = Alignment.Center,
        ) {
            val cur = position.value
            val minPage = kotlin.math.floor(cur - 3.2f).toInt().coerceAtLeast(0)
            val maxPage = kotlin.math.ceil(cur + 3.2f).toInt().coerceAtMost(options.lastIndex)
            for (page in minPage..maxPage) {
                key(page) {
                    val signedDist = page.toFloat() - cur
                    val geom = wheelCylinderTransform(signedDist, rowHeightPx)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                translationY = geom.translationYPx
                                rotationX = geom.rotationXDeg
                                cameraDistance = 12f * density.density
                                scaleX = geom.scaleX
                                scaleY = geom.scaleX
                                alpha = geom.alpha
                            }
                            .selectable(page == index, interactionSource = null, indication = null, role = Role.RadioButton) {
                                if (!isDragging) {
                                    scope.launch {
                                        position.animateTo(page.toFloat(), snapSpring)
                                        options.getOrNull(page)?.let(callback)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            options[page],
                            style = TextStyle(colors.ink, 20.sp, FontWeight.Normal),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
