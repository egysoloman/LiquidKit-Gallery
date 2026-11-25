package com.kyant.backdrop.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import com.kyant.backdrop.components.LiquidSystemBarAppearance
import com.kyant.backdrop.components.enableLiquidEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge()

        setContent {
            LiquidSystemBarAppearance(dark = isSystemInDarkTheme())
            MainContent()
        }
    }
}
