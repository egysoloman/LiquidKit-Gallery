package com.kyant.backdrop.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** SwiftUI `Picker(.menu)` row displaying current choice with dual chevron and Liquid Glass morphing popup menu. */
@Composable
fun LiquidMenuPicker(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    colors: LiquidListColors = liquidListColors(),
) {
    if (options.isEmpty()) return
    val registry = LocalLiquidModalRegistry.current
    val currentSelect by rememberUpdatedState(onSelect)
    val menuState = rememberLiquidMenuState { item -> currentSelect(item.key) }
    var closingReturn by remember { mutableStateOf(false) }
    LaunchedEffect(menuState.expanded) {
        if (menuState.expanded) closingReturn = true
    }
    val menuItems = remember(options, selected) {
        options.map { option ->
            LiquidMenuItem(key = option, title = option, checked = option == selected)
        }
    }
    val resolvedBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop ?: emptyBackdrop()

    if (registry != null) {
        val entry = remember(menuState) { LiquidModalMenuEntry(menuState) }
        SideEffect {
            entry.active = menuState.expanded || closingReturn || menuState.dismissing || menuState.branches.isNotEmpty() || menuState.stackReturn != null
            entry.overlay = { hostOrigin, maxW, maxH ->
                if (menuState.expanded || closingReturn) {
                    val anchor = menuState.openingBounds?.translate(-hostOrigin)
                    if (anchor != null) {
                        Box(
                            Modifier.fillMaxSize().onPreviewKeyEvent {
                                if (menuState.expanded && it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                                    menuState.dismiss()
                                    true
                                } else false
                            },
                        ) {
                            CompositionLocalProvider(LocalMenuBackdrop provides resolvedBackdrop) {
                                LiquidPopupPanel(
                                    state = menuState,
                                    backdrop = resolvedBackdrop,
                                    items = menuItems,
                                    anchor = anchor,
                                    hostWidth = maxW,
                                    hostHeight = maxH,
                                    fromBorderlessTrigger = true,
                                    touchX = menuState.openingPoint?.x?.minus(hostOrigin.x),
                                    leadingCheckAtRoot = true,
                                    onClosed = { closingReturn = false },
                                )
                            }
                        }
                    } else if (!menuState.expanded) {
                        closingReturn = false
                    }
                }
            }
        }
        DisposableEffect(registry, entry) {
            registry.menus.add(entry)
            onDispose { registry.menus.remove(entry) }
        }
        LiquidMenuPickerRow(label, selected, tint, menuState, colors, modifier)
    } else {
        val overlayOpen = menuState.expanded || closingReturn
        SideEffect { menuState.overlayActive = overlayOpen }
        DisposableEffect(menuState) { onDispose { menuState.overlayActive = false } }
        LiquidMenuHost(
            state = menuState,
            backdrop = resolvedBackdrop,
            modifier = modifier,
            rootOverlay = { behind ->
                if (menuState.expanded || closingReturn) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val origin = menuState.hostLayout?.let {
                            if (it.isAttached) it.boundsInRoot().topLeft else Offset.Zero
                        } ?: Offset.Zero
                        val anchor = menuState.openingBounds?.translate(-origin)
                        if (anchor != null) {
                            LiquidPopupPanel(
                                state = menuState,
                                backdrop = behind,
                                items = menuItems,
                                anchor = anchor,
                                hostWidth = maxWidth,
                                hostHeight = maxHeight,
                                fromBorderlessTrigger = true,
                                touchX = menuState.openingPoint?.x?.minus(origin.x),
                                leadingCheckAtRoot = true,
                                onClosed = { closingReturn = false },
                            )
                        } else if (!menuState.expanded) {
                            closingReturn = false
                        }
                    }
                }
            },
        ) {
            LiquidMenuPickerRow(label, selected, tint, menuState, colors, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LiquidMenuPickerRow(
    label: String,
    selected: String,
    tint: Color,
    menuState: LiquidMenuState,
    colors: LiquidListColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
        Row(
            Modifier.liquidBorderlessMenuTrigger(menuState)
                .clip(LiquidCapsule)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(selected, style = TextStyle(tint, 17.sp, FontWeight.Normal))
            LiquidIcon(LiquidSymbol.ChevronUpDown, tint, Modifier.size(13.dp))
        }
    }
}

private val ColorPresetsDefault = listOf(
    Color.Black,
    Color(0xff007aff),
    Color(0xff34c759),
    Color(0xffffcc00),
    Color(0xffff3b30),
)

private val SpectrumHueStops = listOf(
    Color(0xffff0000),
    Color(0xffffff00),
    Color(0xff00ff00),
    Color(0xff00ffff),
    Color(0xff0000ff),
    Color(0xffff00ff),
    Color(0xffff0000),
)
private val SpectrumHueBrush = Brush.verticalGradient(SpectrumHueStops)
private val SpectrumWhiteBrush = Brush.horizontalGradient(0f to Color.White, .5f to Color.Transparent)
private val SpectrumBlackBrush = Brush.horizontalGradient(.5f to Color.Transparent, 1f to Color.Black)
private val ColorWellRainbowBrush = Brush.sweepGradient(
    listOf(Color.Red, Color(0xffffaa00), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color(0xffcc00ff), Color.Red),
)

private val PaletteGridMatrix: List<List<Color>> by lazy {
    val grays = List(12) { col ->
        val v = 1f - col / 11f
        Color(v, v, v, 1f)
    }
    // 12 hues ordered left-to-right matching iOS 27.1 ColorPicker grid
    val columnHues = floatArrayOf(196f, 218f, 245f, 272f, 305f, 342f, 12f, 28f, 42f, 54f, 68f, 88f)
    val rows = (0 until 9).map { r ->
        List(12) { c ->
            val hue = columnHues[c]
            val sat: Float
            val value: Float
            if (r < 4) {
                sat = 1f
                value = .24f + r * .17f
            } else if (r == 4) {
                sat = 1f
                value = .95f
            } else {
                val t = (r - 4) / 4.3f
                sat = (1f - t * .78f).coerceIn(.20f, 1f)
                value = 1f
            }
            hsvToColor(hue, sat, value)
        }
    }
    listOf(grays) + rows
}

/** SwiftUI `ColorPicker` row with rainbow-ringed ColorWell opening an iOS 27.1 Grid / Spectrum / Sliders Liquid Glass popover. */
@Composable
fun LiquidColorPicker(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = color,
    backdrop: Backdrop? = null,
    colors: LiquidListColors = liquidListColors(),
    supportsOpacity: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val wellBounds = remember { mutableStateOf<Rect?>(null) }

    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
        Canvas(
            Modifier.size(28.dp)
                .liquidPopoverAnchor(wellBounds)
                .liquidControlPress()
                .clip(CircleShape)
                .clickable(interactionSource = null, indication = null, role = Role.Button) { open = !open }
                .semantics { contentDescription = label },
        ) {
            val strokeWidth = 3.2.dp.toPx()
            val radius = size.minDimension / 2f
            drawCircle(ColorWellRainbowBrush, radius = radius - strokeWidth / 2f, style = Stroke(strokeWidth))
            drawCircle(colors.surface, radius = radius - strokeWidth * .95f)
            drawCircle(color.copy(alpha = 1f), radius = (radius - strokeWidth * 1.38f).coerceAtLeast(1f))
        }
    }

    if (open) {
        val appearance = liquidGlassAppearance()
        val dark = appearance.dark
        val tabs = remember { listOf("网格", "光谱", "滑块") }
        var activeTab by remember { mutableStateOf("网格") }
        val presets = remember { mutableStateListOf<Color>().apply { addAll(ColorPresetsDefault) } }

        LiquidPopover(
            onDismissRequest = { open = false },
            anchorBoundsInRoot = wellBounds.value,
            backdrop = backdrop,
            colors = colors,
            maxWidth = 332.dp,
            blurRadius = maxOf(appearance.menuBlurRadius, 22.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(label, Modifier.weight(1f).semantics { heading() }, style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold))
                Box(
                    Modifier.size(34.dp)
                        .liquidControlPress()
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFF767680).copy(alpha = .28f) else Color(0xFF767680).copy(alpha = .14f))
                        .border(.6.dp, colors.separator.copy(alpha = .35f), CircleShape)
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { open = false }
                        .semantics { contentDescription = "关闭" },
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidIcon(LiquidSymbol.Close, colors.ink, Modifier.size(15.dp))
                }
            }

            LiquidSegmentedPicker(
                options = tabs,
                selected = activeTab,
                select = { activeTab = it },
                colors = colors,
                backdrop = null,
            )

            when (activeTab) {
                "网格" -> ColorGridPanel(color, onColorChange)
                "光谱" -> ColorSpectrumPanel(color, dark, onColorChange)
                else -> ColorSlidersPanel(color, tint, dark, colors, onColorChange)
            }

            if (supportsOpacity) {
                val opaque = color.copy(alpha = 1f)
                val alphaPct = "${(color.alpha.coerceIn(0f, 1f) * 100f).roundToInt()}%"
                GradientChannelSlider(
                    label = "不透明度",
                    valueText = alphaPct,
                    fraction = color.alpha.coerceIn(0f, 1f),
                    gradientColors = listOf(opaque.copy(alpha = 0f), opaque),
                    thumbColor = color,
                    dark = dark,
                    colors = colors,
                    checkerboard = true,
                    onValueChange = { onColorChange(color.copy(alpha = it)) },
                )
            }

            Spacer(Modifier.fillMaxWidth().height(.5.dp).background(colors.separator.copy(alpha = .7f)))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .background(color),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        presets.take(5).forEach { swatch ->
                            val active = absDiff(swatch, color.copy(alpha = 1f)) < .03f
                            Box(
                                Modifier.size(28.dp)
                                    .liquidControlPress()
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(if (active) Modifier.border(2.5.dp, colors.ink, CircleShape) else Modifier)
                                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onColorChange(swatch.copy(alpha = color.alpha)) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        presets.drop(5).take(4).forEach { swatch ->
                            val active = absDiff(swatch, color.copy(alpha = 1f)) < .03f
                            Box(
                                Modifier.size(28.dp)
                                    .liquidControlPress()
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .then(if (active) Modifier.border(2.5.dp, colors.ink, CircleShape) else Modifier)
                                    .clickable(interactionSource = null, indication = null, role = Role.Button) { onColorChange(swatch.copy(alpha = color.alpha)) },
                            )
                        }
                        Box(
                            Modifier.size(28.dp)
                                .liquidControlPress()
                                .clip(CircleShape)
                                .background(if (dark) Color(0xFF767680).copy(alpha = .30f) else Color(0xFF767680).copy(alpha = .16f))
                                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                    val solid = color.copy(alpha = 1f)
                                    if (presets.none { absDiff(it, solid) < .02f } && presets.size < 9) {
                                        presets.add(solid)
                                    }
                                }
                                .semantics { contentDescription = "添加预设颜色" },
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidIcon(LiquidSymbol.Add, colors.ink, Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorGridPanel(color: Color, onColorChange: (Color) -> Unit) {
    val solid = color.copy(alpha = 1f)
    val nearest = remember(solid) {
        PaletteGridMatrix.asSequence().flatten().minByOrNull { absDiff(it, solid) }
            ?.takeIf { absDiff(it, solid) < .12f }
    }
    Column(
        Modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(12.dp)),
    ) {
        PaletteGridMatrix.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { swatch ->
                    val active = swatch == nearest
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(swatch)
                            .then(
                                if (active) Modifier.border(2.dp, Color.White).border(.75.dp, Color.Black.copy(alpha = .45f))
                                else Modifier
                            )
                            .clickable { onColorChange(swatch.copy(alpha = color.alpha)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSpectrumPanel(color: Color, dark: Boolean, onColorChange: (Color) -> Unit) {
    val latestOnColorChange by rememberUpdatedState(onColorChange)
    val currentAlpha by rememberUpdatedState(color.alpha)
    var reticle by remember(color) { mutableStateOf(colorToSpectrumPosition(color)) }

    Canvas(
        Modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun updateAt(pos: Offset) {
                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        reticle = Offset(nx, ny)
                        latestOnColorChange(spectrumPositionToColor(nx, ny, currentAlpha))
                    }
                    updateAt(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            updateAt(change.position)
                            change.consume()
                        }
                    } while (change.pressed)
                }
            },
    ) {
        drawRect(SpectrumHueBrush)
        drawRect(SpectrumWhiteBrush)
        drawRect(SpectrumBlackBrush)

        val cx = reticle.x.coerceIn(0f, 1f) * size.width
        val cy = reticle.y.coerceIn(0f, 1f) * size.height
        val radius = 12.dp.toPx()
        val luminance = .2126f * color.red + .7152f * color.green + .0722f * color.blue
        val ringColor = if (dark && luminance > .52f) Color.Black else Color.White
        drawCircle(color.copy(alpha = 1f), radius = radius - 2.dp.toPx(), center = Offset(cx, cy))
        drawCircle(ringColor, radius = radius, center = Offset(cx, cy), style = Stroke(2.8.dp.toPx()))
    }
}

@Composable
private fun ColorSlidersPanel(
    color: Color,
    tint: Color,
    dark: Boolean,
    colors: LiquidListColors,
    onColorChange: (Color) -> Unit,
) {
    val rInt = (color.red.coerceIn(0f, 1f) * 255f).roundToInt()
    val gInt = (color.green.coerceIn(0f, 1f) * 255f).roundToInt()
    val bInt = (color.blue.coerceIn(0f, 1f) * 255f).roundToInt()
    val pillFill = if (dark) Color(0xFF2C2C2E) else Color.White

    Column(Modifier.fillMaxWidth().heightIn(min = 206.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientChannelSlider(
            label = "红色",
            valueText = "$rInt",
            fraction = color.red.coerceIn(0f, 1f),
            gradientColors = listOf(color.copy(red = 0f, alpha = 1f), color.copy(red = 1f, alpha = 1f)),
            thumbColor = color.copy(alpha = 1f),
            dark = dark,
            colors = colors,
            onValueChange = { onColorChange(color.copy(red = it)) },
        )
        GradientChannelSlider(
            label = "绿色",
            valueText = "$gInt",
            fraction = color.green.coerceIn(0f, 1f),
            gradientColors = listOf(color.copy(green = 0f, alpha = 1f), color.copy(green = 1f, alpha = 1f)),
            thumbColor = color.copy(alpha = 1f),
            dark = dark,
            colors = colors,
            onValueChange = { onColorChange(color.copy(green = it)) },
        )
        GradientChannelSlider(
            label = "蓝色",
            valueText = "$bInt",
            fraction = color.blue.coerceIn(0f, 1f),
            gradientColors = listOf(color.copy(blue = 0f, alpha = 1f), color.copy(blue = 1f, alpha = 1f)),
            thumbColor = color.copy(alpha = 1f),
            dark = dark,
            colors = colors,
            onValueChange = { onColorChange(color.copy(blue = it)) },
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("sRGB Hex 颜色#", style = TextStyle(tint, 13.sp, FontWeight.Medium))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.width(76.dp).height(30.dp).clip(LiquidCapsule).background(pillFill),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(formatHexRgb(color), style = TextStyle(colors.ink, 13.sp, FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun GradientChannelSlider(
    label: String,
    valueText: String,
    fraction: Float,
    gradientColors: List<Color>,
    thumbColor: Color,
    dark: Boolean,
    colors: LiquidListColors,
    onValueChange: (Float) -> Unit,
    checkerboard: Boolean = false,
) {
    val latestOnChange by rememberUpdatedState(onValueChange)
    val pillFill = if (dark) Color(0xFF2C2C2E) else Color.White
    val checkerLight = if (dark) Color(0xFF8E8E93) else Color.White
    val checkerDark = if (dark) Color(0xFF48484A) else Color(0xFFD1D1D6)
    val channelBrush = remember(gradientColors) { Brush.horizontalGradient(gradientColors) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = TextStyle(colors.secondary, 12.sp, FontWeight.Medium))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier.weight(1f).height(28.dp).clip(LiquidCapsule)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val radiusPx = size.height / 2f
                            fun compute(posX: Float): Float {
                                val span = (size.width - 2 * radiusPx).coerceAtLeast(1f)
                                return ((posX - radiusPx) / span).coerceIn(0f, 1f)
                            }
                            latestOnChange(compute(down.position.x))
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    latestOnChange(compute(change.position.x))
                                    change.consume()
                                }
                            } while (change.pressed)
                        }
                    },
            ) {
                if (checkerboard) {
                    drawRect(checkerLight)
                    val cell = size.height / 3f
                    val cols = (size.width / cell).toInt() + 1
                    for (r in 0..2) {
                        for (c in 0 until cols) {
                            if ((r + c) % 2 == 0) {
                                drawRect(checkerDark, Offset(c * cell, r * cell), Size(cell, cell))
                            }
                        }
                    }
                }
                drawRect(channelBrush)
                val r = size.height / 2f
                val cx = r + (size.width - 2f * r).coerceAtLeast(0f) * fraction.coerceIn(0f, 1f)
                drawCircle(thumbColor.copy(alpha = 1f), radius = r - 1.dp.toPx(), center = Offset(cx, r))
                drawCircle(Color.White, radius = r - 1.5.dp.toPx(), center = Offset(cx, r), style = Stroke(3.dp.toPx()))
            }
            Box(
                Modifier.width(72.dp).height(30.dp).clip(LiquidCapsule).background(pillFill),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(valueText, style = TextStyle(colors.ink, 14.sp, FontWeight.Medium))
            }
        }
    }
}

private fun hexChannel(component: Float): String =
    (component.coerceIn(0f, 1f) * 255).roundToInt().toString(16).uppercase().padStart(2, '0')

internal fun formatHexRgb(color: Color): String =
    "${hexChannel(color.red)}${hexChannel(color.green)}${hexChannel(color.blue)}"

internal fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val rf = r.coerceIn(0f, 1f)
    val gf = g.coerceIn(0f, 1f)
    val bf = b.coerceIn(0f, 1f)
    val cMax = max(rf, max(gf, bf))
    val cMin = min(rf, min(gf, bf))
    val delta = cMax - cMin
    val h = when {
        delta < 1e-6f -> 0f
        cMax == rf -> 60f * (((gf - bf) / delta) % 6f)
        cMax == gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (cMax <= 1e-6f) 0f else delta / cMax
    return floatArrayOf(h, s, cMax)
}

internal fun hsvToColor(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val sector = hue / 60f
    val x = c * (1f - abs(sector % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when (floor(sector).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))
}

internal fun spectrumPositionToColor(nx: Float, ny: Float, alpha: Float = 1f): Color {
    val cx = nx.coerceIn(0f, 1f)
    val cy = ny.coerceIn(0f, 1f)
    val hue = cy * 360f
    return if (cx <= .5f) {
        hsvToColor(hue, (cx / .5f).coerceIn(0f, 1f), 1f, alpha)
    } else {
        hsvToColor(hue, 1f, ((1f - cx) / .5f).coerceIn(0f, 1f), alpha)
    }
}

internal fun colorToSpectrumPosition(color: Color): Offset {
    val hsv = rgbToHsv(color.red, color.green, color.blue)
    val ny = (hsv[0] / 360f).coerceIn(0f, 1f)
    val nx = (.5f * hsv[1] + .5f * (1f - hsv[2])).coerceIn(0f, 1f)
    return Offset(nx, ny)
}

private fun absDiff(a: Color, b: Color): Float =
    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

enum class LiquidDatePickerStyle { Compact, Graphical, Wheel }

@Immutable
data class LiquidCalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<LiquidCalendarDate> {
    init {
        require(month in 1..12) { "Month must be 1..12" }
        require(day in 1..daysInMonth(year, month)) { "Invalid day $day for $year-$month" }
    }

    override fun compareTo(other: LiquidCalendarDate): Int = when {
        year != other.year -> year.compareTo(other.year)
        month != other.month -> month.compareTo(other.month)
        else -> day.compareTo(other.day)
    }
}

internal fun clampCalendarDate(
    date: LiquidCalendarDate,
    minDate: LiquidCalendarDate?,
    maxDate: LiquidCalendarDate?,
): LiquidCalendarDate {
    var result = date
    if (minDate != null && result < minDate) result = minDate
    if (maxDate != null && result > maxDate) result = maxDate
    return result
}

internal fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> error("Invalid month $month")
}

/** Returns 0 (Sunday) through 6 (Saturday) for the 1st day of the given year/month. */
internal fun firstWeekdayOfMonth(year: Int, month: Int): Int {
    val m = month.coerceIn(1, 12)
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (m < 3) year - 1 else year
    return ((y + y / 4 - y / 100 + y / 400 + t[m - 1] + 1) % 7 + 7) % 7
}

internal fun calendarMonthCells(year: Int, month: Int): List<Int?> {
    val lead = firstWeekdayOfMonth(year, month)
    val total = daysInMonth(year, month)
    val cells = buildList<Int?> {
        repeat(lead) { add(null) }
        for (day in 1..total) add(day)
        while (size % 7 != 0) add(null)
    }
    return cells
}

private val WeekdayLabels = listOf("日", "一", "二", "三", "四", "五", "六")
private val CalendarMonthLabels = List(12) { "${it + 1}月" }

@Composable
internal fun LiquidCalendarGrid(
    shownYear: Int,
    shownMonth: Int,
    onMonthChange: (Int, Int) -> Unit,
    isSelected: (LiquidCalendarDate) -> Boolean,
    onSelectDate: (LiquidCalendarDate) -> Unit,
    tint: Color,
    colors: LiquidListColors,
    today: LiquidCalendarDate? = LiquidCalendarDate(2026, 11, 24),
    minDate: LiquidCalendarDate? = null,
    maxDate: LiquidCalendarDate? = null,
    tintMonthArrows: Boolean = false,
) {
    val dark = liquidGlassAppearance().dark
    val tokens = LocalLiquidGlassTokens.current
    var wheelMode by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (wheelMode) 90f else 0f, tokens.press.engage.spec, label = "Calendar wheel toggle")
    val minYear = (minDate?.year ?: 1970).coerceAtMost(shownYear)
    val maxYear = (maxDate?.year ?: 2099).coerceAtLeast(shownYear)
    val yearLabels = remember(minYear, maxYear) { (minYear..maxYear).map { "${it}年" } }
    val arrowInk = if (tintMonthArrows) tint else colors.ink
    val cells = remember(shownYear, shownMonth) { calendarMonthCells(shownYear, shownMonth) }
    val selectedInk = liquidContentColorFor(tint)
    val barFill = if (dark) Color(0xFF767680).copy(alpha = .24f) else Color(0xFF767680).copy(alpha = .12f)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.liquidControlPress()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { wheelMode = !wheelMode }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .semantics { contentDescription = "切换年月滚轮" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(
                    "${shownYear}年${shownMonth}月",
                    style = TextStyle(if (wheelMode) tint else colors.ink, 17.sp, FontWeight.SemiBold),
                )
                LiquidIcon(
                    LiquidSymbol.Forward,
                    tint,
                    Modifier.size(14.dp).graphicsLayer { rotationZ = chevronRotation },
                )
            }
            Spacer(Modifier.weight(1f))
            if (!wheelMode) {
                val canPrev = minDate == null || shownYear > minDate.year || (shownYear == minDate.year && shownMonth > minDate.month)
                val canNext = maxDate == null || shownYear < maxDate.year || (shownYear == maxDate.year && shownMonth < maxDate.month)
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .clickable(interactionSource = null, indication = null, enabled = canPrev, role = Role.Button) {
                            if (shownMonth == 1) onMonthChange(shownYear - 1, 12) else onMonthChange(shownYear, shownMonth - 1)
                        }
                        .graphicsLayer { alpha = if (canPrev) 1f else .30f }
                        .semantics { contentDescription = "上个月" },
                    contentAlignment = Alignment.Center,
                ) { LiquidIcon(LiquidSymbol.Back, arrowInk, Modifier.size(18.dp)) }
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .clickable(interactionSource = null, indication = null, enabled = canNext, role = Role.Button) {
                            if (shownMonth == 12) onMonthChange(shownYear + 1, 1) else onMonthChange(shownYear, shownMonth + 1)
                        }
                        .graphicsLayer { alpha = if (canNext) 1f else .30f }
                        .semantics { contentDescription = "下个月" },
                    contentAlignment = Alignment.Center,
                ) { LiquidIcon(LiquidSymbol.Forward, arrowInk, Modifier.size(18.dp)) }
            }
        }

        if (wheelMode) {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Spacer(Modifier.fillMaxWidth().height(32.dp).clip(LiquidCapsule).background(barFill))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidWheelPicker(
                        options = CalendarMonthLabels,
                        selected = "${shownMonth}月",
                        onSelect = { label ->
                            label.removeSuffix("月").toIntOrNull()?.let { m ->
                                onMonthChange(shownYear, m)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        showSelectionBar = false,
                    )
                    LiquidWheelPicker(
                        options = yearLabels,
                        selected = "${shownYear}年",
                        onSelect = { label ->
                            label.removeSuffix("年").toIntOrNull()?.let { y ->
                                onMonthChange(y, shownMonth)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        showSelectionBar = false,
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                WeekdayLabels.forEach { dayName ->
                    BasicText(
                        dayName,
                        Modifier.weight(1f),
                        style = TextStyle(colors.secondary, 12.sp, FontWeight.SemiBold, textAlign = TextAlign.Center),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(40.dp))
                        } else {
                            val candidate = LiquidCalendarDate(shownYear, shownMonth, day)
                            val inRange = (minDate == null || candidate >= minDate) && (maxDate == null || candidate <= maxDate)
                            val active = isSelected(candidate)
                            val isToday = today != null && candidate == today
                            Box(
                                Modifier.weight(1f).height(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (active) tint else Color.Transparent)
                                        .clickable(interactionSource = null, indication = null, enabled = inRange, role = Role.Button) { onSelectDate(candidate) }
                                        .graphicsLayer { alpha = if (inRange) 1f else .28f }
                                        .semantics { selected = active },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText(
                                        day.toString(),
                                        style = TextStyle(
                                            color = when {
                                                active -> selectedInk
                                                isToday -> tint
                                                else -> colors.ink
                                            },
                                            fontSize = 18.sp,
                                            fontWeight = if (active || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** SwiftUI `DatePicker` supporting Compact capsule popover, inline Graphical calendar, and Wheel modes. */
@Composable
fun LiquidDatePicker(
    label: String,
    date: LiquidCalendarDate,
    onDateChange: (LiquidCalendarDate) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    colors: LiquidListColors = liquidListColors(),
    style: LiquidDatePickerStyle = LiquidDatePickerStyle.Compact,
    minDate: LiquidCalendarDate? = null,
    maxDate: LiquidCalendarDate? = null,
    today: LiquidCalendarDate = LiquidCalendarDate(2026, 11, 24),
) {
    val appearance = liquidGlassAppearance()
    val dark = appearance.dark
    var open by remember { mutableStateOf(false) }
    var shownYear by remember(date.year) { mutableIntStateOf(date.year) }
    var shownMonth by remember(date.month) { mutableIntStateOf(date.month) }
    val badgeBounds = remember { mutableStateOf<Rect?>(null) }
    val pillFill = if (dark) Color(0xFF767680).copy(alpha = .24f) else Color(0xFF767680).copy(alpha = .12f)

    when (style) {
        LiquidDatePickerStyle.Graphical -> {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(label, style = TextStyle(colors.ink, 17.sp))
                LiquidCalendarGrid(
                    shownYear = shownYear,
                    shownMonth = shownMonth,
                    onMonthChange = { y, m -> shownYear = y; shownMonth = m },
                    isSelected = { it == date },
                    onSelectDate = { onDateChange(clampCalendarDate(it, minDate, maxDate)) },
                    tint = tint,
                    colors = colors,
                    today = today,
                    minDate = minDate,
                    maxDate = maxDate,
                    tintMonthArrows = false,
                )
            }
        }

        LiquidDatePickerStyle.Wheel -> {
            val minYear = (minDate?.year ?: 1970).coerceAtMost(date.year)
            val maxYear = (maxDate?.year ?: 2099).coerceAtLeast(date.year)
            val years = remember(minYear, maxYear) { (minYear..maxYear).map { "${it}年" } }
            val dayLabels = remember(date.year, date.month) { (1..daysInMonth(date.year, date.month)).map { "${it}日" } }
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(label, style = TextStyle(colors.ink, 17.sp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Spacer(Modifier.fillMaxWidth().height(32.dp).clip(LiquidCapsule).background(pillFill))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiquidWheelPicker(
                            options = years,
                            selected = "${date.year}年",
                            onSelect = { yLabel ->
                                yLabel.removeSuffix("年").toIntOrNull()?.let { y ->
                                    val d = date.day.coerceAtMost(daysInMonth(y, date.month))
                                    onDateChange(clampCalendarDate(LiquidCalendarDate(y, date.month, d), minDate, maxDate))
                                }
                            },
                            modifier = Modifier.weight(1.1f),
                            colors = colors,
                            showSelectionBar = false,
                        )
                        LiquidWheelPicker(
                            options = CalendarMonthLabels,
                            selected = "${date.month}月",
                            onSelect = { mLabel ->
                                mLabel.removeSuffix("月").toIntOrNull()?.let { m ->
                                    val d = date.day.coerceAtMost(daysInMonth(date.year, m))
                                    onDateChange(clampCalendarDate(LiquidCalendarDate(date.year, m, d), minDate, maxDate))
                                }
                            },
                            modifier = Modifier.weight(0.9f),
                            colors = colors,
                            showSelectionBar = false,
                        )
                        LiquidWheelPicker(
                            options = dayLabels,
                            selected = "${date.day}日",
                            onSelect = { dLabel ->
                                dLabel.removeSuffix("日").toIntOrNull()?.let { d ->
                                    val clampedDay = d.coerceAtMost(daysInMonth(date.year, date.month))
                                    onDateChange(clampCalendarDate(LiquidCalendarDate(date.year, date.month, clampedDay), minDate, maxDate))
                                }
                            },
                            modifier = Modifier.weight(0.9f),
                            colors = colors,
                            showSelectionBar = false,
                        )
                    }
                }
            }
        }

        LiquidDatePickerStyle.Compact -> {
            Row(
                modifier.fillMaxWidth().heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(label, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
                Box(
                    Modifier.liquidPopoverAnchor(badgeBounds)
                        .liquidControlPress()
                        .clip(LiquidCapsule)
                        .background(pillFill)
                        .clickable(interactionSource = null, indication = null, role = Role.Button) {
                            shownYear = date.year
                            shownMonth = date.month
                            open = !open
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        "${date.year}年${date.month}月${date.day}日",
                        style = TextStyle(if (open) tint else colors.ink, 16.sp, FontWeight.Normal),
                    )
                }
            }

            if (open) {
                LiquidPopover(
                    onDismissRequest = { open = false },
                    anchorBoundsInRoot = badgeBounds.value,
                    backdrop = backdrop,
                    colors = colors,
                    maxWidth = 336.dp,
                    blurRadius = maxOf(appearance.menuBlurRadius, 22.dp),
                ) {
                    LiquidCalendarGrid(
                        shownYear = shownYear,
                        shownMonth = shownMonth,
                        onMonthChange = { y, m -> shownYear = y; shownMonth = m },
                        isSelected = { it == date },
                        onSelectDate = { chosen ->
                            onDateChange(clampCalendarDate(chosen, minDate, maxDate))
                            open = false
                        },
                        tint = tint,
                        colors = colors,
                        today = today,
                        minDate = minDate,
                        maxDate = maxDate,
                        tintMonthArrows = false,
                    )
                }
            }
        }
    }
}

/** Multi-date calendar grid matching SwiftUI `MultiDatePicker`. */
@Composable
fun LiquidMultiDatePicker(
    dates: Set<LiquidCalendarDate>,
    onDatesChange: (Set<LiquidCalendarDate>) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    initialYear: Int = 2026,
    initialMonth: Int = 11,
    colors: LiquidListColors = liquidListColors(),
    minDate: LiquidCalendarDate? = null,
    maxDate: LiquidCalendarDate? = null,
    today: LiquidCalendarDate? = LiquidCalendarDate(2026, 11, 24),
) {
    var shownYear by remember { mutableIntStateOf(initialYear) }
    var shownMonth by remember { mutableIntStateOf(initialMonth.coerceIn(1, 12)) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiquidCalendarGrid(
            shownYear = shownYear,
            shownMonth = shownMonth,
            onMonthChange = { y, m -> shownYear = y; shownMonth = m },
            isSelected = { it in dates },
            onSelectDate = { chosen ->
                val target = clampCalendarDate(chosen, minDate, maxDate)
                onDatesChange(if (target in dates) dates - target else dates + target)
            },
            tint = tint,
            colors = colors,
            today = today,
            minDate = minDate,
            maxDate = maxDate,
            tintMonthArrows = true,
        )
        BasicText(
            if (dates.isEmpty()) "点击日期可多选" else "已选 ${dates.size} 个日期",
            style = TextStyle(colors.secondary, 13.sp),
        )
    }
}
