package com.kyant.backdrop.morph

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.components.LocalMenuReturnOwner
import com.kyant.backdrop.components.LocalLiquidGlassTokens
import com.kyant.backdrop.components.GlassForeground
import com.kyant.backdrop.components.LiquidGlassSamplingScope
import com.kyant.backdrop.components.liquidGlassAppearance
import com.kyant.backdrop.components.liquidGlassShadow
import com.kyant.backdrop.components.rememberLiquidRimHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.diffuseBackdrop
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** The live glass body, including its translation and the parent's depth transform. */
internal class LiquidMorphBody(private val frame: () -> LiquidMorphFrame) {
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    val cornerFraction get() = frame().body.run { min(r, radiusY) / min(w, h).coerceAtLeast(1f) }

    fun boundsInRoot(): Rect? {
        val layout = coordinates?.takeIf { it.isAttached } ?: return null
        val body = frame().body.takeIf { it.active } ?: return null
        return Rect(
            layout.localToRoot(Offset(body.cx - body.w / 2f, body.cy - body.h / 2f)),
            layout.localToRoot(Offset(body.cx + body.w / 2f, body.cy + body.h / 2f)),
        )
    }
}

internal val LocalLiquidMorphBody = staticCompositionLocalOf<LiquidMorphBody?> { null }

/**
 * SwiftUI `GlassEffectContainer` + `.glassEffectID`-inspired Liquid Glass morph container.
 *
 * The glass and both content slots share the same moving geometry and interaction transform.
 */
@Composable
fun LiquidMorphContainer(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    collapsedSize: DpSize,
    expandedSize: DpSize,
    modifier: Modifier = Modifier,
    collapsedCornerRadius: Dp = min(collapsedSize.width.value, collapsedSize.height.value).dp * 0.5f,
    expandedCornerRadius: Dp = 32.dp,
    config: LiquidMorphConfig = LiquidMorphConfig(),
    slowMo: Boolean = false,
    handleSurfaceTap: Boolean = true,
    initialExpanded: Boolean = expanded,
    surfaceTouchEnabled: Boolean = true,
    onSourceRestored: (() -> Unit)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    sourceAnchorInRoot: Rect? = null,
    elasticStretchPx: Offset = Offset.Zero,
    elasticCenterPx: Offset = Offset.Zero,
    collapsedContent: @Composable BoxScope.() -> Unit,
    expandedContent: @Composable BoxScope.() -> Unit,
) = LiquidGlassSamplingScope {
    val density = LocalDensity.current
    val margin = 24.dp
    val containerWidth = max(collapsedSize.width, expandedSize.width) + margin * 2
    val containerHeight = max(collapsedSize.height, expandedSize.height) + margin * 2

    var physics by remember { mutableStateOf(LiquidMorphPhysicsState.initial(initialExpanded)) }

    val currentConfig by rememberUpdatedState(config)

    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    LaunchedEffect(expanded, slowMo) {
        val cur = physics
        if (cur.targetExpanded != expanded) {
            val triggerGlow = if (expanded && cur.morph.value < 0.25f) 0.92f else cur.pressGlow
            physics = cur.copy(
                targetExpanded = expanded,
                pressGlow = triggerGlow,
            )
        }
        if (physics.isSettled) return@LaunchedEffect
            var lastNanos = withFrameNanos { it }
            while (!physics.isSettled) {
                val nowNanos = withFrameNanos { it }
                val dtRaw = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, .25f)
                lastNanos = nowNanos
                val dtSec = if (slowMo) dtRaw * 0.25f else dtRaw
                val steps = ceil(dtSec / .04f).toInt().coerceAtLeast(1)
                var next = physics
                repeat(steps) { next = advanceLiquidMorphPhysics(next, currentConfig, dtSec / steps) }
                physics = next
            }
    }

    BoxWithConstraints(modifier.size(containerWidth, containerHeight)) {
        // The popup can be constrained by a narrow pane or a short landscape window.
        val roomWidth = (maxWidth - margin * 2).coerceAtLeast(1.dp)
        val roomHeight = (maxHeight - margin * 2).coerceAtLeast(1.dp)
        val fittedCollapsed = DpSize(minOf(collapsedSize.width, roomWidth), minOf(collapsedSize.height, roomHeight))
        val fittedMenu = DpSize(minOf(expandedSize.width, roomWidth), minOf(expandedSize.height, roomHeight))
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val origin = coordinates?.takeIf { it.isAttached }
        val sourceAnchorX = if (sourceAnchorInRoot != null && origin != null)
            origin.localPositionOf(origin.findRootCoordinates(), sourceAnchorInRoot.center).x else null
        val menuTokens = LocalLiquidGlassTokens.current.menu
        val maxStretchPx = with(density) { minOf(20.dp.toPx(), margin.toPx() * 0.85f) }
        val clampedStretchX = elasticStretchPx.x.coerceIn(-maxStretchPx, maxStretchPx)
        val clampedStretchY = elasticStretchPx.y.coerceIn(-maxStretchPx, maxStretchPx)
        val baseEwPx = with(density) { fittedMenu.width.toPx() }
        val baseEhPx = with(density) { fittedMenu.height.toPx() }
        val collapsedWPx = with(density) { fittedCollapsed.width.toPx() }
        val collapsedHPx = with(density) { fittedCollapsed.height.toPx() }
        val liveEwPx = (baseEwPx + clampedStretchX).coerceAtLeast(collapsedWPx)
        val liveEhPx = (baseEhPx + clampedStretchY).coerceAtLeast(collapsedHPx)
        val alignRight = config.alignment == LiquidMorphAlignment.TopTrailing ||
            config.alignment == LiquidMorphAlignment.BottomTrailing
        val alignBottom = config.alignment == LiquidMorphAlignment.BottomLeading ||
            config.alignment == LiquidMorphAlignment.BottomTrailing
        val deltaW = liveEwPx - baseEwPx
        val deltaH = liveEhPx - baseEhPx
        val geoShiftX = (if (alignRight) -deltaW * 0.28f else deltaW * 0.28f) + elasticCenterPx.x
        val geoShiftY = (if (alignBottom) -deltaH * 0.28f else deltaH * 0.28f) + elasticCenterPx.y
        val frame = evaluateLiquidMorphFrame(
            phys = physics,
            config = config,
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            collapsedWidthPx = collapsedWPx,
            collapsedHeightPx = collapsedHPx,
            collapsedRadiusPx = with(density) { collapsedCornerRadius.toPx() },
            expandedWidthPx = liveEwPx,
            expandedHeightPx = liveEhPx,
            expandedRadiusPx = with(density) { expandedCornerRadius.toPx() },
            density = density.density,
            sourceAnchorX = sourceAnchorX,
            contour = menuTokens.contour,
        )

        val returnOwner = LocalMenuReturnOwner.current
        val borderless = !config.showsCollapsedSurface
        val returnOpacity = remember { Animatable(1f) }
        val materialMillis = (LocalLiquidGlassTokens.current.replacement.exitMillis * .55f).toInt().coerceAtLeast(45)
        val sourceRestored = !expanded && !physics.targetExpanded && frame.sourceAligned(.75f) && (
            physics.isSettled || (
                abs(physics.morph.value) < 0.010f &&
                    abs(physics.sizeSpring.value) < 0.010f &&
                    abs(physics.widthSpring.value) < 0.010f &&
                    abs(physics.dockLift.value) < 0.006f &&
                    abs(physics.dockLift.velocity) < 0.04f
                )
            )
        val restored by rememberUpdatedState(onSourceRestored)
        LaunchedEffect(expanded, sourceRestored, returnOwner, borderless) {
            if (expanded) returnOpacity.snapTo(1f)
            if (!sourceRestored || restored == null) return@LaunchedEffect
            if (returnOwner != null && !borderless) {
                returnOwner.restoringSource = true
                returnOpacity.animateTo(0f, tween(materialMillis))
            }
            restored?.invoke()
        }

        val interactionSource = remember { MutableInteractionSource() }
        val touchScope = rememberCoroutineScope()
        val pressTokens = com.kyant.backdrop.components.LocalLiquidGlassTokens.current.press
        val touch = remember(touchScope, pressTokens) { LiquidMorphTouch(touchScope, pressTokens) }
        val currentFrame by rememberUpdatedState(frame)
        val body = remember { LiquidMorphBody { currentFrame } }
        DisposableEffect(body) { onDispose { body.coordinates = null } }
        val defaultTransform: GraphicsLayerScope.() -> Unit = remember(touch) { { touch.transform(this, currentFrame) } }
        val transform = layerBlock ?: defaultTransform
        val hasLens = isRuntimeShaderSupported()
        val inkShader = remember(hasLens) {
            if (hasLens) SharedRenderEffectShaderCache.obtainRuntimeShader("LiquidMenuContentShader", LiquidMenuContentShader) else null
        }
        val toolbar = returnOwner?.sourceTrigger?.toolbar
        val collapsedFill = if (!borderless && toolbar != null && config.collapsedSurfaceColor == Color.Transparent) {
            if (hasLens) toolbar.surfaceCustomTint ?: Color.Transparent else toolbar.surfaceFill
        } else {
            if (hasLens) appearance.customTintOnly(config.collapsedSurfaceColor) ?: Color.Transparent
            else appearance.overlay(config.collapsedSurfaceColor)
        }
        val expandedFill = if (hasLens) {
            appearance.customTintOnly(config.expandedSurfaceColor) ?: Color.Transparent
        } else {
            appearance.menuOverlay(config.expandedSurfaceColor)
        }
        val optics = remember(config, appearance, collapsedFill, expandedFill) { config.copy(
            collapsedSurfaceColor = collapsedFill,
            expandedSurfaceColor = expandedFill,
        ) }
        val effects: BackdropEffectScope.() -> Unit = {
            vibrancy()
            val panelRadius = appearance.menuBlurRadius + config.blurRadiusDp.dp * .35f
            val controlBlur = appearance.controlBlurRadius *
                if (!borderless && toolbar?.inNavigationBar == true) frame.materialProgress else 1f
            blur(controlBlur.toPx())
            val opacityT = appearance.opacity
            val diffusionCurve = 1f - (1f - opacityT) * (1f - opacityT)
            val diffusion = menuTokens.clearDiffusion + (menuTokens.denseDiffusion - menuTokens.clearDiffusion) * diffusionCurve
            diffuseBackdrop(panelRadius.toPx(), diffusion.coerceIn(0f, 1f) * frame.materialProgress)
            if (hasLens) liquidMorphLens(frame, optics, touch.position, touch.strength.value, appearance)
        }
        val silhouette = remember(frame.pill, frame.body) { frame.fallbackShape() }
        val glass = if (hasLens) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = effects,
                highlight = { null },
                shadow = { null },
                innerShadow = { null },
                layerBlock = transform,
                onDrawSurface = onDrawSurface,
            )
        } else {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { silhouette },
                highlight = { rim },
                shadow = null,
                effects = effects,
                layerBlock = transform,
                onDrawSurface = {
                    onDrawSurface?.invoke(this)
                    val surface = optics.collapsedSurfaceColor
                    if (surface.alpha > 0f) drawRect(surface.copy(alpha = surface.alpha * (1f - frame.materialProgress)))
                    val expandedSurface = optics.expandedSurfaceColor
                    if (expandedSurface.alpha > 0f) drawRect(expandedSurface.copy(alpha = expandedSurface.alpha * frame.materialProgress))
                    val strength = touch.strength.value
                    if (strength > 0f) {
                        val glow = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = strength * 0.46f), Color.Transparent),
                            center = touch.position, radius = frame.pressGlowRadius,
                        )
                        drawCircle(glow, radius = frame.pressGlowRadius, center = touch.position)
                    }
                },
            )
        }

        val elevation = frame.expandedAlpha.coerceIn(0f, 1f)
        val reveal = if (borderless) (physics.morph.value / .22f).coerceIn(0f, 1f) else 1f
        val surfaceAlpha = reveal * reveal * (3f - 2f * reveal)
        if (borderless && returnOwner != null) {
            androidx.compose.runtime.SideEffect { returnOwner.borderlessCover = surfaceAlpha }
            DisposableEffect(returnOwner) { onDispose { returnOwner.borderlessCover = 0f } }
        }
        // The shadow can extend beyond the optical layer's 24-dp layout margin.
        Box(
            Modifier.fillMaxSize().onGloballyPositioned { body.coordinates = it; coordinates = it }
                .graphicsLayer {
                    translationX = geoShiftX
                    translationY = geoShiftY
                }
                .then(if (returnOwner?.restoringSource == true && !expanded) Modifier.graphicsLayer { alpha = returnOpacity.value } else Modifier),
        ) {
            if (surfaceAlpha > 0f) Spacer(Modifier.matchParentSize().graphicsLayer(transform)
                .then(if (borderless) Modifier.graphicsLayer { alpha = surfaceAlpha } else Modifier)
                .liquidGlassShadow(silhouette, elevation, frame = frame))
            if (borderless && surfaceAlpha > 0f) {
                Spacer(Modifier.matchParentSize().graphicsLayer { alpha = surfaceAlpha }.then(glass))
            }
            Box(
                modifier = Modifier.fillMaxSize()
                    .then(if (handleSurfaceTap) Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onExpandedChange(!expanded) },
                    ) else Modifier)
                    .then(if (surfaceTouchEnabled && (handleSurfaceTap || !expanded)) Modifier.pointerInput(touch) {
                        touch.track(this) { currentFrame }
                    } else Modifier)
                    .then(if (borderless) Modifier.graphicsLayer(transform) else glass),
                contentAlignment = AbsoluteAlignment.TopLeft,
            ) {
                // Clip outside the blur/scale layer to the live glass silhouette, keeping the
                // recorded toolbar foreground uncropped and registered to its resting width.
                if (frame.sourceContent.active && frame.collapsedAlpha > 0.01f) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(silhouette),
                        contentAlignment = AbsoluteAlignment.TopLeft,
                    ) {
                        val collapsedW = with(density) { fittedCollapsed.width.toPx() }
                        val collapsedH = with(density) { fittedCollapsed.height.toPx() }
                        val left = (frame.sourceContent.cx - collapsedW * 0.5f).roundToInt()
                        val top = (frame.sourceContent.cy - collapsedH * 0.5f).roundToInt()
                        GlassForeground(
                            modifier = Modifier
                                .absoluteOffset { IntOffset(left, top) }
                                .requiredSize(fittedCollapsed)
                                .graphicsLayer {
                                    alpha = frame.collapsedAlpha
                                    scaleX = frame.collapsedScale
                                    scaleY = frame.collapsedScale
                                    renderEffect = if (frame.collapsedBlurPx > 0.15f) {
                                        BlurEffect(
                                            frame.collapsedBlurPx,
                                            frame.collapsedBlurPx,
                                            TileMode.Decal,
                                        )
                                    } else null
                                },
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = collapsedContent) }
                    }
                }

                if (frame.expandedAlpha > 0.01f && frame.body.active) {
                    Box(
                        modifier = frame.body.contentBounds(density)
                            .graphicsLayer {
                                alpha = frame.expandedAlpha
                                renderEffect = if (inkShader != null && frame.contentRefraction > .001f) {
                                    val contour = frame.body
                                    inkShader.setFloatUniform("halfSize", size.width * .5f, size.height * .5f)
                                    inkShader.setFloatUniform("radius", contour.r)
                                    inkShader.setFloatUniform("crownBias", contour.crownBias)
                                    inkShader.setFloatUniform("contour", contour.radiusY, contour.tipX,
                                        if (contour.tipAtBottom) 1f else -1f, contour.farRadiusY)
                                    val width = min(menuTokens.contentRimWidth.toPx(), size.minDimension * .36f).coerceAtLeast(1f)
                                    inkShader.setFloatUniform("width", width)
                                    inkShader.setFloatUniform("displacement", min(width, menuTokens.contentDisplacement.toPx()) * frame.contentRefraction)
                                    RuntimeShaderEffect(inkShader, "content")
                                } else null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlassForeground(
                            modifier = Modifier
                                .requiredSize(fittedMenu)
                                .graphicsLayer {
                                    scaleX = frame.expandedScaleX
                                    scaleY = frame.expandedScaleY
                                    renderEffect = if (frame.expandedBlurPx > 0.15f) {
                                        BlurEffect(
                                            frame.expandedBlurPx,
                                            frame.expandedBlurPx,
                                            TileMode.Decal,
                                        )
                                    } else null
                                },
                        ) {
                            CompositionLocalProvider(LocalLiquidMorphBody provides body) { expandedContent() }
                        }
                    }
                }
            }
        }
    }
}

private fun SdfBoxPrimitive.contentBounds(density: Density): Modifier = with(density) {
    val left = (cx - w * 0.5f).roundToInt()
    val top = (cy - h * 0.5f).roundToInt()
    val shape = if (hasUniformCorners) RoundedCornerShape(r.toDp()) else GenericShape { size, _ ->
        addMorphBody(this@contentBounds.copy(cx = size.width * .5f, cy = size.height * .5f))
    }
    Modifier.absoluteOffset { IntOffset(left, top) }
        .size(w.toDp(), h.toDp())
        .clip(shape)
}

private fun LiquidMorphFrame.fallbackShape() = GenericShape { _, _ ->
    if (pill.active && body.active) {
        val capsule = Path().apply { addRoundRect(pill.roundRect()) }
        val droplet = Path().apply { addMorphBody(body) }
        op(capsule, droplet, PathOperation.Union)
    } else if (pill.active) {
        addRoundRect(pill.roundRect())
    } else if (body.active) {
        addMorphBody(body)
    }
}
