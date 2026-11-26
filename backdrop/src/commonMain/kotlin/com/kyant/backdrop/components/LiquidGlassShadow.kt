package com.kyant.backdrop.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.morph.LiquidMorphFrame
import com.kyant.backdrop.morph.LiquidBodyDistance
import com.kyant.backdrop.morph.SdfBoxPrimitive
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.ShadowElement

/** Draw the penumbra directly so folding a panel doesn't rasterize a new large blur on every frame. */
@Composable
internal fun Modifier.liquidGlassShadow(
    shape: Shape, elevation: Float, radius: Dp = 0.dp, frame: LiquidMorphFrame? = null,
): Modifier {
    val p = elevation.coerceIn(0f, 1f)
    val dark = liquidGlassAppearance().dark
    val alpha = if (dark) .06f + (.32f - .06f) * p else .016f + (.12f - .016f) * p
    val offset = (4f + 3.75f * p).dp
    if (!isRuntimeShaderSupported()) {
        val shadow = Shadow(radius = (9f + 20f * p).dp, offset = DpOffset(0.dp, offset), color = Color.Black.copy(alpha = alpha))
        return then(ShadowElement(ShapeProvider { shape }) { shadow })
    }
    val shader = remember { RuntimeShader(ShadowShader) }
    return drawWithContent {
        fun lobe(name: String, box: SdfBoxPrimitive?) {
            if (box != null && box.active) {
                shader.setFloatUniform(name, box.cx, box.cy, box.w * .5f, box.h * .5f)
            } else shader.setFloatUniform(name, 0f, 0f, -1f, -1f)
        }
        if (frame == null) {
            shader.setFloatUniform("a", size.width / 2f, size.height / 2f, size.width / 2f, size.height / 2f)
            lobe("b", null)
        } else {
            lobe("a", frame.pill)
            lobe("b", frame.body)
        }
        shader.setFloatUniform("radii", frame?.pill?.r ?: radius.toPx(), frame?.body?.r ?: 0f)
        val body = frame?.body
        shader.setFloatUniform("bodyContour", body?.radiusY ?: 0f, body?.tipX ?: 0f, if (body?.tipAtBottom == true) 1f else -1f, body?.farRadiusY ?: 0f)
        shader.setFloatUniform("bodyCrownBias", body?.crownBias ?: 0f)
        shader.setFloatUniform("join", frame?.smoothK ?: 0f)
        val sigma = (5f + 12f * p).dp.toPx()
        val shift = offset.toPx()
        shader.setFloatUniform("shadow", sigma, shift, alpha)
        val margin = 3.5f * sigma + shift
        // Build ShaderBrush after updating uniforms so Skiko snapshots current uniform values instead of all zeros.
        drawRect(ShaderBrush(shader.asComposeShader()), Offset(-margin, -margin), Size(size.width + 2f * margin, size.height + 2f * margin))
        drawContent()
    }
}

internal const val ShadowShader = """
uniform float4 a;
uniform float4 b;
uniform float2 radii;
uniform float4 bodyContour;
uniform float bodyCrownBias;
uniform float join;
uniform float3 shadow;
float boxDistance(float2 p, float4 rect, float radius) {
    if (rect.z < 0.0) return 100000.0;
    float r = min(radius, min(rect.z, rect.w));
    float2 q = abs(p - rect.xy) - rect.zw + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
$LiquidBodyDistance
float distanceToGlass(float2 xy) {
    float da = boxDistance(xy, a, radii.x);
    float db = b.z < 0.0 ? 100000.0 : bodyDistance(xy - b.xy, b.zw, radii.y, bodyContour, bodyCrownBias);
    float h = max(join - abs(da-db), 0.0) / max(join, 0.001);
    return min(da,db) - h*h*h*join/6.0;
}
half4 main(float2 xy) {
    float d = distanceToGlass(xy);
    if (d < -1.0 || d > 3.5 * shadow.x + shadow.y) return half4(0.0);
    float z = distanceToGlass(xy - float2(0.0, shadow.y)) / shadow.x;
    float tail = 1.0 / (1.0 + exp(clamp(1.59577 * (z + 0.044715*z*z*z), -20.0, 20.0)));
    return half4(0.0, 0.0, 0.0, shadow.z * tail * smoothstep(-0.8, 0.8, d));
}
"""
