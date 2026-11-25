package com.kyant.backdrop.components

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.tanh

internal data class MenuPose(val center: Offset = Offset.Zero, val stretch: Offset = Offset.Zero)

/** Integrate from the last displayed position and velocity, even when a gesture changes owners. */
internal class MenuElasticity(
    private val density: Float,
    private val tokens: GlassPressTokens,
    private val horizontalAway: Float = 1f,
    private val verticalAway: Float = 1f,
) {
    private class Axis {
        var value = 0f
        var velocity = 0f

        fun advance(target: Float, spring: FloatSpringSpec, nanos: Long): Boolean {
            if (nanos > 0 && (abs(value - target) > .025f || abs(velocity) > .35f)) {
                val next = spring.getValueFromNanos(nanos, value, target, velocity)
                velocity = spring.getVelocityFromNanos(nanos, value, target, velocity)
                value = next
                return true
            }
            value = target
            velocity = 0f
            return false
        }
    }

    private fun GlassSpring.axisSpec() = FloatSpringSpec(damping, stiffness)
    private val tracking = tokens.tracking.axisSpec()
    private val release = tokens.centerRelease.axisSpec()
    private val aspectRelease = tokens.release.axisSpec()
    private val x = Axis()
    private val y = Axis()
    private val width = Axis()
    private val height = Axis()
    private val scrollFollower = Axis()
    val pose get() = MenuPose(Offset(x.value, y.value), Offset(width.value, height.value))

    fun attachScroll(position: Float) {
        scrollFollower.value = position
        scrollFollower.velocity = 0f
    }

    fun applyAcceleration(deltaVelocity: Offset) {
        // Velocity/acceleration is integrated smoothly into advance() so active movement tracks without jitter
        // and rebounds elastically once finger movement stops.
    }

    fun lift(pull: Offset, velocity: Velocity) {
        val limit = tokens.menuPullLimit.value * density * 24f
        if (limit <= 0f) return
        fun transfer(v: Float) = limit * tanh(v * tokens.menuReleaseTransfer / limit)
        val vx = transfer(velocity.x)
        val vy = transfer(velocity.y)
        // The surface already has momentum. Transfer measured finger speed without resetting that motion.
        x.velocity += (vx - x.velocity) * .65f
        y.velocity += (vy - y.velocity) * .65f
        val horizontal = horizontalAway * vx
        val vertical = verticalAway * vy
        width.velocity += (strain(horizontal, vertical) - width.velocity) * .65f
        height.velocity += (strain(vertical, horizontal) - height.velocity) * .65f
    }

    private fun strain(along: Float, across: Float) =
        tokens.directionalStretch * (along - tokens.menuCrossContraction * across)

    private fun directed(value: Float, away: Float): Float {
        val signed = value * away
        return if (signed >= 0f) signed else signed * .68f
    }

    fun advance(
        pull: Offset,
        scrolled: Float,
        dragging: Boolean,
        nanos: Long,
        fingerVelocity: Offset = Offset.Zero,
        fingerAcceleration: Offset = Offset.Zero,
        fingerMoving: Boolean = dragging,
    ): Boolean {
        val listMoving = scrollFollower.advance(scrolled, tracking, nanos)
        val flex = tokens.scrollFlex.value * density * tanh((scrolled - scrollFollower.value) / (16f * density))
        val knee = (tokens.menuPullLimit.value * density).coerceAtLeast(.01f)
        val dynamicCap = 9f * density
        // The force becomes harder to overcome, but a longer pull still moves the edge.
        fun displacement(distance: Float) = knee * asinh(distance * tokens.menuDragGain / knee)
        fun velPull(v: Float) = dynamicCap * tanh(v * .011f / dynamicCap)
        val activeMove = dragging && fingerMoving
        val rawX = displacement(pull.x) + if (activeMove) velPull(fingerVelocity.x) else 0f
        val rawY = displacement(pull.y) + flex + if (activeMove) velPull(fingerVelocity.y) else 0f
        val targetX = if (rawX * horizontalAway < 0f) rawX * .45f else rawX
        val targetY = if (rawY * verticalAway < 0f) rawY * .45f else rawY
        val directedX = directed(rawX, horizontalAway)
        val directedY = directed(rawY, verticalAway)
        val centerSpring = if (activeMove) tracking else release
        val movingX = x.advance(targetX, centerSpring, nanos)
        val movingY = y.advance(targetY, centerSpring, nanos)
        val shapeSpring = if (activeMove) tracking else aspectRelease
        val resizingX = width.advance(strain(directedX, directedY), shapeSpring, nanos)
        val resizingY = height.advance(strain(directedY, directedX), shapeSpring, nanos)
        return listMoving || movingX || movingY || resizingX || resizingY
    }
}
