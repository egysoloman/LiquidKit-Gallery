package com.kyant.backdrop.highlight

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.RuntimeShaderCache
import com.kyant.backdrop.internal.AmbientHighlightShaderString
import com.kyant.backdrop.internal.DefaultHighlightShaderString
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.PI

@Immutable
interface HighlightStyle {

    val color: Color

    val blendMode: BlendMode

    fun DrawScope.createShader(
        shape: Shape,
        runtimeShaderCache: RuntimeShaderCache
    ): RuntimeShader?

    @Immutable
    data class Plain(
        override val color: Color = Color.White.copy(alpha = 0.38f),
        override val blendMode: BlendMode = BlendMode.Plus
    ) : HighlightStyle {

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? = null
    }

    @Immutable
    data class Default(
        override val color: Color = Color.White.copy(alpha = 0.5f),
        override val blendMode: BlendMode = BlendMode.Plus,
        val angle: Float = 45f,
        @param:FloatRange(from = 0.0) val falloff: Float = 1f
    ) : HighlightStyle {

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? {
            return if (isRuntimeShaderSupported()) {
                runtimeShaderCache.obtainRuntimeShader(
                    "Default",
                    DefaultHighlightShaderString
                ).apply {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("cornerRadii", getCornerRadii(shape))
                    setColorUniform("color", color.copy(alpha = 1f))
                    setFloatUniform("angle", angle * (PI / 180f).toFloat())
                    setFloatUniform("falloff", falloff)
                }
            } else {
                null
            }
        }
    }

    @Immutable
    data class Ambient(
        @param:FloatRange(from = 0.0, to = 1.0) val intensity: Float = 0.38f
    ) : HighlightStyle {

        override val color: Color = Color.White.copy(alpha = intensity)

        override val blendMode: BlendMode = DrawScope.DefaultBlendMode

        override fun DrawScope.createShader(
            shape: Shape,
            runtimeShaderCache: RuntimeShaderCache
        ): RuntimeShader? {
            return if (isRuntimeShaderSupported()) {
                runtimeShaderCache.obtainRuntimeShader(
                    "Ambient",
                    AmbientHighlightShaderString
                ).apply {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("cornerRadii", getCornerRadii(shape))
                    setFloatUniform("angle", 45f * (PI / 180f).toFloat())
                    setFloatUniform("falloff", 1f)
                }
            } else {
                null
            }
        }
    }

    companion object {

        @Stable
        val Default: Default = Default()

        @Stable
        val Ambient: Ambient = Ambient()

        @Stable
        val Plain: Plain = Plain()
    }
}

internal fun DrawScope.getCornerRadii(shape: Shape): FloatArray {
    val maxRadius = size.minDimension / 2f
    if (shape is com.kyant.backdrop.components.GlassCapsuleShape)
        return FloatArray(4) { shape.firstRect(size).minDimension / 2f }
    if (shape is com.kyant.backdrop.components.GlassCapsulesShape)
        return FloatArray(4) { (shape.rects.firstOrNull()?.minDimension ?: size.minDimension) / 2f }
    return when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> FloatArray(4)
        is Outline.Rounded -> with(outline.roundRect) {
            floatArrayOf(
                topLeftCornerRadius.x.coerceAtMost(maxRadius),
                topRightCornerRadius.x.coerceAtMost(maxRadius),
                bottomRightCornerRadius.x.coerceAtMost(maxRadius),
                bottomLeftCornerRadius.x.coerceAtMost(maxRadius)
            )
        }
        is Outline.Generic -> FloatArray(4) { maxRadius }
    }
}
