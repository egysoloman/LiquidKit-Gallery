package com.kyant.backdrop.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberLiquidHaptics(): (LiquidHaptic) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { haptic ->
            val constant = when (haptic) {
                LiquidHaptic.SelectionTick ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.SEGMENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }

                LiquidHaptic.ToggleFlip ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.TOGGLE_ON
                    } else {
                        HapticFeedbackConstants.CONTEXT_CLICK
                    }

                LiquidHaptic.ThresholdDetent ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                    } else {
                        HapticFeedbackConstants.CONTEXT_CLICK
                    }

                LiquidHaptic.LongPressLift -> HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
        }
    }
}
