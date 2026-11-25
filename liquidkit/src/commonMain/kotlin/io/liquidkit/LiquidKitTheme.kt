package io.liquidkit

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.kyant.backdrop.components.GlassSpring
import com.kyant.backdrop.components.LiquidAppScaffold
import com.kyant.backdrop.components.LiquidGlassAppearance
import com.kyant.backdrop.components.LiquidGlassTheme
import com.kyant.backdrop.components.LiquidGlassTokens
import com.kyant.backdrop.components.LiquidGlassVariant
import com.kyant.backdrop.components.LiquidListColors
import com.kyant.backdrop.components.LiquidModalHost
import com.kyant.backdrop.components.LiquidSafeAreaDefaults
import com.kyant.backdrop.components.LiquidSystemBarAppearance
import com.kyant.backdrop.components.LocalLiquidGlassTokens
import com.kyant.backdrop.components.liquidBottomSafeArea
import com.kyant.backdrop.components.liquidGlassAppearance
import com.kyant.backdrop.components.liquidHorizontalSafeArea
import com.kyant.backdrop.components.liquidListColors
import com.kyant.backdrop.components.liquidTopSafeArea
import com.kyant.backdrop.components.rememberLiquidModalActive
import com.kyant.backdrop.components.warmUpLiquidGlassPipelines

typealias LiquidKitBackdrop = Backdrop
typealias LiquidKitLayerBackdrop = LayerBackdrop
typealias LiquidKitAppearance = LiquidGlassAppearance
typealias LiquidKitTokens = LiquidGlassTokens
typealias LiquidKitSpring = GlassSpring
typealias LiquidKitColors = LiquidListColors
typealias LiquidKitVariant = LiquidGlassVariant
typealias LiquidKitSafeAreaDefaults = LiquidSafeAreaDefaults

val LocalLiquidKitTokens = LocalLiquidGlassTokens

/**
 * Ambient scene backdrop automatically provided by [LiquidKitRoot].
 * Allows SwiftUI / Skip bridged components and Android Compose callers to sample live Liquid Glass
 * backdrops without manually threading a `Backdrop` parameter through every view.
 */
val LocalLiquidKitBackdrop = compositionLocalOf<Backdrop> { emptyBackdrop() }

/** Ambient modal backdrop recorded only when a modal alert, sheet, popover, or menu is active. */
val LocalLiquidKitModalBackdrop = compositionLocalOf<Backdrop> { emptyBackdrop() }

@Composable
fun rememberLiquidKitBackdrop(): LayerBackdrop = rememberLayerBackdrop()

@Composable
fun rememberSolidColorLiquidKitBackdrop(color: Color): Backdrop = rememberSolidColorBackdrop(color)

@Composable
fun rememberCombinedLiquidKitBackdrop(vararg backdrops: Backdrop): Backdrop =
    rememberCombinedBackdrop(*backdrops)

fun Modifier.liquidKitBackdrop(backdrop: LayerBackdrop, enabled: Boolean = true): Modifier =
    layerBackdrop(backdrop, enabled = enabled)

fun Modifier.liquidKitTopSafeArea(): Modifier = liquidTopSafeArea()

fun Modifier.liquidKitBottomSafeArea(): Modifier = liquidBottomSafeArea()

fun Modifier.liquidKitHorizontalSafeArea(): Modifier = liquidHorizontalSafeArea()

@Composable
fun LiquidKitSystemBarAppearance(dark: Boolean, enabled: Boolean = true) =
    LiquidSystemBarAppearance(dark = dark, enabled = enabled)

@Composable
fun liquidKitAppearance(): LiquidKitAppearance = liquidGlassAppearance()

@Composable
fun liquidKitColors(dark: Boolean = liquidKitAppearance().dark): LiquidKitColors = liquidListColors(dark)

@Composable
fun LiquidKitTheme(
    opacity: Float = .5f,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) = LiquidGlassTheme(opacity = opacity.coerceIn(0f, 1f), dark = dark, content = content)

fun warmUpLiquidKitPipelines() = warmUpLiquidGlassPipelines()

/**
 * Root LiquidKit host for both native Android Compose apps and Skip/SwiftUI screens.
 * Supplies theme tokens, automatic edge-to-edge system bar appearance, a zero-VRAM solid-color
 * root backdrop, a demand-gated modal backdrop, and [LiquidModalHost].
 */
@Composable
fun LiquidKitRoot(
    modifier: Modifier = Modifier,
    opacity: Float = .5f,
    dark: Boolean = isSystemInDarkTheme(),
    backgroundColor: Color? = null,
    edgeToEdgeSystemBars: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(350)
        warmUpLiquidKitPipelines()
    }
    LiquidKitTheme(opacity = opacity, dark = dark) {
        if (edgeToEdgeSystemBars) {
            LiquidSystemBarAppearance(dark = dark, enabled = true)
        }
        val colors = liquidKitColors(dark)
        val canvas = backgroundColor ?: colors.background
        val sceneBackdrop = rememberSolidColorBackdrop(canvas)
        val modalBackdrop = rememberLiquidKitBackdrop()
        LiquidModalHost(modifier = modifier.fillMaxSize()) {
            val modalActive = rememberLiquidModalActive()
            Box(
                Modifier.fillMaxSize()
                    .liquidKitBackdrop(modalBackdrop, enabled = modalActive)
                    .background(canvas),
            ) {
                CompositionLocalProvider(
                    LocalLiquidKitBackdrop provides sceneBackdrop,
                    LocalLiquidKitModalBackdrop provides modalBackdrop,
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * Framework-level edge-to-edge app container pairing a shared single-pass [LiquidKitLayerBackdrop]
 * with safe-area-aware floating bottom chrome (e.g. [LiquidKitTabBar]).
 */
@Composable
fun LiquidKitAppScaffold(
    modifier: Modifier = Modifier,
    edgeToEdgeSystemBars: Boolean = true,
    bottomBar: (@Composable BoxScope.(backdrop: LiquidKitLayerBackdrop) -> Unit)? = null,
    content: @Composable BoxScope.(pageBackdrop: LiquidKitLayerBackdrop, safePadding: PaddingValues) -> Unit,
) = LiquidAppScaffold(
    modifier = modifier,
    edgeToEdgeSystemBars = edgeToEdgeSystemBars,
    bottomBar = bottomBar,
    content = content,
)
