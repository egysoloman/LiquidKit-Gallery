package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ──────────────────────────────────────────────────────────────────────────────
// 1. Badge & Redacted Placeholder Skeleton (.badge / .redacted(reason: .placeholder))
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun LiquidBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xffff3b30),
    contentColor: Color = Color.White,
) {
    if (text.isEmpty()) return
    Box(
        modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 20.dp)
            .clip(LiquidCapsule)
            .background(tint)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(contentColor, 12.sp, FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

/** SwiftUI `.redacted(reason: .placeholder)` animated skeleton shimmer overlay. */
fun Modifier.liquidRedacted(active: Boolean, cornerRadius: Dp = 8.dp): Modifier = composed {
    val overlayAlpha by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "LiquidRedacted alpha")
    if (!active && overlayAlpha <= 0.003f) return@composed this
    val dark = liquidGlassAppearance().dark
    val transition = rememberInfiniteTransition(label = "LiquidRedacted")
    val shimmerState = transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1250, easing = LinearEasing), RepeatMode.Restart),
        label = "Redacted shimmer",
    )
    val base = if (dark) Color(0xff2c2c2e) else Color(0xffe5e5ea)
    val highlight = if (dark) Color(0xff3e3e42) else Color(0xfff4f4f8)
    val shimmerColors = remember(base, highlight) { listOf(base, highlight, base) }
    this.drawWithContent {
        drawContent()
        if (overlayAlpha > 0.003f) {
            val shimmer = shimmerState.value
            val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            val brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(size.width * (shimmer - 0.35f), 0f),
                end = Offset(size.width * (shimmer + 0.35f), size.height),
            )
            drawRoundRect(brush = brush, cornerRadius = radius, alpha = overlayAlpha)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 2. iOS 27 Reorderable Grid (.reorderContainer + .reorderable)
// ──────────────────────────────────────────────────────────────────────────────

/** Pure swap/move helper shared by `LiquidReorderableGrid` and desktop unit tests. */
fun <T> moveListItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) return items
    val copy = items.toMutableList()
    val moved = copy.removeAt(fromIndex)
    copy.add(toIndex, moved)
    return copy
}

/**
 * iOS 27 `.reorderContainer` + `.reorderable()` tile grid supporting long-press drag-to-reorder
 * with lifted scale/shadow feedback and live grid-slot swapping.
 */
@Composable
fun <T> LiquidReorderableGrid(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    tileHeight: Dp = 74.dp,
    spacing: Dp = 10.dp,
    tileContent: @Composable BoxScope.(T, Boolean) -> Unit,
) {
    val cols = columns.coerceAtLeast(1)
    val rows = (items.size + cols - 1) / cols
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val currentItems by rememberUpdatedState(items)
    val commitReorder by rememberUpdatedState(onReorder)
    val haptics = rememberLiquidHaptics()
    val settleOffsetSpec = remember { GlassSpring.snappy(0.28f).offsetSpec }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) { spacing.toPx() }
        val tileHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { tileHeight.toPx() }
        val tileWidthDp = ((maxWidth - spacing * (cols - 1)) / cols).coerceAtLeast(48.dp)
        val tileWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { tileWidthDp.toPx() }
        val totalHeight = if (rows == 0) 0.dp else tileHeight * rows + spacing * (rows - 1)

        Box(Modifier.fillMaxWidth().height(totalHeight)) {
            items.forEachIndexed { index, item ->
                val itemKey = key(item)
                val isDragging = draggingKey == itemKey
                val col = index % cols
                val row = index / cols
                val baseX = col * (tileWidthPx + spacingPx)
                val baseY = row * (tileHeightPx + spacingPx)

                key(itemKey) {
                    val scaleState = animateFloatAsState(if (isDragging) 1.07f else 1f, GlassSpring.snappy(0.24f).spec, label = "Tile scale")
                    val posAnim = remember { Animatable(Offset(baseX, baseY), Offset.VectorConverter) }
                    LaunchedEffect(baseX, baseY, isDragging) {
                        val target = Offset(baseX, baseY)
                        if (isDragging) {
                            posAnim.snapTo(target + dragOffset)
                        } else if ((posAnim.targetValue - target).getDistance() > 0.5f || posAnim.isRunning.not()) {
                            posAnim.animateTo(target, settleOffsetSpec)
                        }
                    }
                    Box(
                        Modifier
                            .zIndex(if (isDragging) 10f else 0f)
                            .offset {
                                if (isDragging) {
                                    IntOffset((baseX + dragOffset.x).roundToInt(), (baseY + dragOffset.y).roundToInt())
                                } else {
                                    val pos = posAnim.value
                                    IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                }
                            }
                            .size(tileWidthDp, tileHeight)
                            .graphicsLayer {
                                val s = scaleState.value
                                scaleX = s
                                scaleY = s
                                shadowElevation = ((s - 1f) / 0.07f).coerceIn(0f, 1f) * 16f
                            }
                            .pointerInput(itemKey, cols, tileWidthPx, tileHeightPx, spacingPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val startIdx = currentItems.indexOfFirst { key(it) == itemKey }.coerceAtLeast(0)
                                        val startBase = Offset(
                                            (startIdx % cols) * (tileWidthPx + spacingPx),
                                            (startIdx / cols) * (tileHeightPx + spacingPx),
                                        )
                                        draggingKey = itemKey
                                        dragOffset = Offset.Zero
                                        scope.launch { posAnim.snapTo(startBase) }
                                        haptics(LiquidHaptic.LongPressLift)
                                    },
                                    onDragEnd = {
                                        val releasePos = posAnim.value
                                        draggingKey = null
                                        dragOffset = Offset.Zero
                                        val curList = currentItems
                                        val finalIdx = curList.indexOfFirst { key(it) == itemKey }.coerceAtLeast(0)
                                        val endTarget = Offset(
                                            (finalIdx % cols) * (tileWidthPx + spacingPx),
                                            (finalIdx / cols) * (tileHeightPx + spacingPx),
                                        )
                                        scope.launch {
                                            posAnim.snapTo(releasePos)
                                            posAnim.animateTo(endTarget, settleOffsetSpec)
                                        }
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = Offset.Zero
                                        val curList = currentItems
                                        val finalIdx = curList.indexOfFirst { key(it) == itemKey }.coerceAtLeast(0)
                                        val endTarget = Offset(
                                            (finalIdx % cols) * (tileWidthPx + spacingPx),
                                            (finalIdx / cols) * (tileHeightPx + spacingPx),
                                        )
                                        scope.launch { posAnim.animateTo(endTarget, settleOffsetSpec) }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val list = currentItems
                                        val fromIdx = list.indexOfFirst { key(it) == itemKey }
                                        if (fromIdx >= 0) {
                                            val curCol = fromIdx % cols
                                            val curRow = fromIdx / cols
                                            val curBase = Offset(curCol * (tileWidthPx + spacingPx), curRow * (tileHeightPx + spacingPx))
                                            val centerX = curBase.x + tileWidthPx * 0.5f + dragOffset.x
                                            val centerY = curBase.y + tileHeightPx * 0.5f + dragOffset.y
                                            val targetCol = (centerX / (tileWidthPx + spacingPx)).toInt().coerceIn(0, cols - 1)
                                            val targetRow = (centerY / (tileHeightPx + spacingPx)).toInt().coerceIn(0, max(0, (list.size - 1) / cols))
                                            val targetIdx = (targetRow * cols + targetCol).coerceIn(0, list.lastIndex)
                                            if (targetIdx != fromIdx) {
                                                val deltaCol = (targetIdx % cols) - curCol
                                                val deltaRow = (targetIdx / cols) - curRow
                                                dragOffset -= Offset(
                                                    deltaCol * (tileWidthPx + spacingPx),
                                                    deltaRow * (tileHeightPx + spacingPx),
                                                )
                                                val newBase = Offset(
                                                    (targetIdx % cols) * (tileWidthPx + spacingPx),
                                                    (targetIdx / cols) * (tileHeightPx + spacingPx),
                                                )
                                                scope.launch { posAnim.snapTo(newBase + dragOffset) }
                                                haptics(LiquidHaptic.SelectionTick)
                                                commitReorder(moveListItem(list, fromIdx, targetIdx))
                                            } else {
                                                scope.launch { posAnim.snapTo(curBase + dragOffset) }
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        tileContent(item, isDragging)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 3. Adaptive Inspector (.inspector(isPresented:)) & TabView Bottom Accessory
// ──────────────────────────────────────────────────────────────────────────────

/**
 * SwiftUI `.inspector(isPresented:)`: renders a trailing side column on wide screens (`>= 600.dp`)
 * and automatically adapts to a frosted `LiquidSheet` on compact phones.
 */
@Composable
fun LiquidInspector(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    colors: LiquidListColors = liquidListColors(),
    inspectorWidth: Dp = 300.dp,
    inspectorContent: @Composable ColumnScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val mainBody = remember(content) { movableContentOf { Box(Modifier.fillMaxSize(), content = content) } }
    val inspectorBody = remember(inspectorContent) { movableContentOf<ColumnScope> { scope -> scope.inspectorContent() } }
    LiquidPredictiveBackHandler(enabled = isPresented) { events ->
        try {
            events.collect {}
            onDismiss()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { mainBody() }
                AnimatedVisibility(
                    visible = isPresented,
                    enter = slideInHorizontally(GlassSpring.snappy(0.30f).specWithThreshold(IntOffset(1, 1))) { it } +
                        expandHorizontally(expandFrom = Alignment.End) + fadeIn(tween(180)),
                    exit = slideOutHorizontally(GlassSpring.snappy(0.28f).specWithThreshold(IntOffset(1, 1))) { it } +
                        shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(tween(150)),
                ) {
                    Row(Modifier.width(inspectorWidth).fillMaxHeight()) {
                        Spacer(Modifier.width(.6.dp).fillMaxHeight().background(colors.separator))
                        Column(
                            Modifier.fillMaxSize().background(colors.surface)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                                    ),
                                )
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                BasicText(title, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold))
                                LiquidActionButton("完成", onDismiss, Color(0xff007aff), style = LiquidActionStyle.Borderless, controlSize = LiquidControlSize.Small)
                            }
                            inspectorBody(this)
                        }
                    }
                }
            }
        } else {
            mainBody()
            if (isPresented) {
                LiquidSheet(
                    onDismissRequest = onDismiss,
                    backdrop = backdrop,
                    title = title,
                    detents = listOf(LiquidSheetDetent.Medium, LiquidSheetDetent.Large),
                    colors = colors,
                    content = { inspectorBody(this) },
                )
            }
        }
    }
}

/** iOS 26/27 `.tabViewBottomAccessory` floating refractive capsule bar seated above a tab bar. */
@Composable
fun LiquidTabViewBottomAccessory(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidGlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = LiquidCapsule,
        variant = LiquidGlassVariant.Regular,
        interactive = onClick != null,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 4. MeshGradient, FlowLayout, OutlineGroup, Sortable Table, TipCard
// ──────────────────────────────────────────────────────────────────────────────

internal const val MeshGradient3x3Agsl = """
uniform float2 resolution;
uniform float4 c0, c1, c2;
uniform float4 c3, c4, c5;
uniform float4 c6, c7, c8;

float smooth01(float t) {
    float x = clamp(t, 0.0, 1.0);
    return x * x * (3.0 - 2.0 * x);
}

half4 sampleRow(float4 a, float4 b, float4 c, float u) {
    if (u < 0.5) {
        return half4(mix(a, b, smooth01(u * 2.0)));
    } else {
        return half4(mix(b, c, smooth01((u - 0.5) * 2.0)));
    }
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(resolution, float2(1.0));
    half4 r0 = sampleRow(c0, c1, c2, uv.x);
    half4 r1 = sampleRow(c3, c4, c5, uv.x);
    half4 r2 = sampleRow(c6, c7, c8, uv.x);
    half4 res = (uv.y < 0.5)
        ? mix(r0, r1, half(smooth01(uv.y * 2.0)))
        : mix(r1, r2, half(smooth01((uv.y - 0.5) * 2.0)));
    return half4(res.rgb * res.a, res.a);
}
"""

/**
 * Smooth GPU-accelerated `MeshGradient(width:height:colors:)` matching SwiftUI `MeshGradient`.
 * Uses an AGSL `RuntimeShader` on API 33+ for pixel-perfect continuous interpolation in 1 draw call,
 * and falls back to dense bilinear interpolation on older APIs / Desktop.
 */
@Composable
fun LiquidMeshGradient(
    width: Int,
    height: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    subdivisions: Int = 36,
) {
    val cols = width.coerceAtLeast(2)
    val rows = height.coerceAtLeast(2)
    if (colors.size < cols * rows) return

    val supportsAgsl = remember { isRuntimeShaderSupported() }
    val shader = remember(supportsAgsl) {
        if (supportsAgsl) SharedRenderEffectShaderCache.obtainRuntimeShader("MeshGradient3x3", MeshGradient3x3Agsl) else null
    }

    // Map any 2x2 or 3x3 grid cleanly into the 3x3 AGSL uniforms so both 2x2 and 3x3 run on GPU.
    val grid3x3 = remember(cols, rows, colors) {
        if ((cols == 2 || cols == 3) && (rows == 2 || rows == 3)) {
            fun sampleAt(u: Float, v: Float): Color {
                val gx = u * (cols - 1)
                val gy = v * (rows - 1)
                val c0 = gx.toInt().coerceIn(0, cols - 2)
                val r0 = gy.toInt().coerceIn(0, rows - 2)
                val tx = (gx - c0).coerceIn(0f, 1f)
                val ty = (gy - r0).coerceIn(0f, 1f)
                val top = lerp(colors[r0 * cols + c0], colors[r0 * cols + c0 + 1], tx)
                val bot = lerp(colors[(r0 + 1) * cols + c0], colors[(r0 + 1) * cols + c0 + 1], tx)
                return lerp(top, bot, ty)
            }
            val coords = listOf(0f, 0.5f, 1f)
            coords.flatMap { v -> coords.map { u -> sampleAt(u, v) } }
        } else {
            null
        }
    }

    Canvas(modifier) {
        if (shader != null && grid3x3 != null) {
            shader.setFloatUniform("resolution", size.width, size.height)
            for (i in 0 until 9) {
                val c = grid3x3[i]
                shader.setFloatUniform("c$i", c.red, c.green, c.blue, c.alpha)
            }
            drawRect(brush = ShaderBrush(shader.asComposeShader()))
            return@Canvas
        }
        val stepsX = subdivisions.coerceAtLeast(16)
        val stepsY = subdivisions.coerceAtLeast(16)
        val cellW = size.width / stepsX
        val cellH = size.height / stepsY
        for (sy in 0 until stepsY) {
            val v = (sy + 0.5f) / stepsY
            val gy = v * (rows - 1)
            val r0 = gy.toInt().coerceIn(0, rows - 2)
            val r1 = r0 + 1
            val ty = (gy - r0).coerceIn(0f, 1f)
            val smoothTy = ty * ty * (3f - 2f * ty)
            for (sx in 0 until stepsX) {
                val u = (sx + 0.5f) / stepsX
                val gx = u * (cols - 1)
                val c0 = gx.toInt().coerceIn(0, cols - 2)
                val c1 = c0 + 1
                val tx = (gx - c0).coerceIn(0f, 1f)
                val smoothTx = tx * tx * (3f - 2f * tx)

                val top = lerp(colors[r0 * cols + c0], colors[r0 * cols + c1], smoothTx)
                val bot = lerp(colors[r1 * cols + c0], colors[r1 * cols + c1], smoothTx)
                val c = lerp(top, bot, smoothTy)
                drawRect(
                    color = c,
                    topLeft = Offset(sx * cellW - 0.5f, sy * cellH - 0.5f),
                    size = Size(cellW + 1.5f, cellH + 1.5f),
                )
            }
        }
    }
}

/** Custom multi-line wrapping `Layout` mirroring SwiftUI's `FlowLayout` protocol demo. */
@Composable
fun LiquidFlowLayout(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        val positions = ArrayList<IntOffset>(placeables.size)
        var x = 0
        var y = 0
        var rowHeight = 0
        var maxLineW = 0

        for (p in placeables) {
            if (x > 0 && x + p.width > maxW) {
                x = 0
                y += rowHeight + vGap
                rowHeight = 0
            }
            positions.add(IntOffset(x, y))
            x += p.width + hGap
            rowHeight = max(rowHeight, p.height)
            maxLineW = max(maxLineW, x - hGap)
        }
        val totalW = if (constraints.hasBoundedWidth) max(constraints.minWidth, min(maxW, maxLineW)) else max(constraints.minWidth, maxLineW)
        val totalH = max(constraints.minHeight, y + rowHeight)
        layout(totalW, totalH) {
            placeables.forEachIndexed { i, p -> p.placeRelative(positions[i]) }
        }
    }
}

@Immutable
data class LiquidOutlineNode<T>(
    val id: String,
    val title: String,
    val symbol: LiquidSymbol? = null,
    val payload: T? = null,
    val children: List<LiquidOutlineNode<T>> = emptyList(),
)

/** Recursive collapsible hierarchy mirroring SwiftUI `OutlineGroup`. */
@Composable
fun <T> LiquidOutlineGroup(
    nodes: List<LiquidOutlineNode<T>>,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
    onSelectLeaf: ((LiquidOutlineNode<T>) -> Unit)? = null,
) {
    val expandedIds = remember { mutableStateMapOf<String, Boolean>() }
    Column(modifier.fillMaxWidth()) {
        nodes.forEach { node ->
            OutlineBranch(node, depth = 0, expandedIds = expandedIds, tint = tint, colors = colors, onSelectLeaf = onSelectLeaf)
        }
    }
}

@Composable
private fun <T> OutlineBranch(
    node: LiquidOutlineNode<T>,
    depth: Int,
    expandedIds: MutableMap<String, Boolean>,
    tint: Color,
    colors: LiquidListColors,
    onSelectLeaf: ((LiquidOutlineNode<T>) -> Unit)?,
) {
    val hasChildren = node.children.isNotEmpty()
    val expanded = expandedIds[node.id] ?: (depth == 0)
    val chevronRot by animateFloatAsState(if (expanded) 90f else 0f, tween(180), label = "Outline chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                if (hasChildren) expandedIds[node.id] = !expanded else onSelectLeaf?.invoke(node)
            }
            .padding(start = (depth * 18).dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasChildren) {
            LiquidIcon(LiquidSymbol.Forward, colors.secondary, Modifier.size(12.dp).rotate(chevronRot))
        } else {
            Spacer(Modifier.size(12.dp))
        }
        if (node.symbol != null) {
            LiquidIcon(node.symbol, tint, Modifier.size(18.dp))
        }
        BasicText(
            node.title,
            Modifier.weight(1f),
            style = TextStyle(colors.ink, 15.sp, if (hasChildren) FontWeight.Medium else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (hasChildren) {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                node.children.forEach { child ->
                    OutlineBranch(child, depth + 1, expandedIds, tint, colors, onSelectLeaf)
                }
            }
        }
    }
}

@Immutable
data class LiquidTableColumn<T>(
    val id: String,
    val title: String,
    val weight: Float = 1f,
    val comparator: Comparator<T>? = null,
    val valueText: (T) -> String,
)

/** Sortable multi-column data table mirroring SwiftUI `Table`. */
@Composable
fun <T> LiquidTable(
    rows: List<T>,
    columns: List<LiquidTableColumn<T>>,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
) {
    if (columns.isEmpty()) return
    var sortColId by remember { mutableStateOf(columns.firstOrNull { it.comparator != null }?.id) }
    var ascending by remember { mutableStateOf(true) }
    val sorted = remember(rows, columns, sortColId, ascending) {
        val comp = columns.firstOrNull { it.id == sortColId }?.comparator
        if (comp == null) rows else rows.sortedWith(if (ascending) comp else comp.reversed())
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(.6.dp, colors.separator.copy(alpha = .6f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(colors.secondary.copy(alpha = .08f)).padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { col ->
                val active = col.id == sortColId && col.comparator != null
                Row(
                    Modifier
                        .weight(col.weight)
                        .clickable(enabled = col.comparator != null, interactionSource = null, indication = null) {
                            if (sortColId == col.id) ascending = !ascending
                            else {
                                sortColId = col.id
                                ascending = true
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        col.title + if (active) (if (ascending) " ↑" else " ↓") else "",
                        style = TextStyle(if (active) tint else colors.secondary, 13.sp, FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        sorted.forEachIndexed { idx, row ->
            if (idx > 0) Spacer(Modifier.fillMaxWidth().height(.55.dp).background(colors.separator.copy(alpha = .5f)))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEach { col ->
                    BasicText(
                        col.valueText(row),
                        Modifier.weight(col.weight),
                        style = TextStyle(colors.ink, 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Inline onboarding tip card mirroring Apple `TipKit` `TipView`. */
@Composable
fun LiquidTipCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    symbol: LiquidSymbol = LiquidSymbol.Sparkles,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = if (liquidGlassAppearance().dark) .18f else .10f))
            .border(.75.dp, tint.copy(alpha = .28f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(tint.copy(alpha = .18f)),
            contentAlignment = Alignment.Center,
        ) {
            LiquidIcon(symbol, tint, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title, style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold))
            BasicText(message, style = TextStyle(colors.secondary, 13.sp, lineHeight = 18.sp))
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                LiquidActionButton(actionLabel, onAction, tint, style = LiquidActionStyle.Borderless, controlSize = LiquidControlSize.Small)
            }
        }
        if (onDismiss != null) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                LiquidIcon(LiquidSymbol.Close, colors.secondary, Modifier.size(12.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// 5. Swift Charts Equivalents (Bar / Line+Area+Rule / Pie-Donut / Math Plot)
// ──────────────────────────────────────────────────────────────────────────────

@Immutable
data class LiquidChartSeriesPoint(
    val label: String,
    val series: String,
    val value: Float,
    val color: Color,
)

@Composable
fun LiquidBarChart(
    points: List<LiquidChartSeriesPoint>,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    colors: LiquidListColors = liquidListColors(),
) {
    val categories = remember(points) { points.map { it.label }.distinct() }
    val seriesList = remember(points) { points.map { it.series }.distinct() }
    val maxVal = remember(points) { (points.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f) }
    if (categories.isEmpty()) return

    val animatedFracs = points.map { p ->
        key(p.label, p.series) {
            animateFloatAsState(
                targetValue = (p.value / maxVal).coerceIn(0f, 1f),
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "Bar frac",
            )
        }
    }
    fun animFrac(label: String, series: String): Float {
        val idx = points.indexOfFirst { it.label == label && it.series == series }
        return if (idx >= 0) animatedFracs[idx].value else 0f
    }

    AnimatedContent(targetState = horizontal, label = "Bar orientation", modifier = modifier.fillMaxWidth()) { isHorizontal ->
        if (!isHorizontal) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                    val gridColor = colors.separator.copy(alpha = .45f)
                    for (i in 0..4) {
                        val y = size.height * i / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    val groupW = size.width / categories.size
                    val barGap = 4.dp.toPx()
                    val innerPad = groupW * 0.16f
                    val availW = (groupW - innerPad * 2f).coerceAtLeast(8f)
                    val barW = ((availW - barGap * (seriesList.size - 1)) / seriesList.size.coerceAtLeast(1)).coerceAtLeast(4f)
                    val cr = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    categories.forEachIndexed { cIdx, cat ->
                        seriesList.forEachIndexed { sIdx, ser ->
                            val p = points.firstOrNull { it.label == cat && it.series == ser } ?: return@forEachIndexed
                            val h = animFrac(cat, ser) * (size.height - 6.dp.toPx())
                            val x = cIdx * groupW + innerPad + sIdx * (barW + barGap)
                            drawRoundRect(
                                color = p.color,
                                topLeft = Offset(x, size.height - h),
                                size = Size(barW, h),
                                cornerRadius = cr,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    categories.forEach { cat ->
                        BasicText(cat, style = TextStyle(colors.secondary, 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.height(190.dp),
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.End,
                ) {
                    categories.forEach { cat ->
                        BasicText(cat, style = TextStyle(colors.secondary, 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Canvas(Modifier.weight(1f).height(190.dp)) {
                    val gridColor = colors.separator.copy(alpha = .45f)
                    for (i in 0..4) {
                        val x = size.width * i / 4f
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                    val groupH = size.height / categories.size
                    val barGap = 3.dp.toPx()
                    val innerPad = groupH * 0.14f
                    val availH = (groupH - innerPad * 2f).coerceAtLeast(8f)
                    val barH = ((availH - barGap * (seriesList.size - 1)) / seriesList.size.coerceAtLeast(1)).coerceAtLeast(4f)
                    val cr = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    categories.forEachIndexed { cIdx, cat ->
                        seriesList.forEachIndexed { sIdx, ser ->
                            val p = points.firstOrNull { it.label == cat && it.series == ser } ?: return@forEachIndexed
                            val w = animFrac(cat, ser) * (size.width - 6.dp.toPx())
                            val y = cIdx * groupH + innerPad + sIdx * (barH + barGap)
                            drawRoundRect(
                                color = p.color,
                                topLeft = Offset(0f, y),
                                size = Size(w, barH),
                                cornerRadius = cr,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidLineAreaChart(
    labels: List<String>,
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xff00c7be),
    ruleValue: Float? = null,
    ruleColor: Color = Color(0xffff9500),
    selectedIndex: Int? = null,
    onSelectIndex: ((Int?) -> Unit)? = null,
    colors: LiquidListColors = liquidListColors(),
    chartHeight: Dp = 180.dp,
    showPoints: Boolean = true,
    showGrid: Boolean = true,
    labelColor: Color = colors.secondary,
    yDomain: ClosedFloatingPointRange<Float>? = null,
) {
    if (values.size < 2) return
    val minVal = remember(values, yDomain) { yDomain?.start ?: 0f }
    val maxVal = remember(values, ruleValue, yDomain) {
        yDomain?.endInclusive ?: (max(values.maxOrNull() ?: 1f, ruleValue ?: 0f).coerceAtLeast(1f) * 1.12f)
    }
    val spanVal = (maxVal - minVal).coerceAtLeast(1e-4f)
    val linePath = remember { Path() }
    val areaPath = remember { Path() }
    val areaBrush = remember(lineColor) {
        Brush.verticalGradient(listOf(lineColor.copy(alpha = .35f), Color.Transparent))
    }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val scrubFrac by animateFloatAsState(
        targetValue = if (selectedIndex != null && selectedIndex in values.indices) selectedIndex.toFloat() / (values.size - 1) else -1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "Line scrubber",
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(values.size, onSelectIndex) {
                    if (onSelectIndex == null) return@pointerInput
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val idx = (frac * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
                        onSelectIndex(if (currentSelected == idx) null else idx)
                    }
                }
                .pointerInput(values.size, onSelectIndex) {
                    if (onSelectIndex == null) return@pointerInput
                    detectHorizontalDragGestures { change, _ ->
                        val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onSelectIndex((frac * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex))
                    }
                },
        ) {
            if (showGrid) {
                val gridColor = colors.separator.copy(alpha = .4f)
                for (i in 0..4) {
                    val y = size.height * i / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
            val padX = 10.dp.toPx()
            val spanX = (size.width - padX * 2f).coerceAtLeast(1f)
            val pts = values.mapIndexed { i, v ->
                val x = padX + spanX * i / (values.size - 1)
                val y = size.height * (1f - ((v - minVal) / spanVal).coerceIn(0f, 1f))
                Offset(x, y)
            }
            linePath.reset()
            linePath.moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.lastIndex) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                linePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
            areaPath.reset()
            areaPath.addPath(linePath)
            areaPath.lineTo(pts.last().x, size.height)
            areaPath.lineTo(pts.first().x, size.height)
            areaPath.close()
            drawPath(areaPath, brush = areaBrush)
            drawPath(linePath, color = lineColor, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
            if (showPoints) {
                pts.forEachIndexed { idx, pt ->
                    val chosen = idx == selectedIndex
                    drawCircle(if (chosen) ruleColor else lineColor, radius = if (chosen) 5.5.dp.toPx() else 3.5.dp.toPx(), center = pt)
                }
            }
            if (ruleValue != null) {
                val ry = size.height * (1f - ((ruleValue - minVal) / spanVal).coerceIn(0f, 1f))
                drawLine(
                    color = ruleColor,
                    start = Offset(0f, ry),
                    end = Offset(size.width, ry),
                    strokeWidth = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                )
            }
            if (selectedIndex != null && selectedIndex in pts.indices) {
                val sx = padX + spanX * scrubFrac.coerceIn(0f, 1f)
                drawLine(colors.secondary.copy(alpha = .55f), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { lbl -> BasicText(lbl, style = TextStyle(labelColor, 11.sp)) }
        }
    }
}

@Immutable
data class LiquidPieSlice(val label: String, val value: Float, val color: Color)

@Composable
fun LiquidPieDonutChart(
    slices: List<LiquidPieSlice>,
    modifier: Modifier = Modifier,
    innerRadiusRatio: Float = 0.58f,
    angularInsetDegrees: Float = 2f,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1e-4f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    val ratioAnim by animateFloatAsState(innerRadiusRatio.coerceIn(0f, 0.82f), tween(260, easing = FastOutSlowInEasing), label = "Pie ratio")
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outerR = min(size.width, size.height) * 0.46f
            val ratio = ratioAnim
            val sweepFactor = progress.value.coerceIn(0.01f, 1f)
            var startDeg = -90f
            for (slice in slices) {
                val sweep = (slice.value / total) * 360f * sweepFactor
                val gap = min(angularInsetDegrees, sweep * 0.4f)
                if (ratio <= 0.02f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startDeg + gap * 0.5f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = true,
                        topLeft = Offset(center.x - outerR, center.y - outerR),
                        size = Size(outerR * 2f, outerR * 2f),
                    )
                } else {
                    val strokeW = outerR * (1f - ratio)
                    val midR = outerR - strokeW * 0.5f
                    drawArc(
                        color = slice.color,
                        startAngle = startDeg + gap * 0.5f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(center.x - midR, center.y - midR),
                        size = Size(midR * 2f, midR * 2f),
                        style = Stroke(width = strokeW, cap = StrokeCap.Butt),
                    )
                }
                startDeg += sweep
            }
        }
        AnimatedVisibility(
            visible = innerRadiusRatio >= 0.35f,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150)),
        ) {
            Box(contentAlignment = Alignment.Center, content = centerContent)
        }
    }
}

@Composable
fun LiquidMathPlotChart(
    modifier: Modifier = Modifier,
    frequency: Float = 1f,
    colors: LiquidListColors = liquidListColors(),
) {
    val sinPath = remember { Path() }
    val cosPath = remember { Path() }
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val midY = size.height * 0.5f
        val amp = size.height * 0.38f
        drawLine(colors.separator.copy(alpha = .5f), Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)
        val steps = 96
        sinPath.reset()
        cosPath.reset()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = t * size.width
            val rad = t * 2f * PI.toFloat() * frequency
            val sy = midY - sin(rad) * amp
            val cy = midY - cos(rad) * amp
            if (i == 0) {
                sinPath.moveTo(x, sy)
                cosPath.moveTo(x, cy)
            } else {
                sinPath.lineTo(x, sy)
                cosPath.lineTo(x, cy)
            }
        }
        val stroke = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round)
        drawPath(sinPath, Color(0xff007aff), style = stroke)
        drawPath(cosPath, Color(0xffff9500), style = stroke)
    }
}
