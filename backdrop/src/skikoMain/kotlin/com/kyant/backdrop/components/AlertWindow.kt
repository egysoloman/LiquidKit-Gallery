package com.kyant.backdrop.components

import androidx.compose.runtime.Composable

// Skiko owns the scrim of its modal scene.
@Composable
internal actual fun ConfigureAlertWindow(scrimAlpha: Float) = Unit

@Composable
actual fun SyncLiquidDialogSystemBars(dark: Boolean) = Unit
