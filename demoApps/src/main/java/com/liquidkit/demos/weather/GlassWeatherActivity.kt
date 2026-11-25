package com.liquidkit.demos.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.components.enableLiquidEdgeToEdge
import com.kyant.backdrop.components.liquidPopoverAnchor
import io.liquidkit.*
import kotlin.math.roundToInt

private data class WeatherCity(
    val id: String,
    val name: String,
    val tempC: Int,
    val highC: Int,
    val lowC: Int,
    val condition: String,
    val aqi: Int,
    val humidity: Float,
    val skyLight: List<Color>,
    val skyDark: List<Color>,
    val hourly: List<Triple<String, LiquidKitSymbol, Int>>,
    val daily: List<Quad<String, LiquidKitSymbol, Int, Int>>,
)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private val SampleCities = listOf(
    WeatherCity(
        id = "shanghai", name = "上海", tempC = 22, highC = 25, lowC = 16, condition = "晴转多云", aqi = 32, humidity = 0.54f,
        skyLight = listOf(Color(0xff1759ba), Color(0xff388fe3), Color(0xff69b3f0)),
        skyDark = listOf(Color(0xff0a1a33), Color(0xff143361), Color(0xff1f457a)),
        hourly = listOf(
            Triple("现在", LiquidKitSymbol.Sun, 22), Triple("14时", LiquidKitSymbol.Sun, 24), Triple("15时", LiquidKitSymbol.Sun, 25),
            Triple("16时", LiquidKitSymbol.Sparkles, 24), Triple("17时", LiquidKitSymbol.Moon, 21), Triple("18时", LiquidKitSymbol.Moon, 19),
        ),
        daily = listOf(
            Quad("今天", LiquidKitSymbol.Sun, 16, 25), Quad("周四", LiquidKitSymbol.Sun, 17, 26), Quad("周五", LiquidKitSymbol.Drop, 15, 22),
            Quad("周六", LiquidKitSymbol.Sun, 14, 23), Quad("周日", LiquidKitSymbol.Sun, 16, 24),
        ),
    ),
    WeatherCity(
        id = "tokyo", name = "东京", tempC = 19, highC = 21, lowC = 13, condition = "晚霞微风", aqi = 24, humidity = 0.48f,
        skyLight = listOf(Color(0xff45297a), Color(0xffba457a), Color(0xfff08a4a)),
        skyDark = listOf(Color(0xff1f123d), Color(0xff521f47), Color(0xff703329)),
        hourly = listOf(
            Triple("现在", LiquidKitSymbol.Sun, 19), Triple("14时", LiquidKitSymbol.Sun, 21), Triple("15时", LiquidKitSymbol.Sparkles, 20),
            Triple("16时", LiquidKitSymbol.Moon, 18), Triple("17时", LiquidKitSymbol.Moon, 16), Triple("18时", LiquidKitSymbol.Moon, 15),
        ),
        daily = listOf(
            Quad("今天", LiquidKitSymbol.Sun, 13, 21), Quad("周四", LiquidKitSymbol.Drop, 12, 18), Quad("周五", LiquidKitSymbol.Sun, 13, 20),
            Quad("周六", LiquidKitSymbol.Sun, 14, 22), Quad("周日", LiquidKitSymbol.Sparkles, 13, 20),
        ),
    ),
    WeatherCity(
        id = "sf", name = "旧金山", tempC = 16, highC = 18, lowC = 11, condition = "海雾清朗", aqi = 18, humidity = 0.68f,
        skyLight = listOf(Color(0xff124f6e), Color(0xff26859c), Color(0xff5cb3c2)),
        skyDark = listOf(Color(0xff0a2133), Color(0xff143d4f), Color(0xff1f5466)),
        hourly = listOf(
            Triple("现在", LiquidKitSymbol.Drop, 16), Triple("14时", LiquidKitSymbol.Sun, 18), Triple("15时", LiquidKitSymbol.Sun, 18),
            Triple("16时", LiquidKitSymbol.Sun, 17), Triple("17时", LiquidKitSymbol.Moon, 14), Triple("18时", LiquidKitSymbol.Moon, 13),
        ),
        daily = listOf(
            Quad("今天", LiquidKitSymbol.Drop, 11, 18), Quad("周四", LiquidKitSymbol.Sun, 12, 19), Quad("周五", LiquidKitSymbol.Sun, 11, 18),
            Quad("周六", LiquidKitSymbol.Drop, 10, 16), Quad("周日", LiquidKitSymbol.Sun, 12, 19),
        ),
    ),
)

private val TempRangeBrush = Brush.horizontalGradient(listOf(Color(0xff5ac8fa), Color(0xffffcc00), Color(0xffff9500)))
private val CardShape22 = RoundedCornerShape(22.dp)
private val CardShape20 = RoundedCornerShape(20.dp)

class GlassWeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLiquidEdgeToEdge(dark = true)
        setContent {
            val systemDark = isSystemInDarkTheme()
            // Sky background is vibrant/deep so status & gesture bar icons stay crisp white on both Light and Dark themes.
            LiquidKitRoot(dark = true, edgeToEdgeSystemBars = true) {
                GlassWeatherScreen(systemDark = systemDark)
            }
        }
    }
}

@Composable
private fun GlassWeatherScreen(systemDark: Boolean) {
    var cityIndex by rememberSaveable { mutableIntStateOf(0) }
    var useFahrenheit by rememberSaveable { mutableStateOf(false) }
    var showUnits by rememberSaveable { mutableStateOf(false) }
    val unitAnchor = remember { mutableStateOf<Rect?>(null) }

    val city = SampleCities[cityIndex]
    val skyBrush = remember(city.id, systemDark) {
        Brush.verticalGradient(if (systemDark) city.skyDark else city.skyLight)
    }
    val chartLabels = remember(city.id) { city.hourly.map { it.first } }
    val chartValues = remember(city.id) { city.hourly.map { it.third.toFloat() } }
    val aqiBrush = remember { Brush.horizontalGradient(listOf(Color(0xff34c759), Color(0xffffcc00), Color(0xffff9500))) }
    fun fmt(c: Int): String {
        val v = if (useFahrenheit) (c * 9f / 5f + 32f).roundToInt() else c
        return "${v}°"
    }

    LiquidKitAppScaffold(
        modifier = Modifier.fillMaxSize(),
        edgeToEdgeSystemBars = false,
        bottomBar = { sceneBackdrop ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LiquidKitGlassEffectContainer(backdrop = sceneBackdrop, spacing = 10.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SampleCities.forEachIndexed { idx, c ->
                            LiquidKitActionButton(
                                label = c.name,
                                onClick = { cityIndex = idx },
                                tint = if (idx == cityIndex) Color.White.copy(alpha = .28f) else Color.White,
                                style = if (idx == cityIndex) LiquidKitActionStyle.GlassProminent else LiquidKitActionStyle.Glass,
                                backdrop = sceneBackdrop,
                                controlSize = LiquidKitControlSize.Small,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                LiquidKitGlassSurface(
                    backdrop = sceneBackdrop,
                    modifier = Modifier.size(44.dp).liquidPopoverAnchor(unitAnchor),
                    shape = LiquidKitCapsule,
                    variant = LiquidKitVariant.Clear,
                    interactive = true,
                    onClick = { showUnits = !showUnits },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BasicText(
                            if (useFahrenheit) "°F" else "°C",
                            style = TextStyle(Color.White, 15.sp, FontWeight.Bold),
                        )
                    }
                }
            }
        },
    ) { skyBackdrop, safePadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize()) {
            Spacer(Modifier.matchParentSize().layerBackdrop(skyBackdrop).background(skyBrush))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = safePadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = safePadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = safePadding.calculateTopPadding() + 18.dp,
                        bottom = safePadding.calculateBottomPadding() + 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(city.name, style = TextStyle(Color.White, 34.sp, FontWeight.Medium))
                    BasicText(fmt(city.tempC), style = TextStyle(Color.White, 76.sp, FontWeight.Thin))
                    BasicText(city.condition, style = TextStyle(Color.White.copy(alpha = .92f), 17.sp, FontWeight.Medium))
                    BasicText(
                        "最高 ${fmt(city.highC)}   最低 ${fmt(city.lowC)}",
                        style = TextStyle(Color.White.copy(alpha = .84f), 15.sp, FontWeight.Medium),
                    )
                }

                // Hourly forecast
                WeatherGlassCard(skyBackdrop, CardShape22) {
                    WeatherSectionHeader("每小时预报", LiquidKitSymbol.Clock)
                    Row(Modifier.fillMaxWidth()) {
                        city.hourly.forEach { slot ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BasicText(slot.first, style = TextStyle(Color.White.copy(alpha = .9f), 12.sp, FontWeight.Medium))
                                LiquidKitIcon(
                                    slot.second,
                                    if (slot.second == LiquidKitSymbol.Sun) Color(0xffffd60a) else Color.White,
                                    Modifier.size(19.dp),
                                )
                                BasicText(fmt(slot.third), style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold))
                            }
                        }
                    }
                }

                // Temperature trend chart
                WeatherGlassCard(skyBackdrop, CardShape22) {
                    WeatherSectionHeader("温度趋势", LiquidKitSymbol.Chart)
                    LiquidKitLineAreaChart(
                        labels = chartLabels,
                        values = chartValues,
                        modifier = Modifier.fillMaxWidth(),
                        lineColor = Color.White,
                        chartHeight = 90.dp,
                        showPoints = false,
                        showGrid = false,
                        labelColor = Color.White.copy(alpha = .82f),
                        yDomain = 10f..28f,
                    )
                }

                // AQI + Humidity row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WeatherGlassCard(skyBackdrop, CardShape20, Modifier.weight(1f)) {
                        WeatherSectionHeader("空气质量", LiquidKitSymbol.Sparkles)
                        BasicText("${city.aqi}", style = TextStyle(Color.White, 28.sp, FontWeight.Bold))
                        BasicText("优 · 空气清新", style = TextStyle(Color.White.copy(alpha = .88f), 13.sp, FontWeight.Medium))
                        LiquidKitLinearGauge(
                            value = (city.aqi / 150f).coerceIn(0f, 1f),
                            tint = Color(0xff34c759),
                            valueLabel = "",
                            brush = aqiBrush,
                            trackHeight = 5.dp,
                            trackColor = Color.White.copy(alpha = .26f),
                        )
                    }
                    WeatherGlassCard(skyBackdrop, CardShape20, Modifier.weight(1f)) {
                        WeatherSectionHeader("相对湿度", LiquidKitSymbol.Drop)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                BasicText("${(city.humidity * 100).roundToInt()}%", style = TextStyle(Color.White, 28.sp, FontWeight.Bold))
                                BasicText("露点 12°", style = TextStyle(Color.White.copy(alpha = .84f), 13.sp))
                            }
                            LiquidKitGauge(
                                value = city.humidity,
                                tint = Color(0xff5ac8fa),
                                style = LiquidKitGaugeStyle.CircularCapacity,
                                trackColor = Color.White.copy(alpha = .24f),
                            )
                        }
                    }
                }

                // 5-Day Forecast
                WeatherGlassCard(skyBackdrop, CardShape22) {
                    WeatherSectionHeader("5 日预报", LiquidKitSymbol.Calendar)
                    city.daily.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(row.a, Modifier.width(44.dp), style = TextStyle(Color.White, 15.sp, FontWeight.Medium))
                            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                                LiquidKitIcon(
                                    row.b,
                                    if (row.b == LiquidKitSymbol.Sun) Color(0xffffd60a) else Color.White,
                                    Modifier.size(17.dp),
                                )
                            }
                            BasicText(
                                fmt(row.c),
                                Modifier.width(34.dp),
                                style = TextStyle(Color.White.copy(alpha = .76f), 15.sp, fontFamily = FontFamily.Monospace),
                            )
                            Box(Modifier.weight(1f).height(5.dp).clip(LiquidKitCapsule).background(TempRangeBrush))
                            BasicText(
                                fmt(row.d),
                                Modifier.width(34.dp),
                                style = TextStyle(Color.White, 15.sp, FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            }
            val topInset = safePadding.calculateTopPadding()
            if (topInset > 0.dp) {
                val skyTop = if (systemDark) city.skyDark.first() else city.skyLight.first()
                Spacer(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topInset + 18.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to skyTop,
                                0.68f to skyTop.copy(alpha = .92f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
    }

    if (showUnits) {
        LiquidKitPopover(
            onDismissRequest = { showUnits = false },
            anchorBoundsInRoot = unitAnchor.value,
        ) {
            Column(Modifier.width(220.dp).padding(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("温标设置", style = TextStyle(Color.White, 17.sp, FontWeight.SemiBold))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("使用华氏度 (°F)", Modifier.weight(1f), style = TextStyle(Color.White, 15.sp))
                    LiquidKitToggle(selected = { useFahrenheit }, onSelect = { useFahrenheit = it }, tint = Color(0xff5ac8fa))
                }
            }
        }
    }
}

@Composable
private fun WeatherGlassCard(
    backdrop: Backdrop,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidKitGlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        variant = LiquidKitVariant.Clear,
        tint = Color.White.copy(alpha = .08f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun WeatherSectionHeader(title: String, symbol: LiquidKitSymbol) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        LiquidKitIcon(symbol, Color.White.copy(alpha = .78f), Modifier.size(13.dp))
        BasicText(title, style = TextStyle(Color.White.copy(alpha = .78f), 12.sp, FontWeight.SemiBold))
    }
}
