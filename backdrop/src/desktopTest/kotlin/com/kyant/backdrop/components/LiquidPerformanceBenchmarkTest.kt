package com.kyant.backdrop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class LiquidPerformanceBenchmarkTest {
    private data class Stats(
        val count: Int,
        val meanMs: Double,
        val p50Ms: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val maxMs: Double,
    ) {
        fun format(label: String): String =
            "PERF_BENCH $label: count=$count mean=${f(meanMs)}ms p50=${f(p50Ms)}ms p95=${f(p95Ms)}ms p99=${f(p99Ms)}ms max=${f(maxMs)}ms"

        private fun f(v: Double): String = ((v * 1000.0).roundToInt() / 1000.0).toString()
    }

    private fun measureSamplesMs(warmup: Int, iterations: Int, block: (Int) -> Unit): Stats {
        for (i in 0 until warmup) block(i)
        val samples = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val t0 = System.nanoTime()
            block(i)
            samples[i] = (System.nanoTime() - t0) / 1_000_000.0
        }
        val sorted = samples.sortedArray()
        fun pct(p: Double): Double = sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)]
        return Stats(
            count = iterations,
            meanMs = samples.average(),
            p50Ms = pct(0.50),
            p95Ms = pct(0.95),
            p99Ms = pct(0.99),
            maxMs = sorted.last(),
        )
    }

    @Test
    fun benchmarkGlassCapsulesOutlineConstruction() {
        val density = Density(3f)
        val singleShape = GlassCapsuleShape()
        // Populate cache once with varying sizes, then measure cold vs cached
        val coldSingle = measureSamplesMs(warmup = 5, iterations = 30) { i ->
            singleShape.createOutline(Size(180f + i * 7f, 120f + (i % 5) * 3f), LayoutDirection.Ltr, density)
        }
        singleShape.createOutline(Size(360f, 144f), LayoutDirection.Ltr, density)
        val cachedSingle = measureSamplesMs(warmup = 20, iterations = 500) {
            singleShape.createOutline(Size(360f, 144f), LayoutDirection.Ltr, density)
        }

        val twoLobes = GlassCapsuleShape(
            first = Rect(20f, 20f, 160f, 160f),
            second = Rect(145f, 20f, 285f, 160f),
            spacing = 36f,
        )
        val twoStats = measureSamplesMs(warmup = 20, iterations = 200) {
            twoLobes.createOutline(Size(340f, 180f), LayoutDirection.Ltr, density)
        }

        val threeLobes = GlassCapsulesShape(
            rects = listOf(
                Rect(10f, 20f, 154f, 164f),
                Rect(142f, 20f, 286f, 164f),
                Rect(274f, 20f, 418f, 164f),
            ),
            spacing = 36f,
        )
        val threeStats = measureSamplesMs(warmup = 20, iterations = 200) {
            threeLobes.createOutline(Size(440f, 184f), LayoutDirection.Ltr, density)
        }

        val fourLobes = GlassCapsulesShape(
            rects = listOf(
                Rect(10f, 20f, 154f, 164f),
                Rect(142f, 20f, 286f, 164f),
                Rect(274f, 20f, 418f, 164f),
                Rect(406f, 20f, 550f, 164f),
            ),
            spacing = 36f,
        )
        val fourStats = measureSamplesMs(warmup = 20, iterations = 200) {
            fourLobes.createOutline(Size(570f, 184f), LayoutDirection.Ltr, density)
        }

        println(coldSingle.format("Capsule.outline(cold)"))
        println(cachedSingle.format("Capsule.outline(cached)"))
        println(twoStats.format("GlassCapsuleShape(2_lobes)"))
        println(threeStats.format("GlassCapsulesShape(3_lobes)"))
        println(fourStats.format("GlassCapsulesShape(4_lobes)"))

        assertTrue("Cached single capsule outline too slow: ${cachedSingle.meanMs}ms", cachedSingle.meanMs < 0.25)
        assertTrue("4-lobe GlassCapsulesShape outline regression: ${fourStats.meanMs}ms", fourStats.meanMs < 5.0)
    }

    @Test
    fun verifyShaderCacheReuseAndLookupOverhead() {
        val shaderA = SharedRenderEffectShaderCache.obtainRuntimeShader("LensBench", RoundedRectRefractionShaderString)
        val shaderB = SharedRenderEffectShaderCache.obtainRuntimeShader("LensBench2", RoundedRectRefractionShaderString)
        assertSame("Same AGSL source should reuse compiled RuntimeShader", shaderA, shaderB)
        val lookupStats = measureSamplesMs(warmup = 50, iterations = 1000) {
            SharedRenderEffectShaderCache.obtainRuntimeShader("LensBench", RoundedRectRefractionShaderString)
        }
        println(lookupStats.format("SharedRenderEffectShaderCache.hit"))
        assertTrue("Cache hit overhead too high: ${lookupStats.meanMs}ms", lookupStats.meanMs < 0.05)
    }

    @Test
    fun benchmarkHeadlessSceneFramesAcrossCoreComponents() {
        // 1. LiquidGlassEffectContainer with 4 animated glass capsules
        var expandFraction by mutableFloatStateOf(0f)
        val containerScene = ImageComposeScene(width = 420, height = 160) {
            val bg = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().layerBackdrop(bg).background(Color(0xff233756)))
                LiquidGlassEffectContainer(
                    backdrop = bg,
                    spacing = 24.dp,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((4f + 22f * expandFraction).dp)) {
                        repeat(4) { idx ->
                            LiquidGlassSurface(
                                backdrop = bg,
                                modifier = Modifier.size(48.dp),
                            ) {
                                BasicText("C$idx")
                            }
                        }
                    }
                }
            }
        }
        val containerStats = try {
            measureSamplesMs(warmup = 10, iterations = 60) { i ->
                expandFraction = (i % 20) / 20f
                containerScene.render((i + 1) * 16_000_000L).close()
            }
        } finally {
            containerScene.close()
        }
        println(containerStats.format("Scene.GlassEffectContainer_4orbs"))

        // 2. LiquidBottomTabsLayout resting vs dragged
        var selectedTab by mutableStateOf(0)
        val tabsScene = ImageComposeScene(width = 400, height = 120) {
            val bg = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().layerBackdrop(bg).background(Color(0xfff2f2f7)))
                LiquidBottomTabsLayout(
                    selectedTabIndex = { selectedTab },
                    onTabSelected = { selectedTab = it },
                    backdrop = bg,
                    tabsCount = 3,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp).fillMaxWidth(),
                    tint = Color.Unspecified,
                    height = 64.dp,
                ) {
                    repeat(3) { i ->
                        LiquidBottomTab(onClick = { selectedTab = i }) { BasicText("Tab$i") }
                    }
                }
            }
        }
        val tabsRestStats: Stats
        val tabsDragStats: Stats
        try {
            tabsRestStats = measureSamplesMs(warmup = 10, iterations = 40) { i ->
                tabsScene.render((i + 1) * 16_000_000L).close()
            }
            tabsScene.sendPointerEvent(PointerEventType.Press, Offset(90f, 60f))
            tabsDragStats = measureSamplesMs(warmup = 5, iterations = 40) { i ->
                val x = 90f + (i % 20) * 10f
                tabsScene.sendPointerEvent(PointerEventType.Move, Offset(x, 60f))
                tabsScene.render((100L + i) * 16_000_000L).close()
            }
            tabsScene.sendPointerEvent(PointerEventType.Release, Offset(240f, 60f))
        } finally {
            tabsScene.close()
        }
        println(tabsRestStats.format("Scene.BottomTabs_rest"))
        println(tabsDragStats.format("Scene.BottomTabs_drag"))

        // 3. LiquidSlider continuous drag
        var sliderVal by mutableFloatStateOf(0.3f)
        val sliderScene = ImageComposeScene(width = 360, height = 100) {
            val bg = rememberLayerBackdrop()
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                Box(Modifier.matchParentSize().layerBackdrop(bg).background(Color.White))
                LiquidSlider(
                    value = { sliderVal },
                    onValueChange = { sliderVal = it },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.005f,
                    backdrop = bg,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val sliderStats = try {
            sliderScene.render(1_000_000L).close()
            sliderScene.sendPointerEvent(PointerEventType.Press, Offset(120f, 45f))
            val res = measureSamplesMs(warmup = 5, iterations = 40) { i ->
                val x = 120f + (i % 15) * 10f
                sliderScene.sendPointerEvent(PointerEventType.Move, Offset(x, 45f))
                sliderScene.render((10L + i) * 16_000_000L).close()
            }
            sliderScene.sendPointerEvent(PointerEventType.Release, Offset(220f, 45f))
            res
        } finally {
            sliderScene.close()
        }
        println(sliderStats.format("Scene.LiquidSlider_drag"))

        // 4. LiquidMeshGradient (3x3)
        val meshScene = ImageComposeScene(width = 360, height = 200) {
            LiquidMeshGradient(
                width = 3,
                height = 3,
                colors = List(9) { idx -> Color((0xff2050a0 + idx * 0x00150d07).toInt()) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val meshStats = try {
            measureSamplesMs(warmup = 8, iterations = 40) { i ->
                meshScene.render((i + 1) * 16_000_000L).close()
            }
        } finally {
            meshScene.close()
        }
        println(meshStats.format("Scene.LiquidMeshGradient_3x3"))
    }
}
