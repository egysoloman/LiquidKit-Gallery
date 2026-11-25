package com.kyant.backdrop.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.interaction.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRuntimeShaderSupported

object LiquidButtonDefaults {
    val SurfaceColor = Color.White.copy(alpha = .30f)
}

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()

    val tokens = LocalLiquidGlassTokens.current
    val interactiveHighlight = remember(animationScope, tokens) {
        InteractiveHighlight(
            animationScope, tokens.press, intensity = tokens.highlightIntensity
        )
    }

    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val hasShader = isRuntimeShaderSupported()
    val fill = if (hasShader) appearance.customTintOnly(surfaceColor) else appearance.overlay(surfaceColor)
    GlassForeground(
        modifier
            .then(if (isInteractive) interactiveHighlight.gestureModifier else Modifier)
            .then(if (isInteractive) Modifier.graphicsLayer { interactiveHighlight.motion.apply(this) } else Modifier)
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { LiquidCapsule },
                highlight = { rim }, shadow = { LiquidSurfaceShadow },
                effects = { liquidSurfaceLens(appearance.controlBlurRadius, appearance, LiquidGlassRole.Control) },
                onDrawSurface = {
                    if (fill != null) drawRect(fill)
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (isInteractive) interactiveHighlight.drawLight(this, brightenBackdrop = !appearance.dark)
                }
            )
            .height(48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun LiquidButtonText(text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default) {
    BasicText(text, modifier, style = if (style.color.isSpecified) style else style.copy(color = LocalLiquidContentColor.current))
}

internal fun BackdropEffectScope.liquidSurfaceLens(radius: Dp) {
    vibrancy()
    blur(radius.toPx())
    lens(12.dp.toPx(), 24.dp.toPx())
}

internal fun BackdropEffectScope.liquidSurfaceLens(
    radius: Dp,
    appearance: LiquidGlassAppearance,
    role: LiquidGlassRole = LiquidGlassRole.Control,
) {
    vibrancy()
    blur(radius.toPx())
    lens(12.dp.toPx(), 24.dp.toPx())
    liquidGlassTone(appearance, role)
}

internal fun BackdropEffectScope.liquidNavBarButtonLens() {
    vibrancy()
    blur(7.dp.toPx(), TileMode.Clamp)
    lens(6.dp.toPx(), 12.dp.toPx())
}

internal fun LiquidGlassAppearance.navBarButtonFill(customTint: Color? = null): Color {
    val base = if (dark) {
        Color(0xFF2A2A2E).copy(alpha = lerp(.48f, .80f, opacity))
    } else {
        Color(0xFFFCFDFF).copy(alpha = lerp(.46f, .80f, opacity))
    }
    return if (customTint != null) customTint.compositeOver(base) else base
}
