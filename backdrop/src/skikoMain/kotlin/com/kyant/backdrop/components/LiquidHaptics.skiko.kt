package com.kyant.backdrop.components

import androidx.compose.runtime.Composable

private val NoOpHaptics: (LiquidHaptic) -> Unit = {}

@Composable
internal actual fun rememberLiquidHaptics(): (LiquidHaptic) -> Unit = NoOpHaptics
