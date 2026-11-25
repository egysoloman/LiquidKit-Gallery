package com.kyant.backdrop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp as lerpRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp as lerpFloat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** Only the held row is recorded. Its display list can rise outside a clipped lazy-list item. */
internal class ContextPreview(val owner: LiquidMenuState, val layer: GraphicsLayer) {
    var source by mutableStateOf<MenuTrigger?>(null)
        private set

    fun capture(trigger: MenuTrigger, scope: ContentDrawScope) {
        if (source === trigger) return
        with(scope) { layer.record { scope.drawContent() } }
        source = trigger
    }

    fun release(trigger: MenuTrigger) {
        if (source === trigger) source = null
    }
}

internal val LocalContextPreview = compositionLocalOf<ContextPreview?> { null }

fun Modifier.liquidListContextMenu(state: LiquidMenuState, enabled: Boolean = true, prepare: () -> Unit): Modifier =
    if (enabled) liquidMenuTrigger(state, LiquidMenuActivation.ListContext, beforeOpen = prepare) else this

/** Retains the tile's own rounded outline as it lifts out of its parent. */
fun Modifier.liquidListContextMenu(
    state: LiquidMenuState, sourceShape: RoundedCornerShape, enabled: Boolean = true, prepare: () -> Unit,
): Modifier = if (enabled) registerMenuTrigger(state, LiquidMenuActivation.ListContext, prepare, {}, {},
    borderless = false, previewShape = sourceShape) else this

/** The preview retains the actual row's artwork, indentation, typography and interaction target. */
@Composable
fun LiquidListContextMenuHost(
    state: LiquidMenuState, backdrop: Backdrop, items: List<LiquidMenuItem>, modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(), content: @Composable BoxScope.() -> Unit,
) = ContextMenuHost(state, backdrop, items, modifier, colors, null, content)

/** A supplied preview can replace the row content after activation. */
@Composable
fun LiquidListContextMenuHost(
    state: LiquidMenuState, backdrop: Backdrop, items: List<LiquidMenuItem>, modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(), preview: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = ContextMenuHost(state, backdrop, items, modifier, colors, preview, content)

@Composable
private fun ContextMenuHost(
    state: LiquidMenuState, backdrop: Backdrop, items: List<LiquidMenuItem>, modifier: Modifier,
    colors: LiquidListColors, preview: (@Composable () -> Unit)?, content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val capture = remember(state, layer) { ContextPreview(state, layer) }
    CompositionLocalProvider(LocalContextPreview provides capture) {
        LiquidMenuHost(state, backdrop, modifier, rootOverlay = { behind ->
            state.previewTrigger?.let { trigger -> key(trigger) {
                ContextOverlay(state, trigger, capture, behind, items, colors, preview)
            } }
        }, content = content)
    }
}

@Composable
private fun ContextOverlay(
    state: LiquidMenuState, trigger: MenuTrigger, capture: ContextPreview, backdrop: Backdrop,
    items: List<LiquidMenuItem>, colors: LiquidListColors, preview: (@Composable () -> Unit)?,
) {
    val tokens = LocalLiquidGlassTokens.current.menu
    val dark = liquidGlassAppearance().dark
    val density = LocalDensity.current
    val scale = remember { Animatable(1f) }
    val elevation = remember { Animatable(0f) }
    val focus = remember { Animatable(0f) }
    val held = state.heldTrigger === trigger
    val active = state.expanded && state.sourceTrigger === trigger
    var showingPanel by remember { mutableStateOf(false) }
    if (active) SideEffect { showingPanel = true }
    val initial = remember(trigger) { trigger.fullBounds() ?: state.openingBounds ?: Rect.Zero }
    val bounds = trigger.fullBounds() ?: initial
    val width = bounds.width.coerceAtLeast(1f)
    val minScale = if (preview != null) 1f
    else maxOf(tokens.previewActiveScale, 1f - 2f * with(density) { tokens.previewMaxInset.toPx() } / width)
    DisposableEffect(trigger) { onDispose { capture.release(trigger) } }
    // Releasing the finger over an already active menu keeps the same animation target.
    LaunchedEffect(if (active) 2 else if (held) 1 else 0) {
        if (held && !active && scale.value == 1f) delay(tokens.previewArmDelayMillis.toLong())
        val spring = if (active) tokens.previewSettle.spec else tokens.previewLift.spec
        coroutineScope {
            launch { scale.animateTo(if (active) minScale else if (held) tokens.previewLiftScale else 1f, spring) }
            launch { elevation.animateTo(if (active) tokens.previewActiveElevation.value else if (held) tokens.previewHoldElevation.value else 0f, spring) }
            launch { focus.animateTo(if (active) 1f else 0f, tween(tokens.previewShadeMillis)) }
        }
        if (!active && !held) {
            snapshotFlow { showingPanel }.first { !it }
            state.retirePreview(trigger)
        }
    }
    val underlay = rememberLayerBackdrop()
    val behind = rememberCombinedBackdrop(backdrop, underlay)
    val host = state.hostLayout?.takeIf { it.isAttached } ?: return
    val anchor = bounds.translate(-host.positionInRoot())
    val safe = LocalMenuSafeBounds.current
    val metrics = liquidMenuMetrics()
    var measuredPreviewSize by remember(trigger) { mutableStateOf<IntSize?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safePad = with(density) { 16.dp.toPx() }
        val safeLeft = safe.left + safePad
        val safeRight = maxOf(safeLeft + 1f, minOf(safe.right, constraints.maxWidth.toFloat()) - safePad)
        val safeTop = safe.top + safePad
        val safeBottom = maxOf(safeTop + 1f, minOf(safe.bottom, constraints.maxHeight.toFloat()) - safePad)
        val desiredMenuH = with(density) { metrics.height(items).toPx() }
        val gapDp = if (preview != null) 10.dp else 7.dp
        val gapPx = with(density) { gapDp.toPx() }

        val targetRect = if (preview != null) {
            val maxPreviewW = (safeRight - safeLeft).coerceAtLeast(1f)
            val maxPreviewH = ((safeBottom - safeTop) * 0.44f).coerceAtLeast(1f)
            val fallbackW = with(density) { 240.dp.toPx() }.coerceAtMost(maxPreviewW)
            val fallbackH = with(density) { 114.dp.toPx() }.coerceAtMost(maxPreviewH)
            val targetW = (measuredPreviewSize?.width?.toFloat() ?: fallbackW).coerceIn(1f, maxPreviewW)
            val targetH = (measuredPreviewSize?.height?.toFloat() ?: fallbackH).coerceIn(1f, maxPreviewH)
            val targetLeft = (anchor.center.x - targetW * .5f).coerceIn(safeLeft, maxOf(safeLeft, safeRight - targetW))
            val totalNeeded = targetH + gapPx + desiredMenuH
            val targetTop = when {
                anchor.top + totalNeeded <= safeBottom -> anchor.top.coerceAtLeast(safeTop)
                safeBottom - safeTop >= totalNeeded -> (safeBottom - totalNeeded).coerceAtLeast(safeTop)
                anchor.center.y > (safeTop + safeBottom) * .5f -> (safeBottom - targetH).coerceAtLeast(safeTop)
                else -> safeTop
            }
            Rect(targetLeft, targetTop, targetLeft + targetW, targetTop + targetH)
        } else {
            val fitsBelow = anchor.bottom + gapPx + desiredMenuH <= safeBottom
            val fitsAbove = anchor.top - gapPx - desiredMenuH >= safeTop
            if (fitsBelow || fitsAbove || safeBottom - safeTop < anchor.height + gapPx + desiredMenuH) {
                anchor
            } else {
                val shiftUp = (anchor.bottom + gapPx + desiredMenuH) - safeBottom
                val shiftDown = safeTop - (anchor.top - gapPx - desiredMenuH)
                val dy = if (shiftUp <= shiftDown) -shiftUp else shiftDown
                anchor.translate(0f, dy)
            }
        }

        val displayRect = lerpRect(anchor, targetRect, focus.value.coerceIn(0f, 1f))
        val scrimColor = if (preview != null) Color.Black.copy(alpha = if (dark) .58f else .28f) else colors.contextScrim
        val cardFill = if (preview != null) (if (dark) Color(0xFF242426) else Color.White) else colors.surface

        Box(Modifier.matchParentSize().layerBackdrop(underlay)) {
            Spacer(Modifier.matchParentSize().graphicsLayer { alpha = focus.value.coerceIn(0f, 1f) }.background(scrimColor))
            Box(Modifier.align(AbsoluteAlignment.TopLeft)
                .absoluteOffset { IntOffset(displayRect.left.roundToInt(), displayRect.top.roundToInt()) }
                .requiredSize(with(density) { displayRect.width.toDp() }, with(density) { displayRect.height.toDp() })
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    shadowElevation = elevation.value.dp.toPx().coerceAtLeast(0f)
                    val targetRadius = if (preview != null) 22.dp.toPx() else tokens.previewCorner.toPx()
                    val radius = targetRadius * focus.value
                    val sourceShape = trigger.previewShape
                    fun corner(original: CornerSize?) = CornerSize(maxOf(radius, original?.toPx(size, this) ?: 0f))
                    shape = RoundedCornerShape(corner(sourceShape?.topStart), corner(sourceShape?.topEnd),
                        corner(sourceShape?.bottomEnd), corner(sourceShape?.bottomStart))
                    clip = true
                }.background(cardFill).clearAndSetSemantics {}) {
                val sourceFade = if (preview != null) (1f - focus.value).coerceIn(0f, 1f) else 1f
                if (sourceFade > .01f) {
                    Spacer(Modifier.matchParentSize().drawWithContent {
                        if (capture.source === trigger) drawLayer(capture.layer)
                        if (preview == null && dark && focus.value > 0f) {
                            drawRect(colors.surface.copy(alpha = focus.value), blendMode = BlendMode.Lighten)
                        }
                    }.graphicsLayer { alpha = sourceFade })
                }
                if (preview != null) {
                    val maxPreviewW = with(density) { (safeRight - safeLeft).coerceAtLeast(1f).toDp() }
                    val maxPreviewH = with(density) { ((safeBottom - safeTop) * 0.44f).coerceAtLeast(1f).toDp() }
                    Box(
                        Modifier.matchParentSize()
                            .wrapContentSize(androidx.compose.ui.Alignment.Center, unbounded = true)
                            .sizeIn(maxWidth = maxPreviewW, maxHeight = maxPreviewH)
                            .onSizeChanged { measuredPreviewSize = it }
                            .graphicsLayer {
                                alpha = focus.value.coerceIn(0f, 1f)
                                val sx = displayRect.width / targetRect.width.coerceAtLeast(1f)
                                val sy = displayRect.height / targetRect.height.coerceAtLeast(1f)
                                scaleX = sx
                                scaleY = sy
                            },
                    ) { preview() }
                }
            }
        }
        if (active || showingPanel) {
            val near = state.openingPoint?.minus(host.positionInRoot())
            LiquidPopupPanel(
                state = state,
                backdrop = behind,
                items = items,
                anchor = targetRect,
                hostWidth = maxWidth,
                hostHeight = maxHeight,
                gap = gapDp,
                touchX = if (preview != null) targetRect.center.x else near?.x,
                onClosed = { showingPanel = false },
            )
        }
    }
}
