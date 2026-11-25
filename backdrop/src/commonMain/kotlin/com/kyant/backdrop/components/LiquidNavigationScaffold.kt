package com.kyant.backdrop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.flow.distinctUntilChanged
import org.intellij.lang.annotations.Language
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class LiquidNavigationTitleMode { Automatic, Large, Inline }

@Immutable
data class LiquidPinnedNavChrome(
    val title: String,
    val smallTitleAlpha: Float,
    val headerGlassAlpha: Float,
    val isScrolled: Boolean,
)

val LocalPinnedNavChromeReporter = compositionLocalOf<((LiquidPinnedNavChrome) -> Unit)?> { null }

/** Renders the iOS 27 full-width progressive-blur header glass shared by [LiquidNavigationScaffold] and pinned stack bars. */
@Composable
fun LiquidProgressiveNavHeader(
    backdrop: Backdrop,
    edgeLayer: LayerBackdrop?,
    barHeight: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val appearance = liquidGlassAppearance()
    val dark = appearance.dark
    val supportsRuntimeShader = remember { isRuntimeShaderSupported() }
    val headerFallbackBrush = remember(dark, appearance.opacity) {
        val base = if (dark) Color(0xFF000000) else Color(0xFFF5F7FA)
        val opShift = (appearance.opacity - .5f) * if (dark) .44f else .42f
        val topA = ((if (dark) .82f else .82f) + opShift).coerceIn(.16f, .94f)
        val midA = ((if (dark) .56f else .58f) + opShift).coerceIn(.15f, .92f)
        val botA = ((if (dark) .34f else .36f) + opShift).coerceIn(.14f, .90f)
        Brush.verticalGradient(0f to base.copy(alpha = topA), .5f to base.copy(alpha = midA), 1f to base.copy(alpha = botA))
    }
    val headerHairline = if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .065f)
    val visible = alpha > 0.003f
    SideEffect {
        edgeLayer?.let {
            it.isEnabled = visible
            if (!visible) it.bumpDrawEpoch()
        }
    }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .graphicsLayer { this.alpha = if (visible) alpha.coerceIn(0f, 1f) else 0f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    val baseBlur = 6.dp.toPx()
                    val extraTopBlur = 10.dp.toPx()
                    blur(baseBlur, TileMode.Clamp)
                    if (supportsRuntimeShader) {
                        runtimeShaderEffect("NavHeaderGlass_v2", NavHeaderGlassShader, "content") {
                            setFloatUniform("barWidth", size.width)
                            setFloatUniform("barHeight", size.height)
                            setFloatUniform("density", 1.dp.toPx())
                            setFloatUniform("topBlurPx", extraTopBlur)
                            setFloatUniform("dark", if (dark) 1f else 0f)
                            setFloatUniform("glassOpacity", appearance.opacity)
                        }
                    }
                },
                highlight = null,
                shadow = null,
                exportedBackdrop = edgeLayer,
                onDrawSurface = if (supportsRuntimeShader) null else {
                    {
                        drawRect(headerFallbackBrush)
                        val hairlinePx = 0.5.dp.toPx()
                        drawRect(
                            color = headerHairline,
                            topLeft = Offset(0f, (size.height - hairlinePx).coerceAtLeast(0f)),
                            size = Size(size.width, hairlinePx),
                        )
                    }
                },
            ),
    )
}

/**
 * Place the supplied insets in the scroll content. The content then passes beneath the fixed controls.
 * [scrollState] is the list in the focused pane. Leading and trailing groups sample that content.
 */
@Composable
fun LiquidNavigationScaffold(
    title: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    scrollState: LazyListState? = null,
    titleMode: LiquidNavigationTitleMode = LiquidNavigationTitleMode.Automatic,
    colors: LiquidListColors = liquidListColors(),
    leading: @Composable (Backdrop) -> Unit = {},
    trailing: @Composable (Backdrop) -> Unit = {},
    bottomBar: (@Composable (Backdrop) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) = LiquidNavigationScaffold(title, backdrop, modifier, scrollState, titleMode, colors, leading, trailing, bottomBar,
    WindowInsets(0), scrollOffsetPx = null, content = content)

@Composable
fun LiquidNavigationScaffold(
    title: String, backdrop: Backdrop, modifier: Modifier = Modifier, scrollState: LazyListState? = null,
    titleMode: LiquidNavigationTitleMode = LiquidNavigationTitleMode.Automatic, colors: LiquidListColors = liquidListColors(),
    leading: @Composable (Backdrop) -> Unit = {}, trailing: @Composable (Backdrop) -> Unit = {},
    bottomBar: (@Composable (Backdrop) -> Unit)? = null, contentWindowInsets: WindowInsets,
    scrollOffsetPx: (() -> Float)? = null,
    pageBackdrop: LayerBackdrop? = null,
    recordPageBackdrop: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val page = pageBackdrop ?: rememberLayerBackdrop()
    val titleLayer = rememberLayerBackdrop()
    val edgeLayer = rememberLayerBackdrop()
    val material = rememberCombinedBackdrop(backdrop, page, titleLayer)
    val underToolbar = rememberCombinedBackdrop(backdrop, page, edgeLayer, titleLayer)
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current
        val safe = contentWindowInsets.asPaddingValues()
        val status = safe.calculateTopPadding()
        val nav = safe.calculateBottomPadding()
        val horizontalStart = safe.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
        val horizontalEnd = safe.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
        val large = when (titleMode) {
            LiquidNavigationTitleMode.Large -> true
            LiquidNavigationTitleMode.Inline -> false
            LiquidNavigationTitleMode.Automatic -> scrollState != null && maxHeight >= 360.dp
        }
        val actionsHeight = maxOf(60.dp, with(density) { 22.sp.toDp() } + 16.dp)
        val titleHeight = if (large) maxOf(60.dp, with(density) { 34.sp.toDp() } + 20.dp) else 0.dp
        val actionsHeightPx = with(density) { actionsHeight.toPx() }
        val titleHeightPx = with(density) { titleHeight.toPx() }
        val range = if (large) titleHeightPx.coerceAtLeast(1f) else with(density) { 28.dp.toPx() }
        fun scrolledFraction(): Float = when {
            scrollOffsetPx != null -> (scrollOffsetPx() / range).coerceIn(0f, 1f)
            scrollState == null -> 0f
            scrollState.firstVisibleItemIndex > 0 -> 1f
            else -> (scrollState.firstVisibleItemScrollOffset / range).coerceIn(0f, 1f)
        }
        fun compactFraction(): Float = if (large) scrolledFraction() else 1f
        fun smallTitleAlpha(): Float = ((compactFraction() - .72f) / .28f).coerceIn(0f, 1f)
        fun largeTitleAlpha(): Float = 1f - ((compactFraction() - .45f) / .30f).coerceIn(0f, 1f)

        fun headerGlassAlpha(): Float =
            if (large) ((scrolledFraction() - .20f) / .45f).coerceIn(0f, 1f) else scrolledFraction()

        val isScrolled by remember(scrollState, scrollOffsetPx, range) { derivedStateOf { scrolledFraction() > 0.003f } }
        val showHeaderGlass by remember(large, scrollState, scrollOffsetPx, range) { derivedStateOf { headerGlassAlpha() > 0.003f } }
        val showLarge by remember(large, scrollState, scrollOffsetPx, range) { derivedStateOf { large && largeTitleAlpha() > 0.003f } }
        val showSmall by remember(large, scrollState, scrollOffsetPx, range) { derivedStateOf { !large || smallTitleAlpha() > 0.003f } }
        val smallIsHeading by remember(large, scrollState, scrollOffsetPx, range) { derivedStateOf { !large || smallTitleAlpha() > largeTitleAlpha() } }

        val bottomInset = if (bottomBar == null) 8.dp else 68.dp
        val reportPinnedChrome = LocalPinnedNavChromeReporter.current
        Box(
            Modifier
                .matchParentSize()
                .layerBackdrop(
                    page,
                    enabled = recordPageBackdrop && (reportPinnedChrome == null || showHeaderGlass || pageBackdrop != null),
                )
                .then(if (colors.background.alpha > 0f) Modifier.background(colors.background) else Modifier),
        ) {
            content(
                PaddingValues(
                    start = horizontalStart,
                    top = status + actionsHeight + titleHeight,
                    end = horizontalEnd,
                    bottom = bottomInset + nav,
                ),
            )
        }
        val dark = liquidGlassAppearance().dark
        val edgeAlpha = if (dark) .62f else .42f
        val bottomFadeBrush = remember(colors.background, edgeAlpha) {
            Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = edgeAlpha)))
        }
        LaunchedEffect(reportPinnedChrome, title, large, scrollState, scrollOffsetPx, range) {
            val reporter = reportPinnedChrome ?: return@LaunchedEffect
            snapshotFlow {
                LiquidPinnedNavChrome(
                    title = title,
                    smallTitleAlpha = smallTitleAlpha(),
                    headerGlassAlpha = headerGlassAlpha(),
                    isScrolled = isScrolled,
                )
            }.distinctUntilChanged().collect { reporter(it) }
        }
        if (showHeaderGlass) {
            LiquidProgressiveNavHeader(
                backdrop = page,
                edgeLayer = if (reportPinnedChrome == null) edgeLayer else null,
                barHeight = status + actionsHeight,
                alpha = headerGlassAlpha(),
            )
        }
        var leadingWidth by remember { mutableIntStateOf(0) }
        var trailingWidth by remember { mutableIntStateOf(0) }
        Box(Modifier.fillMaxWidth().padding(top = status, start = horizontalStart, end = horizontalEnd).height(actionsHeight + titleHeight)) {
            if (showLarge) {
                Box(Modifier.fillMaxWidth()
                    .offset { IntOffset(0, (actionsHeightPx - titleHeightPx * compactFraction()).roundToInt()) }
                    .height(titleHeight).layerBackdrop(titleLayer, enabled = isScrolled).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    LiquidContentReplacement(title) { displayed ->
                        BasicText(displayed, Modifier.graphicsLayer { alpha = largeTitleAlpha() }
                            .then(if (!smallIsHeading) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
                            style = TextStyle(colors.ink, 34.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (reportPinnedChrome == null && showSmall) Layout(modifier = Modifier.fillMaxWidth().height(actionsHeight), content = {
                LiquidContentReplacement(title) { displayed ->
                    BasicText(displayed, Modifier.graphicsLayer { alpha = smallTitleAlpha() }
                        .then(if (smallIsHeading) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics {}),
                        style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }) { measured, limits ->
                val gutter = 12.dp.roundToPx()
                val sideInset = 16.dp.roundToPx()
                val start = (if (leadingWidth == 0) gutter else sideInset + leadingWidth + gutter).coerceAtMost(limits.maxWidth)
                val end = (limits.maxWidth - trailingWidth - (if (trailingWidth == 0) 0 else sideInset) - gutter).coerceAtLeast(start)
                val label = measured.single().measure(Constraints(maxWidth = (end - start).coerceAtLeast(0)))
                // A busy trailing group moves the compact title next to Back. Balanced toolbars keep it centered cleanly.
                val centered = (limits.maxWidth - label.width) / 2
                val maxX = (end - label.width).coerceAtLeast(start)
                val x = centered.coerceIn(start, maxX)
                layout(limits.maxWidth, limits.maxHeight) { label.placeRelative(x, (limits.maxHeight - label.height) / 2) }
            }
            if (reportPinnedChrome == null) {
                Box(Modifier.align(Alignment.TopStart).height(actionsHeight).padding(start = 16.dp).onSizeChanged { leadingWidth = it.width },
                    contentAlignment = Alignment.CenterStart) { LiquidNavigationBarStyle { leading(underToolbar) } }
                Box(Modifier.align(Alignment.TopEnd).height(actionsHeight).padding(end = 16.dp).onSizeChanged { trailingWidth = it.width },
                    contentAlignment = Alignment.CenterEnd) { LiquidNavigationBarStyle { trailing(underToolbar) } }
            }
        }
        if (bottomBar != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(bottomFadeBrush)
                .padding(start = horizontalStart, end = horizontalEnd, bottom = nav).height(bottomInset),
                contentAlignment = Alignment.Center) {
                bottomBar(material)
            }
        }
    }
}

@Language("AGSL")
internal const val NavHeaderGlassShader = """
uniform shader content;
uniform float barWidth;
uniform float barHeight;
uniform float density;
uniform float topBlurPx;
uniform float dark;
uniform float glassOpacity;

half4 main(float2 coord) {
    float2 minPt = float2(0.5, 0.5);
    float2 maxPt = float2(max(barWidth - 0.5, 0.5), max(barHeight - 0.5, 0.5));
    float2 baseCoord = clamp(coord, minPt, maxPt);
    float t = clamp(baseCoord.y / max(barHeight, 1.0), 0.0, 1.0);
    float prog = pow(1.0 - t, 1.35);
    float extraR = topBlurPx * prog;
    half3 rgb = content.eval(baseCoord).rgb;
    if (extraR > 0.4) {
        half3 ringSum = half3(0.0);
        for (int i = 0; i < 6; ++i) {
            float a1 = float(i) * 1.04719755;
            float a2 = (float(i) + 0.5) * 1.04719755;
            ringSum += content.eval(clamp(baseCoord + float2(cos(a1), sin(a1)) * (extraR * 0.52), minPt, maxPt)).rgb;
            ringSum += content.eval(clamp(baseCoord + float2(cos(a2), sin(a2)) * extraR, minPt, maxPt)).rgb;
        }
        rgb = mix(rgb, ringSum * half(0.0833333), half(0.80 * clamp(extraR / max(topBlurPx, 1.0), 0.0, 1.0)));
    }
    half luma = dot(rgb, half3(0.2126, 0.7152, 0.0722));
    rgb = clamp(mix(half3(luma), rgb, half(mix(1.08, 1.12, dark))), half3(0.0), half3(1.0));
    if (dark < 0.5) {
        float opShift = (glassOpacity - 0.5) * 0.42;
        float veil = clamp(mix(0.36, 0.82, prog) + opShift, 0.16, 0.94);
        rgb = mix(rgb, half3(0.962, 0.966, 0.976), half(veil));
    } else {
        float opShift = (glassOpacity - 0.5) * 0.44;
        float veil = clamp(mix(0.34, 0.82, prog) + opShift, 0.14, 0.94);
        rgb = mix(rgb, half3(0.0, 0.0, 0.0), half(veil));
    }
    float distBottom = (barHeight - baseCoord.y) / max(density, 1.0);
    float hairline = smoothstep(0.75, 0.0, distBottom);
    if (dark < 0.5) {
        rgb = mix(rgb, half3(0.0), half(0.065 * hairline));
    } else {
        rgb = mix(rgb, half3(1.0), half(0.10 * hairline));
    }
    return half4(clamp(rgb, half3(0.0), half3(1.0)), 1.0);
}
"""
