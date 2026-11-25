package com.kyant.backdrop.components

import androidx.compose.runtime.Composable

/** Semantic iOS 27-calibrated tactile feedback events for Liquid Glass controls. */
enum class LiquidHaptic {
    /** Crisp detent tick when crossing a segment, tab, slider tick, wheel row, or reorder slot. */
    SelectionTick,
    /** Mechanical switch impulse when a Toggle flips state. */
    ToggleFlip,
    /** Distinct threshold detent when crossing a full-swipe commit or slider endpoint. */
    ThresholdDetent,
    /** Heavier lift impulse when long-pressing to pick up a draggable tile or menu. */
    LongPressLift,
}

@Composable
internal expect fun rememberLiquidHaptics(): (LiquidHaptic) -> Unit
