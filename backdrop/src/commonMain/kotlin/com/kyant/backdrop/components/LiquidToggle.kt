package com.kyant.backdrop.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Resolves discrete ON/OFF track color state during drag using a threshold with small hysteresis
 * instead of continuously blending track color across `0..1`, matching iOS 27.1 Switch behavior.
 */
internal fun resolveToggleTrackActive(
    fraction: Float,
    currentlyActive: Boolean,
    threshold: Float = 0.5f,
    hysteresis: Float = 0.02f,
): Boolean = if (currentlyActive) {
    fraction >= (threshold - hysteresis).coerceIn(0f, 1f)
} else {
    fraction >= (threshold + hysteresis).coerceIn(0f, 1f)
}

/**
 * iOS 26/27 Liquid Glass `Toggle` switch calibrated to native optical and tactile behavior:
 * - Crisp resting white capsule (`37 × 24 dp` in `60 × 28 dp` track) with soft drop shadow.
 * - On press/drag: smoothly lifts into a plump liquid-glass droplet (`1.38x × 1.56x`, ~1.37:1)
 *   that refracts the real green/neutral track edge with feathered upper/lower caustic arcs.
 * - Unified single-impulse spring on tap (no double-pump) and symmetric left/right velocity stretch.
 */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val checked by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onSelect)
    val appearance = liquidGlassAppearance()
    val tokens = LocalLiquidGlassTokens.current
    val effectiveBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop
    val isLightTheme = !appearance.dark
    val accentColor = when {
        tint.isSpecified -> tint
        isLightTheme -> Color(0xFF34C759)
        else -> Color(0xFF30D158)
    }
    val trackColor =
        if (isLightTheme) Color(0xFF787880).copy(0.22f)
        else Color(0xFF787880).copy(0.36f)
    val rim = rememberLiquidRimHighlight(!isLightTheme, layeredEdge = false)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val trackWidthDp = 60.dp
    val thumbWidthDp = 37.dp
    val thumbHeightDp = 24.dp
    val thumbPadDp = 2.dp
    val dragWidth = with(density) { (trackWidthDp - thumbWidthDp - thumbPadDp * 2).toPx() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()

    val valueAnim = remember { Animatable(if (checked()) 1f else 0f, 0.0005f) }
    val pressAnim = remember { Animatable(0f, 0.001f) }
    val tapPulse = remember { Animatable(0f, 0.001f) }

    var trackActive by remember { mutableStateOf(checked()) }
    var prevChecked by remember { mutableStateOf(checked()) }
    var dragCommitPending by remember { mutableStateOf<Boolean?>(null) }
    var velocityPx by remember { mutableFloatStateOf(0f) }

    val settleSpring = remember { GlassSpring.swiftUI(response = 0.28f, dampingFraction = 0.74f).spec }
    val pressEngageSpring = remember { GlassSpring.swiftUI(response = 0.22f, dampingFraction = 0.76f).spec }
    val pressReleaseSpring = remember { GlassSpring.swiftUI(response = 0.30f, dampingFraction = 0.70f).spec }

    LaunchedEffect(Unit) {
        snapshotFlow { checked() }
            .collectLatest { isSelected ->
                val changed = isSelected != prevChecked
                val wasDrag = dragCommitPending == isSelected
                prevChecked = isSelected
                dragCommitPending = null
                trackActive = isSelected
                val target = if (isSelected) 1f else 0f
                launch {
                    valueAnim.animateTo(target, settleSpring)
                }
                if (changed && !wasDrag) {
                    launch {
                        // Continue monotonically from current pressAnim to avoid any dip/double-pump on tap release.
                        tapPulse.snapTo(pressAnim.value)
                        tapPulse.animateTo(0.92f, tween(65))
                        delay(55)
                        tapPulse.animateTo(0f, pressReleaseSpring)
                    }
                }
            }
    }

    val smoothVel = animateFloatAsState(
        targetValue = velocityPx,
        animationSpec = GlassSpring.swiftUI(response = 0.18f, dampingFraction = 0.85f).spec,
        label = "Toggle velocity stretch",
    )

    val animatedTrackColor = animateColorAsState(
        targetValue = if (trackActive) accentColor else trackColor,
        animationSpec = tween(durationMillis = 150),
        label = "Toggle track color",
    )
    val trackBackdrop = rememberLayerBackdrop()
    val combinedThumbBackdrop = rememberCombinedBackdrop(effectiveBackdrop, trackBackdrop)
    val isGlassActive by remember {
        derivedStateOf { maxOf(pressAnim.value, tapPulse.value) > 0.001f }
    }
    fun effectivePress(): Float = maxOf(pressAnim.value, tapPulse.value).coerceIn(0f, 1f)

    Box(
        modifier
            .heightIn(min = 44.dp)
            .pointerInput(dragWidth, touchSlop, isLtr) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    down.consume()
                    scope.launch { pressAnim.animateTo(1f, pressEngageSpring) }

                    val startFrac = valueAnim.value.coerceIn(0f, 1f)
                    var totalDx = 0f
                    var didDrag = false
                    var lastX = down.position.x
                    var lastTime = down.uptimeMillis
                    var currentFrac = startFrac

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val p = event.changes.firstOrNull { it.id == down.id } ?: break
                            p.consume()
                            val stepDx = p.position.x - lastX
                            val dt = (p.uptimeMillis - lastTime).coerceAtLeast(1L)
                            lastX = p.position.x
                            lastTime = p.uptimeMillis
                            totalDx += stepDx
                            if (abs(totalDx) > touchSlop * 0.65f) didDrag = true

                            if (didDrag) {
                                val signedDelta = (if (isLtr) totalDx else -totalDx) / dragWidth
                                val raw = startFrac + signedDelta
                                // Soft rubber-band overhang past 0 and 1 just like iOS 27 UISwitch.
                                val banded = when {
                                    raw < 0f -> 0.08f * tanh(raw / 0.08f)
                                    raw > 1f -> 1f + 0.08f * tanh((raw - 1f) / 0.08f)
                                    else -> raw
                                }
                                currentFrac = banded
                                velocityPx = (stepDx / dt) * 1000f
                                scope.launch { valueAnim.snapTo(banded) }
                                val nextTrack = resolveToggleTrackActive(banded.coerceIn(0f, 1f), trackActive)
                                if (nextTrack != trackActive) {
                                    trackActive = nextTrack
                                    haptics(LiquidHaptic.ToggleFlip)
                                }
                            }

                            if (!p.pressed) {
                                velocityPx = 0f
                                if (didDrag) {
                                    val next = resolveToggleTrackActive(currentFrac.coerceIn(0f, 1f), checked())
                                    if (next != trackActive) haptics(LiquidHaptic.ToggleFlip)
                                    trackActive = next
                                    dragCommitPending = next
                                    if (next == checked()) {
                                        // Releasing back onto original side still springs back smoothly.
                                        scope.launch { valueAnim.animateTo(if (next) 1f else 0f, settleSpring) }
                                    } else {
                                        change(next)
                                    }
                                } else {
                                    val next = !checked()
                                    trackActive = next
                                    dragCommitPending = null
                                    haptics(LiquidHaptic.ToggleFlip)
                                    change(next)
                                }
                                break
                            }
                        }
                    } finally {
                        velocityPx = 0f
                        scope.launch { pressAnim.animateTo(0f, pressReleaseSpring) }
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked())
                onClick {
                    val next = !checked()
                    trackActive = next
                    dragCommitPending = null
                    change(next)
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop, enabled = isGlassActive)
                .clip(LiquidCapsule)
                .drawBehind { drawRect(animatedTrackColor.value) }
                .size(trackWidthDp, 28.dp),
        )

        val trackWPx = with(density) { trackWidthDp.toPx() }
        val thumbBaseW = with(density) { thumbWidthDp.toPx() }
        val thumbBaseH = with(density) { thumbHeightDp.toPx() }
        val padPx = with(density) { thumbPadDp.toPx() }
        val heldThumbH = thumbBaseH * 1.56f
        val topSpecularBrush = remember(heldThumbH) {
            val bandH = heldThumbH * 0.11f
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = .40f), Color.White.copy(alpha = 0f)),
                startY = heldThumbH * 0.05f,
                endY = heldThumbH * 0.05f + bandH,
            )
        }
        val bottomSpecularBrush = remember(heldThumbH) {
            val bandH = heldThumbH * 0.11f
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = .26f)),
                startY = heldThumbH * 0.95f - bandH,
                endY = heldThumbH * 0.95f,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val p = effectivePress()
                    val v = valueAnim.value
                    val velStretch = (abs(smoothVel.value) / 900f).fastCoerceIn(0f, 0.06f)
                    val sx = lerp(1f, 1.38f, p) * (1f + velStretch)
                    val sy = lerp(1f, 1.56f, p) * (1f - velStretch * 0.25f)
                    val w = (thumbBaseW * sx).roundToInt().coerceAtLeast(1)
                    val h = (thumbBaseH * sy).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    val restingCenterX = if (isLtr) {
                        padPx + thumbBaseW * 0.5f + dragWidth * v
                    } else {
                        trackWPx - (padPx + thumbBaseW * 0.5f + dragWidth * v)
                    }
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val lx = (restingCenterX - w * 0.5f).roundToInt()
                        val ly = ((constraints.maxHeight - h) * 0.5f).roundToInt()
                        placeable.place(lx, ly)
                    }
                }
                .drawBackdrop(
                    backdrop = if (isGlassActive) combinedThumbBackdrop else emptyBackdrop(),
                    shape = { LiquidCapsule },
                    effects = {
                        val p = effectivePress()
                        if (p > 0.001f) {
                            blur(appearance.blurRadius.toPx() * (1f - p) * .25f)
                            lens(
                                refractionHeight = 5.2.dp.toPx() * p,
                                refractionAmount = 11.5.dp.toPx() * p,
                                depthEffect = false,
                                chromaticAberration = true,
                            )
                        }
                    },
                    highlight = {
                        val p = effectivePress()
                        rim.copy(
                            width = 1.1.dp,
                            alpha = ((if (isLightTheme) 0.96f else 1f) * tokens.highlightIntensity * p).coerceIn(0f, 1f),
                        )
                    },
                    shadow = {
                        val p = effectivePress()
                        Shadow(
                            radius = lerp(3.5f, 8f, p).dp,
                            offset = DpOffset(0.dp, lerp(1.2f, 2.5f, p).dp),
                            color = Color.Black.copy(
                                alpha = lerp(
                                    if (isLightTheme) 0.07f else 0.22f,
                                    if (isLightTheme) 0.11f else 0.26f,
                                    p,
                                ),
                            ),
                        )
                    },
                    innerShadow = null,
                    onDrawSurface = {
                        val p = effectivePress()
                        // Solid white at rest -> crystal clear refracting liquid glass when pressed (no milky veil).
                        drawRect(Color.White.copy(alpha = (1f - p * 1.15f).coerceIn(0f, 1f)))
                        if (p > 0.02f) {
                            // Feathered curved top/bottom caustic arcs only, keeping the droplet body transparent.
                            val bandH = size.height * 0.11f
                            val insetX = size.width * 0.18f
                            val bandW = (size.width - insetX * 2f).coerceAtLeast(1f)
                            val cr = CornerRadius(bandH * 0.5f, bandH * 0.5f)
                            drawRoundRect(
                                brush = topSpecularBrush,
                                topLeft = Offset(insetX, size.height * 0.05f),
                                size = Size(bandW, bandH),
                                cornerRadius = cr,
                                alpha = p,
                            )
                            drawRoundRect(
                                brush = bottomSpecularBrush,
                                topLeft = Offset(insetX, size.height * 0.95f - bandH),
                                size = Size(bandW, bandH),
                                cornerRadius = cr,
                                alpha = p,
                            )
                        }
                    },
                ),
        )
    }
}
