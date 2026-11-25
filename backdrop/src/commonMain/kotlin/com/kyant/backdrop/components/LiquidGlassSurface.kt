package com.kyant.backdrop.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.interaction.InteractiveHighlight
import com.kyant.backdrop.isRuntimeShaderSupported

/** Matches iOS 26/27 SwiftUI `Glass` variants: `.regular`, `.clear`, and `.identity`. */
enum class LiquidGlassVariant { Regular, Clear, Identity }

/**
 * Liquid Glass surface sampling content behind it, mirroring SwiftUI's
 * `.glassEffect(.regular / .clear / .identity)` with optional `.tint(...)` and `.interactive()`.
 */
@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = LiquidCapsule,
    variant: LiquidGlassVariant = LiquidGlassVariant.Regular,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (variant == LiquidGlassVariant.Identity) {
        val tapMod = if (onClick != null) {
            Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
        Box(modifier.then(tapMod), propagateMinConstraints = true, content = content)
        return
    }
    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val strength = appearance.opacity
    val regular = variant == LiquidGlassVariant.Regular
    val hasShader = isRuntimeShaderSupported()
    val backing = when {
        !regular -> Color.White.copy(alpha = if (appearance.dark) .055f else .095f)
        appearance.dark -> Color(0xff5a5c64).copy(alpha = .16f + .56f * strength)
        else -> Color.White.copy(alpha = .12f + .24f * strength + .20f * strength * strength)
    }
    val scope = rememberCoroutineScope()
    val tokens = LocalLiquidGlassTokens.current
    val highlight = if (interactive) {
        remember(scope, tokens) {
            InteractiveHighlight(scope, tokens.press, intensity = tokens.highlightIntensity)
        }
    } else {
        null
    }
    val interactionMod = when {
        highlight != null && onClick != null ->
            highlight.gestureModifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        highlight != null -> highlight.gestureModifier
        onClick != null -> Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    val motionMod = if (highlight != null) Modifier.graphicsLayer { highlight.motion.apply(this) } else Modifier
    val hasTint = tint.isSpecified && tint.alpha > 0f
    val chromaticTint = hasTint && (maxOf(tint.red, tint.green, tint.blue) - minOf(tint.red, tint.green, tint.blue)) > 0.05f
    Box(
        modifier
            .then(interactionMod)
            .then(motionMod)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(if (regular) (appearance.controlBlurRadius * 1.6f).toPx() else 1.8.dp.toPx())
                    val minDimPx = minOf(size.width, size.height)
                    val refractH = minOf(10.dp.toPx(), minDimPx * 0.22f).coerceAtLeast(2f)
                    val refractA = minOf(18.dp.toPx(), minDimPx * 0.34f).coerceAtLeast(4f)
                    lens(refractH, refractA, depthEffect = false, chromaticAberration = true)
                    liquidGlassTone(appearance, if (regular) LiquidGlassRole.SurfaceRegular else LiquidGlassRole.SurfaceClear)
                },
                highlight = { rim },
                shadow = if (interactive) { { LiquidSurfaceShadow } } else null,
                onDrawSurface = {
                    if (!hasShader) drawRect(backing)
                    if (hasTint) {
                        if (chromaticTint && tint.alpha >= 0.35f) {
                            val tintAlpha = if (regular) {
                                if (appearance.dark) .36f else .28f
                            } else {
                                if (appearance.dark) .22f else .18f
                            }
                            drawRect(tint.copy(alpha = 1f), blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = (tint.alpha * tintAlpha).coerceIn(0f, 1f)))
                        } else {
                            drawRect(tint)
                        }
                    }
                    highlight?.drawLight(this, brightenBackdrop = !appearance.dark)
                },
            ),
        propagateMinConstraints = true,
        content = content,
    )
}
