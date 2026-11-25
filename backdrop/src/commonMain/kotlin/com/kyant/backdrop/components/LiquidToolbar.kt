package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.interaction.InteractiveHighlight
import com.kyant.backdrop.isRuntimeShaderSupported
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/** A stable key identifies the action across layout changes. The icon may change independently. */
data class LiquidToolbarItem<Icon>(
    val key: String,
    val label: String,
    val icon: Icon,
    val enabled: Boolean = true,
)

/** Related navigation arrows sit closer together than independent document commands. */
enum class LiquidToolbarSpacing { Navigation, Actions }

internal val LocalNavigationBar = staticCompositionLocalOf { false }

/** Applies the navigation bar's dark backing and disabled ink to the controls in a pane header. */
@Composable
fun LiquidNavigationBarStyle(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigationBar provides true, content = content)
}

object LiquidToolbarDefaults {
    val height = 44.dp
    fun buttonWidth(symbol: LiquidSymbol?, spacing: LiquidToolbarSpacing) =
        if (spacing == LiquidToolbarSpacing.Navigation) height else maxOf(height, ((symbol?.opticalWidth ?: 22) + 24).dp)

    fun gap(symbols: List<LiquidSymbol?>, spacing: LiquidToolbarSpacing) =
        if (spacing == LiquidToolbarSpacing.Navigation) 8.dp
        else (8f + ((symbols.maxOfOrNull { it?.opticalWidth ?: 22 } ?: 22) - 15) * .2f).coerceIn(8f, 10f).dp

    fun width(symbols: List<LiquidSymbol?>, spacing: LiquidToolbarSpacing = LiquidToolbarSpacing.Actions) = when (symbols.size) {
        0 -> 0.dp
        1 -> height
        else -> symbols.fold(0.dp) { sum, symbol -> sum + buttonWidth(symbol, spacing) } + gap(symbols, spacing) * (symbols.size - 1)
    }
}

@Immutable
data class LiquidNavBarAction(
    val key: String,
    val label: String,
    val symbol: LiquidSymbol,
    val tint: Color = Color.Unspecified,
    val enabled: Boolean = true,
    val menuState: LiquidMenuState? = null,
    val menuActivation: LiquidMenuActivation = LiquidMenuActivation.Press,
    val onShortTap: () -> Unit = {},
    val onClick: () -> Unit = {},
)

@Immutable
data class LiquidNavBarGroup(
    val actions: List<LiquidNavBarAction>,
    val spacing: LiquidToolbarSpacing = LiquidToolbarSpacing.Actions,
    val transitionGroup: String = "default",
)

/**
 * Transitions a shared navigation bar slot across hierarchy push/pop:
 * groups sharing the same [LiquidNavBarGroup.transitionGroup] morph capsule size & icons smoothly;
 * `null <-> non-null` or differing `transitionGroup` values perform a crisp full switch.
 */
@Composable
fun LiquidNavBarSlot(
    group: LiquidNavBarGroup?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val active = group?.takeIf { it.actions.isNotEmpty() }
    val replacement = LocalLiquidGlassTokens.current.replacement
    AnimatedContent(
        targetState = active?.transitionGroup,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (fadeIn(tween(replacement.toolbarEnterMillis)) + scaleIn(tween(replacement.toolbarEnterMillis + 20), initialScale = .82f))
                .togetherWith(
                    fadeOut(tween(replacement.toolbarExitMillis)) + scaleOut(tween(replacement.toolbarScaleExitMillis), targetScale = .82f),
                )
                .using(SizeTransform(clip = false))
        },
        label = "NavBar slot switch",
    ) { bucket ->
        if (bucket != null) {
            var retained by remember { mutableStateOf(active) }
            if (active != null && active.transitionGroup == bucket) {
                SideEffect { retained = active }
            }
            val currentGroup = retained ?: return@AnimatedContent
            LiquidToolbarReplacement(
                value = currentGroup,
                backdrop = backdrop,
                width = { grp -> LiquidToolbarDefaults.width(grp.actions.map { it.symbol }, grp.spacing) },
                contentKey = { grp -> grp.actions.map { it.key } },
            ) { displayed, current ->
                val defaultInk = LocalLiquidContentColor.current
                LiquidToolbarSlots(displayed.actions.map { it.symbol }, displayed.spacing) { index, area ->
                    val action = displayed.actions[index]
                    val iconContent: @Composable BoxScope.() -> Unit = {
                        LiquidContentReplacement(action.symbol to action.tint, alignment = Alignment.Center) { (sym, tintColor) ->
                            LiquidIcon(sym, if (tintColor.isSpecified) tintColor else defaultInk, Modifier.size(19.dp))
                        }
                    }
                    val triggerMod = if (current && action.menuState != null) {
                        area.liquidMenuTrigger(
                            state = action.menuState,
                            activation = action.menuActivation,
                            onShortTap = action.onShortTap,
                        )
                    } else {
                        area
                    }
                    if (current) {
                        LiquidToolbarButton(
                            label = action.label,
                            onClick = action.onClick,
                            modifier = triggerMod,
                            enabled = action.enabled,
                            content = iconContent,
                        )
                    } else {
                        Box(
                            area.graphicsLayer { alpha = if (action.enabled) 1f else .38f },
                            contentAlignment = Alignment.Center,
                            content = iconContent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A shared Surface glass toolbar. One lens, tint and touch field span every action.
 * The icon slot accepts the application's own symbols.
 */
@Composable
fun <Icon> LiquidToolbar(
    backdrop: Backdrop,
    items: List<LiquidToolbarItem<Icon>>,
    onClick: (LiquidToolbarItem<Icon>) -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    icon: @Composable BoxScope.(Icon) -> Unit,
) {
    LiquidToolbar(backdrop, items, onClick, LiquidToolbarSpacing.Actions, modifier, surfaceColor, icon)
}

@Composable
fun <Icon> LiquidToolbar(
    backdrop: Backdrop,
    items: List<LiquidToolbarItem<Icon>>,
    onClick: (LiquidToolbarItem<Icon>) -> Unit,
    spacing: LiquidToolbarSpacing,
    modifier: Modifier = Modifier,
    surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    icon: @Composable BoxScope.(Icon) -> Unit,
) {
    val replacement = LocalLiquidGlassTokens.current.replacement
    var lastItems by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) SideEffect { lastItems = items }
    val visibleItems = items.ifEmpty { lastItems }
    AnimatedVisibility(items.isNotEmpty(), modifier, enter = fadeIn(tween(replacement.toolbarEnterMillis)) + scaleIn(initialScale = .92f),
        exit = fadeOut(tween(replacement.toolbarExitMillis)) + scaleOut(tween(replacement.toolbarScaleExitMillis), targetScale = .92f)) {
        LiquidToolbarReplacement(visibleItems, backdrop,
            width = { row -> LiquidToolbarDefaults.width(row.map { it.icon as? LiquidSymbol }, spacing) },
            contentKey = { row -> row.map { it.key } }, surfaceColor = surfaceColor) { row, current ->
            val symbols = row.map { it.icon as? LiquidSymbol }
            val gap = LiquidToolbarDefaults.gap(symbols, spacing)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val fullWidth = LiquidToolbarDefaults.width(symbols, spacing)
                LazyRow(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                    userScrollEnabled = current && maxWidth < fullWidth, overscrollEffect = null) {
                    itemsIndexed(row, key = { _, action -> action.key }) { index, action ->
                        val before = if (index > 0) gap / 2 else 0.dp
                        val after = if (index < row.lastIndex) gap / 2 else 0.dp
                        val cell = if (row.size == 1) LiquidToolbarDefaults.height else LiquidToolbarDefaults.buttonWidth(symbols[index], spacing)
                        val bounds = Modifier.size(cell + before + after, LiquidToolbarDefaults.height)
                        val image: @Composable BoxScope.() -> Unit = {
                            LiquidContentReplacement(action.icon, Modifier.offset(x = (before - after) / 2),
                                alignment = Alignment.Center) { symbol -> icon(symbol) }
                        }
                        if (current) {
                            LiquidToolbarButton(action.label, { onClick(action) }, bounds,
                                enabled = items.isNotEmpty() && action.enabled, content = image)
                        } else Box(bounds.graphicsLayer { alpha = if (action.enabled) 1f else .38f },
                            contentAlignment = Alignment.Center, content = image)
                    }
                }
            }
        }
    }
}

/** A toolbar retains one glass surface while a route replaces its actions. Widths are resting widths. */
@Composable
fun <T> LiquidToolbarReplacement(
    value: T, backdrop: Backdrop, width: (T) -> Dp, modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it }, surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    content: @Composable BoxScope.(value: T, current: Boolean) -> Unit,
) {
    val tokens = LocalLiquidGlassTokens.current.replacement
    val key = contentKey(value)
    val targetWidth = width(value)
    val height = LiquidToolbarDefaults.height
    val presentedWidth by animateDpAsState(targetWidth, tokens.sizeSpring.dpSpec,
        label = "Toolbar width")
    var lastKey by remember { mutableStateOf(key) }
    var lastWidth by remember { mutableStateOf(targetWidth) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (lastKey != key) {
            val peak = if (lastWidth == targetWidth) tokens.sameSizeLift else tokens.resizedLift
            lastKey = key
            lastWidth = targetWidth
            lift.animateTo(peak, tween(tokens.liftMillis))
            lift.animateTo(0f, tokens.sizeSpring.spec)
        }
    }
    val growth = height * lift.value
    BoxWithConstraints(modifier) {
        val available = maxWidth
        val resting = presentedWidth.coerceIn(0.dp, available)
        Box(Modifier.size(resting, height).wrapContentSize(Alignment.Center, unbounded = true)) {
            LiquidToolbarGroup(Modifier.size((resting + growth).coerceAtLeast(1.dp), (height + growth).coerceAtLeast(1.dp)),
                backdrop, surfaceColor) {
                LiquidKeyedReplacement(value, Modifier.fillMaxSize(), Alignment.Center, contentKey) { displayed ->
                    val current = contentKey(displayed) == key
                    val semantic = if (current) Modifier else Modifier.clearAndSetSemantics {}
                    val rowWidth = width(displayed).coerceIn(0.dp, available)
                    val fit = if (rowWidth > resting && rowWidth > 0.dp) resting / rowWidth else 1f
                    Box(Modifier.fillMaxSize().then(semantic), contentAlignment = Alignment.Center) {
                        // Keep the incoming and outgoing rows inside the same moving glass edge.
                        Box(Modifier.wrapContentSize(Alignment.Center, unbounded = true)
                            .size(rowWidth, height).graphicsLayer { scaleX = fit; scaleY = fit },
                            contentAlignment = Alignment.Center) { content(displayed, current) }
                    }
                }
            }
        }
    }
}

/** Mixed controls (for example a button and a menu trigger) use the same optical spacing as LiquidToolbar. */
@Composable
fun LiquidToolbarSlots(symbols: List<LiquidSymbol>, spacing: LiquidToolbarSpacing = LiquidToolbarSpacing.Actions,
    content: @Composable (index: Int, hitArea: Modifier) -> Unit) {
    val gap = LiquidToolbarDefaults.gap(symbols, spacing)
    Row(verticalAlignment = Alignment.CenterVertically) {
        symbols.forEachIndexed { index, symbol ->
            val before = if (index == 0) 0.dp else gap / 2
            val after = if (index == symbols.lastIndex) 0.dp else gap / 2
            val width = if (symbols.size == 1) LiquidToolbarDefaults.height else LiquidToolbarDefaults.buttonWidth(symbol, spacing)
            CompositionLocalProvider(LocalToolbarIconOffset provides (before - after) / 2) {
                content(index, Modifier.size(width + before + after, LiquidToolbarDefaults.height))
            }
        }
    }
}

private val LocalToolbarIconOffset = compositionLocalOf { 0.dp }

/**
 * Groups custom controls into one capsule. Omit [backdrop] when the enclosing surface
 * already supplies the lens, as in a morphing capsule. The Surface tint is drawn once.
 */
@Composable
fun LiquidToolbarGroup(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tokens = LocalLiquidGlassTokens.current
    val group = remember(scope, tokens) {
        ToolbarTouchGroup(InteractiveHighlight(scope, tokens.press, intensity = 2.65f * tokens.highlightIntensity, radiusScale = 1.30f))
    }
    val pairSlot = LocalToolbarPairSlot.current?.takeIf { backdrop != null && surfaceColor == LiquidButtonDefaults.SurfaceColor }
    val container = if (pairSlot == null && backdrop != null) LocalGlassEffectContainerState.current else null
    val containerSlot = remember(container) { if (container != null) ToolbarPairSlot() else null }
    val slot = pairSlot ?: containerSlot
    DisposableEffect(pairSlot, container, containerSlot, group) {
        pairSlot?.group = group
        if (container != null && containerSlot != null) {
            containerSlot.group = group
            container.slots.add(containerSlot)
        }
        onDispose {
            if (pairSlot?.group === group) {
                pairSlot.group = null
                pairSlot.bounds = Rect.Zero
            }
            if (container != null && containerSlot != null) {
                container.slots.remove(containerSlot)
                containerSlot.group = null
                containerSlot.bounds = Rect.Zero
            }
        }
    }
    val shape = LiquidCapsule
    val hidden = LocalReplacedMenuSources.current.any { it.toolbar === group }
    val foreground = rememberGraphicsLayer()
    group.foreground = foreground
    val appearance = liquidGlassAppearance()
    val navigation = LocalNavigationBar.current
    val controlRole = LocalControlGlassRole.current
    val fill = appearance.overlay(surfaceColor)
    group.surfaceBackdrop = backdrop
    group.surfaceFill = fill
    group.surfaceCustomTint = appearance.customTintOnly(surfaceColor)
    group.surfaceRole = controlRole
    group.inNavigationBar = navigation
    val accessibility = if (hidden) Modifier.clearAndSetSemantics {} else Modifier
    Box(modifier.then(accessibility).drawWithContent { if (!hidden) drawContent() }
        .onGloballyPositioned {
            group.layout = it
            slot?.bounds = Rect(it.localToRoot(Offset.Zero), it.localToRoot(Offset(it.size.width.toFloat(), it.size.height.toFloat())))
        }, propagateMinConstraints = true) {
        val motion = if (backdrop != null) Modifier.graphicsLayer { group.light.motion.apply(this) } else Modifier
        Box(motion, propagateMinConstraints = true) {
            val surface = if (slot != null) Modifier else if (backdrop != null) {
                group.surfaceMaterial()
            } else Modifier.background(surfaceColor, shape)
            Spacer(Modifier.matchParentSize().then(surface))
            CompositionLocalProvider(LocalToolbarGroup provides group) {
                val clip = Modifier.drawWithContent {
                    foreground.record { this@drawWithContent.drawContent() }
                    drawLayer(foreground)
                }.clip(shape)
                if (backdrop == null) {
                    Box(clip.then(group.light.modifier), propagateMinConstraints = true, content = content)
                } else {
                    GlassForeground(clip) { content() }
                }
            }
        }
    }
}

/** A single action has its own Surface capsule. Actions placed in a group share its capsule. */
@Composable
fun LiquidToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val group = LocalToolbarGroup.current
    if (group == null) {
        LiquidToolbarGroup(modifier, backdrop) {
            ToolbarAction(label, onClick, enabled, Modifier, checkNotNull(LocalToolbarGroup.current), content)
        }
    } else {
        ToolbarAction(label, onClick, enabled, modifier, group, content)
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    group: ToolbarTouchGroup,
    content: @Composable BoxScope.() -> Unit,
) {
    val owner = remember { Any() }
    var layout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var keyDown by remember { mutableStateOf(false) }
    val disabledAlpha = if (LocalNavigationBar.current && liquidGlassAppearance().dark) .23f else .38f
    DisposableEffect(group, owner, enabled) { onDispose { group.release(owner, completed = false) } }
    Box(
        modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .onGloballyPositioned { layout = it }
            .semantics(mergeDescendants = true) { contentDescription = label }
            .onFocusChanged {
                if (!it.hasFocus && keyDown) {
                    keyDown = false
                    group.release(owner, completed = false)
                }
            }
            .onPreviewKeyEvent { event ->
                if (enabled && (event.key == Key.Enter || event.key == Key.Spacebar || event.key == Key.DirectionCenter)) {
                    if (event.type == KeyEventType.KeyDown && !keyDown) {
                        layout?.let { keyDown = group.press(owner, it, Offset(it.size.width / 2f, it.size.height / 2f)) }
                    } else if (event.type == KeyEventType.KeyUp && keyDown) {
                        keyDown = false
                        group.release(owner)
                    }
                }
                false
            }
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .pointerInput(group, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val source = layout ?: return@awaitEachGesture
                    if (!group.press(owner, source, down.position)) return@awaitEachGesture
                    var released = false
                    try {
                        do {
                            val pointer = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) { released = true; break }
                            if (pointer.position != pointer.previousPosition && !group.move(source, pointer.position)) break
                        } while (true)
                    } finally {
                        group.release(owner, completed = released)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset(x = LocalToolbarIconOffset.current).graphicsLayer {
            alpha = if (enabled) 1f else disabledAlpha
        }, contentAlignment = Alignment.Center, content = content)
    }
}

internal val LocalToolbarGroup = staticCompositionLocalOf<ToolbarTouchGroup?> { null }

internal class ToolbarTouchGroup(val light: InteractiveHighlight) {
    var foreground: GraphicsLayer? = null
    var layout: LayoutCoordinates? = null
    var surfaceBackdrop by mutableStateOf<Backdrop?>(null)
    var surfaceFill by mutableStateOf(Color.Transparent)
    var surfaceCustomTint by mutableStateOf<Color?>(null)
    var surfaceRole by mutableStateOf(LiquidGlassRole.Control)
    var inNavigationBar by mutableStateOf(false)

    /** Used at both ends of a menu morph so the landing capsule samples the actual toolbar backdrop. */
    @Composable
    fun surfaceMaterial(): Modifier {
        val backdrop = surfaceBackdrop ?: return Modifier
        val appearance = liquidGlassAppearance()
        val rim = rememberLiquidRimHighlight(appearance.dark)
        val hasShader = isRuntimeShaderSupported()
        return Modifier.drawBackdrop(backdrop, { LiquidCapsule },
            effects = {
                if (inNavigationBar) liquidNavBarButtonLens()
                else liquidSurfaceLens(appearance.controlBlurRadius, appearance, surfaceRole)
            },
            highlight = { rim }, shadow = { LiquidSurfaceShadow },
            onDrawSurface = {
                val tintFill = when {
                    inNavigationBar -> appearance.navBarButtonFill(surfaceCustomTint)
                    hasShader -> surfaceCustomTint
                    else -> surfaceFill
                }
                if (tintFill != null) drawRect(tintFill)
                light.drawLight(this, !inNavigationBar && !appearance.dark)
            })
    }
    private var owner: Any? = null

    fun press(action: Any, source: LayoutCoordinates, at: Offset): Boolean {
        if (owner != null) return false
        val point = pointInGroup(source, at) ?: return false
        owner = action
        light.pressToolbar(point)
        return true
    }

    fun move(source: LayoutCoordinates, at: Offset): Boolean {
        val point = pointInGroup(source, at) ?: return false
        light.moveToolbar(point)
        return true
    }

    fun release(action: Any, completed: Boolean = true) {
        if (owner !== action) return
        owner = null
        light.releaseToolbar(completed)
    }

    private fun pointInGroup(source: LayoutCoordinates, at: Offset): Offset? {
        val host = layout ?: return null
        if (!host.isAttached || !source.isAttached) return null
        return host.localPositionOf(source, at)
    }
}
