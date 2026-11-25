package com.kyant.backdrop.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GlassSpringTest {
    @Test
    fun swiftUiSpringMapsResponseAndDampingToComposeStiffness() {
        val spring = GlassSpring.swiftUI(response = 0.25f, dampingFraction = 0.85f)
        val expectedOmega = 2f * PI.toFloat() / 0.25f
        assertEquals(0.85f, spring.damping, 0f)
        assertEquals(0.85f, spring.dampingFraction, 0f)
        assertEquals(expectedOmega * expectedOmega, spring.stiffness, 1e-3f)
        assertEquals(0.25f, spring.response, 1e-5f)
    }

    @Test
    fun swiftUiPresetFactoriesMatchBounceDefinitions() {
        val snappy = GlassSpring.snappy(duration = 0.30f)
        assertEquals(0.85f, snappy.dampingFraction, 1e-6f)
        assertEquals(0.30f, snappy.response, 1e-5f)

        val smooth = GlassSpring.smooth(duration = 0.40f)
        assertEquals(1.0f, smooth.dampingFraction, 1e-6f)
        assertEquals(0.40f, smooth.response, 1e-5f)

        val bouncy = GlassSpring.bouncy(duration = 0.35f)
        assertEquals(0.70f, bouncy.dampingFraction, 1e-6f)
        assertEquals(0.35f, bouncy.response, 1e-5f)

        val clampedLow = GlassSpring.snappy(duration = 0.25f, extraBounce = 0.90f)
        assertEquals(0.05f, clampedLow.dampingFraction, 1e-6f)

        val clampedHigh = GlassSpring.smooth(duration = 0.25f, extraBounce = -0.50f)
        assertEquals(1.0f, clampedHigh.dampingFraction, 1e-6f)
    }

    @Test
    fun slowMotionScalingExtendsResponseLinearly() {
        val base = GlassMenuTokens().popupScale
        assertEquals(base, base.slowMotionScaled(1))

        val slowed = base.slowMotionScaled(6)
        assertEquals(base.damping, slowed.damping, 0f)
        assertEquals(base.response * 6f, slowed.response, 1e-4f)
    }

    @Test
    fun composeSpecHelpersExposeMatchingParameters() {
        val spring = GlassSpring.swiftUI(response = 0.28f, dampingFraction = 0.78f)
        val customSpec = spring.specWithThreshold(0.25f)
        for (spec in listOf(spring.spec, spring.offsetSpec, spring.dpSpec, spring.rectSpec, customSpec)) {
            assertEquals(spring.damping, spec.dampingRatio, 0f)
            assertEquals(spring.stiffness, spec.stiffness, 0f)
        }
        assertEquals(Offset(.05f, .05f), spring.offsetSpec.visibilityThreshold)
        assertEquals(0.1.dp, spring.dpSpec.visibilityThreshold)
        assertEquals(0.25f, customSpec.visibilityThreshold)
    }

    @Test
    fun invalidSpringParametersThrow() {
        assertTrue(runCatching { GlassSpring(0f, 100f) }.isFailure)
        assertTrue(runCatching { GlassSpring(0.8f, 0f) }.isFailure)
        assertTrue(runCatching { GlassSpring.swiftUI(0f, 0.8f) }.isFailure)
        assertTrue(runCatching { GlassSpring.swiftUI(0.3f, 0f) }.isFailure)
        assertTrue(runCatching { GlassSpring.snappy(0f) }.isFailure)
        assertTrue(runCatching { GlassSpring.smooth(-0.1f) }.isFailure)
        assertTrue(runCatching { GlassSpring.bouncy(0f) }.isFailure)
    }

    @Test
    fun defaultTokenGroupsHavePositiveAnimationTimings() {
        val tokens = LiquidGlassTokens()
        assertTrue(tokens.press.tapRiseMillis > 0 && tokens.press.morphGlowFadeMillis > 0)
        assertTrue(tokens.tabs.tapLiftMillis > 0)
        assertTrue(
            tokens.replacement.enterMillis > 0 &&
                tokens.replacement.exitMillis > 0 &&
                tokens.replacement.toolbarEnterMillis > 0 &&
                tokens.replacement.toolbarExitMillis > 0 &&
                tokens.replacement.toolbarScaleExitMillis > 0,
        )
        assertTrue(
            tokens.menu.branchCloseMillis > 0 &&
                tokens.menu.stackReturnFallbackMillis > 0 &&
                tokens.menu.popupEnterMillis > 0 &&
                tokens.menu.popupExitMillis > 0 &&
                tokens.menu.historyTintMillis > 0,
        )
        assertTrue(
            tokens.navigation.durationMillis > 0 &&
                tokens.navigation.sameDepthEnterMillis > 0 &&
                tokens.navigation.sameDepthExitMillis > 0 &&
                tokens.navigation.splitResizeMillis > 0 &&
                tokens.navigation.disclosureRotateMillis > 0,
        )
        assertTrue(
            tokens.controls.listPressMillis > 0 &&
                tokens.controls.listReleaseMillis > 0 &&
                tokens.controls.listSelectMillis > 0,
        )
    }
}
