package com.kyant.foldfiles

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import com.kyant.backdrop.components.*
import kotlin.math.roundToInt

class FilesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge()
        val model = ViewModelProvider(this)[FilesModel::class.java]
        setContent {
            LiquidGlassTheme(opacity=model.opacity) {
                LiquidSystemBarAppearance(dark = liquidGlassAppearance().dark)
                FilesApp(model) { entry, share ->
                try {
                    val uri = model.store.shareUri(entry)
                    val intent = if (share) Intent(Intent.ACTION_SEND).setType(entry.mime).putExtra(Intent.EXTRA_STREAM,uri)
                        else Intent(Intent.ACTION_VIEW).setDataAndType(uri,entry.mime)
                    intent.clipData = ClipData.newRawUri(entry.name,uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(if (share) Intent.createChooser(intent,entry.name) else intent)
                } catch (e: Exception) { model.message = if (e is android.content.ActivityNotFoundException) "没有可打开此格式的应用" else e.localizedMessage }
            } }
        }
    }
}

private sealed interface Sheet {
    data object Controls : Sheet
    data object Appearance : Sheet
    data class Create(val parent: Entry) : Sheet
    data class Rename(val parent: Entry, val file: Entry) : Sheet
    data class Delete(val files: List<Entry>) : Sheet
}

@Composable
private fun FilesApp(model: FilesModel, external: (Entry,Boolean)->Unit) {
    val colors = liquidListColors()
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color.Black else Color.White
    val filesColors = colors.copy(background=canvas)
    val sideColor = if (dark) Color(0xff1c2025) else Color(0xffedf1f2)
    val backdrop = rememberSolidColorBackdrop(canvas)
    val split = rememberLiquidNavigationSplitState(LiquidSplitColumn.Detail)
    var visibility by rememberSaveable { mutableStateOf(LiquidSplitVisibility.Automatic) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var held by remember { mutableStateOf<Entry?>(null) }
    var heldParent by remember { mutableStateOf<Entry?>(null) }
    val ctx = LocalContext.current
    val voice = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "搜索文件")
    }
    val hasVoiceRecognizer = remember(ctx) { voice.resolveActivity(ctx.packageManager) != null }
    val message = model.message
    LaunchedEffect(message) { if(message != null) { Toast.makeText(ctx,message,Toast.LENGTH_LONG).show(); model.message=null } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data?.data != null) model.connect(data.data!!,data.flags)
    }
    val connect = {
        picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
    }
    val open: (Entry)->Unit = { entry -> model.open(entry); split.show(LiquidSplitColumn.Detail) }
    val history = rememberLiquidMenuState { it.key.toIntOrNull()?.let(model::jump) }
    val historyItems = model.ancestors.map { LiquidMenuItem(it.index.toString(),it.value.name,LiquidSymbol.Folder) }
    val options = rememberLiquidMenuState { item ->
        when {
            item.key.startsWith("layout.") -> model.changeLayout(FileLayout.valueOf(item.key.substringAfter('.')))
            item.key.startsWith("sort.") -> model.changeOrder(FileOrder.valueOf(item.key.substringAfter('.')))
            item.key == "controls" -> sheet = Sheet.Controls
            item.key == "opacity" -> sheet = Sheet.Appearance
            item.key == "connect" -> connect()
            item.key == "share" -> model.page?.entry?.let { external(it,true) }
        }
    }
    val sort = rememberLiquidMenuState { item -> model.changeOrder(FileOrder.valueOf(item.key)) }
    val sortItems = FileOrder.entries.map { LiquidMenuItem(it.name, it.title, checked = model.order == it) }
    val page = model.page
    val optionsItems = buildList {
        if (page?.entry?.folder == true && page.error == null) {
            add(LiquidMenuItem("display","显示方式",LiquidSymbol.Grid,children=FileLayout.entries.map {
                LiquidMenuItem("layout."+it.name,it.title,checked=it==model.layout) }))
            add(LiquidMenuItem("order","排序方式",LiquidSymbol.Ascending,children=FileOrder.entries.map {
                LiquidMenuItem("sort."+it.name,it.title,checked=it==model.order) }))
        } else if (page?.entry?.folder == false && page.error == null) add(LiquidMenuItem("share","分享文件",LiquidSymbol.Share))
        add(LiquidMenuItem("opacity","玻璃透明度",LiquidSymbol.Unchecked,startsSection=true))
        add(LiquidMenuItem("controls","组件目录",LiquidSymbol.Grid))
        add(LiquidMenuItem("connect","连接文件夹",LiquidSymbol.Link))
    }
    val actions = rememberLiquidMenuState { action -> held?.let { file ->
        when (action.key) {
            "open" -> open(file)
            "pin" -> model.pin(file)
            "share" -> external(file,true)
            "rename" -> heldParent?.let { sheet = Sheet.Rename(it,file) }
            "delete" -> sheet = Sheet.Delete(listOf(file))
        }
    } }
    val contextItems = buildList {
        add(LiquidMenuItem("open",if (held?.folder==true) "打开文件夹" else "预览",LiquidSymbol.Forward))
        if (held?.folder==true) add(LiquidMenuItem("pin",if(model.pins.any { it.id == held?.id }) "取消固定" else "固定到侧栏",LiquidSymbol.Bookmark))
        else add(LiquidMenuItem("share","分享",LiquidSymbol.Share))
        add(LiquidMenuItem("rename","重命名",LiquidSymbol.Rename,enabled=held?.canRename==true))
        add(LiquidMenuItem("delete","删除",LiquidSymbol.Trash,enabled=held?.canDelete==true,startsSection=true,destructive=true))
    }
    val tabBackdrop = rememberLayerBackdrop()
    val modalBackdrop = com.kyant.backdrop.backdrops.rememberCombinedBackdrop(backdrop, tabBackdrop)
    LiquidModalHost {
        BoxWithConstraints(Modifier.fillMaxSize().background(canvas).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        val wide = maxWidth >= 600.dp
        val showsSide = wide && visibility != LiquidSplitVisibility.HideSidebar
        val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val tabsVisible = !wide && keyboard == 0.dp
        val bottom = if (tabsVisible) fileTabInsets().asPaddingValues().calculateBottomPadding() + FileTabClearance + 66.dp
            else maxOf(keyboard, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        val hasTransientOverlay = sheet != null || actions.expanded || sort.expanded || options.expanded ||
            history.expanded || model.selecting || model.query.isNotEmpty()
        fun back() {
            when {
                sheet != null -> sheet = null
                actions.expanded -> actions.back()
                sort.expanded -> sort.back()
                options.expanded -> options.back()
                history.expanded -> history.back()
                model.selecting -> model.selectMode(false)
                model.query.isNotEmpty() -> model.query = ""
                model.cursor > 0 && (wide || split.column == LiquidSplitColumn.Detail) -> model.jump(model.cursor-1)
                !wide && split.column == LiquidSplitColumn.Detail -> split.show(LiquidSplitColumn.Sidebar)
            }
        }
        // Only intercept flat back for transient overlays/menus/search/selection; page stack and
        // compact split navigation handle predictive back interactively inside LiquidSwipeBackNavigation.
        BackHandler(hasTransientOverlay) { back() }
        val toggleSide = {
            if (wide) visibility = if (showsSide) LiquidSplitVisibility.HideSidebar else LiquidSplitVisibility.ShowSidebar
            else split.show(LiquidSplitColumn.Sidebar)
        }
        LiquidListContextMenuHost(actions, backdrop, contextItems, Modifier.fillMaxSize(), filesColors) {
            LiquidAnchoredMenuHost(sort,backdrop,sortItems,Modifier.fillMaxSize(), presentation = LiquidMenuPresentation.Popup) {
                LiquidAnchoredMenuHost(options,backdrop,optionsItems,Modifier.fillMaxSize()) {
                    LiquidNavigationHistoryHost(history,backdrop,historyItems,Modifier.fillMaxSize(), groupAccessory = LiquidSymbol.Forward) {
                        val browseGesture = sheet == null && !actions.expanded && !sort.expanded &&
                            !history.expanded && !options.expanded && !model.selecting && model.query.isEmpty() && keyboard == 0.dp
                        val shareTabScene = !wide && tabsVisible && sheet == null && split.column == LiquidSplitColumn.Detail && page != null
                        Box(Modifier.fillMaxSize().layerBackdrop(tabBackdrop, enabled = (tabsVisible && !shareTabScene) || sheet != null)) {
                        LiquidNavigationSplitView(split,Modifier.fillMaxSize(),visibility=visibility, swipeBackFromBlank = browseGesture && model.cursor == 0,
                            dividerColor=colors.separator.copy(alpha=.6f),sidebar={
                                Places(model,wide,bottom,sideColor,colors,open,{ connect() },toggleSide,{ sheet=Sheet.Appearance })
                            },detail={
                                if (page != null) BrowserPane(page,model,wide,bottom,!showsSide,filesColors,backdrop,toggleSide,
                                    history,options,sort,actions,{ entry, parent -> held=entry; heldParent=parent },open,connect,external,
                                    browseGesture, voice, !wide && hasVoiceRecognizer, { sheet=it },
                                    sharedScene = if (shareTabScene) tabBackdrop else null)
                            })
                        }
                        if (tabsVisible) {
                            val onTabSelect = remember(model, split) {
                                { next: FilesTab ->
                                    model.selectTab(next)
                                    split.show(LiquidSplitColumn.Detail)
                                }
                            }
                            FileTabBar(model.tab, onTabSelect, tabBackdrop, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
    sheet?.let { dialog -> ManagerDialog(dialog,model,colors,modalBackdrop) { sheet=null } }
    }
}

@Composable
private fun Places(model: FilesModel, wide: Boolean, bottom: Dp, sideColor: Color, colors: LiquidListColors, open: (Entry)->Unit,
    connect: ()->Unit, toggleSide: ()->Unit, appearance: ()->Unit) {
    val bg = if (wide) sideColor else colors.background
    val pane = rememberSolidColorBackdrop(bg)
    val selection = if (liquidGlassAppearance().dark) Color(0xff323a40) else Color(0xffdbe4e7)
    val scheme = colors.copy(background = bg, selected = selection)
    var pinsOpen by rememberSaveable { mutableStateOf(true) }
    var rootsOpen by rememberSaveable { mutableStateOf(true) }
    val menu = rememberLiquidMenuState { if (it.key == "connect") connect() else appearance() }
    val items = listOf(LiquidMenuItem("connect","连接文件夹",LiquidSymbol.Link),
        LiquidMenuItem("appearance","玻璃透明度",LiquidSymbol.Filter))
    BackHandler(menu.expanded) { menu.back() }
    val where = model.page?.entry?.id
    Box(Modifier.fillMaxSize().background(bg)) {
        LiquidAnchoredMenuHost(menu, pane, items, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wide) Spacer(Modifier.weight(1f)) else Label("浏览位置", 27, colors.ink, Modifier.weight(1f), bold = true)
                    LiquidToolbarGroup(backdrop = pane) {
                        LiquidToolbarButton("位置选项", {}, Modifier.size(44.dp).liquidMenuTrigger(menu)) { LiquidIcon(LiquidSymbol.More) }
                    }
                    if (wide) LiquidToolbarButton("收起侧栏", toggleSide, Modifier.size(44.dp), backdrop = pane) { LiquidIcon(LiquidSymbol.Sidebar) }
                }
                if (wide) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        LiquidSidebarRow(Recent.name, LiquidSymbol.Clock, accent(), where == Recent.id, { open(Recent) }, colors = scheme)
                        if (model.pins.isNotEmpty()) {
                            Spacer(Modifier.height(13.dp))
                            LiquidSidebarSectionHeader("固定", pinsOpen, { pinsOpen = !pinsOpen }, if (pinsOpen) "收起固定" else "展开固定", colors = scheme)
                            AnimatedVisibility(pinsOpen) {
                                Column { model.pins.forEach { entry -> key(entry.id) {
                                    LiquidSidebarRow(entry.name, LiquidSymbol.Bookmark, accent(), where == entry.id, { open(entry) }, colors = scheme)
                                } } }
                            }
                        }
                        Spacer(Modifier.height(13.dp))
                        LiquidSidebarSectionHeader("位置", rootsOpen, { rootsOpen = !rootsOpen }, if (rootsOpen) "收起位置" else "展开位置", colors = scheme)
                        AnimatedVisibility(rootsOpen) { Column {
                            (listOf(model.store.library) + model.roots).forEach { entry -> key(entry.id) {
                                LiquidSidebarRow(entry.name, if (entry == model.store.library) LiquidSymbol.Drive else LiquidSymbol.Link,
                                    accent(), where == entry.id, { open(entry) }, colors = scheme)
                            } }
                        } }
                        Spacer(Modifier.height(bottom + 12.dp))
                    }
                } else {
                    LiquidList(LiquidListStyle.Grouped, Modifier.fillMaxSize(), colors = scheme, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom + 12.dp)) {
                        liquidListSection("places", listOf(Recent), { it.id }) { entry ->
                            LiquidListCell(entry.name, onClick = { open(entry) }, leading = { LiquidIcon(LiquidSymbol.Clock,accent()) })
                        }
                        if (model.pins.isNotEmpty()) liquidListSection("pins", model.pins, { "pin." + it.id }, "固定") { entry ->
                            LiquidListCell(entry.name, onClick = { open(entry) }, leading = { LiquidIcon(LiquidSymbol.Bookmark,accent()) })
                        }
                        liquidListSection("locations", listOf(model.store.library) + model.roots, { it.id }, "位置") { entry ->
                            LiquidListCell(entry.name, onClick = { open(entry) }, leading = {
                                LiquidIcon(if (entry == model.store.library) LiquidSymbol.Drive else LiquidSymbol.Link,accent())
                            }, trailing = { LiquidIcon(LiquidSymbol.Forward, colors.secondary, Modifier.size(14.dp)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserPane(page: FilePage, model: FilesModel, wide: Boolean, bottom: Dp, revealSidebar: Boolean, colors: LiquidListColors,
    backdrop: Backdrop, toggleSide: ()->Unit, history: LiquidMenuState, options: LiquidMenuState, sort: LiquidMenuState, context: LiquidMenuState,
    prepare: (Entry, Entry)->Unit, open: (Entry)->Unit, reconnect: ()->Unit, external: (Entry,Boolean)->Unit, browseGesture: Boolean,
    voice: Intent, canDictate: Boolean, show: (Sheet)->Unit, sharedScene: LayerBackdrop? = null) {
    val leading: @Composable (Backdrop)->Unit = { glass ->
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if (wide && revealSidebar) LiquidToolbarButton("显示位置",toggleSide,Modifier.size(44.dp),backdrop=glass) {
                LiquidIcon(LiquidSymbol.Sidebar)
            }
            if (!wide && (page.depth > 0 || revealSidebar)) {
                val hasParent = page.depth > 0
                LiquidNavigationBackButton(if (hasParent) history else null, glass,
                    if (hasParent) "上一个位置" else "显示位置", {
                        if (hasParent) model.jump(model.cursor - 1) else toggleSide()
                    })
            } else if (wide) {
                LiquidToolbarGroup(backdrop=glass) {
                    val navigation = if (wide) listOf(LiquidSymbol.Back, LiquidSymbol.Forward) else listOf(LiquidSymbol.Back)
                    LiquidToolbarSlots(navigation, LiquidToolbarSpacing.Navigation) { index, area ->
                        if (index == 0) {
                            val back = { model.jump(model.cursor - 1) }
                            val trigger = if (model.cursor > 0) Modifier.liquidMenuTrigger(history, LiquidMenuActivation.LongPress, onShortTap = back) else Modifier
                            LiquidToolbarButton("上一个位置", back, area.then(trigger), enabled = model.cursor > 0) { LiquidIcon(LiquidSymbol.Back) }
                        } else LiquidToolbarButton("下一个位置", { model.jump(model.cursor + 1) }, area, enabled = model.canForward) { LiquidIcon(LiquidSymbol.Forward) }
                    }
                }
            }
        }
    }
    val trailing: @Composable (Backdrop)->Unit = { glass ->
        val showSelect = page.entry.folder && page.error == null
        LiquidToolbarPair(glass, spacing = 12.dp, fusionDistance = 6.dp, firstVisible = showSelect, first = {
            LiquidToolbarGroup(Modifier.padding(vertical = 4.dp), backdrop=glass) {
                LiquidToolbarButton(if (model.selecting) "完成选择" else "选择文件", { model.selectMode(!model.selecting) },
                    Modifier.height(44.dp).widthIn(min = 66.dp), enabled = showSelect) {
                    LiquidContentReplacement(model.selecting, alignment = Alignment.Center) { selected ->
                        Label(if (selected) "完成" else "选择", 15, LocalLiquidContentColor.current, Modifier.padding(horizontal = 10.dp), bold = true)
                    }
                }
            }
        }, second = {
            val action = if (model.selecting) LiquidSymbol.Trash else LiquidSymbol.NewFolder
            val viewSymbol = when (model.layout) {
                FileLayout.List -> LiquidSymbol.List
                FileLayout.Grid -> LiquidSymbol.Grid
                FileLayout.Cards -> LiquidSymbol.More
            }
            val symbols = if (showSelect) listOf(action, viewSymbol) else listOf(LiquidSymbol.More)
            LiquidToolbarReplacement(symbols, glass, { LiquidToolbarDefaults.width(it) },
                Modifier.padding(vertical = 4.dp), contentKey = { it.map { sym -> sym.name } }) { displayed, current ->
                LiquidToolbarSlots(displayed) { index, area ->
                    val symbol = displayed[index]
                    if (!current) {
                        Box(area, contentAlignment = Alignment.Center) { LiquidIcon(symbol) }
                    } else if (displayed.size > 1 && index == 0) {
                        val files = model.selectedFiles
                        val enabled = if (model.selecting) files.isNotEmpty() && files.all { it.canDelete } else page.entry.canCreate
                        LiquidToolbarButton(if (model.selecting) "删除所选" else "新建文件夹", {
                            if (model.selecting) show(Sheet.Delete(files)) else show(Sheet.Create(page.entry))
                        }, area, enabled = enabled && !model.busy) {
                            LiquidContentReplacement(symbol, alignment = Alignment.Center) { LiquidIcon(it) }
                        }
                    } else LiquidToolbarButton("显示选项", {}, area.liquidMenuTrigger(options)) {
                        LiquidContentReplacement(symbol, alignment = Alignment.Center) { LiquidIcon(it) }
                    }
                }
            }
        })
    }
    val ownScene = rememberLayerBackdrop()
    val scene = sharedScene ?: ownScene
    val pageStates = rememberSaveableStateHolder()
    val density = LocalDensity.current
    var leadingWidth by remember { mutableIntStateOf(0) }
    var trailingWidth by remember { mutableIntStateOf(0) }
    val activeTab = model.tab
    val visitedTabs = remember { linkedSetOf<FilesTab>() }
    visitedTabs.add(activeTab)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(scene)) {
            Layout(
                content = {
                    visitedTabs.forEach { slotTab ->
                        key(slotTab) {
                            val slotPage = model.pageFor(slotTab) ?: page.takeIf { slotTab == activeTab }
                            val slotActive = slotTab == activeTab
                            Box(Modifier.fillMaxSize()) {
                                if (slotPage != null) {
                                    LiquidSwipeBackNavigation(
                                        slotPage,
                                        model.previousPageFor(slotTab).takeIf { slotActive && browseGesture && model.cursorFor(slotTab) == slotPage.depth },
                                        { it.depth },
                                        { it.depth to it.entry.id },
                                        { parent ->
                                            if (model.tab == slotTab && model.cursor == parent.depth + 1 && model.previousPage?.entry?.id == parent.entry.id)
                                                model.jump(parent.depth)
                                        },
                                        Modifier.fillMaxSize(),
                                    ) { displayed ->
                                        pageStates.SaveableStateProvider("${slotTab.name}/${displayed.depth}/${displayed.entry.id}") {
                                            BrowserPage(
                                                displayed, model, wide, bottom, colors, backdrop, sort, context, prepare, open, reconnect, external,
                                                with(density) { leadingWidth.toDp() }, with(density) { trailingWidth.toDp() }, voice, canDictate,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val activeIndex = visitedTabs.indexOf(activeTab)
                val placeable = if (activeIndex in measurables.indices) measurables[activeIndex].measure(constraints) else null
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable?.place(0, 0)
                }
            }
        }
        // The large title and content slide together. Both glass action groups stay on the navigation bar.
        val height = maxOf(52.dp, with(density) { 22.sp.toDp() } + 16.dp)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .height(height).padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 0.dp)) {
            Box(Modifier.onSizeChanged { leadingWidth = it.width }) { LiquidNavigationBarStyle { leading(scene) } }
            Spacer(Modifier.weight(1f))
            Box(Modifier.onSizeChanged { trailingWidth = it.width }) { LiquidNavigationBarStyle { trailing(scene) } }
        }
    }
}

@Composable
private fun BrowserPage(page: FilePage, model: FilesModel, wide: Boolean, bottom: Dp, colors: LiquidListColors,
    backdrop: Backdrop, sort: LiquidMenuState, context: LiquidMenuState, prepare: (Entry, Entry)->Unit,
    open: (Entry)->Unit, reconnect: ()->Unit, external: (Entry,Boolean)->Unit, leadingWidth: Dp, trailingWidth: Dp,
    voice: Intent, canDictate: Boolean) {
    val listState = key(page.entry.id,model.layout) { rememberLazyListState() }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.let { model.query = it }
    }
    val view: @Composable (PaddingValues)->Unit = { padding ->
        val density = LocalDensity.current
        val topInset = padding.calculateTopPadding()
        val largeTitle = if (page.entry.folder) maxOf(60.dp, with(density) { 34.sp.toDp() } + 20.dp) else 0.dp
        val topInsetPx = with(density) { topInset.roundToPx() }
        val largeTitlePx = with(density) { largeTitle.roundToPx() }
        val sortThresholdPx = with(density) { (largeTitle + 44.dp).roundToPx() }
        val showSort by remember(listState, sortThresholdPx) {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < sortThresholdPx
            }
        }
        val listBackdrop = rememberLayerBackdrop()
        val hasSearch = page.entry.folder && page.error == null
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Box(Modifier.matchParentSize().layerBackdrop(listBackdrop, enabled = hasSearch).background(colors.background)) {
                LiquidViewTransition(model.layout, Modifier.fillMaxSize()) { layout ->
                    val ownScroll = remember(layout) { listState }
                    FileItems(page,model,layout,colors,ownScroll,context,prepare,open,
                        PaddingValues(start=if(layout==FileLayout.List) 4.dp else 16.dp,end=if(layout==FileLayout.List) 0.dp else 16.dp,
                            top=topInset + if (hasSearch) 98.dp else 4.dp,bottom=bottom + 12.dp),reconnect,external)
                }
            }
            Column(Modifier.fillMaxWidth().offset {
                val offset = if (listState.firstVisibleItemIndex > 0) largeTitlePx else listState.firstVisibleItemScrollOffset
                IntOffset(0, topInsetPx - minOf(largeTitlePx, offset))
            }) {
                if (hasSearch) {
                    val query = model.queryFor(page.entry.id)
                    LiquidSearchField(query, { model.setQuery(page.entry.id, it) }, "搜索", "清除搜索",
                        Modifier.zIndex(1f).padding(start = 18.dp, end = if (wide) 10.dp else 18.dp, top = 3.dp, bottom = 3.dp),
                        label = "搜索文件", colors = colors, cursorColor = accent(), backdrop = listBackdrop, cancelLabel = if (wide) null else "取消",
                        trailingAction = if (!canDictate) null else ({
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(50))
                                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                    try { dictation.launch(voice) }
                                    catch (_: android.content.ActivityNotFoundException) { model.message = "语音输入暂不可用" }
                                }.semantics { contentDescription = "语音搜索" }, contentAlignment = Alignment.Center) {
                                LiquidIcon(LiquidSymbol.Microphone, colors.secondary, Modifier.size(20.dp))
                            }
                        }))
                    if (showSort) Row(Modifier.fillMaxWidth().background(colors.background).padding(start = if (model.layout == FileLayout.List) 46.dp else 20.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.liquidBorderlessMenuTrigger(sort).heightIn(min = 44.dp).semantics {
                            role = Role.Button; contentDescription = "更改排序"
                        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LiquidContentReplacement(model.order.title) { Label(it, 13, accent(), bold = true) }
                            LiquidIcon(LiquidSymbol.ChevronDown, accent(), Modifier.size(12.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        val count = if (model.selecting) "已选 " + model.selected.size else
                            page.rows.count { it.name.contains(query.trim(), true) }.toString() + " 项"
                        LiquidContentReplacement(count) { Label(it, 13, colors.secondary) }
                    }
                }
            }
        }
    }
    LiquidNavigationScaffold(
        page.entry.name,
        backdrop,
        scrollState = listState,
        titleMode = if (page.entry.folder) LiquidNavigationTitleMode.Large else LiquidNavigationTitleMode.Inline,
        colors = colors,
        leading = { Spacer(Modifier.width(leadingWidth)) },
        trailing = { Spacer(Modifier.width(trailingWidth)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        content = view,
    )
}

@Composable
private fun ManagerDialog(sheet: Sheet, model: FilesModel, colors: LiquidListColors, backdrop: com.kyant.backdrop.Backdrop, close: ()->Unit) {
    var gallery by rememberSaveable(sheet.toString()) { mutableStateOf(false) }
    if (gallery) {
        FileComponentGallery(colors, model) { gallery = false }
        return
    }
    val tint = accent()
    when (sheet) {
        is Sheet.Create, is Sheet.Rename -> {
            var name by rememberSaveable(sheet.toString()) { mutableStateOf((sheet as? Sheet.Rename)?.file?.name.orEmpty()) }
            val submit = {
                if (name.isNotBlank()) {
                    if (sheet is Sheet.Create) model.newFolder(sheet.parent, name)
                    if (sheet is Sheet.Rename) model.rename(sheet.parent, sheet.file, name)
                    close()
                }
            }
            LiquidAlert(if (sheet is Sheet.Create) "新建文件夹" else "重命名",
                listOf(LiquidAlertAction("取消", LiquidAlertRole.Cancel, onClick = close),
                    LiquidAlertAction("保存", LiquidAlertRole.Primary, name.isNotBlank(), submit)), close, tint,
                colors = colors, backdrop = backdrop, field = { LiquidAlertTextField(name, { name = it }, "项目名称", tint, submit, colors) })
            return
        }
        is Sheet.Delete -> {
            LiquidAlert("删除 ${sheet.files.size} 个项目？",
                listOf(LiquidAlertAction("取消", LiquidAlertRole.Cancel, onClick = close),
                    LiquidAlertAction("确认删除", LiquidAlertRole.Destructive) { model.delete(sheet.files); close() }),
                close, tint, message = sheet.files.joinToString("、") { it.name } + "。文件夹内的内容也会删除。", colors = colors, backdrop = backdrop)
            return
        }
        else -> Unit
    }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest=close) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface).padding(22.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Label(if (sheet == Sheet.Controls) "组件目录" else "玻璃透明度",21,colors.ink,bold=true)
            when(sheet) {
                Sheet.Controls -> {
                    var preview by rememberSaveable { mutableStateOf(true) }
                    var progress by rememberSaveable { mutableFloatStateOf(.5f) }
                    val material = rememberLayerBackdrop()
                    Box(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.matchParentSize().layerBackdrop(material).background(colors.surface))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            LiquidActionButton("查看信息", { showInfo = true }, tint, style = LiquidActionStyle.Borderless)
                            LiquidActionButton("全部组件", { gallery = true }, accent(), style = LiquidActionStyle.Plain)
                            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Label("显示预览",17,colors.ink,Modifier.weight(1f))
                                LiquidToggle({ preview }, { preview = it }, material,
                                    Modifier.semantics { contentDescription = "显示预览" })
                            }
                            Label("进度", 15, colors.ink)
                            LiquidSlider({ progress }, { progress = it }, 0f..1f, .001f, material,
                                Modifier.semantics { contentDescription = "调整进度" })
                            AnimatedVisibility(preview) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    LiquidProgressIndicator(progress, accent(), Modifier.weight(1f).semantics { contentDescription = "当前进度" })
                                    Label((progress * 100).roundToInt().toString() + "%",14,colors.secondary)
                                }
                            }
                            LiquidActionButton("完成", close, accent(), style = LiquidActionStyle.Filled)
                        }
                    }
                }
                Sheet.Appearance -> {
                    val sample=rememberLayerBackdrop()
                    Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
                        Box(Modifier.fillMaxSize().layerBackdrop(sample).background(Brush.horizontalGradient(listOf(
                            if(isSystemInDarkTheme()) Color(0xff2e5251) else Color(0xffb6d8ce),
                            if(isSystemInDarkTheme()) Color(0xff344356) else Color(0xffe8d4b0)))),contentAlignment=Alignment.Center) {
                            Label("折页文库",33,colors.ink,bold=true)
                        }
                        LiquidToolbarGroup(Modifier.size(164.dp,46.dp),backdrop=sample) {
                            Row(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                                LiquidIcon(LiquidSymbol.Search,modifier=Modifier.size(18.dp))
                            }
                        }
                    }
                    LiquidOpacitySlider(model.opacity,model::changeOpacity,"玻璃不透明度",onValueChangeFinished=model::saveOpacity,showIcons=true,backdrop=sample)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Label("透明",13,colors.secondary); Label((model.opacity*100).roundToInt().toString()+"%",14,colors.ink,bold=true); Label("浓厚",13,colors.secondary)
                    }
                    LiquidActionButton("完成", { model.saveOpacity(); close() }, accent(), style = LiquidActionStyle.Filled)
                }
                else -> Unit
            }
        }
    }
    if (showInfo) {
        val dismiss = { showInfo = false }
        LiquidAlert("折页组件", listOf(LiquidAlertAction("完成", onClick = dismiss)), dismiss, tint, colors = colors, backdrop = backdrop)
    }
}
