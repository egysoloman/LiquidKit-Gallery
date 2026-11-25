package com.liquidkit.swiftgallery

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.components.enableLiquidEdgeToEdge
import io.liquidkit.LiquidKitRoot

class SwiftGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember(context) { context.getSharedPreferences("swift_gallery_prefs", Context.MODE_PRIVATE) }
            val favoritesStore = remember(prefs) { FavoritesStore(prefs) }
            var appearanceMode by remember(prefs) {
                val saved = prefs.getString("appearanceMode", AppearanceMode.System.name)
                mutableStateOf(AppearanceMode.entries.firstOrNull { it.name == saved } ?: AppearanceMode.System)
            }
            val systemDark = isSystemInDarkTheme()
            val dark = when (appearanceMode) {
                AppearanceMode.System -> systemDark
                AppearanceMode.Light -> false
                AppearanceMode.Dark -> true
            }
            CompositionLocalProvider(LocalFavoritesStore provides favoritesStore) {
                LiquidKitRoot(
                    modifier = Modifier.fillMaxSize(),
                    opacity = 0.5f,
                    dark = dark,
                    edgeToEdgeSystemBars = true,
                ) {
                    SwiftGalleryHomeView(
                        appearanceMode = appearanceMode,
                        onAppearanceChange = { next ->
                            appearanceMode = next
                            prefs.edit().putString("appearanceMode", next.name).apply()
                        },
                    )
                }
            }
        }
    }
}
