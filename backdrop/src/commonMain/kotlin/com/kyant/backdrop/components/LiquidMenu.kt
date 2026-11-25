package com.kyant.backdrop.components

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp as lerpRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.menuBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.morph.LocalLiquidMorphBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlin.math.sin

internal val LocalMenuSafeBounds = compositionLocalOf { Rect.Zero }
internal val LocalMenuReturnOwner = staticCompositionLocalOf<LiquidMenuState?> { null }
private val LocalMenuHosts = compositionLocalOf<List<LiquidMenuState>> { emptyList() }

/** Keeps the original pointer for the duration of the gesture, including after the trigger melts away. */
@Composable
fun LiquidMenuHost(
    state: LiquidMenuState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    submenuInk: Color = Color.Unspecified,
    submenuTint: Color = Color.Unspecified,
    rootOverlay: (@Composable BoxScope.(Backdrop) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = LiquidGlassSamplingScope {
    val modalRegistry = LocalLiquidModalRegistry.current
    val ancestors = LocalMenuHosts.current
    val hosts = remember(ancestors, state) { ancestors + state }
    val exit = state.stackReturn
    val pageActive = state.expanded || state.dismissing || state.branches.isNotEmpty() || exit != null ||
        state.previewTrigger != null || state.heldTrigger != null || state.borderlessCover > 0f ||
        state.rootBody != null || state.overlayActive || state.restoringSource

    if (modalRegistry != null) {
        val modalEntry = remember(state) { LiquidModalMenuEntry(state) }
        val currentRootOverlay by rememberUpdatedState(rootOverlay)
        SideEffect {
            modalEntry.active = pageActive
            modalEntry.overlay = { _, _, _ ->
                if (modalEntry.active) {
                    val liveBackdrop = LocalLiquidModalBackdrop.current
                    val underMenu = if (liveBackdrop != null && backdrop != liveBackdrop) {
                        rememberCombinedBackdrop(backdrop, liveBackdrop)
                    } else {
                        liveBackdrop ?: backdrop
                    }
                    val overlayActive = state.branches.isNotEmpty()
                    val behindBranches = if (currentRootOverlay != null) {
                        val overlay = rememberLayerBackdrop()
                        Box(Modifier.matchParentSize().menuBackdrop(overlay, enabled = overlayActive)) {
                            val scene = if (state.restoringSource) backdrop else underMenu
                            CompositionLocalProvider(
                                LocalMenuReturnOwner provides state,
                                LocalMenuBackdrop provides backdrop,
                            ) {
                                currentRootOverlay?.invoke(this, scene)
                            }
                        }
                        rememberCombinedBackdrop(underMenu, overlay)
                    } else {
                        underMenu
                    }
                    val host = state.hostLayout?.takeIf { it.isAttached }
                    Box(Modifier.matchParentSize().then(if (exit == null) Modifier else Modifier.graphicsLayer {
                        val current = exit.bounds()
                        val h = host
                        if (current == null || h == null) {
                            alpha = 0f
                        } else {
                            val root = h.findRootCoordinates()
                            val remaining = minOf(current.width / exit.initial.width, current.height / exit.initial.height)
                            val progress = ((1f - remaining) / .38f).coerceIn(0f, 1f)
                            val merge = progress * progress * (3f - 2f * progress)
                            val source = lerpRect(exit.initial, exit.stack, merge)
                            val start = h.localPositionOf(root, source.topLeft)
                            val end = h.localPositionOf(root, current.topLeft)
                            scaleX = current.width / source.width.coerceAtLeast(1f)
                            scaleY = current.height / source.height.coerceAtLeast(1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = end.x - start.x * scaleX
                            translationY = end.y - start.y * scaleY
                            alpha = (remaining / .76f).coerceIn(0f, 1f)
                            if (merge > 0f && scaleX > 0f && scaleY > 0f) {
                                val edges = Rect(
                                    h.localPositionOf(root, exit.stack.topLeft),
                                    h.localPositionOf(root, exit.stack.bottomRight),
                                ).inflate(24.dp.toPx() * (1f - merge))
                                val radius = current.minDimension * (exit.body?.cornerFraction ?: .5f) * merge
                                val rounded = RoundRect(edges, CornerRadius(radius / scaleX, radius / scaleY))
                                shape = GenericShape { _, _ -> addRoundRect(rounded) }
                                clip = true
                            }
                        }
                    })) {
                        var behind = behindBranches
                        state.branches.forEachIndexed { index, branch ->
                            key(branch) {
                                val layer = rememberLayerBackdrop()
                                val parent = behind
                                Box(Modifier.matchParentSize().menuBackdrop(layer, enabled = index < state.branches.lastIndex)) {
                                    CompositionLocalProvider(LocalMenuBackdrop provides backdrop) {
                                        BranchPanel(state, branch, parent, submenuInk, submenuTint)
                                    }
                                }
                                behind = rememberCombinedBackdrop(parent, layer)
                            }
                        }
                    }
                }
            }
        }
        DisposableEffect(modalRegistry, modalEntry) {
            modalRegistry.menus.add(modalEntry)
            onDispose { modalRegistry.menus.remove(modalEntry) }
        }
        val modal = rootOverlay != null && (state.expanded || state.dismissing)
        CompositionLocalProvider(LocalMenuBackdrop provides backdrop) {
            Box(modifier.then(if (modal) Modifier.clearAndSetSemantics {} else Modifier), content = content)
        }
        return@LiquidGlassSamplingScope
    }

    val page = rememberLayerBackdrop()
    val underMenu = rememberCombinedBackdrop(backdrop, page)
    DisposableEffect(state, ancestors) {
        ancestors.forEach { it.descendants.add(state) }
        onDispose {
            ancestors.forEach { it.descendants.remove(state) }
            state.hostLayout = null
            state.detachHost()
        }
    }
    var layout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var window by remember { mutableStateOf(IntSize.Zero) }
    val menuTokens = LocalLiquidGlassTokens.current.menu
    LaunchedEffect(state, exit) {
        if (exit != null) {
            if (exit.body != null) snapshotFlow { exit.body.boundsInRoot() == null }.first { it }
            else exit.fallback.animateTo(0f, tween(menuTokens.stackReturnFallbackMillis))
            state.finishReturn(exit)
        }
    }
    // Disclosure, scrolling and the panel spring can all move another row under a stationary finger.
    LaunchedEffect(state, state.pointer != null) {
        while (state.pointer != null) {
            withFrameNanos { }
            state.refreshHit()
        }
    }
    val dwell = state.dwell
    LaunchedEffect(state, dwell) {
        if (dwell != null) {
            delay(if (dwell.row.header) 480L else 420L)
            state.finishDwell(dwell)
        }
    }
    BoxWithConstraints(
        modifier.onGloballyPositioned {
            layout = it
            state.hostLayout = it
            hostOrigin = it.positionInRoot()
            window = it.findRootCoordinates().size
        }.menuGestures(state, LocalLiquidGlassTokens.current.press.menuInsideDragRatio) { layout },
        propagateMinConstraints = true,
    ) {
        val width = maxWidth
        val fontScale = LocalDensity.current.fontScale
        val metrics = remember(width, fontScale) { LiquidMenuMetrics(width, fontScale) }
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing
        val safe = Rect(
            (insets.getLeft(density, direction) - hostOrigin.x).coerceAtLeast(0f),
            (insets.getTop(density) - hostOrigin.y).coerceAtLeast(0f),
            minOf(constraints.maxWidth.toFloat(), window.width - hostOrigin.x - insets.getRight(density, direction)),
            minOf(constraints.maxHeight.toFloat(), window.height - hostOrigin.y - insets.getBottom(density)),
        )
        CompositionLocalProvider(LocalMenuMetrics provides metrics, LocalMenuBackdrop provides backdrop,
            LocalMenuSafeBounds provides safe, LocalMenuHosts provides hosts) {
            val modal = rootOverlay != null && (state.expanded || state.dismissing)
            val overlayActive = state.branches.isNotEmpty()
            Box(Modifier.menuBackdrop(page, enabled = pageActive).then(if (modal) Modifier.clearAndSetSemantics {} else Modifier), content = content)
            val behindBranches = if (rootOverlay != null) {
                val overlay = rememberLayerBackdrop()
                Box(Modifier.matchParentSize().menuBackdrop(overlay, enabled = overlayActive)) {
                    // Once the real source is visible it must not be captured through its own
                    // returning glass. Both surfaces now sample the original source backdrop.
                    val scene = if (state.restoringSource) backdrop else underMenu
                    CompositionLocalProvider(LocalMenuReturnOwner provides state) { rootOverlay(scene) }
                }
                rememberCombinedBackdrop(underMenu, overlay)
            } else underMenu
            // All descendants ride the returning root glass, including their text and shadows.
            Box(Modifier.matchParentSize().then(if (exit == null) Modifier else Modifier.graphicsLayer {
                val current = exit.bounds()
                val host = layout?.takeIf { it.isAttached }
                if (current == null || host == null) {
                    alpha = 0f
                } else {
                    val root = host.findRootCoordinates()
                    val remaining = minOf(current.width / exit.initial.width, current.height / exit.initial.height)
                    val progress = ((1f - remaining) / .38f).coerceIn(0f, 1f)
                    val merge = progress * progress * (3f - 2f * progress)
                    val source = lerpRect(exit.initial, exit.stack, merge)
                    val start = host.localPositionOf(root, source.topLeft)
                    val end = host.localPositionOf(root, current.topLeft)
                    scaleX = current.width / source.width.coerceAtLeast(1f)
                    scaleY = current.height / source.height.coerceAtLeast(1f)
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = end.x - start.x * scaleX
                    translationY = end.y - start.y * scaleY
                    alpha = (remaining / .76f).coerceIn(0f, 1f)
                    if (merge > 0f && scaleX > 0f && scaleY > 0f) {
                        val edges = Rect(host.localPositionOf(root, exit.stack.topLeft),
                            host.localPositionOf(root, exit.stack.bottomRight)).inflate(24.dp.toPx() * (1f - merge))
                        val radius = current.minDimension * (exit.body?.cornerFraction ?: .5f) * merge
                        val rounded = RoundRect(edges, CornerRadius(radius / scaleX, radius / scaleY))
                        shape = GenericShape { _, _ -> addRoundRect(rounded) }
                        clip = true
                    }
                }
            })) {
                var behind = behindBranches
                state.branches.forEachIndexed { index, branch ->
                    key(branch) {
                        val layer = rememberLayerBackdrop()
                        val parent = behind
                        Box(Modifier.matchParentSize().menuBackdrop(layer, enabled = index < state.branches.lastIndex)) {
                            BranchPanel(state, branch, parent, submenuInk, submenuTint)
                        }
                        behind = rememberCombinedBackdrop(parent, layer)
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchPanel(state: LiquidMenuState, branch: MenuBranch, backdrop: Backdrop, ink: Color, tint: Color) {
    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val menuTokens = LocalLiquidGlassTokens.current.menu
    val returning = state.dismissing
    LaunchedEffect(branch, branch.open, returning) {
        if (returning) return@LaunchedEffect
            if (branch.open) {
                branch.reveal.animateTo(1f, menuTokens.branchOpen.spec)
            } else {
                branch.reveal.animateTo(0f, tween(menuTokens.branchCloseMillis))
                state.retire(branch)
            }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        Spacer(Modifier.matchParentSize().onGloballyPositioned {
            // During a stack return the outer layer moves this whole coordinate system.
            if (!state.dismissing) origin = it.positionInRoot()
        })
        val anchor = branch.anchor.translate(-origin)
        val parent = branch.parentPanel.translate(-origin)
        val gutter = with(density) { 8.dp.toPx() }
        val safe = LocalMenuSafeBounds.current
        val topSafe = safe.top + gutter
        val bottomSafe = (safe.bottom - gutter).coerceAtLeast(topSafe + 1f)
        val maxWidthPx = (safe.width - 2f * gutter).coerceAtLeast(1f)
        val width = parent.width.coerceIn(1f, maxWidthPx)
        val metrics = liquidMenuMetrics()
        val rowHeight = metrics.row
        val wanted = with(density) { metrics.height(branch.item.children, header = true).toPx() }
        val fullHeight = wanted.coerceIn(1f, bottomSafe - topSafe)
        val targetY = (anchor.top - with(density) { (18.dp * metrics.scale).toPx() }).coerceIn(topSafe, (bottomSafe - fullHeight).coerceAtLeast(topSafe))
        val x = parent.left.coerceIn(safe.left + gutter, maxOf(safe.left + gutter, safe.right - width - gutter))
        val p = branch.reveal.value.coerceIn(0f, 1f)
        val motion = rememberMenuMotionPose(state, branch.level.size, trailing = true, bottom = false)
        val maxStretchPx = with(density) { 16.dp.toPx() }
        val stretchW = motion.pose.stretch.x.coerceIn(-maxStretchPx, maxStretchPx)
        val stretchH = motion.pose.stretch.y.coerceIn(-maxStretchPx, maxStretchPx)
        val h = (lerp(anchor.height.coerceAtLeast(1f), fullHeight, p) + stretchH * p).coerceAtLeast(1f)
        val y = lerp(anchor.top, targetY, p) - stretchH * p * .22f
        val left = lerp(anchor.left, x, p) - stretchW * p * .78f
        val currentWidth = (lerp(anchor.width.coerceAtLeast(1f), width, p) + stretchW * p).coerceAtLeast(1f)
        val bend = sin(p * kotlin.math.PI.toFloat()).coerceAtLeast(0f)
        val shape = RoundedCornerShape(minOf(metrics.radius, with(density) { h.toDp() } / 2))
        val hasShader = isRuntimeShaderSupported()
        val menuFill = if (hasShader) appearance.customTintOnly(tint) else appearance.menuOverlay(tint)
        Box(
            Modifier.absoluteOffset { IntOffset(left.roundToInt(), y.roundToInt()) }
                .size(with(density) { currentWidth.toDp() }, with(density) { h.toDp() })
                .menuPanelMotion(state, level = branch.level.size, 0.dp, trailing = true, bottom = false, externalMotion = motion),
        ) {
            Spacer(Modifier.matchParentSize().graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha; alpha = (p / .28f).coerceIn(0f, 1f) }
                .liquidGlassShadow(shape, p, radius = metrics.radius)
                .drawBackdrop(backdrop, { shape }, highlight = { rim }, shadow = null, effects = {
                    vibrancy()
                    blur((appearance.menuBlurRadius + 4.dp * bend).toPx())
                    lens((10.dp + 5.dp * bend).toPx(), (16.dp + 10.dp * bend).toPx())
                    liquidGlassTone(appearance, LiquidGlassRole.Menu)
                }, onDrawSurface = { if (menuFill != null) drawRect(menuFill) }))
            CompositionLocalProvider(LocalLiquidContentColor provides branch.sourceInk) {
                GlassForeground(Modifier.fillMaxSize()) { sampled ->
                    MenuRows(
                        state, branch.level, branch.item.children, emptyList(), branch,
                        if (ink == Color.Unspecified) sampled else ink, rowHeight,
                        Modifier.fillMaxSize().clip(shape), disclosure = p,
                    )
                }
            }
        }
    }
}

/** Place inside the expanded slot of a LiquidMorphContainer. Child sheets grow from their parent row. */
@Composable
fun LiquidMenuPanel(
    state: LiquidMenuState,
    items: List<LiquidMenuItem>,
    modifier: Modifier = Modifier,
    actions: List<LiquidMenuItem> = emptyList(),
    ink: Color = Color.Unspecified,
    rowHeight: Dp = liquidMenuMetrics().row,
    leadingCheckAtRoot: Boolean = true,
) {
    val body = LocalLiquidMorphBody.current
    DisposableEffect(state, body) {
        state.rootBody = body
        onDispose { if (state.rootBody === body) state.rootBody = null }
    }
    Box(modifier, propagateMinConstraints = true) {
        PanelInk(LocalMenuBackdrop.current, ink, Color.Transparent) { foreground ->
            MenuRows(
                state,
                emptyList(),
                items,
                actions,
                null,
                foreground,
                rowHeight,
                Modifier.fillMaxSize(),
                leadingCheckAtRoot = leadingCheckAtRoot,
            )
        }
    }
}

@Composable
private fun PanelInk(backdrop: Backdrop?, ink: Color, tint: Color, content: @Composable (Color) -> Unit) {
    if (ink != Color.Unspecified || backdrop == null) {
        content(if (ink != Color.Unspecified) ink else LocalLiquidContentColor.current)
    } else {
        AdaptiveGlassForeground(backdrop, Modifier.fillMaxSize(), tint) { content(it) }
    }
}

@Composable
private fun MenuRows(
    state: LiquidMenuState, level: List<String>, items: List<LiquidMenuItem>, actions: List<LiquidMenuItem>,
    header: MenuBranch?, ink: Color, rowHeight: Dp, modifier: Modifier, disclosure: Float = 1f,
    leadingCheckAtRoot: Boolean = true,
) {
    var panel by remember { mutableStateOf<LayoutCoordinates?>(null) }
    DisposableEffect(state, level) {
        onDispose { if (state.panels[level] === panel) state.panels.remove(level) }
    }
    val dark = liquidGlassAppearance().dark
    val metrics = liquidMenuMetrics()
    val covering = state.branches.filter { it.level.size == level.size + 1 && it.level.dropLast(1) == level }
    val coverage = covering.maxOfOrNull { it.reveal.value } ?: 0f
    val coveredIndices = covering.map { branch -> items.indexOfFirst { it.key == branch.item.key } to branch }
    val dim = lerp(1f, if (dark) .58f else .72f, coverage.coerceIn(0f, 1f))
    val scroll = rememberLazyListState()
    val fling = ScrollableDefaults.flingBehavior()
    val scope = rememberCoroutineScope()
    val viewport = remember(scroll, fling, scope) { MenuViewport(scroll, fling, scope) }
    DisposableEffect(state, level, viewport) {
        state.viewports[level] = viewport
        onDispose {
            viewport.stop()
            if (state.viewports[level] === viewport) state.viewports.remove(level)
        }
    }
    val density = LocalDensity.current
    val pointer = state.pointer
    val selected = state.highlighted?.takeIf { it.level == level }?.layout?.takeIf { it.isAttached }
    val local = panel?.takeIf { it.isAttached }
    val hit = if (selected != null && local != null) local.localBoundingBoxOf(selected, clipBounds = true) else null
    var lastHit by remember { mutableStateOf<Rect?>(null) }
    if (hit != null && !hit.isEmpty) SideEffect { lastHit = hit }
    val rowBounds = hit?.takeUnless { it.isEmpty } ?: lastHit
    val menuTokens = LocalLiquidGlassTokens.current.menu
    val light by animateFloatAsState(if (hit != null) (if (dark) .15f else .11f) else 0f, menuTokens.touchLight.spec, label = "Menu touch light")
    val follow = if (rowBounds != null) {
        val global = selected.visibleBounds()
        val drag = if (pointer != null && global != null) (pointer - global.center) else Offset.Zero
        val limit = with(density) { 3.dp.toPx() }
        val offset = Offset((drag.x * .04f).coerceIn(-limit, limit), (drag.y * .10f).coerceIn(-limit, limit))
        val rect by animateRectAsState(rowBounds.translate(offset), menuTokens.touchFollow.rectSpec, label = "Menu touch follows row")
        Modifier.drawBehind {
            drawRoundRect(ink.copy(alpha = light), rect.topLeft, rect.size, CornerRadius(rect.height * .5f))
        }
    } else Modifier
    val inset = metrics.inset * disclosure
    val replacement = LocalLiquidGlassTokens.current.replacement
    val incoming = if (header == null) Modifier else Modifier.graphicsLayer {
        alpha = (disclosure / .40f).coerceIn(0f, 1f)
        val blur = replacement.blur.toPx() * sin(disclosure * kotlin.math.PI.toFloat())
        renderEffect = if (blur > .4f) BlurEffect(blur, blur, TileMode.Decal) else null
    }
    val checks = items.any { it.checked }
    LazyColumn(
        modifier.then(if (state.activeLevel != level) Modifier.clearAndSetSemantics {} else Modifier)
            .onGloballyPositioned { panel = it; state.panels[level] = it }
            .graphicsLayer { alpha = dim }.then(follow),
        state = scroll,
        contentPadding = PaddingValues(start = inset, top = inset, end = inset, bottom = metrics.inset),
    ) {
        if (header != null) item(key = "header") {
            MenuCell(state, level, header.item, ink, Modifier.fillMaxWidth().height(rowHeight),
                header = header, disclosure = disclosure)
            MenuDivider(metrics, ink)
        }
        if (actions.isNotEmpty()) item(key = "actions") {
            Row(Modifier.fillMaxWidth().height(metrics.actions).then(incoming)) {
                actions.forEach { item -> key(item.key) {
                    MenuCell(state, level, item, ink, Modifier.weight(1f).fillMaxHeight(), compact = true)
                } }
            }
        }
        itemsIndexed(items, key = { _, item -> "row:" + item.key }) { i, item ->
            val cover = coveredIndices.filter { it.first in 0..i }
            Column(incoming.graphicsLayer {
                val reveal = cover.maxOfOrNull { it.second.reveal.value } ?: 0f
                alpha = (1f - reveal / .72f).coerceIn(0f, 1f)
            }) {
                if (item.startsSection) MenuDivider(metrics, ink)
                val height = if (item.subtitle == null) rowHeight else maxOf(metrics.subtitleRow, rowHeight)
                val child = covering.firstOrNull { it.item.key == item.key }
                MenuCell(state, level, item, ink, Modifier.fillMaxWidth().height(height), checkColumn = checks,
                    leadingCheckAtRoot = leadingCheckAtRoot, labelInChild = child != null)
            }
        }
    }
}

@Composable
private fun MenuDivider(metrics: LiquidMenuMetrics, ink: Color) {
    Box(Modifier.fillMaxWidth().height(metrics.section), contentAlignment = Alignment.Center) {
        Spacer(Modifier.fillMaxWidth().padding(horizontal = metrics.inset).height(.6.dp).background(ink.copy(alpha = .14f)))
    }
}

@Composable
private fun MenuCell(
    state: LiquidMenuState, level: List<String>, item: LiquidMenuItem, defaultInk: Color, modifier: Modifier,
    compact: Boolean = false, header: MenuBranch? = null, checkColumn: Boolean = false,
    leadingCheckAtRoot: Boolean = true, disclosure: Float = 0f, labelInChild: Boolean = false,
) {
    val ink = if (item.destructive) liquidGlassAppearance().destructiveColor else defaultInk
    val target = remember(level, item.key, header) { MenuRow(level, header != null, item) }
    SideEffect {
        target.item = item
        target.ink = ink
        target.leadingCheck = checkColumn && (level.isNotEmpty() || leadingCheckAtRoot)
    }
    DisposableEffect(state, target) {
        state.rows.add(target)
        onDispose { state.rows.remove(target) }
    }
    val metrics = liquidMenuMetrics()
    val enabled = item.enabled && state.expanded && !state.dismissing && state.activeLevel == level
    Box(
        modifier.onGloballyPositioned { target.layout = it }
            .clip(if (compact) RoundedCornerShape(16.dp * metrics.scale) else LiquidCapsule)
            .semantics { if (item.checked) selected = true }
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button) { state.activate(target) }
            .graphicsLayer { alpha = if (item.enabled) 1f else .40f },
        contentAlignment = Alignment.Center,
    ) {
        if (labelInChild) return@Box
        if (compact) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item.symbol?.let { LiquidIcon(it, ink, Modifier.size(19.dp * metrics.scale)) }
                BasicText(item.title, style = TextStyle(ink, metrics.smallFont, FontWeight.Medium), maxLines = 1)
            }
        } else {
            val source = header?.source ?: target
            val foreground = if (header != null && header.sourceInk != Color.Unspecified)
                lerpColor(header.sourceInk, ink, disclosure) else ink
            val style = MenuLabelStyle(item, foreground, source.level.isEmpty(),
                if (header == null) checkColumn && (level.isNotEmpty() || leadingCheckAtRoot) else source.leadingCheck, disclosure)
            if (item.children.isEmpty()) MenuRowLabel(style) else source.label(style)
        }
    }
}

internal data class MenuLabelStyle(
    val item: LiquidMenuItem, val ink: Color, val rootRow: Boolean, val leadingCheck: Boolean, val disclosure: Float,
)

/** A disclosing row moves this same content into its child's header and back into its original slot. */
@Composable
internal fun MenuRowLabel(style: MenuLabelStyle) {
    val (item, ink, rootRow, leadingCheck, progress) = style
    val metrics = liquidMenuMetrics()
    val gap = 13.dp * metrics.scale
    val startPadding = lerp(if (leadingCheck && rootRow) 10f else if (rootRow) 16f else 10f, 16f, progress).dp * metrics.scale
    val endPadding = lerp(if (rootRow) 16f else 10f, 16f, progress).dp * metrics.scale
    val weight = FontWeight(lerp(500f, 600f, progress).roundToInt())
    Row(Modifier.fillMaxWidth().padding(start = startPadding, end = endPadding), verticalAlignment = Alignment.CenterVertically) {
        if (leadingCheck) {
            val remaining = 1f - progress
            Box(Modifier.width(13.dp * metrics.scale * remaining), contentAlignment = Alignment.Center) {
                if (item.checked) LiquidIcon(LiquidSymbol.Check, ink.copy(alpha = ink.alpha * remaining),
                    Modifier.size(13.dp * metrics.scale * remaining))
            }
            Spacer(Modifier.width(6.dp * metrics.scale * remaining))
        }
        item.symbol?.let {
            LiquidIcon(it, ink, Modifier.size(20.dp * metrics.scale))
            Spacer(Modifier.width(gap))
        }
        Column(Modifier.weight(1f)) {
            BasicText(item.title, style = TextStyle(ink, metrics.fontSize, weight), maxLines = 1)
            item.subtitle?.let { BasicText(it, style = TextStyle(ink.copy(alpha = .65f), metrics.smallFont), maxLines = 1) }
        }
        if (item.checked && rootRow && !leadingCheck) {
            Spacer(Modifier.width(gap * (1f - progress)))
            LiquidIcon(LiquidSymbol.Check, ink.copy(alpha = ink.alpha * (1f - progress)),
                Modifier.size(15.dp * metrics.scale * (1f - progress)))
        }
        if (item.children.isNotEmpty()) {
            Spacer(Modifier.width(gap))
            val turn = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 90f else -90f
            LiquidIcon(LiquidSymbol.Forward, ink, Modifier.size(15.dp * metrics.scale).graphicsLayer {
                rotationZ = turn * progress
            })
        }
    }
}
