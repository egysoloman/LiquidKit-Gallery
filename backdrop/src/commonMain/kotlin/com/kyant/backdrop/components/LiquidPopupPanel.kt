package com.kyant.backdrop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.morph.LiquidMorphAlignment
import com.kyant.backdrop.morph.LiquidMorphConfig
import com.kyant.backdrop.morph.LiquidMorphContainer
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** An independent menu appears beside its trigger. The label, or a lifted context preview, stays in place. */
@Composable
internal fun BoxScope.LiquidPopupPanel(
    state: LiquidMenuState, backdrop: Backdrop, items: List<LiquidMenuItem>, anchor: Rect,
    hostWidth: Dp, hostHeight: Dp, gap: Dp = 4.dp, actions: List<LiquidMenuItem> = emptyList(), slowMotion: Boolean = false,
    fromBorderlessTrigger: Boolean = false, touchX: Float? = null, leadingCheckAtRoot: Boolean = true,
    onClosed: () -> Unit,
) {
    val metrics = liquidMenuMetrics()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = LocalMenuSafeBounds.current
    val leftLimit = with(density) { safe.left.toDp() } + 12.dp
    val rightLimit = with(density) { minOf(hostWidth, safe.right.toDp()) } - 12.dp
    val topLimit = with(density) { safe.top.toDp() } + 4.dp
    val bottomLimit = with(density) { minOf(hostHeight, safe.bottom.toDp()) } - 4.dp
    val left = with(density) { anchor.left.toDp() }
    val right = with(density) { anchor.right.toDp() }
    val top = with(density) { anchor.top.toDp() }
    val bottom = with(density) { anchor.bottom.toDp() }
    val width = minOf(metrics.width, (rightLimit - leftLimit).coerceAtLeast(1.dp))
    val desired = metrics.height(items, hasActions = actions.isNotEmpty())
    val middleY = (top + bottom) / 2
    val belowStart = if (fromBorderlessTrigger) middleY - metrics.row / 2 else bottom + gap
    val aboveEnd = if (fromBorderlessTrigger) middleY + metrics.row / 2 else top - gap
    val below = bottomLimit - belowStart
    val above = aboveEnd - topLimit
    val up = below < desired && above > below
    val available = if (up) above else below
    val height = minOf(desired, available.coerceAtLeast(1.dp))
    val trailing = (left + right) / 2 > hostWidth / 2
    val contact = touchX?.let { with(density) { it.toDp() } }
    val x = (if (contact != null) contact - width / 2 else if (right - left >= width) {
        if (direction == LayoutDirection.Ltr) left else right - width
    } else if (trailing) right - width else left).coerceIn(leftLimit, maxOf(leftLimit, rightLimit - width))
    val y = (if (up) aboveEnd - height else belowStart).coerceIn(topLimit, maxOf(topLimit, bottomLimit - height))
    if (fromBorderlessTrigger) {
        val alignment = when {
            up && trailing -> LiquidMorphAlignment.BottomTrailing
            up -> LiquidMorphAlignment.BottomLeading
            trailing -> LiquidMorphAlignment.TopTrailing
            else -> LiquidMorphAlignment.TopLeading
        }
        // The first material forms over the touched ink. Fit the finished panel independently.
        val seed = DpSize(minOf(right - left, width, metrics.row * 2), minOf(bottom - top, height, metrics.row) * .42f)
        val middleX = (left + right) / 2
        val seedX = if (trailing) middleX + seed.width / 2 - width else middleX - seed.width / 2
        val seedY = if (up) middleY + seed.height / 2 - height else middleY - seed.height / 2
        val inset = 24.dp
        val tokens = LocalLiquidGlassTokens.current.menu
        val motion = rememberMenuMotionPose(state, 0, trailing, up)
        LiquidMorphContainer(state.expanded, { if (!it) state.dismiss() }, backdrop,
            collapsedSize = seed, expandedSize = DpSize(width, height),
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(seedX - inset, seedY - inset)
                .wrapContentSize(AbsoluteAlignment.TopLeft, unbounded = true)
                .menuPanelMotion(state, 0, inset, trailing, up, externalMotion = motion, translateOnly = true),
            expandedCornerRadius = minOf(metrics.radius, height / 2),
            config = remember(alignment, x, y, seedX, seedY, tokens) { LiquidMorphConfig(alignment = alignment,
                openResponse = tokens.borderlessOpenResponse, closeResponse = tokens.borderlessCloseResponse,
                closeDamping = 0.78f,
                expandedOffsetXDp = (x - seedX).value, expandedOffsetYDp = (y - seedY).value, showsCollapsedSurface = false) },
            slowMo = slowMotion, initialExpanded = false, handleSurfaceTap = false, surfaceTouchEnabled = false,
            elasticStretchPx = motion.pose.stretch,
            elasticCenterPx = motion.pose.center,
            onSourceRestored = onClosed, collapsedContent = {},
            expandedContent = {
                LiquidMenuPanel(state, items, Modifier.fillMaxSize(), actions, leadingCheckAtRoot = leadingCheckAtRoot)
            })
        return
    }
    val pivot = TransformOrigin((((contact ?: ((left + right) / 2)) - x) / width).coerceIn(0f, 1f),
        (((top + bottom) / 2 - y) / height).coerceIn(0f, 1f))
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(.58f) }
    val rows = remember { Animatable(0f) }
    val menuTokens = LocalLiquidGlassTokens.current.menu
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(state.expanded) {
        val show = state.expanded
        val durationScale = if (slowMotion) 6 else 1
        coroutineScope {
            launch { alpha.animateTo(if (show) 1f else 0f, tween((if (show) menuTokens.popupEnterMillis else menuTokens.popupExitMillis) * durationScale)) }
            launch { scale.animateTo(if (show) 1f else .88f, if (show) menuTokens.popupScale.slowMotionScaled(durationScale).spec else tween(menuTokens.popupScaleExitMillis * durationScale)) }
            launch { rows.animateTo(if (show) 1f else 0f, tween(menuTokens.popupRowsMillis * durationScale, delayMillis = if (show) menuTokens.popupRowsDelayMillis * durationScale else 0)) }
        }
        if (!show) closed()
    }
    val replacement = LocalLiquidGlassTokens.current.replacement
    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val motion = rememberMenuMotionPose(state, 0, trailing, up)
    val stretchW = with(density) { motion.pose.stretch.x.coerceIn(-16.dp.toPx(), 16.dp.toPx()).toDp() }
    val stretchH = with(density) { motion.pose.stretch.y.coerceIn(-16.dp.toPx(), 16.dp.toPx()).toDp() }
    val liveW = (width + stretchW).coerceAtLeast(80.dp)
    val liveH = (height + stretchH).coerceAtLeast(48.dp)
    val shiftX = if (trailing) -(liveW - width) * .78f else -(liveW - width) * .22f
    val shiftY = if (up) -(liveH - height) * .78f else -(liveH - height) * .22f
    val shape = RoundedCornerShape(minOf(metrics.radius, liveH / 2))
    Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x + shiftX, y + shiftY).size(liveW, liveH).graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        this.alpha = alpha.value
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = pivot
    }.menuPanelMotion(state, 0, 0.dp, trailing, up, externalMotion = motion)) {
        val hasShader = isRuntimeShaderSupported()
        Spacer(Modifier.matchParentSize().liquidGlassShadow(shape, 1f, radius = metrics.radius)
            .drawBackdrop(backdrop, { shape }, effects = { liquidSurfaceLens(appearance.menuBlurRadius, appearance, LiquidGlassRole.Menu) },
                highlight = { rim }, shadow = null,
                onDrawSurface = { if (!hasShader) drawRect(appearance.menuOverlay(Color.Unspecified)) }))
        LiquidMenuPanel(
            state,
            items,
            Modifier.fillMaxSize().graphicsLayer { this.alpha = rows.value }.diffuseContent(replacement, { 1f - rows.value }),
            actions,
            leadingCheckAtRoot = leadingCheckAtRoot,
        )
    }
}
