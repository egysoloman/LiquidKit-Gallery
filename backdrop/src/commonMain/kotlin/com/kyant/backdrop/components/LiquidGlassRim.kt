package com.kyant.backdrop.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.internal.RoundedRectSDF
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.highlight.BrushHighlightStyle
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.getCornerRadii
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.pow

internal val LiquidSurfaceShadow = Shadow(radius = 10.dp, offset = DpOffset(0.dp, 3.dp), color = Color.Black.copy(alpha = .028f))

@Composable
internal fun rememberLiquidRimHighlight(
    dark: Boolean, layeredEdge: Boolean = false, reflectionInSurface: Boolean = false,
): Highlight {
    val tokens = LocalLiquidGlassTokens.current
    val runtime = isRuntimeShaderSupported()
    val width = if (reflectionInSurface && !runtime) minOf(tokens.rimWidth, .75.dp) else tokens.rimWidth
    val reflection = if (reflectionInSurface && runtime) 0f else tokens.tabs.rimReflection
    val absorption = tokens.tabs.rimAbsorption
    val fallbackHairline = if (reflectionInSurface && !runtime && !dark) tokens.tabs.topHairlineOpacity else 0f
    return remember(dark, layeredEdge, width, reflection, absorption, fallbackHairline) {
        val style = GlassRimStyle(if (dark) 0f else 1f, layeredEdge, reflection, absorption, fallbackHairline)
        if (runtime) Highlight(width = width, blurRadius = 0.dp, style = style)
        else Highlight(width = width, style = style)
    }
}

// The plate reflects at its top and bottom and absorbs light at its ends.
// Both the material and the overhanging rim evaluate the same continuous contour.
internal const val TabEdgeResponse = """
float2 tabEdgeResponse(float depth, float2 normal) {
    float angle = atan(abs(normal.y), max(abs(normal.x), 0.0001)) * 0.63661977;
    float reflection = pow(angle, 1.85) * exp(-pow(abs((depth - 0.55) / 0.44), 2.0));
    float absorption = (0.025 + 0.975 * pow(1.0 - angle, 1.65)) *
        exp(-pow(abs((depth + 0.18) / 0.34), 2.0));
    return float2(reflection, absorption);
}
"""

// Reflected light, dark-lobe coverage and its premultiplied grey.
internal const val LiquidRimResponse = """
float3 rimResponse(float depthDp, float2 n, float light, float pale) {
    float up = abs(n.y);
    float horizontal = abs(n.x);
    float lightWeight = up * up;
    float sideWeight = pow(horizontal, 1.5);
    // Keep the optical ridge at a continuous depth through the cap-to-flat tangent.
    float brightPeak = mix(0.48, 0.30, lightWeight);
    float width = mix(0.32, 0.24, pale);
    float edgeDistance = (depthDp + 0.04) / width;
    float lightDistance = (depthDp - mix(brightPeak, brightPeak + 0.24, pale)) / width;
    // Luminous white specular ridge: strong on top/bottom, gently continuous around sides.
    float darkRim = light * (0.32 + 0.62 * lightWeight);
    float lightRim = (0.14 + 0.44 * lightWeight);
    float reflected = mix(darkRim, lightRim, pale) * exp(-lightDistance * lightDistance);
    // Crisp neutral-silver outer hairline on pale/light glass (calibrated to iOS 27 Codex buttons).
    float contourAlpha = pale * (0.095 + 0.195 * sideWeight) * exp(-edgeDistance * edgeDistance);
    float edgeGrey = 0.10;
    float coverage = min(1.0 - reflected, contourAlpha);
    return float3(reflected, coverage, edgeGrey * coverage);
}
"""

internal const val RimShader = """
uniform float2 size;
uniform float4 radii;
uniform float pixelDensity;
uniform float pale;
uniform float layeredEdge;
uniform float reflection, absorption;
$GlassCapsuleField
$RoundedRectSDF
$LiquidRimResponse
$TabEdgeResponse
half4 main(float2 point) {
    float2 halfSize = size * 0.5;
    float2 p = point - halfSize;
    float radius = radiusAt(p, radii);
    float d = capA.z > 0.0 ? capDistance(point) : sdRoundedRect(p, halfSize, radius);
    float2 normal = capA.z > 0.0 ? capNormal(point) : gradSdRoundedRect(p, halfSize, radius);
    if (layeredEdge > 0.5) {
        float depthDp = -d / pixelDensity;
        float2 response = tabEdgeResponse(depthDp, normal);
        float capGlow = 0.22 * exp(-pow((depthDp - 0.50) / 0.42, 2.0));
        float white = pale > 0.5
            ? (reflection > 0.001 ? min(1.0, (0.28 + 1.35 * reflection) * max(response.x, capGlow)) : 0.0)
            : (reflection > 0.001 ? min(1.0, (0.14 + 1.10 * reflection * response.x) * max(response.x, capGlow * 0.85)) : 0.05 * capGlow);
        float dark = min(0.24, 0.38 * absorption * response.y) * pale;
        float edgeGrey = 0.82 * pale;
        float alpha = white + (1.0 - white) * dark;
        return half4(half3(white + (1.0 - white) * dark * edgeGrey), alpha);
    }
    float3 rim = rimResponse(-d / pixelDensity, normal, 0.275, pale);
    return half4(half3(rim.x + rim.z), rim.x + rim.y);
}
"""

/** One optical stroke. Its sign changes continuously as the normal turns through each corner. */
private data class GlassRimStyle(
    val pale: Float, val layeredEdge: Boolean, val reflection: Float, val absorption: Float,
    val fallbackHairline: Float,
) : BrushHighlightStyle {
    override val color = Color.White
    override val blendMode = DrawScope.DefaultBlendMode
    override fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader =
        runtimeShaderCache.obtainRuntimeShader("Directional glass rim", RimShader).apply {
            setFloatUniform("size", size.width, size.height)
            setCapsules(shape, size)
            setFloatUniform("radii", getCornerRadii(shape))
            setFloatUniform("pixelDensity", density)
            setFloatUniform("pale", pale)
            setFloatUniform("layeredEdge", if (layeredEdge) 1f else 0f)
            setFloatUniform("reflection", reflection)
            setFloatUniform("absorption", absorption)
        }

    override fun DrawScope.outlineBrush(shape: Shape): Brush {
        val corner = (getCornerRadii(shape)[0] / size.height.coerceAtLeast(1f)).coerceIn(.001f, .5f)
        fun tint(verticalNormal: Float): Color {
            val v = verticalNormal
            if (layeredEdge) {
                val angle = kotlin.math.asin(v.coerceIn(0f, 1f)) * (2f / kotlin.math.PI.toFloat())
                val rawReflect = angle.pow(1.85f)
                val capFloor = 0.22f
                val shine = if (pale > .5f) {
                    if (reflection > .001f) ((0.28f + 1.35f * reflection) * maxOf(rawReflect, capFloor)).coerceIn(0f, 1f) else 0f
                } else {
                    if (reflection > .001f) ((0.14f + 1.10f * reflection * rawReflect) * maxOf(rawReflect, capFloor * 0.85f)).coerceIn(0f, 1f) else 0.05f * capFloor
                }
                val rawAbsorb = (1f - angle).pow(1.65f)
                val shade = (((0.38f * absorption * rawAbsorb).coerceAtMost(0.24f) + fallbackHairline * v) * pale).coerceIn(0f, 1f)
                val edgeGrey = 0.82f * pale
                val alpha = shine + (1f - shine) * shade
                val light = if (alpha == 0f) 0f else (shine + (1f - shine) * shade * edgeGrey) / alpha
                return Color(light, light, light, alpha)
            }
            val lightWeight = v * v
            val sideWeight = (1f - v * v).pow(.75f)
            val darkRim = 0.275f * (0.32f + 0.62f * lightWeight)
            val lightRim = 0.14f + 0.44f * lightWeight
            val gain = darkRim + (lightRim - darkRim) * pale
            val contourAlpha = pale * (0.095f + 0.195f * sideWeight)
            val edgeGrey = 0.10f
            val coverage = contourAlpha.coerceAtMost(1f - gain)
            val a = gain + coverage
            val gray = if (a > 0f) (gain + edgeGrey * coverage) / a else 0f
            return Color(gray, gray, gray, a)
        }
        val turns = floatArrayOf(0f, .15f, .2929f, .5f, .75f, 1f)
        val stops = buildList {
            for (t in turns) add(t * corner to tint(1f - t))
            for (i in turns.lastIndex downTo 0) {
                val t = turns[i]
                if (t < 1f || corner < .5f) add(1f - t * corner to tint(1f - t))
            }
        }
        return Brush.verticalGradient(*stops.toTypedArray())
    }
}
