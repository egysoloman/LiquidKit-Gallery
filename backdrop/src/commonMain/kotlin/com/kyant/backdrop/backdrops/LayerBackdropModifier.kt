package com.kyant.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.kyant.backdrop.internal.recordLayer

fun Modifier.layerBackdrop(backdrop: LayerBackdrop, enabled: Boolean = true): Modifier =
    this then LayerBackdropElement(backdrop, drawRecording = false, enabled = enabled)

/** Full-viewport menu layers display exactly the same recording that the next pane refracts. */
internal fun Modifier.menuBackdrop(backdrop: LayerBackdrop, enabled: Boolean = true): Modifier =
    this then LayerBackdropElement(backdrop, drawRecording = true, enabled = enabled)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val drawRecording: Boolean,
    val enabled: Boolean,
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        val node = LayerBackdropNode(backdrop, drawRecording, enabled)
        backdrop.activeOwner = node
        backdrop.isEnabled = enabled
        return node
    }

    override fun update(node: LayerBackdropNode) {
        val switchedBackdrop = node.backdrop != backdrop
        val toggledEnabled = node.enabled != enabled
        if (switchedBackdrop) {
            if (node.backdrop.activeOwner === node) {
                node.backdrop.layerCoordinates = null
                node.backdrop.isEnabled = false
                node.backdrop.activeOwner = null
            }
            node.backdrop.bumpDrawEpoch()
            node.backdrop = backdrop
        }
        node.drawRecording = drawRecording
        node.enabled = enabled
        backdrop.activeOwner = node
        backdrop.isEnabled = enabled
        if (switchedBackdrop || toggledEnabled) {
            backdrop.bumpDrawEpoch()
            BackdropDrawEpoch.bump()
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
        properties["enabled"] = enabled
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop || drawRecording != other.drawRecording || enabled != other.enabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + drawRecording.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var drawRecording: Boolean,
    var enabled: Boolean,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun onAttach() {
        backdrop.activeOwner = this
        backdrop.isEnabled = enabled
    }

    override fun ContentDrawScope.draw() {
        backdrop.activeOwner = this@LayerBackdropNode
        backdrop.isEnabled = enabled
        if (!enabled) {
            drawContent()
            return
        }
        val singlePass = (drawRecording || backdrop.isDefaultOnDraw) && size.width >= 1f && size.height >= 1f
        if (!singlePass) drawContent()
        recordLayer(backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
        backdrop.bumpDrawEpoch()
        BackdropDrawEpoch.bump()
        if (singlePass) drawLayer(backdrop.graphicsLayer)
    }

    private var lastLayerPos: Offset = Offset.Unspecified

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.activeOwner = this
            backdrop.layerCoordinates = coordinates
            val pos = coordinates.positionInWindow()
            if (enabled && lastLayerPos != Offset.Unspecified && lastLayerPos != pos) {
                backdrop.bumpDrawEpoch()
                BackdropDrawEpoch.bump()
            }
            lastLayerPos = pos
        }
    }

    override fun onDetach() {
        val wasActive = enabled && backdrop.drawEpoch > 0L
        // Prevent an exiting destination in AnimatedContent from clobbering a newly mounted destination sharing this backdrop.
        if (backdrop.activeOwner === this) {
            backdrop.layerCoordinates = null
            backdrop.isEnabled = false
            backdrop.activeOwner = null
            if (wasActive) {
                backdrop.bumpDrawEpoch()
                BackdropDrawEpoch.bump()
            }
        }
        lastLayerPos = Offset.Unspecified
    }
}
