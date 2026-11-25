package com.kyant.backdrop.morph

import androidx.compose.ui.geometry.boundingRect
import androidx.compose.ui.geometry.Offset
import com.kyant.backdrop.components.addLiquidCapsule
import com.kyant.backdrop.components.capsuleDistance
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow

/** Pixel distance for elliptical corners and a thinning tip. Both optical and shadow shaders use this field. */
internal const val LiquidBodyDistance = """
float bodyDistance(float2 p, float2 halfSize, float rx, float4 contour, float crownBias) {
    float2 b = max(halfSize, float2(0.001));
    float a = contour.z * p.y / b.y;
    float c = clamp(a, -1.0, 1.0);
    float u = max(c, 0.0);
    // Continue linearly beyond each pole so the optical normal also agrees across the rim.
    float bend = u * u * (u + 3.0 * max(a - 1.0, 0.0));
    float warped = a + crownBias * (1.0 - c * c - 2.0 * c * (a - c));
    p.x -= contour.y * bend;
    p.y = contour.z * b.y * warped;
    float2 r = clamp(float2(rx, warped >= 0.0 ? contour.x : contour.w), float2(0.001), b);
    float yScale = r.x / r.y;
    float2 q = (abs(p) - b + r) * float2(1.0, yScale);
    float2 m = max(q, 0.0);
    float roundness = min(r.x / b.x, r.y / b.y);
    float k = mix(2.28, 2.0, smoothstep(0.55, 0.92, roundness));
    float d = min(max(q.x, q.y), 0.0) - r.x;
    float2 n = m.x + m.y < 0.001
        ? (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0))
        : m / length(m);
    if (k <= 2.001) {
        d += length(m);
    } else if (m.x + m.y >= 0.001) {
        float norm = pow(pow(m.x, k) + pow(m.y, k), 1.0 / k);
        d += norm;
        n = pow(m / norm, float2(k - 1.0));
    }
    n *= mix(float2(-1.0), float2(1.0), step(float2(0.0), p));
    float shear = contour.z * contour.y * 3.0 * u * u / b.y;
    n.y = n.y * yScale * (1.0 - 2.0 * crownBias * c) - n.x * shear;
    return d / max(length(n), 0.001);
}
"""

private fun SdfBoxPrimitive.cornerPower(ry: Float): Float {
    val roundness = min(r / (w * .5f), ry / (h * .5f))
    val t = ((roundness - .55f) / .37f).coerceIn(0f, 1f)
    return 2.28f - .28f * t * t * (3f - 2f * t)
}

/** The horizontal half-width before bending the source-facing tip. */
private fun SdfBoxPrimitive.halfSpan(y: Float, ry: Float, power: Float): Float {
    val vertical = ((abs(y) - h * .5f + ry) / ry.coerceAtLeast(.001f)).coerceIn(0f, 1f)
    return w * .5f - r + r * (1f - vertical.pow(power)).coerceAtLeast(0f).pow(1f / power)
}

private inline fun <T> SdfBoxPrimitive.spanAt(y: Float, span: (Float, Float) -> T): T {
    val axial = if (tipAtBottom) y else -y
    val a = axial / (h * .5f)
    val c = a.coerceIn(-1f, 1f)
    val u = c.coerceAtLeast(0f)
    val bend = u * u * (u + 3f * (a - 1f).coerceAtLeast(0f))
    val warped = a + crownBias * (1f - c * c - 2f * c * (a - c))
    val ry = if (warped >= 0f) radiusY else farRadiusY
    return span(tipX * bend, halfSpan(warped * h * .5f, ry, cornerPower(ry)))
}

internal fun SdfBoxPrimitive.containsPoint(at: Offset, outset: Float): Boolean {
    if (!active) return false
    if (hasUniformCorners && r >= min(w, h) / 2f - .001f)
        return capsuleDistance(at, roundRect().boundingRect) <= outset
    val y = at.y - cy
    if (abs(y) > h * .5f + outset) return false
    return spanAt(y) { shift, half -> abs(at.x - cx - shift) <= half + outset }
}

internal fun Path.addMorphBody(body: SdfBoxPrimitive) = with(body) {
    if (hasUniformCorners) {
        if (r >= min(w, h) / 2f - .001f) addLiquidCapsule(roundRect().boundingRect) else addRoundRect(roundRect())
        return@with
    }
    // Cosine-spaced samples retain the curved poles even while the aspect ratio changes.
    val steps = 64
    for (i in 0..2 * steps + 1) {
        val right = i <= steps
        val index = if (right) i else 2 * steps + 1 - i
        val y = -h * .5f * cos(PI * index / steps).toFloat()
        spanAt(y) { shift, half ->
            val x = cx + shift + if (right) half else -half
            if (i == 0) moveTo(x, cy + y) else lineTo(x, cy + y)
        }
    }
    close()
}
