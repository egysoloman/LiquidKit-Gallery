package com.kyant.backdrop.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import com.kyant.backdrop.interaction.LiquidPressMotion

internal class LiquidMorphTouch(
    private val scope: CoroutineScope,
    private val tokens: com.kyant.backdrop.components.GlassPressTokens,
) {
    private val motion = LiquidPressMotion(scope, tokens)
    var position by mutableStateOf(Offset.Zero)
    private var origin by mutableStateOf(Offset.Zero)
    val strength = Animatable(0f)

    private var contactSize by mutableStateOf(Size.Zero)
    private var contactCenter by mutableStateOf(Offset.Zero)
    private var startedOnPill by mutableStateOf(false)

    suspend fun track(input: PointerInputScope, frame: () -> LiquidMorphFrame) {
        with(input) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val glass = frame()
                val outset = glass.smoothK / 6f + 1f
                val onPill = glass.pill.active && down.position in glass.pill.roundRect(outset)
                if (!onPill && !glass.body.containsPoint(down.position, outset)) return@awaitEachGesture

                val contact = if (onPill) glass.pill else glass.body
                contactSize = Size(contact.w, contact.h)
                contactCenter = Offset(contact.cx, contact.cy)
                startedOnPill = onPill
                motion.begin()
                origin = down.position
                position = down.position
                scope.launch(start = CoroutineStart.UNDISPATCHED) { strength.snapTo(1f) }
                var dragged = false
                try {
                    do {
                        val pointer = awaitPointerEvent(PointerEventPass.Main).changes
                            .firstOrNull { it.id == down.id } ?: break
                        position = pointer.position
                        motion.drag(position - origin)
                        dragged = dragged || (position - origin).getDistance() > viewConfiguration.touchSlop
                        // A drag on the glass cancels a tap. Nested controls get their movement first.
                        if (dragged && pointer.pressed && !pointer.isConsumed) pointer.consume()
                    } while (pointer.pressed)
                } finally {
                    motion.release()
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { strength.animateTo(0f, tween(tokens.morphGlowFadeMillis)) }
                }
            }
        }
    }

    fun transform(layer: GraphicsLayerScope, frame: LiquidMorphFrame) {
        val visible = if (startedOnPill) frame.collapsedAlpha else frame.expandedAlpha
        motion.apply(layer, contactSize, contactCenter, visible.coerceIn(0f, 1f))
    }
}

internal fun SdfBoxPrimitive.roundRect(outset: Float = 0f) = RoundRect(
    Rect(cx - w * 0.5f - outset, cy - h * 0.5f - outset, cx + w * 0.5f + outset, cy + h * 0.5f + outset),
    CornerRadius(r + outset),
)
