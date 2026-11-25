package com.kyant.foldfiles.probe

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inspector.WindowInspector
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.foldfiles.FilesActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// Only in the instrumentation APK. Capture the real toolbar without changing its composition.
class ToolbarPressCapture : Instrumentation() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var options: Bundle

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        options = arguments ?: Bundle()
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var activity: Activity? = null
        val status = runCatching {
            require(Build.VERSION.SDK_INT >= 29) { "Frame commit callbacks require API 29" }
            val screen = startActivitySync(Intent(targetContext, FilesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            activity = screen
            val root = File(targetContext.cacheDir, "toolbar-probe/${System.currentTimeMillis()}")
            val density = screen.resources.displayMetrics.density
            val controls = mapOf("label" to "选择文件", "group" to "新建文件夹", "circle" to "显示位置")
            when (options.getString("control")) {
                "menu" -> captureMenu(screen, density, root)
                "hover" -> captureHeldBoundary(screen, density, root)
                "return" -> captureReturn(screen, density, root)
                "tabs" -> captureTabs(screen, density, root)
                "quickBack" -> captureBackButton(screen, density, root)
                "quickActions" -> captureActionButtons(screen, density, root)
                "context" -> captureContext(screen, density, root)
            }
            for ((control, description) in controls) {
                if (options.containsKey("control") && options.getString("control") != control) continue
                // Leave the actual display before its edge by a pixel, even for the
                // leading round button. Each run must start from the same visible control.
                val centerDp = findControl(description).exactCenterX() / density
                val leftLimit = maxOf(-96, -(centerDp - 1f).toInt())
                val paths = mapOf("down" to (0 to 100), "left" to (leftLimit to 0),
                    "diagonal" to (maxOf(-80, leftLimit) to 80))
                for ((direction, delta) in paths) {
                    if (options.containsKey("direction") && options.getString("direction") != direction) continue
                    val hit = findControl(description)
                    val glass = Rect(hit)
                    if (control == "group") glass.union(findControl("显示选项"))
                    val folder = File(root, "$control-$direction")
                    record(screen, hit, glass, density, delta, folder)
                    sendStatus(0, Bundle().apply { putString("stream", "\n$control-$direction") })
                }
            }
            result.putString("stream", "\n${root.absolutePath}\n")
        }
        activity?.let { runOnMainSync { it.finish() } }
        status.exceptionOrNull()?.let { result.putString("stream", it.stackTraceToString()) }
        finish(if (status.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    private fun findControl(label: String, selectedOnly: Boolean = false): Rect {
        fun search(node: AccessibilityNodeInfo, selectedAncestor: Boolean = false): Rect? {
            val selected = selectedAncestor || node.isSelected
            if ((node.contentDescription?.toString() == label || node.text?.toString() == label) && node.isVisibleToUser && (!selectedOnly || selected)) {
                return Rect().also { node.getBoundsInScreen(it) }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { search(it, selected) }?.let { return it }
            return null
        }
        repeat(40) {
            if (Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
            uiAutomation.rootInActiveWindow?.let { search(it) }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing toolbar control: $label")
    }

    private fun focusedWindow(activity: Activity): Window {
        fun dialog(view: View): Window? {
            if (view is DialogWindowProvider) return view.window
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                dialog(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        var window = activity.window
        runOnMainSync {
            val root = WindowInspector.getGlobalWindowViews().lastOrNull { it.hasWindowFocus() }
            if (root != null && root !== window.decorView) dialog(root)?.let { window = it }
        }
        return window
    }

    private fun tap(hit: Rect) {
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, hit.exactCenterX(), hit.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
            SystemClock.sleep(60)
        }
    }

    private fun captureBackButton(activity: Activity, density: Float, root: File) {
        tap(findControl("浏览"))
        for (hold in listOf(0, 55, 120, 340)) {
            val file = findControl("馆藏索引.tsv")
            val options = findControl("显示选项")
            val header = Rect(0, options.top, activity.window.decorView.width, options.bottom)
            record(activity, file, header, density, 0 to 0, File(root, "enter-$hold"),
                holdMillis = 55, dragMillis = 0, endHoldMillis = 0)
            findControl("分享文件")
            record(activity, findControl("上一个位置"), header, density, 0 to 0, File(root, "back-$hold"),
                holdMillis = hold, dragMillis = 0, endHoldMillis = 0)
            findControl("显示位置")
            findControl("馆藏索引.tsv")
        }
        tap(findControl("印刷样张"))
        tap(findControl("用纸.csv"))
        val back = findControl("上一个位置")
        record(activity, back, back, density, 0 to 0, File(root, "back-nested"),
            holdMillis = 0, dragMillis = 0, endHoldMillis = 0)
        findControl("用纸.csv")
        findControl("选择文件")
        tap(findControl("用纸.csv"))
        val history = findControl("上一个位置")
        record(activity, history, history, density, 0 to 0, File(root, "history"),
            holdMillis = 780, dragMillis = 0, endHoldMillis = 0)
        tap(findControl("本机文库"))
        findControl("显示位置")
    }

    private fun captureContext(activity: Activity, density: Float, root: File) {
        tap(findControl("浏览"))
        val row = findControl("影像目录")
        val width = activity.resources.displayMetrics.widthPixels
        val rowArea = Rect(0, row.centerY() - (48 * density).roundToInt(), width,
            row.centerY() + (76 * density).roundToInt())
        record(activity, row, rowArea, density, 0 to 0, File(root, "context-row"),
            holdMillis = 940, dragMillis = 0, endHoldMillis = 0)
        tap(findControl("打开文件夹"))
        tap(findControl("上一个位置"))
        findControl("影像目录")
        val sort = findControl("更改排序")
        val popup = Rect(sort.left, sort.top, sort.left + (246 * density).roundToInt(), sort.bottom + (140 * density).roundToInt())
        record(activity, sort, popup, density, 0 to 0, File(root, "borderless-sort"),
            holdMillis = 820, dragMillis = 0, endHoldMillis = 0)
        tap(findControl("名称"))
        findControl("更改排序")
    }

    private fun captureActionButtons(activity: Activity, density: Float, root: File) {
        fun quick(label: String, name: String) {
            val hit = findControl(label)
            record(activity, hit, hit, density, 0 to 0, File(root, name),
                holdMillis = 0, dragMillis = 0, endHoldMillis = 0)
        }
        tap(findControl("最近"))
        findControl("最近打开")
        quick("新建文件夹", "disabled")
        quick("选择文件", "select")
        tap(findControl("完成选择"))
        tap(findControl("浏览"))
        quick("新建文件夹", "group")
        tap(findControl("取消"))
        for (label in listOf("显示选项", "组件目录", "全部组件", "按钮与链接")) {
            findControl(label)
            // Each of these actions presents a new menu, sheet or page.
            SystemClock.sleep(360)
            tap(findControl(label))
        }
        var count = 2
        for ((label, name) in listOf("普通按钮" to "plain", "无边框按钮" to "borderless",
                "带底色按钮" to "tinted", "强调按钮" to "filled", "玻璃按钮" to "glass")) {
            quick(label, name)
            findControl("点击计数  ${++count}")
        }
    }

    private fun captureReturn(activity: Activity, density: Float, root: File) {
        tap(findControl("最近"))
        findControl("最近打开")
        val options = findControl("显示选项")
        val glass = Rect(options).apply { union(findControl("新建文件夹")) }
        val reference = sourceFill(activity, glass)
        for (name in listOf("outside", "leaf")) {
            if (name == "outside") {
                val opening = Rect(glass).apply {
                    left = (right - 280 * density).roundToInt()
                    bottom = (top + 255 * density).roundToInt()
                }
                record(activity, options, opening, density, 0 to 0, File(root, "menu-opening"),
                    holdMillis = 80, dragMillis = 1, endHoldMillis = 0, settleMillis = 750)
            } else tap(options)
            findControl("排序方式")
            // Parent and submenu hit targets move during the opening spring.
            SystemClock.sleep(700)
            val hit = if (name == "leaf") {
                tap(findControl("排序方式"))
                findControl("修改时间")
                SystemClock.sleep(550)
                findControl("修改时间")
            } else {
                val middle = activity.window.decorView.height * 2 / 3
                Rect(glass.left, middle, glass.right, middle + 2)
            }
            record(activity, hit, glass, density, 0 to 0, File(root, "return-$name"),
                holdMillis = 72, dragMillis = 1, endHoldMillis = 0, settleMillis = 1400)
            findControl("显示选项")
            val restored = sourceFill(activity, glass)
            check(listOf(Color.red(reference) - Color.red(restored), Color.green(reference) - Color.green(restored),
                Color.blue(reference) - Color.blue(restored)).all { kotlin.math.abs(it) <= 3 }) {
                "Menu return changed the toolbar fill ($name): $reference -> $restored"
            }
        }
    }

    private fun sourceFill(activity: Activity, glass: Rect): Int {
        val origin = IntArray(2)
        runOnMainSync { activity.window.decorView.getLocationOnScreen(origin) }
        // Sample the clear space between the icons, below the specular rim.
        val x = glass.centerX() - origin[0]
        val y = glass.top + glass.height() / 5 - origin[1]
        val bitmap = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)
        val done = CountDownLatch(1)
        var status = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(activity.window, Rect(x - 2, y - 2, x + 3, y + 3), bitmap, {
            status = it
            done.countDown()
        }, handler)
        check(done.await(3, TimeUnit.SECONDS) && status == PixelCopy.SUCCESS)
        return try { bitmap.getPixel(2, 2) } finally { bitmap.recycle() }
    }

    private fun captureTabs(activity: Activity, density: Float, root: File) {
        val recent = findControl("最近")
        val browse = findControl("浏览")
        val glass = Rect(recent).apply {
            union(browse)
            inset(-(35 * density).roundToInt(), -(18 * density).roundToInt())
        }
        tap(recent)
        findControl("最近打开")
        for ((name, target) in listOf("right" to browse, "left" to recent, "hold" to browse)) {
            record(activity, target, glass, density, 0 to 0, File(root, "tabs-$name"),
                holdMillis = if (name == "hold") 650 else 36, dragMillis = 1, endHoldMillis = 0,
                settleMillis = options.getString("tailMillis")?.toIntOrNull()?.coerceIn(590, 2500) ?: 590)
            findControl(if (name == "left") "最近" else "浏览", selectedOnly = true)
            if (name == "left") findControl("最近打开")
        }
    }

    private fun captureHeldBoundary(activity: Activity, density: Float, root: File) {
        tap(findControl("更改排序"))
        val first = findControl("名称")
        val second = findControl("修改时间")
        SystemClock.sleep(500)
        val area = Rect(0, first.top - (65 * density).roundToInt(),
            activity.window.decorView.width, second.bottom + (105 * density).roundToInt())
        val start = Rect(first).apply { offset(0, -(13 * density).roundToInt()) }
        val boundary = (first.exactCenterY() + second.exactCenterY()) / 2
        val travel = ((boundary - start.exactCenterY()) / density).roundToInt() + 1
        // Reach barely inside row two, then stop sending moves as the surface passes the finger.
        record(activity, start, area, density, 12 to travel, File(root, "menu-boundary"),
            holdMillis = 180, dragMillis = 36, endHoldMillis = 600)
        findControl("大小")
    }

    private fun captureMenu(activity: Activity, density: Float, root: File) {
        val trigger = findControl("更改排序")
        // The sort menu opens below its trigger on this phone. Enclose its full animated surface.
        val margin = (24 * density).roundToInt()
        val area = Rect(trigger.left - margin, trigger.top - margin,
            trigger.left + (285 * density).roundToInt(), trigger.bottom + (230 * density).roundToInt())
        record(activity, trigger, area, density, 76 to 135, File(root, "menu-transfer"),
            holdMillis = 1100, dragMillis = 480, endHoldMillis = 300)
        val target = findControl("大小")
        for ((name, duration) in listOf("slow" to 480, "fast" to 100, "held" to 100)) {
            record(activity, target, area, density, 32 to 92, File(root, "menu-$name"),
                holdMillis = 180, dragMillis = duration, endHoldMillis = if (name == "held") 420 else 0)
            findControl("大小") // A release after stretching leaves the menu available.
        }
    }

    private fun record(
        activity: Activity, hit: Rect, glass: Rect, density: Float, delta: Pair<Int, Int>, folder: File,
        holdMillis: Int = 280, dragMillis: Int = 200, endHoldMillis: Int = 280, settleMillis: Int = 590,
    ) {
        val window = focusedWindow(activity)
        val decor = window.decorView
        val origin = IntArray(2)
        runOnMainSync { decor.getLocationOnScreen(origin) }
        val region = Rect(glass).apply {
            offset(-origin[0], -origin[1])
            val margin = (30 * density).roundToInt()
            inset(-margin, -margin)
            check(intersect(0, 0, decor.width, decor.height))
        }
        val x = hit.exactCenterX() - origin[0]
        val y = hit.exactCenterY() - origin[1]
        val pixelScale = if (folder.name.startsWith("menu-") || settleMillis > 1000) 2 else 1
        val marker = ContactStamp().apply { bounds = region }
        val samples = sortedMapOf<Int, Bitmap>()
        val timeline = JSONArray()
        val done = CountDownLatch(1)
        var pending = 0
        var finished = false
        var downTime = 0L
        var upTime = 0L
        var released = false
        val dragStart = 200 + holdMillis
        val dragEnd = dragStart + dragMillis
        val releaseAt = dragEnd + endHoldMillis
        fun touch(action: Int, fraction: Float) {
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) downTime = now
            if (action == MotionEvent.ACTION_UP) upTime = now
            val event = MotionEvent.obtain(downTime, now, action, x + delta.first * density * fraction,
                y + delta.second * density * fraction, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { window.callback.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        val frames = object : Choreographer.FrameCallback {
            var first = 0L
            override fun doFrame(frameTimeNanos: Long) {
                if (first == 0L) first = frameTimeNanos
                val elapsed = (frameTimeNanos - first) / 1_000_000
                val phase = when {
                    elapsed >= 200 && downTime == 0L -> 1
                    downTime > 0L && !released && SystemClock.uptimeMillis() - downTime < minOf(holdMillis, 36) -> 1
                    elapsed < 200 -> 0 // Rest.
                    elapsed < dragStart -> 1 // Stationary press.
                    elapsed < dragEnd -> 2 // Linear drag.
                    elapsed < releaseAt -> 3 // Hold at the end of the drag.
                    else -> 4          // Released.
                }
                if (phase in 1..3 && downTime == 0L) {
                    touch(MotionEvent.ACTION_DOWN, 0f)
                    if (holdMillis == 0 && dragMillis == 0 && endHoldMillis == 0) {
                        touch(MotionEvent.ACTION_UP, 0f)
                        released = true
                    }
                }
                if (phase == 2) touch(MotionEvent.ACTION_MOVE, (elapsed - dragStart) / dragMillis.toFloat())
                if (phase == 3 && marker.phase != 3) touch(MotionEvent.ACTION_MOVE, 1f)
                if (phase == 4 && !released) {
                    if (marker.phase == 2) touch(MotionEvent.ACTION_MOVE, 1f)
                    touch(MotionEvent.ACTION_UP, 1f)
                    released = true
                }
                marker.frame++
                marker.phase = phase
                timeline.put(JSONArray(listOf(marker.frame, elapsed, phase)))
                marker.invalidateSelf()
                pending++
                decor.viewTreeObserver.registerFrameCommitCallback {
                    val bitmap = Bitmap.createBitmap(region.width() / pixelScale, region.height() / pixelScale, Bitmap.Config.ARGB_8888)
                    // PixelCopy offsets its source rectangle by a dialog window's surface insets.
                    PixelCopy.request(window, Rect(region), bitmap, { result ->
                        if (result == PixelCopy.SUCCESS) {
                            val id = ContactStamp.read(bitmap, pixelScale)
                            if (id != null && id !in samples) samples[id] = bitmap else bitmap.recycle()
                        } else bitmap.recycle()
                        pending--
                        if (finished && pending == 0) done.countDown()
                    }, handler)
                }
                finished = elapsed >= releaseAt + settleMillis
                if (!finished) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        runOnMainSync {
            decor.overlay.add(marker)
            Choreographer.getInstance().postFrameCallback(frames)
        }
        try {
            check(done.await(12, TimeUnit.SECONDS)) { "Frame capture timed out" }
            val phases = (0 until timeline.length()).associate {
                val event = timeline.getJSONArray(it)
                event.getInt(0) to event.getInt(2)
            }
            check(samples.keys.any { phases[it] == 0 } && samples.keys.any { phases[it] == 4 }) {
                "Missing rendered rest or release frames in ${folder.name}"
            }
            check(folder.mkdirs())
            for ((id, bitmap) in samples) {
                File(folder, "%03d.png".format(id)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            fun rect(r: Rect) = JSONArray(listOf(r.left, r.top, r.right, r.bottom))
            File(folder, "frames.json").writeText(JSONObject().put("hit", rect(hit)).put("glass", rect(glass))
                .put("region", rect(region)).put("pixelScale", pixelScale).put("origin", JSONArray(origin.toList())).put("density", density)
                .put("deltaDp", JSONArray(listOf(delta.first, delta.second))).put("dragMillis", dragMillis)
                .put("contactMillis", upTime - downTime).put("timeline", timeline).toString())
        } finally {
            runOnMainSync {
                Choreographer.getInstance().removeFrameCallback(frames)
                decor.overlay.remove(marker)
            }
        }
    }
}

// The pixels carry their own frame number. PixelCopy can return a later frame than the
// commit that requested it. The contact colour and the toolbar share a window buffer.
private class ContactStamp : Drawable() {
    var frame = 0
    var phase = 0
    private val paint = Paint()
    override fun draw(canvas: Canvas) {
        fun cell(index: Int, color: Int) {
            paint.color = color
            val x = (bounds.left + index * 4).toFloat()
            canvas.drawRect(x, bounds.top.toFloat(), x + 4, bounds.top + 6f, paint)
        }
        cell(0, Color.MAGENTA)
        for (i in 0..8) cell(i + 1, if (frame and (1 shl i) != 0) Color.WHITE else Color.BLACK)
        cell(10, if (phase in 1..3) Color.RED else Color.GRAY)
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    companion object {
        fun read(bitmap: Bitmap, pixelScale: Int): Int? {
            if (bitmap.getPixel(2 / pixelScale, 2 / pixelScale) != Color.MAGENTA) return null
            var value = 0
            for (i in 0..8) if (Color.red(bitmap.getPixel((6 + 4 * i) / pixelScale, 2 / pixelScale)) > 127) value += 1 shl i
            return value
        }
    }
}
