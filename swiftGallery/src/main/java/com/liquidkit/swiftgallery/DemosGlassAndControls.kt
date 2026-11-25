package com.liquidkit.swiftgallery

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.components.LiquidAnchoredMenuHost
import com.kyant.backdrop.components.LiquidMenuItem
import com.kyant.backdrop.components.liquidMenuTrigger
import com.kyant.backdrop.components.rememberLiquidMenuState
import io.liquidkit.*
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
// Category 1: iOS 27 & Liquid Glass
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun LiquidGlassEffectsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var isExpanded by remember { mutableStateOf(false) }
    var tintName by remember { mutableStateOf("青色") }
    var isInteractive by remember { mutableStateOf(true) }
    val tintMap = remember {
        mapOf(
            "青色" to Color(0xff00c7be),
            "蓝色" to Color(0xff007aff),
            "紫色" to Color(0xffaf52de),
            "粉色" to Color(0xffff2d55),
            "橙色" to Color(0xffff9500),
        )
    }
    val selectedTint = tintMap[tintName] ?: Color(0xff00c7be)

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "基础材质变体：regular / clear / identity",
            subtitle = "通过 .glassEffect(_:in:) 为任意视图与形状赋予 Liquid Glass 材质。",
            code = """
                HStack(spacing: 12) {
                    Text("regular").glassEffect(.regular, in: Capsule())
                    Text("clear").glassEffect(.clear, in: Capsule())
                    Text("identity").glassEffect(.identity, in: Capsule())
                }
            """,
        ) {
            GlassMeshPreviewBox { glass ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (v in LiquidKitVariant.entries) {
                        LiquidKitGlassSurface(backdrop = glass, variant = v) {
                            BasicText(
                                v.name.lowercase(),
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }
        }

        DemoSection(
            title = "着色 (.tint) 与交互反馈 (.interactive)",
            subtitle = "支持链式调用 .tint(_:) 与 .interactive(_:)，触摸时产生弹性高光与形变反馈。",
            code = """
                Label("播放", systemImage: "play.fill")
                    .glassEffect(.regular.tint(selectedTint).interactive(isInteractive), in: Capsule())
            """,
        ) {
            LiquidKitSegmentedPicker(tintMap.keys.toList(), tintName, { tintName = it }, colors = colors)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("启用 .interactive() 触控反馈", Modifier.weight(1f), style = TextStyle(colors.ink, 15.sp))
                LiquidKitToggle({ isInteractive }, { isInteractive = it })
            }
            GlassMeshPreviewBox { glass ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiquidKitGlassSurface(
                        backdrop = glass,
                        variant = LiquidKitVariant.Regular,
                        tint = selectedTint,
                        interactive = isInteractive,
                        onClick = {},
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiquidKitIcon(LiquidKitSymbol.Play, Color.White, Modifier.size(16.dp))
                            BasicText("播放", style = TextStyle(Color.White, 14.sp, FontWeight.SemiBold))
                        }
                    }
                    LiquidKitGlassSurface(
                        modifier = Modifier.size(46.dp),
                        backdrop = glass,
                        shape = CircleShape,
                        variant = LiquidKitVariant.Regular,
                        tint = Color(0xffff2d55),
                        interactive = isInteractive,
                        onClick = {},
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LiquidKitIcon(LiquidKitSymbol.Heart, Color.White, Modifier.size(20.dp))
                        }
                    }
                    LiquidKitGlassSurface(
                        backdrop = glass,
                        shape = RoundedCornerShape(14.dp),
                        variant = LiquidKitVariant.Clear,
                        tint = selectedTint,
                        interactive = isInteractive,
                        onClick = {},
                    ) {
                        BasicText("圆角矩形", Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = TextStyle(Color.White, 13.sp, FontWeight.Medium))
                    }
                }
            }
        }

        DemoSection(
            title = "GlassEffectContainer 邻近融合动画",
            subtitle = "将多个玻璃元素放入 LiquidKitGlassEffectContainer，切换状态时相邻玻璃会自动融合成形。",
            code = """
                GlassEffectContainer(spacing: 22) {
                    HStack(spacing: 14) {
                        Button { isExpanded.toggle() } label: { Image(systemName: isExpanded ? "xmark" : "plus") }
                            .glassEffect(.regular.interactive(), in: Circle())
                        if isExpanded { /* 展开玻璃气泡组 */ }
                    }
                }
            """,
        ) {
            GlassMeshPreviewBox { glass ->
                val expandProgress by animateFloatAsState(
                    targetValue = if (isExpanded) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 340f),
                    label = "GlassContainerExpand",
                )
                LiquidKitGlassEffectContainer(
                    backdrop = glass,
                    modifier = Modifier.fillMaxSize(),
                    spacing = 14.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (expandProgress > 0.02f) {
                            val targets = listOf(
                                LiquidKitSymbol.Rename to -22f,
                                LiquidKitSymbol.Camera to 32f,
                                LiquidKitSymbol.Send to 86f,
                            )
                            val reveal = ((expandProgress - 0.12f) / 0.88f).coerceIn(0f, 1f)
                            for ((sym, targetDp) in targets) {
                                LiquidKitToolbarGroup(
                                    Modifier
                                        .offset { IntOffset((lerp(-80f, targetDp, expandProgress).dp.toPx()).roundToInt(), 0) }
                                        .size(44.dp),
                                    backdrop = glass,
                                ) {
                                    LiquidKitToolbarButton(
                                        sym.name,
                                        {},
                                        Modifier.fillMaxSize().graphicsLayer {
                                            alpha = reveal
                                            val s = lerp(0.55f, 1f, reveal)
                                            scaleX = s
                                            scaleY = s
                                        },
                                    ) {
                                        LiquidKitIcon(sym, Color.White, Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        LiquidKitToolbarGroup(
                            Modifier
                                .offset { IntOffset((-80.dp.toPx() * expandProgress).roundToInt(), 0) }
                                .size(48.dp),
                            backdrop = glass,
                        ) {
                            LiquidKitToolbarButton("切换展开", { isExpanded = !isExpanded }, Modifier.fillMaxSize()) {
                                LiquidKitIcon(if (isExpanded) LiquidKitSymbol.Close else LiquidKitSymbol.Add, Color.White, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            BasicText("点击 + / × 按钮观察玻璃气泡融合/拆分过渡", style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

@Composable
private fun GlassMeshPreviewBox(content: @Composable BoxScope.(com.kyant.backdrop.Backdrop) -> Unit) {
    val scene = rememberLayerBackdrop()
    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        LiquidKitMeshGradient(
            width = 3,
            height = 3,
            colors = listOf(
                Color(0xff00c7be), Color(0xff007aff), Color(0xff5856d6),
                Color(0xff30d158), Color(0xff30b0c7), Color(0xffaf52de),
                Color(0xff007aff), Color(0xffff2d55), Color(0xffff9500),
            ),
            modifier = Modifier.matchParentSize().layerBackdrop(scene),
        )
        content(scene)
    }
}

@Composable
fun GlassButtonsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var tapCount by remember { mutableIntStateOf(0) }
    var sizeChoice by remember { mutableStateOf("regular") }
    val sizeMap = mapOf(
        "mini" to LiquidKitControlSize.Mini,
        "small" to LiquidKitControlSize.Small,
        "regular" to LiquidKitControlSize.Regular,
        "large" to LiquidKitControlSize.Large,
    )
    val ctrlSize = sizeMap[sizeChoice] ?: LiquidKitControlSize.Regular

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = ".glass 与 .glassProminent 按钮样式",
            subtitle = "系统原生 Liquid Glass 按钮样式，自动处理高光、半透明折射与按压弹动。",
            code = """
                HStack(spacing: 14) {
                    Button("Glass 按钮") { tapCount += 1 }.buttonStyle(.glass)
                    Button("Prominent 按钮") { tapCount += 1 }.buttonStyle(.glassProminent).tint(.blue)
                }
            """,
        ) {
            val scene = rememberLayerBackdrop()
            Box(Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Spacer(
                    Modifier.matchParentSize().layerBackdrop(scene)
                        .background(Brush.linearGradient(listOf(Color(0xff5856d6), Color(0xff00c7be), Color(0xff34c759)))),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidKitActionButton("Glass 按钮", { tapCount++ }, backdrop = scene, style = LiquidKitActionStyle.Glass)
                    LiquidKitActionButton("Prominent", { tapCount++ }, backdrop = scene, tint = Color(0xff007aff), style = LiquidKitActionStyle.GlassProminent)
                }
            }
            BasicText("已触发点击：$tapCount 次", style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "圆形图标、胶囊与尺寸变体",
            subtitle = "配合 shape 与 controlSize 实现工具栏与浮层操作按钮。",
            code = """
                Button(action: {}) { Image(systemName: "sparkles") }
                    .buttonStyle(.glass).buttonBorderShape(.circle).controlSize(controlSize)
            """,
        ) {
            LiquidKitSegmentedPicker(sizeMap.keys.toList(), sizeChoice, { sizeChoice = it }, colors = colors)
            val scene = rememberLayerBackdrop()
            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Spacer(
                    Modifier.matchParentSize().layerBackdrop(scene)
                        .background(Brush.horizontalGradient(listOf(Color(0xffaf52de), Color(0xffff2d55), Color(0xffff9500)))),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiquidKitActionButton(
                        "", { tapCount++ }, backdrop = scene, style = LiquidKitActionStyle.Glass,
                        leadingSymbol = LiquidKitSymbol.Sparkles, controlSize = ctrlSize, shape = LiquidKitButtonShape.Circle,
                    )
                    LiquidKitActionButton(
                        "分享", { tapCount++ }, backdrop = scene, tint = Color(0xff5856d6), style = LiquidKitActionStyle.GlassProminent,
                        leadingSymbol = LiquidKitSymbol.Share, controlSize = ctrlSize,
                    )
                    LiquidKitActionButton(
                        "删除", { tapCount++ }, backdrop = scene, style = LiquidKitActionStyle.Glass,
                        controlSize = ctrlSize, destructive = true,
                    )
                }
            }
        }
    }
}

@Composable
fun Ios27NewFeaturesDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var selectedTab by remember { mutableStateOf("overview") }
    var sliderValue by remember { mutableFloatStateOf(50f) }
    var crossPage by remember { mutableStateOf("A") }
    var tiles by remember {
        mutableStateOf(
            listOf(
                Triple("1", "玻璃", Color(0xff00c7be)),
                Triple("2", "动画", Color(0xffaf52de)),
                Triple("3", "图表", Color(0xffff9500)),
                Triple("4", "布局", Color(0xff007aff)),
                Triple("5", "列表", Color(0xff34c759)),
                Triple("6", "导航", Color(0xffff2d55)),
            ),
        )
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "1. iOS 27 TabsPickerStyle (.pickerStyle(.tabs))",
            subtitle = "静息胶囊阴影 + 按压/拖拽液态玻璃折射透镜。",
            code = """
                Picker("模块", selection: ${"$"}selectedTab) {
                    ForEach(TabChoice.allCases) { Text(${"$"}0.rawValue).tag(${"$"}0) }
                }.pickerStyle(.tabs)
            """,
        ) {
            LiquidKitTabsPicker(
                options = listOf(
                    LiquidKitTabOption("overview", "概览"),
                    LiquidKitTabOption("design", "设计"),
                    LiquidKitTabOption("code", "源码"),
                    LiquidKitTabOption("test", "测试"),
                ),
                selectedId = selectedTab,
                onSelect = { selectedTab = it },
                colors = colors,
            )
            BasicText("当前选中：$selectedTab", style = TextStyle(colors.secondary, 13.sp))
        }

        DemoSection(
            title = "2. iOS 27 Reorderable Grid (.reorderContainer)",
            subtitle = "长按任意色块并拖动即可跨格重排。",
            code = """
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 85))]) {
                    ForEach(tiles) { TileCard(tile: ${"$"}0) }.reorderable()
                }.reorderContainer(for: ReorderTile.self) { applyReorder(${"$"}0, to: &tiles) }
            """,
        ) {
            LiquidKitReorderableGrid(
                items = tiles,
                key = { it.first },
                onReorder = { tiles = it },
                columns = 3,
                tileHeight = 68.dp,
            ) { tile, dragging ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(tile.third, tile.third.copy(alpha = if (dragging) .95f else .75f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(tile.second, style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                }
            }
            BasicText("当前顺序：${tiles.joinToString(" → ") { it.second }}", style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "3. iOS 27 CrossFade 转场 (.navigationTransition(.crossFade))",
            subtitle = "使用 LiquidKitViewTransition(.CrossFade) 实现零离屏模糊开销的平滑淡入淡出切换。",
            code = """
                NavigationLink("进入 CrossFade 目标页") {
                    CrossFadeDestinationView().navigationTransition(.crossFade)
                }
            """,
        ) {
            LiquidKitViewTransition(targetState = crossPage, style = LiquidKitViewTransitionStyle.CrossFade) { pg ->
                Box(
                    Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (pg == "A") Color(0xff007aff).copy(alpha = .16f) else Color(0xffaf52de).copy(alpha = .18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("当前视图：$pg（CrossFade 转场）", style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold))
                }
            }
            LiquidKitActionButton(
                "切换 CrossFade 目标视图",
                { crossPage = if (crossPage == "A") "B" else "A" },
                tint = Color(0xff007aff),
                style = LiquidKitActionStyle.Filled,
            )
        }

        DemoSection(
            title = "4. SliderTick 与 neutralValue 刻度滑块 (iOS 26/27)",
            subtitle = "支持声明式刻度吸附与中性点（50）双向填充。",
            code = """
                Slider(value: ${"$"}sliderValue, in: 0...100, neutralValue: 50) {
                    Text("均衡度")
                } ticks: { SliderTick(0); SliderTick(25); SliderTick(50); SliderTick(75); SliderTick(100) }
            """,
        ) {
            LiquidKitSlider(
                value = { sliderValue },
                onValueChange = { sliderValue = it },
                valueRange = 0f..100f,
                ticks = listOf(0f, 25f, 50f, 75f, 100f),
                neutralValue = 50f,
            )
            BasicText("当前均衡值：${sliderValue.roundToInt()}（中性点 50）", style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "5. safeAreaBar 边缘渐进玻璃栏",
            subtitle = "在滚动容器底部注入渐进模糊边缘栏，内容滑过底部时平滑羽化。",
            code = """
                ScrollView { ForEach(1...12) { Text("滚动内容行 \\(${"$"}0)") } }
                    .safeAreaBar(edge: .bottom) { Text("safeAreaBar 底部栏") }
            """,
        ) {
            val barScene = rememberLayerBackdrop()
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp))) {
                Column(
                    Modifier
                        .matchParentSize()
                        .layerBackdrop(barScene)
                        .background(colors.background)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (r in 1..10) {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(colors.surface).padding(10.dp),
                        ) {
                            BasicText("滚动内容行 $r", style = TextStyle(colors.ink, 14.sp))
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }
                LiquidKitSafeAreaBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    backdrop = barScene,
                    edge = LiquidKitSafeAreaEdge.Bottom,
                    colors = colors,
                ) {
                    BasicText("safeAreaBar 底部栏", Modifier.weight(1f), style = TextStyle(colors.ink, 13.sp, FontWeight.SemiBold))
                    LiquidKitActionButton("确认", {}, backdrop = barScene, tint = Color(0xff007aff), style = LiquidKitActionStyle.GlassProminent, controlSize = LiquidKitControlSize.Small)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 2: Buttons & Controls
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ButtonDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var lastAction by remember { mutableStateOf("尚未点击") }
    var disabled by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sizeName by remember { mutableStateOf("regular") }
    val sizeMap = mapOf(
        "mini" to LiquidKitControlSize.Mini,
        "small" to LiquidKitControlSize.Small,
        "regular" to LiquidKitControlSize.Regular,
        "large" to LiquidKitControlSize.Large,
        "XL" to LiquidKitControlSize.ExtraLarge,
    )

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "全部原生 ButtonStyle 样式",
            subtitle = "包含 plain、borderless、bordered、borderedProminent、glass 与 glassProminent。",
            code = """
                Button("bordered") {}.buttonStyle(.bordered)
                Button("borderedProminent") {}.buttonStyle(.borderedProminent)
                Button("glass") {}.buttonStyle(.glass)
                Button("glassProminent") {}.buttonStyle(.glassProminent)
            """,
        ) {
            LiquidKitFlowLayout {
                LiquidKitActionButton("plain", { lastAction = "plain" }, style = LiquidKitActionStyle.Plain)
                LiquidKitActionButton("borderless", { lastAction = "borderless" }, style = LiquidKitActionStyle.Borderless)
                LiquidKitActionButton("bordered", { lastAction = "bordered" }, style = LiquidKitActionStyle.Tinted)
                LiquidKitActionButton("borderedProminent", { lastAction = "borderedProminent" }, style = LiquidKitActionStyle.Filled)
                LiquidKitActionButton("glass", { lastAction = "glass" }, style = LiquidKitActionStyle.Glass)
                LiquidKitActionButton("glassProminent", { lastAction = "glassProminent" }, style = LiquidKitActionStyle.GlassProminent)
            }
            BasicText("最近触发：$lastAction", style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "ButtonRole、形状与尺寸 (controlSize)",
            subtitle = "区分 destructive 角色、capsule / roundedRectangle / circle 形状及 mini 到 XL 尺寸。",
            code = """
                Button("删除项目", role: .destructive) {}
                    .buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 10))
            """,
        ) {
            LiquidKitSegmentedPicker(sizeMap.keys.toList(), sizeName, { sizeName = it }, colors = colors)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("禁用按钮 (.disabled)", Modifier.weight(1f), style = TextStyle(colors.ink, 15.sp))
                LiquidKitToggle({ disabled }, { disabled = it })
            }
            val cs = sizeMap[sizeName] ?: LiquidKitControlSize.Regular
            LiquidKitFlowLayout {
                LiquidKitActionButton("胶囊", { lastAction = "胶囊" }, style = LiquidKitActionStyle.Tinted, leadingSymbol = LiquidKitSymbol.Check, enabled = !disabled, controlSize = cs)
                LiquidKitActionButton("删除", { lastAction = "删除" }, style = LiquidKitActionStyle.Filled, shape = LiquidKitButtonShape.RoundedRectangle, destructive = true, enabled = !disabled, controlSize = cs)
                LiquidKitActionButton("", { lastAction = "圆形" }, style = LiquidKitActionStyle.Filled, leadingSymbol = LiquidKitSymbol.Add, shape = LiquidKitButtonShape.Circle, enabled = !disabled, controlSize = cs)
                LiquidKitActionButton(if (loading) "加载中..." else "切换加载态", { loading = !loading }, style = LiquidKitActionStyle.Filled, enabled = !disabled, controlSize = cs)
            }
            if (loading) LiquidKitActivityIndicator(label = "正在提交...", colors = colors)
        }
    }
}

@Composable
fun MenuAndLinkDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    val context = LocalContext.current
    var sortOrder by remember { mutableStateOf("按名称") }
    var menuLog by remember { mutableStateOf("点击菜单体验选项") }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    val menu = rememberLiquidMenuState { chosen ->
        if (chosen.key.startsWith("sort:")) sortOrder = chosen.title
        menuLog = chosen.title
    }
    val items = listOf(
        LiquidMenuItem("new", "新建文件", symbol = LiquidKitSymbol.Document),
        LiquidMenuItem(
            "more", "更多操作", symbol = LiquidKitSymbol.More,
            children = listOf(
                LiquidMenuItem("link", "复制链接", symbol = LiquidKitSymbol.Link),
                LiquidMenuItem("pdf", "导出 PDF", symbol = LiquidKitSymbol.Download),
            ),
        ),
        LiquidMenuItem("sort:name", "按名称", checked = sortOrder == "按名称"),
        LiquidMenuItem("sort:date", "按日期", checked = sortOrder == "按日期"),
        LiquidMenuItem("clear", "清空", symbol = LiquidKitSymbol.Trash, destructive = true),
    )

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Menu 下拉菜单、嵌套子菜单与 ShareLink",
            subtitle = "原生液态玻璃锚定菜单，支持子菜单切换、勾选状态与破坏性操作。",
            code = """
                Menu("打开菜单") {
                    Button("新建文件") {}
                    Menu("更多操作") { Button("复制链接") {} }
                    Button("清空", role: .destructive) {}
                }
            """,
        ) {
            LiquidAnchoredMenuHost(menu, backdrop, items, Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiquidKitToolbarGroup(Modifier.height(44.dp), backdrop = backdrop) {
                        LiquidKitToolbarButton("打开菜单", menu::show, Modifier.liquidMenuTrigger(menu).padding(horizontal = 16.dp)) {
                            BasicText("打开菜单", style = TextStyle(Color(0xff007aff), 15.sp, FontWeight.Medium))
                        }
                    }
                    LiquidKitActionButton("系统分享", {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "SwiftUI Gallery for Android"), "分享"))
                    }, style = LiquidKitActionStyle.Tinted, leadingSymbol = LiquidKitSymbol.Share)
                }
            }
            BasicText("菜单日志：$menuLog（排序：$sortOrder）", style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "ControlGroup 分组按钮",
            subtitle = "将相关操作合并至一个紧密胶囊控制组中。",
            code = """
                ControlGroup {
                    Toggle("粗体", isOn: ${"$"}bold)
                    Toggle("斜体", isOn: ${"$"}italic)
                }
            """,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidKitToggleButton("粗体", bold, { bold = it })
                LiquidKitToggleButton("斜体", italic, { italic = it })
            }
            LiquidKitControlGroup(colors = colors) {
                LiquidKitActionButton("剪切", { menuLog = "剪切" }, style = LiquidKitActionStyle.Plain)
                LiquidKitActionButton("复制", { menuLog = "复制" }, style = LiquidKitActionStyle.Plain)
                LiquidKitActionButton("粘贴", { menuLog = "粘贴" }, style = LiquidKitActionStyle.Plain)
            }
        }
    }
}

@Composable
fun ToggleDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var wifi by remember { mutableStateOf(true) }
    var bluetooth by remember { mutableStateOf(false) }
    var mute by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(true) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "LiquidToggle 开关 (.toggleStyle(.switch))",
            subtitle = "按压或拖拽触发液态玻璃液滴折射与上下高光反射。",
            code = """
                Toggle("无线局域网", isOn: ${"$"}wifi)
                Toggle("蓝牙", isOn: ${"$"}bluetooth)
            """,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("无线局域网", Modifier.weight(1f), style = TextStyle(colors.ink, 16.sp))
                LiquidKitToggle({ wifi }, { wifi = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("蓝牙", Modifier.weight(1f), style = TextStyle(colors.ink, 16.sp))
                LiquidKitToggle({ bluetooth }, { bluetooth = it })
            }
        }

        DemoSection(
            title = "按钮式开关 (.toggleStyle(.button))",
            subtitle = "适合工具栏过滤或富文本状态开关。",
            code = """
                Toggle("静音", systemImage: "bell", isOn: ${"$"}mute).toggleStyle(.button)
            """,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidKitToggleButton("静音", mute, { mute = it }, leadingSymbol = LiquidKitSymbol.Bell, tint = Color(0xffff9500))
                LiquidKitToggleButton("置顶", pin, { pin = it }, leadingSymbol = LiquidKitSymbol.Saved, tint = Color(0xff007aff))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 3: Text & Input
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun TextAndLabelDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var counter by remember { mutableIntStateOf(128) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Dynamic Type 字体层级与设计",
            subtitle = "LargeTitle、Title、Headline、Body、Monospaced 与 Label 图标排版。",
            code = """
                Text("Large Title").font(.largeTitle.bold())
                Text("Monospaced 128").font(.body.monospacedDigit())
                Label("系统文稿", systemImage: "doc.text")
            """,
        ) {
            BasicText("Large Title", style = TextStyle(colors.ink, 30.sp, FontWeight.Bold))
            BasicText("Title 2 · 半粗体", style = TextStyle(colors.ink, 21.sp, FontWeight.SemiBold))
            BasicText("Body · 常规正文内容排版示例", style = TextStyle(colors.ink, 16.sp))
            BasicText("Monospace · counter = $counter", style = TextStyle(colors.secondary, 14.sp, fontFamily = FontFamily.Monospace))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LiquidKitIcon(LiquidKitSymbol.Document, Color(0xff007aff), Modifier.size(20.dp))
                BasicText("Label 图标文本组合", style = TextStyle(colors.ink, 16.sp))
                Spacer(Modifier.weight(1f))
                LiquidKitActionButton("+10", { counter += 10 }, style = LiquidKitActionStyle.Tinted, controlSize = LiquidKitControlSize.Small)
            }
        }
    }
}

@Composable
fun TextFieldDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var plain by remember { mutableStateOf("") }
    var rounded by remember { mutableStateOf("LiquidKit") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Plain、RoundedBorder 与 SecureField",
            subtitle = "支持密码显示切换及原生光标高亮。",
            code = """
                TextField("用户名", text: ${"$"}rounded).textFieldStyle(.roundedBorder)
                SecureField("输入口令", text: ${"$"}secret)
            """,
        ) {
            LiquidKitTextField(plain, { plain = it }, "Plain 无边框输入", colors = colors, style = LiquidKitTextFieldStyle.Plain)
            LiquidKitTextField(rounded, { rounded = it }, "圆角边框输入", colors = colors, style = LiquidKitTextFieldStyle.RoundedBorder)
            LiquidKitTextField(secret, { secret = it }, "安全口令 (SecureField)", secret = !showSecret, colors = colors, style = LiquidKitTextFieldStyle.RoundedBorder)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("明文显示密码", Modifier.weight(1f), style = TextStyle(colors.ink, 14.sp))
                LiquidKitToggle({ showSecret }, { showSecret = it })
            }
        }
    }
}

@Composable
fun TextEditorDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var notes by remember { mutableStateOf("在这里输入多行笔记，实时统计字数与行数。\n支持 LiquidKit 圆角卡片容器。") }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "TextEditor 多行文本编辑",
            subtitle = "实时字数统计与自定义表面背景。",
            code = """
                TextEditor(text: ${"$"}notes)
                    .scrollContentBackground(.hidden)
                    .frame(minHeight: 120)
            """,
        ) {
            LiquidKitTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "输入多行内容...",
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                multiline = true,
                colors = colors,
                style = LiquidKitTextFieldStyle.RoundedBorder,
            )
            BasicText("字数：${notes.length} · 行数：${notes.count { it == '\n' } + 1}", style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 4: Pickers & Sliders
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun PickerDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val fruits = listOf("苹果", "橘子", "葡萄", "芒果")
    var fruit by remember { mutableStateOf(fruits.first()) }
    var palette by remember { mutableStateOf("blue") }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Segmented、Tabs (iOS 27) 与 Menu 样式",
            subtitle = "覆盖分段控件、iOS 27 .tabs 液态玻璃选择器与下拉菜单。",
            code = """
                Picker("水果", selection: ${"$"}fruit) { ... }.pickerStyle(.segmented)
                Picker("水果", selection: ${"$"}fruit) { ... }.pickerStyle(.tabs)
                Picker("水果", selection: ${"$"}fruit) { ... }.pickerStyle(.menu)
            """,
        ) {
            LiquidKitSegmentedPicker(fruits, fruit, { fruit = it }, colors = colors)
            LiquidKitTabsPicker(
                options = fruits.map { LiquidKitTabOption(it, it) },
                selectedId = fruit,
                onSelect = { fruit = it },
                colors = colors,
            )
            LiquidKitMenuPicker("菜单选择 (.menu)", fruits, fruit, { fruit = it }, colors = colors)
        }

        DemoSection(
            title = "Wheel 滚轮、Palette 调色板与 Inline 内联",
            subtitle = "完整对齐全部 6 种 SwiftUI PickerStyle。",
            code = """
                Picker("滚轮", selection: ${"$"}fruit) { ... }.pickerStyle(.wheel)
                Picker("主题色", selection: ${"$"}palette) { ... }.pickerStyle(.palette)
                Picker("内联", selection: ${"$"}fruit) { ... }.pickerStyle(.inline)
            """,
        ) {
            LiquidKitWheelPicker(fruits, fruit, { fruit = it }, colors = colors)
            LiquidKitPalettePicker(
                options = listOf(
                    LiquidKitPaletteOption("blue", "蓝", Color(0xff007aff)),
                    LiquidKitPaletteOption("purple", "紫", Color(0xffaf52de)),
                    LiquidKitPaletteOption("orange", "橙", Color(0xffff9500)),
                    LiquidKitPaletteOption("green", "绿", Color(0xff34c759)),
                ),
                selectedId = palette,
                onSelect = { palette = it },
                colors = colors,
            )
            LiquidKitInlinePicker(fruits, fruit, { fruit = it }, title = "内联选择 (.inline)", colors = colors)
        }
    }
}

@Composable
fun DateAndColorPickerDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var styleName by remember { mutableStateOf("compact") }
    var date by remember { mutableStateOf(LiquidKitCalendarDate(2026, 11, 25)) }
    var multiDates by remember { mutableStateOf(setOf(LiquidKitCalendarDate(2026, 11, 25), LiquidKitCalendarDate(2026, 11, 28))) }
    var pickedColor by remember { mutableStateOf(Color(0xff00c7be)) }
    var supportsOpacity by remember { mutableStateOf(true) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "DatePicker 全部样式 (compact / graphical / wheel)",
            subtitle = "实时切换三种日期选择器外观。",
            code = """
                DatePicker("选择日期", selection: ${"$"}date).datePickerStyle(.graphical)
            """,
        ) {
            LiquidKitSegmentedPicker(listOf("compact", "graphical", "wheel"), styleName, { styleName = it }, colors = colors)
            val style = when (styleName) {
                "graphical" -> LiquidKitDatePickerStyle.Graphical
                "wheel" -> LiquidKitDatePickerStyle.Wheel
                else -> LiquidKitDatePickerStyle.Compact
            }
            LiquidKitDatePicker("日期", date, { date = it }, style = style, colors = colors)
        }

        DemoSection(
            title = "MultiDatePicker 多日期与 ColorPicker 取色器",
            subtitle = "支持多天点选与 Alpha 透明度通道开关。",
            code = """
                MultiDatePicker("选择多天", selection: ${"$"}multiDates)
                ColorPicker("强调色", selection: ${"$"}pickedColor, supportsOpacity: supportsOpacity)
            """,
        ) {
            LiquidKitMultiDatePicker(multiDates, { multiDates = it }, colors = colors)
            BasicText("已选中 ${multiDates.size} 天", style = TextStyle(colors.secondary, 12.sp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("支持透明度 (supportsOpacity)", Modifier.weight(1f), style = TextStyle(colors.ink, 14.sp))
                LiquidKitToggle({ supportsOpacity }, { supportsOpacity = it })
            }
            LiquidKitColorPicker("自定义颜色", pickedColor, { pickedColor = it }, colors = colors, supportsOpacity = supportsOpacity)
            Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(pickedColor), contentAlignment = Alignment.Center) {
                BasicText("当前预览颜色", style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun SliderAndStepperDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var continuous by remember { mutableFloatStateOf(0.55f) }
    var neutral by remember { mutableFloatStateOf(20f) }
    var glassOpacity by remember { mutableFloatStateOf(0.5f) }
    var count by remember { mutableIntStateOf(4) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "连续滑块、刻度中性点滑块与玻璃不透明度",
            subtitle = "按住拇指展开液态玻璃折射，刻度滑块支持磁吸与双向填充。",
            code = """
                Slider(value: ${"$"}neutral, in: -50...50, neutralValue: 0) {
                    Text("偏移")
                } ticks: { SliderTick(-50); SliderTick(0); SliderTick(50) }
            """,
        ) {
            LiquidKitLabeledContent("连续值", "${(continuous * 100).roundToInt()}%", colors = colors)
            LiquidKitSlider({ continuous }, { continuous = it })
            LiquidKitLabeledContent("中性点偏移 (-50..50)", "${neutral.roundToInt()}", colors = colors)
            LiquidKitSlider(
                value = { neutral },
                onValueChange = { neutral = it },
                valueRange = -50f..50f,
                ticks = listOf(-50f, -25f, 0f, 25f, 50f),
                neutralValue = 0f,
            )
            LiquidKitOpacitySlider(glassOpacity, { glassOpacity = it }, label = "材质浓度")
        }

        DemoSection(
            title = "Stepper 步进器",
            subtitle = "支持点击与长按连发增减。",
            code = """
                Stepper("数量", value: ${"$"}count, in: 0...20)
            """,
        ) {
            LiquidKitStepper("数量", count, { count = it }, 0..20, colors = colors)
        }
    }
}
