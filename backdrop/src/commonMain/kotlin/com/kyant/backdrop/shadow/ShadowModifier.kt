package com.kyant.backdrop.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.blur
import kotlin.math.ceil

internal class ShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> Shadow?
) : ModifierNodeElement<ShadowNode>() {

    override fun create(): ShadowNode {
        return ShadowNode(shapeProvider, shadow)
    }

    override fun update(node: ShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShadowElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class ShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> Shadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null

    private val paint = Paint()

    private var cachedShadowSize = IntSize.Zero
    private var cachedOutline: androidx.compose.ui.graphics.Outline? = null
    private var cachedColor = androidx.compose.ui.graphics.Color.Unspecified
    private var cachedRadius = Float.NaN
    private var cachedOffsetX = Float.NaN
    private var cachedOffsetY = Float.NaN

    override fun ContentDrawScope.draw() {
        val shadow = shadow()
        if (shadow == null || shadow.alpha <= 0.001f || shadow.color.alpha <= 0.001f) {
            return drawContent()
        }

        val shadowLayer = shadowLayer
        if (shadowLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val radius = shadow.radius.toPx()
            val offsetX = shadow.offset.x.toPx()
            val offsetY = shadow.offset.y.toPx()
            val shadowSize = IntSize(
                bucket32(ceil(size.width + radius * 4f + kotlin.math.abs(offsetX)).toInt()),
                bucket32(ceil(size.height + radius * 4f + kotlin.math.abs(offsetY)).toInt())
            )
            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)

            val needsRecord =
                cachedShadowSize != shadowSize ||
                    cachedOutline != outline ||
                    cachedColor != shadow.color ||
                    cachedRadius != radius ||
                    cachedOffsetX != offsetX ||
                    cachedOffsetY != offsetY

            if (needsRecord) {
                if (cachedColor != shadow.color) {
                    paint.color = shadow.color
                    cachedColor = shadow.color
                }
                if (cachedRadius != radius) {
                    paint.blur(radius)
                    cachedRadius = radius
                }
                shadowLayer.record(shadowSize) {
                    translate(radius * 2f + offsetX, radius * 2f + offsetY) {
                        val canvas = drawContext.canvas
                        canvas.drawOutline(outline, paint)
                        canvas.translate(-offsetX, -offsetY)
                        canvas.drawOutline(outline, ShadowMaskPaint)
                        canvas.translate(offsetX, offsetY)
                    }
                }
                cachedShadowSize = shadowSize
                cachedOutline = outline
                cachedOffsetX = offsetX
                cachedOffsetY = offsetY
            }

            shadowLayer.alpha = shadow.alpha
            shadowLayer.blendMode = shadow.blendMode

            translate(-radius * 2f, -radius * 2f) {
                drawLayer(shadowLayer)
            }
        }

        drawContent()
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer =
            graphicsContext.createGraphicsLayer().apply {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        invalidateCache()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
        invalidateCache()
    }

    private fun invalidateCache() {
        cachedShadowSize = IntSize.Zero
        cachedOutline = null
        cachedColor = androidx.compose.ui.graphics.Color.Unspecified
        cachedRadius = Float.NaN
        cachedOffsetX = Float.NaN
        cachedOffsetY = Float.NaN
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}

private fun bucket32(v: Int): Int = (v.coerceAtLeast(1) + 31) and 31.inv()
