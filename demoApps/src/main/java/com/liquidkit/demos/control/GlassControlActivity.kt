package com.liquidkit.demos.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.components.enableLiquidEdgeToEdge
import io.liquidkit.*
import kotlin.math.roundToInt

private val HomeAccent = Color(0xffff9500)
private val SceneBannerBrush = Brush.linearGradient(listOf(Color(0xffff9500), Color(0xfff54d73), Color(0xff5856d6)))
private val CardShape18 = RoundedCornerShape(18.dp)

private data class SceneSpec(val name: String, val symbol: LiquidKitSymbol, val color: Color)

private val HomeScenes = listOf(
    SceneSpec("专注", LiquidKitSymbol.Lightbulb, Color(0xffff9500)),
    SceneSpec("影院", LiquidKitSymbol.Sparkles, Color(0xffaf52de)),
    SceneSpec("睡眠", LiquidKitSymbol.Moon, Color(0xff5856d6)),
    SceneSpec("离家", LiquidKitSymbol.Bell, Color(0xffff3b30)),
)

class GlassControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            LiquidKitRoot(dark = dark, edgeToEdgeSystemBars = true) {
                GlassControlScreen()
            }
        }
    }
}

@Composable
private fun GlassControlScreen() {
    val colors = liquidKitColors()
    val backdrop = LocalLiquidKitBackdrop.current
    var activeScene by rememberSaveable { mutableStateOf("专注") }
    var room by rememberSaveable { mutableStateOf("客厅") }
    var mainLightOn by rememberSaveable { mutableStateOf(true) }
    var brightness by rememberSaveable { mutableFloatStateOf(0.72f) }
    var acTemp by rememberSaveable { mutableIntStateOf(23) }
    var fanMode by rememberSaveable { mutableStateOf("自动") }
    var ambientColor by remember { mutableStateOf(HomeAccent) }
    var scheduleDate by remember { mutableStateOf(LiquidKitCalendarDate(2026, 11, 25)) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showAwayAlert by rememberSaveable { mutableStateOf(false) }

    val rooms = remember { listOf("客厅", "卧室", "书房") }
    val fanModes = remember { listOf("自动", "低速", "中速", "强劲") }
    val brightnessTicks = remember { listOf(0f, .25f, .5f, .75f, 1f) }
    val listState = rememberLazyListState()

    LiquidKitNavigationScaffold(
        title = "智能中枢",
        modifier = Modifier.fillMaxSize().background(colors.background),
        backdrop = backdrop,
        scrollState = listState,
        titleMode = LiquidKitNavigationTitleMode.Large,
        colors = colors,
        contentWindowInsets = LiquidKitSafeAreaDefaults.windowInsets,
        trailing = { glass ->
            LiquidKitToolbarGroup(Modifier.height(44.dp), backdrop = glass) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiquidKitToolbarButton("离家确认", { showAwayAlert = true }, Modifier.size(44.dp)) {
                        LiquidKitIcon(LiquidKitSymbol.Bell, HomeAccent, Modifier.size(19.dp))
                    }
                    LiquidKitToolbarButton("添加设备", { showAddSheet = true }, Modifier.size(44.dp)) {
                        LiquidKitIcon(LiquidKitSymbol.Add, HomeAccent, Modifier.size(19.dp))
                    }
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
                top = insets.calculateTopPadding() + 4.dp,
                bottom = insets.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Scene banner
            item(key = "scene-banner") {
                val bannerBackdrop = rememberLayerBackdrop()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(144.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Spacer(Modifier.matchParentSize().layerBackdrop(bannerBackdrop).background(SceneBannerBrush))
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                BasicText("$room · 当前场景", style = TextStyle(Color.White.copy(alpha = .86f), 12.sp, FontWeight.SemiBold))
                                BasicText("${activeScene}模式", style = TextStyle(Color.White, 22.sp, FontWeight.Bold))
                            }
                            LiquidKitGlassSurface(
                                backdrop = bannerBackdrop,
                                variant = LiquidKitVariant.Clear,
                                tint = Color.White.copy(alpha = .12f),
                            ) {
                                BasicText(
                                    "${acTemp}°C · ${(brightness * 100).roundToInt()}%",
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                                )
                            }
                        }
                        LiquidKitGlassEffectContainer(backdrop = bannerBackdrop, spacing = 8.dp) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeScenes.forEach { item ->
                                    val selected = activeScene == item.name
                                    LiquidKitActionButton(
                                        label = item.name,
                                        onClick = {
                                            if (item.name == "离家") showAwayAlert = true
                                            else activeScene = item.name
                                        },
                                        tint = if (selected) item.color else Color.White,
                                        style = if (selected) LiquidKitActionStyle.GlassProminent else LiquidKitActionStyle.Glass,
                                        backdrop = bannerBackdrop,
                                        leadingSymbol = item.symbol,
                                        controlSize = LiquidKitControlSize.Small,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Room picker
            item(key = "room") {
                LiquidKitSegmentedPicker(
                    options = rooms,
                    selected = room,
                    select = { room = it },
                    colors = colors,
                )
            }

            // Lighting card
            item(key = "lighting") {
                Column(
                    Modifier.fillMaxWidth().clip(CardShape18).background(colors.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText("灯光与氛围", style = TextStyle(colors.secondary, 13.sp, FontWeight.SemiBold))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("主灯开关", Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
                        LiquidKitToggle(selected = { mainLightOn }, onSelect = { mainLightOn = it }, tint = HomeAccent)
                    }
                    ControlDivider(colors)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText("环境光亮度", style = TextStyle(colors.ink, 17.sp))
                            BasicText(
                                "${(brightness * 100).roundToInt()}%",
                                style = TextStyle(colors.secondary, 15.sp, fontFamily = FontFamily.Monospace),
                            )
                        }
                        LiquidKitSlider(
                            value = { brightness },
                            onValueChange = { brightness = it },
                            tint = HomeAccent,
                        )
                    }
                    ControlDivider(colors)
                    LiquidKitColorPicker(
                        label = "氛围灯色",
                        color = ambientColor,
                        onColorChange = { ambientColor = it },
                        tint = HomeAccent,
                        colors = colors,
                    )
                }
            }

            // Climate & schedule card
            item(key = "climate") {
                Column(
                    Modifier.fillMaxWidth().clip(CardShape18).background(colors.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText("恒温与定时", style = TextStyle(colors.secondary, 13.sp, FontWeight.SemiBold))
                    LiquidKitStepper(
                        label = "空调温度 ${acTemp}°C",
                        value = acTemp,
                        onChange = { acTemp = it },
                        range = 16..30,
                        tint = HomeAccent,
                        colors = colors,
                        showValue = false,
                    )
                    ControlDivider(colors)
                    LiquidKitMenuPicker(
                        label = "风速模式",
                        options = fanModes,
                        selected = fanMode,
                        onSelect = { fanMode = it },
                        tint = HomeAccent,
                        colors = colors,
                    )
                    ControlDivider(colors)
                    LiquidKitDatePicker(
                        label = "定时开启",
                        date = scheduleDate,
                        onDateChange = { scheduleDate = it },
                        tint = HomeAccent,
                        colors = colors,
                    )
                }
            }

            // Energy card
            item(key = "energy") {
                Column(
                    Modifier.fillMaxWidth().clip(CardShape18).background(colors.surface).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BasicText("今日能耗", style = TextStyle(colors.secondary, 13.sp, FontWeight.SemiBold))
                        BasicText("6.8 kWh · 优", style = TextStyle(Color(0xff34c759), 15.sp, FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                    }
                    LiquidKitLinearGauge(
                        value = 0.42f,
                        tint = Color(0xff34c759),
                        valueLabel = "",
                        trackHeight = 5.dp,
                    )
                }
            }
        }
    }

    if (showAwayAlert) {
        LiquidKitAlert(
            title = "启用离家布防？",
            message = "将关闭全部客厅与书房灯光，并切换空调至节能待机模式。",
            actions = listOf(
                LiquidKitAlertAction("取消", LiquidKitAlertRole.Cancel) { showAwayAlert = false },
                LiquidKitAlertAction("确认离家", LiquidKitAlertRole.Destructive) {
                    activeScene = "离家"
                    mainLightOn = false
                    showAwayAlert = false
                },
            ),
            onDismissRequest = { showAwayAlert = false },
            tint = HomeAccent,
            colors = colors,
        )
    }

    if (showAddSheet) {
        LiquidKitSheet(
            onDismissRequest = { showAddSheet = false },
            title = "添加设备",
            doneLabel = "完成",
            tint = HomeAccent,
            colors = colors,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiquidKitIcon(LiquidKitSymbol.Lightbulb, HomeAccent, Modifier.size(44.dp))
                BasicText("发现附近 Matter 设备", style = TextStyle(colors.ink, 18.sp, FontWeight.SemiBold))
                BasicText(
                    "将手机靠近智能吸顶灯或温湿度传感器即可快速配网。",
                    style = TextStyle(colors.secondary, 14.sp, lineHeight = 20.sp),
                )
            }
        }
    }
}

@Composable
private fun ControlDivider(colors: LiquidKitColors) {
    Spacer(Modifier.fillMaxWidth().height(.5.dp).background(colors.separator.copy(alpha = .55f)))
}
