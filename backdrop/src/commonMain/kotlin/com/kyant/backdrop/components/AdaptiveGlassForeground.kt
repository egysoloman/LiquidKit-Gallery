package com.kyant.backdrop.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import org.intellij.lang.annotations.Language

internal enum class LiquidGlassRole(val id: Float) {
    Control(0f),
    Menu(1f),
    SurfaceRegular(2f),
    SurfaceClear(3f),
    Bar(4f),
}

internal val LocalControlGlassRole = staticCompositionLocalOf { LiquidGlassRole.Control }

@Language("AGSL")
internal const val LiquidGlassToneGlsl = """
float glassQuadCurve(float s, float m, float e, float t) {
    float frac = clamp((m - s) / max(e - s, 0.001), 0.02, 0.98);
    float p = -log2(frac);
    return t <= 0.0 ? s : s + (e - s) * pow(clamp(t, 0.0, 1.0), p);
}

half3 applyLiquidGlassTone(half3 rgb, float dark, float op, float role) {
    if (dark < 0.5) {
        half3 lightVeil = half3(0.988, 0.994, 1.0);
        if (role > 2.5 && role < 3.5) {
            half3 lifted = pow(max(rgb, half3(0.0001)), half3(0.88));
            return mix(lifted, lightVeil, half(0.06 + 0.02 * op));
        }
        float isMenu = (role > 0.5 && role < 1.5) ? 1.0 : 0.0;
        float isReg = (role > 1.5 && role < 2.5) ? 1.0 : 0.0;
        float isBar = role > 3.5 ? 1.0 : 0.0;
        float gammaMid = isMenu > 0.5 ? 0.69 : (isBar > 0.5 ? 0.72 : (isReg > 0.5 ? 0.76 : 0.78));
        float gammaStart = isMenu > 0.5 ? 0.83 : 0.88;
        float gamma = mix(gammaStart, gammaMid, smoothstep(0.0, 0.5, op));
        half3 lifted = pow(max(rgb, half3(0.0001)), half3(gamma));
        half luma = dot(lifted, half3(0.2126, 0.7152, 0.0722));
        float satAdjust = isReg > 0.5
            ? (1.0 - 0.16 * (0.35 + 0.65 * smoothstep(0.0, 0.65, op)))
            : (1.0 + (isMenu > 0.5 ? 0.11 : 0.06) * (1.0 - smoothstep(0.55, 1.0, op)));
        lifted = clamp(mix(half3(luma), lifted, half(satAdjust)), half3(0.0), half3(1.0));
        float b = isBar > 0.5
            ? glassQuadCurve(0.10, 0.42, 0.94, op)
            : (isMenu > 0.5
                ? glassQuadCurve(0.12, 0.34, 0.90, op)
                : (isReg > 0.5 ? glassQuadCurve(0.10, 0.34, 0.86, op) : glassQuadCurve(0.08, 0.27, 0.88, op)));
        return mix(lifted, lightVeil, half(clamp(b, 0.0, 0.96)));
    } else {
        if (role > 2.5 && role < 3.5) {
            half3 lifted = pow(max(rgb, half3(0.0001)), half3(0.90));
            return mix(lifted, half3(0.95, 0.97, 1.0), half(0.055 + 0.02 * op));
        }
        float isMenu = (role > 0.5 && role < 1.5) ? 1.0 : 0.0;
        float isReg = (role > 1.5 && role < 2.5) ? 1.0 : 0.0;
        float isBar = role > 3.5 ? 1.0 : 0.0;
        half luma = dot(rgb, half3(0.2126, 0.7152, 0.0722));
        if (isBar > 0.5) {
            half3 barToned = mix(half3(luma), rgb, half(0.88));
            half3 barVeil = mix(half3(0.145, 0.145, 0.155), half3(0.10, 0.10, 0.11), half(smoothstep(0.45, 1.0, op)));
            float b = glassQuadCurve(0.09, 0.38, 0.88, op);
            return mix(barToned, barVeil, half(clamp(b, 0.0, 0.96)));
        }
        if (isReg > 0.5) {
            // Preserve rich color translucency on Dark Regular glass (e.g. MiniPlayer over artwork).
            rgb = mix(half3(luma), rgb, half(0.90 - 0.08 * smoothstep(0.0, 0.65, op)));
        }
        float floorPeak = isReg > 0.5 ? 0.115 : (isMenu > 0.5 ? 0.058 : 0.050);
        float floorLift = (1.0 - smoothstep(0.02, 0.66, float(luma)))
            * mix(floorPeak * 0.55, floorPeak, smoothstep(0.0, 0.5, op))
            * (1.0 - smoothstep(0.55, 1.0, op));
        float hiDrop = isMenu > 0.5 ? 0.065 : (isReg > 0.5 ? 0.04 : 0.025);
        float hiMul = 1.0 - hiDrop * smoothstep(0.28, 0.72, float(luma)) * smoothstep(0.0, 0.5, op);
        half3 toned = clamp(rgb * half(hiMul) + half3(floorLift, floorLift, floorLift * 1.06), half3(0.0), half3(1.0));
        half3 midVeil = isReg > 0.5 ? half3(0.32, 0.33, 0.36) : (isMenu > 0.5 ? half3(0.30, 0.31, 0.34) : half3(0.40, 0.41, 0.44));
        half3 denseVeil = isMenu > 0.5 ? half3(0.14, 0.14, 0.155) : half3(0.115, 0.115, 0.13);
        half3 darkVeil = mix(midVeil, denseVeil, half(smoothstep(0.45, 1.0, op)));
        float b = isMenu > 0.5
            ? glassQuadCurve(0.07, 0.22, 0.88, op)
            : (isReg > 0.5 ? glassQuadCurve(0.07, 0.18, 0.84, op) : glassQuadCurve(0.06, 0.14, 0.86, op));
        return mix(toned, darkVeil, half(clamp(b, 0.0, 0.96)));
    }
}
"""

@Language("AGSL")
internal const val LiquidGlassToneShaderString = """
uniform shader content;
uniform float dark;
uniform float glassOpacity;
uniform float role;
$LiquidGlassToneGlsl
half4 main(float2 coord) {
    half4 c = content.eval(coord);
    if (c.a <= half(0.001)) return c;
    half3 straight = clamp(c.rgb / c.a, half3(0.0), half3(1.0));
    half3 toned = applyLiquidGlassTone(straight, dark, glassOpacity, role);
    return half4(toned * c.a, c.a);
}
"""

internal fun BackdropEffectScope.liquidGlassTone(appearance: LiquidGlassAppearance, role: LiquidGlassRole) {
    runtimeShaderEffect(
        key = "LiquidGlassTone_v1",
        shaderString = LiquidGlassToneShaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("dark", if (appearance.dark) 1f else 0f)
        setFloatUniform("glassOpacity", appearance.opacity)
        setFloatUniform("role", role.id)
    }
}

/** A fixed palette and a continuous material density, from clear (0) to dense (1). */
@Immutable
data class LiquidGlassAppearance(val dark: Boolean, val opacity: Float = .5f) {
    init { require(opacity in 0f..1f) { "Glass opacity must be between 0 and 1" } }

    val contentColor get() = if (dark) Color.White else Color.Black
    val destructiveColor get() = if (dark) Color(0xffff7773) else Color(0xffb5262d)
    // The opacity control sets material density. Even at zero the optical rim and lens remain visible.
    private val clear = if (dark) Color(0xff8e9098).copy(alpha = .08f) else Color(0xfffdfeff).copy(alpha = .09f)
    private val middle = if (dark) Color(0xff64666e).copy(alpha = .30f) else Color(0xfffafbfd).copy(alpha = .31f)
    private val dense = if (dark) Color(0xff202023).copy(alpha = .88f) else Color(0xfff9fafc).copy(alpha = .88f)
    // Fit one quadratic through the three reference samples. Interpolate premultiplied channels
    // so a nearly transparent light tint cannot wash out the dark palette halfway through a drag.
    private fun channel(start: Float, mid: Float, end: Float): Float =
        start + (end - start) * opacity + 4f * opacity * (1f - opacity) * (mid - (start + end) * .5f)

    val surface: Color = run {
        val alpha = channel(clear.alpha, middle.alpha, dense.alpha)
        fun component(start: Float, mid: Float, end: Float) =
            (channel(start * clear.alpha, mid * middle.alpha, end * dense.alpha) / alpha).coerceIn(0f, 1f)
        Color(component(clear.red, middle.red, dense.red), component(clear.green, middle.green, dense.green),
            component(clear.blue, middle.blue, dense.blue), alpha, ColorSpaces.ExtendedSrgb)
    }
    val blurRadius = channel(1.1f, if (dark) 3.4f else 4.6f, 6.5f).dp
    // Floating controls preserve print and artwork. An open menu already diffuses its backdrop at zero.
    val controlBlurRadius = channel(.6f, 1.2f, 2.2f).dp
    // Calibrated in RenderEffect units against the 10–90% artwork edge under an open menu.
    val menuBlurRadius = channel(15.5f, 19f, 24f).dp

    internal fun menuOverlay(tint: Color): Color {
        val backing = if (dark) {
            val high = ((opacity - .5f) / .5f).coerceIn(0f, 1f)
            Color(
                red = 0x52 / 255f + (0x24 - 0x52) / 255f * high,
                green = 0x54 / 255f + (0x24 - 0x54) / 255f * high,
                blue = 0x5c / 255f + (0x28 - 0x5c) / 255f * high,
            )
        } else Color(0xfffafcff)
        val extraAlpha = if (dark) .06f * (1f - opacity * .65f) else .08f * (1f - opacity * .55f)
        return backing.copy(alpha = extraAlpha).compositeOver(overlay(tint))
    }

    internal fun overlay(tint: Color) = when (tint) {
        Color.Unspecified, Color.Transparent, LiquidButtonDefaults.SurfaceColor -> surface
        else -> tint.compositeOver(surface)
    }

    internal fun customTintOnly(tint: Color): Color? = when (tint) {
        Color.Unspecified, Color.Transparent, LiquidButtonDefaults.SurfaceColor -> null
        else -> tint
    }
}

private val LocalGlassAppearance = compositionLocalOf<LiquidGlassAppearance?> { null }
val LocalLiquidContentColor = staticCompositionLocalOf { Color.Black }
internal val LocalMenuBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun liquidGlassAppearance(): LiquidGlassAppearance =
    LocalGlassAppearance.current ?: LiquidGlassAppearance(isSystemInDarkTheme())

@Composable
fun LiquidGlassTheme(opacity: Float = .5f, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val appearance = remember(dark, opacity) { LiquidGlassAppearance(dark, opacity) }
    CompositionLocalProvider(LocalGlassAppearance provides appearance, LocalLiquidContentColor provides appearance.contentColor, content = content)
}

// Kept for applications built against the original public API.
@Composable
fun LiquidGlassSamplingScope(content: @Composable () -> Unit) {
    if (LocalGlassAppearance.current == null) LiquidGlassTheme(content = content) else content()
}

@Composable
internal fun GlassForeground(modifier: Modifier, content: @Composable BoxScope.(Color) -> Unit) {
    val color = liquidGlassAppearance().contentColor
    Box(modifier, propagateMinConstraints = true) {
        CompositionLocalProvider(LocalLiquidContentColor provides color) { content(color) }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun AdaptiveGlassForeground(backdrop: Backdrop, modifier: Modifier = Modifier, tint: Color = Color.Transparent,
    content: @Composable BoxScope.(Color) -> Unit) = GlassForeground(modifier, content)

/** Contrast of an explicitly provided solid color, for application-owned accents (aligned with iOS HIG). */
fun liquidContentColorFor(background: Color): Color = if (background.luminance() > .55f) Color(0xff0d0d0e) else Color.White
