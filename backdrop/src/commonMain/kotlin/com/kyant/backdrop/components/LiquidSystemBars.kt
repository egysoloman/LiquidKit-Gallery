package com.kyant.backdrop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Synchronizes host platform status bar and gesture navigation bar appearance with [dark]
 * and disables forced system navigation bar contrast scrims so Liquid Glass content extends
 * cleanly edge-to-edge behind both the top status bar and bottom gesture indicator.
 */
@Composable
expect fun LiquidSystemBarAppearance(dark: Boolean, enabled: Boolean = true)

/**
 * Standard safe-area WindowInsets presets for edge-to-edge Liquid Glass apps.
 * Content extends full-bleed beneath the status bar and bottom gesture bar while interactive
 * chrome and scrollable tails remain cleanly inside the safe area.
 */
object LiquidSafeAreaDefaults {
    /** Full safe drawing area (status bar + cutout + gesture/nav bar + IME). */
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing

    /** Top status bar + display cutout + horizontal safe area for top navigation chrome. */
    val topBarInsets: WindowInsets
        @Composable get() = WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    /** Bottom gesture/navigation bar + horizontal safe area for floating bottom tab/tool bars. */
    val bottomBarInsets: WindowInsets
        @Composable get() = WindowInsets.navigationBars
            .union(WindowInsets.mandatorySystemGestures)
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

    /** Horizontal cutout / edge safe area for landscape & unfolded foldable layouts. */
    val horizontalInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

    /** Computes extra scrollable bottom clearance above a floating bottom bar (tab bar + safe area). */
    @Composable
    fun floatingBottomBarClearance(barHeight: Dp = 62.dp, extraMargin: Dp = 20.dp): Dp =
        bottomBarInsets.asPaddingValues().calculateBottomPadding() + barHeight + extraMargin
}

/** Pads content away from the top status bar & display cutout while allowing backgrounds to extend behind them. */
fun Modifier.liquidTopSafeArea(): Modifier = composed {
    windowInsetsPadding(LiquidSafeAreaDefaults.topBarInsets)
}

/** Pads floating bottom chrome away from the bottom gesture indicator & horizontal cutouts. */
fun Modifier.liquidBottomSafeArea(): Modifier = composed {
    windowInsetsPadding(LiquidSafeAreaDefaults.bottomBarInsets)
}

/** Pads content away from horizontal display cutouts & system bar edges. */
fun Modifier.liquidHorizontalSafeArea(): Modifier = composed {
    windowInsetsPadding(LiquidSafeAreaDefaults.horizontalInsets)
}

/**
 * Top-level framework container that pairs edge-to-edge content with a single shared [LayerBackdrop]
 * and floating bottom chrome (such as [LiquidTabBar] or [LiquidSafeAreaBar]) anchored cleanly
 * inside [LiquidSafeAreaDefaults.bottomBarInsets].
 *
 * Pass the provided `pageBackdrop` directly to [LiquidNavigationScaffold] (`pageBackdrop = pageBackdrop`)
 * so scrolling content records only ONE full-screen layer instead of nested duplicate FBOs.
 */
@Composable
fun LiquidAppScaffold(
    modifier: Modifier = Modifier,
    edgeToEdgeSystemBars: Boolean = true,
    bottomBar: (@Composable BoxScope.(backdrop: LayerBackdrop) -> Unit)? = null,
    content: @Composable BoxScope.(pageBackdrop: LayerBackdrop, safePadding: PaddingValues) -> Unit,
) {
    val appearance = liquidGlassAppearance()
    if (edgeToEdgeSystemBars) {
        LiquidSystemBarAppearance(dark = appearance.dark, enabled = true)
    }
    val pageBackdrop = rememberLayerBackdrop()
    val safePadding = LiquidSafeAreaDefaults.windowInsets.asPaddingValues()
    Box(modifier.fillMaxSize()) {
        content(pageBackdrop, safePadding)
        if (bottomBar != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .liquidBottomSafeArea(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                bottomBar(pageBackdrop)
            }
        }
    }
}
