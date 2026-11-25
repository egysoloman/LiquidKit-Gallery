package com.kyant.backdrop.components

import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.coroutineContext

/** Menu dimensions on a 420-point reference viewport. Short viewports can scroll the panel independently. */
@Immutable
class LiquidMenuMetrics(viewportWidth: Dp, fontScale: Float = 1f) {
    // Only limit a panel which physically cannot fit. A phone or a split pane uses the same control sizes.
    val scale = ((viewportWidth - 20.dp) / 250.dp).coerceIn(.1f, 1f)
    private val textHeight = fontScale.coerceAtLeast(1f)
    val width = 250.dp * scale
    val inset = 10.dp * scale
    val row = 42.dp * scale * textHeight
    val subtitleRow = 58.dp * scale * textHeight
    val actions = 52.dp * scale * textHeight
    val section = 21.dp * scale
    val radius = 32.dp * scale
    val fontSize = (16f * scale).sp
    val smallFont = (12f * scale).sp

    fun height(items: List<LiquidMenuItem>, hasActions: Boolean = false, header: Boolean = false, rowHeight: Dp = row): Dp {
        var height = inset * 2
        if (hasActions) height += actions
        if (header) height += rowHeight + section
        for (item in items) {
            if (item.startsSection) height += section
            height += if (item.subtitle == null) rowHeight else maxOf(rowHeight, subtitleRow)
        }
        return height
    }
}

internal val LocalMenuMetrics = staticCompositionLocalOf { LiquidMenuMetrics(420.dp) }

@Composable
fun liquidMenuMetrics(): LiquidMenuMetrics = LocalMenuMetrics.current

/** Recedes the glass and its contents together, about the visible upper edge of the parent menu. */
fun Modifier.liquidMenuDepth(
    state: LiquidMenuState,
    level: Int = 0,
    inset: Dp = 0.dp,
    trailing: Boolean = true,
    bottom: Boolean = false,
): Modifier = menuPanelMotion(state, level, inset, trailing, bottom)

internal data class MenuMotionState(
    val pose: MenuPose = MenuPose(),
    val recedeScale: Float = 1f,
)

@Composable
internal fun rememberMenuMotionPose(
    state: LiquidMenuState,
    level: Int,
    trailing: Boolean = true,
    bottom: Boolean = false,
): MenuMotionState {
    val activeKey = if (state.activeLevel.size == level) state.activeLevel else null
    val viewport = activeKey?.let { state.viewports[it] }
    val density = LocalDensity.current.density
    val springs = LocalLiquidGlassTokens.current.press
    val physics = remember(density, springs, trailing, bottom) {
        MenuElasticity(
            density = density,
            tokens = springs,
            horizontalAway = if (trailing) -1f else 1f,
            verticalAway = if (bottom) -1f else 1f,
        )
    }
    var pose by remember(physics) { mutableStateOf(MenuPose()) }
    LaunchedEffect(viewport, physics) {
        val panel = viewport
        physics.attachScroll(panel?.fingerScroll ?: 0f)
        var lastRelease = panel?.releaseCount
        var lastMoveSeq = panel?.moveSeq ?: 0
        var idleFrames = 0
        val durationScale = coroutineContext[MotionDurationScale]
        // Sleep when settled. Read the latest finger target on each frame, without restarting the spring.
        snapshotFlow {
            listOf<Any?>(panel?.pull, panel?.fingerScroll, panel?.releaseCount, panel?.moveSeq)
        }.collect {
            var previous = 0L
            do {
                val moving = withFrameNanos { now ->
                    if (panel != null && lastRelease != panel.releaseCount) {
                        if (!panel.dragging) physics.lift(panel.releasedPull, panel.releaseVelocity)
                        lastRelease = panel.releaseCount
                    }
                    if (panel != null && panel.dragging) {
                        if (panel.moveSeq != lastMoveSeq) {
                            lastMoveSeq = panel.moveSeq
                            idleFrames = 0
                        } else {
                            idleFrames++
                            if (idleFrames >= 6) panel.decayMotion(0.65f)
                        }
                    } else {
                        idleFrames = 0
                    }
                    val scale = durationScale?.scaleFactor ?: 1f
                    val elapsed = if (previous == 0L) 16_000_000L else (now - previous).coerceAtLeast(1_000_000L)
                    previous = now
                    val nanos = if (scale <= 0f) 0L else (elapsed / scale.toDouble()).toLong()
                    val running = physics.advance(
                        pull = panel?.pull ?: Offset.Zero,
                        scrolled = panel?.fingerScroll ?: 0f,
                        dragging = panel?.dragging == true,
                        nanos = nanos,
                        fingerVelocity = panel?.pointerVelocity ?: Offset.Zero,
                        fingerAcceleration = panel?.pointerAcceleration ?: Offset.Zero,
                        fingerMoving = (panel?.dragging == true) && idleFrames < 6,
                    )
                    pose = physics.pose
                    running
                }
            } while (moving)
        }
    }
    val cover = state.branches.filter { it.level.size == level + 1 }.maxOfOrNull { it.reveal.value } ?: 0f
    val recedeScale = 1f - .028f * cover.coerceIn(0f, 1f)
    return MenuMotionState(pose = pose, recedeScale = recedeScale)
}

internal fun Modifier.menuPanelMotion(
    state: LiquidMenuState,
    level: Int,
    inset: Dp,
    trailing: Boolean,
    bottom: Boolean,
    externalMotion: MenuMotionState? = null,
    translateOnly: Boolean = false,
): Modifier = composed {
    val activeKey = if (state.activeLevel.size == level) state.activeLevel else null
    val motion = externalMotion ?: rememberMenuMotionPose(state, level, trailing, bottom)
    var outerBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    if (activeKey != null) {
        DisposableEffect(state, activeKey) {
            onDispose { state.restPanelBounds.remove(activeKey) }
        }
    }
    onGloballyPositioned { outerBox = it }.graphicsLayer {
        val scale = motion.recedeScale
        val width = (size.width - 2 * inset.toPx()).coerceAtLeast(1f)
        val height = (size.height - 2 * inset.toPx()).coerceAtLeast(1f)
        transformOrigin = TransformOrigin.Center
        scaleX = scale
        scaleY = scale
        // Shrinking a covered level still pivots about its original disclosure corner.
        val recede = (1f - scale) / 2
        val tx = (if (translateOnly) 0f else motion.pose.center.x) + (if (trailing) width else -width) * recede
        val ty = (if (translateOnly) 0f else motion.pose.center.y) + (if (bottom) height else -height) * recede
        translationX = tx
        translationY = ty
        val totalTx = tx + if (translateOnly) motion.pose.center.x else 0f
        val totalTy = ty + if (translateOnly) motion.pose.center.y else 0f
        val key = activeKey ?: if (level == 0) emptyList() else null
        val panelBounds = key?.let { state.panels[it].visibleBounds() }
        val box = outerBox?.takeIf { it.isAttached }
        val settledRest = key != null && state.viewports[key]?.dragging != true &&
            kotlin.math.abs(motion.pose.center.x) < 0.5f && kotlin.math.abs(motion.pose.center.y) < 0.5f &&
            kotlin.math.abs(motion.pose.stretch.x) < 0.5f && kotlin.math.abs(motion.pose.stretch.y) < 0.5f
        if (key != null && panelBounds != null && box != null && scale > .01f &&
            (settledRest || state.restPanelBounds[key] == null)
        ) {
            val pivot = box.localToRoot(Offset(box.size.width / 2f, box.size.height / 2f))
            state.restPanelBounds[key] = Rect(
                left = pivot.x + (panelBounds.left - totalTx - pivot.x) / scale,
                top = pivot.y + (panelBounds.top - totalTy - pivot.y) / scale,
                right = pivot.x + (panelBounds.right - totalTx - pivot.x) / scale,
                bottom = pivot.y + (panelBounds.bottom - totalTy - pivot.y) / scale,
            )
        }
    }
}

// Positive strain adds to the visible extent. Compression smoothly approaches zero size.
internal fun menuStretchScale(stretch: Float, extent: Float): Float {
    val strain = stretch / extent.coerceAtLeast(1f)
    return if (strain >= 0f) 1f + strain else 1f / (1f - strain)
}
