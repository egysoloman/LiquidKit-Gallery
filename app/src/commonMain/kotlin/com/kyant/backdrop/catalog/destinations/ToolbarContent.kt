package com.kyant.backdrop.catalog.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.catalog.BackdropDemoScaffold
import com.kyant.backdrop.catalog.utils.BackHandler
import com.kyant.backdrop.components.*
import kotlin.math.roundToInt

internal data class ColorStudy(val name: String, val hex: String, val color: Color)
internal val studies = listOf(
    ColorStudy("雾松", "#A7C9B0", Color(0xFFA7C9B0)),
    ColorStudy("海湾", "#477DA1", Color(0xFF477DA1)),
    ColorStudy("柑橘", "#F0AD58", Color(0xFFF0AD58)),
    ColorStudy("赤陶", "#C97059", Color(0xFFC97059)),
)
private data class StudyPage(val study: ColorStudy?, val values: Boolean) {
    val depth get() = if (study == null) 0 else if (values) 2 else 1
}

private val Ink = Color(0xFF162B34)
private val Paper = Color(0xFFF7F8F0)

@Composable
fun ToolbarContent() {
    var grid by rememberSaveable { mutableStateOf(false) }
    var reversed by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var swatchDetail by rememberSaveable { mutableStateOf(false) }
    var onlySaved by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val detail = studies.firstOrNull { it.hex == selected }
    val visible = if (onlySaved) studies.filter { it.hex in saved } else studies
    val display = if (!grid && reversed) visible.asReversed() else visible
    val page = StudyPage(detail, swatchDetail)
    val rootSymbol = if (onlySaved) LiquidSymbol.Saved else LiquidSymbol.Bookmark
    val goBack = { if (swatchDetail) swatchDetail = false else selected = null }
    val history = rememberLiquidMenuState { item ->
        swatchDetail = false
        if (item.key == "palette") selected = null
    }
    val ancestors = buildList {
        if (swatchDetail && detail != null) add(LiquidMenuItem("study", detail.name))
        if (detail != null) add(LiquidMenuItem("palette", "色样"))
    }
    BackHandler(detail != null) { if (history.expanded) history.back() else goBack() }

    BackdropDemoScaffold { backdrop ->
        LiquidNavigationHistoryHost(history, backdrop, ancestors, Modifier.fillMaxSize(), rootSymbol = rootSymbol) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (detail != null) {
                        LiquidNavigationBackButton(history, backdrop, if (swatchDetail) "返回色样详情" else "返回色样", goBack)
                    } else {
                        LiquidNavigationRootButton(backdrop, rootSymbol, if (onlySaved) "显示全部色样" else "只看收藏",
                            onClick = { onlySaved = !onlySaved })
                    }
                    Spacer(Modifier.width(10.dp))
                    val title = if (swatchDetail) "色值" else detail?.name ?: "色样"
                    LiquidContentReplacement(title, Modifier.weight(1f)) { displayed ->
                        BasicText(displayed, style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    }
                    val actions = if (detail != null) {
                        val marked = detail.hex in saved
                        listOf(LiquidToolbarItem("save", if (marked) "取消收藏" else "收藏色样",
                            if (marked) LiquidSymbol.Saved else LiquidSymbol.Bookmark))
                    } else buildList {
                        add(LiquidToolbarItem("layout", if (grid) "切换为列表" else "切换为网格", if (grid) LiquidSymbol.List else LiquidSymbol.Grid))
                        if (!grid) add(LiquidToolbarItem("order", if (reversed) "恢复顺序" else "反转顺序",
                            if (reversed) LiquidSymbol.Descending else LiquidSymbol.Ascending))
                    }
                    LiquidToolbar(
                        backdrop, actions,
                        onClick = { action ->
                            when (action.key) {
                                "layout" -> grid = !grid
                                "order" -> reversed = !reversed
                                "save" -> if (detail != null) saved = if (detail.hex in saved) saved - detail.hex else saved + detail.hex
                            }
                        },
                    ) { LiquidIcon(it) }
                }
                LiquidNavigationTransition(page, StudyPage::depth, Modifier.fillMaxSize()) { visiblePage ->
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val detail = visiblePage.study
                        if (detail != null) {
                            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Paper)) {
                                Spacer(Modifier.fillMaxWidth().height(184.dp).background(detail.color))
                                if (visiblePage.values) {
                                    BasicText(detail.hex, Modifier.padding(16.dp), style = TextStyle(Ink, 25.sp, fontWeight = FontWeight.Medium))
                                    val rgb = listOf(detail.color.red, detail.color.green, detail.color.blue).map { (it * 255).roundToInt() }
                                    BasicText("RGB  " + rgb.joinToString(" · "), Modifier.padding(start = 16.dp, bottom = 16.dp),
                                        style = TextStyle(Ink.copy(alpha = .74f), 16.sp))
                                } else {
                                    Row(Modifier.fillMaxWidth().clickable { swatchDetail = true }.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        BasicText(detail.hex, Modifier.weight(1f), style = TextStyle(Ink, 16.sp, fontWeight = FontWeight.Medium))
                                        LiquidIcon(LiquidSymbol.Forward, Ink, Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else LiquidContentReplacement(grid to display) { (shownGrid, shown) ->
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (shown.isEmpty()) {
                                    BasicText("还没有收藏的色样", Modifier.padding(16.dp), style = TextStyle(Ink, 16.sp))
                                } else if (shownGrid) {
                                    shown.chunked(2).forEach { row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            row.forEach { study ->
                                                Column(
                                                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Paper)
                                                        .clickable { selected = study.hex },
                                                ) {
                                                    Spacer(Modifier.fillMaxWidth().height(82.dp).background(study.color))
                                                    BasicText(study.name, Modifier.padding(12.dp), style = TextStyle(Ink, 16.sp, fontWeight = FontWeight.Medium))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Paper)) {
                                        shown.forEachIndexed { index, study ->
                                            Row(
                                                Modifier.fillMaxWidth().clickable { selected = study.hex }.padding(16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Spacer(Modifier.size(36.dp).background(study.color, RoundedCornerShape(9.dp)))
                                                BasicText(study.name, Modifier.weight(1f), style = TextStyle(Ink, 17.sp, fontWeight = FontWeight.Medium))
                                                BasicText(study.hex, style = TextStyle(Ink.copy(alpha = .72f), 13.sp))
                                                if (study.hex in saved) LiquidIcon(LiquidSymbol.Saved, Ink, Modifier.size(15.dp))
                                            }
                                            if (index < shown.lastIndex) Spacer(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(Ink.copy(alpha = .08f)))
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
}
