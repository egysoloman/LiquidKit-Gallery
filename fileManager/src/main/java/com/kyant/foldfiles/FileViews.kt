package com.kyant.foldfiles

import android.text.format.Formatter
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.components.*
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun Label(text: String, size: Int, color: Color, modifier: Modifier = Modifier, bold: Boolean = false, lines: Int = 1) {
    BasicText(text, modifier, style = TextStyle(color, size.sp, if (bold) FontWeight.SemiBold else FontWeight.Normal),
        maxLines = lines, overflow = TextOverflow.Ellipsis)
}

internal val DarkAccent = Color(0xff75cbd8)
internal val FileTabClearance = 12.dp
private val FileTabs = listOf(
    LiquidTab(FilesTab.RecentFiles, "最近", LiquidSymbol.FilledClock),
    LiquidTab(FilesTab.PinnedFiles, "固定", LiquidSymbol.Saved),
    LiquidTab(FilesTab.Browse, "浏览", LiquidSymbol.FilledFolder),
)

@Composable
internal fun fileTabInsets() = LiquidSafeAreaDefaults.bottomBarInsets

@Composable
internal fun FileTabBar(selected: FilesTab, onSelect: (FilesTab) -> Unit, backdrop: Backdrop, modifier: Modifier,
    tint: Color = Color(.19f, .64f, .65f),
) = LiquidTabBar(FileTabs, selected, onSelect, backdrop, tint,
        modifier.windowInsetsPadding(fileTabInsets()).padding(start = 12.dp, end = 12.dp, bottom = FileTabClearance))

internal val LightAccent = Color(0xff186c79)
@Composable internal fun accent() = if (isSystemInDarkTheme()) DarkAccent else LightAccent

@Composable
internal fun FileArt(entry: Entry, modifier: Modifier = Modifier) {
    val ext = remember(entry.name) { entry.name.substringAfterLast('.', "").uppercase(Locale.ROOT).take(4) }
    val shades = remember(entry.folder, ext) {
        when {
            entry.folder -> Color(0xff358293) to Color(0xffa6e3df)
            ext == "TSV" || ext == "CSV" || ext == "XLSX" -> Color(0xff50866b) to Color(0xffdbedde)
            ext == "JSON" || ext == "XML" -> Color(0xff776398) to Color(0xffe8dff3)
            ext == "MD" -> Color(0xff577e91) to Color(0xffd4e8ee)
            else -> Color(0xff977444) to Color(0xfff0e5cf)
        }
    }
    var minSidePx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier
            .onSizeChanged {
                val side = minOf(it.width, it.height)
                if (side != minSidePx) minSidePx = side
            }
            .drawWithCache {
                val s = size.minDimension / 44f
                val x = (size.width - 44f * s) / 2f
                val y = (size.height - 44f * s) / 2f
                fun buildPoly(vararg coords: Float): Path = Path().apply {
                    for (i in coords.indices step 2) {
                        val px = x + coords[i] * s
                        val py = y + coords[i + 1] * s
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                if (entry.folder) {
                    val back = Path().apply {
                        moveTo(x + 2 * s, y + 12 * s); quadraticTo(x + 2 * s, y + 8 * s, x + 6 * s, y + 8 * s)
                        lineTo(x + 15 * s, y + 8 * s); quadraticTo(x + 17 * s, y + 8 * s, x + 19 * s, y + 11 * s)
                        lineTo(x + 38 * s, y + 11 * s); quadraticTo(x + 42 * s, y + 11 * s, x + 42 * s, y + 15 * s)
                        lineTo(x + 42 * s, y + 33 * s); quadraticTo(x + 42 * s, y + 37 * s, x + 38 * s, y + 37 * s)
                        lineTo(x + 6 * s, y + 37 * s); quadraticTo(x + 2 * s, y + 37 * s, x + 2 * s, y + 33 * s)
                        close()
                    }
                    val round = CornerRadius(3 * s)
                    val frontBrush = Brush.verticalGradient(listOf(shades.second, Color(0xff65b8c1)), y + 16 * s, y + 37 * s)
                    val notch = buildPoly(28f, 30f, 36f, 30f, 28f, 35f)
                    val notchColor = shades.first.copy(alpha = .48f)
                    val highlightColor = Color.White.copy(alpha = .68f)
                    onDrawBehind {
                        drawPath(back, shades.first)
                        drawRoundRect(Color(0xffe1f2e8), Offset(x + 4 * s, y + 14 * s), Size(36 * s, 18 * s), round)
                        drawRoundRect(frontBrush, Offset(x + 2 * s, y + 16 * s), Size(40 * s, 21 * s), round)
                        drawLine(highlightColor, Offset(x + 6 * s, y + 17 * s), Offset(x + 38 * s, y + 17 * s), s)
                        drawPath(notch, notchColor)
                    }
                } else {
                    val sheet = buildPoly(9f, 2f, 28f, 2f, 36f, 10f, 36f, 41f, 9f, 41f)
                    val fold = buildPoly(28f, 2f, 36f, 10f, 28f, 10f)
                    val foldColor = shades.first.copy(alpha = .58f)
                    val lineColor = shades.first.copy(alpha = .5f)
                    onDrawBehind {
                        drawPath(sheet, shades.second)
                        drawPath(fold, foldColor)
                        drawRect(shades.first, Offset(x + 9 * s, y + 29 * s), Size(27 * s, 9 * s))
                        drawLine(lineColor, Offset(x + 13 * s, y + 15 * s), Offset(x + 27 * s, y + 15 * s), 1.4f * s)
                        drawLine(lineColor, Offset(x + 13 * s, y + 19 * s), Offset(x + 23 * s, y + 19 * s), 1.4f * s)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!entry.folder && ext.isNotEmpty() && minSidePx > 0) {
            val sideDp = with(density) { minSidePx.toDp() }
            BasicText(
                ext,
                Modifier.offset(y = sideDp * .26f),
                style = TextStyle(Color.White, (sideDp.value * .145f).sp, FontWeight.Bold, fontFamily = FontFamily.Monospace),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun entryDetail(entry: Entry, knownCount: Int? = entry.childCount): String {
    val ctx = LocalContext.current
    return remember(entry, ctx, knownCount) {
        val kind = when {
            entry.folder && knownCount != null -> "$knownCount 项"
            entry.folder -> "文件夹"
            entry.bytes != null -> Formatter.formatShortFileSize(ctx, entry.bytes)
            else -> "文件"
        }
        val date = if (entry.modified > 0)
            SimpleDateFormat("yyyy/M/d HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(entry.modified)) else ""
        listOf(date, kind).filter(String::isNotEmpty).joinToString(" · ")
    }
}

@Composable
internal fun FileCell(entry: Entry, selecting: Boolean, selected: Boolean, modifier: Modifier = Modifier,
    artSize: Dp = 40.dp, onClick: (() -> Unit)? = null) {
    val colors = liquidListColors()
    val detail = entryDetail(entry)
    LiquidListCell(entry.name, modifier, subtitle=detail, selected=selected, onClick=onClick,
        leading={ FileArt(entry, Modifier.size(artSize)) },
        trailing={
            LiquidContentReplacement(if (selecting) (if (selected) LiquidSymbol.CheckCircle else LiquidSymbol.Unchecked)
                else if (entry.folder) LiquidSymbol.Forward else null, alignment=Alignment.Center) { symbol ->
                if (symbol != null) LiquidIcon(symbol, if (selected) accent() else colors.secondary,
                    Modifier.size(if (symbol == LiquidSymbol.Forward) 13.dp else 22.dp))
            }
        })
}

@Composable
internal fun FileItems(page: FilePage, model: FilesModel, style: FileLayout, colors: LiquidListColors, state: LazyListState,
    context: LiquidMenuState, prepare: (Entry, Entry) -> Unit, open: (Entry) -> Unit, insets: PaddingValues,
    reconnect: () -> Unit, onExternal: (Entry, Boolean) -> Unit) {
    if (page.error != null) {
        LiquidUnavailableView("无法打开此位置", LiquidSymbol.Folder, Modifier.padding(insets), page.error, colors) {
            LiquidActionButton(if (page.needsGrant) "重新连接" else "重试", if (page.needsGrant) reconnect else model::refresh,
                accent(), style = LiquidActionStyle.Filled)
        }
        return
    }
    if (!page.entry.folder) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(insets).padding(horizontal=22.dp, vertical=20.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FileArt(page.entry,Modifier.size(45.dp))
                Column { Label(page.entry.name,18,colors.ink,bold=true,lines=2); Label(page.entry.mime,13,colors.secondary) }
            }
            if (page.text != null) androidx.compose.foundation.text.selection.SelectionContainer {
                BasicText(page.text.ifEmpty { "空文档" }, Modifier.fillMaxWidth().padding(vertical=6.dp),
                    style=TextStyle(colors.ink,16.sp,lineHeight=26.sp))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                LiquidActionButton("用其他应用打开", { onExternal(page.entry,false) }, accent())
                LiquidActionButton("分享文件", { onExternal(page.entry,true) }, accent(), style = LiquidActionStyle.Plain)
            }
        }
        return
    }
    val order = remember(model.order) { fileComparator(model.order) }
    val query = model.queryFor(page.entry.id)
    val rows = remember(page, order, query) {
        val filtered = page.rows.filter { it.name.contains(query.trim(), ignoreCase = true) }
        if (page.entry == Recent && model.order == FileOrder.Name) filtered else filtered.sortedWith(order)
    }
    val select: (Entry) -> Unit = { if (model.selecting) model.toggle(it) else open(it) }
    if (rows.isEmpty()) {
        val search = query.isNotBlank()
        val title = when { search -> "没有匹配的文件"; page.entry == Recent -> "还没有打开过文件"; else -> "空文件夹" }
        LiquidUnavailableView(title, if (search) LiquidSymbol.Search else LiquidSymbol.Folder, Modifier.padding(insets),
            description = if (search) "试试其他名称或文件扩展名。" else null, colors = colors)
    } else if (style == FileLayout.Grid) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = ((maxWidth - 24.dp + 10.dp) / 128.dp).toInt().coerceAtLeast(1)
            val tileShape = RoundedCornerShape(16.dp)
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = insets, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows.chunked(columns), key = { it.first().id }) { batch ->
                    Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        batch.forEach { entry ->
                            val selected = entry.id in model.selected
                            Column(Modifier.weight(1f).liquidListContextMenu(context, tileShape, !model.selecting) { prepare(entry, page.entry) }
                                .clip(tileShape).background(if (selected) colors.selected else colors.surface)
                                .clickable(interactionSource = null, indication = if (model.selecting) LocalIndication.current else null) { select(entry) }.padding(12.dp),
                                horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                FileArt(entry,Modifier.size(54.dp))
                                Label(entry.name,14,colors.ink,lines=2)
                                if (model.selecting) LiquidIcon(if (selected) LiquidSymbol.CheckCircle else LiquidSymbol.Unchecked, accent(),Modifier.size(18.dp))
                            }
                        }
                        repeat(columns - batch.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    } else {
        if (style == FileLayout.List) {
            FileOutline(page, rows, order, model, colors, state, insets, context, prepare, select)
        } else LiquidList(LiquidListStyle.Cards, Modifier.fillMaxSize(), state, colors, insets) {
            liquidListSection("files", rows, { it.id }) { entry ->
                FileCell(entry, model.selecting, entry.id in model.selected,
                    Modifier.liquidListContextMenu(context, !model.selecting) { prepare(entry, page.entry) },
                    artSize = 60.dp) { select(entry) }
            }
        }
    }
}

private fun fileComparator(order: FileOrder): Comparator<Entry> {
    val collator = Collator.getInstance(Locale.CHINESE)
    val names = Comparator<Entry> { a, b -> collator.compare(a.name, b.name) }
    val withinKind = when (order) {
        FileOrder.Name -> names
        FileOrder.Modified -> compareByDescending<Entry> { it.modified }.then(names)
        FileOrder.Size -> compareByDescending<Entry> { it.bytes ?: -1L }.then(names)
    }
    return compareByDescending<Entry> { it.folder }.then(withinKind)
}

private data class OutlineRow(val key: String, val entry: Entry, val parent: Entry, val level: Int)
private data class Branch(val children: List<Entry> = emptyList(), val failed: Boolean = false)

@Composable
private fun FileOutline(page: FilePage, roots: List<Entry>, order: Comparator<Entry>, model: FilesModel,
    colors: LiquidListColors, state: LazyListState, insets: PaddingValues, context: LiquidMenuState,
    prepare: (Entry, Entry) -> Unit, open: (Entry) -> Unit) {
    val expanded = model.expandedFolders(page.entry.id)
    val branches = remember(page.entry.id, model.revision) { mutableStateMapOf<String, Branch>() }
    val allowBranches = model.queryFor(page.entry.id).isBlank() && page.entry != Recent
    if (allowBranches) expanded.forEach { id -> key(id) {
        LaunchedEffect(branches, id, model.revision) {
            val branch = try {
                Branch(withContext(Dispatchers.IO) { model.store.children(Entry(id, "")) })
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Branch(failed = true) }
            branches[id] = branch
        }
    } }
    val visible = buildList {
        val ancestors = mutableSetOf(page.entry.id)
        fun append(entries: List<Entry>, parent: Entry, level: Int, prefix: String) {
            for (entry in entries) {
                if (entry.id in ancestors) continue
                val key = prefix + entry.id.length + ":" + entry.id
                add(OutlineRow(key, entry, parent, level))
                if (allowBranches && entry.id in expanded) {
                    ancestors.add(entry.id)
                    append(branches[entry.id]?.children.orEmpty().sortedWith(order), entry, level + 1, key)
                    ancestors.remove(entry.id)
                }
            }
        }
        append(roots, page.entry, 0, "")
    }
    LiquidList(LiquidListStyle.Plain, Modifier.fillMaxSize(), state, colors, insets) {
        liquidListSection("outline", visible, { it.key }) { row ->
            val entry = row.entry
            val expandedRow = allowBranches && entry.id in expanded
            val branch = if (expandedRow) branches[entry.id] else null
            val detail = if (branch?.failed == true) "无法读取，点按名称打开" else entryDetail(entry, branch?.children?.size ?: entry.childCount)
            val selected = entry.id in model.selected
            LiquidOutlineCell(entry.name, detail, { open(entry) }, accent(), { FileArt(entry, Modifier.fillMaxSize()) },
                Modifier.liquidListContextMenu(context, !model.selecting) { prepare(entry, row.parent) },
                level = row.level, expanded = expandedRow, toggleLabel = (if (expandedRow) "收起" else "展开") + entry.name,
                onToggle = if (allowBranches && entry.folder && entry.childCount != 0) ({
                    model.toggleFolder(page.entry.id, entry.id)
                }) else null, selected = selected, trailing = if (!model.selecting) null else ({
                    LiquidIcon(if (selected) LiquidSymbol.CheckCircle else LiquidSymbol.Unchecked, accent(), Modifier.size(22.dp))
                }))
        }
    }
}
