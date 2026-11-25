package com.liquidkit.swiftgallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liquidkit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppearanceMode(val title: String, val symbol: LiquidKitSymbol) {
    System("跟随系统", LiquidKitSymbol.CircleHalfFilled),
    Light("浅色模式", LiquidKitSymbol.Sun),
    Dark("深色模式", LiquidKitSymbol.Moon),
}

class FavoritesStore(private val prefs: SharedPreferences) {
    private val key = "SwiftGallery.favoriteRoutes"
    var favorites by mutableStateOf(prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet())
        private set

    fun isFavorite(route: ComponentRoute): Boolean = route.name in favorites

    fun toggle(route: ComponentRoute) {
        val next = favorites.toMutableSet()
        if (!next.add(route.name)) next.remove(route.name)
        favorites = next
        prefs.edit().putStringSet(key, next).apply()
    }

    val count: Int get() = favorites.size
}

val LocalFavoritesStore = staticCompositionLocalOf<FavoritesStore> {
    error("LocalFavoritesStore not provided")
}

val LocalExpandAllCode = compositionLocalOf { false }
val LocalDetailExpandControl = compositionLocalOf<Pair<Boolean, () -> Unit>?> { null }

@Composable
fun ComponentDemoPage(
    item: ComponentItem,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val favorites = LocalFavoritesStore.current
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    var localExpandAll by remember { mutableStateOf(false) }
    val hoistedExpand = LocalDetailExpandControl.current
    val expandAllCode = hoistedExpand?.first ?: localExpandAll
    val toggleExpand = hoistedExpand?.second ?: { localExpandAll = !localExpandAll }
    val scroll = rememberScrollState()
    val isFav = favorites.isFavorite(item.route)

    CompositionLocalProvider(LocalExpandAllCode provides expandAllCode) {
        LiquidKitNavigationScaffold(
            title = item.title,
            modifier = Modifier.fillMaxSize().background(colors.background),
            backdrop = backdrop,
            titleMode = LiquidKitNavigationTitleMode.Inline,
            colors = colors,
            contentWindowInsets = WindowInsets.safeDrawing,
            scrollOffsetPx = { scroll.value.toFloat() },
            leading = { glass ->
                LiquidKitNavBarSlot(
                    group = LiquidKitNavBarGroup(
                        actions = listOf(
                            LiquidKitNavBarAction(
                                key = "back",
                                label = "返回",
                                symbol = LiquidKitSymbol.Back,
                                onClick = onBack,
                            ),
                        ),
                        transitionGroup = "detail-back",
                    ),
                    backdrop = glass,
                )
            },
            trailing = { glass ->
                LiquidKitNavBarSlot(
                    group = LiquidKitNavBarGroup(
                        actions = listOf(
                            LiquidKitNavBarAction(
                                key = "fav",
                                label = if (isFav) "取消收藏" else "收藏组件",
                                symbol = if (isFav) LiquidKitSymbol.StarFilled else LiquidKitSymbol.Star,
                                tint = if (isFav) Color(0xffffcc00) else Color.Unspecified,
                                onClick = { favorites.toggle(item.route) },
                            ),
                            LiquidKitNavBarAction(
                                key = "code",
                                label = if (expandAllCode) "收起全部源码" else "展开全部源码",
                                symbol = LiquidKitSymbol.Code,
                                tint = if (expandAllCode) Color(0xff007aff) else Color.Unspecified,
                                onClick = toggleExpand,
                            ),
                        ),
                        transitionGroup = "default",
                    ),
                    backdrop = glass,
                )
            },
        ) { insets ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(insets)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DemoHeaderCard(item, colors)
                content()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DemoHeaderCard(item: ComponentItem, colors: LiquidKitColors) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconBrush = remember(item.category.color) {
                Brush.linearGradient(listOf(item.category.color, item.category.color.copy(alpha = .72f)))
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBrush),
                contentAlignment = Alignment.Center,
            ) {
                LiquidKitIcon(item.symbol, Color.White, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        item.title,
                        Modifier.weight(1f, fill = false),
                        style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.badge?.let { badge ->
                        Box(
                            Modifier
                                .clip(LiquidKitCapsule)
                                .background(item.category.color.copy(alpha = .18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            BasicText(
                                badge,
                                style = TextStyle(item.category.color, 11.sp, FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                BasicText(
                    item.englishName,
                    style = TextStyle(colors.secondary, 12.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BasicText(item.summary, style = TextStyle(colors.secondary, 14.sp, lineHeight = 20.sp))
        if (item.tags.isNotEmpty()) {
            LiquidKitFlowLayout(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 6.dp,
                verticalSpacing = 6.dp,
            ) {
                item.tags.forEach { tag ->
                    Box(
                        Modifier
                            .clip(LiquidKitCapsule)
                            .background(colors.secondary.copy(alpha = .13f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        BasicText(tag, style = TextStyle(colors.secondary, 11.sp, fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
    }
}

@Composable
fun DemoSection(
    title: String,
    subtitle: String? = null,
    code: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = liquidKitColors()
    val dark = liquidKitAppearance().dark
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expandAll = LocalExpandAllCode.current
    var isCodeExpanded by remember { mutableStateOf(expandAll) }
    var didCopy by remember { mutableStateOf(false) }

    LaunchedEffect(expandAll) { isCodeExpanded = expandAll }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(title, style = TextStyle(colors.ink, 16.sp, FontWeight.SemiBold))
            if (subtitle != null) {
                BasicText(subtitle, style = TextStyle(colors.secondary, 12.5.sp, lineHeight = 17.sp))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (dark) Color(0xff111113) else Color(0xfffafafc))
                .border(.75.dp, colors.separator.copy(alpha = .38f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LiquidKitActionButton(
                label = if (isCodeExpanded) "收起源码" else "查看源码 / Code",
                onClick = { isCodeExpanded = !isCodeExpanded },
                tint = Color(0xff007aff),
                style = LiquidKitActionStyle.Tinted,
                leadingSymbol = LiquidKitSymbol.Code,
                controlSize = LiquidKitControlSize.Small,
            )
            LiquidKitActionButton(
                label = if (didCopy) "已复制" else "复制代码",
                onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)
                    clip?.setPrimaryClip(ClipData.newPlainText("SwiftUI Code", code))
                    didCopy = true
                    scope.launch {
                        delay(1500)
                        didCopy = false
                    }
                },
                tint = if (didCopy) Color(0xff34c759) else Color(0xff007aff),
                style = LiquidKitActionStyle.Tinted,
                leadingSymbol = if (didCopy) LiquidKitSymbol.Check else LiquidKitSymbol.Clipboard,
                controlSize = LiquidKitControlSize.Small,
            )
        }

        AnimatedVisibility(
            visible = isCodeExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            CodeBlockView(code)
        }
    }
}

@Composable
fun CodeBlockView(code: String) {
    val highlighted = remember(code) { SwiftSyntaxHighlighter.highlight(code) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xff171a21))
            .border(.75.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        BasicText(
            text = highlighted,
            style = TextStyle(Color(0xffe3e8f2), 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
        )
    }
}

object SwiftSyntaxHighlighter {
    private val keywords = setOf(
        "import", "struct", "class", "enum", "protocol", "extension", "func", "var", "let",
        "if", "else", "switch", "case", "default", "for", "in", "while", "return", "true",
        "false", "nil", "some", "any", "self", "Self", "private", "public", "static",
        "try", "await", "async", "do", "catch", "throw", "where", "typealias", "init",
    )
    private val wrappers = setOf(
        "@State", "@Binding", "@Environment", "@FocusState", "@Namespace", "@Observable",
        "@MainActor", "@ViewBuilder", "@main", "@AppStorage",
    )

    fun highlight(source: String): AnnotatedString = buildAnnotatedString {
        val lines = source.trimIndent().split('\n')
        lines.forEachIndexed { lineIdx, line ->
            val commentStart = findComment(line)
            val codePart = if (commentStart >= 0) line.substring(0, commentStart) else line
            appendCodeSegment(codePart)
            if (commentStart >= 0) {
                pushStyle(SpanStyle(Color(0xff7a8f9e)))
                append(line.substring(commentStart))
                pop()
            }
            if (lineIdx < lines.lastIndex) append('\n')
        }
    }

    private fun findComment(line: String): Int {
        var inStr = false
        var prev = '\u0000'
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"' && prev != '\\') inStr = !inStr
            else if (!inStr && ch == '/' && i + 1 < line.length && line[i + 1] == '/') return i
            prev = ch
        }
        return -1
    }

    private fun AnnotatedString.Builder.appendCodeSegment(seg: String) {
        var i = 0
        while (i < seg.length) {
            val ch = seg[i]
            when {
                ch == '"' -> {
                    var end = i + 1
                    var prev = '"'
                    while (end < seg.length) {
                        val cur = seg[end]
                        end++
                        if (cur == '"' && prev != '\\') break
                        prev = cur
                    }
                    pushStyle(SpanStyle(Color(0xfffc7d70)))
                    append(seg.substring(i, end))
                    pop()
                    i = end
                }
                ch == '@' -> {
                    var end = i + 1
                    while (end < seg.length && (seg[end].isLetterOrDigit() || seg[end] == '_')) end++
                    val word = seg.substring(i, end)
                    pushStyle(SpanStyle(if (word in wrappers) Color(0xfffc6eb0) else Color(0xffdbe0eb)))
                    append(word)
                    pop()
                    i = end
                }
                ch.isLetter() || ch == '_' -> {
                    var end = i + 1
                    while (end < seg.length && (seg[end].isLetterOrDigit() || seg[end] == '_')) end++
                    val word = seg.substring(i, end)
                    val color = when {
                        word in keywords -> Color(0xfffc6eb0)
                        word.firstOrNull()?.isUpperCase() == true -> Color(0xff73d9fa)
                        i > 0 && seg[i - 1] == '.' -> Color(0xffabe08f)
                        else -> Color(0xffe3e8f2)
                    }
                    pushStyle(SpanStyle(color))
                    append(word)
                    pop()
                    i = end
                }
                ch.isDigit() -> {
                    var end = i + 1
                    while (end < seg.length && (seg[end].isDigit() || seg[end] == '.')) end++
                    pushStyle(SpanStyle(Color(0xffd9c778)))
                    append(seg.substring(i, end))
                    pop()
                    i = end
                }
                else -> {
                    pushStyle(SpanStyle(Color(0xffc2ccdb)))
                    append(ch)
                    pop()
                    i++
                }
            }
        }
    }
}
