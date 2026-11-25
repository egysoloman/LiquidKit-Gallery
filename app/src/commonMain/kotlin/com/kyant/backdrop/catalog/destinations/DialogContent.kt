package com.kyant.backdrop.catalog.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.BackdropDemoScaffold
import com.kyant.backdrop.catalog.utils.LoremIpsum
import com.kyant.backdrop.components.LiquidActionButton
import com.kyant.backdrop.components.LiquidActionSheet
import com.kyant.backdrop.components.LiquidActionStyle
import com.kyant.backdrop.components.LiquidAlert
import com.kyant.backdrop.components.LiquidAlertAction
import com.kyant.backdrop.components.LiquidAlertRole
import com.kyant.backdrop.components.LiquidFormSection
import com.kyant.backdrop.components.LiquidLabeledContent
import com.kyant.backdrop.components.LiquidModalHost
import com.kyant.backdrop.components.LiquidPopover
import com.kyant.backdrop.components.LiquidSheet
import com.kyant.backdrop.components.LiquidSheetDetent
import com.kyant.backdrop.components.LiquidTextField
import com.kyant.backdrop.components.liquidGlassAppearance
import com.kyant.backdrop.components.liquidListColors
import com.kyant.backdrop.components.liquidPopoverAnchor

private enum class CatalogModal { None, Alert, ActionSheet, Sheet, Popover }

@Composable
fun DialogContent() {
    val appearance = liquidGlassAppearance()
    val colors = liquidListColors(appearance.dark)
    val accent = if (appearance.dark) Color(0xff0a84ff) else Color(0xff007aff)
    var activeModal by remember { mutableStateOf(CatalogModal.Alert) }
    var lastResult by remember { mutableStateOf("Liquid Glass Alert ready") }
    var sheetNote by remember { mutableStateOf("") }
    val popoverAnchor = remember { mutableStateOf<Rect?>(null) }

    LiquidModalHost {
        BackdropDemoScaffold { backdrop ->
            Column(
                Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(
                    lastResult,
                    style = TextStyle(colors.ink, 15.sp, FontWeight.Medium),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LiquidActionButton(
                        "Alert",
                        { activeModal = CatalogModal.Alert },
                        accent,
                        Modifier.weight(1f),
                        style = LiquidActionStyle.GlassProminent,
                        backdrop = backdrop,
                    )
                    LiquidActionButton(
                        "Action Sheet",
                        { activeModal = CatalogModal.ActionSheet },
                        Color.Unspecified,
                        Modifier.weight(1f),
                        style = LiquidActionStyle.Glass,
                        backdrop = backdrop,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LiquidActionButton(
                        "Sheet",
                        { activeModal = CatalogModal.Sheet },
                        Color.Unspecified,
                        Modifier.weight(1f),
                        style = LiquidActionStyle.Glass,
                        backdrop = backdrop,
                    )
                    LiquidActionButton(
                        "Popover",
                        { activeModal = CatalogModal.Popover },
                        Color.Unspecified,
                        Modifier.weight(1f).liquidPopoverAnchor(popoverAnchor),
                        style = LiquidActionStyle.Glass,
                        backdrop = backdrop,
                    )
                }
            }

            val close = { activeModal = CatalogModal.None }
            when (activeModal) {
                CatalogModal.Alert -> LiquidAlert(
                    title = "Liquid Glass Alert",
                    actions = listOf(
                        LiquidAlertAction("Cancel", LiquidAlertRole.Cancel) {
                            lastResult = "Alert cancelled"
                            close()
                        },
                        LiquidAlertAction("Confirm", LiquidAlertRole.Primary) {
                            lastResult = "Alert confirmed"
                            close()
                        },
                    ),
                    onDismissRequest = close,
                    tint = accent,
                    message = LoremIpsum.take(120) + "…",
                    colors = colors,
                    backdrop = backdrop,
                )

                CatalogModal.ActionSheet -> LiquidActionSheet(
                    title = "Display Options",
                    message = "Select how items are presented.",
                    actions = listOf(
                        LiquidAlertAction("Grid View", LiquidAlertRole.Primary) { lastResult = "Selected Grid View" },
                        LiquidAlertAction("List View") { lastResult = "Selected List View" },
                        LiquidAlertAction("Reset Layout", LiquidAlertRole.Destructive) { lastResult = "Reset Layout" },
                    ),
                    onDismissRequest = close,
                    tint = accent,
                    cancelLabel = "Cancel",
                    backdrop = backdrop,
                    colors = colors,
                )

                CatalogModal.Sheet -> LiquidSheet(
                    onDismissRequest = close,
                    backdrop = backdrop,
                    title = "Inspector Sheet",
                    doneLabel = "Done",
                    tint = accent,
                    detents = listOf(LiquidSheetDetent.Medium, LiquidSheetDetent.Large),
                    colors = colors,
                ) {
                    LiquidFormSection("Details", colors = colors) {
                        row { LiquidLabeledContent("Surface", "Refractive Glass", colors = colors) }
                        row { LiquidLabeledContent("Detents", "Medium · Large", colors = colors) }
                        row { LiquidTextField(sheetNote, { sheetNote = it }, "Notes", tint = accent, colors = colors) }
                    }
                }

                CatalogModal.Popover -> LiquidPopover(
                    onDismissRequest = close,
                    anchorBoundsInRoot = popoverAnchor.value,
                    backdrop = backdrop,
                    colors = colors,
                ) {
                    BasicText("Catalog Popover", style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold))
                    BasicText("Anchored glass surface with automatic screen clamping.", style = TextStyle(colors.secondary, 14.sp))
                }

                CatalogModal.None -> Unit
            }
        }
    }
}
