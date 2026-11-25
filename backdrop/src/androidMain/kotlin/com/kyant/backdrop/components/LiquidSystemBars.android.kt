package com.kyant.backdrop.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Configures an Android [ComponentActivity] for full edge-to-edge Liquid Glass rendering:
 * extends content behind both the top status bar / display cutout and the bottom gesture
 * navigation bar, removes forced system contrast scrims, and syncs initial bar icon luminance.
 */
fun ComponentActivity.enableLiquidEdgeToEdge(dark: Boolean = false) {
    val style = if (dark) {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    } else {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    configureLiquidWindow(window, dark)
}

private fun configureLiquidWindow(window: android.view.Window, dark: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.isStatusBarContrastEnforced = false
        }
    }
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
    }
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

@Composable
actual fun LiquidSystemBarAppearance(dark: Boolean, enabled: Boolean) {
    if (!enabled) return
    val view = LocalView.current
    SideEffect {
        val window = view.context.findHostActivity()?.window ?: return@SideEffect
        configureLiquidWindow(window, dark)
    }
    LaunchedEffect(view, dark) {
        view.post {
            val window = view.context.findHostActivity()?.window ?: return@post
            configureLiquidWindow(window, dark)
        }
    }
}
