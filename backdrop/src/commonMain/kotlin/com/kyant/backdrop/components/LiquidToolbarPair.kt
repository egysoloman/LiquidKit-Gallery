package com.kyant.backdrop.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import kotlin.math.roundToInt

internal class ToolbarPairSlot {
    var group by mutableStateOf<ToolbarTouchGroup?>(null)
    var bounds by mutableStateOf(Rect.Zero)
    fun visualBounds(origin: Offset, density: Density): Rect? {
        val motion = group?.light?.motion ?: return null
        if (bounds.isEmpty) return null
        val pose = motion.transform(bounds.size, density)
        val center = bounds.center - origin + pose.translation
        val half = Offset(bounds.width * pose.scaleX / 2f, bounds.height * pose.scaleY / 2f)
        return Rect(center - half, center + half)
    }
}

internal val LocalToolbarPairSlot = staticCompositionLocalOf<ToolbarPairSlot?> { null }

/** Two neighboring toolbar groups share a glass outline when their rendered edges approach.
 * Each slot owns one [LiquidToolbarGroup] and retains its independent actions and menu anchor.
 * [spacing] lays out the controls; [fusionDistance] controls their optical connection.
 */
@Composable
fun LiquidToolbarPair(
    backdrop: Backdrop, spacing: Dp, modifier: Modifier = Modifier, fusionDistance: Dp = LocalLiquidGlassTokens.current.fusionDistance,
    first: @Composable () -> Unit, second: @Composable () -> Unit,
) = LiquidToolbarPair(backdrop, spacing, true, modifier, fusionDistance, first, second)

/** A disappearing group and its shared glass lobe occupy the same animated bounds. */
@Composable
fun LiquidToolbarPair(
    backdrop: Backdrop, spacing: Dp, firstVisible: Boolean, modifier: Modifier = Modifier,
    fusionDistance: Dp = LocalLiquidGlassTokens.current.fusionDistance,
    first: @Composable () -> Unit, second: @Composable () -> Unit,
) {
    val a = remember { ToolbarPairSlot() }
    val b = remember { ToolbarPairSlot() }
    val density = LocalDensity.current
    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val replacedState = rememberUpdatedState(LocalReplacedMenuSources.current)
    val navigation = LocalNavigationBar.current
    val transition = LocalLiquidGlassTokens.current.replacement
    val firstScale by animateFloatAsState(if (firstVisible) 1f else 0f,
        tween(if (firstVisible) transition.enterMillis else transition.exitMillis), label = "First toolbar group")
    val hasShader = com.kyant.backdrop.isRuntimeShaderSupported()
    val fill = appearance.overlay(LiquidButtonDefaults.SurfaceColor)
    var origin by remember { mutableStateOf(Offset.Zero) }
    fun rect(slot: ToolbarPairSlot) = if (replacedState.value.any { it.toolbar === slot.group }) null
        else slot.visualBounds(origin, density)
    fun outline(): GlassCapsuleShape {
        val left = rect(a)
        val right = rect(b)
        return GlassCapsuleShape(left ?: right ?: Rect.Zero, if (left == null) null else right,
            with(density) { fusionDistance.toPx().coerceAtLeast(0f) })
    }
    Box(modifier) {
        Spacer(Modifier.matchParentSize().layout { measurable, constraints ->
            // The visual layer has bleed for the press and its shadow. Hit targets and layout stay put.
            val bleed = 20.dp.roundToPx()
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val layer = measurable.measure(Constraints.fixed(w + 2 * bleed, h + 2 * bleed))
            layout(w, h) { layer.place(-bleed, -bleed) }
        }.onGloballyPositioned { origin = it.localToRoot(Offset.Zero) }
            .drawBackdrop(backdrop, ::outline,
                effects = {
                    if (navigation) liquidNavBarButtonLens()
                    else liquidSurfaceLens(appearance.controlBlurRadius, appearance, LiquidGlassRole.Control)
                },
                highlight = { rim }, shadow = { LiquidSurfaceShadow },
                onDrawSurface = {
                    if (navigation) drawRect(appearance.navBarButtonFill())
                    else if (!hasShader) drawRect(fill)
                    val brighten = !navigation && !appearance.dark
                    for (slot in listOf(a, b)) {
                        val light = slot.group?.light ?: continue
                        if (light.pressProgress <= 0f) continue
                        val r = rect(slot) ?: continue
                        light.drawLightInLobe(this, r, brightenBackdrop = brighten)
                    }
                }))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing * firstScale)) {
            if (firstScale > 0f) {
                Box(Modifier.layout { measurable, constraints ->
                    val child = measurable.measure(constraints)
                    val scale = firstScale
                    val width = (child.width * scale).roundToInt()
                    layout(width, child.height) {
                        child.placeWithLayer((width - child.width) / 2, 0) { scaleX = scale; scaleY = scale }
                    }
                }) { CompositionLocalProvider(LocalToolbarPairSlot provides a, content = first) }
            }
            CompositionLocalProvider(LocalToolbarPairSlot provides b, content = second)
        }
    }
}
