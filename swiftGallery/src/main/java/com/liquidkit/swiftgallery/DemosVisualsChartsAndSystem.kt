package com.liquidkit.swiftgallery

import android.view.HapticFeedbackConstants
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.liquidkit.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ──────────────────────────────────────────────────────────────────────────────
// Category 9: Graphics, Materials & Animation
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ShapesGradientsDemo(item: ComponentItem, onBack: () -> Unit) {
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "内置形状与 3×3 MeshGradient",
            subtitle = "Circle、Capsule、UnevenRoundedRectangle 与双线性插值 MeshGradient。",
            code = """
                MeshGradient(width: 3, height: 3, points: [...], colors: [...])
            """,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Spacer(Modifier.size(46.dp).background(Color(0xff007aff), CircleShape))
                Spacer(Modifier.size(64.dp, 46.dp).background(Color(0xff00c7be), LiquidKitCapsule))
                Spacer(Modifier.size(64.dp, 46.dp).background(Color(0xffaf52de), RoundedCornerShape(16.dp, 4.dp, 16.dp, 4.dp)))
            }
            LiquidKitMeshGradient(
                width = 3,
                height = 3,
                colors = listOf(
                    Color(0xff00c7be), Color(0xff007aff), Color(0xff5856d6),
                    Color(0xff34c759), Color(0xffffcc00), Color(0xffff2d55),
                    Color(0xff007aff), Color(0xffaf52de), Color(0xffff9500),
                ),
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)),
            )
        }
    }
}

@Composable
fun SymbolsImagesDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val infinite = rememberInfiniteTransition(label = "SymbolPulse")
    val pulse by infinite.animateFloat(0.88f, 1.16f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "Pulse")
    val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "Spin")
    val showcase = listOf(
        LiquidKitSymbol.Sparkles, LiquidKitSymbol.StarFilled, LiquidKitSymbol.Heart, LiquidKitSymbol.Sun,
        LiquidKitSymbol.Moon, LiquidKitSymbol.Globe, LiquidKitSymbol.Map, LiquidKitSymbol.Chart,
        LiquidKitSymbol.Camera, LiquidKitSymbol.Send, LiquidKitSymbol.Lightbulb, LiquidKitSymbol.Palette,
    )

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "LiquidSymbol 矢量符号与动效",
            subtitle = "自动映射 SF Symbols 名称并在 Android 端纯矢量绘制。",
            code = """
                Image(systemName: "sparkles").symbolEffect(.breathe)
            """,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                LiquidKitIcon(LiquidKitSymbol.Heart, Color(0xffff2d55), Modifier.size(32.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
                LiquidKitIcon(LiquidKitSymbol.Sun, Color(0xffff9500), Modifier.size(32.dp).graphicsLayer { rotationZ = spin })
                LiquidKitIcon(LiquidKitSymbol.Sparkles, Color(0xff00c7be), Modifier.size(32.dp).graphicsLayer { scaleX = pulse; scaleY = pulse })
            }
            LiquidKitFlowLayout(horizontalSpacing = 12.dp, verticalSpacing = 12.dp) {
                showcase.forEach { sym ->
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(colors.secondary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        LiquidKitIcon(sym, Color(0xff007aff), Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MaterialsEffectsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var opacity by remember { mutableFloatStateOf(0.5f) }
    val scene = rememberLayerBackdrop()

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "多档液态玻璃浓度与材质对比",
            subtitle = "拖动滑块实时调节 LiquidGlassAppearance 模糊与着色强度。",
            code = """
                LiquidGlassTheme(opacity: opacity) {
                    LiquidGlassSurface(variant: .regular) { Text("Regular") }
                }
            """,
        ) {
            LiquidKitOpacitySlider(opacity, { opacity = it }, label = "玻璃不透明度")
            LiquidKitTheme(opacity = opacity, dark = liquidKitAppearance().dark) {
                Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Spacer(
                        Modifier.matchParentSize().layerBackdrop(scene)
                            .background(Brush.horizontalGradient(listOf(Color(0xffff9500), Color(0xff00c7be), Color(0xff5856d6)))),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (v in LiquidKitVariant.entries) {
                            LiquidKitGlassSurface(backdrop = scene, variant = v) {
                                BasicText(v.name, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = TextStyle(colors.ink, 14.sp, FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimationsTransitionsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var toggled by remember { mutableStateOf(false) }
    val snappy by animateFloatAsState(if (toggled) 1f else 0f, spring(0.85f, 630f), label = "snappy")
    val smooth by animateFloatAsState(if (toggled) 1f else 0f, spring(1.0f, 380f), label = "smooth")
    val bouncy by animateFloatAsState(if (toggled) 1f else 0f, spring(0.65f, 320f), label = "bouncy")

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "SwiftUI 弹簧物理预设对比",
            subtitle = "snappy / smooth / bouncy 三档阻尼曲线同步对比。",
            code = """
                withAnimation(.bouncy) { toggled.toggle() }
            """,
        ) {
            for ((label, frac, color) in listOf(
                Triple("snappy", snappy, Color(0xff007aff)),
                Triple("smooth", smooth, Color(0xff34c759)),
                Triple("bouncy", bouncy, Color(0xffff9500)),
            )) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(label, style = TextStyle(colors.secondary, 12.sp))
                    BoxWithConstraints(Modifier.fillMaxWidth().height(26.dp).clip(LiquidKitCapsule).background(colors.secondary.copy(alpha = .12f))) {
                        val travel = maxWidth - 26.dp
                        Box(Modifier.offset(x = travel * frac).size(26.dp).clip(CircleShape).background(color))
                    }
                }
            }
            LiquidKitActionButton("触发弹簧对比", { toggled = !toggled }, style = LiquidKitActionStyle.Filled)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 10: Swift Charts
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun BarLineAreaChartsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var horizontal by remember { mutableStateOf(false) }
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    val barPoints = remember {
        listOf(
            LiquidKitChartSeriesPoint("1月", "国内", 120f, Color(0xff007aff)),
            LiquidKitChartSeriesPoint("1月", "海外", 80f, Color(0xff00c7be)),
            LiquidKitChartSeriesPoint("2月", "国内", 150f, Color(0xff007aff)),
            LiquidKitChartSeriesPoint("2月", "海外", 95f, Color(0xff00c7be)),
            LiquidKitChartSeriesPoint("3月", "国内", 180f, Color(0xff007aff)),
            LiquidKitChartSeriesPoint("3月", "海外", 130f, Color(0xff00c7be)),
            LiquidKitChartSeriesPoint("4月", "国内", 210f, Color(0xff007aff)),
            LiquidKitChartSeriesPoint("4月", "海外", 165f, Color(0xff00c7be)),
            LiquidKitChartSeriesPoint("5月", "国内", 195f, Color(0xff007aff)),
            LiquidKitChartSeriesPoint("5月", "海外", 175f, Color(0xff00c7be)),
        )
    }
    val months = listOf("1月", "2月", "3月", "4月", "5月")
    val domestic = listOf(120f, 150f, 180f, 210f, 195f)

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "BarMark 分组与横向柱状图",
            subtitle = "一键在垂直柱状图与横向条形图间切换。",
            code = """
                Chart(salesData) { BarMark(x: .value("月份", ${"$"}0.month), y: .value("营收", ${"$"}0.revenue)) }
            """,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("切换为横向条形图", Modifier.weight(1f), style = TextStyle(colors.ink, 15.sp))
                LiquidKitToggle({ horizontal }, { horizontal = it })
            }
            LiquidKitBarChart(barPoints, horizontal = horizontal, colors = colors)
        }

        DemoSection(
            title = "LineMark + AreaMark + RuleMark 与划选",
            subtitle = "在图表上左右滑动查看各月营收与目标阈值（160）。",
            code = """
                Chart { AreaMark(...); LineMark(...); RuleMark(y: .value("目标", 160)) }
                    .chartXSelection(value: ${"$"}selectedMonth)
            """,
        ) {
            LiquidKitLineAreaChart(
                labels = months,
                values = domestic,
                ruleValue = 160f,
                selectedIndex = selectedIdx,
                onSelectIndex = { selectedIdx = it },
                colors = colors,
            )
            val tip = selectedIdx?.let { "${months[it]} 国内营收：${domestic[it].roundToInt()}" } ?: "在折线图上左右滑动查看数值"
            BasicText(tip, style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

@Composable
fun PieDonutChartsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var innerRatio by remember { mutableFloatStateOf(0.58f) }
    var gapDeg by remember { mutableFloatStateOf(2.5f) }
    val slices = remember {
        listOf(
            LiquidKitPieSlice("iOS 27", 46f, Color(0xff007aff)),
            LiquidKitPieSlice("Android", 28f, Color(0xff34c759)),
            LiquidKitPieSlice("macOS", 16f, Color(0xffaf52de)),
            LiquidKitPieSlice("visionOS", 10f, Color(0xffff9500)),
        )
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "SectorMark 饼图与环形图动态转换",
            subtitle = "调节内径比例可在实心饼图与甜甜圈环形图间无缝切换。",
            code = """
                Chart(shares) { SectorMark(angle: .value("占比", ${"$"}0.value), innerRadius: .ratio(innerRatio)) }
            """,
        ) {
            LiquidKitLabeledContent("内径比例", "${(innerRatio * 100).roundToInt()}%", colors = colors)
            LiquidKitSlider({ innerRatio }, { innerRatio = it }, 0f..0.75f)
            LiquidKitLabeledContent("扇区间隙", "${gapDeg.roundToInt()}°", colors = colors)
            LiquidKitSlider({ gapDeg }, { gapDeg = it }, 0f..8f)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiquidKitPieDonutChart(slices, innerRadiusRatio = innerRatio, angularInsetDegrees = gapDeg) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("总计", style = TextStyle(colors.secondary, 11.sp))
                        BasicText("100%", style = TextStyle(colors.ink, 18.sp, FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun MathPlotsChartsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var freq by remember { mutableFloatStateOf(1.5f) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "LinePlot 数学函数曲线 (sin & cos)",
            subtitle = "蓝色为 sin(ωx)，橙色为 cos(ωx)，可交互调节频率。",
            code = """
                Chart { LinePlot(x: "x", y: "sin") { x in sin(x * freq) } }
            """,
        ) {
            LiquidKitLabeledContent("频率 ω", "${(freq * 10).roundToInt() / 10f}x", colors = colors)
            LiquidKitSlider({ freq }, { freq = it }, 0.5f..4f)
            LiquidKitMathPlotChart(frequency = freq, colors = colors)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 11: Gestures & Haptics
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun BasicGesturesDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var tapPos by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var status by remember { mutableStateOf("点击或拖拽下方卡片") }
    val animX by animateFloatAsState(dragOffset.x, spring(0.65f, 360f), label = "dx")
    val animY by animateFloatAsState(dragOffset.y, spring(0.65f, 360f), label = "dy")

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "SpatialTap、LongPress 与弹性拖拽",
            subtitle = "单击记录坐标、长按触发状态、拖拽松手自动弹簧归位。",
            code = """
                .gesture(DragGesture().onChanged { offset = ${"$"}0.translation }.onEnded { offset = .zero })
            """,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.secondary.copy(alpha = .10f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tapPos = it; status = "单击坐标 (${it.x.roundToInt()}, ${it.y.roundToInt()})" },
                            onLongPress = { status = "触发长按 LongPress" },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(animX.roundToInt(), animY.roundToInt()) }
                        .size(96.dp, 54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xff007aff))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { dragOffset = Offset.Zero; status = "拖拽松手回弹" },
                                onDragCancel = { dragOffset = Offset.Zero },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount
                                status = "拖拽中 (${dragOffset.x.roundToInt()}, ${dragOffset.y.roundToInt()})"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("拖我", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                }
            }
            BasicText(status, style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

@Composable
fun ComposedGesturesDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val chips = listOf("Liquid Glass", "SwiftUI", "Compose", "AGSL")
    var dropped by remember { mutableStateOf(listOf<String>()) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "跨容器标签投递演示",
            subtitle = "点击任意源标签投递至目标收集箱，可点击已投递标签移回。",
            code = """
                Text(tag).draggable(tag)
                DropZone().dropDestination(for: String.self) { items, _ in dropped.append(contentsOf: items) }
            """,
        ) {
            LiquidKitFlowLayout {
                chips.filter { it !in dropped }.forEach { tag ->
                    LiquidKitActionButton(tag, { dropped = dropped + tag }, style = LiquidKitActionStyle.Tinted, controlSize = LiquidKitControlSize.Small)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Color(0xff007aff).copy(alpha = .4f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                if (dropped.isEmpty()) {
                    BasicText("目标收集区（点击上方标签投递）", style = TextStyle(colors.secondary, 13.sp))
                } else {
                    LiquidKitFlowLayout {
                        dropped.forEach { tag ->
                            LiquidKitActionButton(tag, { dropped = dropped - tag }, style = LiquidKitActionStyle.Filled, controlSize = LiquidKitControlSize.Small)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HapticsFeedbackDemo(item: ComponentItem, onBack: () -> Unit) {
    val view = LocalView.current
    val colors = liquidKitColors()
    var lastHaptic by remember { mutableStateOf("点击按钮体验系统马达震动") }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "SensoryFeedback 原生触觉映射",
            subtitle = "直接调用 Android View.performHapticFeedback 对齐 iOS impact / selection / confirm / reject。",
            code = """
                Button("轻触反馈") { trigger += 1 }.sensoryFeedback(.impact(flexibility: .soft), trigger: trigger)
            """,
        ) {
            LiquidKitFlowLayout {
                LiquidKitActionButton("Selection 轻点", {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastHaptic = "已触发 CLOCK_TICK (selection)"
                }, style = LiquidKitActionStyle.Tinted)
                LiquidKitActionButton("Impact 重击", {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    lastHaptic = "已触发 LONG_PRESS (impact)"
                }, style = LiquidKitActionStyle.Tinted)
                LiquidKitActionButton("Confirm 成功", {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    lastHaptic = "已触发 CONTEXT_CLICK (success)"
                }, style = LiquidKitActionStyle.Filled, tint = Color(0xff34c759))
            }
            BasicText(lastHaptic, style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 12: Advanced Layout & Rich Text
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun RichTextEditorDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var text by remember { mutableStateOf("Liquid Glass 富文本实时样式演示") }
    var bold by remember { mutableStateOf(true) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    var tintName by remember { mutableStateOf("默认") }
    val tintColor = when (tintName) {
        "蓝" -> Color(0xff007aff)
        "紫" -> Color(0xffaf52de)
        "橙" -> Color(0xffff9500)
        else -> colors.ink
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "富文本格式切换与实时渲染",
            subtitle = "实时组合粗体、斜体、下划线与前景色。",
            code = """
                TextEditor(text: ${"$"}attributedText, selection: ${"$"}selection)
            """,
        ) {
            LiquidKitFlowLayout {
                LiquidKitToggleButton("粗体", bold, { bold = it }, controlSize = LiquidKitControlSize.Small)
                LiquidKitToggleButton("斜体", italic, { italic = it }, controlSize = LiquidKitControlSize.Small)
                LiquidKitToggleButton("下划线", underline, { underline = it }, controlSize = LiquidKitControlSize.Small)
            }
            LiquidKitSegmentedPicker(listOf("默认", "蓝", "紫", "橙"), tintName, { tintName = it }, colors = colors)
            LiquidKitTextField(text, { text = it }, "编辑富文本...", colors = colors, style = LiquidKitTextFieldStyle.RoundedBorder)
            val rich = buildAnnotatedString {
                pushStyle(
                    SpanStyle(
                        color = tintColor,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
                    ),
                )
                append(text)
                pop()
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.secondary.copy(alpha = .08f)).padding(12.dp)) {
                BasicText(rich, style = TextStyle(fontSize = 17.sp))
            }
        }
    }
}

@Composable
fun CustomLayoutFlowDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var spacing by remember { mutableFloatStateOf(8f) }
    var tags by remember { mutableStateOf(listOf("Liquid Glass", "SwiftUI", "AGSL", "KMP", "Predictive Back", "iOS 27", "Compose")) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "FlowLayout 自动换行流式布局",
            subtitle = "自定义 Compose Layout 实现自动折行，支持动态间距与标签增删。",
            code = """
                FlowLayout(spacing: spacing) { ForEach(tags) { TagChip(${"$"}0) } }
            """,
        ) {
            LiquidKitLabeledContent("标签间距", "${spacing.roundToInt()} dp", colors = colors)
            LiquidKitSlider({ spacing }, { spacing = it }, 4f..18f)
            LiquidKitFlowLayout(horizontalSpacing = spacing.dp, verticalSpacing = spacing.dp) {
                tags.forEach { t ->
                    LiquidKitActionButton(t, { tags = tags - t }, style = LiquidKitActionStyle.Tinted, controlSize = LiquidKitControlSize.Small)
                }
            }
            LiquidKitActionButton("添加新标签", { tags = tags + "标签 ${tags.size + 1}" }, style = LiquidKitActionStyle.Filled, controlSize = LiquidKitControlSize.Small)
        }
    }
}

@Composable
fun GeometryVisualEffectsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "onGeometryChange 实时尺寸监听与视差卡片",
            subtitle = "零额外重排读取布局像素尺寸。",
            code = """
                Card().onGeometryChange(for: CGSize.self) { ${"$"}0.size } action: { size = ${"$"}0 }
            """,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .onSizeChanged { boxSize = it }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xff007aff), Color(0xff00c7be)))),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("实测像素尺寸：${boxSize.width} × ${boxSize.height} px", style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun TabViewAndSplitViewDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val scene = rememberLayerBackdrop()
    var tab by remember { mutableStateOf("music") }
    var playing by remember { mutableStateOf(true) }
    var minimized by remember { mutableStateOf(false) }
    var showSplitPreview by remember { mutableStateOf(false) }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "TabView + tabViewBottomAccessory 附属播放条",
            subtitle = "iOS 26/27 悬浮液态玻璃底栏、独立搜索球与上方附属播放胶囊。",
            code = """
                TabView { ... }
                    .tabViewBottomAccessory { MiniPlayerBar() }
                    .tabBarMinimizeBehavior(.onScrollDown)
            """,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("最小化底栏", Modifier.weight(1f), style = TextStyle(colors.ink, 14.sp))
                LiquidKitToggle({ minimized }, { minimized = it })
            }
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp))) {
                LiquidKitMeshGradient(
                    width = 2,
                    height = 2,
                    colors = listOf(Color(0xff233756), Color(0xff30a3a6), Color(0xff5856d6), Color(0xffdc8b4d)),
                    modifier = Modifier.matchParentSize().layerBackdrop(scene),
                )
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiquidKitTabViewBottomAccessory(backdrop = scene, onClick = { playing = !playing }) {
                        LiquidKitIcon(if (playing) LiquidKitSymbol.Play else LiquidKitSymbol.Clock, Color.White, Modifier.size(18.dp))
                        BasicText(if (playing) "正在播放 · Liquid Glass Theme" else "已暂停", Modifier.weight(1f), style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold))
                    }
                    LiquidKitTabBar(
                        tabs = listOf(
                            LiquidKitTab("music", "音乐", LiquidKitSymbol.Play),
                            LiquidKitTab("lib", "资料库", LiquidKitSymbol.FilledFolder),
                        ),
                        selected = tab,
                        onSelect = { tab = it },
                        backdrop = scene,
                        minimized = minimized,
                        onExpandFromMinimized = { minimized = false },
                        searchAction = {},
                    )
                }
            }
        }

        DemoSection(
            title = "NavigationSplitView 响应式多栏布局",
            subtitle = "窄屏（<600dp）呈现独立导航栈与右滑返回，横屏/宽屏（>=600dp）自动展开侧边栏 + 详情双栏视图。",
            code = """
                NavigationSplitView {
                    SidebarContent()
                } detail: {
                    DetailContent()
                }
            """,
        ) {
            LiquidKitActionButton(
                label = "打开独立 SplitView / 自适应导航页",
                onClick = { showSplitPreview = true },
                style = LiquidKitActionStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showSplitPreview) {
        StandaloneAdaptiveNavigationDialog(onDismiss = { showSplitPreview = false })
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 13: Styling, Transforms & Search
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ColorsAndHierarchyDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var mix by remember { mutableFloatStateOf(0.5f) }
    val blended = lerp(Color(0xff00c7be), Color(0xffff2d55), mix)
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Color.mix 交互混色与五级透明层级",
            subtitle = "滑动调节青色与粉色的插值比例。",
            code = """
                Color.cyan.mix(with: .pink, by: mix)
            """,
        ) {
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(blended), contentAlignment = Alignment.Center) {
                BasicText("混合比例 ${(mix * 100).roundToInt()}%", style = TextStyle(Color.White, 15.sp, FontWeight.Bold))
            }
            LiquidKitSlider({ mix }, { mix = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (alpha in listOf(1f, .75f, .5f, .3f, .15f)) {
                    Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(blended.copy(alpha = alpha)))
                }
            }
        }
    }
}

@Composable
fun TransformsAndMasksDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var rotY by remember { mutableFloatStateOf(18f) }
    var rotX by remember { mutableFloatStateOf(-8f) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "3D 透视旋转 (rotation3DEffect)",
            subtitle = "实时调节 X / Y 轴三维透视旋转角。",
            code = """
                Card().rotation3DEffect(.degrees(rotY), axis: (x: 0, y: 1, z: 0))
            """,
        ) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(190.dp, 86.dp)
                        .graphicsLayer {
                            rotationY = rotY
                            rotationX = rotX
                            cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xff5856d6), Color(0xff00c7be)))),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("3D Liquid Card", style = TextStyle(Color.White, 16.sp, FontWeight.Bold))
                }
            }
            LiquidKitLabeledContent("Y 轴旋转", "${rotY.roundToInt()}°", colors = colors)
            LiquidKitSlider({ rotY }, { rotY = it }, -45f..45f, neutralValue = 0f)
            LiquidKitLabeledContent("X 轴俯仰", "${rotX.roundToInt()}°", colors = colors)
            LiquidKitSlider({ rotX }, { rotX = it }, -30f..30f, neutralValue = 0f)
        }
    }
}

@Composable
fun CustomStylesShowcaseDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var checked by remember { mutableStateOf(true) }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "自定义组合样式组件",
            subtitle = "基于 LiquidKit 原语快速封装品牌化按钮、复选开关与分组卡片。",
            code = """
                struct BrandButtonStyle: ButtonStyle { ... }
            """,
        ) {
            LiquidKitGroupBox("品牌卡片主题", colors = colors) {
                LiquidKitToggleButton(if (checked) "已启用自定义样式" else "点击启用", checked, { checked = it }, tint = Color(0xff5856d6))
                LiquidKitActionButton("渐变强调按钮", {}, tint = Color(0xffff2d55), style = LiquidKitActionStyle.GlassProminent)
            }
        }
    }
}

@Composable
fun SearchAndFocusDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("全部") }
    val data = listOf("LiquidGlassSurface" to "材质", "LiquidTabBar" to "导航", "LiquidSlider" to "控件", "LiquidBarChart" to "图表")
    val results = data.filter { (name, cat) ->
        (scope == "全部" || cat == scope) && (query.isBlank() || name.contains(query, ignoreCase = true) || cat.contains(query))
    }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Searchable + SearchScopes 实时过滤",
            subtitle = "液态玻璃搜索栏联动 Scope 分类筛选。",
            code = """
                .searchable(text: ${"$"}query).searchScopes(${"$"}scope) { ... }
            """,
        ) {
            LiquidKitSearchField(query, { query = it }, "搜索 API 名称...", colors = colors)
            LiquidKitSegmentedPicker(listOf("全部", "材质", "导航", "控件", "图表"), scope, { scope = it }, colors = colors)
            results.forEach { (name, cat) ->
                LiquidKitLabeledContent(name, cat, colors = colors)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Category 14: System Frameworks & Real-time
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun NativeWebViewDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    val dark = liquidKitAppearance().dark
    var htmlMode by remember { mutableStateOf("本地 HTML") }
    val sampleHtml = remember(dark) {
        val bg = if (dark) "#111318" else "#f6f8fc"
        val fg = if (dark) "#f2f4f8" else "#111318"
        """
        <html><body style="margin:0;padding:18px;font-family:-apple-system,sans-serif;background:$bg;color:$fg;">
          <h3 style="margin:0 0 8px 0;color:#00a3c4;">Native Android WebView</h3>
          <p style="font-size:14px;line-height:1.5;opacity:0.85;">
            此卡片由 Android 原生 <code>android.webkit.WebView</code> 实时渲染，桥接 SwiftUI iOS 26+ <code>WebView / WebPage</code>。
          </p>
          <button onclick="this.innerText='JS 交互已触发 ✓'" style="padding:8px 14px;border:none;border-radius:999px;background:#007aff;color:#fff;font-weight:600;">
            点击测试 JS 交互
          </button>
        </body></html>
        """.trimIndent()
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "Android 原生 WebView 实时渲染",
            subtitle = "支持注入本地 HTML 与 JS 交互回调。",
            code = """
                WebView(page) // Android 端由 AndroidView { WebView(it) } 原生承接
            """,
        ) {
            LiquidKitSegmentedPicker(listOf("本地 HTML", "精简卡片"), htmlMode, { htmlMode = it }, colors = colors)
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(175.dp).clip(RoundedCornerShape(12.dp)),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL(null, sampleHtml, "text/html", "UTF-8", null)
                    }
                },
                update = { web ->
                    val html = if (htmlMode == "本地 HTML") sampleHtml else sampleHtml.replace("Native Android WebView", "LiquidKit Embedded WebPage")
                    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                },
            )
        }
    }
}

@Composable
fun MapKitSwiftUIDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var city by remember { mutableStateOf("北京") }
    var satellite by remember { mutableStateOf(false) }
    val pinPos = when (city) {
        "上海" -> Offset(0.72f, 0.62f)
        "深圳" -> Offset(0.58f, 0.78f)
        else -> Offset(0.45f, 0.34f)
    }
    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "交互式矢量地图、地标 Marker 与围栏圆",
            subtitle = "替代 iOS 独占 MapKit，支持城市视角切换与地图样式切换。",
            code = """
                Map { Marker(city, coordinate: coord); MapCircle(center: coord, radius: 500) }
            """,
        ) {
            LiquidKitSegmentedPicker(listOf("北京", "上海", "深圳"), city, { city = it }, colors = colors)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("卫星混合风格", Modifier.weight(1f), style = TextStyle(colors.ink, 14.sp))
                LiquidKitToggle({ satellite }, { satellite = it })
            }
            Canvas(Modifier.fillMaxWidth().height(165.dp).clip(RoundedCornerShape(14.dp))) {
                val bg = if (satellite) Color(0xff132a2e) else Color(0xffe7efe4)
                val road = if (satellite) Color(0xff2c585c) else Color(0xffffffff)
                drawRect(bg)
                for (i in 1..5) {
                    val y = size.height * i / 6f
                    drawLine(road, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                    val x = size.width * i / 6f
                    drawLine(road, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5.dp.toPx())
                }
                val center = Offset(size.width * pinPos.x, size.height * pinPos.y)
                drawCircle(Color(0xff007aff).copy(alpha = .22f), radius = 36.dp.toPx(), center = center)
                drawCircle(Color(0xff007aff), radius = 36.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
                drawCircle(Color(0xffff3b30), radius = 8.dp.toPx(), center = center)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = center)
            }
            BasicText("当前聚焦：$city 中心地理围栏", style = TextStyle(colors.secondary, 12.sp))
        }
    }
}

@Composable
fun TipKitAndMediaDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var showTip by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0.28f) }

    LaunchedEffect(playing) {
        while (playing) {
            delay(80)
            pos = (pos + 0.01f) % 1f
        }
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "TipKit 引导提示卡片 (LiquidKitTipCard)",
            subtitle = "支持内联展示、关闭与重置。",
            code = """
                TipView(FavoriteTip())
            """,
        ) {
            if (showTip) {
                LiquidKitTipCard(
                    title = "快速收藏常用组件",
                    message = "点击页面右上角星标即可把当前组件加入首页收藏筛选。",
                    symbol = LiquidKitSymbol.Lightbulb,
                    onDismiss = { showTip = false },
                    colors = colors,
                )
            } else {
                LiquidKitActionButton("重新显示 TipCard", { showTip = true }, style = LiquidKitActionStyle.Tinted, controlSize = LiquidKitControlSize.Small)
            }
        }

        DemoSection(
            title = "媒体播放控制条",
            subtitle = "支持播放/暂停与实时进度拖动。",
            code = """
                VideoPlayer(player: player).frame(height: 180)
            """,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiquidKitActionButton(
                    if (playing) "暂停" else "播放",
                    { playing = !playing },
                    style = LiquidKitActionStyle.Filled,
                    leadingSymbol = LiquidKitSymbol.Play,
                    controlSize = LiquidKitControlSize.Small,
                )
                Box(Modifier.weight(1f)) {
                    LiquidKitSlider({ pos }, { pos = it })
                }
            }
        }
    }
}

@Composable
fun TimelineAndPathsDemo(item: ComponentItem, onBack: () -> Unit) {
    val colors = liquidKitColors()
    var tick by remember { mutableLongStateOf(0L) }
    var trim by remember { mutableFloatStateOf(0.85f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            tick += 60
        }
    }

    ComponentDemoPage(item, onBack) {
        DemoSection(
            title = "TimelineView 实时模拟时钟与星形描边",
            subtitle = "帧驱动指针旋转与动态 Path 绘制。",
            code = """
                TimelineView(.animation) { ctx in AnalogClock(date: ctx.date) }
            """,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(110.dp)) {
                    val r = size.minDimension * 0.44f
                    drawCircle(colors.separator.copy(alpha = .5f), r, style = Stroke(2.dp.toPx()))
                    val secRad = ((tick / 1000f) * 6f - 90f) * (PI / 180.0)
                    val minRad = ((tick / 60000f) * 6f - 90f) * (PI / 180.0)
                    drawLine(colors.ink, center, Offset(center.x + r * 0.6f * cos(minRad).toFloat(), center.y + r * 0.6f * sin(minRad).toFloat()), 3.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0xffff3b30), center, Offset(center.x + r * 0.82f * cos(secRad).toFloat(), center.y + r * 0.82f * sin(secRad).toFloat()), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(Color(0xffff3b30), 3.5.dp.toPx(), center)
                }
                Canvas(Modifier.size(110.dp)) {
                    val rOuter = size.minDimension * 0.44f
                    val rInner = rOuter * 0.44f
                    val pts = (0 until 10).map { i ->
                        val rad = (i * 36.0 - 90.0) * (PI / 180.0)
                        val r = if (i % 2 == 0) rOuter else rInner
                        Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
                    }
                    val totalSegs = (pts.size * trim).roundToInt().coerceIn(1, pts.size)
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1..totalSegs) {
                            val pt = pts[i % pts.size]
                            lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(path, Color(0xff007aff), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            LiquidKitLabeledContent("Path 描边进度", "${(trim * 100).roundToInt()}%", colors = colors)
            LiquidKitSlider({ trim }, { trim = it }, 0.1f..1f)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Central Router mapping all 46 ComponentRoutes 1:1
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ComponentDetailRouter(route: ComponentRoute, onBack: () -> Unit) {
    val item = ComponentCatalog.item(route) ?: return
    when (route) {
        ComponentRoute.LiquidGlassEffects -> LiquidGlassEffectsDemo(item, onBack)
        ComponentRoute.GlassButtons -> GlassButtonsDemo(item, onBack)
        ComponentRoute.Ios27NewFeatures -> Ios27NewFeaturesDemo(item, onBack)
        ComponentRoute.Buttons -> ButtonDemo(item, onBack)
        ComponentRoute.MenusAndLinks -> MenuAndLinkDemo(item, onBack)
        ComponentRoute.Toggles -> ToggleDemo(item, onBack)
        ComponentRoute.TextAndLabel -> TextAndLabelDemo(item, onBack)
        ComponentRoute.TextField -> TextFieldDemo(item, onBack)
        ComponentRoute.TextEditor -> TextEditorDemo(item, onBack)
        ComponentRoute.Picker -> PickerDemo(item, onBack)
        ComponentRoute.DateAndColorPicker -> DateAndColorPickerDemo(item, onBack)
        ComponentRoute.SliderAndStepper -> SliderAndStepperDemo(item, onBack)
        ComponentRoute.ProgressView -> ProgressViewDemo(item, onBack)
        ComponentRoute.Gauge -> GaugeDemo(item, onBack)
        ComponentRoute.StatusEmptyStates -> StatusEmptyStatesDemo(item, onBack)
        ComponentRoute.StacksAndGrid -> StacksAndGridDemo(item, onBack)
        ComponentRoute.ScrollView -> ScrollViewDemo(item, onBack)
        ComponentRoute.ContainersGrouping -> ContainersGroupingDemo(item, onBack)
        ComponentRoute.ListStyles -> ListStylesDemo(item, onBack)
        ComponentRoute.ListInteractions -> ListInteractionsDemo(item, onBack)
        ComponentRoute.OutlineAndTable -> OutlineAndTableDemo(item, onBack)
        ComponentRoute.NavigationAndToolbar -> NavigationAndToolbarDemo(item, onBack)
        ComponentRoute.SheetsPopovers -> SheetsPopoversDemo(item, onBack)
        ComponentRoute.AlertsDialogs -> AlertsDialogsDemo(item, onBack)
        ComponentRoute.ShapesGradients -> ShapesGradientsDemo(item, onBack)
        ComponentRoute.SymbolsImages -> SymbolsImagesDemo(item, onBack)
        ComponentRoute.MaterialsEffects -> MaterialsEffectsDemo(item, onBack)
        ComponentRoute.AnimationsTransitions -> AnimationsTransitionsDemo(item, onBack)
        ComponentRoute.BarLineAreaCharts -> BarLineAreaChartsDemo(item, onBack)
        ComponentRoute.PieDonutCharts -> PieDonutChartsDemo(item, onBack)
        ComponentRoute.MathPlotsCharts -> MathPlotsChartsDemo(item, onBack)
        ComponentRoute.BasicGestures -> BasicGesturesDemo(item, onBack)
        ComponentRoute.ComposedGestures -> ComposedGesturesDemo(item, onBack)
        ComponentRoute.HapticsFeedback -> HapticsFeedbackDemo(item, onBack)
        ComponentRoute.RichTextEditor -> RichTextEditorDemo(item, onBack)
        ComponentRoute.CustomLayoutFlow -> CustomLayoutFlowDemo(item, onBack)
        ComponentRoute.GeometryVisualEffects -> GeometryVisualEffectsDemo(item, onBack)
        ComponentRoute.TabViewAndSplitView -> TabViewAndSplitViewDemo(item, onBack)
        ComponentRoute.ColorsAndHierarchy -> ColorsAndHierarchyDemo(item, onBack)
        ComponentRoute.TransformsAndMasks -> TransformsAndMasksDemo(item, onBack)
        ComponentRoute.CustomStylesShowcase -> CustomStylesShowcaseDemo(item, onBack)
        ComponentRoute.SearchAndFocus -> SearchAndFocusDemo(item, onBack)
        ComponentRoute.NativeWebView -> NativeWebViewDemo(item, onBack)
        ComponentRoute.MapKitSwiftUI -> MapKitSwiftUIDemo(item, onBack)
        ComponentRoute.TipKitAndMedia -> TipKitAndMediaDemo(item, onBack)
        ComponentRoute.TimelineAndPaths -> TimelineAndPathsDemo(item, onBack)
    }
}
