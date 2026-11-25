package com.kyant.backdrop.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.kyant.backdrop.interaction.pressTransform
import com.kyant.backdrop.interaction.LiquidPressMotion
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GlassCapsuleTest {
    @Test fun aTapBetweenDisplayFramesStillLiftsAndReturnsWhileCancellationStaysStill() {
        for (hz in listOf(60, 90, 120)) {
            val clock = BroadcastFrameClock()
            val scope = CoroutineScope(Dispatchers.Unconfined + clock)
            try {
                val motion = LiquidPressMotion(scope, GlassPressTokens())
                var frame = 0L
                fun advance() { clock.sendFrame(++frame * (1_000_000_000L / hz)) }
                motion.begin()
                motion.release(completed = false)
                repeat(hz) { advance(); assertEquals(0f, motion.progress, .001f) }
                motion.begin()
                motion.release()
                assertEquals(0f, motion.progress, .001f)
                var peak = 0f
                repeat(hz) { advance(); peak = maxOf(peak, motion.progress) }
                assertTrue("Apex at ${hz}Hz: $peak", peak in .25f.. .55f)
                assertEquals(0f, motion.progress, .001f)
                motion.begin()
                motion.release()
                repeat(hz / 9) { advance() }
                val returning = motion.progress
                assertTrue(returning > 0f)
                motion.begin()
                motion.release()
                assertEquals(returning, motion.progress, .001f)
                var caughtPeak = returning
                repeat(hz / 10) { advance(); caughtPeak = maxOf(caughtPeak, motion.progress) }
                assertTrue("Repeat tap should lift again at ${hz}Hz", caughtPeak > returning + .01f)
                repeat(hz) { advance() }
                motion.begin()
                motion.release()
                advance()
                frame += hz / 4 // Skip the first quarter second of the navigation transition.
                advance()
                assertEquals(GlassPressTokens().minimumTapLift, motion.progress, .001f)
                advance()
                assertEquals(GlassPressTokens().minimumTapLift, motion.progress, .001f)
            } finally { scope.cancel() }
        }
    }

    @Test fun stationaryPressEnlargesBothAxesEquallyAcrossAspectRatios() {
        for (size in listOf(Size(48f, 48f), Size(66f, 44f), Size(110f, 44f), Size(44f, 110f))) {
            val pose = pressTransform(size, 1f, Offset.Zero, Offset.Zero, 16f)
            assertTrue(pose.scaleX > 1f)
            assertEquals(pose.scaleX, pose.scaleY, 1e-5f)
            assertEquals(Offset.Zero, pose.translation)
        }
        val size = Size(110f, 44f)
        val stretch = pressTransform(size, 1f, Offset(0f, 80f), Offset(0f, 80f), 16f)
        val rebound = pressTransform(size, 0f, Offset(0f, -8f), Offset(0f, -12f), 16f)
        assertTrue(stretch.scaleY > stretch.scaleX && stretch.translation.y > 0f)
        assertTrue(rebound.scaleY < rebound.scaleX && rebound.translation.y < 0f)
    }

    @Test fun liftingAndCatchingAReturningButtonKeepThePresentedPose() {
        val clock = BroadcastFrameClock()
        val scope = CoroutineScope(Dispatchers.Unconfined + clock)
        try {
            val motion = LiquidPressMotion(scope, GlassPressTokens())
            val bounds = Size(66f, 44f)
            fun pose() = motion.transform(bounds, Density(1f))
            var time = 0L
            fun frame() { time += 16_666_667; clock.sendFrame(time) }
            motion.begin()
            motion.drag(Offset(-80f, 100f))
            repeat(45) { frame() }
            val held = pose()
            assertTrue(held.scaleY > held.scaleX && held.translation.y > 0f)
            motion.release()
            assertEquals(held, pose())
            frame()
            assertTrue(pose().scaleY > 1f)
            frame()
            val returning = pose()
            motion.begin()
            assertEquals(returning, pose())
            motion.release()
            val remaining = (0..64).map { frame(); pose() }
            assertTrue(remaining.any { it.scaleY < 1f })
            assertTrue(remaining.any { it.translation.y < 0f })
            assertEquals(1f, remaining.last().scaleY, .002f)
        } finally { scope.cancel() }
    }

    @Test fun curvatureFlowsFromTheFlatAndSquareBoundsGiveACircle() {
        val pill = Rect(0f, 0f, 180f, 44f)
        val radius = pill.height / 2f
        val joinX = pill.right - radius - .75f * radius
        val distance = { x: Float -> capsuleDistance(Offset(x, 0f), pill) }
        assertEquals(0f, distance(joinX - 3f), .0001f)
        assertTrue(abs(distance(joinX + 2f)) < .0001f)
        assertTrue(distance(joinX + 16f) > .001f)
        val circle = Rect(0f, 0f, 48f, 48f)
        for (point in listOf(Offset(0f, 24f), Offset(24f, 48f), Offset(38.4f, 43.2f))) {
            assertEquals(0f, capsuleDistance(point, circle), .0001f)
        }
    }

    @Test fun onlyNearbyPeersShareAnOutlineIncludingWhenVerticallyOffset() {
        val a = Rect(0f, 0f, 66f, 44f)
        val b = Rect(77f, 0f, 187f, 44f)
        val pressed = Rect(-8f, -5.3f, 74f, 49.3f)
        fun distance(at: Offset, l: Rect, r: Rect) =
            glassUnion(capsuleDistance(at, l), capsuleDistance(at, r), 7f)
        assertTrue(distance(Offset(71.5f, 22f), a, b) > 0)
        assertTrue(distance(Offset(75.5f, 22f), pressed, b) < 0)
        assertTrue(distance(Offset(75.5f, 26f), pressed, b.translate(0f, 8f)) < 0)
        assertTrue(distance(Offset(75.5f, 0f), pressed, b) > 0)
        assertEquals(capsuleDistance(Offset(4f, 12f), a), distance(Offset(4f, 12f), a, b), .0001f)
    }
}
