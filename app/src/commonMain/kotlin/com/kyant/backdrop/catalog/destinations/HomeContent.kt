package com.kyant.backdrop.catalog.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.catalog.CatalogDestination
import com.kyant.backdrop.components.*

private val groups = listOf(
    "Liquid glass components" to listOf(
        "Glass appearance" to CatalogDestination.AdaptiveLuminanceGlass, "Liquid morph (iOS 26)" to CatalogDestination.LiquidMorph,
        "Toolbar" to CatalogDestination.Toolbar, "Navigation split view" to CatalogDestination.NavigationSplit,
        "Buttons" to CatalogDestination.Buttons, "Toggle" to CatalogDestination.Toggle, "Slider" to CatalogDestination.Slider,
        "Bottom tabs" to CatalogDestination.BottomTabs, "Dialog" to CatalogDestination.Dialog,
    ),
    "System UIs" to listOf("Lock screen (SDF texture)" to CatalogDestination.LockScreen,
        "Control center" to CatalogDestination.ControlCenter, "Magnifier" to CatalogDestination.Magnifier),
    "Experiments" to listOf("Glass playground" to CatalogDestination.GlassPlayground, "Progressive blur" to CatalogDestination.ProgressiveBlur,
        "Scroll container" to CatalogDestination.ScrollContainer, "Lazy scroll container" to CatalogDestination.LazyScrollContainer),
)

@Composable
fun HomeContent(onNavigate: (CatalogDestination) -> Unit) {
    val colors = liquidListColors()
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(query) {
        groups.mapNotNull { (title, entries) ->
            val matching = entries.filter { (name, _) -> name.contains(query.trim(), ignoreCase = true) }
            if (matching.isEmpty()) null else title to matching
        }
    }
    LiquidAppScaffold(
        bottomBar = { barBackdrop ->
            LiquidSearchField(
                query, { query = it }, "Search components", "Clear search",
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).widthIn(max = 540.dp),
                backdrop = barBackdrop,
                cancelLabel = "Cancel",
            )
        },
    ) { pageBackdrop, safePadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(Modifier.matchParentSize().layerBackdrop(pageBackdrop).background(colors.background)) {
            if (visible.isEmpty()) {
                LiquidUnavailableView(
                    "No matching components",
                    LiquidSymbol.Search,
                    Modifier.padding(safePadding).padding(bottom = 68.dp),
                )
            } else Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = safePadding.calculateTopPadding(),
                        start = safePadding.calculateStartPadding(layoutDirection),
                        end = safePadding.calculateEndPadding(layoutDirection),
                        bottom = safePadding.calculateBottomPadding() + 76.dp,
                    )
            ) {
                BasicText("Backdrop Catalog", Modifier.padding(16.dp, 20.dp, 16.dp, 16.dp),
                    style = TextStyle(colors.ink, 28.sp, FontWeight.Medium))
                visible.forEach { (title, entries) ->
                    Subtitle(title)
                    entries.forEach { (label, destination) -> ListItem({ onNavigate(destination) }, label) }
                }
            }
        }
    }
}

@Composable
private fun Subtitle(label: String) {
    BasicText(
        label,
        Modifier
            .padding(16f.dp, 24f.dp, 16f.dp, 8f.dp)
            .fillMaxWidth(),
        style = TextStyle(Color(0xFF0088FF), 15f.sp, FontWeight.Medium)
    )
}

@Composable
private fun ListItem(
    onClick: () -> Unit,
    label: String
) {
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White

    BasicText(
        label,
        Modifier
            .clickable(onClick = onClick)
            .padding(16f.dp)
            .fillMaxWidth(),
        style = TextStyle(contentColor, 17f.sp)
    )
}
