package com.kyant.backdrop.catalog.utils

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import com.kyant.backdrop.components.LiquidButtonText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton

@Composable
actual fun BackHandler(
    enabled: Boolean,
    onBack: () -> Unit
) {
    if (enabled) {
        LiquidButton(
            { if (enabled) onBack() },
            emptyBackdrop(),
            Modifier
                .padding(8f.dp)
                .safeDrawingPadding()
                .height(48f.dp),
            tint = Color(0xFF0088FF)
        ) {
            LiquidButtonText(
                "Back",
                Modifier.padding(horizontal = 8f.dp),
                style = TextStyle(fontSize = 16f.sp)
            )
        }
    }
}
