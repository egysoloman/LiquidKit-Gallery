package com.kyant.backdrop.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope

suspend fun PointerInputScope.inspectDragGestures(
    lockOnDown: Boolean = false,
    holdLockMillis: Long = 110L,
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.isConsumed) return@awaitEachGesture
        var locked = lockOnDown
        if (locked) down.consume()
        var ended = false
        try {
            onDragStart(down)
            onDrag(down, Offset.Zero)
            val slop = viewConfiguration.touchSlop
            while (true) {
                val pass = if (locked) PointerEventPass.Initial else PointerEventPass.Final
                val p = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
                if (!p.pressed) {
                    if (lockOnDown) p.consume()
                    ended = true
                    onDragEnd(p)
                    break
                }
                val delta = p.position - down.position
                if (!locked && (p.uptimeMillis - down.uptimeMillis >= holdLockMillis || kotlin.math.abs(delta.x) > slop)) {
                    locked = true
                }
                if (locked) {
                    if (lockOnDown || delta.getDistance() > slop) {
                        p.consume()
                    }
                } else if (p.isConsumed && kotlin.math.abs(delta.y) > slop && kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x)) {
                    break
                }
                if (p.position != p.previousPosition) onDrag(p, p.position - p.previousPosition)
            }
        } finally {
            if (!ended) onDragCancel()
        }
    }
}
