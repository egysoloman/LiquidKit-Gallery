package com.kyant.backdrop.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sqrt

/** A damping ratio below one permits a spring to cross its destination. */
@Immutable
data class GlassSpring(val damping: Float, val stiffness: Float) {
    init { require(damping > 0f && stiffness > 0f) }

    internal val spec = spring<Float>(damping, stiffness, .0008f)
    internal val offsetSpec = spring(damping, stiffness, Offset(.05f, .05f))
    internal val dpSpec = spring(damping, stiffness, 0.1.dp)
    internal val rectSpec = spring(damping, stiffness, Rect(.05f, .05f, .05f, .05f))

    /** SwiftUI-equivalent period in seconds derived from Compose stiffness. */
    val response: Float get() = 2f * PI.toFloat() / sqrt(stiffness)

    /** Alias matching SwiftUI's `dampingFraction` terminology. */
    val dampingFraction: Float get() = damping

    internal fun <T> specWithThreshold(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(damping, stiffness, visibilityThreshold)

    internal fun slowMotionScaled(factor: Int): GlassSpring =
        if (factor <= 1) this else copy(stiffness = stiffness / (factor * factor))

    companion object {
        /** Maps SwiftUI's `(response, dampingFraction)` parameterization directly to Compose spring physics. */
        fun swiftUI(response: Float, dampingFraction: Float): GlassSpring {
            require(response > 0f && dampingFraction > 0f)
            val omega = 2f * PI.toFloat() / response
            return GlassSpring(damping = dampingFraction, stiffness = omega * omega)
        }

        /** SwiftUI-aligned snappy spring (`bounce = 0.15 + extraBounce`). */
        fun snappy(duration: Float = 0.25f, extraBounce: Float = 0f): GlassSpring {
            require(duration > 0f)
            return swiftUI(duration, (1f - 0.15f - extraBounce).coerceIn(0.05f, 1f))
        }

        /** SwiftUI-aligned smooth spring without default overshoot (`bounce = extraBounce`). */
        fun smooth(duration: Float = 0.25f, extraBounce: Float = 0f): GlassSpring {
            require(duration > 0f)
            return swiftUI(duration, (1f - extraBounce).coerceIn(0.05f, 1f))
        }

        /** SwiftUI-aligned bouncy spring (`bounce = 0.30 + extraBounce`). */
        fun bouncy(duration: Float = 0.35f, extraBounce: Float = 0f): GlassSpring {
            require(duration > 0f)
            return swiftUI(duration, (1f - 0.30f - extraBounce).coerceIn(0.05f, 1f))
        }
    }
}

@Immutable
data class GlassPressTokens(
    val engage: GlassSpring = GlassSpring(.78f, 720f),
    val release: GlassSpring = GlassSpring(.40f, 360f),
    val centerRelease: GlassSpring = GlassSpring(.50f, 360f),
    val liftRelease: GlassSpring = GlassSpring(.40f, 1000f),
    val expansion: Dp = 16.dp,
    val directionalStretch: Float = 1.0f,
    val tracking: GlassSpring = GlassSpring(.96f, 820f),
    val scrollFlex: Dp = 2.8.dp,
    val menuDragGain: Float = .088f,
    /** Inside-menu static pull ratio (0 = inside stretch comes from finger velocity/acceleration and rebounds on stop). */
    val menuInsideDragRatio: Float = 0f,
    /** Distance scale at which a menu pull starts gaining resistance. */
    val menuPullLimit: Dp = 30.dp,
    val menuReleaseTransfer: Float = .12f,
    val menuCrossContraction: Float = .56f,
    /** Minimum visible fraction of the normal expansion when a tap ends between frames. */
    val minimumTapLift: Float = .32f,
    val tapRiseMillis: Int = 42,
    val actionHighlightAlpha: Float = .14f,
    val highlightResponse: Float = 4f,
    val ambientHighlightAlpha: Float = .075f,
    val spotHighlightAlpha: Float = .10f,
    val highlightOffset: GlassSpring = GlassSpring(.56f, 215f),
    val morphGlowFadeMillis: Int = 165,
)

@Immutable
data class GlassTabTokens(
    val selection: GlassSpring = GlassSpring(.62f, 400f),
    val trailingEdge: GlassSpring = GlassSpring(.66f, 150f),
    val tracking: GlassSpring = GlassSpring(.68f, 720f),
    val trackingTrailingEdge: GlassSpring = GlassSpring(.62f, 260f),
    val hoverPullSpring: GlassSpring = GlassSpring(.58f, 460f),
    val plateLift: Float = .053f,
    val heldWidthGain: Float = .16f,
    val heldHeightGain: Float = .44f,
    val travelStretch: Float = .58f,
    val pullStretch: Float = .14f,
    val plateMinification: Float = .185f,
    val dispersion: Dp = 4.2.dp,
    val diffusionRadius: Dp = 6.dp,
    val diffusionMix: Float = .55f,
    val darkDiffusionRadius: Dp = 5.2.dp,
    val lensDiffusion: Dp = 2.4.dp,
    val rimReflection: Float = .42f,
    val rimAbsorption: Float = .26f,
    val darkLensDiffusionScale: Float = .45f,
    val lightHeldHeightGain: Float = .055f,
    val inkVibrancy: Float = 1f,
    val lightBackingAtMidpoint: Float = .055f,
    val lightBackingAtMaximum: Float = .42f,
    val darkBackingAtMaximum: Float = .50f,
    val denseDiffusionRadius: Dp = 13.dp,
    val darkDenseDiffusionRadius: Dp = 18.dp,
    val diffusionResponse: Float = .82f,
    val darkDenseLensMix: Float = .82f,
    val rimJoinDepth: Dp = 4.6.dp,
    val topHairlineOpacity: Float = .032f,
    val hoverBevelWidth: Dp = 10.dp,
    val heldInkMagnification: Float = .16f,
    val hoverMaterialStrength: Float = .10f,
    val tapLiftMillis: Int = 65,
    val lightInterfaceShade: Float = .050f,
    val raisedHairlineOpacity: Float = .016f,
    val inkRefractionWidth: Dp = 10.dp,
    val inkRefractionAmount: Dp = 12.dp,
    val restingPlateRimStrength: Float = .68f,
    val platePressSheen: Float = .22f,
    val clearLensDiffusionMix: Float = .12f,
) {
    internal fun edgeSpring(leading: Boolean, tracking: Boolean): GlassSpring = when {
        tracking -> if (leading) this.tracking else trackingTrailingEdge
        leading -> selection
        else -> trailingEdge
    }

    /** Fraction of the colored plate replaced by the neutral backing, at the current setting. */
    internal fun backingFraction(appearance: LiquidGlassAppearance): Float {
        val travel = appearance.opacity * 2f
        val upper = (travel - 1f).coerceAtLeast(0f)
        val fraction = if (appearance.dark) {
            darkBackingAtMaximum * upper * (1.4f - .4f * upper)
        } else {
            lightBackingAtMidpoint * travel.coerceAtMost(1f) + (lightBackingAtMaximum - lightBackingAtMidpoint) * upper
        }
        return fraction.coerceIn(0f, 1f)
    }
}

@Immutable
data class GlassReplacementTokens(
    val blur: Dp = 4.dp,
    val scaleChange: Float = .065f,
    val enterMillis: Int = 180,
    val exitMillis: Int = 135,
    val sameSizeLift: Float = .22f,
    val resizedLift: Float = .14f,
    val sizeSpring: GlassSpring = GlassSpring(.78f, 490f),
    val liftMillis: Int = 95,
    val toolbarEnterMillis: Int = 170,
    val toolbarExitMillis: Int = 145,
    val toolbarScaleExitMillis: Int = 150,
)

/** Shape of the departing volume. Fractions are independent of density and animation speed. */
@Immutable
data class GlassMenuContourTokens(
    val detachedAspect: Float = 1.33f,
    /** Positive bias moves the widest section away from the source without sharpening the pole. */
    val crownBias: Float = .21f,
    val maximumLean: Float = .125f,
    /** Progress of the lagging shape spring as round shoulders become menu corners. */
    val cornerStart: Float = .50f,
    val cornerEnd: Float = .99f,
) {
    init {
        require(detachedAspect >= 1f && crownBias in 0f..0.45f && maximumLean in 0f..0.25f)
        require(cornerStart >= 0f && cornerStart < cornerEnd && cornerEnd <= 1f)
    }
}

@Immutable
data class GlassMenuTokens(
    val clearDiffusion: Float = .65f,
    val denseDiffusion: Float = .98f,
    val contentRimWidth: Dp = 25.dp,
    val contentDisplacement: Dp = 18.dp,
    val previewLiftScale: Float = 1.024f,
    val previewActiveScale: Float = .94f,
    val previewMaxInset: Dp = 20.dp,
    val previewHoldElevation: Dp = 13.dp,
    val previewActiveElevation: Dp = 17.dp,
    val previewCorner: Dp = 10.dp,
    val previewLift: GlassSpring = GlassSpring(.86f, 420f),
    val previewSettle: GlassSpring = GlassSpring(.85f, 540f),
    val previewArmDelayMillis: Int = 70,
    val previewShadeMillis: Int = 175,
    val borderlessPressedAlpha: Float = .57f,
    val borderlessExpansion: Float = .045f,
    val borderlessHoldMillis: Int = 260,
    val borderlessOpenResponse: Float = .27f,
    val borderlessCloseResponse: Float = .21f,
    val touchLight: GlassSpring = GlassSpring(.90f, 770f),
    val touchFollow: GlassSpring = GlassSpring(.86f, 760f),
    val branchOpen: GlassSpring = GlassSpring(.91f, 405f),
    val branchCloseMillis: Int = 210,
    val stackReturnFallbackMillis: Int = 240,
    val popupEnterMillis: Int = 145,
    val popupExitMillis: Int = 120,
    val popupScale: GlassSpring = GlassSpring(.83f, 590f),
    val popupScaleExitMillis: Int = 135,
    val popupRowsMillis: Int = 110,
    val popupRowsDelayMillis: Int = 36,
    val historyHotSpring: GlassSpring = GlassSpring(.92f, 460f),
    val historyPullSpring: GlassSpring = GlassSpring(.84f, 620f),
    val historyTintMillis: Int = 210,
    val contour: GlassMenuContourTokens = GlassMenuContourTokens(),
)

@Immutable
data class GlassAlertTokens(
    val width: Dp = 320.dp,
    val corner: Dp = 32.dp,
    val actionHeight: Dp = 48.dp,
    val actionGap: Dp = 8.dp,
    val scrimAlpha: Float = .20f,
    val lightSurfaceMix: Float = .11f,
    val darkSurfaceMix: Float = .10f,
    val lightActionMix: Float = .225f,
    val darkActionMix: Float = .25f,
    val enterSpring: GlassSpring = GlassSpring.swiftUI(0.32f, 0.84f),
    val exitMillis: Int = 165,
)

@Immutable
data class GlassSheetTokens(
    val detentSpring: GlassSpring = GlassSpring.swiftUI(0.38f, 0.88f),
    val scrimAlpha: Float = .20f,
    val corner: Dp = 34.dp,
    val grabberWidth: Dp = 36.dp,
    val grabberHeight: Dp = 5.dp,
    val dismissFraction: Float = .28f,
    val dismissVelocity: Dp = 900.dp,
    val popoverCorner: Dp = 28.dp,
    val popoverSpring: GlassSpring = GlassSpring.swiftUI(0.28f, 0.82f),
)

@Immutable
data class GlassNavigationTokens(
    val durationMillis: Int = 355,
    val sameDepthEnterMillis: Int = 210,
    val sameDepthExitMillis: Int = 180,
    val splitResizeMillis: Int = 245,
    val disclosureRotateMillis: Int = 180,
    val pageEasing: CubicBezierEasing = CubicBezierEasing(.26f, .61f, .24f, 1f),
    val commitFraction: Float = .45f,
    val flingDistancePerSecond: Dp = 720.dp,
    val quickSwipeWindowMillis: Int = 180,
)

@Immutable
data class GlassControlTokens(
    val listPressMillis: Int = 75,
    val listReleaseMillis: Int = 180,
    val listSelectMillis: Int = 140,
    val swipeSettle: GlassSpring = GlassSpring(.92f, 650f),
    val sliderThumbPress: GlassSpring = GlassSpring(.56f, 370f),
    val sliderThumbRebound: GlassSpring = GlassSpring(.56f, 290f),
    val dragValue: GlassSpring = GlassSpring(1f, 1000f),
    val dragVelocity: GlassSpring = GlassSpring(0.5f, 300f),
    val dragPress: GlassSpring = GlassSpring(1f, 1000f),
    val dragScale: GlassSpring = GlassSpring(.65f, 250f),
    val segmentedSelection: GlassSpring = GlassSpring(.60f, 380f),
    val segmentedTrailingEdge: GlassSpring = GlassSpring(.64f, 155f),
    val segmentedTracking: GlassSpring = GlassSpring(.68f, 760f),
    val segmentedTrackingTrailing: GlassSpring = GlassSpring(.62f, 270f),
    val segmentedPressScale: Float = 1.045f,
    val segmentedLensWidthGain: Float = .18f,
    val segmentedLensHeightGain: Float = .52f,
    val segmentedTravelStretch: Float = .50f,
    val segmentedLensMagnification: Float = .075f,
    val stepperRepeatDelayMillis: Long = 420L,
    val stepperRepeatIntervalMillis: Long = 110L,
)

@Immutable
data class LiquidGlassTokens(
    val press: GlassPressTokens = GlassPressTokens(),
    val tabs: GlassTabTokens = GlassTabTokens(),
    val rimWidth: Dp = 1.15.dp,
    val highlightIntensity: Float = 1f,
    val fusionDistance: Dp = 3.35.dp,
    val replacement: GlassReplacementTokens = GlassReplacementTokens(),
    val menu: GlassMenuTokens = GlassMenuTokens(),
    val navigation: GlassNavigationTokens = GlassNavigationTokens(),
    val controls: GlassControlTokens = GlassControlTokens(),
    val alert: GlassAlertTokens = GlassAlertTokens(),
    val sheet: GlassSheetTokens = GlassSheetTokens(),
)

/** Override once with CompositionLocalProvider to tune all participating controls. */
val LocalLiquidGlassTokens = staticCompositionLocalOf { LiquidGlassTokens() }
