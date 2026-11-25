package com.kyant.backdrop.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow

/** Platform-neutral snapshot of an active predictive back gesture frame. */
@Immutable
data class LiquidBackEvent(
    val progress: Float,
    val touchX: Float = 0f,
    val touchY: Float = 0f,
    val swipeEdge: Int = 0,
)

/**
 * Multiplatform predictive-back handler. On Android 14+ (API 34+), delivers real-time gesture
 * progress via `androidx.activity.compose.PredictiveBackHandler` so navigation stacks and sheets
 * respond interactively to system edge back swipes.
 */
@Composable
expect fun LiquidPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (Flow<LiquidBackEvent>) -> Unit,
)
