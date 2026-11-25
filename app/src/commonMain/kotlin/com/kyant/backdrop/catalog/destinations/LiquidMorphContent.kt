package com.kyant.backdrop.catalog.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.BackdropDemoScaffold
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.utils.BackHandler
import com.kyant.backdrop.components.LocalLiquidContentColor
import com.kyant.backdrop.components.LiquidButtonText
import com.kyant.backdrop.components.liquidMenuDepth
import com.kyant.backdrop.components.liquidMenuMetrics
import com.kyant.backdrop.components.LiquidButtonDefaults
import androidx.compose.foundation.layout.BoxScope
import com.kyant.backdrop.components.LiquidAnchoredMenuHost
import com.kyant.backdrop.components.LiquidMenuPresentation
import com.kyant.backdrop.components.LiquidMenuHost
import com.kyant.backdrop.components.LiquidMenuItem
import com.kyant.backdrop.components.LiquidMenuPanel
import com.kyant.backdrop.components.LiquidMenuState
import com.kyant.backdrop.components.LiquidBorderlessButton
import com.kyant.backdrop.components.liquidBorderlessMenuTrigger
import com.kyant.backdrop.components.liquidMenuTrigger
import com.kyant.backdrop.components.rememberLiquidMenuState
import com.kyant.backdrop.components.LiquidToolbarDefaults
import com.kyant.backdrop.components.LiquidToolbarSlots
import com.kyant.backdrop.components.LiquidToolbarGroup
import com.kyant.backdrop.components.LiquidIcon
import com.kyant.backdrop.components.LiquidSymbol
import com.kyant.backdrop.components.LiquidToolbarButton
import com.kyant.backdrop.morph.LiquidMorphAlignment
import com.kyant.backdrop.morph.LiquidMorphConfig
import com.kyant.backdrop.morph.LiquidMorphContainer

@Immutable
private data class MorphDemoPreset(
    val label: String,
    val collapsedSize: DpSize,
    val expandedSize: DpSize,
    val expandedRadius: Dp,
    val config: LiquidMorphConfig,
    val isBar: Boolean = false,
    val isMultiButton: Boolean = false,
    val opensFromLeadingSlot: Boolean = false,
)

private val multiSymbols = listOf(LiquidSymbol.Bell, LiquidSymbol.Like, LiquidSymbol.DownloadCircle)

private val DemoPresets = listOf(
    MorphDemoPreset(
        label = "Multi",
        collapsedSize = DpSize(LiquidToolbarDefaults.width(multiSymbols), LiquidToolbarDefaults.height),
        expandedSize = DpSize(268.dp, 304.dp),
        expandedRadius = 34.dp,
        config = LiquidMorphConfig(
            alignment = LiquidMorphAlignment.TopTrailing,
            triggerBiasX = 0.32f,
            tintAlpha = 0.06f,
        ),
        isMultiButton = true,
    ),
    MorphDemoPreset(
        label = "Tall",
        collapsedSize = DpSize(128.dp, 44.dp),
        expandedSize = DpSize(244.dp, 510.dp),
        expandedRadius = 32.dp,
        config = LiquidMorphConfig(
            alignment = LiquidMorphAlignment.TopTrailing,
            triggerBiasX = -0.25f,
            tintAlpha = 0.06f,
        ),
        opensFromLeadingSlot = true,
    ),
    MorphDemoPreset(
        label = "Circle",
        collapsedSize = DpSize(48.dp, 48.dp),
        expandedSize = DpSize(256.dp, 236.dp),
        expandedRadius = 34.dp,
        config = LiquidMorphConfig(
            alignment = LiquidMorphAlignment.TopTrailing,
            triggerBiasX = 0f,
            tintAlpha = 0.12f,
        ),
    ),
    MorphDemoPreset(
        label = "Bar",
        collapsedSize = DpSize(356.dp, 48.dp),
        expandedSize = DpSize(228.dp, 127.dp),
        expandedRadius = 28.dp,
        config = LiquidMorphConfig(
            alignment = LiquidMorphAlignment.BottomLeading,
            triggerBiasX = -0.24f,
            blendStrength = 1.1f,
            tintAlpha = 0.14f,
        ),
        isBar = true,
    ),
)

@Composable
fun LiquidMorphContent() {
    var presetIndex by rememberSaveable { mutableIntStateOf(0) }
    var barExpanded by remember { mutableStateOf(false) }
    var slowMo by rememberSaveable { mutableStateOf(false) }
    var sourceSurface by rememberSaveable { mutableStateOf(true) }
    var folder by rememberSaveable { mutableStateOf("downloads") }
    var quality by rememberSaveable { mutableStateOf("standard") }
    var wifiOnly by rememberSaveable { mutableStateOf(false) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var offline by rememberSaveable { mutableStateOf(false) }
    var marks by rememberSaveable { mutableStateOf(false) }
    var background by rememberSaveable { mutableIntStateOf(0) }
    val menu = rememberLiquidMenuState { item ->
        when (item.key) {
            "downloads", "field", "ideas", "archive", "project", "inbox" -> folder = item.key
            "standard", "lossless" -> quality = item.key
            "wifi" -> wifiOnly = !wifiOnly
            "favorite" -> favorite = !favorite
            "pin" -> pinned = !pinned
            "offline" -> offline = !offline
            "marks" -> marks = !marks
        }
    }
    val qualities = listOf(
        LiquidMenuItem("standard", "标准音质", checked = quality == "standard"),
        LiquidMenuItem("lossless", "无损音质", checked = quality == "lossless"),
    )
    val preset = DemoPresets[presetIndex]
    val longMenu = preset.isMultiButton || preset.opensFromLeadingSlot
    val items = listOfNotNull(
        if (longMenu) LiquidMenuItem("pin", "固定到顶部", LiquidSymbol.Bookmark, checked = pinned, startsSection = true) else null,
        if (longMenu) LiquidMenuItem("offline", "离线可用", LiquidSymbol.Download, checked = offline,
            subtitle = if (preset.opensFromLeadingSlot) "在此设备上保留副本" else null) else null,
        LiquidMenuItem("location", "保存位置", LiquidSymbol.Download, children = listOf(
            LiquidMenuItem("downloads", "下载文件夹", checked = folder == "downloads", subtitle = "设备存储"),
            LiquidMenuItem("library", "资料库", children = listOf(
                LiquidMenuItem("field", "现场录音", checked = folder == "field"),
                LiquidMenuItem("ideas", "灵感片段", checked = folder == "ideas"),
            )),
            LiquidMenuItem("archive", "录音归档", checked = folder == "archive"),
            LiquidMenuItem("project", "项目文件", checked = folder == "project"),
            LiquidMenuItem("inbox", "收件箱", checked = folder == "inbox"),
        ), startsSection = true, subtitle = if (preset.opensFromLeadingSlot) "下载内容的存放位置" else null),
        LiquidMenuItem("quality", "音质", LiquidSymbol.List, children = qualities),
        LiquidMenuItem("wifi", "仅无线网络", LiquidSymbol.Grid, checked = wifiOnly, startsSection = true,
            subtitle = if (preset.opensFromLeadingSlot) "连接无线网络时下载" else null),
        if (longMenu) LiquidMenuItem("marks", "显示录音标记", LiquidSymbol.Bookmark, checked = marks, startsSection = true) else null,
        LiquidMenuItem("sync", "正在同步", LiquidSymbol.Download, enabled = false, startsSection = longMenu),
    )
    val actions = listOf(
        LiquidMenuItem("favorite", if (favorite) "已收藏" else "收藏",
            if (favorite) LiquidSymbol.Saved else LiquidSymbol.Bookmark),
        qualities[0].copy(symbol = LiquidSymbol.List),
        qualities[1].copy(symbol = LiquidSymbol.Grid),
    )
    val expanded = if (preset.isBar) barExpanded else menu.expanded
    val effectiveConfig = remember(preset, sourceSurface) {
        preset.config.copy(
            collapsedSurfaceColor = if (preset.isBar) preset.config.collapsedSurfaceColor else LiquidButtonDefaults.SurfaceColor,
            showsCollapsedSurface = sourceSurface,
        )
    }
    BackHandler(expanded) { if (preset.isBar) barExpanded = false else menu.back() }

    val backgroundColor = when (background) {
        1 -> Color(0xFFF4F5F6)
        2 -> Color(0xFF192329)
        else -> Color.Transparent
    }
    BackdropDemoScaffold(Modifier.drawWithContent {
        drawContent()
        if (backgroundColor.alpha > 0f) drawRect(backgroundColor)
    }) { backdrop ->
        val independentMenu = !sourceSurface && !preset.isBar
        val scene: @Composable BoxScope.() -> Unit = {
            if (preset.isBar) {
                Spacer(Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { barExpanded = !barExpanded })
            }
            val metrics = liquidMenuMetrics()
            val density = LocalDensity.current
            val safeTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val placement = if (longMenu) Modifier.padding(
                top = (maxOf(68.5.dp * metrics.scale, safeTop + 2.dp) - 24.dp).coerceAtLeast(0.dp),
                end = (27.dp * metrics.scale - 24.dp).coerceAtLeast(0.dp),
            ) else Modifier.statusBarsPadding().navigationBarsPadding().padding(
                top = if (preset.isBar) 0.dp else 16.dp, bottom = if (preset.isBar) 148.dp else 0.dp,
                start = if (preset.isBar) 2.dp else 8.dp, end = if (preset.isBar) 2.dp else 8.dp,
            )
            val panelSize = when {
                preset.opensFromLeadingSlot -> DpSize(metrics.width, maxOf(metrics.height(items, hasActions = true),
                    metrics.width * (preset.expandedSize.height / preset.expandedSize.width)))
                longMenu -> DpSize(metrics.width, metrics.height(items, hasActions = true))
                preset.isBar -> preset.expandedSize
                else -> DpSize(minOf(metrics.width, preset.expandedSize.width), preset.expandedSize.height)
            }
            val morphAlignment = when (effectiveConfig.alignment) {
                LiquidMorphAlignment.TopTrailing -> Alignment.TopEnd
                LiquidMorphAlignment.BottomLeading -> Alignment.BottomStart
                LiquidMorphAlignment.TopLeading -> Alignment.TopStart
                LiquidMorphAlignment.BottomTrailing -> Alignment.BottomEnd
            }

            Box(
                modifier = Modifier
                    .align(morphAlignment).then(placement),
            ) {
                if (independentMenu) {
                    Box(Modifier.padding(24.dp).size(preset.collapsedSize)) {
                        MorphToolbar(preset, menu, LocalLiquidContentColor.current, borderless = true)
                    }
                } else LiquidMorphContainer(
                    expanded = expanded,
                    onExpandedChange = { open ->
                        if (preset.isBar) barExpanded = open else if (open) menu.show() else menu.dismiss()
                    },
                    handleSurfaceTap = preset.isBar,
                    backdrop = backdrop,
                    collapsedSize = preset.collapsedSize,
                    expandedSize = panelSize,
                    modifier = Modifier.liquidMenuDepth(
                        menu,
                        inset = 24.dp,
                        trailing = effectiveConfig.alignment == LiquidMorphAlignment.TopTrailing ||
                            effectiveConfig.alignment == LiquidMorphAlignment.BottomTrailing,
                        bottom = effectiveConfig.alignment == LiquidMorphAlignment.BottomLeading ||
                            effectiveConfig.alignment == LiquidMorphAlignment.BottomTrailing,
                    ),
                    expandedCornerRadius = if (longMenu) metrics.radius else preset.expandedRadius,
                    config = effectiveConfig,
                    sourceAnchorInRoot = if (preset.isBar) null else menu.openingBounds,
                    slowMo = slowMo,
                    collapsedContent = {
                        val ink = LocalLiquidContentColor.current
                        if (preset.isBar) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                BasicText("+", style = TextStyle(ink.copy(0.8f), 18.sp, FontWeight.Medium))
                                BasicText(
                                    "Send message...",
                                    modifier = Modifier.weight(1f),
                                    style = TextStyle(ink.copy(0.48f), 14.sp),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ink.copy(0.10f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    BasicText("↑", style = TextStyle(ink.copy(0.8f), 13.sp, FontWeight.SemiBold))
                                }
                            }
                        } else {
                            MorphToolbar(preset, menu, ink)
                        }
                    },
                    expandedContent = {
                        if (preset.isBar) {
                            val ink = LocalLiquidContentColor.current
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                MenuItemRow("✓", "Full Access", ink)
                                Spacer(Modifier.fillMaxWidth().height(0.8.dp).background(ink.copy(alpha = .14f)))
                                MenuItemRow("✎", "Workspace Write", ink)
                                Spacer(Modifier.fillMaxWidth().height(0.8.dp).background(ink.copy(alpha = .14f)))
                                MenuItemRow("○", "Read Only", ink)
                            }
                        } else {
                            LiquidMenuPanel(menu, items, Modifier.fillMaxSize(), actions, ink = LocalLiquidContentColor.current)
                        }
                    },
                )
            }

            // Bottom preset & slow-mo toolbar cleanly above "Pick an image"
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DemoPresets.forEachIndexed { idx, item ->
                    val selected = idx == presetIndex
                    LiquidButton(
                        onClick = {
                            presetIndex = idx
                            menu.dismiss()
                            barExpanded = false
                        },
                        backdrop = backdrop,
                        tint = if (selected) Color(0xFF0088FF) else Color.Unspecified,
                        surfaceColor = if (selected) Color.Unspecified else LiquidButtonDefaults.SurfaceColor,
                    ) {
                        LiquidButtonText(
                            item.label,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
                LiquidButton(
                    onClick = { slowMo = !slowMo },
                    backdrop = backdrop,
                    tint = if (slowMo) Color(0xFFFF4F79) else Color.Unspecified,
                    surfaceColor = if (slowMo) Color.Unspecified else LiquidButtonDefaults.SurfaceColor,
                ) {
                    LiquidButtonText(
                        if (slowMo) "0.25x" else "1.0x",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
            LiquidButton(
                onClick = { sourceSurface = !sourceSurface }, backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 8.dp, bottom = 16.dp)
                    .semantics { contentDescription = "切换按钮底面" },
                surfaceColor = LiquidButtonDefaults.SurfaceColor,
            ) {
                LiquidButtonText(if (sourceSurface) "有底面" else "无边框", style = TextStyle(fontSize = 14.sp))
            }
            LiquidButton(
                onClick = { background = (background + 1) % 3 }, backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 16.dp)
                    .semantics { contentDescription = "切换验证背景" },
                surfaceColor = LiquidButtonDefaults.SurfaceColor,
            ) {
                LiquidButtonText(listOf("原图", "浅底", "深底")[background], style = TextStyle(fontSize = 14.sp))
            }
        }
        if (independentMenu) {
            LiquidAnchoredMenuHost(menu, backdrop, items, Modifier.fillMaxSize(), LiquidMenuPresentation.Popup,
                actions = actions, slowMotion = slowMo, content = scene)
        } else {
            LiquidMenuHost(menu, backdrop, Modifier.fillMaxSize(), content = scene)
        }
    }
}

@Composable
private fun MorphToolbar(preset: MorphDemoPreset, menu: LiquidMenuState, ink: Color, borderless: Boolean = false) {
    val slots = when {
        preset.isMultiButton -> listOf(
            LiquidSymbol.Bell to "Notifications",
            LiquidSymbol.Like to "Like",
            LiquidSymbol.DownloadCircle to "Download",
        )
        preset.opensFromLeadingSlot -> listOf(LiquidSymbol.More to "更多选项", LiquidSymbol.Ascending to "升序")
        else -> listOf(LiquidSymbol.More to "更多选项")
    }
    if (borderless) {
        val action: @Composable (Int, Modifier) -> Unit = { index, area ->
            val (symbol, label) = slots[index]
            val opens = index == (if (preset.opensFromLeadingSlot) 0 else slots.lastIndex)
            val labeled = area.semantics { contentDescription = label }
            if (opens) Box(labeled.liquidBorderlessMenuTrigger(menu), contentAlignment = Alignment.Center) { LiquidIcon(symbol, ink) }
            else LiquidBorderlessButton({}, labeled) { LiquidIcon(symbol, ink) }
        }
        if (preset.isMultiButton) LiquidToolbarSlots(multiSymbols, content = action)
        else Row(Modifier.fillMaxSize()) { slots.indices.forEach { action(it, Modifier.weight(1f).fillMaxHeight()) } }
        return
    }
    // The morph shader already paints the Surface tint inside the animated glass outline.
    LiquidToolbarGroup(Modifier.fillMaxSize(), surfaceColor = Color.Transparent) {
        if (preset.isMultiButton) {
            LiquidToolbarSlots(multiSymbols) { index, area ->
                val (symbol, label) = slots[index]
                val trigger = index == slots.lastIndex
                LiquidToolbarButton(label, { if (trigger) menu.show() },
                    area.then(if (trigger) Modifier.liquidMenuTrigger(menu) else Modifier)) { LiquidIcon(symbol, ink) }
            }
        } else Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            slots.forEachIndexed { index, (symbol, label) ->
                val opensMenu = index == (if (preset.opensFromLeadingSlot) 0 else slots.lastIndex)
                LiquidToolbarButton(
                    label = label,
                    onClick = { if (opensMenu) menu.show() },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .then(if (opensMenu) Modifier.liquidMenuTrigger(menu) else Modifier),
                ) { LiquidIcon(symbol, ink) }
            }
        }
    }
}

@Composable
private fun MenuItemRow(icon: String, label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(icon, style = TextStyle(color.copy(alpha = 0.85f), 15.sp))
        BasicText(label, style = TextStyle(color, 15.sp, FontWeight.Medium))
    }
}
