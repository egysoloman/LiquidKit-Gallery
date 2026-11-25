package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

private class BackDrag<T>(val from: T, val parent: T) {
    var fraction by mutableFloatStateOf(0f)
    var commit by mutableStateOf<Boolean?>(null)
}

internal fun GlassNavigationTokens.commitsBackSwipe(
    fraction: Float, distance: Float, endDelta: Float, durationMillis: Long,
    releaseSpeed: Float, flingSpeed: Float, slop: Float,
): Boolean {
    if (releaseSpeed < -flingSpeed) return false
    if (fraction >= commitFraction) return true
    if (distance <= 2f * slop) return false
    if (releaseSpeed > flingSpeed) return true
    // A batched UP can carry the last part of a fast swipe well after the final MOVE.
    return endDelta >= slop * .5f && releaseSpeed >= 0f &&
        durationMillis in 1L..quickSwipeWindowMillis.toLong() &&
        distance * 1000f > flingSpeed * durationMillis
}

/** Dragging unclaimed space or performing an Android predictive-back swipe reveals [previousPage]. */
@Composable
fun <T : Any> LiquidSwipeBackNavigation(
    page: T, previousPage: T?, depth: (T) -> Int, contentKey: (T) -> Any,
    onNavigateBack: (T) -> Unit, modifier: Modifier = Modifier,
    predictiveBackEnabled: Boolean = true,
    onVisualTargetChange: ((T) -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    val key = contentKey(page)
    val parentKey = previousPage?.let(contentKey)
    val state = remember { SeekableTransitionState(page) }
    val navTransition = rememberTransition(state, label = "Navigation")
    var drag by remember { mutableStateOf<BackDrag<T>?>(null) }
    val latestPage by rememberUpdatedState(page)
    val latestParent by rememberUpdatedState(previousPage)
    val navigate by rememberUpdatedState(onNavigateBack)
    val reportVisualTarget by rememberUpdatedState(onVisualTargetChange)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val motion = LocalLiquidGlassTokens.current.navigation
    val duration = motion.durationMillis
    LaunchedEffect(reportVisualTarget, state, motion) {
        val cb = reportVisualTarget ?: return@LaunchedEffect
        snapshotFlow {
            val activeDrag = drag
            when {
                activeDrag != null && activeDrag.commit == true -> activeDrag.parent
                activeDrag != null && activeDrag.commit == false -> activeDrag.from
                activeDrag != null -> if (activeDrag.fraction >= motion.commitFraction) activeDrag.parent else activeDrag.from
                else -> latestPage
            }
        }.distinctUntilChanged().collect { cb(it) }
    }
    LiquidPredictiveBackHandler(enabled = predictiveBackEnabled && previousPage != null) { events ->
        val parent = latestParent ?: return@LiquidPredictiveBackHandler
        val session = BackDrag(latestPage, parent)
        drag = session
        try {
            events.collect { event ->
                session.fraction = event.progress.coerceIn(0f, 1f)
            }
            session.commit = true
        } catch (cancelled: CancellationException) {
            session.commit = false
            throw cancelled
        } finally {
            if (session.commit == null) session.commit = false
        }
    }
    LaunchedEffect(key, drag) {
        val gesture = drag
        if (gesture == null || contentKey(gesture.from) != key) {
            if (gesture != null) drag = null
            if (contentKey(state.currentState) != key || contentKey(state.targetState) != key)
                state.animateTo(latestPage, tween(duration, easing = motion.pageEasing))
            return@LaunchedEffect
        }
        val (_, commit) = snapshotFlow { gesture.fraction to gesture.commit }.first { (fraction, released) ->
            state.seekTo(fraction, gesture.parent)
            released != null
        }
        if (commit == true && latestParent?.let(contentKey) == contentKey(gesture.parent)) {
            val remaining = ((1f - state.fraction) * duration).roundToInt().coerceAtLeast(1)
            state.animateTo(gesture.parent, tween(remaining, easing = motion.pageEasing))
            navigate(gesture.parent)
        } else {
            val start = state.fraction
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            val nanos = start * duration * scale * 1_000_000f
            if (nanos > 0f) {
                val first = withFrameNanos { it }
                do {
                    val elapsed = withFrameNanos { it - first }
                    val time = (elapsed / nanos).coerceIn(0f, 1f)
                    state.seekTo(start * (1f - motion.pageEasing.transform(time)), gesture.parent)
                } while (time < 1f)
            }
            state.snapTo(latestPage)
        }
        drag = null
    }
    Box(modifier.clipToBounds().pointerInput(key, parentKey, direction, motion) {
        if (parentKey == null) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val parent = latestParent ?: return@awaitEachGesture
            if (down.isConsumed || drag != null || navTransition.isRunning) return@awaitEachGesture
            val velocity = DragReleaseVelocity(down.uptimeMillis, down.position)
            val slop = viewConfiguration.touchSlop
            var session: BackDrag<T>? = null
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val touch = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (touch.isConsumed || event.changes.count { it.pressed } > 1) break
                    for (sample in touch.historical) velocity.move(sample.uptimeMillis, sample.position)
                    velocity.move(touch.uptimeMillis, touch.position)
                    val delta = touch.position - down.position
                    val dx = direction * delta.x
                    if (session == null) {
                        if (dx < -slop || abs(delta.y) > maxOf(slop, dx)) break
                        if (dx <= slop || dx < 1.25f * abs(delta.y)) {
                            if (!touch.pressed) break
                            continue
                        }
                        session = BackDrag(latestPage, parent)
                        drag = session
                    }
                    session.fraction = ((dx - slop) / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    touch.consume()
                    if (!touch.pressed) {
                        val speed = velocity.atRelease(touch.uptimeMillis).x * direction
                        session.commit = motion.commitsBackSwipe(
                            fraction = session.fraction, distance = dx,
                            endDelta = direction * (touch.position.x - touch.previousPosition.x),
                            durationMillis = touch.uptimeMillis - down.uptimeMillis, releaseSpeed = speed,
                            flingSpeed = motion.flingDistancePerSecond.toPx(), slop = slop,
                        )
                        break
                    }
                }
            } finally {
                session?.let { if (it.commit == null) it.commit = false }
            }
        }
    }) {
        val dark = liquidGlassAppearance().dark
        navTransition.AnimatedContent(Modifier.fillMaxSize(), contentKey = contentKey, transitionSpec = {
            // A linear timeline maps drag fraction directly to pixels. Programmatic navigation eases the clock.
            val slide = tween<IntOffset>(duration, easing = LinearEasing)
            val fade = tween<Float>(duration, easing = LinearEasing)
            val movement = when (depth(targetState).compareTo(depth(initialState))) {
                1 -> slideInHorizontally(slide) { it * direction } togetherWith
                    slideOutHorizontally(slide) { -it * direction / 3 }
                -1 -> slideInHorizontally(slide) { -it * direction / 3 } togetherWith
                    slideOutHorizontally(slide) { it * direction }
                else -> fadeIn(fade) togetherWith fadeOut(fade)
            }
            movement.apply { targetContentZIndex = depth(targetState).toFloat() }
        }) { displayed ->
            val displayedKey = contentKey(displayed)
            val displayedDepth = depth(displayed)
            val isTransitioning = navTransition.isRunning || drag != null
            val isTopLayer = displayedDepth >= depth(navTransition.currentState) && displayedDepth >= depth(navTransition.targetState)
            val coverFraction by navTransition.animateFloat(
                transitionSpec = { tween(duration, easing = LinearEasing) },
                label = "Navigation cover dim",
            ) { state ->
                if (depth(state) > displayedDepth) 1f else 0f
            }
            val maxDim = if (dark) .14f else .06f
            // Keep the modifier chain structurally constant across rest/transition to prevent RenderNode teardown flashes.
            val layerDecor = Modifier
                .zIndex(displayedDepth.toFloat())
                .drawWithCache {
                    val shadowAlpha = if (dark) .10f else .045f
                    val edgeWidth = 22.dp.toPx()
                    val leftEdge = direction > 0
                    val x = if (leftEdge) -edgeWidth else size.width
                    val edgeColors = if (leftEdge) listOf(Color.Transparent, Color.Black.copy(alpha = shadowAlpha))
                    else listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent)
                    val edgeBrush = Brush.horizontalGradient(edgeColors, startX = x, endX = x + edgeWidth)
                    val topLeft = Offset(x, 0f)
                    val rectSize = Size(edgeWidth, size.height)
                    onDrawWithContent {
                        drawContent()
                        if (isTransitioning && isTopLayer) {
                            drawRect(brush = edgeBrush, topLeft = topLeft, size = rectSize)
                        }
                        if (coverFraction > .001f) {
                            drawRect(Color.Black.copy(alpha = (maxDim * coverFraction).coerceIn(0f, 1f)))
                        }
                    }
                }
                .graphicsLayer {
                    clip = false
                }
            Box(Modifier.fillMaxSize().then(layerDecor)) {
                content(when (displayedKey) {
                    key -> page
                    parentKey -> previousPage
                    else -> displayed
                })
            }
        }
    }
}
