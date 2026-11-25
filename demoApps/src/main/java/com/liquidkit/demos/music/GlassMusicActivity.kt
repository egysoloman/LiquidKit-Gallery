package com.liquidkit.demos.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.components.enableLiquidEdgeToEdge
import io.liquidkit.*

private data class AlbumItem(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val colors: List<Color>,
    val symbol: LiquidKitSymbol,
    val duration: String,
)

private val MusicAccent = Color(0xffff2d55)

private val SampleAlbums = listOf(
    AlbumItem("aurora", "极光协奏", "林屿 · 空间音频", "精选", listOf(Color(0xff00c7be), Color(0xff5856d6)), LiquidKitSymbol.Sparkles, "4:18"),
    AlbumItem("ocean", "深海回声", "青鲸乐队", "精选", listOf(Color(0xff007aff), Color(0xff30b0c7)), LiquidKitSymbol.Drop, "3:52"),
    AlbumItem("tokyo", "东京雨夜", "Sora Takahashi", "歌单", listOf(Color(0xffff2d55), Color(0xffaf52de)), LiquidKitSymbol.Moon, "5:04"),
    AlbumItem("drift", "失重漫游", "Nova Collective", "歌单", listOf(Color(0xffff9500), Color(0xffff3b30)), LiquidKitSymbol.Sun, "3:41"),
    AlbumItem("amber", "琥珀爵士", "Quartet Noir", "无损", listOf(Color(0xffd98529), Color(0xff7a3d9e)), LiquidKitSymbol.StarFilled, "6:12"),
    AlbumItem("orbit", "星际航线", "Kairo Synth", "无损", listOf(Color(0xff34c759), Color(0xff007aff)), LiquidKitSymbol.Globe, "4:45"),
)

private val AlbumBrushes: Map<String, Brush> = SampleAlbums.associate { it.id to Brush.linearGradient(it.colors) }
private val HeroMeshColors = listOf(
    Color(0xffff2d55), Color(0xffaf52de), Color(0xff5856d6),
    Color(0xffff9500), Color(0xfff5407a), Color(0xff007aff),
    Color(0xff291f52), Color(0xff522e94), Color(0xff00c7be),
)

private enum class MusicTab { Listen, Browse, Library, Search }

class GlassMusicActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            LiquidKitRoot(dark = dark, edgeToEdgeSystemBars = true) {
                GlassMusicScreen()
            }
        }
    }
}

@Composable
private fun GlassMusicScreen() {
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    var activeTab by rememberSaveable { mutableStateOf(MusicTab.Listen) }
    var filter by rememberSaveable { mutableStateOf("精选") }
    var currentId by rememberSaveable { mutableStateOf(SampleAlbums[0].id) }
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var progress by rememberSaveable { mutableFloatStateOf(0.38f) }
    var spatialAudio by rememberSaveable { mutableStateOf(true) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }

    val currentTrack = remember(currentId) { SampleAlbums.firstOrNull { it.id == currentId } ?: SampleAlbums[0] }
    val filters = remember { listOf("精选", "歌单", "无损") }
    val filteredAlbums = remember(filter) {
        if (filter == "精选") SampleAlbums
        else SampleAlbums.filter { it.genre == filter } + SampleAlbums.filter { it.genre != filter }
    }
    val tabs = remember {
        listOf(
            LiquidKitTab(MusicTab.Listen, "立即收听", LiquidKitSymbol.Play),
            LiquidKitTab(MusicTab.Browse, "浏览", LiquidKitSymbol.Grid),
            LiquidKitTab(MusicTab.Library, "资料库", LiquidKitSymbol.StarFilled),
        )
    }
    val listState = rememberLazyListState()
    var tabBarMinimized by remember { mutableStateOf(false) }
    val isScrolledDeep by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
    LaunchedEffect(isScrolledDeep) { tabBarMinimized = isScrolledDeep }
    val title = when (activeTab) {
        MusicTab.Listen -> "流光音乐"
        MusicTab.Browse -> "浏览"
        MusicTab.Library -> "资料库"
        MusicTab.Search -> "搜索音乐"
    }

    LiquidKitAppScaffold(
        modifier = Modifier.fillMaxSize(),
        edgeToEdgeSystemBars = false,
        bottomBar = { tabSceneBackdrop ->
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniPlayerBar(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    backdrop = tabSceneBackdrop,
                    colors = colors,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onOpen = { showPlayer = true },
                )
                LiquidKitTabBar(
                    tabs = tabs,
                    selected = if (activeTab == MusicTab.Search) MusicTab.Listen else activeTab,
                    onSelect = {
                        activeTab = it
                        tabBarMinimized = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backdrop = tabSceneBackdrop,
                    tint = MusicAccent,
                    minimized = tabBarMinimized,
                    onExpandFromMinimized = { tabBarMinimized = false },
                    searchAction = {
                        activeTab = MusicTab.Search
                        tabBarMinimized = false
                    },
                    searchLabel = "搜索音乐",
                    searchSelected = activeTab == MusicTab.Search,
                )
            }
        },
    ) { tabSceneBackdrop, _ ->
        LiquidKitNavigationScaffold(
            title = title,
            modifier = Modifier.fillMaxSize(),
            backdrop = backdrop,
            scrollState = listState,
            titleMode = LiquidKitNavigationTitleMode.Large,
            colors = colors,
            contentWindowInsets = LiquidKitSafeAreaDefaults.windowInsets,
            pageBackdrop = tabSceneBackdrop,
            trailing = { glass ->
                LiquidKitToolbarGroup(Modifier.size(44.dp), backdrop = glass) {
                    LiquidKitToolbarButton("均衡器与播放器", { showPlayer = true }, Modifier.fillMaxSize()) {
                        LiquidKitIcon(LiquidKitSymbol.SliderHorizontal, MusicAccent, Modifier.size(19.dp))
                    }
                }
            },
        ) { insets ->
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = insets.calculateStartPadding(layoutDirection) + 16.dp,
                    end = insets.calculateEndPadding(layoutDirection) + 16.dp,
                    top = insets.calculateTopPadding() + 6.dp,
                    bottom = insets.calculateBottomPadding() + 158.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (activeTab == MusicTab.Search) {
                    item(key = "search") {
                        LiquidKitSearchField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = "艺人、专辑与无损歌单",
                            colors = colors,
                        )
                    }
                }

                item(key = "filter") {
                    LiquidKitSegmentedPicker(
                        options = filters,
                        selected = filter,
                        select = { filter = it },
                        colors = colors,
                    )
                }

                item(key = "hero") {
                    val heroBackdrop = rememberLayerBackdrop()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        LiquidKitMeshGradient(
                            width = 3,
                            height = 3,
                            colors = HeroMeshColors,
                            modifier = Modifier.matchParentSize().layerBackdrop(heroBackdrop),
                        )
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LiquidKitGlassSurface(
                                backdrop = heroBackdrop,
                                variant = LiquidKitVariant.Clear,
                                tint = Color.White.copy(alpha = .12f),
                            ) {
                                BasicText(
                                    "空间音频精选 · 24-bit ALAC",
                                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = TextStyle(Color.White.copy(alpha = .94f), 12.sp, FontWeight.SemiBold),
                                )
                            }
                            BasicText("午夜霓虹 · 2026", style = TextStyle(Color.White, 22.sp, FontWeight.Bold))
                            BasicText(
                                "沉浸杜比全景声与高解析母带精选，呈现通透玻璃质感声场。",
                                style = TextStyle(Color.White.copy(alpha = .88f), 13.sp, lineHeight = 18.sp),
                            )
                            LiquidKitGlassEffectContainer(backdrop = heroBackdrop, spacing = 10.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LiquidKitActionButton(
                                        label = "立即播放",
                                        onClick = { currentId = SampleAlbums[0].id; isPlaying = true },
                                        tint = MusicAccent,
                                        style = LiquidKitActionStyle.GlassProminent,
                                        backdrop = heroBackdrop,
                                        leadingSymbol = LiquidKitSymbol.Play,
                                    )
                                    LiquidKitActionButton(
                                        label = "随机播放",
                                        onClick = { currentId = SampleAlbums.random().id; isPlaying = true },
                                        tint = Color.White,
                                        style = LiquidKitActionStyle.Glass,
                                        backdrop = heroBackdrop,
                                        leadingSymbol = LiquidKitSymbol.Sparkles,
                                    )
                                }
                            }
                        }
                    }
                }

                val rows = filteredAlbums.chunked(2)
                items(rows.size, key = { rows[it].first().id }) { rowIndex ->
                    val pair = rows[rowIndex]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (album in pair) {
                            AlbumCard(
                                album = album,
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = { currentId = album.id; isPlaying = true },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showPlayer) {
        LiquidKitSheet(
            onDismissRequest = { showPlayer = false },
            title = "正在播放",
            doneLabel = "完成",
            tint = MusicAccent,
            colors = colors,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AlbumBrushes[currentTrack.id]!!),
                contentAlignment = Alignment.Center,
            ) {
                LiquidKitIcon(currentTrack.symbol, Color.White, Modifier.size(56.dp))
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(currentTrack.title, style = TextStyle(colors.ink, 20.sp, FontWeight.Bold))
                BasicText(currentTrack.artist, style = TextStyle(colors.secondary, 14.sp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidKitSlider(
                    value = { progress },
                    onValueChange = { progress = it },
                    tint = MusicAccent,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("1:38", style = TextStyle(colors.secondary, 12.sp, fontFamily = FontFamily.Monospace))
                    BasicText(currentTrack.duration, style = TextStyle(colors.secondary, 12.sp, fontFamily = FontFamily.Monospace))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("空间音频头部追踪", Modifier.weight(1f), style = TextStyle(colors.ink, 16.sp))
                LiquidKitToggle(selected = { spatialAudio }, onSelect = { spatialAudio = it }, tint = MusicAccent)
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumItem,
    colors: LiquidKitColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(126.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AlbumBrushes[album.id]!!),
        ) {
            LiquidKitIcon(album.symbol, Color.White.copy(alpha = .92f), Modifier.size(32.dp).align(Alignment.Center))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(LiquidKitCapsule)
                    .background(Color.Black.copy(alpha = .28f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                BasicText(album.duration, style = TextStyle(Color.White, 11.sp, FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
            }
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(album.title, style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(album.artist, style = TextStyle(colors.secondary, 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniPlayerBar(
    track: AlbumItem,
    isPlaying: Boolean,
    backdrop: com.kyant.backdrop.Backdrop,
    colors: LiquidKitColors,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
) {
    LiquidKitGlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        shape = LiquidKitCapsule,
        variant = LiquidKitVariant.Regular,
        interactive = true,
        onClick = onOpen,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(AlbumBrushes[track.id]!!),
                contentAlignment = Alignment.Center,
            ) {
                LiquidKitIcon(track.symbol, Color.White, Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText(track.title, style = TextStyle(colors.ink, 15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(track.artist, style = TextStyle(colors.secondary, 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(LiquidKitCapsule)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                LiquidKitIcon(
                    if (isPlaying) LiquidKitSymbol.Sparkles else LiquidKitSymbol.Play,
                    MusicAccent,
                    Modifier.size(17.dp),
                )
            }
        }
    }
}
