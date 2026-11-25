package com.kyant.backdrop.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.components.GlassPressTokens
import com.kyant.backdrop.components.addLiquidCapsule
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class InteractiveHighlight internal constructor(
    val animationScope: CoroutineScope,
    private val pressTokens: GlassPressTokens,
    private val intensity: Float = 1f,
    private val radiusScale: Float = 1.5f,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    constructor(
        animationScope: CoroutineScope, intensity: Float = 1f, radiusScale: Float = 1.5f,
        position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    ) : this(animationScope, GlassPressTokens(), intensity, radiusScale, position)

    internal val motion = LiquidPressMotion(animationScope, pressTokens)

    private val positionAnimationSpec =
        pressTokens.highlightOffset.specWithThreshold(Offset.VisibilityThreshold)

    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = motion.highlightProgress
    val offset: Offset get() = positionAnimation.value - startPosition

    private val supportsShader = isRuntimeShaderSupported()
    private var cachedShader: RuntimeShader? = null
    private val lobePath = Path()

    private fun obtainShader(): RuntimeShader? {
        if (!supportsShader) return null
        return cachedShader ?: SharedRenderEffectShaderCache.obtainRuntimeShader(
            "InteractiveHighlight",
            InteractiveHighlightShaderString,
        ).also { cachedShader = it }
    }

    val modifier: Modifier =
        Modifier.drawWithContent {
            drawLight(this)
            drawContent()
        }

    internal fun drawLight(scope: DrawScope, brightenBackdrop: Boolean = false) = with(scope) {
        val progress = pressProgress
        if (progress > 0f) {
            val amount = (intensity * progress).coerceAtLeast(0f)
            val radius = (size.minDimension * radiusScale).coerceAtLeast(1f)
            val point = position(size, positionAnimation.value)
            val center = Offset(point.x.fastCoerceIn(0f, size.width), point.y.fastCoerceIn(0f, size.height))
            // Use clean SrcOver white highlights; ColorDodge produces premultiplied black overflow over semi-transparent gaps.
            val ambient = Color.White.copy(
                alpha = ((if (brightenBackdrop) 0.065f else pressTokens.ambientHighlightAlpha) * amount).coerceIn(0f, 1f),
            )
            val spot = Color.White.copy(
                alpha = ((if (brightenBackdrop) 0.115f else pressTokens.spotHighlightAlpha) * amount).coerceIn(0f, 1f),
            )
            val activeShader = obtainShader()
            if (activeShader != null) {
                drawRect(ambient)
                activeShader.apply {
                    setFloatUniform("size", size.width, size.height)
                    setColorUniform("color", spot)
                    setFloatUniform("radius", radius)
                    setFloatUniform("position", center.x, center.y)
                }
                drawRect(ShaderBrush(activeShader.asComposeShader()))
            } else {
                drawRect(ambient)
                drawRect(Brush.radialGradient(listOf(spot, Color.Transparent), center, radius))
            }
        }
    }

    /** Renders press illumination strictly inside the pressed capsule [rect] without spilling into adjacent lobes. */
    internal fun drawLightInLobe(
        scope: DrawScope,
        rect: Rect,
        brightenBackdrop: Boolean = false,
    ) {
        with(scope) {
            val progress = pressProgress
            if (progress <= 0f || rect.isEmpty) return@with
            lobePath.rewind()
            lobePath.addLiquidCapsule(rect)
            clipPath(lobePath) {
                inset(rect.left, rect.top, size.width - rect.right, size.height - rect.bottom) {
                    drawLight(this, brightenBackdrop)
                }
            }
        }
    }

    val gestureModifier: Modifier =
        Modifier.pointerInput(this@InteractiveHighlight) {
            inspectDragGestures(
                onDragStart = { pressToolbar(it.position) },
                onDragEnd = { releaseToolbar() },
                onDragCancel = { releaseToolbar(completed = false) },
            ) { change, _ ->
                moveToolbar(change.position)
            }
        }

    internal fun pressToolbar(at: Offset) {
        motion.begin()
        startPosition = at
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) { positionAnimation.snapTo(at) }
    }

    internal fun moveToolbar(at: Offset) {
        motion.drag(at - startPosition)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) { positionAnimation.snapTo(at) }
    }

    internal fun releaseToolbar(completed: Boolean = true) {
        motion.release(completed)
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            positionAnimation.animateTo(startPosition, positionAnimationSpec)
        }
    }
}

internal const val InteractiveHighlightShaderString = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return half4(color.rgb * color.a, color.a) * intensity;
}"""
