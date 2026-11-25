package com.kyant.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.blur
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.internal.setRuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal interface BrushHighlightStyle : HighlightStyle {
    fun DrawScope.outlineBrush(shape: Shape): Brush
}

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private var cachedSafeSize = IntSize.Zero
    private var cachedDrawSize = androidx.compose.ui.geometry.Size.Unspecified
    private var cachedOutline: Outline? = null
    private var cachedInnerShape: Shape? = null
    private var cachedStyle: HighlightStyle? = null
    private var cachedStrokeWidth = Float.NaN
    private var cachedBlurRadius = Float.NaN
    private var cachedMargin = -1
    private var cachedOuterRim = false

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f || highlight.alpha <= 0.001f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val style = highlight.style
            val outerRim = style is BrushHighlightStyle && isRuntimeShaderSupported()
            val margin = if (outerRim) ceil(highlight.width.toPx()).toInt() else 1
            val safeSize =
                IntSize(
                    bucket32(ceil(size.width).toInt() + 2 * margin),
                    bucket32(ceil(size.height).toInt() + 2 * margin)
                )

            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
            val innerShape = shapeProvider.innerShape
            val strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
            val blurRadius = highlight.blurRadius.toPx()

            val needsRecord =
                cachedSafeSize != safeSize ||
                    cachedDrawSize != size ||
                    cachedOutline != outline ||
                    cachedInnerShape != innerShape ||
                    cachedStyle != style ||
                    cachedStrokeWidth != strokeWidth ||
                    cachedBlurRadius != blurRadius ||
                    cachedMargin != margin ||
                    cachedOuterRim != outerRim

            if (needsRecord) {
                val clipPath =
                    if (outline is Outline.Rounded) {
                        clipPath ?: Path().also { clipPath = it }
                    } else {
                        null
                    }

                configurePaint(highlight, innerShape, strokeWidth, blurRadius)

                highlightLayer.record(safeSize) {
                    translate(margin.toFloat(), margin.toFloat()) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        if (!outerRim) canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
                cachedSafeSize = safeSize
                cachedDrawSize = size
                cachedOutline = outline
                cachedInnerShape = innerShape
                cachedStyle = style
                cachedStrokeWidth = strokeWidth
                cachedBlurRadius = blurRadius
                cachedMargin = margin
                cachedOuterRim = outerRim
            }

            highlightLayer.alpha = highlight.alpha
            highlightLayer.blendMode = style.blendMode

            translate(-margin.toFloat(), -margin.toFloat()) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
        invalidateCache()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        invalidateCache()
    }

    private fun invalidateCache() {
        cachedSafeSize = IntSize.Zero
        cachedDrawSize = androidx.compose.ui.geometry.Size.Unspecified
        cachedOutline = null
        cachedInnerShape = null
        cachedStyle = null
        cachedStrokeWidth = Float.NaN
        cachedBlurRadius = Float.NaN
        cachedMargin = -1
        cachedOuterRim = false
    }

    private fun DrawScope.configurePaint(
        highlight: Highlight,
        innerShape: Shape,
        strokeWidth: Float,
        blurRadius: Float,
    ) {
        paint.color = highlight.style.color
        paint.strokeWidth = strokeWidth
        if (cachedBlurRadius != blurRadius) {
            paint.blur(blurRadius)
        }
        val style = highlight.style
        val shader = if (isRuntimeShaderSupported()) with(style) {
            createShader(innerShape, SharedRenderEffectShaderCache)
        } else null
        if (shader == null && style is BrushHighlightStyle) {
            with(style) { outlineBrush(innerShape).applyTo(size, paint, 1f) }
        } else {
            paint.setRuntimeShader(shader)
        }
    }
}

private fun bucket32(v: Int): Int = (v.coerceAtLeast(1) + 31) and 31.inv()
