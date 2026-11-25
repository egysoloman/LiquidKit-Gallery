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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class LiquidAlertRole { Default, Primary, Cancel, Destructive }

data class LiquidAlertAction(val label: String, val role: LiquidAlertRole = LiquidAlertRole.Default,
    val enabled: Boolean = true, val onClick: () -> Unit)

internal fun resolveTrackedActionRelease(
    point: Offset,
    bounds: List<Rect?>,
    enabled: List<Boolean>,
): Int? {
    for (i in bounds.indices) {
        val rect = bounds[i] ?: continue
        val isEnabled = enabled.getOrElse(i) { false }
        if (isEnabled && rect.contains(point)) return i
    }
    return null
}

private fun GlassAlertTokens.controlFill(colors: LiquidListColors, dark: Boolean): Color =
    lerp(colors.surface, colors.ink, if (dark) darkActionMix else lightActionMix)

@Composable
internal fun Modifier.liquidModalActionPill(
    fill: Color,
    pressed: Float,
    dark: Boolean,
): Modifier {
    val pressTarget = if (dark) Color.White else Color.Black
    val effectiveFill = lerp(fill, pressTarget, pressed * if (dark) .14f else .10f)
    return graphicsLayer {
        val s = 1f + .018f * pressed
        scaleX = s
        scaleY = s
    }
        .clip(LiquidCapsule)
        .background(effectiveFill)
}

/** A modal decision rendered in Liquid Glass when [backdrop] is provided. Back or scrim dismisses cleanly. */
@Composable
fun LiquidAlert(
    title: String,
    actions: List<LiquidAlertAction>,
    onDismissRequest: () -> Unit,
    tint: Color,
    message: String? = null,
    colors: LiquidListColors = liquidListColors(),
    backdrop: Backdrop? = null,
    field: (@Composable () -> Unit)? = null,
) {
    require(actions.isNotEmpty())
    val appearance = liquidGlassAppearance()
    val tokens = LocalLiquidGlassTokens.current.alert
    val neutral = tokens.controlFill(colors, appearance.dark)
    val line = colors.ink.copy(alpha = if (appearance.dark) .14f else .10f)
    val shape = RoundedCornerShape(tokens.corner)
    val hasInWindowHost = LocalLiquidModalRegistry.current != null

    LiquidModalLayer(scrimAlpha = if (hasInWindowHost) 0f else tokens.scrimAlpha, onDismissRequest = onDismissRequest) { dismiss ->
        val scaleAnim = remember { Animatable(1.16f) }
        val alphaAnim = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }

        fun dismissAnimated(after: (() -> Unit)? = null) {
            if (closing) return
            closing = true
            scope.launch {
                kotlinx.coroutines.coroutineScope {
                    launch { scaleAnim.animateTo(0.93f, tween(tokens.exitMillis, easing = FastOutSlowInEasing)) }
                    launch { alphaAnim.animateTo(0f, tween(tokens.exitMillis)) }
                }
                if (after != null) after() else dismiss()
            }
        }

        fun runAction(onClick: () -> Unit) {
            dismissAnimated(after = onClick)
        }

        LaunchedEffect(Unit) {
            launch { scaleAnim.animateTo(1f, tokens.enterSpring.spec) }
            launch { alphaAnim.animateTo(1f, tween(135)) }
        }

        LiquidPredictiveBackHandler(enabled = !closing) { events ->
            try {
                events.collect { event ->
                    val t = event.progress.coerceIn(0f, 1f)
                    scaleAnim.snapTo(1f - .07f * t)
                    alphaAnim.snapTo(1f - .45f * t)
                }
                dismissAnimated()
            } catch (cancelled: CancellationException) {
                scope.launch {
                    launch { scaleAnim.animateTo(1f, tokens.enterSpring.spec) }
                    launch { alphaAnim.animateTo(1f, tween(140)) }
                }
                throw cancelled
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (hasInWindowHost) {
                Spacer(
                    Modifier.matchParentSize()
                        .drawBehind {
                            drawRect(Color.Black.copy(alpha = tokens.scrimAlpha * alphaAnim.value.coerceIn(0f, 1f)))
                        }
                        .clickable(interactionSource = null, indication = null) { dismissAnimated() },
                )
            }
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
                val alertBgBackdrop = rememberLayerBackdrop()
                Box(
                    Modifier.widthIn(max = tokens.width).fillMaxWidth()
                        .graphicsLayer {
                            val s = scaleAnim.value
                            scaleX = s
                            scaleY = s
                            alpha = alphaAnim.value.coerceIn(0f, 1f)
                        },
                ) {
                    Spacer(
                        Modifier.matchParentSize()
                            .layerBackdrop(alertBgBackdrop)
                            .liquidModalSurface(
                                shape,
                                backdrop,
                                colors,
                                elevation = 1f,
                                cornerRadius = tokens.corner,
                                scrimTint = tokens.scrimAlpha,
                            )
                            .then(if (backdrop == null) Modifier.border(.5.dp, line, shape) else Modifier),
                    )
                    CompositionLocalProvider(LocalLiquidSurfaceBackdrop provides alertBgBackdrop) {
                        Column(
                            Modifier.fillMaxWidth()
                                .semantics { paneTitle = title }
                                .clickable(interactionSource = null, indication = null) { /* absorb clicks */ }
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                    val alignment = if (message.isNullOrEmpty()) TextAlign.Center else TextAlign.Start
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                        BasicText(
                            title,
                            Modifier.fillMaxWidth().semantics { heading() },
                            style = TextStyle(colors.ink, 18.sp, FontWeight.SemiBold, textAlign = alignment),
                        )
                        if (!message.isNullOrEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            BasicText(message, style = TextStyle(colors.secondary, 15.sp, textAlign = alignment))
                        }
                    }
                    if (field != null) { Spacer(Modifier.height(14.dp)); field() }
                    Spacer(Modifier.height(18.dp))
                    val style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    val measure = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val pressTokens = LocalLiquidGlassTokens.current.press
                    val buttonBounds = remember(actions.size) { MutableList<Rect?>(actions.size) { null } }
                    var activeIndex by remember(actions) { mutableStateOf<Int?>(null) }
                    val latestActions by rememberUpdatedState(actions)
                    val latestRunAction by rememberUpdatedState(::runAction)

                    @Composable fun action(index: Int, item: LiquidAlertAction, modifier: Modifier) {
                        val isPressed = activeIndex == index
                        val pressProgress by animateFloatAsState(
                            if (isPressed) 1f else 0f,
                            if (isPressed) pressTokens.engage.spec else pressTokens.release.spec,
                            label = "Alert action press",
                        )
                        val primary = item.role == LiquidAlertRole.Primary
                        val pillFill = when {
                            primary && item.enabled -> tint
                            appearance.dark -> if (primary) Color(0xFF3E3F44) else Color(0xFF47484D)
                            else -> Color(0xFFDADBE0)
                        }
                        val ink = when {
                            !item.enabled -> colors.secondary.copy(alpha = .64f)
                            primary -> liquidContentColorFor(tint)
                            item.role == LiquidAlertRole.Destructive -> appearance.destructiveColor
                            else -> colors.ink
                        }
                        Box(
                            modifier.heightIn(min = tokens.actionHeight)
                                .onGloballyPositioned { coords ->
                                    if (coords.isAttached) buttonBounds[index] = coords.boundsInRoot()
                                }
                                .liquidModalActionPill(pillFill, pressProgress, appearance.dark)
                                .semantics {
                                    role = Role.Button
                                    if (!item.enabled) disabled()
                                    onClick {
                                        if (item.enabled) {
                                            latestRunAction(item.onClick)
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(item.label, style = style.copy(color = ink))
                        }
                    }

                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        var groupCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
                        Box(
                            Modifier.fillMaxWidth()
                                .onGloballyPositioned { groupCoords = it }
                                .pointerInput(actions.size) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val coords = groupCoords ?: return@awaitEachGesture
                                        if (!coords.isAttached) return@awaitEachGesture
                                        val startRoot = coords.localToRoot(down.position)
                                        val enabledFlags = latestActions.map { it.enabled }
                                        val startHit = resolveTrackedActionRelease(startRoot, buttonBounds, enabledFlags)
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
                                                val hit = resolveTrackedActionRelease(lastRoot, buttonBounds, enabledFlags)
                                                if (change.pressed) {
                                                    if (startHit != null || hit != null) {
                                                        activeIndex = hit
                                                        change.consume()
                                                    }
                                                } else {
                                                    val commitIndex = hit
                                                    activeIndex = null
                                                    if (commitIndex != null) {
                                                        change.consume()
                                                        latestRunAction(latestActions[commitIndex].onClick)
                                                    }
                                                    break
                                                }
                                            }
                                        } finally {
                                            activeIndex = null
                                        }
                                    }
                                },
                        ) {
                            val half = (this@BoxWithConstraints.maxWidth - tokens.actionGap) / 2 - 24.dp
                            val pairFits = actions.size == 2 && actions.all {
                                measure.measure(it.label, style = style, softWrap = false).size.width <= with(density) { half.toPx() }
                            }
                            if (pairFits) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.actionGap)) {
                                actions.forEachIndexed { idx, item -> action(idx, item, Modifier.weight(1f)) }
                            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(tokens.actionGap)) {
                                actions.forEachIndexed { idx, item -> action(idx, item, Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidAlertTextField(value: String, onValueChange: (String) -> Unit, label: String, tint: Color,
    onConfirm: () -> Unit, colors: LiquidListColors = liquidListColors()) {
    val focus = remember { FocusRequester() }
    val tokens = LocalLiquidGlassTokens.current.alert
    val fill = if (liquidGlassAppearance().dark) Color(0xFF3B3C41) else Color(0xFFD4D5DB)
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = label },
        textStyle = TextStyle(colors.ink, 16.sp), cursorBrush = SolidColor(tint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onConfirm() }),
        decorationBox = { editor ->
            Box(Modifier.fillMaxWidth().heightIn(min = tokens.actionHeight).background(fill, LiquidCapsule)
                .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) BasicText(label, style = TextStyle(colors.secondary, 16.sp))
                editor()
            }
        })
    LaunchedEffect(focus) { focus.requestFocus() }
}
