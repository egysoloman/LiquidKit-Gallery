package com.kyant.backdrop.components

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Transition style for switching views, mirroring SwiftUI / iOS 27 view transitions. */
enum class LiquidViewTransitionStyle { CrossFade, Zoom, Slide }

/** GPU-lightweight view switcher without full-screen blur FBO allocation. */
@Composable
fun <T> LiquidViewTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    style: LiquidViewTransitionStyle = LiquidViewTransitionStyle.CrossFade,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    val motion = LocalLiquidGlassTokens.current.navigation
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    var zOrder by remember { mutableFloatStateOf(0f) }
    val nextZ = remember(targetState) { zOrder += 1f; zOrder }
    AnimatedContent(
        targetState = targetState,
        modifier = modifier.clipToBounds(),
        contentKey = contentKey,
        transitionSpec = {
            when (style) {
                LiquidViewTransitionStyle.CrossFade -> {
                    val enter = fadeIn(tween(150, easing = motion.pageEasing))
                    val exit = fadeOut(snap(delayMillis = 150))
                    (enter togetherWith exit).apply { targetContentZIndex = nextZ }
                }
                LiquidViewTransitionStyle.Zoom -> {
                    val enter = fadeIn(tween(motion.sameDepthEnterMillis, easing = motion.pageEasing)) +
                        scaleIn(tween(motion.durationMillis, easing = motion.pageEasing), initialScale = .92f)
                    val exit = fadeOut(tween(motion.sameDepthExitMillis, easing = motion.pageEasing)) +
                        scaleOut(tween(motion.sameDepthExitMillis, easing = motion.pageEasing), targetScale = 1.04f)
                    (enter togetherWith exit).apply { targetContentZIndex = nextZ }
                }
                LiquidViewTransitionStyle.Slide -> {
                    val slide = tween<IntOffset>(motion.durationMillis, easing = motion.pageEasing)
                    val fade = tween<Float>(motion.durationMillis, easing = motion.pageEasing)
                    ((slideInHorizontally(slide) { direction * it / 4 } + fadeIn(fade)) togetherWith
                        (slideOutHorizontally(slide) { -direction * it / 4 } + fadeOut(fade))).apply {
                        targetContentZIndex = nextZ
                    }
                }
            }
        },
        label = "Liquid view transition",
    ) { displayed ->
        content(displayed)
    }
}

/** Lets the destination move beneath the returning glass while the navigation toolbar stays anchored. */
@Composable
fun <T> LiquidNavigationTransition(
    page: T,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    LiquidNavigationTransition(page, depth, { it }, modifier, content)
}

/** A refresh of the same destination updates in place while an outgoing route retains its own data. */
@Composable
fun <T> LiquidNavigationTransition(
    page: T,
    depth: (T) -> Int,
    contentKey: (T) -> Any?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val motion = LocalLiquidGlassTokens.current.navigation
    val dark = liquidGlassAppearance().dark
    val pageSlide = remember(motion) { tween<IntOffset>(motion.durationMillis, easing = motion.pageEasing) }
    val navTransition = updateTransition(page, label = "Navigation level")
    navTransition.AnimatedContent(modifier.clipToBounds(), contentKey = contentKey, transitionSpec = {
        val step = depth(targetState).compareTo(depth(initialState))
        val transition = when {
            step > 0 -> slideInHorizontally(pageSlide) { direction * it } togetherWith
                slideOutHorizontally(pageSlide) { -direction * it / 3 }
            step < 0 -> slideInHorizontally(pageSlide) { -direction * it / 3 } togetherWith
                slideOutHorizontally(pageSlide) { direction * it }
            else -> fadeIn(tween(motion.sameDepthEnterMillis)) togetherWith fadeOut(tween(motion.sameDepthExitMillis))
        }
        transition.apply { targetContentZIndex = depth(targetState).toFloat() }
    }) { displayed ->
        val displayedDepth = depth(displayed)
        val isTransitioning = navTransition.isRunning
        val isTopLayer = displayedDepth >= depth(navTransition.currentState) && displayedDepth >= depth(navTransition.targetState)
        val coverFraction by navTransition.animateFloat(
            transitionSpec = { tween(motion.durationMillis, easing = motion.pageEasing) },
            label = "Navigation cover dim",
        ) { state ->
            if (depth(state) > displayedDepth) 1f else 0f
        }
        val maxDim = if (dark) .14f else .06f
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
            content(displayed)
        }
    }
}

/** Keeps both values alive during a local replacement. Draw the value supplied to [content]. */
@Composable
fun <T> LiquidContentReplacement(
    value: T,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable (T) -> Unit,
) {
    LiquidKeyedReplacement(value, modifier, alignment, { it }, content)
}

@Composable
internal fun <T> LiquidKeyedReplacement(
    value: T, modifier: Modifier, alignment: Alignment, contentKey: (T) -> Any?, content: @Composable (T) -> Unit,
) {
    val tokens = LocalLiquidGlassTokens.current.replacement
    AnimatedContent(value, modifier, contentAlignment = alignment, contentKey = contentKey, transitionSpec = {
        (fadeIn(tween(tokens.enterMillis)) togetherWith fadeOut(tween(tokens.exitMillis)))
            .using(SizeTransform(clip = false))
    }, label = "Content replacement") { displayed ->
        val focus by transition.animateFloat(transitionSpec = {
            tween(if (targetState == EnterExitState.Visible) tokens.enterMillis else tokens.exitMillis)
        }, label = "Content focus") { if (it == EnterExitState.Visible) 0f else 1f }
        val outgoing = transition.targetState == EnterExitState.PostExit
        Box(Modifier.diffuseContent(tokens, { focus }, outgoing)) { content(displayed) }
    }
}

/** Render compact foreground replacements with optical diffusion, skipping full-screen blur FBOs. */
@Composable
internal fun Modifier.diffuseContent(tokens: GlassReplacementTokens, amount: () -> Float, outgoing: Boolean = false): Modifier {
    val layer = rememberGraphicsLayer()
    return drawWithCache {
        val extent = tokens.blur.toPx().coerceAtLeast(0f)
        val maxBlurTargetPx = 420.dp.toPx()
        val allowBlurFbo = size.maxDimension <= maxBlurTargetPx && extent > .4f
        val bleed = if (allowBlurFbo) kotlin.math.ceil(extent * 3f).toInt() else 0
        val padded = IntSize(size.width.toInt() + 2 * bleed, size.height.toInt() + 2 * bleed)
        onDrawWithContent {
            val fraction = amount().coerceIn(0f, 1f)
            if (fraction <= .003f || size.minDimension <= 0f) {
                drawContent()
            } else {
                val zoom = 1f + (if (outgoing) 1f else -1f) * tokens.scaleChange * fraction
                if (!allowBlurFbo) {
                    withTransform({ scale(zoom, zoom, center) }) { this@onDrawWithContent.drawContent() }
                } else {
                    val radius = extent * fraction
                    layer.renderEffect = if (radius > .4f) BlurEffect(radius, radius, TileMode.Decal) else null
                    layer.record(size = padded) {
                        translate(bleed.toFloat(), bleed.toFloat()) { this@onDrawWithContent.drawContent() }
                    }
                    withTransform({ scale(zoom, zoom, center); translate(-bleed.toFloat(), -bleed.toFloat()) }) { drawLayer(layer) }
                }
            }
        }
    }
}
