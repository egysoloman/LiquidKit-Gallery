package com.kyant.backdrop.components

import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.media.ImageReader
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.kyant.backdrop.interaction.InteractiveHighlightShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import java.util.concurrent.atomic.AtomicBoolean

private val gpuWarmupStarted = AtomicBoolean(false)

actual fun warmUpLiquidGlassPipelines() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (!gpuWarmupStarted.compareAndSet(false, true)) return
    Thread({
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        val t0 = SystemClock.elapsedRealtime()
        warmUpOnHardwareRenderer()
        val elapsed = SystemClock.elapsedRealtime() - t0
        Log.i("LiquidPerf", "GpuWarmup completed in $elapsed ms")
    }, "LiquidGlassGpuWarmup").apply {
        isDaemon = true
        start()
    }
}

private fun warmUpOnHardwareRenderer() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var reader: ImageReader? = null
    var renderer: HardwareRenderer? = null
    try {
        reader = ImageReader.newInstance(8, 8, PixelFormat.RGBA_8888, 2)
        renderer = HardwareRenderer().apply {
            setSurface(reader.surface)
        }
        val root = RenderNode("LiquidWarmupRoot").apply {
            setPosition(0, 0, 8, 8)
            setHasOverlappingRendering(true)
        }

        fun makeEffectNode(name: String, effect: RenderEffect?): RenderNode? {
            if (effect == null) return null
            return RenderNode(name).apply {
                setPosition(0, 0, 8, 8)
                val c = beginRecording(8, 8)
                c.drawColor(Color.argb(140, 180, 210, 240))
                endRecording()
                setRenderEffect(effect)
            }
        }

        val effectNodes = mutableListOf<RenderNode>()
        fun addEffect(name: String, effectFactory: () -> RenderEffect) {
            runCatching { makeEffectNode(name, effectFactory()) }.getOrNull()?.let { effectNodes += it }
        }

        addEffect("blur") {
            RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)
        }
        addEffect("tabMaterialLight") {
            val s = RuntimeShader(TabMaterialShader).apply {
                setFloatUniform("halfSize", 4f, 4f)
                setFloatUniform("inset", 0f)
                setFloatUniform("density", 3f)
                setFloatUniform("spread", 4f)
                setFloatUniform("softMix", 0.5f)
                setFloatUniform("dark", 0f)
                setFloatUniform("backing", 0.2f)
                setFloatUniform("active", 1f)
                setFloatUniform("reflection", 0.5f)
                setFloatUniform("hairline", 0.2f)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }
        addEffect("tabMaterialDark") {
            val s = RuntimeShader(TabMaterialShader).apply {
                setFloatUniform("halfSize", 4f, 4f)
                setFloatUniform("inset", 0f)
                setFloatUniform("density", 3f)
                setFloatUniform("spread", 4f)
                setFloatUniform("softMix", 0.5f)
                setFloatUniform("dark", 1f)
                setFloatUniform("backing", 0.2f)
                setFloatUniform("active", 1f)
                setFloatUniform("reflection", 0.5f)
                setFloatUniform("hairline", 0.2f)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }
        addEffect("tabPlateLight") {
            val s = RuntimeShader(TabPlateShader).apply {
                setFloatUniform("rect", 4f, 4f, 3f, 3f)
                setFloatUniform("plate", 4f, 4f, 3f, 3f)
                setFloatUniform("strength", 1f)
                setFloatUniform("density", 3f)
                setFloatUniform("reduction", 0.15f)
                setFloatUniform("dispersion", 4f)
                setFloatUniform("diffusion", 4f)
                setFloatUniform("dark", 0f)
                setFloatUniform("joinWidth", 6f)
                setFloatUniform("bevelWidth", 3f)
                setFloatUniform("interfaceShade", 0.5f)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }
        addEffect("tabInk") {
            val s = RuntimeShader(TabInkShader).apply {
                setFloatUniform("rect", 4f, 4f, 3f, 3f)
                setFloatUniform("strength", 1f)
                setFloatUniform("density", 3f)
                setFloatUniform("dispersionPx", 3f)
                setFloatUniform("bevelWidth", 3f)
                setFloatUniform("refractionPx", 2f)
                setFloatUniform("grid", 4f, 4f, 4f, 0.1f)
                setFloatUniform("darkSurface", 0f)
                setFloatUniform("sceneOffset", 0f)
                setFloatUniform("vibrancy", 0.5f)
                setFloatUniform("backing", 0.2f)
                setColorUniform("ink", Color.BLUE)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }
        addEffect("navHeader") {
            val s = RuntimeShader(NavHeaderGlassShader).apply {
                setFloatUniform("barWidth", 8f)
                setFloatUniform("barHeight", 8f)
                setFloatUniform("density", 3f)
                setFloatUniform("topBlurPx", 12f)
                setFloatUniform("dark", 0f)
                setFloatUniform("glassOpacity", 0.5f)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }
        addEffect("lensPlain") {
            val s = RuntimeShader(RoundedRectRefractionShaderString).apply {
                setFloatUniform("size", 8f, 8f)
                setFloatUniform("offset", 0f, 0f)
                setFloatUniform("cornerRadii", floatArrayOf(4f, 4f, 4f, 4f))
                setFloatUniform("refractionHeight", 4f)
                setFloatUniform("refractionAmount", -4f)
                setFloatUniform("depthEffect", 1f)
                setFloatUniform("capA", 0f, 0f, 0f, 0f)
                setFloatUniform("capB", 0f, 0f, 0f, 0f)
                setFloatUniform("capC", 0f, 0f, 0f, 0f)
                setFloatUniform("capD", 0f, 0f, 0f, 0f)
                setFloatUniform("capSpacing", 0f)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }
        addEffect("lensDispersion") {
            val s = RuntimeShader(RoundedRectRefractionWithDispersionShaderString).apply {
                setFloatUniform("size", 8f, 8f)
                setFloatUniform("offset", 0f, 0f)
                setFloatUniform("cornerRadii", floatArrayOf(4f, 4f, 4f, 4f))
                setFloatUniform("refractionHeight", 4f)
                setFloatUniform("refractionAmount", -4f)
                setFloatUniform("depthEffect", 1f)
                setFloatUniform("chromaticAberration", 1f)
                setFloatUniform("capA", 0f, 0f, 0f, 0f)
                setFloatUniform("capB", 0f, 0f, 0f, 0f)
                setFloatUniform("capC", 0f, 0f, 0f, 0f)
                setFloatUniform("capD", 0f, 0f, 0f, 0f)
                setFloatUniform("capSpacing", 0f)
            }
            RenderEffect.createRuntimeShaderEffect(s, "content")
        }

        val rootCanvas = root.beginRecording(8, 8)
        for (node in effectNodes) {
            rootCanvas.drawRenderNode(node)
        }
        val paint = Paint().apply { alpha = 128 }
        runCatching {
            val bevel = RuntimeShader(TabBevelShader).apply {
                setFloatUniform("halfSize", 4f, 4f)
                setFloatUniform("contact", 0f, 0f)
                setFloatUniform("density", 3f)
                setFloatUniform("dark", 0f)
                setFloatUniform("amount", 1f)
                setFloatUniform("bevelWidth", 3f)
            }
            paint.shader = bevel
            rootCanvas.drawRect(0f, 0f, 8f, 8f, paint)
        }
        for (layered in floatArrayOf(0f, 1f)) {
            runCatching {
                val rim = RuntimeShader(RimShader).apply {
                    setFloatUniform("size", 8f, 8f)
                    setFloatUniform("radii", floatArrayOf(4f, 4f, 4f, 4f))
                    setFloatUniform("pixelDensity", 3f)
                    setFloatUniform("pale", 1f)
                    setFloatUniform("layeredEdge", layered)
                    setFloatUniform("reflection", 0.5f)
                    setFloatUniform("absorption", 0.4f)
                    setFloatUniform("capA", 0f, 0f, 0f, 0f)
                    setFloatUniform("capB", 0f, 0f, 0f, 0f)
                    setFloatUniform("capC", 0f, 0f, 0f, 0f)
                    setFloatUniform("capD", 0f, 0f, 0f, 0f)
                    setFloatUniform("capSpacing", 0f)
                }
                paint.shader = rim
                rootCanvas.drawRect(0f, 0f, 8f, 8f, paint)
            }
        }
        runCatching {
            val sh = RuntimeShader(ShadowShader).apply {
                setFloatUniform("a", 4f, 4f, 3f, 3f)
                setFloatUniform("b", 0f, 0f, -1f, -1f)
                setFloatUniform("radii", 3f, 0f)
                setFloatUniform("bodyContour", 0f, 0f, -1f, 0f)
                setFloatUniform("bodyCrownBias", 0f)
                setFloatUniform("join", 0f)
                setFloatUniform("shadow", 4f, 2f, 0.1f)
            }
            paint.shader = sh
            rootCanvas.drawRect(0f, 0f, 8f, 8f, paint)
        }
        runCatching {
            val touch = RuntimeShader(InteractiveHighlightShaderString).apply {
                setFloatUniform("size", 8f, 8f)
                setColorUniform("color", Color.WHITE)
                setFloatUniform("radius", 6f)
                setFloatUniform("position", 4f, 4f)
            }
            paint.shader = touch
            rootCanvas.drawRect(0f, 0f, 8f, 8f, paint)
        }
        root.endRecording()

        renderer.setContentRoot(root)
        renderer.createRenderRequest()
            .setWaitForPresent(true)
            .syncAndDraw()
        runCatching { reader.acquireLatestImage()?.close() }
    } catch (t: Throwable) {
        Log.w("LiquidPerf", "GpuWarmup skipped: ${t.message}")
    } finally {
        runCatching { renderer?.destroy() }
        runCatching { reader?.close() }
    }
}
