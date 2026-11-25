package com.kyant.foldfiles

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val families = listOf("按钮与链接", "选择与数值", "文本与输入", "状态与进度", "图像与形状", "列表与容器", "导航与弹层", "玻璃材质", "按压与回弹")
private val familySymbols = listOf(
    LiquidSymbol.HandTap, LiquidSymbol.SliderHorizontal, LiquidSymbol.TextCursor,
    LiquidSymbol.Gauge, LiquidSymbol.SquareOnCircle, LiquidSymbol.ListBulletRectangle,
    LiquidSymbol.MenuCard, LiquidSymbol.CircleHalfFilled, LiquidSymbol.HandDraw,
)
private val views = listOf("列表", "网格", "分栏")

private fun galleryParent(route: String): String? = when (route) {
    "" -> null
    "工具栏过渡" -> families[0]
    "滚轮选择器", "多日期选择" -> families[1]
    else -> ""
}

private fun galleryDepth(route: String): Int = when (route) {
    "" -> 0
    "工具栏过渡", "滚轮选择器", "多日期选择" -> 2
    else -> 1
}

@Composable
internal fun FileComponentGallery(colors: LiquidListColors, model: FilesModel, dismiss: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var dateYear by rememberSaveable { mutableIntStateOf(2026) }
    var dateMonth by rememberSaveable { mutableIntStateOf(11) }
    var dateDay by rememberSaveable { mutableIntStateOf(24) }
    var multiDateKeys by rememberSaveable { mutableStateOf(listOf("2026-11-24")) }
    var choice by rememberSaveable { mutableStateOf("列表") }

    fun back() {
        val route = page ?: ""
        val parent = galleryParent(route)
        if (parent == null) dismiss() else page = parent.ifEmpty { null }
    }

    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SyncLiquidDialogSystemBars(liquidGlassAppearance().dark)
        val tint = accent()
        val modalBackdrop = rememberLayerBackdrop()
        val scope = rememberCoroutineScope()
        val dismissPreview = remember { Animatable(0f) }
        val route = page ?: ""
        val parentRoute = galleryParent(route)
        val sheetSpringTokens = LocalLiquidGlassTokens.current.sheet.detentSpring
        val sheetSpring = remember(sheetSpringTokens) { spring<Float>(sheetSpringTokens.damping, sheetSpringTokens.stiffness) }

        LiquidPredictiveBackHandler(enabled = route.isEmpty()) { events ->
            try {
                events.collect { event ->
                    dismissPreview.snapTo(event.progress.coerceIn(0f, 1f))
                }
                dismiss()
            } catch (cancelled: CancellationException) {
                scope.launch { dismissPreview.animateTo(0f, sheetSpring) }
                throw cancelled
            }
        }

        LiquidModalHost {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val p = dismissPreview.value
                        if (p > 0f) {
                            val s = 1f - .055f * p
                            scaleX = s
                            scaleY = s
                            translationY = p * 44.dp.toPx()
                            alpha = 1f - .14f * p
                            shape = RoundedCornerShape((28f * p).dp)
                            clip = true
                        }
                    }
                    .layerBackdrop(modalBackdrop, enabled = rememberLiquidModalActive())
                    .background(colors.background),
            ) {
                LiquidSwipeBackNavigation(
                    page = route,
                    previousPage = parentRoute,
                    depth = ::galleryDepth,
                    contentKey = { it },
                    onNavigateBack = { target -> page = target.ifEmpty { null } },
                    modifier = Modifier.fillMaxSize(),
                ) { displayedRoute ->
                    val displayedPage = displayedRoute.ifEmpty { null }
                    val pageBackdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize().background(colors.background)) {
                        Spacer(Modifier.matchParentSize().layerBackdrop(pageBackdrop).background(colors.background))
                        if (displayedPage == families[8]) {
                            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 18.dp)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LiquidToolbarGroup(Modifier.size(44.dp), pageBackdrop) {
                                        LiquidToolbarButton("返回", ::back, Modifier.fillMaxSize()) {
                                            LiquidIcon(LiquidSymbol.Back, colors.ink, Modifier.size(18.dp))
                                        }
                                    }
                                    Label(families[8], 20, colors.ink, Modifier.weight(1f), bold = true)
                                }
                                GalleryPressSample(colors)
                            }
                        } else {
                            val scroll = key(displayedRoute) { rememberScrollState() }
                            LiquidNavigationScaffold(
                                title = displayedPage ?: "全部组件",
                                backdrop = pageBackdrop,
                                modifier = Modifier.fillMaxSize(),
                                titleMode = LiquidNavigationTitleMode.Inline,
                                colors = colors,
                                contentWindowInsets = WindowInsets.safeDrawing,
                                scrollOffsetPx = { scroll.value.toFloat() },
                                leading = { glass ->
                                    LiquidToolbarGroup(Modifier.size(44.dp), glass) {
                                        LiquidToolbarButton(if (displayedPage == null) "完成" else "返回", ::back, Modifier.fillMaxSize()) {
                                            LiquidIcon(if (displayedPage == null) LiquidSymbol.Close else LiquidSymbol.Back, colors.ink, Modifier.size(18.dp))
                                        }
                                    }
                                },
                            ) { insets ->
                                when (displayedPage) {
                                    null -> Column(
                                        Modifier.fillMaxSize().verticalScroll(scroll).padding(insets)
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        LiquidFormSection(colors = colors) {
                                            families.forEachIndexed { index, title ->
                                                navigationRow(title, { page = title }, leading = familySymbols[index], tint = tint)
                                            }
                                        }
                                    }

                                    "工具栏过渡" -> Column(
                                        Modifier.fillMaxSize().verticalScroll(scroll).padding(insets)
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                    ) {
                                        GalleryToolbarTransitions(colors, pageBackdrop)
                                    }

                                    "滚轮选择器" -> Column(
                                        Modifier.fillMaxSize().verticalScroll(scroll).padding(insets)
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                    ) {
                                        LiquidFormSection("显示方式", colors = colors) {
                                            row { LiquidWheelPicker(views, choice, { choice = it }, colors = colors) }
                                        }
                                    }

                                    "多日期选择" -> {
                                        val parsed = multiDateKeys.mapNotNull { key ->
                                            val parts = key.split('-')
                                            if (parts.size == 3) {
                                                val y = parts[0].toIntOrNull() ?: return@mapNotNull null
                                                val m = parts[1].toIntOrNull() ?: return@mapNotNull null
                                                val d = parts[2].toIntOrNull() ?: return@mapNotNull null
                                                runCatching { LiquidCalendarDate(y, m, d) }.getOrNull()
                                            } else null
                                        }.toSet()
                                        Column(
                                            Modifier.fillMaxSize().verticalScroll(scroll).padding(insets)
                                                .padding(horizontal = 18.dp, vertical = 10.dp),
                                        ) {
                                            LiquidFormSection("选取日期", colors = colors) {
                                                row {
                                                    LiquidMultiDatePicker(
                                                        dates = parsed,
                                                        onDatesChange = { next ->
                                                            multiDateKeys = next.map { "${it.year}-${it.month}-${it.day}" }.sorted()
                                                        },
                                                        tint = tint,
                                                        colors = colors,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    families[6] -> Box(Modifier.fillMaxSize().padding(insets).padding(horizontal = 18.dp)) {
                                        GalleryPresentations(colors, pageBackdrop, modalBackdrop)
                                    }

                                    else -> Column(
                                        Modifier.fillMaxSize().verticalScroll(scroll).padding(insets)
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        GalleryExamples(
                                            family = displayedPage,
                                            colors = colors,
                                            backdrop = pageBackdrop,
                                            modalBackdrop = modalBackdrop,
                                            model = model,
                                            choice = choice,
                                            onChoiceChange = { choice = it },
                                            date = LiquidCalendarDate(dateYear, dateMonth, dateDay),
                                            onDateChange = { d -> dateYear = d.year; dateMonth = d.month; dateDay = d.day },
                                            onOpenSubpage = { page = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryToolbarTransitions(colors: LiquidListColors, backdrop: Backdrop) {
    val examples = listOf("圆钮换页", "双钮到头像", "双钮到三钮")
    var example by rememberSaveable { mutableStateOf(examples.first()) }
    var destination by rememberSaveable(example) { mutableStateOf(false) }
    val scene = rememberLayerBackdrop()
    val toggle = { destination = !destination }
    val glassHeight = LiquidToolbarDefaults.height
    fun symbols(next: Boolean): List<LiquidSymbol?> = when (example) {
        examples[0] -> listOf(LiquidSymbol.More)
        examples[1] -> if (next) listOf(null) else listOf(LiquidSymbol.Share, LiquidSymbol.More)
        else -> if (next) listOf(LiquidSymbol.Bell, LiquidSymbol.Like, LiquidSymbol.DownloadCircle)
        else listOf(LiquidSymbol.Search, LiquidSymbol.Reload)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiquidSegmentedPicker(examples, example, { example = it; destination = false }, colors = colors, backdrop = backdrop)
        val field by androidx.compose.animation.animateColorAsState(
            if (destination) colors.surface else colors.background, label = "Toolbar page",
        )
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(20.dp))) {
            Spacer(Modifier.matchParentSize().layerBackdrop(scene).background(field))
            key(example) {
                LiquidToolbarReplacement(destination, scene, { next ->
                    LiquidToolbarDefaults.width(symbols(next))
                }, Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)) { next, current ->
                    val items = symbols(next)
                    val gap = LiquidToolbarDefaults.gap(items, LiquidToolbarSpacing.Actions)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        items.forEachIndexed { index, symbol ->
                            val before = if (index == 0) 0.dp else gap / 2
                            val after = if (index == items.lastIndex) 0.dp else gap / 2
                            val width = if (items.size == 1) glassHeight else LiquidToolbarDefaults.buttonWidth(symbol, LiquidToolbarSpacing.Actions)
                            val cell = Modifier.size(width + before + after, glassHeight)
                            val icon: @Composable BoxScope.() -> Unit = {
                                Box(Modifier.offset(x = (before - after) / 2), contentAlignment = Alignment.Center) {
                                    if (symbol == null) {
                                        Canvas(Modifier.size(38.dp).clip(CircleShape).background(Color(0xff6b949e))) {
                                            val u = size.width
                                            drawCircle(Color(0xffe1e9d2), .15f * u, androidx.compose.ui.geometry.Offset(.5f * u, .36f * u))
                                            drawCircle(Color(0xffe1e9d2), .33f * u, androidx.compose.ui.geometry.Offset(.5f * u, .94f * u))
                                        }
                                    } else LiquidIcon(symbol, colors.ink, Modifier.size(if (items.size == 1 && next) 26.dp else 24.dp))
                                }
                            }
                            if (current) {
                                LiquidToolbarButton("切换样例 " + (index + 1), toggle, cell, content = icon)
                            } else Box(cell, contentAlignment = Alignment.Center, content = icon)
                        }
                    }
                }
            }
        }
        LiquidActionButton(if (destination) "返回起始状态" else "切换工具栏", toggle, accent())
    }
}

@Composable
private fun GalleryPressSample(colors: LiquidListColors) {
    var mode by rememberSaveable { mutableStateOf("圆钮") }
    var gap by rememberSaveable { mutableFloatStateOf(11f) }
    var stagger by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf("") }
    var taps by rememberSaveable { mutableIntStateOf(0) }
    var contact by remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()
    val menu = rememberLiquidMenuState { result = it.title }
    val items = remember { (1..5).map { LiquidMenuItem("item$it", "项目 $it") } }
    Column(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) contact = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
        }
    }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LiquidSegmentedPicker(listOf("圆钮", "菜单", "短胶囊", "长胶囊", "相邻"), mode, { mode = it }, colors = colors, backdrop = backdrop)
        if (mode == "相邻") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("间距 ${gap.roundToInt()}", 13, colors.ink)
                LiquidSlider({ gap }, { gap = it }, 0f..28f, .01f, backdrop,
                    Modifier.weight(1f).semantics { contentDescription = "间距" })
                Label("错开", 13, colors.ink, Modifier.clearAndSetSemantics {}.clickable(
                    interactionSource = null, indication = null, onClick = { stagger = !stagger }))
                LiquidToggle({ stagger }, { stagger = it }, backdrop, Modifier.semantics { contentDescription = "错开" })
            }
        }
        LiquidAnchoredMenuHost(menu, backdrop, items, Modifier.fillMaxWidth().weight(1f)) {
            Spacer(Modifier.matchParentSize().layerBackdrop(backdrop).background(Color(0xff214261)))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val isMenu = mode == "菜单"
                val capsuleWidth = when (mode) { "短胶囊" -> 66.dp; "长胶囊" -> 110.dp; else -> 48.dp }
                val y = maxHeight * .34f
                val onTap: (String) -> Unit = { label -> taps++; result = "$label · $taps" }
                if (mode == "相邻") {
                    LiquidGlassEffectContainer(
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = y - 22.dp),
                        spacing = 7.dp,
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(gap.dp),
                        ) {
                            SampleCapsule("选择", 66.dp, backdrop, colors, onTap)
                            Box(Modifier.offset(y = if (stagger) 8.dp else 0.dp)) {
                                SampleCapsule("操作", 110.dp, backdrop, colors, onTap)
                            }
                        }
                    }
                } else {
                    val circle = mode == "圆钮" || isMenu
                    val x = if (circle) (maxWidth / 2 + 77.dp).coerceIn(0.dp, (maxWidth - 48.dp).coerceAtLeast(0.dp))
                    else maxWidth * .6f - capsuleWidth / 2
                    Box(Modifier.offset(x, y - if (circle) 24.dp else 22.dp)) {
                        if (circle) {
                            LiquidToolbarGroup(Modifier.size(48.dp), backdrop) {
                                val trigger = if (isMenu) Modifier.liquidMenuTrigger(menu) else Modifier
                                LiquidToolbarButton(if (isMenu) "展开样张菜单" else "样张圆钮", {
                                    if (isMenu) menu.show() else { taps++; result = "点击 $taps" }
                                }, Modifier.fillMaxSize().then(trigger)) {
                                    LiquidIcon(LiquidSymbol.More, colors.ink, Modifier.size(20.dp))
                                }
                            }
                        } else SampleCapsule(mode, capsuleWidth, backdrop, colors, onTap)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.size(12.dp).background(if (contact) Color(0xffff008c) else Color.Gray, CircleShape))
            Label("触点", 13, colors.secondary, Modifier.weight(1f))
            Label(result, 13, colors.secondary)
        }
    }
}

@Composable
private fun SampleCapsule(title: String, width: androidx.compose.ui.unit.Dp, backdrop: Backdrop,
    colors: LiquidListColors, onTap: (String) -> Unit) {
    LiquidToolbarGroup(Modifier.size(width, 44.dp), backdrop) {
        LiquidToolbarButton(title, { onTap(title) }, Modifier.fillMaxSize()) { Label(title, 16, colors.ink) }
    }
}

@Composable
private fun GalleryExamples(
    family: String,
    colors: LiquidListColors,
    backdrop: Backdrop,
    modalBackdrop: Backdrop,
    model: FilesModel,
    choice: String,
    onChoiceChange: (String) -> Unit,
    date: LiquidCalendarDate,
    onDateChange: (LiquidCalendarDate) -> Unit,
    onOpenSubpage: (String) -> Unit,
) {
    val context = LocalContext.current
    val tint = accent()
    var count by rememberSaveable { mutableIntStateOf(2) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var value by rememberSaveable { mutableFloatStateOf(.5f) }
    var stepped by rememberSaveable { mutableFloatStateOf(.5f) }
    var tabFilter by rememberSaveable { mutableStateOf("all") }
    var paletteChoice by rememberSaveable { mutableStateOf("teal") }
    var inlineOrder by rememberSaveable { mutableStateOf("按名称") }
    var glassTaps by rememberSaveable { mutableIntStateOf(0) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var markR by rememberSaveable { mutableFloatStateOf(.19f) }
    var markG by rememberSaveable { mutableFloatStateOf(.64f) }
    var markB by rememberSaveable { mutableFloatStateOf(.65f) }
    val pickedColor = Color(markR, markG, markB)

    when (family) {
        families[0] -> {
            LiquidFormSection(colors = colors) {
                navigationRow("工具栏过渡", { onOpenSubpage("工具栏过渡") })
            }
            LiquidFormSection("按钮样式", colors = colors) {
                row { LiquidActionButton("普通按钮", { count++ }, colors.ink, style = LiquidActionStyle.Plain) }
                row { LiquidActionButton("无边框按钮", { count++ }, tint, style = LiquidActionStyle.Borderless) }
                row { LiquidActionButton("带底色按钮", { count++ }, tint, style = LiquidActionStyle.Tinted) }
                row { LiquidActionButton("强调按钮", { count++ }, tint, style = LiquidActionStyle.Filled) }
                row { LiquidActionButton("玻璃按钮", { count++ }, Color.Unspecified, style = LiquidActionStyle.Glass) }
                row { LiquidActionButton("强调玻璃", { count++ }, tint, style = LiquidActionStyle.GlassProminent) }
                row { LiquidLabeledContent("点击计数", count.toString(), colors = colors) }
            }
            LiquidFormSection("内容操作", colors = colors) {
                row { LiquidActionButton(if (enabled) "编辑" else "完成编辑", { enabled = !enabled }, tint, style = LiquidActionStyle.Borderless) }
                if (!enabled) row { LiquidTextField(text, { text = it }, "项目名称", tint = tint, colors = colors) }
                row {
                    LiquidActionButton("粘贴", {
                        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                        clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { text = it.toString() }
                    }, tint, style = LiquidActionStyle.Filled, leadingSymbol = LiquidSymbol.Clipboard)
                }
                navigationRow(text.ifEmpty { "重命名项目" }, { prompt = "重命名" }, leading = LiquidSymbol.Document, tint = tint)
                row { LiquidActionButton("组件使用说明", { prompt = "使用说明" }, tint, style = LiquidActionStyle.Borderless) }
                row {
                    LiquidActionButton("分享", {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "折页组件样张"), "分享样张"))
                    }, tint, style = LiquidActionStyle.Borderless, leadingSymbol = LiquidSymbol.Share)
                }
            }
        }

        families[1] -> {
            LiquidFormSection("数值", colors = colors) {
                row {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label("显示预览", 17, colors.ink, Modifier.weight(1f))
                        LiquidToggle({ enabled }, { enabled = it }, backdrop, Modifier.semantics { contentDescription = "显示预览" })
                    }
                }
                row {
                    LiquidSlider({ value }, { value = it }, 0f..1f, .001f, backdrop,
                        Modifier.semantics { contentDescription = "调整进度" }, tapToSeek = false)
                }
                row {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LiquidLabeledContent("刻度吸附滑块", "${(stepped * 100).roundToInt()}%", colors = colors)
                        LiquidSlider(
                            value = { stepped },
                            onValueChange = { stepped = it },
                            valueRange = 0f..1f,
                            visibilityThreshold = .001f,
                            backdrop = backdrop,
                            modifier = Modifier.semantics { contentDescription = "刻度滑块" },
                            tapToSeek = true,
                            ticks = listOf(0f, .25f, .5f, .75f, 1f),
                        )
                    }
                }
                row { LiquidStepper("数量", count, { count = it }, 0..30, tint, colors = colors) }
            }
            LiquidFormSection("选择器", colors = colors) {
                row { LiquidMenuPicker("菜单", views, choice, onChoiceChange, tint, backdrop = modalBackdrop, colors = colors) }
                row { LiquidSegmentedPicker(views, choice, onChoiceChange, colors = colors, backdrop = backdrop) }
                row {
                    LiquidTabsPicker(
                        options = listOf(
                            LiquidTabOption("all", "全部", LiquidSymbol.Grid),
                            LiquidTabOption("doc", "文档", LiquidSymbol.Document),
                            LiquidTabOption("folder", "目录", LiquidSymbol.Folder),
                        ),
                        selectedId = tabFilter,
                        onSelect = { tabFilter = it },
                        backdrop = backdrop,
                        tint = tint,
                        colors = colors,
                    )
                }
                row {
                    LiquidPalettePicker(
                        options = listOf(
                            LiquidPaletteOption("teal", "青绿", Color(0xff30a3a6)),
                            LiquidPaletteOption("blue", "蔚蓝", Color(0xff007aff)),
                            LiquidPaletteOption("indigo", "靛青", Color(0xff5856d6)),
                            LiquidPaletteOption("amber", "琥珀", Color(0xffff9f0a)),
                            LiquidPaletteOption("coral", "珊瑚", Color(0xffff3b30)),
                        ),
                        selectedId = paletteChoice,
                        onSelect = { paletteChoice = it },
                        tint = tint,
                        colors = colors,
                    )
                }
                row { LiquidDatePicker("日期", date, onDateChange, tint, backdrop = modalBackdrop, colors = colors) }
                row {
                    LiquidColorPicker("标记颜色", pickedColor, {
                        markR = it.red; markG = it.green; markB = it.blue
                    }, tint = tint, backdrop = modalBackdrop, colors = colors)
                }
                navigationRow("滚轮选择器", { onOpenSubpage("滚轮选择器") })
                navigationRow("多日期选择", { onOpenSubpage("多日期选择") })
            }
            LiquidInlinePicker(
                options = listOf("按名称", "按修改时间", "按大小"),
                selected = inlineOrder,
                onSelect = { inlineOrder = it },
                title = "内联选择器",
                tint = tint,
                colors = colors,
            )
        }

        families[2] -> {
            LiquidFormSection("文本", colors = colors) {
                row { Label("折页文库", 22, colors.ink, bold = true) }
                row {
                    Row(Modifier.heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidIcon(LiquidSymbol.Document, tint, Modifier.size(21.dp))
                        Label("馆藏索引", 17, colors.ink)
                    }
                }
                row { LiquidLabeledContent("格式", "纯文本", colors = colors) }
            }
            LiquidFormSection("输入", colors = colors) {
                row { LiquidTextField(text, { text = it }, "名称", tint = tint, colors = colors) }
                row { LiquidTextField(secret, { secret = it }, "示例口令", secret = true, tint = tint, colors = colors) }
                row { LiquidTextField(notes, { notes = it }, "备注", multiline = true, tint = tint, colors = colors) }
            }
        }

        families[3] -> {
            LiquidFormSection("进度", colors = colors) {
                row {
                    LiquidSlider({ value }, { value = it }, 0f..1f, .001f, backdrop,
                        Modifier.semantics { contentDescription = "调整进度" }, tapToSeek = false)
                }
                row {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label("读取进度", 17, colors.ink)
                        LiquidProgressIndicator(value, tint, Modifier.fillMaxWidth())
                    }
                }
                row {
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                        LiquidActivityIndicator(label = "正在读取", colors = colors)
                    }
                }
                row { LiquidLinearGauge(value, tint, label = "占用", colors = colors) }
                row {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        LiquidGauge(value, tint, colors = colors, style = LiquidGaugeStyle.CircularCapacity)
                        LiquidGauge(value, tint, colors = colors, style = LiquidGaugeStyle.CircularOpen)
                    }
                }
            }
            LiquidFormSection(colors = colors) {
                row {
                    Box(Modifier.fillMaxWidth().height(152.dp), contentAlignment = Alignment.Center) {
                        LiquidUnavailableView("空文件夹", LiquidSymbol.Folder, colors = colors)
                    }
                }
            }
        }

        families[4] -> {
            LiquidFormSection("图像", colors = colors) {
                row {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LiquidIcon(LiquidSymbol.FilledFolder, tint, Modifier.size(22.dp))
                        Label("系统符号", 17, colors.ink)
                    }
                }
                row {
                    val image by produceState<ImageBitmap?>(null, context) {
                        this.value = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeResource(context.resources, R.drawable.fold_sample).asImageBitmap()
                        }
                    }
                    LiquidLabeledContent("异步图像", colors = colors) {
                        image?.let { Image(it, "异步图像", Modifier.size(60.dp, 38.dp)) }
                    }
                }
            }
            LiquidFormSection("形状", colors = colors) {
                for (figure in listOf("矩形", "圆角矩形", "独立圆角", "圆", "椭圆", "胶囊")) {
                    row {
                        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Label(figure, 17, colors.ink, Modifier.weight(1f))
                            if (figure == "椭圆") Canvas(Modifier.size(60.dp, 32.dp)) { drawOval(tint) }
                            else Spacer(Modifier.size(if (figure == "圆") 32.dp else 60.dp, 32.dp).background(tint, when (figure) {
                                "矩形" -> RectangleShape
                                "圆角矩形" -> RoundedCornerShape(9.dp)
                                "独立圆角" -> RoundedCornerShape(14.dp, 2.dp, 14.dp, 2.dp)
                                "胶囊" -> LiquidCapsule
                                else -> CircleShape
                            }))
                        }
                    }
                }
            }
        }

        families[5] -> {
            LiquidFormSection("分组", colors = colors) {
                row {
                    LiquidDisclosureGroup("文件夹", enabled, { enabled = it }, colors = colors) {
                        Spacer(Modifier.fillMaxWidth().height(.55.dp).background(colors.separator.copy(alpha = .55f)))
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LiquidIcon(LiquidSymbol.Folder, tint, Modifier.size(19.dp)); Label("路线手记", 17, colors.ink)
                        }
                        Spacer(Modifier.fillMaxWidth().height(.55.dp).background(colors.separator.copy(alpha = .55f)))
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            LiquidIcon(LiquidSymbol.Document, tint, Modifier.size(19.dp)); Label("用纸.csv", 17, colors.ink)
                        }
                    }
                }
                row {
                    LiquidGroupBox("馆藏", colors = colors) {
                        LiquidLabeledContent("项目", "2", colors = colors)
                    }
                }
                row {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label("控制组", 17, colors.ink, Modifier.weight(1f))
                        LiquidControlGroup(colors = colors) {
                            LiquidActionButton("增加", { count++ }, tint, style = LiquidActionStyle.Plain)
                            LiquidActionButton("减少", { count = maxOf(0, count - 1) }, tint, style = LiquidActionStyle.Plain)
                        }
                    }
                }
                row { LiquidStepper("馆藏数量", count, { count = it }, 0..30, tint, colors = colors) }
            }
            LiquidFormSection(colors = colors) {
                navigationRow("表格", { prompt = "表格" })
                navigationRow("滚动与分隔线", { prompt = "样张" })
            }
        }

        else -> {
            val sample = rememberLayerBackdrop()
            LiquidFormSection(colors = colors) {
                row {
                    Box(Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().layerBackdrop(sample).background(Brush.linearGradient(listOf(
                            Color(0xff5856d6), Color(0xff30b0c7), Color(0xffff9f0a)))),
                            contentAlignment = Alignment.Center) {
                            Column { repeat(4) { Label("Fold   ${it + 1}   材质校对", 17, colors.ink, bold = true) } }
                        }
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiquidGlassSurface(sample, Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) { LiquidIcon(LiquidSymbol.Grid, colors.ink, Modifier.size(20.dp)) }
                            }
                            LiquidToolbarGroup(Modifier.weight(1f).height(48.dp), backdrop = sample) {
                                Row(
                                    Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                ) {
                                    LiquidIcon(LiquidSymbol.Search, tint, Modifier.size(19.dp))
                                    Label("搜索", 16, colors.ink)
                                }
                            }
                        }
                    }
                }
                row {
                    LiquidOpacitySlider(model.opacity, model::changeOpacity, "玻璃不透明度",
                        onValueChangeFinished = model::saveOpacity, showIcons = true, backdrop = backdrop)
                }
                row { LiquidLabeledContent("应用衬底", "${(model.opacity * 100).roundToInt()}%", colors = colors) }
            }
            LiquidFormSection(colors = colors) {
                row { MenuMaterialSample(backdrop, colors) }
            }
            LiquidFormSection("系统玻璃", colors = colors) {
                row {
                    val variants = rememberLayerBackdrop()
                    Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Spacer(Modifier.matchParentSize().layerBackdrop(variants).background(Brush.horizontalGradient(
                            listOf(Color(0xffff9f0a), Color(0xff30b0c7), Color(0xff5856d6)))))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (variant in LiquidGlassVariant.entries) {
                                    LiquidGlassSurface(variants, variant = variant) {
                                        Label(variant.name, 15, colors.ink, Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LiquidGlassSurface(
                                    backdrop = variants,
                                    variant = LiquidGlassVariant.Regular,
                                    tint = tint,
                                    interactive = true,
                                    onClick = { glassTaps++ },
                                ) {
                                    Label("着色交互 · $glassTaps", 14, Color.White, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), bold = true)
                                }
                                LiquidGlassSurface(
                                    backdrop = variants,
                                    variant = LiquidGlassVariant.Clear,
                                    tint = Color(0xffff9f0a),
                                    interactive = true,
                                    onClick = { glassTaps++ },
                                ) {
                                    Label("Clear 着色", 14, colors.ink, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
                row {
                    val barScene = rememberLayerBackdrop()
                    Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(15.dp))) {
                        Box(
                            Modifier.matchParentSize().layerBackdrop(barScene).background(Brush.horizontalGradient(
                                listOf(Color(0xff233756), Color(0xff30a3a6), Color(0xffdc8b4d)))),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Label("SafeAreaBar 渐进模糊底栏预览", 14, Color.White, Modifier.padding(top = 12.dp), bold = true)
                        }
                        LiquidSafeAreaBar(
                            backdrop = barScene,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            edge = LiquidSafeAreaEdge.Bottom,
                            colors = colors,
                        ) {
                            Label("渐进安全区栏", 14, colors.ink, Modifier.weight(1f), bold = true)
                            Label("iOS 27", 13, colors.secondary)
                        }
                    }
                }
            }
        }
    }

    if (prompt == "重命名") {
        var draft by rememberSaveable { mutableStateOf(text) }
        val dismiss = { prompt = "" }
        val save = { text = draft; dismiss() }
        LiquidAlert("重命名", listOf(LiquidAlertAction("取消", LiquidAlertRole.Cancel, onClick = dismiss),
            LiquidAlertAction("保存", LiquidAlertRole.Primary, draft.isNotBlank(), save)), dismiss, tint, colors = colors,
            backdrop = modalBackdrop, field = { LiquidAlertTextField(draft, { draft = it }, "名称", tint, save, colors) })
    } else if (prompt == "使用说明") {
        val dismiss = { prompt = "" }
        LiquidAlert("组件使用说明", listOf(LiquidAlertAction("完成", onClick = dismiss)), dismiss, tint,
            message = "可切换系统外观，并在材质页比较透明度。", colors = colors, backdrop = modalBackdrop)
    } else if (prompt == "表格" || prompt == "样张") {
        LiquidSheet(onDismissRequest = { prompt = "" }, backdrop = modalBackdrop, title = prompt, tint = tint, colors = colors) {
            LiquidFormSection(colors = colors) {
                when (prompt) {
                    "表格" -> for ((title, kind) in listOf("名称" to "类型", "路线手记" to "文件夹", "用纸.csv" to "文档")) {
                        row {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Label(title, 16, colors.ink, Modifier.weight(2f)); Label(kind, 16, colors.secondary, Modifier.weight(1f))
                            }
                        }
                    }
                    "样张" -> repeat(24) {
                        row { Label("样张 ${it + 1}", 17, colors.ink) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuMaterialSample(backdrop: Backdrop, colors: LiquidListColors) {
    var choice by rememberSaveable { mutableStateOf("列表") }
    val menu = rememberLiquidMenuState { choice = it.key }
    val sample = rememberLayerBackdrop()
    val items = views.map { LiquidMenuItem(it, it, checked = it == choice) }
    LiquidAnchoredMenuHost(menu, backdrop, items,
        Modifier.fillMaxWidth().height(212.dp).clip(RoundedCornerShape(18.dp))) {
        Row(Modifier.matchParentSize().layerBackdrop(sample)) {
            Spacer(Modifier.weight(1f).fillMaxHeight().background(Color(0xffff9020)))
            Spacer(Modifier.width(12.dp).fillMaxHeight().background(Color.White))
            Spacer(Modifier.weight(1f).fillMaxHeight().background(Color(0xff385780)))
        }
        LiquidToolbarGroup(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(212.dp, 44.dp), backdrop = sample) {
            LiquidToolbarButton("材质菜单", menu::show, Modifier.fillMaxSize().liquidMenuTrigger(menu)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("材质菜单", 16, colors.ink)
                    LiquidIcon(LiquidSymbol.More, colors.ink)
                }
            }
        }
    }
}

@Composable
private fun GalleryPresentations(colors: LiquidListColors, backdrop: Backdrop, modalBackdrop: Backdrop) {
    var choice by rememberSaveable { mutableStateOf("列表") }
    var archive by rememberSaveable { mutableStateOf("") }
    var longMenu by remember { mutableStateOf(false) }
    var overlay by rememberSaveable { mutableStateOf("") }
    val glass = rememberLiquidMenuState { choice = it.key }
    val plain = rememberLiquidMenuState { if (longMenu) archive = it.title else choice = it.key }
    val context = rememberLiquidMenuState { choice = it.key }
    val items = views.map { LiquidMenuItem(it, it, checked = choice == it) }
    val popoverBounds = remember { mutableStateOf<Rect?>(null) }
    val archives = remember {
        fun entries(name: String, count: Int) = (1..count).map {
            val title = "$name " + it.toString().padStart(2, '0')
            LiquidMenuItem(title, title)
        }
        listOf(LiquidMenuItem("earlier", "往期样张", children = entries("往期", 72))) + entries("样张", 64)
    }
    val tint = accent()
    val preview: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiquidIcon(LiquidSymbol.Document, tint); Label("长按项目", 17, colors.ink)
        }
    }
    LiquidListContextMenuHost(context, backdrop, items, Modifier.fillMaxSize(), colors) {
        LiquidAnchoredMenuHost(glass, backdrop, items, Modifier.fillMaxSize()) {
            LiquidAnchoredMenuHost(plain, backdrop, if (longMenu) archives else items, Modifier.fillMaxSize(), LiquidMenuPresentation.Popup) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LiquidFormSection("菜单", colors = colors) {
                        row {
                            LiquidToolbarGroup(Modifier.size(116.dp, 44.dp), backdrop = backdrop) {
                                LiquidToolbarButton("按钮菜单", { glass.show() }, Modifier.fillMaxSize().liquidMenuTrigger(glass)) { Label("按钮菜单", 16, tint) }
                            }
                        }
                        row {
                            Box(Modifier.liquidBorderlessMenuTrigger(plain) { longMenu = false }.heightIn(min = 44.dp),
                                contentAlignment = Alignment.CenterStart) { Label("无边框菜单", 16, tint) }
                        }
                        row { Box(Modifier.fillMaxWidth().liquidListContextMenu(context) {}) { preview() } }
                        row {
                            Box(Modifier.liquidBorderlessMenuTrigger(plain) { longMenu = true }.heightIn(min = 44.dp),
                                contentAlignment = Alignment.CenterStart) { Label("长菜单", 16, tint) }
                        }
                        if (archive.isNotEmpty()) row { Label(archive, 16, colors.secondary) }
                    }
                    LiquidFormSection("呈现", colors = colors) {
                        for (name in listOf("提示框", "双操作提示", "三操作提示", "输入提示")) {
                            navigationRow(name, { overlay = name })
                        }
                        navigationRow("操作列表", { overlay = "操作列表" })
                        navigationRow("半屏面板", { overlay = "半屏面板" })
                        row(onClick = { overlay = "浮层" }) {
                            Row(
                                Modifier.fillMaxWidth().liquidPopoverAnchor(popoverBounds).heightIn(min = 28.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Label("浮层", 17, colors.ink, Modifier.weight(1f))
                                LiquidIcon(LiquidSymbol.Forward, colors.secondary.copy(alpha = .85f), Modifier.size(14.dp))
                            }
                        }
                        navigationRow("标签页", { overlay = "标签页" })
                        row { Label("显示方式   $choice", 16, colors.secondary) }
                    }
                }
            }
        }
    }
    val dismiss = { overlay = "" }
    val cancel = LiquidAlertAction("取消", LiquidAlertRole.Cancel, onClick = dismiss)
    var sampleName by rememberSaveable { mutableStateOf("") }
    when (overlay) {
        "提示框" -> LiquidAlert("折页参照", listOf(LiquidAlertAction("完成", onClick = dismiss)), dismiss, tint,
            message = "已读取样张。", colors = colors, backdrop = modalBackdrop)
        "双操作提示" -> LiquidAlert("移除样张？", listOf(cancel,
            LiquidAlertAction("移除", LiquidAlertRole.Destructive) { archive = "已移除样张"; dismiss() }),
            dismiss, tint, message = "此操作仅作用于样张。", colors = colors, backdrop = modalBackdrop)
        "三操作提示" -> LiquidAlert("保存更改？", listOf(
            LiquidAlertAction("保存") { archive = "已保存更改"; dismiss() },
            LiquidAlertAction("放弃更改", LiquidAlertRole.Destructive) { archive = "已放弃更改"; dismiss() }, cancel),
            dismiss, tint, colors = colors, backdrop = modalBackdrop)
        "输入提示" -> {
            var draft by rememberSaveable { mutableStateOf(sampleName) }
            val save = { sampleName = draft; archive = draft; dismiss() }
            LiquidAlert("重命名", listOf(cancel, LiquidAlertAction("保存", LiquidAlertRole.Default, draft.isNotBlank(), save)),
                dismiss, tint, colors = colors, backdrop = modalBackdrop,
                field = { LiquidAlertTextField(draft, { draft = it }, "名称", tint, save, colors) })
        }
        "操作列表" -> LiquidActionSheet(
            title = "显示方式",
            actions = listOf("列表", "网格").map { option ->
                LiquidAlertAction(option, if (option == choice) LiquidAlertRole.Primary else LiquidAlertRole.Default) {
                    choice = option
                }
            },
            onDismissRequest = dismiss,
            tint = tint,
            backdrop = modalBackdrop,
            colors = colors,
        )
        "半屏面板" -> {
            var name by rememberSaveable { mutableStateOf("") }
            LiquidSheet(
                onDismissRequest = dismiss,
                backdrop = modalBackdrop,
                title = "文件信息",
                tint = tint,
                detents = listOf(LiquidSheetDetent.Medium, LiquidSheetDetent.Large),
                colors = colors,
            ) {
                LiquidFormSection(colors = colors) {
                    row { LiquidTextField(name, { name = it }, "名称", tint = tint, colors = colors) }
                    row { LiquidLabeledContent("展示方式", choice, colors = colors) }
                }
                LiquidFormSection("通用", colors = colors) {
                    row { LiquidLabeledContent("种类", "PDF 文稿", colors = colors) }
                    row { LiquidLabeledContent("大小", "4.8 MB", colors = colors) }
                    row { LiquidLabeledContent("位置", "iCloud 云盘 · 印刷样张", colors = colors) }
                    row { LiquidLabeledContent("创建时间", "2026年11月18日 09:41", colors = colors) }
                    row { LiquidLabeledContent("修改时间", "2026年11月24日 14:20", colors = colors) }
                    row { LiquidLabeledContent("颜色描述文件", "Display P3", colors = colors) }
                }
            }
        }
        "浮层" -> LiquidPopover(
            onDismissRequest = dismiss,
            anchorBoundsInRoot = popoverBounds.value,
            backdrop = modalBackdrop,
            colors = colors,
        ) {
            Label("馆藏索引", 17, colors.ink, bold = true)
            Label("支持随锚点自动上下对齐并带实时液态玻璃折射。", 14, colors.secondary)
        }
        "标签页" -> Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            SyncLiquidDialogSystemBars(liquidGlassAppearance().dark)
            var tab by rememberSaveable { mutableStateOf(FilesTab.Browse) }
            var showBands by rememberSaveable { mutableStateOf(false) }
            var minimized by rememberSaveable { mutableStateOf(false) }
            var showSearchOrb by rememberSaveable { mutableStateOf(true) }
            var searchHits by rememberSaveable { mutableIntStateOf(0) }
            val scene = rememberLayerBackdrop()
            val title = when (tab) {
                FilesTab.RecentFiles -> "最近打开"
                FilesTab.PinnedFiles -> "固定"
                FilesTab.Browse -> "本机文库"
            }
            val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val galleryTabs = remember {
                listOf(
                    LiquidTab(FilesTab.RecentFiles, "最近", LiquidSymbol.Clock),
                    LiquidTab(FilesTab.PinnedFiles, "固定", LiquidSymbol.Bookmark),
                    LiquidTab(FilesTab.Browse, "浏览", LiquidSymbol.FilledFolder),
                )
            }
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().layerBackdrop(scene).background(colors.background)) {
                    if (showBands) Row(Modifier.fillMaxWidth().height(180.dp + navInsetBottom).align(Alignment.BottomCenter)) {
                        Spacer(Modifier.weight(1f).fillMaxHeight().background(Color(0xff233756)))
                        Spacer(Modifier.width(92.dp).fillMaxHeight().background(Color(0xff63c5b7)))
                        Spacer(Modifier.weight(1f).fillMaxHeight().background(Color(0xffdc8b4d)))
                    }
                }
                LiquidToolbarGroup(
                    Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(16.dp).height(44.dp),
                    backdrop = scene,
                ) {
                    LiquidToolbarButton("完成", dismiss, Modifier.padding(horizontal = 16.dp)) {
                        Label("完成", 16, colors.ink, bold = true)
                    }
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Label(if (searchHits > 0) "$title · 搜索 $searchHits" else title, 20, colors.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("对照色带", 17, colors.ink)
                        LiquidToggle({ showBands }, { showBands = it }, scene,
                            Modifier.semantics { contentDescription = "对照色带" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("独立搜索球", 17, colors.ink)
                        LiquidToggle({ showSearchOrb }, { showSearchOrb = it }, scene,
                            Modifier.semantics { contentDescription = "独立搜索球" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("最小化底栏", 17, colors.ink)
                        LiquidToggle({ minimized }, { minimized = it }, scene,
                            Modifier.semantics { contentDescription = "最小化底栏" })
                    }
                }
                LiquidTabBar(
                    tabs = galleryTabs,
                    selected = tab,
                    onSelect = { tab = it },
                    backdrop = scene,
                    tint = tint,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 21.dp, vertical = 10.dp),
                    minimized = minimized,
                    onExpandFromMinimized = { minimized = false },
                    searchAction = if (showSearchOrb) { { searchHits++ } } else null,
                )
            }
        }
    }
}
