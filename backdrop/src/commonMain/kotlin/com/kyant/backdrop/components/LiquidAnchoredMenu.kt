package com.kyant.backdrop.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.morph.LiquidMorphAlignment
import com.kyant.backdrop.morph.LiquidMorphConfig
import com.kyant.backdrop.morph.LiquidMorphContainer

enum class LiquidMenuPresentation { ButtonTransform, Popup }

/** Presents a document menu from the specific toolbar slot marked with liquidMenuTrigger. */
@Composable
fun LiquidAnchoredMenuHost(state: LiquidMenuState, backdrop: Backdrop, items: List<LiquidMenuItem>,
    modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    LiquidAnchoredMenuHost(state, backdrop, items, modifier, LiquidMenuPresentation.ButtonTransform, content = content)

@Composable
fun LiquidAnchoredMenuHost(state: LiquidMenuState, backdrop: Backdrop, items: List<LiquidMenuItem>,
    modifier: Modifier = Modifier, presentation: LiquidMenuPresentation, actions: List<LiquidMenuItem> = emptyList(),
    slowMotion: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var returning by remember(state) { mutableStateOf(false) }
    if (state.expanded) SideEffect { returning = true }
    val overlayOpen = state.openingBounds != null && (state.expanded || returning)
    SideEffect { state.overlayActive = overlayOpen }
    DisposableEffect(state) { onDispose { state.overlayActive = false } }
    LiquidMenuHost(state, backdrop, modifier, rootOverlay = { behind ->
        val source = state.openingBounds
        if (source != null && (state.expanded || returning)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // This host was laid out before the trigger could be touched. Its overlay shares the origin.
                val at = state.hostLayout?.takeIf { it.isAttached }?.positionInRoot() ?: return@BoxWithConstraints
                val anchor = (state.openingGroupBounds ?: source).translate(-at)
                if (presentation == LiquidMenuPresentation.Popup) {
                    LiquidPopupPanel(state, behind, items, source.translate(-at), maxWidth, maxHeight, actions = actions,
                        slowMotion = slowMotion, fromBorderlessTrigger = true) { returning = false }
                    return@BoxWithConstraints
                }
                val density = LocalDensity.current
                val safe = LocalMenuSafeBounds.current
                val width = minOf(liquidMenuMetrics().width, (with(density) { safe.width.toDp() } - 20.dp).coerceAtLeast(1.dp))
                val fromRight = anchor.center.x > constraints.maxWidth / 2f
                val fromBottom = anchor.center.y > constraints.maxHeight / 2f
                val left = with(density) { anchor.left.toDp() }
                val top = with(density) { anchor.top.toDp() }
                val right = with(density) { anchor.right.toDp() }
                val bottom = with(density) { anchor.bottom.toDp() }
                val inset = 10.dp
                val height = minOf(liquidMenuMetrics().height(items, hasActions = actions.isNotEmpty()),
                    (with(density) { safe.height.toDp() } - inset * 2).coerceAtLeast(1.dp))
                val x = if (fromRight) right - width else left
                val y = if (fromBottom) bottom - height else top
                val destinationX = with(density) {
                    val min = safe.left.toDp() + inset
                    x.coerceIn(min, maxOf(min, safe.right.toDp() - width - inset))
                }
                val destinationY = with(density) {
                    val min = safe.top.toDp() + inset
                    y.coerceIn(min, maxOf(min, safe.bottom.toDp() - height - inset))
                }
                val alignment = when {
                    fromBottom && fromRight -> LiquidMorphAlignment.BottomTrailing
                    fromBottom -> LiquidMorphAlignment.BottomLeading
                    fromRight -> LiquidMorphAlignment.TopTrailing
                    else -> LiquidMorphAlignment.TopLeading
                }
                val config = remember(alignment, destinationX, destinationY, x, y) { LiquidMorphConfig(alignment = alignment,
                    expandedOffsetXDp = (destinationX - x).value, expandedOffsetYDp = (destinationY - y).value,
                    showsCollapsedSurface = true, menuOpeningRebound = true) }
                val motion = rememberMenuMotionPose(state, 0, fromRight, fromBottom)
                LiquidMorphContainer(state.expanded, { if (!it) state.dismiss() }, behind,
                    DpSize(right - left, bottom - top), DpSize(width, height),
                    modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x - 24.dp, y - 24.dp)
                        .menuPanelMotion(state, 0, 24.dp, fromRight, fromBottom, externalMotion = motion, translateOnly = true),
                    config = config, slowMo = slowMotion, initialExpanded = false, handleSurfaceTap = false, surfaceTouchEnabled = false,
                    sourceAnchorInRoot = source,
                    elasticStretchPx = motion.pose.stretch,
                    elasticCenterPx = motion.pose.center,
                    onSourceRestored = { returning = false }, collapsedContent = {
                        state.sourceTrigger?.toolbar?.foreground?.let { layer ->
                            Spacer(Modifier.fillMaxSize().drawWithContent { drawLayer(layer) })
                        }
                    },
                    expandedContent = { LiquidMenuPanel(state, items, Modifier.fillMaxSize(), actions) })
            }
        }
    }) {
        val scope = this
        val owners = LocalBorderlessMenuOwners.current
        CompositionLocalProvider(LocalBorderlessMenuOwners provides
            if (presentation == LiquidMenuPresentation.Popup) owners + state else owners) {
            MenuSourceHandoff(state, presentation == LiquidMenuPresentation.ButtonTransform && (state.expanded || returning)) { scope.content() }
        }
    }
}
