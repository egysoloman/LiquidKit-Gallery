package com.kyant.backdrop.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * SwiftUI `Picker(.inline)` rendering selectable rows with trailing iOS checkmarks
 * and native full-bleed press highlights inside a grouped card.
 */
@Composable
fun LiquidInlinePicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    LiquidFormSection(title = title, modifier = modifier.selectableGroup(), colors = colors) {
        options.forEach { option ->
            val isChosen = option == selected
            row(inset = false, onClick = null) {
                val setRowHighlight = LocalFormRowHighlight.current
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                var holding by remember { mutableStateOf(false) }
                LaunchedEffect(pressed) {
                    if (pressed) {
                        delay(110L)
                        holding = true
                    } else {
                        holding = false
                    }
                }
                LaunchedEffect(holding, setRowHighlight) {
                    setRowHighlight?.invoke(holding)
                }
                DisposableEffect(setRowHighlight) {
                    onDispose { setRowHighlight?.invoke(false) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                            onSelect(option)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .heightIn(min = 28.dp)
                        .semantics { this.selected = isChosen },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText(option, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isChosen) {
                        LiquidIcon(LiquidSymbol.Check, tint, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Immutable
data class LiquidPaletteOption(
    val id: String,
    val label: String,
    val color: Color = Color.Unspecified,
    val symbol: LiquidSymbol? = null,
)

/**
 * SwiftUI `Picker(.palette)` compact horizontal selector displaying color swatches or icon pills
 * with an animated iOS selection ring and press spring.
 */
@Composable
fun LiquidPalettePicker(
    options: List<LiquidPaletteOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    val dark = liquidGlassAppearance().dark
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isChosen = option.id == selectedId
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val ringAlpha by animateFloatAsState(if (isChosen) 1f else 0f, tween(160), label = "Palette ring")
            val pressScale by animateFloatAsState(
                if (pressed) 0.90f else if (isChosen) 1.06f else 1f,
                GlassSpring.snappy(0.24f).spec,
                label = "Palette scale",
            )
            val fill = if (option.color.isSpecified) option.color else if (dark) Color(0xff2c2c2e) else Color(0xffe5e5ea)
            val ringColor = if (option.color.isSpecified) option.color else tint
            Box(
                Modifier.size(38.dp)
                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                    .clip(CircleShape)
                    .border(2.2.dp, ringColor.copy(alpha = ringAlpha), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .selectable(selected = isChosen, interactionSource = interaction, indication = null, role = Role.RadioButton) {
                        onSelect(option.id)
                    }
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center,
            ) {
                if (option.symbol != null) {
                    val iconTint = if (option.color.isSpecified) liquidContentColorFor(option.color)
                    else if (isChosen) tint else colors.ink
                    LiquidIcon(option.symbol, iconTint, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Immutable
data class LiquidTabOption(
    val id: String,
    val label: String,
    val symbol: LiquidSymbol? = null,
)

/**
 * iOS 27 `Picker(.tabs)` (`TabPickerStyle`): horizontal capsule picker matching native
 * resting drop shadow + interactive lifted Liquid Glass refractive lens on press/drag/glide,
 * sharing the exact two-layer optical architecture and spring physics of `LiquidSegmentedPicker`.
 */
@Composable
fun LiquidTabsPicker(
    options: List<LiquidTabOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    val tokens = LocalLiquidGlassTokens.current
    val controls = tokens.controls
    val appearance = liquidGlassAppearance()
    val dark = appearance.dark
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentSelect by rememberUpdatedState(onSelect)
    val selectedIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val count = options.size

    var trackWidthPx by remember { mutableIntStateOf(0) }
    var finger by remember(options, rtl) { mutableStateOf<Float?>(null) }
    var tracking by remember(options, rtl) { mutableStateOf(false) }
    var pull by remember(options, rtl) { mutableStateOf(Offset.Zero) }

    val destination = finger ?: selectedIndex.toFloat()
    val travel = updateTransition(destination, label = "TabsPicker edges")
    val startEdge by travel.animateFloat(
        transitionSpec = {
            val leading = targetState < initialState
            when {
                tracking -> (if (leading) controls.segmentedTracking else controls.segmentedTrackingTrailing).spec
                leading -> controls.segmentedSelection.spec
                else -> controls.segmentedTrailingEdge.spec
            }
        },
        label = "TabsPicker start edge",
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
        label = "TabsPicker end edge",
    ) { it }

    val yOffset by animateFloatAsState(
        pull.y,
        if (tracking) tokens.tabs.hoverPullSpring.spec else tokens.press.centerRelease.spec,
        label = "TabsPicker vertical pull",
    )
    val aspect by animateFloatAsState(
        abs(pull.y) - abs(pull.x),
        if (tracking) tokens.tabs.hoverPullSpring.spec else tokens.press.release.spec,
        label = "TabsPicker aspect",
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
        label = "TabsPicker held strength",
    )
    val edgeStrain = (endEdge - startEdge).coerceIn(-.70f, .70f)
    val activeGlass = maxOf(heldStrength, tapLift.value, abs(edgeStrain) * 1.65f).coerceIn(0f, 1f)

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
    val trackHeight = 36.dp
    val innerPad = 2.dp
    val pillHeight = trackHeight - innerPad * 2

    // Outer Box stays unclipped so the lifted glass lens can expand cleanly beyond the track bounds.
    Box(
        modifier.fillMaxWidth().height(trackHeight)
            .onSizeChanged { trackWidthPx = it.width }
            .selectableGroup()
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
                                currentSelect(options[endIdx].id)
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
        val lensH = (with(density) { pillHeight.toPx() } * sy).roundToInt().coerceAtLeast(1)
        val lensCenterX = thumbOffsetX + restingThumbWidthPx * .5f
        val lensCenterY = with(density) { (trackHeight * .5f).toPx() } + yOffset * with(density) { 2.5.dp.toPx() }

        // Layer 1: Track + resting elevated pill (soft drop shadow, NO harsh border!) + subtle separators.
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
                        .height(pillHeight)
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
        }

        // Layer 2: Lifted Floating Liquid Glass Lens rendered OVER the track during press / drag / glide.
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
                            rim.copy(
                                width = 1.0.dp,
                                alpha = ((if (!dark) .94f else 1f) * tokens.highlightIntensity * activeGlass).coerceIn(0f, 1f),
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = (10.dp * activeGlass).coerceAtLeast(.5.dp),
                                offset = DpOffset(0.dp, 2.5.dp * activeGlass),
                                color = Color.Black.copy(alpha = (if (dark) .28f else .08f) * activeGlass),
                            )
                        },
                        innerShadow = if (dark) {
                            {
                                InnerShadow(
                                    radius = 2.dp * activeGlass,
                                    alpha = .18f * activeGlass,
                                )
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            val wash = if (dark) Color.White.copy(alpha = .07f * activeGlass)
                            else Color.White.copy(alpha = .18f * activeGlass)
                            drawRect(wash)
                        },
                    ),
            )
        }

        // Layer 3: Crisp tab icons + labels with subtle magnification under the active lens.
        Row(
            Modifier.fillMaxSize().padding(horizontal = innerPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { idx, option ->
                val active = option.id == selectedId
                val proximity = (1f - abs(centerIdx - idx)).coerceIn(0f, 1f)
                val labelScale = 1f + controls.segmentedLensMagnification * activeGlass * proximity
                val ink by animateColorAsState(
                    if (active) tint else colors.ink.copy(alpha = .78f),
                    tween(140),
                    label = "TabsPicker ink",
                )
                Row(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics {
                            selected = active
                            contentDescription = option.label
                        }
                        .graphicsLayer { scaleX = labelScale; scaleY = labelScale }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (option.symbol != null) {
                        LiquidIcon(option.symbol, ink, Modifier.size(16.dp))
                    }
                    BasicText(
                        option.label,
                        style = TextStyle(ink, 14.sp, if (active) FontWeight.SemiBold else FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

enum class LiquidSafeAreaEdge { Top, Bottom }

/**
 * iOS 27 `.safeAreaBar(edge:)` edge-attached frosted Liquid Glass bar with progressive blur
 * and soft vertical fade mask so scrolling content glides cleanly beneath.
 */
@Composable
fun LiquidSafeAreaBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    edge: LiquidSafeAreaEdge = LiquidSafeAreaEdge.Bottom,
    colors: LiquidListColors = liquidListColors(),
    blurRadius: Dp = 16.dp,
    respectSystemBars: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val dark = liquidGlassAppearance().dark
    val maskBrush = remember(edge) {
        if (edge == LiquidSafeAreaEdge.Top) {
            Brush.verticalGradient(0f to Color.White, .78f to Color.White.copy(alpha = .94f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, .22f to Color.White.copy(alpha = .94f), 1f to Color.White)
        }
    }
    val scrimBrush = remember(edge, dark, colors.background) {
        val strong = colors.background.copy(alpha = if (dark) .88f else .86f)
        val mid = colors.background.copy(alpha = if (dark) .74f else .72f)
        if (edge == LiquidSafeAreaEdge.Top) {
            Brush.verticalGradient(0f to strong, .72f to mid, 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, .28f to mid, 1f to strong)
        }
    }
    val safeMod = when {
        !respectSystemBars -> Modifier
        edge == LiquidSafeAreaEdge.Top -> Modifier.liquidTopSafeArea()
        else -> Modifier.liquidBottomSafeArea()
    }
    Box(modifier.fillMaxWidth()) {
        Spacer(
            Modifier.matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(maskBrush, blendMode = BlendMode.DstIn)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = { blur(blurRadius.toPx()) },
                    highlight = null,
                    shadow = null,
                    onDrawSurface = { drawRect(scrimBrush) },
                ),
        )
        Row(
            Modifier.fillMaxWidth()
                .then(safeMod)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
