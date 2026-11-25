package com.kyant.backdrop.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.morph.LiquidMorphAlignment
import com.kyant.backdrop.morph.LiquidMorphConfig
import com.kyant.backdrop.morph.LiquidMorphContainer
import kotlin.math.roundToInt
import kotlin.math.tanh

private val LocalHistoryVisible = staticCompositionLocalOf { false }

@Composable
private fun historyPressTint() = Color.White.copy(alpha = if (liquidGlassAppearance().dark) .42f else .92f)

/** Back and directory occupy a single glass surface. Only a hold originating on Back opens history. */
@Composable
fun LiquidNavigationControls(
    state: LiquidMenuState?, backdrop: Backdrop, onBack: () -> Unit, onDirectory: (() -> Unit)?,
    backLabel: String, directoryLabel: String, modifier: Modifier = Modifier,
) {
    if (onDirectory == null) {
        LiquidNavigationBackButton(state, backdrop, backLabel, onBack, modifier)
        return
    }
    var group by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val covered = LocalHistoryVisible.current && state?.openingGroupBounds != null
    LiquidToolbarGroup(modifier.onGloballyPositioned { group = it }.graphicsLayer { alpha = if (covered) 0f else 1f }, backdrop) {
        LiquidToolbarSlots(listOf(LiquidSymbol.Back, LiquidSymbol.Sidebar), LiquidToolbarSpacing.Navigation) { index, slot ->
            if (index == 0) {
                val back = if (state == null) Modifier else Modifier.liquidMenuTrigger(state, LiquidMenuActivation.LongPress,
                    beforeOpen = { state.openingGroupBounds = group.visibleBounds() }, onShortTap = onBack)
                LiquidToolbarButton(backLabel, onBack, slot.then(back)) { LiquidIcon(LiquidSymbol.Back) }
            } else LiquidToolbarButton(directoryLabel, onDirectory, slot) { LiquidIcon(LiquidSymbol.Sidebar) }
        }
    }
}

/** Supplies the measured back button and its history panel with one uninterrupted touch stream. */
@Composable
fun LiquidNavigationHistoryHost(
    state: LiquidMenuState,
    backdrop: Backdrop,
    ancestors: List<LiquidMenuItem>,
    modifier: Modifier = Modifier,
    ink: Color = Color.Unspecified,
    rootSymbol: LiquidSymbol? = null,
    content: @Composable BoxScope.() -> Unit,
) = LiquidNavigationHistoryHost(state, backdrop, ancestors, modifier, ink, rootSymbol, LiquidSymbol.Sidebar, content)

@Composable
fun LiquidNavigationHistoryHost(
    state: LiquidMenuState, backdrop: Backdrop, ancestors: List<LiquidMenuItem>, modifier: Modifier = Modifier,
    ink: Color = Color.Unspecified, rootSymbol: LiquidSymbol? = null, groupAccessory: LiquidSymbol,
    content: @Composable BoxScope.() -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    val visible = state.openingBounds != null && (state.expanded || closing)
    var lastAncestors by remember { mutableStateOf(ancestors) }
    if (state.expanded) SideEffect {
        closing = true
        lastAncestors = ancestors
    }
    SideEffect { state.overlayActive = visible }
    DisposableEffect(state) { onDispose { state.overlayActive = false } }
    LiquidMenuHost(state, backdrop, modifier, submenuInk = ink, rootOverlay = { behind ->
        val bounds = state.openingBounds
        if (visible && bounds != null) {
            val destination = if (ancestors.isEmpty()) rootSymbol else LiquidSymbol.Back
            HistoryPanel(state, behind, bounds, if (state.expanded) ancestors else lastAncestors, ink, destination, groupAccessory) { closing = false }
        }
    }) {
        val scope = this
        MenuSourceHandoff(state, visible) {
            CompositionLocalProvider(LocalHistoryVisible provides (visible && !state.restoringSource)) { scope.content() }
        }
    }
}

/** A quick release goes back once. Holding brightens and enlarges this same Surface before history opens. */
@Composable
fun LiquidNavigationBackButton(
    state: LiquidMenuState?,
    backdrop: Backdrop,
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = Color.Unspecified,
) {
    val covered = LocalHistoryVisible.current
    val diameter = 44.dp * liquidMenuMetrics().scale
    val history = if (state == null) Modifier else Modifier.liquidMenuTrigger(state,
        LiquidMenuActivation.LongPress, onShortTap = onBack)
    Box(modifier.size(diameter).then(history)) {
        LiquidToolbarGroup(Modifier.fillMaxSize().graphicsLayer {
            alpha = if (covered) 0f else 1f
        }, backdrop) {
            LiquidToolbarButton(label, onBack, Modifier.fillMaxSize()) {
                LiquidIcon(LiquidSymbol.Back, ink)
            }
        }
    }
}

/** The root toolbar action occupies the same landing position as the back button on deeper pages. */
@Composable
fun LiquidNavigationRootButton(
    backdrop: Backdrop, symbol: LiquidSymbol, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val covered = LocalHistoryVisible.current
    LiquidToolbarButton(label, onClick, modifier.size(44.dp * liquidMenuMetrics().scale)
        .graphicsLayer { alpha = if (covered) 0f else 1f }, backdrop = backdrop) { LiquidIcon(symbol) }
}

@Composable
private fun HistoryPanel(
    state: LiquidMenuState, backdrop: Backdrop, source: Rect, items: List<LiquidMenuItem>, ink: Color, destination: LiquidSymbol?, groupAccessory: LiquidSymbol, onClosed: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var origin by remember { mutableStateOf(Offset.Zero) }
        Spacer(Modifier.matchParentSize().onGloballyPositioned { origin = it.positionInRoot() })
        val metrics = liquidMenuMetrics()
        val density = LocalDensity.current
        val group = state.openingGroupBounds
        val anchor = (group ?: source).translate(-origin)
        val menuTokens = LocalLiquidGlassTokens.current.menu
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val hot by animateFloatAsState(if (state.expanded) 1.34f else 1f, menuTokens.historyHotSpring.spec, label = "Return to back button")
        val sourceSize = with(density) {
            if (group != null) DpSize(anchor.width.toDp(), anchor.height.toDp())
            else (anchor.minDimension * hot).toDp().let { DpSize(it, it) }
        }
        val safe = LocalMenuSafeBounds.current
        val historyHeight = maxOf(64.dp * metrics.scale, metrics.height(items))
            .coerceAtMost((with(density) { (safe.bottom - anchor.top).toDp() } - 12.dp).coerceAtLeast(1.dp))
        val available = with(density) { if (rtl) anchor.right.toDp() else maxWidth - anchor.left.toDp() }
        val panelWidth = minOf(metrics.width, (available - 20.dp).coerceAtLeast(48.dp))
        val limit = with(density) { 9.dp.toPx() }
        val pull = state.openingPull
        val finger by animateOffsetAsState(Offset(limit * tanh(.22f * pull.x / limit), limit * tanh(.22f * pull.y / limit)),
            menuTokens.historyPullSpring.offsetSpec, label = "History follows the held finger")
        val sourceTint by animateColorAsState(
            if (state.expanded) historyPressTint() else LiquidButtonDefaults.SurfaceColor,
            tween(menuTokens.historyTintMillis), label = "Restore button material",
        )
        val menuTint = if (liquidGlassAppearance().dark) Color(0xff242828).copy(alpha = .46f) else Color.White.copy(alpha = .34f)
        val config = remember(metrics, sourceTint, menuTint, rtl) {
            LiquidMorphConfig(
                alignment = if (rtl) LiquidMorphAlignment.TopTrailing else LiquidMorphAlignment.TopLeading,
                triggerBiasX = 0f,
                collapsedSurfaceColor = sourceTint,
                expandedSurfaceColor = menuTint,
                expandedOffsetXDp = (if (rtl) -10f else 10f) * metrics.scale,
                blurRadiusDp = 3.5f,
                tintAlpha = .045f,
                menuOpeningRebound = true,
            )
        }
        LiquidMorphContainer(
            expanded = state.expanded,
            onExpandedChange = { if (!it) state.dismiss() },
            backdrop = backdrop,
            collapsedSize = sourceSize,
            expandedSize = DpSize(panelWidth, historyHeight),
            sourceAnchorInRoot = source,
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset {
                val margin = 24.dp.toPx()
                val left = if (rtl) anchor.center.x + sourceSize.width.toPx() / 2 - maxOf(sourceSize.width, panelWidth).toPx()
                    else anchor.center.x - sourceSize.width.toPx() / 2
                IntOffset((left - margin + finger.x).roundToInt(), (anchor.center.y - sourceSize.height.toPx() / 2 - margin + finger.y).roundToInt())
            }.menuPanelMotion(state, 0, 24.dp, rtl, false),
            expandedCornerRadius = minOf(metrics.radius, historyHeight / 2),
            config = config,
            initialExpanded = false,
            handleSurfaceTap = false,
            surfaceTouchEnabled = false,
            onSourceRestored = onClosed,
            collapsedContent = {
                if (group != null) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround) {
                    LiquidIcon(destination ?: LiquidSymbol.Back, ink)
                    LiquidIcon(groupAccessory, ink)
                } else if (!state.expanded && destination != null) LiquidIcon(destination, ink)
            },
            expandedContent = {
                val row = maxOf(metrics.row, (historyHeight - metrics.inset * 2) / items.size.coerceAtLeast(1))
                LiquidMenuPanel(state, items, Modifier.fillMaxSize(),
                    ink = if (ink == Color.Unspecified) LocalLiquidContentColor.current else ink, rowHeight = row)
            },
        )
    }
}
