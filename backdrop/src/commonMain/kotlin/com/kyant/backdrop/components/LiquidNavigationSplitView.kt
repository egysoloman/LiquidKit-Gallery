package com.kyant.backdrop.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class LiquidSplitColumn { Sidebar, Content, Detail }
enum class LiquidSplitVisibility { Automatic, HideSidebar, DetailOnly, ShowSidebar }

class LiquidSplitLayout internal constructor(
    val columns: List<LiquidSplitColumn>,
    val focusedColumn: LiquidSplitColumn,
    val supportsSidebar: Boolean,
)

@Stable
class LiquidNavigationSplitState internal constructor(initialPath: List<LiquidSplitColumn>) {
    private var path by mutableStateOf(initialPath)
    val column get() = path.last()
    val canGoBack get() = path.size > 1
    val ancestors get() = path.dropLast(1).asReversed()

    fun show(column: LiquidSplitColumn) {
        if (this.column != column) path = path.takeWhile { it.ordinal < column.ordinal } + column
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        path = path.dropLast(1)
        return true
    }

    /** Resolves the visible panes and the toolbar affordances from the same width and navigation path. */
    fun layoutFor(
        width: Dp, visibility: LiquidSplitVisibility = LiquidSplitVisibility.Automatic, hasContent: Boolean = true,
        twoColumnWidth: Dp = 600.dp, threeColumnWidth: Dp = 1000.dp,
    ): LiquidSplitLayout {
        require(twoColumnWidth > 0.dp && threeColumnWidth >= twoColumnWidth)
        val sidebar = LiquidSplitColumn.Sidebar
        val content = LiquidSplitColumn.Content
        val detail = LiquidSplitColumn.Detail
        val all = if (hasContent) listOf(sidebar, content, detail) else listOf(sidebar, detail)
        val focus = column.takeIf { it in all } ?: sidebar
        if (!width.value.isFinite() || width < twoColumnWidth) return LiquidSplitLayout(listOf(focus), focus, false)
        if (visibility == LiquidSplitVisibility.DetailOnly) return LiquidSplitLayout(listOf(detail), detail, false)
        val visible = when {
            visibility == LiquidSplitVisibility.HideSidebar -> all.drop(1)
            all.size == 2 || width >= threeColumnWidth -> all
            focus == detail && visibility == LiquidSplitVisibility.ShowSidebar -> listOf(sidebar, detail)
            focus == detail -> listOf(content, detail)
            else -> listOf(sidebar, content)
        }
        return LiquidSplitLayout(visible, focus.takeIf { it in visible } ?: visible.first(), true)
    }

    internal companion object {
        val Saver = listSaver<LiquidNavigationSplitState, String>(
            save = { state -> state.path.map { it.name } },
            restore = { names -> LiquidNavigationSplitState(names.map(LiquidSplitColumn::valueOf)) },
        )
    }
}

@Composable
fun rememberLiquidNavigationSplitState(
    initialColumn: LiquidSplitColumn = LiquidSplitColumn.Sidebar,
): LiquidNavigationSplitState = rememberSaveable(saver = LiquidNavigationSplitState.Saver) {
    LiquidNavigationSplitState(listOf(LiquidSplitColumn.Sidebar, initialColumn).distinct())
}

/**
 * Uses the available layout width to present one, two or three panes. Selections belong to the caller.
 * Omit [content] for a sidebar/detail hierarchy. Each pane keeps its own saveable UI and scroll state.
 */
@Composable
fun LiquidNavigationSplitView(
    state: LiquidNavigationSplitState,
    modifier: Modifier = Modifier,
    visibility: LiquidSplitVisibility = LiquidSplitVisibility.Automatic,
    twoColumnWidth: Dp = 600.dp,
    threeColumnWidth: Dp = 1000.dp,
    dividerColor: Color = Color.Gray.copy(alpha = .24f),
    sidebar: @Composable () -> Unit,
    content: (@Composable () -> Unit)? = null,
    detail: @Composable () -> Unit,
) = LiquidNavigationSplitView(state, modifier, visibility, twoColumnWidth, threeColumnWidth, dividerColor,
    swipeBackFromBlank = false, sidebar = sidebar, content = content, detail = detail)

@Composable
fun LiquidNavigationSplitView(
    state: LiquidNavigationSplitState,
    modifier: Modifier = Modifier,
    visibility: LiquidSplitVisibility = LiquidSplitVisibility.Automatic,
    twoColumnWidth: Dp = 600.dp,
    threeColumnWidth: Dp = 1000.dp,
    dividerColor: Color = Color.Gray.copy(alpha = .24f),
    swipeBackFromBlank: Boolean,
    sidebar: @Composable () -> Unit,
    content: (@Composable () -> Unit)? = null,
    detail: @Composable () -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val pane: @Composable (LiquidSplitColumn) -> Unit = { column ->
        holder.SaveableStateProvider(column.name) {
            when (column) {
                LiquidSplitColumn.Sidebar -> sidebar()
                LiquidSplitColumn.Content -> content?.invoke()
                LiquidSplitColumn.Detail -> detail()
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val visible = state.layoutFor(maxWidth, visibility, content != null, twoColumnWidth, threeColumnWidth).columns
        if (maxWidth < twoColumnWidth || !maxWidth.value.isFinite()) {
            LiquidSwipeBackNavigation(visible.first(), state.ancestors.firstOrNull().takeIf { swipeBackFromBlank },
                { it.ordinal }, { it }, { state.back() }, Modifier.fillMaxSize(), content = pane)
        } else {
            val sidebarWidth = (maxWidth * .33f).coerceAtMost(310.dp)
            val contentWidth = (maxWidth * .38f).coerceAtMost(370.dp)
            val targetSidebar = if (LiquidSplitColumn.Sidebar in visible) sidebarWidth + 1.dp else 0.dp
            val targetContent = when {
                LiquidSplitColumn.Content !in visible -> 0.dp
                visible.last() == LiquidSplitColumn.Content -> maxWidth - targetSidebar
                else -> contentWidth + 1.dp
            }
            val splitSpring = remember { GlassSpring.smooth(0.32f).dpSpec }
            val sidebarSlot by animateDpAsState(targetSidebar, splitSpring, label = "Sidebar width")
            val contentSlot by animateDpAsState(targetContent, splitSpring, label = "Content width")
            val showDetail = maxWidth - sidebarSlot - contentSlot > .5.dp
            val sidebarFraction = if (sidebarWidth > 0.dp) (sidebarSlot / (sidebarWidth + 1.dp)).coerceIn(0f, 1f) else 0f
            Row(Modifier.fillMaxSize().clipToBounds()) {
                if (sidebarSlot > 0.dp) {
                    Box(Modifier.width(sidebarSlot).fillMaxHeight().clipToBounds()) {
                        Row(Modifier.wrapContentWidth(androidx.compose.ui.Alignment.End, unbounded = true)) {
                            Box(Modifier.width(sidebarWidth).fillMaxHeight()) { pane(LiquidSplitColumn.Sidebar) }
                            Spacer(Modifier.fillMaxHeight().width(1.dp).background(dividerColor.copy(alpha = dividerColor.alpha * sidebarFraction)))
                        }
                    }
                }
                if (contentSlot > 0.dp && content != null) {
                    Box(Modifier.width(contentSlot).fillMaxHeight().clipToBounds()) {
                        pane(LiquidSplitColumn.Content)
                        if (visible.last() != LiquidSplitColumn.Content) Spacer(Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
                            .fillMaxHeight().width(1.dp).background(dividerColor))
                    }
                }
                if (showDetail) {
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) { pane(LiquidSplitColumn.Detail) }
                }
            }
        }
    }
}
