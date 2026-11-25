package com.liquidkit.swiftgallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.components.LiquidListContextMenuHost
import com.kyant.backdrop.components.LiquidMenuItem
import com.kyant.backdrop.components.LiquidSwipeActions
import com.kyant.backdrop.components.liquidListContextMenu
import com.kyant.backdrop.components.liquidPopoverAnchor
import com.kyant.backdrop.components.rememberLiquidMenuState
import io.liquidkit.*
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
// Category 5: Indicators & Status
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ProgressViewDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var progress by remember { mutableFloatStateOf(0.62f) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "不确定加载与线性进度条",
            subtitle = "12 辐 Apple 风格旋转指示器与可联动进度条。",
            code = """
                ProgressView("同步中...")
                ProgressView("下载进度", value: progress, total: 1.0)
            """,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                LiquidKitActivityIndicator(colors = colors)
                LiquidKitActivityIndicator(label = "同步中...", color = Color(0xffaf52de), colors = colors)
            }
            LiquidKitLabeledContent("下载资源", "${(progress * 100).roundToInt()}%", colors = colors)
            LiquidKitProgressIndicator(progress, Color(0xff007aff), Modifier.fillMaxWidth())
            LiquidKitSlider({ progress }, { progress = it })
        }

        DemoSection(
            title = "自定义环形 ProgressViewStyle",
            subtitle = "Canvas 圆弧绘制，与上方滑块实时平滑联动。",
            code = """
                Circle().trim(from: 0, to: progress).stroke(.mint.gradient, lineWidth: 8)
            """,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(colors.separator.copy(alpha = .35f), 0f, 360f, false, style = stroke)
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color(0xff00c7be), Color(0xff34c759), Color(0xff00c7be))),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = stroke,
                        )
                    }
                    BasicText("${(progress * 100).roundToInt()}%", style = TextStyle(colors.ink, 13.sp, FontWeight.Bold))
                }
                BasicText("随上方滑块实时平滑联动", style = TextStyle(colors.secondary, 13.sp))
            }
        }
    }
}

@Composable
fun GaugeDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var cpu by remember { mutableFloatStateOf(0.68f) }
    val gradient = Brush.horizontalGradient(listOf(Color(0xff34c759), Color(0xffffcc00), Color(0xffff9500), Color(0xffff3b30)))

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Gauge 全部仪表样式",
            subtitle = "包含 linearCapacity、CircularCapacity 与 CircularOpen 渐变仪表。",
            code = """
                Gauge(value: cpu, in: 0...1) { Text("CPU") }
                    .gaugeStyle(.accessoryCircularCapacity).tint(gradient)
            """,
        ) {
            LiquidKitSlider({ cpu }, { cpu = it })
            LiquidKitLinearGauge(cpu, Color(0xff007aff), label = "CPU 占用", valueLabel = "${(cpu * 100).roundToInt()}%", colors = colors, brush = gradient)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidKitGauge(cpu, Color(0xff00c7be), colors = colors, style = LiquidKitGaugeStyle.CircularCapacity, brush = gradient)
                    BasicText("CircularCapacity", style = TextStyle(colors.secondary, 11.sp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidKitGauge(cpu, Color(0xff007aff), colors = colors, style = LiquidKitGaugeStyle.CircularOpen, brush = gradient)
                    BasicText("CircularOpen", style = TextStyle(colors.secondary, 11.sp))
                }
            }
        }
    }
}

@Composable
fun StatusEmptyStatesDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var isRedacted by remember { mutableStateOf(true) }
    var unread by remember { mutableIntStateOf(5) }
    var searchEmpty by remember { mutableStateOf(false) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "ContentUnavailableView 空状态",
            subtitle = "自定义空状态与搜索无结果态切换。",
            code = """
                ContentUnavailableView("暂无收藏", systemImage: "star")
            """,
        ) {
            LiquidKitSegmentedPicker(
                listOf("自定义空状态", "搜索无结果"),
                if (searchEmpty) "搜索无结果" else "自定义空状态",
                { searchEmpty = it == "搜索无结果" },
                colors = colors,
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                LiquidKitUnavailableView(
                    title = if (searchEmpty) "未找到匹配结果" else "暂无收藏组件",
                    symbol = if (searchEmpty) LiquidKitSymbol.Search else LiquidKitSymbol.Star,
                    description = if (searchEmpty) "尝试更换其他关键词。" else "点击详情页右上角星标即可加入收藏。",
                    colors = colors,
                )
            }
        }

        DemoSection(
            title = "Redacted 骨架占位与 Badge 角标",
            subtitle = "Modifier.liquidKitRedacted(active) 骨架扫光与 LiquidKitBadge。",
            code = """
                ProfileRow().redacted(reason: isRedacted ? .placeholder : [])
                Text("通知").badge(unread)
            """,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("启用骨架屏 (.redacted)", Modifier.weight(1f), style = TextStyle(colors.ink, 15.sp))
                LiquidKitToggle({ isRedacted }, { isRedacted = it })
            }
            Column(Modifier.fillMaxWidth().liquidKitRedacted(isRedacted), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("LiquidKit 27.1 光学引擎", style = TextStyle(colors.ink, 16.sp, FontWeight.SemiBold))
                BasicText("实时采样底层场景折射并叠加动态色散高光。", style = TextStyle(colors.secondary, 13.sp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("未读通知", Modifier.weight(1f), style = TextStyle(colors.ink, 15.sp))
                LiquidKitBadge(unread.toString())
                LiquidKitActionButton("+1", { unread++ }, style = LiquidKitActionStyle.Tinted, controlSize = LiquidKitControlSize.Mini)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 6: Layout & Containers
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun StacksAndGridDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var spacing by remember { mutableFloatStateOf(10f) }
    var horizontal by remember { mutableStateOf(true) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "HStack / VStack / AnyLayout 间距调节",
            subtitle = "实时调节元素间距并在水平与垂直栈间切换。",
            code = """
                let layout = horizontal ? AnyLayout(HStackLayout(spacing: spacing)) : AnyLayout(VStackLayout(spacing: spacing))
            """,
        ) {
            LiquidKitSegmentedPicker(
                listOf("水平 HStack", "垂直 VStack"),
                if (horizontal) "水平 HStack" else "垂直 VStack",
                { horizontal = it.startsWith("水平") },
                colors = colors,
            )
            LiquidKitLabeledContent("间距", "${spacing.roundToInt()} dp", colors = colors)
            LiquidKitSlider({ spacing }, { spacing = it }, 2f..24f)
            val boxes = listOf(Color(0xff007aff), Color(0xff00c7be), Color(0xffaf52de))
            if (horizontal) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.dp)) {
                    boxes.forEachIndexed { i, c ->
                        Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(c), contentAlignment = Alignment.Center) {
                            BasicText("${i + 1}", style = TextStyle(Color.White, 15.sp, FontWeight.Bold))
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
                    boxes.forEachIndexed { i, c ->
                        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(c), contentAlignment = Alignment.Center) {
                            BasicText("Row ${i + 1}", style = TextStyle(Color.White, 14.sp, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollViewDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val palette = listOf(Color(0xff007aff), Color(0xff00c7be), Color(0xffaf52de), Color(0xffff9500), Color(0xffff2d55))
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "水平卡片滚动轮播",
            subtitle = "流畅横向滚动卡片流，支持渐变卡片预览。",
            code = """
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 12) { ForEach(1...5) { CardView(${"$"}0) } }
                }.scrollTargetBehavior(.viewAligned)
            """,
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                palette.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .size(168.dp, 108.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(c, c.copy(alpha = .72f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("卡片 ${i + 1}", style = TextStyle(Color.White, 18.sp, FontWeight.Bold))
                    }
                }
            }
            BasicText("左右滑动浏览 5 张卡片", style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

@Composable
fun ContainersGroupingDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var expanded by remember { mutableStateOf(true) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "GroupBox、DisclosureGroup 与 LabeledContent",
            subtitle = "结构化容器、可折叠分组与键值对行。",
            code = """
                GroupBox("设备信息") {
                    LabeledContent("渲染引擎", value: "AGSL Liquid Glass")
                    DisclosureGroup("高级选项", isExpanded: ${"$"}expanded) { ... }
                }
            """,
        ) {
            LiquidKitGroupBox("设备信息", colors = colors) {
                LiquidKitLabeledContent("渲染引擎", "AGSL Liquid Glass", colors = colors)
                LiquidKitLabeledContent("跨平台桥接", "LiquidKit 1.0", colors = colors)
            }
            LiquidKitDisclosureGroup("可折叠高级参数", expanded, { expanded = it }, colors = colors) {
                LiquidKitLabeledContent("折射半径", "19 dp", colors = colors)
                LiquidKitLabeledContent("弹簧阻尼", "0.74", colors = colors)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 7: Lists & Collections
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ListStylesDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var styleName by remember { mutableStateOf("Grouped") }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "List 样式切换 (Grouped / Plain / Cards)",
            subtitle = "支持全宽原生 iOS 按压反馈与相邻分隔线隐匿。",
            code = """
                List { ForEach(rows) { Text(${"$"}0) } }.listStyle(.insetGrouped)
            """,
        ) {
            LiquidKitSegmentedPicker(listOf("Grouped", "Plain", "Cards"), styleName, { styleName = it }, colors = colors)
            LiquidKitFormSection(title = "示例列表 · $styleName", colors = colors) {
                for (rowTitle in listOf("路线手记", "印刷样张", "影像目录")) {
                    navigationRow(rowTitle, {})
                }
            }
        }
    }
}

@Composable
fun ListInteractionsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var entries by remember { mutableStateOf(listOf("设计规范.md", "光学参数.json", "测试记录.csv", "发布清单.txt")) }
    var pinned by remember { mutableStateOf(setOf<String>()) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "左右滑动操作 (swipeActions) 与重置",
            subtitle = "向左滑动任意行可置顶或删除项目。",
            code = """
                ForEach(entries) { row in
                    Text(row).swipeActions { Button("删除", role: .destructive) {} }
                }
            """,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.forEach { entry ->
                    val isPin = entry in pinned
                    LiquidSwipeActions(
                        rowKey = entry,
                        trailing = listOf(
                            LiquidKitSwipeAction(if (isPin) "取消置顶" else "置顶", LiquidKitSymbol.Saved, Color(0xffff9500)) {
                                pinned = if (isPin) pinned - entry else pinned + entry
                            },
                            LiquidKitSwipeAction("删除", LiquidKitSymbol.Trash, Color(0xffff3b30)) {
                                entries = entries - entry
                            },
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(entry, Modifier.weight(1f), style = TextStyle(colors.ink, 15.sp))
                            if (isPin) LiquidKitIcon(LiquidKitSymbol.Saved, Color(0xffff9500), Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (entries.size < 4) {
                LiquidKitActionButton("恢复全部示例行", {
                    entries = listOf("设计规范.md", "光学参数.json", "测试记录.csv", "发布清单.txt")
                }, style = LiquidKitActionStyle.Tinted, controlSize = LiquidKitControlSize.Small)
            }
        }
    }
}

@Composable
fun OutlineAndTableDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var selectedLeaf by remember { mutableStateOf("点击任意叶子节点查看") }
    val tree = remember {
        listOf(
            LiquidKitOutlineNode<Unit>(
                "root", "LiquidKit 工程", LiquidKitSymbol.Folder,
                children = listOf(
                    LiquidKitOutlineNode(
                        "backdrop", "backdrop 核心模块", LiquidKitSymbol.Folder,
                        children = listOf(
                            LiquidKitOutlineNode("glass", "LiquidGlassSurface.kt", LiquidKitSymbol.Document),
                            LiquidKitOutlineNode("slider", "LiquidSlider.kt", LiquidKitSymbol.Document),
                        ),
                    ),
                    LiquidKitOutlineNode(
                        "swift", "liquidkit-swift 桥接", LiquidKitSymbol.Folder,
                        children = listOf(
                            LiquidKitOutlineNode("theme", "LiquidKitTheme.swift", LiquidKitSymbol.Document),
                            LiquidKitOutlineNode("controls", "LiquidKitControls.swift", LiquidKitSymbol.Document),
                        ),
                    ),
                ),
            ),
        )
    }
    data class MetricRow(val name: String, val module: String, val tests: Int)
    val tableRows = remember {
        listOf(
            MetricRow("核心渲染", "backdrop", 71),
            MetricRow("KMP 外观层", "liquidkit", 4),
            MetricRow("SwiftUI 桥接", "liquidkit-swift", 5),
            MetricRow("组件画廊", "swiftGallery", 46),
        )
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "OutlineGroup 树形目录",
            subtitle = "点击父文件夹展开/折叠子树。",
            code = """
                OutlineGroup(tree, children: \\.children) { node in Label(node.title, systemImage: node.icon) }
            """,
        ) {
            LiquidKitOutlineGroup(tree, colors = colors, onSelectLeaf = { selectedLeaf = "选中文件：${it.title}" })
            BasicText(selectedLeaf, style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "Table 可排序多列表格",
            subtitle = "点击列标题即可按名称或数量升降序排列。",
            code = """
                Table(rows, sortOrder: ${"$"}sortOrder) {
                    TableColumn("模块", value: \\.name)
                    TableColumn("数量", value: \\.tests)
                }
            """,
        ) {
            LiquidKitTable(
                rows = tableRows,
                columns = listOf(
                    LiquidKitTableColumn("name", "名称", weight = 1.2f, comparator = compareBy { it.name }) { it.name },
                    LiquidKitTableColumn("mod", "模块", weight = 1.2f, comparator = compareBy { it.module }) { it.module },
                    LiquidKitTableColumn("cnt", "项数", weight = 0.7f, comparator = compareBy { it.tests }) { it.tests.toString() },
                ),
                colors = colors,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 8: Navigation & Presentation
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun NavigationAndToolbarDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var depth by remember { mutableIntStateOf(0) }
    var transitionKind by remember { mutableStateOf("CrossFade") }
    var showStandaloneNav by remember { mutableStateOf(false) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "独立自适应导航页 (NavigationStack / SplitView)",
            subtitle = "点击打开独立全屏导航体验：手机竖屏独立逐级 Push 并支持 SwipeBack，横屏/宽屏 (>=600dp) 自动切换侧边栏 + 详情双栏视图。",
            code = """
                NavigationSplitView(columnVisibility: ${"$"}visibility) {
                    SidebarList(selection: ${"$"}folder)
                } detail: {
                    NavigationStack(path: ${"$"}detailPath) {
                        DetailView(folder)
                    }
                }
            """,
        ) {
            LiquidKitFormSection(title = "当前设备断点自适应模式", colors = colors) {
                row(inset = true, separatorStart = 50.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LiquidKitIcon(LiquidKitSymbol.Sidebar, Color(0xff007aff), Modifier.size(22.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText("手机独立页面 / 宽屏双栏 SplitView", style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold))
                            BasicText("包含顶栏渐进模糊、玻璃 Toolbar 按钮与多级右滑返回", style = TextStyle(colors.secondary, 12.sp))
                        }
                    }
                }
            }
            LiquidKitActionButton(
                label = "打开独立导航演示",
                onClick = { showStandaloneNav = true },
                style = LiquidKitActionStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DemoSection(
            title = "轻量视图转场模式 (CrossFade / Zoom / Slide)",
            subtitle = "演示局部容器内部的编程式层级切换转场效果。",
            code = """
                LiquidKitViewTransition(targetState = depth, style = .crossFade) { ... }
            """,
        ) {
            LiquidKitSegmentedPicker(listOf("CrossFade", "Zoom", "Slide"), transitionKind, { transitionKind = it }, colors = colors)
            val style = when (transitionKind) {
                "Zoom" -> LiquidKitViewTransitionStyle.Zoom
                "Slide" -> LiquidKitViewTransitionStyle.Slide
                else -> LiquidKitViewTransitionStyle.CrossFade
            }
            LiquidKitViewTransition(targetState = depth, style = style) { d ->
                Box(
                    Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xff007aff).copy(alpha = .12f + (d % 3) * .08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("局部容器状态：$d（$transitionKind）", style = TextStyle(colors.ink, 16.sp, FontWeight.SemiBold))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidKitActionButton("下一状态", { depth++ }, style = LiquidKitActionStyle.Filled)
                LiquidKitActionButton("上一状态", { if (depth > 0) depth-- }, style = LiquidKitActionStyle.Tinted, enabled = depth > 0)
            }
        }
    }

    if (showStandaloneNav) {
        StandaloneAdaptiveNavigationDialog(onDismiss = { showStandaloneNav = false })
    }
}

private data class NavDemoFolder(
    val title: String,
    val subtitle: String,
    val symbol: LiquidKitSymbol,
    val tint: Color,
    val items: List<Pair<String, String>>,
)

private val navDemoFolders = listOf(
    NavDemoFolder(
        title = "收件箱",
        subtitle = "3 项未读评审讨论",
        symbol = LiquidKitSymbol.Folder,
        tint = Color(0xff007aff),
        items = listOf(
            "iOS 27 液态玻璃规范确认" to "包含渐进模糊顶部栏、分离式玻璃工具栏按钮与胶囊融合边界定义。",
            "SwipeBack 手势响应曲线" to "核验空白区域右滑与快扫临界速度判定，保持跟手零卡顿。",
            "深色烟熏玻璃 Tint 参数" to "统一下拉菜单、Alert 弹窗与 Toolbar 在深色模式下的折射透光度。",
        ),
    ),
    NavDemoFolder(
        title = "星标规范",
        subtitle = "核心材质与几何标准",
        symbol = LiquidKitSymbol.StarFilled,
        tint = Color(0xffff9500),
        items = listOf(
            "超椭圆胶囊 SDF 数学模型" to "以顺时针 Hermite 轮廓与平滑桥接路径实现零布尔运算高效融合。",
            "RuntimeShader 缓存共享" to "全局复用已编译 AGSL 着色器实例与异步管线预热，降低首帧阻塞。",
            "原生 ListStyle 五样式对齐" to "完整区分 insetGrouped、grouped、inset、plain 与可折叠 sidebar。",
        ),
    ),
    NavDemoFolder(
        title = "动效交互",
        subtitle = "Spring 物理弹簧预设",
        symbol = LiquidKitSymbol.Sparkles,
        tint = Color(0xffaf52de),
        items = listOf(
            "交互高光按压形变" to "长按滑块拇指与玻璃控件时展开液态高光与均匀缩放弹性形变。",
            "Slider 双向刻度吸附" to "支持 SliderTick 刻度锚点与 neutralValue 双向进度条填充。",
            "Inline Picker 轻量响应" to "短按立即切换选中项且不闪烁行背景，长按显示原生按压反馈。",
        ),
    ),
    NavDemoFolder(
        title = "系统适配",
        subtitle = "多断点横竖屏响应式布局",
        symbol = LiquidKitSymbol.Sidebar,
        tint = Color(0xff34c759),
        items = listOf(
            "横屏紧凑单胶囊底栏" to "宽度 >= 560dp 时底部标签与搜索入口融合为紧凑水平单一玻璃胶囊。",
            "宽屏 SplitView 阈值" to "当容器宽度 >= 600dp 时自动同时显示左侧边栏与右侧详情内容栏。",
            "Baseline Profile 预热" to "配合 R8 压缩与热点方法规则显著缩短 APK 冷启动时间。",
        ),
    ),
)

@Composable
internal fun StandaloneAdaptiveNavigationDialog(onDismiss: () -> Unit) {
    val colors = liquidKitColors()
    val splitState = rememberLiquidKitSplitState(LiquidKitSplitColumn.Sidebar)
    var selectedFolderIndex by remember { mutableIntStateOf(0) }
    var selectedArticle by remember { mutableStateOf<Pair<String, String>?>(null) }
    var starred by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            when {
                selectedArticle != null -> selectedArticle = null
                !splitState.back() -> onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
            val isWide = maxWidth >= 600.dp
            LiquidKitNavigationSplitView(
                state = splitState,
                modifier = Modifier.fillMaxSize(),
                twoColumnWidth = 600.dp,
                swipeBackFromBlank = true,
                sidebar = {
                    val sidebarScroll = rememberScrollState()
                    LiquidKitNavigationScaffold(
                        title = "导航目录",
                        colors = colors,
                        scrollOffsetPx = { sidebarScroll.value.toFloat() },
                        trailing = { barBackdrop ->
                            LiquidKitNavBarSlot(
                                group = LiquidKitNavBarGroup(
                                    listOf(
                                        LiquidKitNavBarAction(
                                            key = "close-nav-sidebar",
                                            label = "关闭",
                                            symbol = LiquidKitSymbol.Close,
                                            onClick = onDismiss,
                                        ),
                                    ),
                                ),
                                backdrop = barBackdrop,
                            )
                        },
                    ) { insets ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(sidebarScroll)
                                .padding(insets)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.surface)
                                    .padding(16.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BasicText(
                                        if (isWide) "宽屏双栏 SplitView 模式" else "手机独立页面模式",
                                        style = TextStyle(Color(0xff007aff), 13.sp, FontWeight.SemiBold),
                                    )
                                    BasicText(
                                        if (isWide) "左侧边栏与右侧详情并排呈现，点击目录项即时联动右侧详情。"
                                        else "点击任一目录将压入独立全屏详情页，支持左上角返回或右滑手势返回。",
                                        style = TextStyle(colors.secondary, 13.sp),
                                    )
                                }
                            }
                            LiquidKitFormSection(title = "导航分组 (Sidebar)", colors = colors) {
                                navDemoFolders.forEachIndexed { index, folder ->
                                    val active = selectedFolderIndex == index
                                    row(
                                        inset = true,
                                        separatorStart = 60.dp,
                                        selected = isWide && active,
                                        onClick = {
                                            selectedFolderIndex = index
                                            selectedArticle = null
                                            splitState.show(LiquidKitSplitColumn.Detail)
                                        },
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(folder.tint),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                LiquidKitIcon(folder.symbol, Color.White, Modifier.size(18.dp))
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                BasicText(
                                                    folder.title,
                                                    style = TextStyle(if (isWide && active) Color(0xff007aff) else colors.ink, 16.sp, FontWeight.Medium),
                                                )
                                                BasicText(folder.subtitle, style = TextStyle(colors.secondary, 12.sp))
                                            }
                                            if (!isWide || active) {
                                                LiquidKitIcon(
                                                    LiquidKitSymbol.Forward,
                                                    if (isWide && active) Color(0xff007aff) else colors.secondary,
                                                    Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                detail = {
                    val folder = navDemoFolders[selectedFolderIndex]
                    val currentPage: NavDetailPage = selectedArticle?.let { NavDetailPage.Article(it.first, it.second) } ?: NavDetailPage.Folder
                    val previousPage: NavDetailPage? = if (selectedArticle != null) NavDetailPage.Folder else null
                    LiquidKitSwipeBackNavigation(
                        page = currentPage,
                        previousPage = previousPage,
                        depth = { if (it is NavDetailPage.Article) 1 else 0 },
                        onNavigateBack = { selectedArticle = null },
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val article = (page as? NavDetailPage.Article)
                        val detailScroll = rememberScrollState()
                        val pageTitle = article?.title ?: folder.title
                        LiquidKitNavigationScaffold(
                            title = pageTitle,
                            colors = colors,
                            scrollOffsetPx = { detailScroll.value.toFloat() },
                            leading = { barBackdrop ->
                                if (article != null || !isWide) {
                                    LiquidKitNavBarSlot(
                                        group = LiquidKitNavBarGroup(
                                            listOf(
                                                LiquidKitNavBarAction(
                                                    key = if (article != null) "back-article" else "back-sidebar",
                                                    label = "返回",
                                                    symbol = LiquidKitSymbol.Back,
                                                    onClick = {
                                                        if (article != null) selectedArticle = null
                                                        else splitState.back()
                                                    },
                                                ),
                                            ),
                                        ),
                                        backdrop = barBackdrop,
                                    )
                                }
                            },
                            trailing = { barBackdrop ->
                                LiquidKitNavBarSlot(
                                    group = LiquidKitNavBarGroup(
                                        listOf(
                                            LiquidKitNavBarAction(
                                                key = "star-nav-detail",
                                                label = if (starred) "已星标" else "星标",
                                                symbol = if (starred) LiquidKitSymbol.StarFilled else LiquidKitSymbol.Star,
                                                tint = if (starred) Color(0xffff9500) else Color.Unspecified,
                                                onClick = { starred = !starred },
                                            ),
                                            LiquidKitNavBarAction(
                                                key = "close-nav-detail",
                                                label = "关闭",
                                                symbol = LiquidKitSymbol.Close,
                                                onClick = onDismiss,
                                            ),
                                        ),
                                    ),
                                    backdrop = barBackdrop,
                                )
                            },
                        ) { insets ->
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(detailScroll)
                                    .padding(insets)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (article == null) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(colors.surface)
                                            .padding(16.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(folder.tint),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                LiquidKitIcon(folder.symbol, Color.White, Modifier.size(22.dp))
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                BasicText(folder.title, style = TextStyle(colors.ink, 18.sp, FontWeight.Bold))
                                                BasicText("${folder.subtitle} · 点击任意条目继续 Push 至第 3 级", style = TextStyle(colors.secondary, 13.sp))
                                            }
                                        }
                                    }
                                    LiquidKitFormSection(title = "${folder.title} 内容列表", colors = colors) {
                                        for (entry in folder.items) {
                                            row(inset = true, separatorStart = 16.dp, onClick = { selectedArticle = entry }) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                ) {
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        BasicText(entry.first, style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold))
                                                        BasicText(entry.second, style = TextStyle(colors.secondary, 12.sp))
                                                    }
                                                    LiquidKitIcon(LiquidKitSymbol.Forward, colors.secondary, Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(160.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Brush.linearGradient(listOf(folder.tint.copy(alpha = .24f), Color(0xff007aff).copy(alpha = .12f))))
                                            .padding(18.dp),
                                        contentAlignment = Alignment.BottomStart,
                                    ) {
                                        BasicText(
                                            "上下滑动页面可实时观察顶部 Liquid Glass 渐进模糊顶栏与底部工具栏玻璃折射效果。",
                                            style = TextStyle(colors.ink, 14.sp),
                                        )
                                    }
                                } else {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(colors.surface)
                                            .padding(18.dp),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            BasicText("${folder.title} · 详情页", style = TextStyle(folder.tint, 13.sp, FontWeight.SemiBold))
                                            BasicText(article.title, style = TextStyle(colors.ink, 20.sp, FontWeight.Bold))
                                            BasicText(article.body, style = TextStyle(colors.secondary, 15.sp))
                                        }
                                    }
                                    LiquidKitActionButton(
                                        label = "返回上一级 (${folder.title})",
                                        onClick = { selectedArticle = null },
                                        style = LiquidKitActionStyle.Tinted,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

private sealed interface NavDetailPage {
    data object Folder : NavDetailPage
    data class Article(val title: String, val body: String) : NavDetailPage
}

@Composable
fun SheetsPopoversDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var showSheet by remember { mutableStateOf(false) }
    var showPopover by remember { mutableStateOf(false) }
    var showInspector by remember { mutableStateOf(false) }
    val popBounds = remember { mutableStateOf<Rect?>(null) }

    LiquidKitInspector(
        isPresented = showInspector,
        onDismiss = { showInspector = false },
        title = "属性检查器 Inspector",
        modifier = Modifier.fillMaxSize(),
        colors = colors,
        inspectorContent = {
            LiquidKitFormSection("检查器属性", colors = colors) {
                row { LiquidKitLabeledContent("布局模式", "宽屏侧栏 / 窄屏抽屉", colors = colors) }
                row { LiquidKitLabeledContent("材质", "Liquid Glass Regular", colors = colors) }
                row { LiquidKitLabeledContent("预测式返回", "原生支持", colors = colors) }
            }
        },
    ) {
        ComponentDemoPage(item, onBack) {
            DemoSection(
                title = "Sheet 半屏抽屉、Popover 气泡与 Inspector",
                subtitle = "全部原生支持 Android 预测式返回手势与实时液态玻璃折射。",
                code = """
                    .sheet(isPresented: ${"$"}showSheet) { ... }
                    .popover(isPresented: ${"$"}showPopover) { ... }
                    .inspector(isPresented: ${"$"}showInspector) { ... }
                """,
            ) {
                LiquidKitActionButton("打开多段高度 LiquidSheet", { showSheet = true }, Modifier.fillMaxWidth(), style = LiquidKitActionStyle.Filled)
                LiquidKitActionButton(
                    "打开锚定 LiquidPopover",
                    { showPopover = true },
                    Modifier.fillMaxWidth().liquidPopoverAnchor(popBounds),
                    style = LiquidKitActionStyle.Tinted,
                )
                LiquidKitActionButton("打开自适应 LiquidInspector", { showInspector = true }, Modifier.fillMaxWidth(), style = LiquidKitActionStyle.Tinted)
            }
        }
    }

    if (showSheet) {
        LiquidKitSheet(
            onDismissRequest = { showSheet = false },
            title = "可调高度 Sheet",
            detents = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
            colors = colors,
        ) {
            LiquidKitFormSection(colors = colors) {
                row { LiquidKitLabeledContent("停靠档位", "Medium / Large", colors = colors) }
                row { LiquidKitLabeledContent("预测式返回", "已接入", colors = colors) }
            }
        }
    }
    if (showPopover) {
        LiquidKitPopover(onDismissRequest = { showPopover = false }, anchorBoundsInRoot = popBounds.value, colors = colors) {
            BasicText("LiquidPopover 浮层", style = TextStyle(colors.ink, 16.sp, FontWeight.SemiBold))
            BasicText("自动根据锚点位置上下避让并采样实时背景玻璃折射。", style = TextStyle(colors.secondary, 13.sp))
        }
    }
}

@Composable
fun AlertsDialogsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var dialog by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("未触发") }
    val ctx = rememberLiquidKitMenuState { log = "菜单选择：${it.title}" }
    val customCtx = rememberLiquidKitMenuState { log = "自定义预览菜单：${it.title}" }
    val ctxItems = listOf(
        LiquidKitMenuItem("copy", "复制内容", symbol = LiquidKitSymbol.Clipboard),
        LiquidKitMenuItem("fav", "添加到收藏", symbol = LiquidKitSymbol.Star),
        LiquidKitMenuItem("del", "移除", symbol = LiquidKitSymbol.Trash, startsSection = true, destructive = true),
    )

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Alert 警告弹窗与带输入框弹窗",
            subtitle = "支持标准按钮角色 (destructive/cancel) 及内嵌 TextField 输入。",
            code = """
                .alert("确认删除？", isPresented: ${"$"}showDestructive) { Button("删除", role: .destructive) {} }
                .alert("重命名组件", isPresented: ${"$"}showInput) { TextField("名称", text: ${"$"}name) }
            """,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidKitActionButton(
                    "破坏性 Alert",
                    { dialog = "alert" },
                    tint = Color(0xffff3b30),
                    style = LiquidKitActionStyle.Tinted,
                )
                LiquidKitActionButton(
                    "带输入框 Alert",
                    {
                        inputText = ""
                        dialog = "input"
                    },
                    style = LiquidKitActionStyle.Filled,
                )
            }
            BasicText("结果：$log", style = TextStyle(colors.secondary, 12.sp))
        }

        DemoSection(
            title = "ConfirmationDialog 操作确认表单",
            subtitle = "在底部弹出多选项列表 (iPad 上锚定到触发按钮附近)。",
            code = """
                .confirmationDialog("选择导出方式", isPresented: ${"$"}showDialog) {
                    Button("导出 PNG") {}
                    Button("导出 PDF") {}
                }
            """,
        ) {
            LiquidKitActionButton(
                "打开 ConfirmationDialog",
                { dialog = "action" },
                style = LiquidKitActionStyle.Tinted,
            )
        }

        DemoSection(
            title = "ContextMenu 上下文菜单（含自定义预览）",
            subtitle = "长按下方卡片弹出液态玻璃菜单与自定义预览视图。",
            code = """
                Text("长按卡片").contextMenu {
                    Button("复制内容", systemImage: "doc.on.clipboard") {}
                    Button("移除", role: .destructive) {}
                } preview: {
                    CustomPreviewCard()
                }
            """,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidKitContextMenuHost(ctx, ctxItems, Modifier.fillMaxWidth(), colors = colors) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.secondary.copy(alpha = .12f))
                            .liquidKitContextMenu(ctx) {}
                            .padding(14.dp),
                    ) {
                        BasicText("长按此处弹出标准 ContextMenu", style = TextStyle(colors.ink, 14.sp, FontWeight.Medium))
                    }
                }
                LiquidKitContextMenuHost(
                    state = customCtx,
                    items = ctxItems,
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    preview = {
                        Column(
                            Modifier.size(236.dp, 114.dp).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        ) {
                            LiquidKitIcon(LiquidKitSymbol.Sparkles, Color(0xffff9500), Modifier.size(34.dp))
                            BasicText(
                                "ContextMenu 自定义预览",
                                style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold),
                            )
                        }
                    },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xffff9500).copy(alpha = .14f))
                            .liquidKitContextMenu(customCtx) {}
                            .padding(14.dp),
                    ) {
                        BasicText("长按此处弹出自定义预览 ContextMenu", style = TextStyle(colors.ink, 14.sp, FontWeight.Medium))
                    }
                }
            }
        }
    }

    if (dialog == "alert") {
        LiquidKitAlert(
            title = "确认移除此项目？",
            message = "移除后可随时从组件目录重新添加。",
            actions = listOf(
                LiquidKitAlertAction("取消", LiquidKitAlertRole.Cancel) { log = "已取消"; dialog = "" },
                LiquidKitAlertAction("移除", LiquidKitAlertRole.Destructive) { log = "已确认移除"; dialog = "" },
            ),
            onDismissRequest = { log = "已取消"; dialog = "" },
            colors = colors,
        )
    } else if (dialog == "input") {
        LiquidKitAlert(
            title = "重命名样张",
            message = "输入新的组件显示名称。",
            actions = listOf(
                LiquidKitAlertAction("取消", LiquidKitAlertRole.Cancel) { log = "已取消重命名"; dialog = "" },
                LiquidKitAlertAction("保存", LiquidKitAlertRole.Primary) {
                    log = if (inputText.isBlank()) "未输入名称" else "已重命名为：$inputText"
                    dialog = ""
                },
            ),
            onDismissRequest = { log = "已取消重命名"; dialog = "" },
            colors = colors,
            field = {
                LiquidKitAlertTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = "输入名称",
                    onConfirm = {
                        log = if (inputText.isBlank()) "未输入名称" else "已重命名为：$inputText"
                        dialog = ""
                    },
                    colors = colors,
                )
            },
        )
    } else if (dialog == "action") {
        LiquidKitActionSheet(
            title = "选择导出格式",
            actions = listOf(
                LiquidKitAlertAction("导出 PNG", LiquidKitAlertRole.Primary) { log = "导出 PNG"; dialog = "" },
                LiquidKitAlertAction("导出 PDF") { log = "导出 PDF"; dialog = "" },
                LiquidKitAlertAction("重置为默认", LiquidKitAlertRole.Destructive) { log = "已重置"; dialog = "" },
            ),
            onDismissRequest = { log = "已取消"; dialog = "" },
            colors = colors,
        )
    }
}
