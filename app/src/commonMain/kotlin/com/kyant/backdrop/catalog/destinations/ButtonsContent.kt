package com.kyant.backdrop.catalog.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.kyant.backdrop.components.LiquidButtonText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.BackdropDemoScaffold
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.components.LiquidButtonDefaults

@Composable
fun ButtonsContent() {
    BackdropDemoScaffold { backdrop ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16f.dp)
        ) {
            LiquidButton(
                {},
                backdrop
            ) {
                LiquidButtonText(
                    "Transparent Liquid Button",
                    style = TextStyle(fontSize = 15f.sp)
                )
            }
            LiquidButton(
                {},
                backdrop,
                surfaceColor = LiquidButtonDefaults.SurfaceColor
            ) {
                LiquidButtonText(
                    "Surface Liquid Button",
                    style = TextStyle(fontSize = 15f.sp)
                )
            }
            LiquidButton(
                {},
                backdrop,
                tint = Color(0xFF0088FF)
            ) {
                LiquidButtonText(
                    "Tinted Liquid Button",
                    style = TextStyle(fontSize = 15f.sp)
                )
            }
            LiquidButton(
                {},
                backdrop,
                tint = Color(0xFFFF8D28)
            ) {
                LiquidButtonText(
                    "Tinted Liquid Button",
                    style = TextStyle(fontSize = 15f.sp)
                )
            }
        }
    }
}
