package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/**
 * Zero-VRAM solid-color [Backdrop] that avoids allocating a full-screen offscreen [GraphicsLayer]
 * texture when the backing canvas is a uniform color, while participating in `DrawBackdropNode`
 * draw-epoch caching so static glass surfaces never redundantly re-record.
 */
@Composable
fun rememberSolidColorBackdrop(color: Color): Backdrop =
    remember(color) { SolidColorBackdrop(color) }

@Immutable
internal class SolidColorBackdrop(val color: Color) : CompositeLayerBackdrop {

    override val isLayerBacked: Boolean = true

    override val isCoordinatesDependent: Boolean = false

    override fun combinedDrawEpoch(): Long =
        (color.value.toLong() xor 0x51A7E2B3L).let { if (it == 0L) 1L else it }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        drawRect(color)
    }
}
