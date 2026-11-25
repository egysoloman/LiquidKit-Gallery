package com.kyant.backdrop.components

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

@Composable
actual fun LiquidPredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (Flow<LiquidBackEvent>) -> Unit,
) {
    // Non-Android platforms rely on swipe-back gestures and native navigation chrome.
}
