package com.kyant.backdrop.catalog

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.catalog.destinations.GlassAppearanceContent
import com.kyant.backdrop.catalog.destinations.BottomTabsContent
import com.kyant.backdrop.catalog.destinations.ButtonsContent
import com.kyant.backdrop.catalog.destinations.ControlCenterContent
import com.kyant.backdrop.catalog.destinations.DialogContent
import com.kyant.backdrop.catalog.destinations.GlassPlaygroundContent
import com.kyant.backdrop.catalog.destinations.HomeContent
import com.kyant.backdrop.catalog.destinations.LazyScrollContainerContent
import com.kyant.backdrop.catalog.destinations.LiquidMorphContent
import com.kyant.backdrop.catalog.destinations.LockScreenContent
import com.kyant.backdrop.catalog.destinations.MagnifierContent
import com.kyant.backdrop.catalog.destinations.ProgressiveBlurContent
import com.kyant.backdrop.catalog.destinations.ScrollContainerContent
import com.kyant.backdrop.catalog.destinations.SliderContent
import com.kyant.backdrop.catalog.destinations.ToggleContent
import com.kyant.backdrop.catalog.destinations.ToolbarContent
import com.kyant.backdrop.catalog.destinations.SplitCatalogContent
import com.kyant.backdrop.components.LiquidGlassTheme
import com.kyant.backdrop.components.LiquidNavigationTransition
import com.kyant.backdrop.catalog.utils.BackHandler

@Composable
fun MainContent() {
    var opacity by rememberSaveable { mutableFloatStateOf(.5f) }
    val isLightTheme = !isSystemInDarkTheme()

    LiquidGlassTheme(opacity = opacity) {
        CompositionLocalProvider(
            LocalIndication provides ripple(color = if (isLightTheme) Color.Black else Color.White)
        ) {
            var destination by rememberSaveable { mutableStateOf(CatalogDestination.Home) }

            BackHandler(destination != CatalogDestination.Home) {
                destination = CatalogDestination.Home
            }

            LiquidNavigationTransition(destination, { if (it == CatalogDestination.Home) 0 else 1 }) { displayed ->
                when (displayed) {
                    CatalogDestination.Home -> HomeContent(onNavigate = { destination = it })

                    CatalogDestination.LiquidMorph -> LiquidMorphContent()
                    CatalogDestination.Toolbar -> ToolbarContent()
                    CatalogDestination.NavigationSplit -> SplitCatalogContent { destination = CatalogDestination.Home }
                    CatalogDestination.Buttons -> ButtonsContent()
                    CatalogDestination.Toggle -> ToggleContent()
                    CatalogDestination.Slider -> SliderContent()
                    CatalogDestination.BottomTabs -> BottomTabsContent()
                    CatalogDestination.Dialog -> DialogContent()

                    CatalogDestination.LockScreen -> LockScreenContent()
                    CatalogDestination.ControlCenter -> ControlCenterContent()
                    CatalogDestination.Magnifier -> MagnifierContent()

                    CatalogDestination.GlassPlayground -> GlassPlaygroundContent()
                    CatalogDestination.AdaptiveLuminanceGlass -> GlassAppearanceContent(opacity) { opacity = it }
                    CatalogDestination.ProgressiveBlur -> ProgressiveBlurContent()
                    CatalogDestination.ScrollContainer -> ScrollContainerContent()
                    CatalogDestination.LazyScrollContainer -> LazyScrollContainerContent()
                }
            }
        }
    }
}
