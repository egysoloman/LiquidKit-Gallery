package com.kyant.backdrop.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kyant.backdrop.interaction.LiquidPressMotion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Observe a control's touch while its editor, click handler or containing scroll view owns the action. */
@Composable
internal fun Modifier.liquidControlPress(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val motion = rememberControlPress()
    return observeControlPress(motion).graphicsLayer { motion.apply(this) }
}

@Composable
internal fun rememberControlPress(): LiquidPressMotion {
    val scope = rememberCoroutineScope()
    val press = LocalLiquidGlassTokens.current.press
    return remember(scope, press) { LiquidPressMotion(scope, press) }
}

@Composable
internal fun Modifier.observeControlPress(motion: LiquidPressMotion, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    var keyDown by remember(motion) { mutableStateOf(false) }
    return onFocusChanged {
        if (!it.hasFocus && keyDown) { keyDown = false; motion.release(completed = false) }
    }.onPreviewKeyEvent {
        if (it.key == Key.Enter || it.key == Key.Spacebar || it.key == Key.DirectionCenter) {
            if (it.type == KeyEventType.KeyDown && !keyDown) keyDown = motion.begin()
            if (it.type == KeyEventType.KeyUp && keyDown) { keyDown = false; motion.release() }
        }
        false
    }.pointerInput(motion) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(false, PointerEventPass.Initial)
            if (!motion.begin()) return@awaitEachGesture
            var locked = false
            var released = false
            try {
                do {
                    val pass = if (locked) PointerEventPass.Initial else PointerEventPass.Final
                    val event = awaitPointerEvent(pass)
                    val p = event.changes.singleOrNull() ?: break
                    if (p.id != down.id) break
                    if (!p.pressed) {
                        released = true
                        break
                    }
                    val delta = p.position - down.position
                    if (!locked && (p.uptimeMillis - down.uptimeMillis >= 110L || abs(delta.x) > slop)) {
                        locked = true
                    }
                    if (locked) {
                        if (delta.getDistance() > slop) p.consume()
                    } else if (p.isConsumed && abs(delta.y) > slop && abs(delta.y) > abs(delta.x)) {
                        break
                    }
                    motion.drag(delta)
                } while (true)
            } finally { motion.release(completed = released) }
        }
    }
}
