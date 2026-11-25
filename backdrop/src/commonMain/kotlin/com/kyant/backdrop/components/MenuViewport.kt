package com.kyant.backdrop.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Touch displacement left over after scrolling the menu under the finger. */
internal class MenuViewport(
    private val scroll: ScrollableState,
    private val flingBehavior: FlingBehavior,
    private val scope: CoroutineScope,
) {
    var pull by mutableStateOf(Offset.Zero)
        private set
    var dragging by mutableStateOf(false)
        private set
    var fingerScroll by mutableFloatStateOf(0f)
        private set
    var releaseCount by mutableStateOf(0)
        private set
    var directScroll by mutableStateOf(false)
        private set
    var releaseVelocity = Velocity.Zero
        private set
    var releasedPull = Offset.Zero
        private set
    var accelerationImpulse by mutableStateOf(Offset.Zero)
        private set
    var accelerationSeq by mutableIntStateOf(0)
        private set
    var moveSeq by mutableIntStateOf(0)
        private set
    var pointerVelocity by mutableStateOf(Offset.Zero)
        private set
    var pointerAcceleration by mutableStateOf(Offset.Zero)
        private set
    val scrollable: Boolean get() = scroll.canScrollForward || scroll.canScrollBackward
    private var fling: Job? = null
    private var overscrollPull = Offset.Zero
    private var lastMoveMillis = 0L

    fun stop() {
        fling?.cancel()
        dragging = false
        directScroll = false
        pull = Offset.Zero
        overscrollPull = Offset.Zero
        pointerVelocity = Offset.Zero
        pointerAcceleration = Offset.Zero
        lastMoveMillis = 0L
    }

    fun resetVelocity() {
        pointerVelocity = Offset.Zero
        pointerAcceleration = Offset.Zero
        lastMoveMillis = 0L
    }

    fun decayMotion(factor: Float) {
        val vx = if (abs(pointerVelocity.x) < 1f) 0f else pointerVelocity.x * factor
        val vy = if (abs(pointerVelocity.y) < 1f) 0f else pointerVelocity.y * factor
        val ax = if (abs(pointerAcceleration.x) < 1f) 0f else pointerAcceleration.x * factor
        val ay = if (abs(pointerAcceleration.y) < 1f) 0f else pointerAcceleration.y * factor
        pointerVelocity = Offset(vx, vy)
        pointerAcceleration = Offset(ax, ay)
    }

    fun drag(delta: Offset, scrollRows: Boolean, outsidePull: Offset? = null, timeMillis: Long = 0L) {
        dragging = true
        directScroll = scrollRows
        moveSeq++
        if (timeMillis > 0L) {
            val paused = lastMoveMillis <= 0L || (timeMillis - lastMoveMillis) > 95L
            val prevV = if (paused) Offset.Zero else pointerVelocity
            val dtSec = if (paused) .016f else ((timeMillis - lastMoveMillis) / 1000f).coerceIn(.008f, .095f)
            val instantV = Offset(
                (delta.x / dtSec).coerceIn(-3200f, 3200f),
                (delta.y / dtSec).coerceIn(-3200f, 3200f),
            )
            val nextV = Offset(
                lerp(prevV.x, instantV.x, .22f),
                lerp(prevV.y, instantV.y, .22f),
            )
            val instantA = Offset(
                ((nextV.x - prevV.x) / dtSec).coerceIn(-24000f, 24000f),
                ((nextV.y - prevV.y) / dtSec).coerceIn(-24000f, 24000f),
            )
            pointerVelocity = nextV
            pointerAcceleration = Offset(
                lerp(pointerAcceleration.x, instantA.x, .35f),
                lerp(pointerAcceleration.y, instantA.y, .35f),
            )
            accelerationImpulse = nextV - prevV
            accelerationSeq++
            lastMoveMillis = timeMillis
        }
        // A reversal first returns the stretched edge to its rest position.
        val unwind = if (delta.y * overscrollPull.y < 0f) delta.y.sign * minOf(abs(delta.y), abs(overscrollPull.y)) else 0f
        val travel = delta.y - unwind
        val consumed = if (scrollRows && scrollable) -scroll.dispatchRawDelta(-travel) else 0f
        fingerScroll += consumed
        overscrollPull = Offset(overscrollPull.x + delta.x, overscrollPull.y + delta.y - consumed)
        pull = if (outsidePull == null) {
            overscrollPull
        } else {
            val combinedY = when {
                !scrollRows || !scrollable -> outsidePull.y
                outsidePull.y != 0f && overscrollPull.y * outsidePull.y >= 0f && abs(outsidePull.y) > abs(overscrollPull.y) -> outsidePull.y
                else -> overscrollPull.y
            }
            Offset(outsidePull.x, combinedY)
        }
    }

    fun release(velocity: Velocity = Velocity.Zero) {
        if (!dragging) return
        val canFling = abs(pull.y) < 1f &&
            ((velocity.y < 0f && scroll.canScrollForward) || (velocity.y > 0f && scroll.canScrollBackward))
        releasedPull = pull
        releaseVelocity = velocity
        releaseCount++
        dragging = false
        directScroll = false
        pull = Offset.Zero
        overscrollPull = Offset.Zero
        pointerVelocity = Offset.Zero
        pointerAcceleration = Offset.Zero
        lastMoveMillis = 0L
        if (canFling) fling = scope.launch {
            scroll.scroll { with(flingBehavior) { performFling(-velocity.y) } }
        }
    }
}

// Keep the last useful segment when a device batches motion more slowly than VelocityTracker's fit window.
internal class DragReleaseVelocity(time: Long, start: Offset) {
    private val tracker = VelocityTracker().apply { addPosition(time, start) }
    private var last = start
    private var lastTime = time
    private var segment = Velocity.Zero

    fun move(time: Long, point: Offset) {
        if (point == last || time <= lastTime) return
        val elapsed = time - lastTime
        val delta = (point - last) * (1000f / elapsed)
        segment = if (elapsed <= 100L) Velocity(delta.x, delta.y) else Velocity.Zero
        last = point
        lastTime = time
        tracker.addPosition(time, point)
    }

    fun atRelease(time: Long): Velocity {
        val freshness = ((100L - (time - lastTime)).toFloat() / 60f).coerceIn(0f, 1f)
        if (freshness == 0f) return Velocity.Zero
        val fit = tracker.calculateVelocity()
        return (if (fit.x == 0f && fit.y == 0f) segment else fit) * freshness
    }
}
