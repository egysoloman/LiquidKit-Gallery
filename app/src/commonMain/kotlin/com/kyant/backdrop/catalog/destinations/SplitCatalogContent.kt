package com.kyant.backdrop.catalog.destinations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.kyant.backdrop.catalog.utils.BackHandler
import com.kyant.backdrop.components.*
import kotlinx.coroutines.launch

private val collections = listOf("全部色样", "冷色", "暖色", "已收藏")
private val coolStudies = studies.take(2) + listOf(
    ColorStudy("雨青", "#6DA2A2", Color(0xFF6DA2A2)), ColorStudy("靛蓝", "#536699", Color(0xFF536699)),
    ColorStudy("松针", "#477765", Color(0xFF477765)), ColorStudy("溪石", "#94A9AF", Color(0xFF94A9AF)),
    ColorStudy("霜紫", "#AEA4CA", Color(0xFFAEA4CA)), ColorStudy("苔绿", "#718559", Color(0xFF718559)),
    ColorStudy("夜潮", "#414F72", Color(0xFF414F72)),
)
private val warmStudies = studies.drop(2) + listOf(
    ColorStudy("桂花", "#DFBC69", Color(0xFFDFBC69)), ColorStudy("桃枝", "#DA9991", Color(0xFFDA9991)),
    ColorStudy("可可", "#94715E", Color(0xFF94715E)), ColorStudy("麦穗", "#CBB682", Color(0xFFCBB682)),
    ColorStudy("玫砂", "#BA878B", Color(0xFFBA878B)), ColorStudy("杏仁", "#E1C6AF", Color(0xFFE1C6AF)),
    ColorStudy("晚霞", "#CF7868", Color(0xFFCF7868)),
)
private val archiveStudies = coolStudies + warmStudies

@Composable
fun SplitCatalogContent(onExit: () -> Unit) {
    val split = rememberLiquidNavigationSplitState()
    val colors = liquidListColors()
    var collection by rememberSaveable { mutableStateOf(collections.first()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var archived by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var contextKey by rememberSaveable { mutableStateOf<String?>(null) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var sidebarVisibility by rememberSaveable { mutableStateOf(LiquidSplitVisibility.Automatic) }
    val sidebarScroll = rememberLazyListState()
    var firstRowOnSwitch by remember { mutableIntStateOf(0) }
    val contentScroll = key(collection, grid) { rememberLazyListState(initialFirstVisibleItemIndex = firstRowOnSwitch) }
    SideEffect { firstRowOnSwitch = 0 }
    val detailScroll = key(selected) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    val swatch = archiveStudies.firstOrNull { it.hex == selected }
    val contextStudy = archiveStudies.firstOrNull { it.hex == contextKey }
    fun collectionEntries(name: String) = when (name) {
        "冷色" -> coolStudies
        "暖色" -> warmStudies
        "已收藏" -> archiveStudies.filter { it.hex in saved }
        else -> archiveStudies
    }.filterNot { it.hex in archived }
    fun visibleEntries(name: String) = collectionEntries(name).let { if (reversed) it.asReversed() else it }
    val visible = visibleEntries(collection)
    fun switchLayout() {
        val firstRow = (contentScroll.firstVisibleItemIndex - 1).coerceAtLeast(0)
        val index = if (grid) firstRow * 2 else firstRow / 2
        val target = if (contentScroll.firstVisibleItemIndex == 0) 0 else 1 + index
        firstRowOnSwitch = target
        grid = !grid
    }
    fun toggleSaved(study: ColorStudy) { saved = if (study.hex in saved) saved - study.hex else saved + study.hex }
    fun archive(study: ColorStudy) {
        archived = (archived + study.hex).distinct()
        if (selected == study.hex) selected = null
    }
    fun open(study: ColorStudy) { selected = study.hex; split.show(LiquidSplitColumn.Detail) }
    fun title(column: LiquidSplitColumn) = when (column) {
        LiquidSplitColumn.Sidebar -> "色样档案"
        LiquidSplitColumn.Content -> collection
        LiquidSplitColumn.Detail -> swatch?.name ?: "色样详情"
    }
    val context = rememberLiquidMenuState { item -> contextStudy?.let { study ->
        when (item.key) { "save" -> toggleSaved(study); "open" -> open(study); "archive" -> archive(study) }
    } }
    val contextItems = listOf(LiquidMenuItem("open", "查看色值", LiquidSymbol.Forward),
        LiquidMenuItem("save", if (contextKey in saved) "取消收藏" else "收藏色样", LiquidSymbol.Bookmark),
        LiquidMenuItem("archive", "归档色样", LiquidSymbol.Download, startsSection = true))
    fun backOne() { if (split.back() && split.column == LiquidSplitColumn.Sidebar) sidebarVisibility = LiquidSplitVisibility.Automatic }
    val history = rememberLiquidMenuState {
        val column = LiquidSplitColumn.valueOf(it.key)
        if (column == LiquidSplitColumn.Sidebar) sidebarVisibility = LiquidSplitVisibility.Automatic
        split.show(column)
    }
    val ancestors = split.ancestors.map { LiquidMenuItem(it.name, title(it)) }
    BackHandler(split.canGoBack || history.expanded || context.expanded) {
        when { context.expanded -> context.back(); history.expanded -> history.back(); else -> backOne() }
    }
    val backdrop = rememberSolidColorBackdrop(colors.background)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = split.layoutFor(maxWidth, sidebarVisibility)
        val hasSidebar = LiquidSplitColumn.Sidebar in layout.columns
        fun directory() {
            sidebarVisibility = if (hasSidebar) LiquidSplitVisibility.HideSidebar else LiquidSplitVisibility.ShowSidebar
        }
        val activeScroll = when (layout.focusedColumn) {
            LiquidSplitColumn.Sidebar -> sidebarScroll
            LiquidSplitColumn.Content -> contentScroll
            LiquidSplitColumn.Detail -> detailScroll
        }
        Spacer(Modifier.matchParentSize().background(colors.background))
        LiquidListContextMenuHost(context, backdrop, contextItems, Modifier.fillMaxSize(), colors,
            preview = { if (contextStudy != null) {
                if (grid) GridSwatch(contextStudy, colors, Modifier.fillMaxSize())
                else SwatchCell(contextStudy, contextKey in saved, colors = colors)
            } }) {
            LiquidNavigationHistoryHost(history, backdrop, ancestors, Modifier.fillMaxSize()) {
                LiquidNavigationScaffold(title(layout.focusedColumn), backdrop, Modifier.fillMaxSize(),
                    scrollState = activeScroll, colors = colors,
                    contentWindowInsets = LiquidSafeAreaDefaults.windowInsets,
                    leading = { glass ->
                        LiquidNavigationControls(history.takeIf { split.canGoBack }, glass, if (split.canGoBack) ::backOne else onExit,
                            if (layout.supportsSidebar) ::directory else null, if (split.canGoBack) "返回上一级" else "返回示例目录",
                            if (hasSidebar) "隐藏目录" else "显示目录")
                    }, trailing = { glass ->
                        val tools = when (layout.focusedColumn) {
                            LiquidSplitColumn.Sidebar -> emptyList()
                            LiquidSplitColumn.Content -> buildList {
                                add(LiquidToolbarItem("layout", if (grid) "切换列表" else "切换网格", if (grid) LiquidSymbol.List else LiquidSymbol.Grid))
                                if (!grid) add(LiquidToolbarItem("order", if (reversed) "恢复原序" else "反向排序", if (reversed) LiquidSymbol.Descending else LiquidSymbol.Ascending))
                            }
                            LiquidSplitColumn.Detail -> if (swatch == null) emptyList() else listOf(LiquidToolbarItem("save",
                                if (swatch.hex in saved) "取消收藏" else "收藏色样", if (swatch.hex in saved) LiquidSymbol.Saved else LiquidSymbol.Bookmark))
                        }
                        LiquidToolbar(glass, tools, { item -> when (item.key) {
                            "layout" -> switchLayout(); "order" -> reversed = !reversed
                            "save" -> swatch?.let(::toggleSaved)
                        } }) { LiquidIcon(it) }
                    }, bottomBar = { glass ->
                        val index = visible.indexOf(swatch)
                        val tools = when (layout.focusedColumn) {
                            LiquidSplitColumn.Detail -> listOf(
                                LiquidToolbarItem("previous", "上一色样", LiquidSymbol.Back, index > 0),
                                LiquidToolbarItem("next", "下一色样", LiquidSymbol.Forward, index >= 0 && index < visible.lastIndex))
                            else -> buildList {
                                add(LiquidToolbarItem("favorites", "浏览收藏", LiquidSymbol.Bookmark))
                                if (layout.focusedColumn == LiquidSplitColumn.Content) add(LiquidToolbarItem("top", "回到顶部", LiquidSymbol.Ascending))
                                if (archived.isNotEmpty()) add(LiquidToolbarItem("restore", "恢复上次归档", LiquidSymbol.Download))
                            }
                        }
                        LiquidToolbar(glass, tools, { item -> when (item.key) {
                            "favorites" -> { collection = "已收藏"; selected = null; split.show(LiquidSplitColumn.Content) }
                            "top" -> scope.launch { activeScroll.animateScrollToItem(0) }
                            "restore" -> archived = archived.dropLast(1)
                            "previous" -> visible.getOrNull(index - 1)?.let(::open)
                            "next" -> visible.getOrNull(index + 1)?.let(::open)
                        } }) { LiquidIcon(it) }
                    }) { insets ->
                    val padding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 5.dp,
                        bottom = insets.calculateBottomPadding() + 8.dp)
                    LiquidNavigationSplitView(split, Modifier.fillMaxSize(),
                        visibility = sidebarVisibility,
                        dividerColor = colors.separator,
                        sidebar = {
                            LiquidList(Modifier.fillMaxSize(), sidebarScroll, colors, padding) {
                                liquidListSection("collections", collections, { it }, "色系") { name ->
                                    LiquidListCell(name, selected = name == collection, onClick = {
                                        if (name != collection) selected = null
                                        collection = name; split.show(LiquidSplitColumn.Content)
                                    }, leading = { LiquidIcon(if (name == "已收藏") LiquidSymbol.Bookmark else LiquidSymbol.Grid) },
                                        trailing = { LiquidIcon(LiquidSymbol.Forward, colors.secondary, Modifier.size(15.dp)) })
                                }
                            }
                        }, content = {
                            LiquidContentReplacement(collection to grid, Modifier.fillMaxSize()) { (shownCollection, shownGrid) ->
                                val shownScroll = remember(shownCollection, shownGrid) { contentScroll }
                                val shownRows = visibleEntries(shownCollection)
                                LiquidList(Modifier.fillMaxSize(), shownScroll, colors, padding) {
                                    if (shownGrid) {
                                        item { BasicText("色卡", Modifier.padding(vertical = 12.dp), style = TextStyle(colors.secondary, 14.sp)) }
                                        items(shownRows.chunked(2), key = { "grid." + it.first().hex }) { pair ->
                                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                pair.forEach { study ->
                                                    GridSwatch(study, colors, Modifier.weight(1f)
                                                        .liquidListContextMenu(context, SwatchShape) { contextKey = study.hex }
                                                        .clickable(interactionSource = null, indication = null) { open(study) })
                                                }
                                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    } else liquidListSection("swatches", shownRows, { it.hex }, "色卡") { study ->
                                        val marked = study.hex in saved
                                        LiquidSwipeActions(study.hex,
                                            leading = listOf(LiquidSwipeAction(if (marked) "取消收藏" else "收藏", LiquidSymbol.Bookmark,
                                                Color(0xff447864)) { toggleSaved(study) }),
                                            trailing = listOf(LiquidSwipeAction("归档", LiquidSymbol.Download, Color(0xffa14d42)) { archive(study) }),
                                        ) {
                                            SwatchCell(study, marked, Modifier.liquidListContextMenu(context) { contextKey = study.hex },
                                                selected = selected == study.hex, colors = colors, onClick = { open(study) })
                                        }
                                    }
                                    if (shownRows.isEmpty()) item { BasicText(if (shownCollection == "已收藏") "还没有收藏的色样" else "没有色样", Modifier.padding(12.dp), style = TextStyle(colors.secondary, 16.sp)) }
                                }
                            }
                        }, detail = {
                            LiquidContentReplacement(swatch, Modifier.fillMaxSize()) { shown ->
                                val shownScroll = remember(shown) { detailScroll }
                                LiquidList(Modifier.fillMaxSize(), shownScroll, colors, padding) {
                                item { BasicText("色值", Modifier.padding(vertical = 12.dp), style = TextStyle(colors.secondary, 14.sp, FontWeight.SemiBold)) }
                                if (shown == null) item { BasicText("请选择色样", style = TextStyle(colors.ink, 16.sp)) }
                                else item {
                                    Spacer(Modifier.fillMaxWidth().height(156.dp).background(shown.color, RoundedCornerShape(16.dp)))
                                    LiquidListCell(shown.name, subtitle = shown.hex)
                                }
                                }
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun SwatchCell(study: ColorStudy, saved: Boolean, modifier: Modifier = Modifier, selected: Boolean = false,
    colors: LiquidListColors, onClick: (() -> Unit)? = null) {
    LiquidListCell(study.name, modifier, subtitle = study.hex, selected = selected, onClick = onClick,
        leading = { Spacer(Modifier.size(26.dp).background(study.color, RoundedCornerShape(7.dp))) },
        trailing = { LiquidIcon(if (saved) LiquidSymbol.Saved else LiquidSymbol.Forward, colors.secondary, Modifier.size(16.dp)) })
}

private val SwatchShape = RoundedCornerShape(14.dp)

@Composable
private fun GridSwatch(study: ColorStudy, colors: LiquidListColors, modifier: Modifier = Modifier) {
    Column(modifier.background(colors.surface, SwatchShape).padding(10.dp)) {
        Spacer(Modifier.fillMaxWidth().height(70.dp).background(study.color, RoundedCornerShape(9.dp)))
        BasicText(study.name, Modifier.padding(top = 6.dp), style = TextStyle(colors.ink, 16.sp))
    }
}
