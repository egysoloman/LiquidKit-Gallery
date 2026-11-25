package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.internal.ColorFilterEffect
import com.kyant.backdrop.internal.compositeOverInput

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    if (edgeTreatment != TileMode.Clamp || renderEffect != null) {
        if (radius > padding) {
            padding = radius
        }
    }

    renderEffect =
        BlurEffect(
            renderEffect,
            radius,
            radius,
            edgeTreatment
        )
}

/** Blur part of the transmitted scene while retaining detail in the same coordinate space. */
internal fun BackdropEffectScope.diffuseBackdrop(radius: Float, fraction: Float) {
    if (fraction <= 0f || radius <= 0f || !isRenderEffectSupported()) return
    val clear = renderEffect
    blur(radius)
    if (fraction < 1f) {
        val opacity = ColorMatrix().apply { this[3, 3] = fraction }
        val diffuse = ColorFilterEffect(renderEffect, ColorFilter.colorMatrix(opacity))
        renderEffect = compositeOverInput(diffuse, clear)
    }
}
