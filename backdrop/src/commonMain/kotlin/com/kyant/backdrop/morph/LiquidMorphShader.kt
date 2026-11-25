package com.kyant.backdrop.morph

import androidx.compose.ui.geometry.Offset
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.components.GlassCapsuleSDF
import com.kyant.backdrop.components.LiquidGlassAppearance
import com.kyant.backdrop.components.LiquidGlassToneGlsl
import com.kyant.backdrop.components.LiquidRimResponse
import com.kyant.backdrop.effects.runtimeShaderEffect
import org.intellij.lang.annotations.Language

@Language("AGSL")
internal const val LiquidMorphShaderString = """
uniform shader content;

uniform float2 offset;
uniform float4 pillRect;      // xy = center, zw = halfSize
uniform float pillRadius;
uniform float pillActive;

uniform float4 bodyRect;      // xy = center, zw = halfSize
uniform float bodyRadius;
uniform float4 bodyContour;
uniform float bodyCrownBias;
uniform float bodyActive;

uniform float2 shapeCenter;
uniform float smoothK;

uniform float pressGlow;
uniform float2 pressGlowPos;
uniform float pressGlowRadius;

uniform float refraction;
uniform float bevelWidth;
uniform float refractionAmount;
uniform float dispersion;
uniform float specular;
uniform float pixelDensity;
uniform float tintAlpha;
uniform float pale;
uniform float glassOpacity;
uniform float materialProgress;
layout(color) uniform half4 collapsedSurface;
layout(color) uniform half4 expandedSurface;

$GlassCapsuleSDF
float sdRoundedBox(float2 p, float2 b, float r) {
    float minDim = min(b.x, b.y);
    if (r >= minDim - 0.001) return sdGlassCap(p, b);
    float rad = min(r, minDim);
    float2 q = abs(p) - b + float2(rad);
    float2 m = max(q, 0.0);
    float roundness = clamp(rad / max(minDim, 1.0), 0.0, 1.0);
    float kExp = mix(2.28, 2.0, smoothstep(0.55, 0.92, roundness));
    float cornerDist = kExp <= 2.001 ? length(m) : (m.x + m.y > 1e-5)
        ? pow(pow(max(m.x, 1e-6), kExp) + pow(max(m.y, 1e-6), kExp), 1.0 / kExp)
        : 0.0;
    return min(max(q.x, q.y), 0.0) + cornerDist - rad;
}

$LiquidBodyDistance
float sminCubic(float a, float b, float k) {
    if (k <= 0.001) return min(a, b);
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * h * k * (1.0 / 6.0);
}

float liquidMorphSDF(float2 p) {
    float dPill = 1e5;
    float dBody = 1e5;
    if (pillActive > 0.5) {
        dPill = sdRoundedBox(p - pillRect.xy, pillRect.zw, pillRadius);
    }
    if (bodyActive > 0.5) {
        dBody = bodyContour.x == bodyRadius && bodyContour.w == bodyRadius && bodyContour.y == 0.0 && bodyCrownBias == 0.0
            ? sdRoundedBox(p - bodyRect.xy, bodyRect.zw, bodyRadius)
            : bodyDistance(p - bodyRect.xy, bodyRect.zw, bodyRadius, bodyContour, bodyCrownBias);
    }
    return sminCubic(dPill, dBody, smoothK);
}

float2 calcNormal(float2 p) {
    const float e = 1.25;
    float dx = liquidMorphSDF(p + float2(e, 0.0)) - liquidMorphSDF(p - float2(e, 0.0));
    float dy = liquidMorphSDF(p + float2(0.0, e)) - liquidMorphSDF(p - float2(0.0, e));
    float2 g = float2(dx, dy);
    float len = length(g);
    return len > 1e-5 ? g / len : float2(0.0, -1.0);
}

float circleMap(float x) {
    float c = clamp(x, 0.0, 0.999);
    return 1.0 - sqrt(1.0 - c * c);
}

$LiquidRimResponse
$LiquidGlassToneGlsl
half4 main(float2 coord) {
    float2 p = coord + offset;
    float d = liquidMorphSDF(p);

    float mask = 1.0 - smoothstep(-0.85, 0.85, d);
    if (d > pixelDensity * 1.4) return half4(0.0);
    float edgeDist = max(-d, 0.0);
    float bevel = max(bevelWidth, 4.0);
    float2 n = edgeDist < bevel + pixelDensity ? calcNormal(p) : float2(0.0, -1.0);

    // Exact Kyant AndroidLiquidGlass spherical lens refraction (circleMap + radial depthEffect!)
    float2 relCenter = p - shapeCenter;
    float relLen = length(relCenter);
    float2 radialDir = relLen > 1e-3 ? relCenter / relLen : float2(0.0, 0.0);
    float2 refractGrad = normalize(n + 0.35 * radialDir);

    float2 refractPx = float2(0.0);
    float dispFactor = 0.0;
    if (edgeDist < bevel) {
        float x = clamp(1.0 - edgeDist / bevel, 0.0, 1.0);
        float mag = circleMap(x) * refractionAmount * refraction;
        refractPx = -mag * refractGrad;
        dispFactor = dispersion * x * x * 0.18;
    }

    half3 glassRgb;
    if (dispFactor > 0.002) {
        half r = content.eval(coord + refractPx * (1.0 - dispFactor)).r;
        half g = content.eval(coord + refractPx).g;
        half b = content.eval(coord + refractPx * (1.0 + dispFactor)).b;
        glassRgb = half3(r, g, b);
    } else {
        glassRgb = content.eval(coord + refractPx).rgb;
    }

    // Both surface weights come from the same shape transition using luminance-adaptive
    // iOS 27.1 toning between Control (0) and Menu (1), then any explicit caller tint.
    half3 collapsedTone = applyLiquidGlassTone(glassRgb, 1.0 - pale, glassOpacity, 0.0);
    half3 expandedTone = applyLiquidGlassTone(glassRgb, 1.0 - pale, glassOpacity, 1.0);
    glassRgb = mix(collapsedTone, expandedTone, half(clamp(materialProgress, 0.0, 1.0)));
    half customA = clamp(collapsedSurface.a + expandedSurface.a, half(0.0), half(1.0));
    if (customA > half(0.001)) {
        glassRgb = glassRgb * (1.0 - customA) + collapsedSurface.rgb * collapsedSurface.a
            + expandedSurface.rgb * expandedSurface.a;
    }
    glassRgb = mix(glassRgb, half3(1.0), half(tintAlpha * 0.36 * exp(-edgeDist / bevel)));

    float3 rim = rimResponse(-d / pixelDensity, n, min(0.32, 0.22 * specular), pale);
    glassRgb = glassRgb * half(1.0 - rim.x - rim.y) + half3(rim.x + rim.z);

    // Finger light shares the glass mask and catches the bevel as it passes.
    if (pressGlow > 0.001) {
        float distGlow = length(p - pressGlowPos);
        float radial = 1.0 - smoothstep(0.0, pressGlowRadius, distGlow);
        float rim = exp(-edgeDist / max(2.0, bevel * 0.22));
        float bloom = pressGlow * radial * (0.46 + 0.28 * rim);
        glassRgb = mix(glassRgb, half3(1.0), half(bloom));
    }

    glassRgb = clamp(glassRgb, half3(0.0), half3(1.0));
    half outA = half(mix(rim.y, 1.0, mask));
    half3 outRgb = mix(half3(rim.z), glassRgb, half(mask));
    return half4(outRgb, outA);
}
"""

/** Warps the premultiplied menu glyph layer at the moving glass rim. */
@Language("AGSL")
internal const val LiquidMenuContentShader = """
uniform shader content;
uniform float2 halfSize;
uniform float radius;
uniform float4 contour;
uniform float crownBias;
uniform float width;
uniform float displacement;
$LiquidBodyDistance
half4 main(float2 coord) {
    float2 p = coord - halfSize;
    float depth = -bodyDistance(p, halfSize, radius, contour, crownBias);
    if (depth >= width) return content.eval(coord);
    float2 e = float2(0.75, 0.0);
    float2 grad = float2(
        bodyDistance(p + e.xy, halfSize, radius, contour, crownBias) - bodyDistance(p - e.xy, halfSize, radius, contour, crownBias),
        bodyDistance(p + e.yx, halfSize, radius, contour, crownBias) - bodyDistance(p - e.yx, halfSize, radius, contour, crownBias)
    );
    float edge = 1.0 - smoothstep(0.0, width, max(depth, 0.0));
    return content.eval(coord - grad / max(length(grad), 0.001) * (edge * edge * displacement));
}
"""

/**
 * Applies the optical shader to the smooth union of the capsule and droplet.
 */
fun BackdropEffectScope.liquidMorphLens(
    frame: LiquidMorphFrame,
    config: LiquidMorphConfig,
    pressPosition: Offset = Offset(frame.pressGlowX, frame.pressGlowY),
    pressStrength: Float = frame.pressGlow,
) {
    liquidMorphLens(frame, config, pressPosition, pressStrength, LiquidGlassAppearance(dark = false, opacity = 0.5f))
}

internal fun BackdropEffectScope.liquidMorphLens(frame: LiquidMorphFrame, config: LiquidMorphConfig,
    pressPosition: Offset, pressStrength: Float, appearance: LiquidGlassAppearance) {
    val bevelPx = config.bevelWidthDp * density
    val refractAmountPx = config.refractionAmountDp * density
    runtimeShaderEffect(
        key = "LiquidMorph2SDF_v6",
        shaderString = LiquidMorphShaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform(
            "pillRect",
            frame.pill.cx,
            frame.pill.cy,
            frame.pill.w * 0.5f,
            frame.pill.h * 0.5f,
        )
        setFloatUniform("pillRadius", frame.pill.r)
        setFloatUniform("pillActive", if (frame.pill.active) 1f else 0f)

        setFloatUniform(
            "bodyRect",
            frame.body.cx,
            frame.body.cy,
            frame.body.w * 0.5f,
            frame.body.h * 0.5f,
        )
        setFloatUniform("bodyRadius", frame.body.r)
        setFloatUniform("bodyCrownBias", frame.body.crownBias)
        setFloatUniform("bodyContour", frame.body.radiusY, frame.body.tipX, if (frame.body.tipAtBottom) 1f else -1f, frame.body.farRadiusY)
        setFloatUniform("bodyActive", if (frame.body.active) 1f else 0f)

        setFloatUniform("shapeCenter", frame.boundCx, frame.boundCy)
        setFloatUniform("smoothK", frame.smoothK)

        setFloatUniform("pressGlow", pressStrength.coerceIn(0f, 1f))
        setFloatUniform("pressGlowPos", pressPosition.x, pressPosition.y)
        setFloatUniform("pressGlowRadius", frame.pressGlowRadius)

        setFloatUniform("refraction", config.refraction)
        setFloatUniform("bevelWidth", bevelPx)
        setFloatUniform("refractionAmount", refractAmountPx)
        setFloatUniform("dispersion", config.dispersion)
        val settledSpecular = 1.25f + (config.specular - 1.25f) * frame.materialProgress
        setFloatUniform("specular", settledSpecular)
        setFloatUniform("pixelDensity", density)
        setFloatUniform("tintAlpha", config.tintAlpha * frame.materialProgress)
        setFloatUniform("pale", if (appearance.dark) 0f else 1f)
        setFloatUniform("glassOpacity", appearance.opacity)
        setFloatUniform("materialProgress", frame.materialProgress)
        val expandedSurface = config.expandedSurfaceColor
        setColorUniform("expandedSurface", expandedSurface.copy(alpha = expandedSurface.alpha * frame.materialProgress))
        val surface = config.collapsedSurfaceColor
        setColorUniform("collapsedSurface", surface.copy(alpha = surface.alpha * (1f - frame.materialProgress)))
    }
}
