package com.liquidkit.swiftgallery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.liquidkit.*

private enum class GalleryTab { Gallery, Ios27, Search }

private val CardCorner14 = RoundedCornerShape(14.dp)
private val CardCorner16 = RoundedCornerShape(16.dp)
private val BadgeCorner8 = RoundedCornerShape(8.dp)
private val BadgeCorner9 = RoundedCornerShape(9.dp)
private val BadgeCorner12 = RoundedCornerShape(12.dp)
private val CategoryIconBrushes: Map<ComponentCategory, Brush> =
    ComponentCategory.entries.associateWith { cat ->
        Brush.linearGradient(listOf(cat.color, cat.color.copy(alpha = .74f)))
    }
private val CategoryItemCounts: Map<ComponentCategory, Int> =
    ComponentCategory.entries.associateWith { cat -> ComponentCatalog.items(cat).size }
private val Ios27HeroBrush: Brush =
    Brush.linearGradient(listOf(Color(0xff00c7be), Color(0xff007aff), Color(0xff5856d6)))

@Composable
fun SwiftGalleryHomeView(
    appearanceMode: AppearanceMode,
    onAppearanceChange: (AppearanceMode) -> Unit,
) {
    var activeTab by rememberSaveable { mutableStateOf(GalleryTab.Gallery) }
    var activeRouteName by rememberSaveable { mutableStateOf("") }
    var dragVisualRoute by remember(activeRouteName) { mutableStateOf<String?>(null) }
    val visualRouteName = dragVisualRoute ?: activeRouteName
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryName by rememberSaveable { mutableStateOf("") }
    var showFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    var tabBarMinimized by remember { mutableStateOf(false) }
    val expandAllByRoute = remember { mutableStateMapOf<ComponentRoute, Boolean>() }

    val selectedCategory = remember(selectedCategoryName) {
        ComponentCategory.entries.firstOrNull { it.name == selectedCategoryName }
    }
    val visualRoute = remember(visualRouteName) {
        ComponentRoute.entries.firstOrNull { it.name == visualRouteName }
    }
    val favorites = LocalFavoritesStore.current
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    val navSceneBackdrop = rememberLayerBackdrop()
    val underToolbar = rememberCombinedBackdrop(backdrop, navSceneBackdrop)
    val pageStates = rememberSaveableStateHolder()

    val appearanceMenu = rememberLiquidKitMenuState { chosen ->
        AppearanceMode.entries.firstOrNull { it.name == chosen.key }?.let(onAppearanceChange)
    }
    val appearanceItems = remember(appearanceMode) {
        AppearanceMode.entries.map { mode ->
            LiquidKitMenuItem(mode.name, mode.title, symbol = mode.symbol, checked = mode == appearanceMode)
        }
    }
    val chromeByRoute = remember { mutableStateMapOf<String, LiquidKitPinnedNavChrome>() }
    val chromeKey = if (visualRoute != null) visualRouteName else "tab-${activeTab.name}"
    val activeChrome = chromeByRoute[chromeKey] ?: LiquidKitPinnedNavChrome(
        title = visualRoute?.let { ComponentCatalog.item(it)?.title } ?: when (activeTab) {
            GalleryTab.Gallery -> "SwiftUI Gallery"
            GalleryTab.Ios27 -> "iOS 27 & Glass"
            GalleryTab.Search -> "搜索组件"
        },
        smallTitleAlpha = if (visualRoute != null) 1f else 0f,
        headerGlassAlpha = 0f,
        isScrolled = false,
    )

    val leadingGroup = remember(visualRoute) {
        if (visualRoute != null) {
            LiquidKitNavBarGroup(
                actions = listOf(
                    LiquidKitNavBarAction(
                        key = "back",
                        label = "返回",
                        symbol = LiquidKitSymbol.Back,
                        onClick = { activeRouteName = "" },
                    ),
                ),
                transitionGroup = "detail-back",
            )
        } else null
    }
    val trailingGroup = remember(visualRoute, activeTab, appearanceMode, favorites.favorites, expandAllByRoute.toMap()) {
        if (visualRoute != null) {
            val isFav = favorites.isFavorite(visualRoute)
            val isExpand = expandAllByRoute[visualRoute] == true
            LiquidKitNavBarGroup(
                actions = listOf(
                    LiquidKitNavBarAction(
                        key = "fav",
                        label = if (isFav) "取消收藏" else "收藏组件",
                        symbol = if (isFav) LiquidKitSymbol.StarFilled else LiquidKitSymbol.Star,
                        tint = if (isFav) Color(0xffffcc00) else Color.Unspecified,
                        onClick = { favorites.toggle(visualRoute) },
                    ),
                    LiquidKitNavBarAction(
                        key = "code",
                        label = if (isExpand) "收起全部源码" else "展开全部源码",
                        symbol = LiquidKitSymbol.Code,
                        tint = if (isExpand) Color(0xff007aff) else Color.Unspecified,
                        onClick = { expandAllByRoute[visualRoute] = !isExpand },
                    ),
                ),
                transitionGroup = "default",
            )
        } else if (activeTab == GalleryTab.Gallery) {
            LiquidKitNavBarGroup(
                actions = listOf(
                    LiquidKitNavBarAction(
                        key = "appearance",
                        label = "外观模式",
                        symbol = appearanceMode.symbol,
                        menuState = appearanceMenu,
                        onClick = appearanceMenu::show,
                    ),
                ),
                transitionGroup = "default",
            )
        } else {
            null
        }
    }

    LiquidKitAnchoredMenuHost(
        state = appearanceMenu,
        items = appearanceItems,
        modifier = Modifier.fillMaxSize(),
        backdrop = underToolbar,
    ) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            LiquidKitSwipeBackNavigation(
                page = activeRouteName,
                previousPage = if (activeRouteName.isNotEmpty()) "" else null,
                depth = { if (it.isEmpty()) 0 else 1 },
                onNavigateBack = { activeRouteName = it },
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(
                        navSceneBackdrop,
                        enabled = activeRouteName.isNotEmpty() || dragVisualRoute != null || activeChrome.isScrolled,
                    )
                    .background(colors.background),
                predictiveBackEnabled = true,
                onVisualTargetChange = { target -> dragVisualRoute = target.takeIf { it != activeRouteName } },
            ) { displayedRouteName ->
                val displayedRoute = ComponentRoute.entries.firstOrNull { it.name == displayedRouteName }
                val routeReporter: (LiquidKitPinnedNavChrome) -> Unit = remember(displayedRouteName) {
                    { chrome -> chromeByRoute[displayedRouteName] = chrome }
                }
                pageStates.SaveableStateProvider(displayedRouteName) {
                    CompositionLocalProvider(LocalLiquidKitPinnedNavChromeReporter provides routeReporter) {
                        if (displayedRoute != null) {
                            val isExpand = expandAllByRoute[displayedRoute] == true
                            CompositionLocalProvider(
                                LocalDetailExpandControl provides (isExpand to {
                                    expandAllByRoute[displayedRoute] = !isExpand
                                }),
                            ) {
                                ComponentDetailRouter(route = displayedRoute, onBack = { activeRouteName = "" })
                            }
                        } else {
                            val navTabs = remember {
                                listOf(
                                    LiquidKitTab(GalleryTab.Gallery, "组件库", LiquidKitSymbol.Grid),
                                    LiquidKitTab(GalleryTab.Ios27, "iOS 27", LiquidKitSymbol.Sparkles),
                                )
                            }
                            LiquidKitAppScaffold(
                                modifier = Modifier.fillMaxSize().background(colors.background),
                                edgeToEdgeSystemBars = false, // Already managed by LiquidKitRoot
                                bottomBar = { tabSceneBackdrop ->
                                    LiquidKitTabBar(
                                        tabs = navTabs,
                                        selected = if (activeTab == GalleryTab.Search) GalleryTab.Gallery else activeTab,
                                        onSelect = {
                                            activeTab = it
                                            tabBarMinimized = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 21.dp, vertical = 10.dp),
                                        backdrop = tabSceneBackdrop,
                                        tint = Color(0xff007aff),
                                        minimized = tabBarMinimized,
                                        onExpandFromMinimized = { tabBarMinimized = false },
                                        searchAction = {
                                            activeTab = GalleryTab.Search
                                            tabBarMinimized = false
                                        },
                                        searchLabel = "搜索组件",
                                        searchSelected = activeTab == GalleryTab.Search,
                                    )
                                },
                            ) { tabSceneBackdrop, _ ->
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(colors.background),
                                ) {
                                    LiquidKitViewTransition(
                                        targetState = activeTab,
                                        modifier = Modifier.fillMaxSize(),
                                        style = LiquidKitViewTransitionStyle.CrossFade,
                                    ) { tab ->
                                        val tabKey = "tab-${tab.name}"
                                        val tabReporter: (LiquidKitPinnedNavChrome) -> Unit = remember(tabKey) {
                                            { chrome -> chromeByRoute[tabKey] = chrome }
                                        }
                                        pageStates.SaveableStateProvider(tabKey) {
                                            CompositionLocalProvider(LocalLiquidKitPinnedNavChromeReporter provides tabReporter) {
                                                when (tab) {
                                                    GalleryTab.Gallery -> GalleryBrowseView(
                                                        selectedCategory = selectedCategory,
                                                        onSelectCategory = { selectedCategoryName = it?.name.orEmpty() },
                                                        showFavoritesOnly = showFavoritesOnly,
                                                        onToggleFavoritesOnly = { showFavoritesOnly = !showFavoritesOnly },
                                                        onScrollMinimize = { tabBarMinimized = it },
                                                        onOpenRoute = { activeRouteName = it.name },
                                                        pageBackdrop = tabSceneBackdrop,
                                                    )

                                                    GalleryTab.Ios27 -> Ios27SpotlightView(
                                                        onScrollMinimize = { tabBarMinimized = it },
                                                        onOpenRoute = { activeRouteName = it.name },
                                                        pageBackdrop = tabSceneBackdrop,
                                                    )

                                                    GalleryTab.Search -> SearchGalleryView(
                                                        query = searchQuery,
                                                        onQueryChange = { searchQuery = it },
                                                        onScrollMinimize = { tabBarMinimized = it },
                                                        onOpenRoute = { activeRouteName = it.name },
                                                        pageBackdrop = tabSceneBackdrop,
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

            // Shared NavigationStack top bar pinned across hierarchy pushes/pops with smooth toolbar morphing.
            val density = LocalDensity.current
            val safe = WindowInsets.safeDrawing.asPaddingValues()
            val status = safe.calculateTopPadding()
            val layoutDirection = LocalLayoutDirection.current
            val horizontalStart = safe.calculateStartPadding(layoutDirection)
            val horizontalEnd = safe.calculateEndPadding(layoutDirection)
            val actionsHeight = maxOf(60.dp, with(density) { 22.sp.toDp() } + 16.dp)
            var leadingWidth by remember { mutableIntStateOf(0) }
            var trailingWidth by remember { mutableIntStateOf(0) }
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = status, start = horizontalStart, end = horizontalEnd)
                    .height(actionsHeight),
            ) {
                if (activeChrome.smallTitleAlpha > 0.003f) {
                    val isDetail = visualRoute != null
                    val slidePx = with(density) { 120.dp.roundToPx() }
                    val navMotion = LocalLiquidKitTokens.current.navigation
                    Layout(
                        modifier = Modifier.fillMaxSize(),
                        content = {
                            AnimatedContent(
                                targetState = isDetail to activeChrome.title,
                                contentKey = { it },
                                contentAlignment = Alignment.Center,
                                transitionSpec = {
                                    val pushingForward = targetState.first && !initialState.first
                                    val poppingBack = !targetState.first && initialState.first
                                    val dir = if (pushingForward) 1 else if (poppingBack) -1 else 0
                                    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(280, easing = navMotion.pageEasing)
                                    val content = if (dir != 0) {
                                        (slideInHorizontally(slideSpec) { dir * slidePx } +
                                            fadeIn(tween(200, delayMillis = 45, easing = navMotion.pageEasing))) togetherWith
                                            (slideOutHorizontally(slideSpec) { -dir * slidePx } + fadeOut(tween(130)))
                                    } else {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                    }
                                    content.using(SizeTransform(clip = false))
                                },
                                label = "PinnedNavTitleSlide",
                            ) { (_, displayed) ->
                                BasicText(
                                    displayed,
                                    Modifier.graphicsLayer { alpha = activeChrome.smallTitleAlpha }
                                        .semantics { heading() },
                                    style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    ) { measured, limits ->
                        val gutter = 12.dp.roundToPx()
                        val sideInset = 16.dp.roundToPx()
                        val start = (if (leadingWidth == 0) gutter else sideInset + leadingWidth + gutter).coerceAtMost(limits.maxWidth)
                        val end = (limits.maxWidth - trailingWidth - (if (trailingWidth == 0) 0 else sideInset) - gutter).coerceAtLeast(start)
                        val label = measured.single().measure(Constraints(maxWidth = (end - start).coerceAtLeast(0)))
                        val centered = (limits.maxWidth - label.width) / 2
                        val maxX = (end - label.width).coerceAtLeast(start)
                        val x = centered.coerceIn(start, maxX)
                        layout(limits.maxWidth, limits.maxHeight) { label.placeRelative(x, (limits.maxHeight - label.height) / 2) }
                    }
                }
                Box(
                    Modifier.align(Alignment.CenterStart).padding(start = 16.dp).onSizeChanged { leadingWidth = it.width },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    LiquidKitNavigationBarStyle {
                        LiquidKitNavBarSlot(leadingGroup, underToolbar)
                    }
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).onSizeChanged { trailingWidth = it.width },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    LiquidKitNavigationBarStyle {
                        LiquidKitNavBarSlot(trailingGroup, underToolbar)
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBrowseView(
    selectedCategory: ComponentCategory?,
    onSelectCategory: (ComponentCategory?) -> Unit,
    showFavoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    onScrollMinimize: (Boolean) -> Unit,
    onOpenRoute: (ComponentRoute) -> Unit,
    pageBackdrop: LiquidKitLayerBackdrop,
) {
    val favorites = LocalFavoritesStore.current
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    val listState = rememberLazyListState()

    val isScrolledDeep by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 2 }
    }
    LaunchedEffect(isScrolledDeep) { onScrollMinimize(isScrolledDeep) }

    val displayedCategories = remember(selectedCategory) {
        if (selectedCategory != null) listOf(selectedCategory) else ComponentCategory.entries
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = ((maxWidth - 32.dp + 10.dp) / 168.dp).toInt().coerceIn(2, 4)
        val categoryRows = remember(columns) { ComponentCategory.entries.chunked(columns) }
        LiquidKitNavigationScaffold(
            title = "SwiftUI Gallery",
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            scrollState = listState,
            titleMode = LiquidKitNavigationTitleMode.Large,
            colors = colors,
            contentWindowInsets = WindowInsets.safeDrawing,
            pageBackdrop = pageBackdrop,
        ) { insets ->
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = insets.calculateStartPadding(layoutDirection) + 16.dp,
                    end = insets.calculateEndPadding(layoutDirection) + 16.dp,
                    top = insets.calculateTopPadding() + 4.dp,
                    bottom = insets.calculateBottomPadding() + 94.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header-bar") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            "按分类浏览（${ComponentCategory.entries.size} 大类 · ${ComponentCatalog.allItems.size} 页）",
                            Modifier.weight(1f),
                            style = TextStyle(colors.secondary, 13.sp, FontWeight.Medium),
                        )
                        if (selectedCategory != null || showFavoritesOnly) {
                            LiquidKitActionButton(
                                label = "重置筛选",
                                onClick = {
                                    onSelectCategory(null)
                                    if (showFavoritesOnly) onToggleFavoritesOnly()
                                },
                                tint = Color(0xff007aff),
                                style = LiquidKitActionStyle.Borderless,
                                controlSize = LiquidKitControlSize.Mini,
                            )
                        }
                    }
                }

                item(key = "summary-card") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CardCorner16)
                            .background(colors.surface)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BasicText("SwiftUI 全景组件画廊", style = TextStyle(colors.ink, 17.sp, FontWeight.SemiBold))
                            BasicText(
                                "${ComponentCategory.entries.size} 大分类 · ${ComponentCatalog.allItems.size} 个主题页 · 全部附带源码",
                                style = TextStyle(colors.secondary, 12.sp),
                            )
                        }
                        LiquidKitActionButton(
                            label = "收藏 (${favorites.count})",
                            onClick = onToggleFavoritesOnly,
                            tint = if (showFavoritesOnly) Color(0xffffb800) else Color(0xff007aff),
                            style = LiquidKitActionStyle.Tinted,
                            leadingSymbol = if (showFavoritesOnly) LiquidKitSymbol.StarFilled else LiquidKitSymbol.Star,
                            controlSize = LiquidKitControlSize.Small,
                        )
                    }
                }

                itemsIndexed(categoryRows, key = { idx, _ -> "category-row-$idx" }) { _, rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { cat ->
                            val chosen = selectedCategory == cat
                            val count = CategoryItemCounts[cat] ?: 0
                            val iconBrush = CategoryIconBrushes[cat]!!
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(CardCorner14)
                                    .background(if (chosen) cat.color.copy(alpha = .15f) else colors.surface)
                                    .then(
                                        if (chosen) Modifier.border(1.5.dp, cat.color, CardCorner14)
                                        else Modifier
                                    )
                                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                                        onSelectCategory(if (chosen) null else cat)
                                    }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(32.dp)
                                            .clip(BadgeCorner8)
                                            .background(iconBrush),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LiquidKitIcon(cat.symbol, Color.White, Modifier.size(17.dp))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    BasicText("$count 项", style = TextStyle(colors.secondary, 11.sp, FontWeight.SemiBold))
                                }
                                BasicText(
                                    cat.title,
                                    style = TextStyle(colors.ink, 14.sp, FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                BasicText(
                                    cat.englishSubtitle,
                                    style = TextStyle(colors.secondary, 11.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                if (showFavoritesOnly && favorites.count == 0) {
                    item(key = "empty-favorites") {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidKitUnavailableView(
                                title = "暂无收藏组件",
                                symbol = LiquidKitSymbol.Star,
                                description = "点开任意组件详情页，点击右上角星标即可加入收藏夹。",
                                colors = colors,
                            )
                        }
                    }
                } else {
                    displayedCategories.forEach { category ->
                        val items = ComponentCatalog.items(category).let { list ->
                            if (showFavoritesOnly) list.filter { favorites.isFavorite(it.route) } else list
                        }
                        if (items.isNotEmpty()) {
                            item(key = "cat-${category.name}") {
                                LiquidKitFormSection(
                                    title = category.title,
                                    footer = category.englishSubtitle,
                                    colors = colors,
                                ) {
                                    items.forEach { entry ->
                                        row(onClick = { onOpenRoute(entry.route) }) {
                                            ComponentRowContent(entry, favorites.isFavorite(entry.route), colors)
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

@Composable
private fun Ios27SpotlightView(
    onScrollMinimize: (Boolean) -> Unit,
    onOpenRoute: (ComponentRoute) -> Unit,
    pageBackdrop: LiquidKitLayerBackdrop,
) {
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    val listState = rememberLazyListState()
    val spotlightItems = remember { ComponentCatalog.allItems.filter { it.badge != null } }

    val isScrolledDeep by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    LaunchedEffect(isScrolledDeep) { onScrollMinimize(isScrolledDeep) }

    LiquidKitNavigationScaffold(
        title = "iOS 27 & Glass",
        modifier = Modifier.fillMaxSize(),
        backdrop = backdrop,
        scrollState = listState,
        titleMode = LiquidKitNavigationTitleMode.Large,
        colors = colors,
        contentWindowInsets = WindowInsets.safeDrawing,
        pageBackdrop = pageBackdrop,
    ) { insets ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection) + 16.dp,
                end = insets.calculateEndPadding(layoutDirection) + 16.dp,
                top = insets.calculateTopPadding() + 6.dp,
                bottom = insets.calculateBottomPadding() + 94.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                val heroBackdrop = rememberLayerBackdrop()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Spacer(
                        Modifier
                            .matchParentSize()
                            .layerBackdrop(heroBackdrop)
                            .background(Ios27HeroBrush),
                    )
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LiquidKitGlassSurface(
                            backdrop = heroBackdrop,
                            variant = LiquidKitVariant.Clear,
                            tint = Color.White.copy(alpha = .14f),
                        ) {
                            BasicText(
                                "iOS 27 SDK · LiquidKit",
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold),
                            )
                        }
                        BasicText("Liquid Glass 与最新交互组件", style = TextStyle(Color.White, 21.sp, FontWeight.Bold))
                        BasicText(
                            "支持 glassEffect、tabs 选择器、reorderContainer、crossFade、原生 WebView 与富文本编辑。",
                            style = TextStyle(Color.White.copy(alpha = .92f), 12.5.sp, lineHeight = 18.sp),
                        )
                    }
                }
            }

            items(spotlightItems, key = { it.route.name }) { item ->
                val iconBrush = CategoryIconBrushes[item.category]!!
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CardCorner16)
                        .background(colors.surface)
                        .clickable(interactionSource = null, indication = null, role = Role.Button) {
                            onOpenRoute(item.route)
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(BadgeCorner12)
                            .background(iconBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        LiquidKitIcon(item.symbol, Color.White, Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(item.title, Modifier.weight(1f, fill = false), style = TextStyle(colors.ink, 16.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.badge?.let { badge ->
                                Box(
                                    Modifier.clip(LiquidKitCapsule).background(item.category.color.copy(alpha = .18f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    BasicText(badge, style = TextStyle(item.category.color, 10.5.sp, FontWeight.Bold), maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        BasicText(item.englishName, style = TextStyle(colors.secondary, 11.5.sp, fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(item.summary, style = TextStyle(colors.secondary, 12.5.sp, lineHeight = 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    LiquidKitIcon(LiquidKitSymbol.Forward, colors.secondary.copy(alpha = .7f), Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchGalleryView(
    query: String,
    onQueryChange: (String) -> Unit,
    onScrollMinimize: (Boolean) -> Unit,
    onOpenRoute: (ComponentRoute) -> Unit,
    pageBackdrop: LiquidKitLayerBackdrop,
) {
    val favorites = LocalFavoritesStore.current
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    val listState = rememberLazyListState()

    val isScrolledDeep by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    LaunchedEffect(isScrolledDeep) { onScrollMinimize(isScrolledDeep) }

    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) ComponentCatalog.allItems
        else ComponentCatalog.allItems.filter { item ->
            item.title.contains(q, ignoreCase = true) ||
                item.englishName.contains(q, ignoreCase = true) ||
                item.summary.contains(q, ignoreCase = true) ||
                item.category.title.contains(q, ignoreCase = true) ||
                item.tags.any { it.contains(q, ignoreCase = true) }
        }
    }

    LiquidKitNavigationScaffold(
        title = "搜索组件",
        modifier = Modifier.fillMaxSize(),
        backdrop = backdrop,
        scrollState = listState,
        titleMode = LiquidKitNavigationTitleMode.Large,
        colors = colors,
        contentWindowInsets = WindowInsets.safeDrawing,
        pageBackdrop = pageBackdrop,
    ) { insets ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection) + 16.dp,
                end = insets.calculateEndPadding(layoutDirection) + 16.dp,
                top = insets.calculateTopPadding() + 4.dp,
                bottom = insets.calculateBottomPadding() + 94.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search-bar") {
                LiquidKitSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "搜索组件名、API 或关键词（如 glass、charts）",
                    colors = colors,
                )
            }
            if (filtered.isEmpty()) {
                item(key = "empty-search") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        LiquidKitUnavailableView(
                            title = "未找到“$query”",
                            symbol = LiquidKitSymbol.Search,
                            description = "请尝试搜索 glass、picker、charts、webview 等关键词。",
                            colors = colors,
                        )
                    }
                }
            } else {
                item(key = "results-card") {
                    LiquidKitFormSection(colors = colors) {
                        filtered.forEach { entry ->
                            row(onClick = { onOpenRoute(entry.route) }) {
                                ComponentRowContent(entry, favorites.isFavorite(entry.route), colors)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRowContent(item: ComponentItem, isFavorite: Boolean, colors: LiquidKitColors) {
    val iconBrush = CategoryIconBrushes[item.category]!!
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(BadgeCorner9)
                .background(iconBrush),
            contentAlignment = Alignment.Center,
        ) {
            LiquidKitIcon(item.symbol, Color.White, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    item.title,
                    Modifier.weight(1f, fill = false),
                    style = TextStyle(colors.ink, 15.5.sp, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isFavorite) {
                    LiquidKitIcon(LiquidKitSymbol.StarFilled, Color(0xffffcc00), Modifier.size(12.dp))
                }
                item.badge?.let { badge ->
                    Box(
                        Modifier.clip(LiquidKitCapsule).background(item.category.color.copy(alpha = .15f))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    ) {
                        BasicText(badge, style = TextStyle(item.category.color, 10.sp, FontWeight.Bold), maxLines = 1, softWrap = false)
                    }
                }
            }
            BasicText(
                item.englishName,
                style = TextStyle(colors.secondary, 11.5.sp, fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                item.summary,
                style = TextStyle(colors.secondary, 12.sp, lineHeight = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LiquidKitIcon(LiquidKitSymbol.Forward, colors.secondary.copy(alpha = .7f), Modifier.size(13.dp))
    }
}
