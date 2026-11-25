package com.kyant.backdrop.components

import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal actual fun ConfigureAlertWindow(scrimAlpha: Float) {
    val view = LocalView.current
    DisposableEffect(view, scrimAlpha) {
        val window = generateSequence(view) { it.parent as? View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        val original = window?.attributes?.dimAmount
        window?.setDimAmount(scrimAlpha.coerceIn(0f, 1f))
        onDispose { if (original != null) window.setDimAmount(original) }
    }
}

@Composable
actual fun SyncLiquidDialogSystemBars(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, dark) {
        val window = generateSequence(view) { it.parent as? View }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
            } else {
                @Suppress("DEPRECATION")
                val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                @Suppress("DEPRECATION")
                val current = window.decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (dark) current and flags.inv() else current or flags
            }
        }
        onDispose {}
    }
}
