package com.kyant.backdrop.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.components.GlassPressTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

internal data class PressTransform(
    val scaleX: Float = 1f, val scaleY: Float = 1f, val translation: Offset = Offset.Zero,
)

internal fun pressTransform(
    contact: Size, lift: Float, travel: Offset, strain: Offset,
    maxExpansion: Float, directionalStretch: Float = .96f, visibility: Float = 1f,
): PressTransform {
    if (contact.minDimension <= 0f) return PressTransform()
    val diameter = contact.minDimension
    val uniform = 1f + minOf(maxExpansion, .36f * diameter) * lift * visibility / contact.maxDimension
    val limit = diameter * .275f
    fun follow(value: Float) = limit * tanh(.016f * value / limit) * visibility
    val stretch = directionalStretch * (follow(strain.x) - follow(strain.y))
    return PressTransform(uniform + stretch / contact.width, uniform - stretch / contact.height,
        Offset(follow(travel.x), follow(travel.y)))
}

internal class LiquidPressMotion(private val scope: CoroutineScope, private val tokens: GlassPressTokens) {
    private val lift = Animatable(0f)
    private val center = Animatable(Offset.Zero, Offset.VectorConverter)
    // Keeping the two axis magnitudes signed during release allows each to overshoot zero.
    private val strain = Animatable(Offset.Zero, Offset.VectorConverter)
    private var held = false
    private var contactStartLift = 0f
    private var liftJob: Job? = null
    val progress: Float get() = lift.value.coerceIn(0f, 1f)
    val highlightProgress: Float get() = 1f - (1f - progress).pow(tokens.highlightResponse)

    fun begin(): Boolean {
        if (held) return false
        held = true
        contactStartLift = progress
        val velocity = lift.velocity
        liftJob?.cancel()
        liftJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            lift.animateTo(1f, tokens.engage.spec, initialVelocity = velocity)
        }
        return true
    }

    fun drag(displacement: Offset) {
        if (!held) return
        val magnitude = Offset(abs(displacement.x), abs(displacement.y))
        scope.launch(start = CoroutineStart.UNDISPATCHED) { center.animateTo(displacement, tokens.tracking.offsetSpec) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { strain.animateTo(magnitude, tokens.tracking.offsetSpec) }
    }

    fun release(completed: Boolean = true) {
        if (!held) return
        held = false
        val velocity = lift.velocity
        liftJob?.cancel()
        liftJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val apex = contactStartLift + (1f - contactStartLift) * tokens.minimumTapLift.coerceIn(0f, 1f)
            if (completed && apex > 0f && lift.value < apex) {
                // A busy navigation frame can skip an entire short spring. Present the apex
                // before starting the return. A new contact can interrupt either animation.
                val duration = (tokens.tapRiseMillis * (1f - progress / apex)).roundToInt().coerceAtLeast(1)
                lift.animateTo(apex, tween(duration, easing = LinearOutSlowInEasing), initialVelocity = velocity)
                withFrameNanos { }
                lift.animateTo(0f, tokens.liftRelease.spec)
            } else {
                lift.animateTo(0f, tokens.liftRelease.spec, initialVelocity = velocity)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { center.animateTo(Offset.Zero, tokens.centerRelease.offsetSpec) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { strain.animateTo(Offset.Zero, tokens.release.offsetSpec) }
    }

    fun transform(contact: Size, density: Density, visibility: Float = 1f): PressTransform =
        pressTransform(contact, lift.value, center.value, strain.value,
            with(density) { tokens.expansion.toPx() }, tokens.directionalStretch, visibility)

    fun apply(layer: GraphicsLayerScope, contact: Size = layer.size,
        center: Offset = layer.size.center, visibility: Float = 1f) = with(layer) {
        if (size.minDimension <= 0f) return@with
        val pose = transform(contact, this, visibility)
        transformOrigin = TransformOrigin(center.x / size.width, center.y / size.height)
        translationX = pose.translation.x
        translationY = pose.translation.y
        scaleX = pose.scaleX
        scaleY = pose.scaleY
    }
}
