package com.kyant.backdrop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

// Nested menu hosts can independently transfer ownership of their measured source.
internal val LocalBorderlessMenuOwners = compositionLocalOf { emptySet<LiquidMenuState>() }
internal val LocalReplacedMenuSources = compositionLocalOf { emptyList<MenuTrigger>() }

@Composable
internal fun MenuSourceHandoff(state: LiquidMenuState, presenting: Boolean, content: @Composable () -> Unit) {
    val source = state.sourceTrigger
    val inherited = LocalReplacedMenuSources.current
    CompositionLocalProvider(LocalReplacedMenuSources provides
        if (presenting && !state.restoringSource && source != null) inherited + source else inherited, content = content)
}

/** Marks precisely the action allowed to open the menu. Other buttons retain their normal touch behavior. */
fun Modifier.liquidMenuTrigger(
    state: LiquidMenuState,
    activation: LiquidMenuActivation = LiquidMenuActivation.Press,
    beforeOpen: () -> Unit = {},
    onShortTap: () -> Unit = {},
    onHoldChange: (Boolean) -> Unit = {},
): Modifier = registerMenuTrigger(state, activation, beforeOpen, onShortTap, onHoldChange, borderless = false)

/** The ink lifts on contact and dissolves as the independent popup covers its anchor. */
fun Modifier.liquidBorderlessMenuTrigger(state: LiquidMenuState, beforeOpen: () -> Unit = {}): Modifier =
    registerMenuTrigger(state, LiquidMenuActivation.Press, beforeOpen, {}, {}, borderless = true)

internal fun Modifier.registerMenuTrigger(
    state: LiquidMenuState, activation: LiquidMenuActivation, beforeOpen: () -> Unit,
    onShortTap: () -> Unit, onHoldChange: (Boolean) -> Unit, borderless: Boolean,
    previewShape: RoundedCornerShape? = null,
): Modifier = composed {
    val open by rememberUpdatedState(beforeOpen)
    val tap by rememberUpdatedState(onShortTap)
    val hold by rememberUpdatedState(onHoldChange)
    val trigger = remember(state, activation) { MenuTrigger(activation, { open() }, { tap() }, { hold(it) }) }
    val group = LocalToolbarGroup.current
    val plain = borderless || (activation == LiquidMenuActivation.Press && state in LocalBorderlessMenuOwners.current)
    val tokens = LocalLiquidGlassTokens.current
    val corners = if (activation == LiquidMenuActivation.ListContext) previewShape ?: LocalListItemShape.current else null
    SideEffect {
        trigger.toolbar = group
        trigger.borderlessHoldMillis = if (plain) tokens.menu.borderlessHoldMillis.toLong() else null
        trigger.previewShape = corners
    }
    val hidden = trigger in LocalReplacedMenuSources.current
    val preview = if (activation == LiquidMenuActivation.ListContext) LocalContextPreview.current else null
    DisposableEffect(state, trigger) {
        state.triggers.add(trigger)
        onDispose { state.removeTrigger(trigger) }
    }
    val ink = if (plain) {
        val engaged = state.heldTrigger === trigger || (state.expanded && state.sourceTrigger === trigger)
        val pressed by animateFloatAsState(if (engaged) 1f else 0f,
            tokens.press.engage.spec, label = "Borderless menu contact")
        val activeHere = state.expanded && state.sourceTrigger === trigger
        val cover = if (state.sourceTrigger === trigger) state.borderlessCover else 0f
        var wasActive by remember { mutableStateOf(false) }
        var reboundSeq by remember { mutableIntStateOf(0) }
        val rebound = remember { Animatable(1f) }
        val triggerLanding = wasActive && !state.expanded && (state.sourceTrigger !== trigger || cover < 0.85f)
        SideEffect {
            if (activeHere) {
                wasActive = true
            } else if (triggerLanding) {
                wasActive = false
                reboundSeq++
            }
        }
        LaunchedEffect(activeHere) {
            if (activeHere && rebound.value != 1f) {
                rebound.snapTo(1f)
                trigger.borderlessReboundScale = 1f
            }
        }
        LaunchedEffect(reboundSeq) {
            if (reboundSeq > 0) {
                rebound.snapTo(1.14f)
                trigger.borderlessReboundScale = 1.14f
                rebound.animateTo(1f, spring(dampingRatio = 0.46f, stiffness = 460f)) {
                    trigger.borderlessReboundScale = value
                }
                trigger.borderlessReboundScale = 1f
            }
        }
        Modifier.clickable(interactionSource = null, indication = null, role = Role.Button) { state.openFrom(trigger) }
            .graphicsLayer {
                val activeCover = if (state.sourceTrigger === trigger) state.borderlessCover else 0f
                alpha = ((1f - activeCover) * (1f - (1f - tokens.menu.borderlessPressedAlpha) * pressed)).coerceIn(0f, 1f)
                scaleX = (1f + tokens.menu.borderlessExpansion * pressed) * rebound.value
                scaleY = scaleX
            }
    } else Modifier
    then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)
        .drawWithContent {
            if (preview?.owner === state && state.previewTrigger === trigger) {
                val firstCapture = preview.source !== trigger
                preview.capture(trigger, this)
                if (firstCapture) drawContent()
            } else if (!hidden || group != null) drawContent()
        }
        .onGloballyPositioned { trigger.layout = it }.then(ink).semantics {
        if (activation == LiquidMenuActivation.Press) onClick { state.openFrom(trigger); true }
        onLongClick {
            state.openFrom(trigger)
            true
        }
    }
}

internal fun Modifier.menuGestures(
    state: LiquidMenuState,
    insideDragRatio: Float = GlassPressTokens().menuInsideDragRatio,
    host: () -> LayoutCoordinates?,
): Modifier =
    pointerInput(state, insideDragRatio) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val layout = host()?.takeIf { it.isAttached } ?: return@awaitEachGesture
            val start = layout.localToRoot(down.position)
            if (down.isConsumed) return@awaitEachGesture
            val openedByThisPress = !state.expanded
            if (openedByThisPress && state.descendants.any {
                    (it.expanded || it.dismissing) && it.hostLayout.visibleBounds()?.contains(start) == true
                }) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            val rearm = slop.coerceAtLeast(1f)
            if (openedByThisPress) {
                // Check the origin exactly once. Sliding into a trigger cannot acquire a menu.
                val trigger = state.triggerAt(start) ?: return@awaitEachGesture
                val listContext = trigger.activation == LiquidMenuActivation.ListContext
                if (!listContext) {
                    down.consume()
                }
                val isHistory = trigger.activation == LiquidMenuActivation.LongPress
                val timeout = trigger.borderlessHoldMillis ?:
                    maxOf(viewConfiguration.longPressTimeoutMillis, if (isHistory) 600L else 500L)
                var transferred = false
                state.beginHold(start, trigger)
                try {
                    trigger.onHold(true)
                    val release = withTimeoutOrNull(timeout) {
                        awaitTriggerRelease(state, layout, trigger, down.id, start, slop)
                    }
                    if (release == TriggerRelease.Cancelled || state.expanded ||
                        trigger !in state.triggers || trigger.layout.visibleBounds() == null
                    ) return@awaitEachGesture
                    if (release == TriggerRelease.Tap && listContext) return@awaitEachGesture
                    if (release == TriggerRelease.Tap && isHistory) trigger.onTap()
                    else state.openFrom(trigger)
                    if (release == TriggerRelease.Tap) return@awaitEachGesture
                    transferred = true
                    state.trackingFromTrigger = true
                } finally {
                    trigger.onHold(false)
                    if (!transferred) state.finishHold()
                }
            } else {
                down.consume()
                if (!state.containsPanel(start)) {
                    state.dismiss()
                    return@awaitEachGesture
                }
                if (state.panels[state.activeLevel].visibleBounds()?.contains(start) != true) {
                    state.back()
                    return@awaitEachGesture
                }
                state.viewports[state.activeLevel]?.stop()
                state.point(start, rearm)
            }

            val tappedRow = if (openedByThisPress) null else state.highlighted
            val trackingHistory = openedByThisPress && state.sourceTrigger?.activation == LiquidMenuActivation.LongPress
            var moved = false
            var viewport: MenuViewport? = null
            val velocity = DragReleaseVelocity(down.uptimeMillis, start)
            var previous = start
            var panelOrigin = start
            var engaged = false
            var keepOpen = false
            var scrollWithFinger = !openedByThisPress
            try {
                do {
                    val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                    val change = changes.firstOrNull { it.id == down.id } ?: break
                    if (!layout.isAttached || change.isConsumed || changes.any { it.id != down.id && it.pressed }) break
                    val at = layout.localToRoot(change.position)
                    for (sample in change.historical) velocity.move(sample.uptimeMillis, layout.localToRoot(sample.position))
                    velocity.move(change.uptimeMillis, at)
                    moved = moved || (at - start).getDistance() > slop
                    val level = state.activeLevel
                    val current = state.viewports[level]
                    if (current !== viewport) {
                        viewport?.release()
                        viewport = current
                        current?.stop()
                        // A menu may be mounted or disclose another level under this same finger.
                        // Begin measuring the pull at first contact with the panel.
                        panelOrigin = if (openedByThisPress) previous else start
                        engaged = false
                        scrollWithFinger = !openedByThisPress
                    }
                    val bounds = state.panels[level].visibleBounds()
                    val scrollable = current?.scrollable == true
                    if (!engaged && current != null && bounds != null &&
                        (!openedByThisPress || bounds.contains(at) || moved)) {
                        engaged = true
                        if (openedByThisPress && !bounds.contains(previous)) panelOrigin = at
                        previous = panelOrigin
                    }
                    if (engaged && current != null && moved && at != previous) {
                        val delta = at - previous
                        if (!openedByThisPress && bounds != null && (at.y < bounds.top || at.y > bounds.bottom)) {
                            scrollWithFinger = true
                        }
                        val rest = state.restPanelBounds[level] ?: bounds
                        val dragPull = computeMenuDragPull(at, panelOrigin, rest, insideDragRatio)
                        current.drag(
                            delta,
                            scrollRows = if (openedByThisPress) false else scrollWithFinger,
                            outsidePull = dragPull,
                            timeMillis = change.uptimeMillis,
                        )
                        // Releasing an already-open drag or a stretched scrollable menu keeps the menu open.
                        if (!openedByThisPress || scrollable) {
                            keepOpen = true
                        }
                    }
                    previous = at
                    val insidePanel = bounds?.contains(at) == true
                    val outsideRest = computeMenuBoundaryPull(at, state.restPanelBounds[level] ?: bounds) != Offset.Zero
                    if (change.pressed) {
                        state.moveHold(at)
                        if (!insidePanel || outsideRest) {
                            state.clearHoldHighlight()
                        } else if (openedByThisPress && scrollable) {
                            // Scrollable long menus only stretch on hold-drag without highlighting items.
                            if (moved) state.clearHoldHighlight()
                        } else if (!openedByThisPress || moved || trackingHistory) {
                            state.point(at, rearm)
                        }
                    } else {
                        if (current?.dragging == true) current.release(velocity.atRelease(change.uptimeMillis))
                        if (shouldCommitHoldDragRelease(
                                openedByThisPress = openedByThisPress,
                                scrollable = scrollable,
                                moved = moved,
                                insidePanel = insidePanel,
                                keepOpen = keepOpen,
                                trackingHistory = trackingHistory,
                                containsPanel = state.containsPanel(at),
                            )
                        ) {
                            state.release(at, rearm, if (moved) null else tappedRow)
                        }
                    }
                    change.consume()
                } while (change.pressed && state.expanded)
            } finally {
                viewport?.release(Velocity.Zero)
                state.finishHold()
                state.endTracking()
            }
        }
    }

internal fun computeMenuBoundaryPull(point: androidx.compose.ui.geometry.Offset, restBounds: androidx.compose.ui.geometry.Rect?): androidx.compose.ui.geometry.Offset {
    if (restBounds == null) return androidx.compose.ui.geometry.Offset.Zero
    val dx = when {
        point.x < restBounds.left -> point.x - restBounds.left
        point.x > restBounds.right -> point.x - restBounds.right
        else -> 0f
    }
    val dy = when {
        point.y < restBounds.top -> point.y - restBounds.top
        point.y > restBounds.bottom -> point.y - restBounds.bottom
        else -> 0f
    }
    return androidx.compose.ui.geometry.Offset(dx, dy)
}

/**
 * Computes C0-continuous menu pull across all edges of [restBounds]:
 * inside the panel it applies subtle [insideRatio] elastic follow matching iOS 27.1;
 * outside the panel it preserves the full 1.0x boundary pull plus the interior displacement.
 */
internal fun computeMenuDragPull(
    point: androidx.compose.ui.geometry.Offset,
    origin: androidx.compose.ui.geometry.Offset,
    restBounds: androidx.compose.ui.geometry.Rect?,
    insideRatio: Float,
): androidx.compose.ui.geometry.Offset {
    if (restBounds == null) return androidx.compose.ui.geometry.Offset.Zero
    val clampedPoint = androidx.compose.ui.geometry.Offset(
        point.x.coerceIn(restBounds.left, restBounds.right),
        point.y.coerceIn(restBounds.top, restBounds.bottom),
    )
    val clampedOrigin = androidx.compose.ui.geometry.Offset(
        origin.x.coerceIn(restBounds.left, restBounds.right),
        origin.y.coerceIn(restBounds.top, restBounds.bottom),
    )
    val ratio = insideRatio.coerceIn(0f, 1f)
    val inside = (clampedPoint - clampedOrigin) * ratio
    val outside = point - clampedPoint
    return inside + outside
}

internal fun shouldCommitHoldDragRelease(
    openedByThisPress: Boolean,
    scrollable: Boolean,
    moved: Boolean,
    insidePanel: Boolean,
    keepOpen: Boolean,
    trackingHistory: Boolean,
    containsPanel: Boolean,
): Boolean {
    if (keepOpen) return false
    if (!openedByThisPress) return true
    if (scrollable) return false
    return (moved && insidePanel) || (trackingHistory && containsPanel)
}

private enum class TriggerRelease { Tap, Cancelled }

private suspend fun AwaitPointerEventScope.awaitTriggerRelease(
    state: LiquidMenuState,
    host: LayoutCoordinates,
    trigger: MenuTrigger,
    id: PointerId,
    start: Offset,
    slop: Float,
): TriggerRelease {
    while (true) {
        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
        val change = changes.firstOrNull { it.id == id } ?: return TriggerRelease.Cancelled
        if (change.isConsumed || !host.isAttached || trigger !in state.triggers ||
            changes.any { it.id != id && it.pressed }
        ) return TriggerRelease.Cancelled
        val at = host.localToRoot(change.position)
        if ((at - start).getDistance() > slop ||
            trigger.layout.visibleBounds()?.inflate(slop * .35f)?.contains(at) != true
        ) return TriggerRelease.Cancelled
        state.moveHold(at)
        if (trigger.activation != LiquidMenuActivation.ListContext) change.consume()
        if (!change.pressed) return TriggerRelease.Tap
    }
}
