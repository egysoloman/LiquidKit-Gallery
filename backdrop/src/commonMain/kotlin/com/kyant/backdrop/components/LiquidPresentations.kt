package com.kyant.backdrop.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as floatLerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.SolidColorBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LiquidSheetDetent(val fraction: Float) { Medium(0.52f), Large(0.95f) }

/** Resolves final sheet detent after drag/fling, returning `null` when the sheet should dismiss. */
internal fun resolveSheetDetent(
    currentFraction: Float,
    dragDeltaFraction: Float,
    velocityPxPerSec: Float,
    dismissVelocityPxPerSec: Float,
    detents: List<LiquidSheetDetent>,
    dismissThreshold: Float,
): LiquidSheetDetent? {
    val ordered = detents.sortedBy { it.fraction }
    if (ordered.isEmpty()) return null
    val smallest = ordered.first()
    if (velocityPxPerSec >= dismissVelocityPxPerSec) {
        if (currentFraction <= smallest.fraction + 0.08f) return null
        return ordered.lastOrNull { it.fraction < currentFraction - 0.02f } ?: smallest
    }
    if (velocityPxPerSec <= -dismissVelocityPxPerSec) {
        return ordered.firstOrNull { it.fraction > currentFraction + 0.02f } ?: ordered.last()
    }
    val projected = (currentFraction - dragDeltaFraction).coerceAtMost(1f)
    if (projected < smallest.fraction * (1f - dismissThreshold.coerceIn(0f, 0.9f))) return null
    return ordered.minBy { abs(it.fraction - projected) }
}

internal fun computeSheetBackgroundDepth(
    fraction: Float,
    mediumFraction: Float = LiquidSheetDetent.Medium.fraction,
    largeFraction: Float = LiquidSheetDetent.Large.fraction,
): Float = if (largeFraction <= mediumFraction) 0f
else ((fraction - mediumFraction) / (largeFraction - mediumFraction)).coerceIn(0f, 1f)

internal class LiquidModalEntry {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

internal class LiquidModalMenuEntry(val state: LiquidMenuState) {
    var active by mutableStateOf(false)
    var overlay by mutableStateOf<@Composable BoxScope.(hostOrigin: Offset, maxWidth: Dp, maxHeight: Dp) -> Unit>({ _, _, _ -> })
}

/** Returns whether any modal sheet, alert, popover, or menu overlay inside [LiquidModalHost] is active. */
@Composable
fun rememberLiquidModalActive(): Boolean {
    val registry = LocalLiquidModalRegistry.current ?: return false
    return registry.entries.isNotEmpty() || registry.menus.any {
        it.active ||
            it.state.expanded ||
            it.state.dismissing ||
            it.state.branches.isNotEmpty() ||
            it.state.stackReturn != null
    }
}

internal class LiquidModalRegistry {
    val entries = mutableStateListOf<LiquidModalEntry>()
    val menus = mutableStateListOf<LiquidModalMenuEntry>()
    var sheetBackgroundDepth by mutableFloatStateOf(0f)
}

internal val LocalLiquidModalRegistry = staticCompositionLocalOf<LiquidModalRegistry?> { null }
internal val LocalLiquidModalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val DarkFloatingSheenBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = .03f), Color.Transparent, Color.Black.copy(alpha = .04f)),
)
private val LightFloatingSheenBrush = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = .16f), Color.White.copy(alpha = .02f), Color(0xFFDCE0E8).copy(alpha = .06f)),
)

/**
 * Same-window modal container allowing [LiquidAlert], [LiquidSheet], [LiquidActionSheet], [LiquidPopover],
 * and modal-hosted [LiquidMenuState] panels to sample the underlying window [Backdrop] with zero clipping.
 */
@Composable
fun LiquidModalHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val registry = remember { LiquidModalRegistry() }
    var hostLayout by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    val registeredMenus = registry.menus.toList()
    DisposableEffect(registeredMenus) {
        registeredMenus.forEach { a ->
            registeredMenus.forEach { b -> if (a !== b) a.state.descendants.add(b.state) }
        }
        onDispose {
            registeredMenus.forEach { a ->
                registeredMenus.forEach { b -> if (a !== b) a.state.descendants.remove(b.state) }
            }
        }
    }

    registeredMenus.forEach { entry ->
        key(entry) {
            val state = entry.state
            val menuTokens = LocalLiquidGlassTokens.current.menu
            val exit = state.stackReturn
            LaunchedEffect(state, exit) {
                if (exit != null) {
                    if (exit.body != null) snapshotFlow { exit.body.boundsInRoot() == null }.first { it }
                    else exit.fallback.animateTo(0f, tween(menuTokens.stackReturnFallbackMillis))
                    state.finishReturn(exit)
                }
            }
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
            SideEffect { state.hostLayout = hostLayout }
            DisposableEffect(state) {
                onDispose {
                    state.hostLayout = null
                    state.detachHost()
                }
            }
        }
    }

    val insideDragRatio = LocalLiquidGlassTokens.current.press.menuInsideDragRatio
    val gestureMod = registeredMenus.fold(Modifier as Modifier) { acc, entry ->
        acc.menuGestures(entry.state, insideDragRatio) { hostLayout }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .onGloballyPositioned {
                hostLayout = it
                hostOrigin = it.positionInRoot()
                windowSize = it.findRootCoordinates().size
                registeredMenus.forEach { entry -> entry.state.hostLayout = it }
            }
            .then(gestureMod),
        propagateMinConstraints = true,
    ) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val fontScale = density.fontScale
        val metrics = remember(maxWidth, fontScale) { LiquidMenuMetrics(maxWidth, fontScale) }
        val insets = WindowInsets.safeDrawing
        val safe = Rect(
            (insets.getLeft(density, direction) - hostOrigin.x).coerceAtLeast(0f),
            (insets.getTop(density) - hostOrigin.y).coerceAtLeast(0f),
            minOf(constraints.maxWidth.toFloat(), windowSize.width - hostOrigin.x - insets.getRight(density, direction)),
            minOf(constraints.maxHeight.toFloat(), windowSize.height - hostOrigin.y - insets.getBottom(density)),
        )
        CompositionLocalProvider(
            LocalLiquidModalRegistry provides registry,
            LocalMenuMetrics provides metrics,
            LocalMenuSafeBounds provides safe,
        ) {
            val hasSheetDepth by remember(registry) { derivedStateOf { registry.sheetBackgroundDepth > 0.001f } }
            val hasModalOrMenu by remember(registry) {
                derivedStateOf {
                    registry.entries.isNotEmpty() || registry.menus.any {
                        it.active || it.state.expanded || it.state.dismissing || it.state.branches.isNotEmpty() || it.state.stackReturn != null
                    }
                }
            }
            val liveModalBackdrop = rememberLayerBackdrop()
            Box(Modifier.matchParentSize().layerBackdrop(liveModalBackdrop, enabled = hasModalOrMenu)) {
                if (hasSheetDepth) {
                    Spacer(Modifier.matchParentSize().background(Color.Black))
                }
                Box(
                    Modifier.matchParentSize()
                        .graphicsLayer {
                            val depth = registry.sheetBackgroundDepth.coerceIn(0f, 1f)
                            if (depth > 0.001f) {
                                val scale = 1f - .065f * depth
                                scaleX = scale
                                scaleY = scale
                                translationY = 10.dp.toPx() * depth
                                shape = RoundedCornerShape(22.dp * depth)
                                clip = true
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val depth = registry.sheetBackgroundDepth.coerceIn(0f, 1f)
                            if (depth > 0.001f) {
                                drawRect(Color.Black.copy(alpha = .14f * depth))
                            }
                        },
                ) {
                    content()
                }
            }
            CompositionLocalProvider(LocalLiquidModalBackdrop provides liveModalBackdrop) {
                registry.menus.forEach { entry ->
                    key(entry) {
                        Box(Modifier.matchParentSize()) {
                            entry.overlay(this, hostOrigin, this@BoxWithConstraints.maxWidth, this@BoxWithConstraints.maxHeight)
                        }
                    }
                }
                registry.entries.forEach { entry ->
                    key(entry) {
                        Box(Modifier.matchParentSize()) {
                            entry.content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiquidModalLayer(
    scrimAlpha: Float,
    onDismissRequest: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val registry = LocalLiquidModalRegistry.current
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    if (registry != null) {
        val entry = remember { LiquidModalEntry() }
        SideEffect {
            entry.content = {
                Box(
                    Modifier.fillMaxSize().onPreviewKeyEvent {
                        if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                            currentDismiss()
                            true
                        } else {
                            false
                        }
                    },
                ) {
                    content(currentDismiss)
                }
            }
        }
        DisposableEffect(registry, entry) {
            registry.entries.add(entry)
            onDispose { registry.entries.remove(entry) }
        }
    } else {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            ConfigureAlertWindow(scrimAlpha)
            SyncLiquidDialogSystemBars(liquidGlassAppearance().dark)
            Box(Modifier.fillMaxSize()) {
                content(currentDismiss)
            }
        }
    }
}

internal enum class ModalSurfaceStyle { FloatingGlass, SheetSurface }

@Composable
internal fun Modifier.liquidModalSurface(
    shape: androidx.compose.ui.graphics.Shape,
    backdrop: Backdrop?,
    colors: LiquidListColors,
    elevation: Float = 1f,
    cornerRadius: androidx.compose.ui.unit.Dp = 28.dp,
    blurRadius: Dp? = null,
    style: ModalSurfaceStyle = ModalSurfaceStyle.FloatingGlass,
    scrimTint: Float = 0f,
): Modifier {
    val appearance = liquidGlassAppearance()
    val floating = style == ModalSurfaceStyle.FloatingGlass
    val rim = rememberLiquidRimHighlight(appearance.dark, layeredEdge = false)
    val liveBackdrop = LocalLiquidModalBackdrop.current
    val resolvedBackdrop = if (liveBackdrop != null && (backdrop == null || backdrop is SolidColorBackdrop)) {
        liveBackdrop
    } else {
        backdrop
    }
    return if (resolvedBackdrop != null) {
        val effectiveBlur = blurRadius ?: if (floating) {
            floatLerp(16f, 26f, appearance.opacity).dp
        } else {
            floatLerp(10f, 18f, appearance.opacity).dp
        }
        val veil = if (floating) {
            if (appearance.dark) Color(0xFF1F2023).copy(alpha = floatLerp(.54f, .80f, appearance.opacity))
            else Color(0xFFFAFBFD).copy(alpha = floatLerp(.52f, .82f, appearance.opacity))
        } else {
            if (appearance.dark) Color(0xFF1C1C1E).copy(alpha = floatLerp(.76f, .94f, appearance.opacity))
            else Color(0xFFF2F3F7).copy(alpha = floatLerp(.78f, .94f, appearance.opacity))
        }
        val specularSheen = if (floating) {
            if (appearance.dark) DarkFloatingSheenBrush else LightFloatingSheenBrush
        } else {
            null
        }
        liquidGlassShadow(shape, elevation, radius = cornerRadius)
            .drawBackdrop(
                backdrop = resolvedBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(effectiveBlur.toPx())
                    if (floating) {
                        lens(
                            refractionHeight = 10.dp.toPx(),
                            refractionAmount = 16.dp.toPx(),
                            depthEffect = false,
                            chromaticAberration = false,
                        )
                    } else {
                        lens(
                            refractionHeight = 12.dp.toPx(),
                            refractionAmount = 22.dp.toPx(),
                            depthEffect = false,
                            chromaticAberration = false,
                        )
                    }
                },
                highlight = {
                    rim.copy(
                        alpha = if (floating) {
                            if (appearance.dark) .22f else .46f
                        } else {
                            if (appearance.dark) .26f else .44f
                        },
                    )
                },
                shadow = null,
                onDrawSurface = {
                    if (scrimTint > 0f && (!floating || appearance.dark)) {
                        drawRect(Color.Black.copy(alpha = scrimTint * if (appearance.dark) .22f else .32f))
                    }
                    drawRect(veil)
                    if (specularSheen != null) {
                        drawRect(specularSheen)
                    }
                },
            )
    } else {
        val tokens = LocalLiquidGlassTokens.current.alert
        val fill = lerp(colors.surface, colors.ink, if (appearance.dark) tokens.darkSurfaceMix else tokens.lightSurfaceMix)
        val top = lerp(fill, Color.White, if (appearance.dark) .04f else .07f)
        val fallbackBrush = remember(top, fill) { Brush.verticalGradient(listOf(top, fill)) }
        liquidGlassShadow(shape, elevation, radius = cornerRadius)
            .clip(shape)
            .background(fallbackBrush)
    }
}

/** Bottom-presented sheet supporting Medium/Large detents, drag grabber resize, swipe-to-dismiss, and Liquid Glass optics. */
@Composable
fun LiquidSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    title: String? = null,
    doneLabel: String? = "完成",
    tint: Color = Color(0xff007aff),
    detents: List<LiquidSheetDetent> = listOf(LiquidSheetDetent.Medium, LiquidSheetDetent.Large),
    initialDetent: LiquidSheetDetent = detents.firstOrNull() ?: LiquidSheetDetent.Medium,
    colors: LiquidListColors = liquidListColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val registry = LocalLiquidModalRegistry.current
    val appearance = liquidGlassAppearance()
    val ordered = remember(detents) { detents.ifEmpty { listOf(LiquidSheetDetent.Medium) }.sortedBy { it.fraction } }
    val sheetTokens = LocalLiquidGlassTokens.current.sheet
    val hasInWindowHost = registry != null

    LiquidModalLayer(scrimAlpha = if (hasInWindowHost) 0f else sheetTokens.scrimAlpha, onDismissRequest = onDismissRequest) { dismiss ->
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        var activeDetent by remember { mutableStateOf(if (initialDetent in ordered) initialDetent else ordered.first()) }
        val fractionAnim = remember { Animatable(activeDetent.fraction) }
        val revealAnim = remember { Animatable(0f) }
        val scrimAnim = remember { Animatable(0f) }
        var closing by remember { mutableStateOf(false) }

        val depthStart = remember(ordered) {
            ordered.firstOrNull { it != ordered.last() }?.fraction ?: minOf(LiquidSheetDetent.Medium.fraction, ordered.last().fraction * .6f)
        }
        val depthEnd = ordered.last().fraction
        SideEffect {
            if (registry != null) {
                registry.sheetBackgroundDepth = if (closing) 0f
                else computeSheetBackgroundDepth(fractionAnim.value, depthStart, depthEnd) * revealAnim.value.coerceIn(0f, 1f)
            }
        }
        DisposableEffect(registry) {
            onDispose { registry?.sheetBackgroundDepth = 0f }
        }

        fun finishDismiss() {
            if (closing) return
            closing = true
            registry?.sheetBackgroundDepth = 0f
            scope.launch {
                launch { scrimAnim.animateTo(0f, tween(180)) }
                revealAnim.animateTo(0f, tween(215, easing = FastOutSlowInEasing))
                dismiss()
            }
        }

        LaunchedEffect(Unit) {
            launch { scrimAnim.animateTo(if (hasInWindowHost) sheetTokens.scrimAlpha else 0f, tween(210)) }
            revealAnim.animateTo(1f, sheetTokens.detentSpring.spec)
        }

        LiquidPredictiveBackHandler(enabled = !closing) { events ->
            try {
                events.collect { event ->
                    val p = event.progress.coerceIn(0f, 1f)
                    revealAnim.snapTo((1f - p * .68f).coerceAtLeast(.04f))
                }
                finishDismiss()
            } catch (cancelled: CancellationException) {
                scope.launch { revealAnim.animateTo(1f, sheetTokens.detentSpring.spec) }
                throw cancelled
            }
        }

        BoxWithConstraints(modifier.fillMaxSize()) {
            val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val maxSheetWidth = minOf(maxWidth, 620.dp)
            val corner = sheetTokens.corner
            val shape = RoundedCornerShape(topStart = corner, topEnd = corner)

            if (hasInWindowHost || scrimAnim.value > 0f) {
                Spacer(
                    Modifier.matchParentSize()
                        .background(Color.Black.copy(alpha = scrimAnim.value.coerceIn(0f, 1f)))
                        .clickable(interactionSource = null, indication = null) { finishDismiss() },
                )
            }

            val currentHeightFraction = fractionAnim.value.coerceIn(0f, 1f)
            val sheetHeightPx = (containerHeightPx * currentHeightFraction).coerceAtLeast(1f)
            val sheetHeightDp = with(density) { sheetHeightPx.toDp() }
            val velocityTracker = remember { VelocityTracker() }
            val dismissVelocityPx = with(density) { sheetTokens.dismissVelocity.toPx() }
            val bodyScroll = rememberScrollState()
            val maxFrac = ordered.last().fraction
            var sheetDragActive by remember { mutableStateOf(false) }

            fun applySheetDragDelta(deltaY: Float) {
                val next = (fractionAnim.value - deltaY / containerHeightPx).coerceIn(0f, maxFrac + .06f)
                scope.launch { fractionAnim.snapTo(next) }
            }

            fun settleSheetDrag(velocityY: Float) {
                sheetDragActive = false
                val dragDeltaFraction = activeDetent.fraction - fractionAnim.value
                val next = resolveSheetDetent(
                    currentFraction = activeDetent.fraction,
                    dragDeltaFraction = dragDeltaFraction,
                    velocityPxPerSec = velocityY,
                    dismissVelocityPxPerSec = dismissVelocityPx,
                    detents = ordered,
                    dismissThreshold = sheetTokens.dismissFraction,
                )
                if (next == null) {
                    finishDismiss()
                } else {
                    activeDetent = next
                    scope.launch { fractionAnim.animateTo(next.fraction, sheetTokens.detentSpring.spec) }
                }
            }

            val sheetNestedScroll = remember(ordered, containerHeightPx, dismissVelocityPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (closing || source != NestedScrollSource.UserInput) return Offset.Zero
                        val dy = available.y
                        if (dy == 0f) return Offset.Zero
                        if (dy < 0f && fractionAnim.value < maxFrac - .002f) {
                            sheetDragActive = true
                            val roomPx = (maxFrac - fractionAnim.value).coerceAtLeast(0f) * containerHeightPx
                            val consumedY = maxOf(dy, -roomPx)
                            applySheetDragDelta(consumedY)
                            if (fractionAnim.value >= maxFrac - .002f && consumedY > dy) {
                                sheetDragActive = false
                            }
                            return Offset(0f, consumedY)
                        }
                        if (sheetDragActive && bodyScroll.value == 0) {
                            applySheetDragDelta(dy)
                            return Offset(0f, dy)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (closing || source != NestedScrollSource.UserInput) return Offset.Zero
                        val dy = available.y
                        if (dy > 0f && bodyScroll.value == 0) {
                            sheetDragActive = true
                            applySheetDragDelta(dy)
                            return Offset(0f, dy)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (closing) return Velocity.Zero
                        if (sheetDragActive || abs(fractionAnim.value - activeDetent.fraction) > .005f) {
                            settleSheetDrag(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }

            val sheetBgBackdrop = rememberLayerBackdrop()
            val sheetBodyBackdrop = rememberLayerBackdrop()
            var headerHeightPx by remember { mutableIntStateOf(0) }

            Box(
                Modifier.align(Alignment.BottomCenter)
                    .width(maxSheetWidth)
                    .height(sheetHeightDp)
                    .graphicsLayer {
                        translationY = (1f - revealAnim.value) * (sheetHeightPx + 24.dp.toPx())
                    }
                    .semantics { if (!title.isNullOrEmpty()) paneTitle = title }
                    .nestedScroll(sheetNestedScroll)
                    .pointerInput(Unit) {
                        // Absorb taps on sheet body so scrim behind does not dismiss.
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                            }
                        }
                    }
                    .liquidGlassShadow(shape, 1f, radius = corner),
            ) {
                // Record sheet surface + scrollable body separately so controls inside/above the sheet
                // refract the sheet's own surface & body rather than X-raying to the dimmed parent page.
                Box(Modifier.matchParentSize().clip(shape).layerBackdrop(sheetBodyBackdrop)) {
                    Spacer(
                        Modifier.matchParentSize()
                            .layerBackdrop(sheetBgBackdrop)
                            .liquidModalSurface(
                                shape,
                                backdrop,
                                colors,
                                elevation = 0f,
                                cornerRadius = corner,
                                style = ModalSurfaceStyle.SheetSurface,
                            ),
                    )
                    CompositionLocalProvider(LocalLiquidSurfaceBackdrop provides sheetBgBackdrop) {
                        val topPad = with(density) { headerHeightPx.toDp() }
                        Column(
                            Modifier.fillMaxSize().verticalScroll(bodyScroll)
                                .padding(top = topPad + 4.dp, start = 18.dp, end = 18.dp, bottom = 12.dp)
                                .navigationBarsPadding().imePadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            content = content,
                        )
                    }
                }
                Column(
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { headerHeightPx = it.height }
                        .pointerInput(ordered, containerHeightPx, dismissVelocityPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    applySheetDragDelta(dragAmount)
                                },
                                onDragCancel = {
                                    scope.launch { fractionAnim.animateTo(activeDetent.fraction, sheetTokens.detentSpring.spec) }
                                },
                                onDragEnd = {
                                    settleSheetDrag(velocityTracker.calculateVelocity().y)
                                },
                            )
                        }
                        .padding(top = 10.dp, bottom = 8.dp, start = 18.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(
                        Modifier.size(sheetTokens.grabberWidth, sheetTokens.grabberHeight)
                            .clip(LiquidCapsule)
                            .background(colors.secondary.copy(alpha = .42f)),
                    )
                    if (!title.isNullOrEmpty() || doneLabel != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!title.isNullOrEmpty()) {
                                BasicText(
                                    title,
                                    Modifier.weight(1f).semantics { heading() },
                                    style = TextStyle(colors.ink, 22.sp, FontWeight.Bold),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (doneLabel != null) {
                                LiquidActionButton(
                                    doneLabel,
                                    ::finishDismiss,
                                    colors.ink,
                                    style = if (backdrop != null) LiquidActionStyle.Glass else LiquidActionStyle.Tinted,
                                    backdrop = sheetBodyBackdrop,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** SwiftUI `.confirmationDialog` bottom-anchored Liquid Glass action sheet with detached Cancel capsule. */
@Composable
fun LiquidActionSheet(
    actions: List<LiquidAlertAction>,
    onDismissRequest: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    cancelLabel: String = "取消",
    backdrop: Backdrop? = null,
    colors: LiquidListColors = liquidListColors(),
) {
    require(actions.isNotEmpty())
    val sheetTokens = LocalLiquidGlassTokens.current.sheet
    val alertTokens = LocalLiquidGlassTokens.current.alert
    val hasInWindowHost = LocalLiquidModalRegistry.current != null
    val appearance = liquidGlassAppearance()

    LiquidModalLayer(scrimAlpha = if (hasInWindowHost) 0f else sheetTokens.scrimAlpha, onDismissRequest = onDismissRequest) { dismiss ->
        val slideAnim = remember { Animatable(0f) }
        val scrimAnim = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }

        fun finishDismiss(after: (() -> Unit)? = null) {
            if (closing) return
            closing = true
            scope.launch {
                kotlinx.coroutines.coroutineScope {
                    launch { scrimAnim.animateTo(0f, tween(190)) }
                    launch { slideAnim.animateTo(0f, tween(210, easing = FastOutSlowInEasing)) }
                }
                if (after != null) after() else dismiss()
            }
        }

        LaunchedEffect(Unit) {
            launch { scrimAnim.animateTo(if (hasInWindowHost) sheetTokens.scrimAlpha else 0f, tween(210)) }
            slideAnim.animateTo(1f, sheetTokens.detentSpring.spec)
        }

        LiquidPredictiveBackHandler(enabled = !closing) { events ->
            try {
                events.collect { event ->
                    val p = event.progress.coerceIn(0f, 1f)
                    slideAnim.snapTo((1f - p * .55f).coerceIn(0f, 1f))
                    scrimAnim.snapTo((if (hasInWindowHost) sheetTokens.scrimAlpha else 0f) * (1f - p * .55f))
                }
                finishDismiss()
            } catch (cancelled: CancellationException) {
                scope.launch {
                    launch { scrimAnim.animateTo(if (hasInWindowHost) sheetTokens.scrimAlpha else 0f, tween(180)) }
                    launch { slideAnim.animateTo(1f, sheetTokens.detentSpring.spec) }
                }
                throw cancelled
            }
        }

        BoxWithConstraints(modifier.fillMaxSize()) {
            if (hasInWindowHost) {
                Spacer(
                    Modifier.matchParentSize()
                        .background(Color.Black.copy(alpha = scrimAnim.value.coerceIn(0f, 1f)))
                        .clickable(interactionSource = null, indication = null) { finishDismiss() },
                )
            }
            val density = LocalDensity.current
            val pressTokens = LocalLiquidGlassTokens.current.press
            val bottomSafePx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }
            var sheetHeightPx by remember { mutableFloatStateOf(0f) }
            val shape = RoundedCornerShape(alertTokens.corner)
            val totalButtons = actions.size + 1
            val itemBounds = remember(totalButtons) { MutableList<Rect?>(totalButtons) { null } }
            val enabledFlags = remember(actions) { actions.map { it.enabled } + true }
            var activeIndex by remember(actions) { mutableStateOf<Int?>(null) }
            var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val latestActions by rememberUpdatedState(actions)
            val latestFinishDismiss by rememberUpdatedState(::finishDismiss)

            Column(
                Modifier.align(Alignment.BottomCenter).widthIn(max = 400.dp).fillMaxWidth()
                    .navigationBarsPadding().imePadding().padding(horizontal = 14.dp, vertical = 14.dp)
                    .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                    .onGloballyPositioned { containerCoords = it }
                    .pointerInput(actions.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val coords = containerCoords ?: return@awaitEachGesture
                            if (!coords.isAttached) return@awaitEachGesture
                            val startRoot = coords.localToRoot(down.position)
                            val startHit = resolveTrackedActionRelease(startRoot, itemBounds, enabledFlags)
                            if (startHit != null) {
                                activeIndex = startHit
                                down.consume()
                            }
                            var lastRoot = startRoot
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (coords.isAttached) lastRoot = coords.localToRoot(change.position)
                                    val hit = resolveTrackedActionRelease(lastRoot, itemBounds, enabledFlags)
                                    if (change.pressed) {
                                        if (startHit != null || hit != null) {
                                            activeIndex = hit
                                            change.consume()
                                        }
                                    } else {
                                        activeIndex = null
                                        if (hit != null) {
                                            change.consume()
                                            if (hit < latestActions.size) {
                                                val chosenAction = latestActions[hit].onClick
                                                latestFinishDismiss(chosenAction)
                                            } else {
                                                latestFinishDismiss(null)
                                            }
                                        }
                                        break
                                    }
                                }
                            } finally {
                                activeIndex = null
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = slideAnim.value
                        val distance = (if (sheetHeightPx > 0f) sheetHeightPx else 320.dp.toPx()) + bottomSafePx + 28.dp.toPx()
                        alpha = (p * 5f).coerceIn(0f, 1f)
                        translationY = (1f - p) * distance
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val sheetCardBackdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxWidth()) {
                    Spacer(
                        Modifier.matchParentSize()
                            .layerBackdrop(sheetCardBackdrop)
                            .liquidModalSurface(
                                shape,
                                backdrop,
                                colors,
                                elevation = 1f,
                                cornerRadius = alertTokens.corner,
                                scrimTint = sheetTokens.scrimAlpha * .7f,
                            ),
                    )
                    CompositionLocalProvider(LocalLiquidSurfaceBackdrop provides sheetCardBackdrop) {
                        Column(
                            Modifier.fillMaxWidth()
                                .semantics { if (!title.isNullOrEmpty()) paneTitle = title }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (!title.isNullOrEmpty() || !message.isNullOrEmpty()) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (!title.isNullOrEmpty()) {
                                        BasicText(title, style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold, textAlign = TextAlign.Center))
                                    }
                                    if (!message.isNullOrEmpty()) {
                                        BasicText(message, style = TextStyle(colors.secondary, 13.sp, textAlign = TextAlign.Center))
                                    }
                                }
                            }
                            actions.forEachIndexed { index, item ->
                                val isPressed = activeIndex == index
                                val pressProgress by animateFloatAsState(
                                    if (isPressed) 1f else 0f,
                                    if (isPressed) pressTokens.engage.spec else pressTokens.release.spec,
                                    label = "ActionSheet row press",
                                )
                                val pillFill = if (appearance.dark) Color(0xFF47484D) else Color(0xFFDADBE0)
                                val ink = when {
                                    !item.enabled -> colors.secondary.copy(alpha = .5f)
                                    item.role == LiquidAlertRole.Destructive -> appearance.destructiveColor
                                    else -> colors.ink
                                }
                                Box(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .onGloballyPositioned { if (it.isAttached) itemBounds[index] = it.boundsInRoot() }
                                        .liquidModalActionPill(pillFill, pressProgress, appearance.dark)
                                        .semantics {
                                            role = Role.Button
                                            if (!item.enabled) disabled()
                                            onClick {
                                                if (item.enabled) {
                                                    finishDismiss(item.onClick)
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText(
                                        item.label,
                                        style = TextStyle(ink, 17.sp, if (item.role == LiquidAlertRole.Primary) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center),
                                    )
                                }
                            }
                        }
                    }
                }

                val cancelPressed = activeIndex == actions.size
                val cancelProgress by animateFloatAsState(
                    if (cancelPressed) 1f else 0f,
                    if (cancelPressed) pressTokens.engage.spec else pressTokens.release.spec,
                    label = "ActionSheet cancel press",
                )
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .onGloballyPositioned { if (it.isAttached) itemBounds[actions.size] = it.boundsInRoot() }
                        .graphicsLayer {
                            val s = 1f + .02f * cancelProgress
                            scaleX = s
                            scaleY = s
                        }
                        .liquidModalSurface(
                            LiquidCapsule,
                            backdrop,
                            colors,
                            elevation = 1f,
                            cornerRadius = 28.dp,
                            scrimTint = sheetTokens.scrimAlpha * .7f,
                        )
                        .background(Color.White.copy(alpha = cancelProgress * (if (appearance.dark) .14f else .24f)), LiquidCapsule)
                        .semantics {
                            role = Role.Button
                            onClick { finishDismiss(); true }
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(cancelLabel, style = TextStyle(tint, 17.sp, FontWeight.SemiBold, textAlign = TextAlign.Center))
                }
            }
        }
    }
}

/** Records global coordinates of an anchor trigger for positioning a [LiquidPopover]. */
@Composable
fun Modifier.liquidPopoverAnchor(bounds: MutableState<Rect?>): Modifier =
    onGloballyPositioned { coords: LayoutCoordinates ->
        if (coords.isAttached) bounds.value = coords.boundsInRoot()
    }

/** Floating Liquid Glass popover anchored cleanly near [anchorBoundsInRoot], clamped within screen bounds. */
@Composable
fun LiquidPopover(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorBoundsInRoot: Rect? = null,
    backdrop: Backdrop? = null,
    colors: LiquidListColors = liquidListColors(),
    maxWidth: Dp = 360.dp,
    blurRadius: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetTokens = LocalLiquidGlassTokens.current.sheet
    val alertTokens = LocalLiquidGlassTokens.current.alert
    val hasInWindowHost = LocalLiquidModalRegistry.current != null

    LiquidModalLayer(scrimAlpha = if (hasInWindowHost) 0f else .08f, onDismissRequest = onDismissRequest) { dismiss ->
        val progress = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }

        fun finishDismiss() {
            if (closing) return
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(alertTokens.exitMillis))
                dismiss()
            }
        }

        LaunchedEffect(Unit) { progress.animateTo(1f, sheetTokens.popoverSpring.spec) }

        LiquidPredictiveBackHandler(enabled = !closing) { events ->
            try {
                events.collect { event ->
                    progress.snapTo((1f - event.progress.coerceIn(0f, 1f) * .45f).coerceIn(0f, 1f))
                }
                finishDismiss()
            } catch (cancelled: CancellationException) {
                scope.launch { progress.animateTo(1f, sheetTokens.popoverSpring.spec) }
                throw cancelled
            }
        }

        var hostOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var cardWidthPx by remember { mutableIntStateOf(0) }
        var cardHeightPx by remember { mutableIntStateOf(0) }
        BoxWithConstraints(
            modifier.fillMaxSize()
                .onGloballyPositioned { if (it.isAttached) hostOrigin = it.positionInRoot() }
                .clickable(interactionSource = null, indication = null) { finishDismiss() },
        ) {
            val density = LocalDensity.current
            val padPx = with(density) { 12.dp.roundToPx() }
            val gapPx = with(density) { 8.dp.roundToPx() }
            val maxW = constraints.maxWidth
            val maxH = constraints.maxHeight
            val shape = RoundedCornerShape(sheetTokens.popoverCorner)

            var lockedOpenUp by remember { mutableStateOf<Boolean?>(null) }
            val localAnchor = anchorBoundsInRoot?.translate(-hostOrigin)
            val openUp = lockedOpenUp ?: if (localAnchor != null && cardHeightPx > 0) {
                val computed = (maxH - localAnchor.bottom < cardHeightPx + padPx + gapPx) && (localAnchor.top > maxH - localAnchor.bottom)
                SideEffect { lockedOpenUp = computed }
                computed
            } else {
                false
            }
            val posX = if (localAnchor != null && cardWidthPx > 0) {
                (localAnchor.center.x - cardWidthPx / 2f).roundToInt()
                    .coerceIn(padPx, (maxW - cardWidthPx - padPx).coerceAtLeast(padPx))
            } else {
                ((maxW - cardWidthPx) / 2).coerceAtLeast(padPx)
            }
            val posY = if (localAnchor != null && cardHeightPx > 0) {
                val rawY = if (openUp) (localAnchor.top - cardHeightPx - gapPx).roundToInt()
                else (localAnchor.bottom + gapPx).roundToInt()
                rawY.coerceIn(padPx, (maxH - cardHeightPx - padPx).coerceAtLeast(padPx))
            } else {
                ((maxH - cardHeightPx) / 2).coerceAtLeast(padPx)
            }

            val popoverBgBackdrop = rememberLayerBackdrop()
            Box(
                Modifier.align(AbsoluteAlignment.TopLeft)
                    .offset { IntOffset(posX, posY) }
                    .onGloballyPositioned {
                        cardWidthPx = it.size.width
                        cardHeightPx = it.size.height
                    }
                    .widthIn(min = 180.dp, max = minOf(this@BoxWithConstraints.maxWidth - 24.dp, maxWidth))
                    .graphicsLayer {
                        val p = progress.value
                        val scale = if (closing) .88f + .12f * p else .78f + .22f * p
                        scaleX = scale
                        scaleY = scale
                        alpha = if (closing) p.coerceIn(0f, 1f) else (p * 2.1f).coerceIn(0f, 1f)
                        translationY = (1f - p) * (if (openUp) 8.dp.toPx() else -8.dp.toPx())
                        transformOrigin = TransformOrigin(.5f, if (openUp) 1f else 0f)
                    }
                    .clickable(interactionSource = null, indication = null) { /* absorb clicks on popover card */ },
            ) {
                Spacer(
                    Modifier.matchParentSize()
                        .layerBackdrop(popoverBgBackdrop)
                        .liquidModalSurface(
                            shape,
                            backdrop,
                            colors,
                            elevation = 1f,
                            cornerRadius = sheetTokens.popoverCorner,
                            blurRadius = blurRadius,
                        ),
                )
                CompositionLocalProvider(LocalLiquidSurfaceBackdrop provides popoverBgBackdrop) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
internal expect fun ConfigureAlertWindow(scrimAlpha: Float)

@Composable
expect fun SyncLiquidDialogSystemBars(dark: Boolean)
