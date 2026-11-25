package com.kyant.backdrop.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
actual fun LiquidPredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (Flow<LiquidBackEvent>) -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        onBack(
            progress.map { event ->
                LiquidBackEvent(
                    progress = event.progress,
                    touchX = event.touchX,
                    touchY = event.touchY,
                    swipeEdge = event.swipeEdge,
                )
            },
        )
    }
}
