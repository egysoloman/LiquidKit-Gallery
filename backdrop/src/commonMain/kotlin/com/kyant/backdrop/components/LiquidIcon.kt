package com.kyant.backdrop.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** System names can be passed directly to Image(systemName:) on Apple platforms. Android draws its own vectors. */
enum class LiquidSymbol(val systemName: String, internal val opticalWidth: Int) {
    Back("chevron.backward", 15), Forward("chevron.forward", 15), Check("checkmark", 20), More("ellipsis", 22),
    Bell("bell", 22), Like("hand.thumbsup", 23), Download("arrow.down.to.line", 19), Grid("square.grid.2x2", 22),
    List("list.bullet", 23), Ascending("arrow.up", 19), Descending("arrow.down", 19), Bookmark("bookmark", 19),
    Saved("bookmark.fill", 19), Sidebar("sidebar.leading", 26), Folder("folder", 26), NewFolder("folder.badge.plus", 29),
    Document("doc.text", 20), Search("magnifyingglass", 23), Clock("clock", 23), Drive("externaldrive", 26),
    Link("link", 25), Rename("pencil", 19), Trash("trash", 20), Close("xmark", 19), CheckCircle("checkmark.circle", 23),
    Unchecked("circle", 23), ChevronDown("chevron.down", 21), ChevronUpDown("chevron.up.chevron.down", 16), Share("square.and.arrow.up", 21),
    Add("plus", 20), Help("questionmark", 15), Filter("line.3.horizontal.decrease.circle", 23), DownloadCircle("arrow.down.circle", 23),
    Microphone("mic", 15), FilledClock("clock.fill", 23), FilledFolder("folder.fill", 26), Reload("arrow.clockwise", 20),
    SquareOnSquare("square.on.square", 22), SquareFillOnSquareFill("square.fill.on.square.fill", 22),
    Clipboard("doc.on.clipboard", 21), Calendar("calendar", 22),
    SliderHorizontal("slider.horizontal.3", 23), HandTap("hand.tap", 21), TextCursor("text.cursor", 17),
    Gauge("gauge.with.dots.needle.50percent", 24), CircleHalfFilled("circle.lefthalf.filled", 22),
    SquareOnCircle("square.on.circle", 23), MenuCard("menucard", 21), HandDraw("hand.draw", 23),
    ListBulletRectangle("list.bullet.rectangle", 24),
    Sparkles("sparkles", 23), Star("star", 24), StarFilled("star.fill", 24), Heart("heart.fill", 23),
    Sun("sun.max.fill", 24), Moon("moon.fill", 22), Play("play.fill", 20), Globe("globe", 23),
    Map("map.fill", 24), Lightbulb("lightbulb.max.fill", 22), Chart("chart.bar.xaxis", 24),
    Camera("camera", 24), Send("paperplane.fill", 23), Code("curlybraces", 24),
    Drop("drop.halffull", 20), Palette("paintpalette.fill", 24);

    companion object {
        private val bySystemName = entries.associateBy { it.systemName }
        fun fromSystemName(name: String): LiquidSymbol? = bySystemName[name]
    }
}

private val IconPen = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val ThinPen = Stroke(1.45f)
private val ThickPen = Stroke(2.2f)
private val MoreDotXs = floatArrayOf(5f, 12f, 19f)
private val GridCoords = floatArrayOf(4f, 13.5f)
private val ListRowYs = floatArrayOf(6f, 12f, 18f)
private val BulletRowYs = floatArrayOf(8.5f, 12f, 15.5f)
private val PaletteDotXs = floatArrayOf(8.5f, 12f, 15.5f, 15f)
private val PaletteDotYs = floatArrayOf(9.5f, 7.8f, 9.8f, 14.2f)
private val SunRayCos = FloatArray(8) { i -> kotlin.math.cos(i * 45.0 * (kotlin.math.PI / 180.0)).toFloat() }
private val SunRaySin = FloatArray(8) { i -> kotlin.math.sin(i * 45.0 * (kotlin.math.PI / 180.0)).toFloat() }

/** Monochrome paths in a 24-unit drawing area. Give the containing action its accessible label. */
@Composable
fun LiquidIcon(symbol: LiquidSymbol, color: Color = Color.Unspecified, modifier: Modifier = Modifier, pressedColor: Color = color) {
    val highlight = if (pressedColor != color && pressedColor != Color.Unspecified) LocalToolbarGroup.current?.light else null
    val base = if (color == Color.Unspecified) LocalLiquidContentColor.current else color
    val pressed = if (pressedColor == Color.Unspecified) base else pressedColor
    val scratchPath = remember { Path() }
    Canvas(modifier.size(22.dp)) {
        val ink = lerp(base, pressed, highlight?.pressProgress?.coerceIn(0f, 1f) ?: 0f)
        val factor = size.minDimension / 24f
        scale(factor, pivot = Offset.Zero) {
            when (symbol) {
                LiquidSymbol.Back, LiquidSymbol.Forward, LiquidSymbol.Check, LiquidSymbol.More,
                LiquidSymbol.Close, LiquidSymbol.Add, LiquidSymbol.Help, LiquidSymbol.ChevronDown,
                LiquidSymbol.ChevronUpDown, LiquidSymbol.Grid, LiquidSymbol.List,
                LiquidSymbol.Ascending, LiquidSymbol.Descending, LiquidSymbol.Search ->
                    drawCoreUiSymbol(symbol, ink, scratchPath)

                LiquidSymbol.Sidebar, LiquidSymbol.Bookmark, LiquidSymbol.Saved, LiquidSymbol.Folder,
                LiquidSymbol.NewFolder, LiquidSymbol.FilledFolder, LiquidSymbol.Document,
                LiquidSymbol.Download, LiquidSymbol.DownloadCircle, LiquidSymbol.Filter,
                LiquidSymbol.Share, LiquidSymbol.Link, LiquidSymbol.Rename, LiquidSymbol.Trash ->
                    drawFileActionSymbol(symbol, ink, scratchPath)

                LiquidSymbol.Bell, LiquidSymbol.Like, LiquidSymbol.Microphone, LiquidSymbol.Clock,
                LiquidSymbol.FilledClock, LiquidSymbol.CheckCircle, LiquidSymbol.Unchecked,
                LiquidSymbol.Drive, LiquidSymbol.Reload, LiquidSymbol.Clipboard, LiquidSymbol.Calendar ->
                    drawSystemStatusSymbol(symbol, ink, scratchPath)

                LiquidSymbol.SliderHorizontal, LiquidSymbol.HandTap, LiquidSymbol.TextCursor,
                LiquidSymbol.Gauge, LiquidSymbol.CircleHalfFilled, LiquidSymbol.SquareOnCircle,
                LiquidSymbol.SquareOnSquare, LiquidSymbol.SquareFillOnSquareFill, LiquidSymbol.MenuCard,
                LiquidSymbol.HandDraw, LiquidSymbol.ListBulletRectangle ->
                    drawControlGallerySymbol(symbol, ink, scratchPath)

                LiquidSymbol.Sparkles, LiquidSymbol.Star, LiquidSymbol.StarFilled, LiquidSymbol.Heart,
                LiquidSymbol.Sun, LiquidSymbol.Moon, LiquidSymbol.Play, LiquidSymbol.Globe,
                LiquidSymbol.Map, LiquidSymbol.Lightbulb, LiquidSymbol.Chart, LiquidSymbol.Camera,
                LiquidSymbol.Send, LiquidSymbol.Code, LiquidSymbol.Drop, LiquidSymbol.Palette ->
                    drawVisualSymbol(symbol, ink, scratchPath)
            }
        }
    }
}

private inline fun DrawScope.iconLine(ink: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(ink, Offset(x1, y1), Offset(x2, y2), IconPen.width, StrokeCap.Round)

private inline fun DrawScope.iconOutline(ink: Color, scratchPath: Path, block: Path.() -> Unit) {
    scratchPath.reset()
    scratchPath.fillType = PathFillType.NonZero
    scratchPath.block()
    drawPath(scratchPath, ink, style = IconPen)
}

private inline fun DrawScope.iconFilled(ink: Color, scratchPath: Path, evenOdd: Boolean = false, block: Path.() -> Unit) {
    scratchPath.reset()
    scratchPath.fillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
    scratchPath.block()
    drawPath(scratchPath, ink)
}

private fun DrawScope.drawCoreUiSymbol(symbol: LiquidSymbol, ink: Color, scratchPath: Path) {
    when (symbol) {
        LiquidSymbol.Check -> iconOutline(ink, scratchPath) { moveTo(4.5f, 12f); lineTo(9.8f, 17.2f); lineTo(20f, 6.7f) }
        LiquidSymbol.More -> for (x in MoreDotXs) drawCircle(ink, 1.75f, Offset(x, 12f))
        LiquidSymbol.Back, LiquidSymbol.Forward -> {
            val sign = if ((layoutDirection == LayoutDirection.Ltr) == (symbol == LiquidSymbol.Back)) 1f else -1f
            iconOutline(ink, scratchPath) { moveTo(12 + 3.5f * sign, 4.5f); lineTo(12 - 4f * sign, 12f); lineTo(12 + 3.5f * sign, 19.5f) }
        }
        LiquidSymbol.Grid -> {
            for (x in GridCoords) for (y in GridCoords) {
                drawRoundRect(ink, Offset(x, y), Size(6.5f, 6.5f), CornerRadius(1.4f), style = IconPen)
            }
        }
        LiquidSymbol.List -> {
            for (y in ListRowYs) {
                drawCircle(ink, 1.35f, Offset(4.5f, y))
                iconLine(ink, 9f, y, 20f, y)
            }
        }
        LiquidSymbol.Ascending, LiquidSymbol.Descending -> {
            val top = if (symbol == LiquidSymbol.Ascending) 7f else 16f
            val bottom = 23f - top
            iconLine(ink, 5f, 6f, 13.5f, 6f); iconLine(ink, 5f, 12f, 11f, 12f); iconLine(ink, 5f, 18f, 8.5f, 18f)
            iconLine(ink, 17f, top, 17f, bottom)
            iconOutline(ink, scratchPath) {
                val wing = if (symbol == LiquidSymbol.Ascending) top + 3.6f else top - 3.6f
                moveTo(13.5f, wing); lineTo(17f, top); lineTo(20.5f, wing)
            }
        }
        LiquidSymbol.Search -> {
            drawCircle(ink, 6.6f, Offset(10.2f, 10.2f), style = IconPen); iconLine(ink, 15.2f, 15.2f, 21f, 21f)
        }
        LiquidSymbol.Close -> { iconLine(ink, 6f, 6f, 18f, 18f); iconLine(ink, 18f, 6f, 6f, 18f) }
        LiquidSymbol.Add -> { iconLine(ink, 4f, 12f, 20f, 12f); iconLine(ink, 12f, 4f, 12f, 20f) }
        LiquidSymbol.Help -> {
            iconOutline(ink, scratchPath) { moveTo(7.8f, 7.5f); cubicTo(8f, 1f, 18.8f, 2.6f, 16.5f, 9.4f); cubicTo(15.6f, 12.2f, 12f, 11f, 12f, 15f) }
            drawCircle(ink, 1.3f, Offset(12f, 20f))
        }
        LiquidSymbol.ChevronDown -> iconOutline(ink, scratchPath) { moveTo(5f, 9f); lineTo(12f, 15.5f); lineTo(19f, 9f) }
        LiquidSymbol.ChevronUpDown -> {
            iconOutline(ink, scratchPath) { moveTo(7.5f, 9.2f); lineTo(12f, 4.7f); lineTo(16.5f, 9.2f) }
            iconOutline(ink, scratchPath) { moveTo(7.5f, 14.8f); lineTo(12f, 19.3f); lineTo(16.5f, 14.8f) }
        }
        else -> Unit
    }
}

private fun DrawScope.drawFileActionSymbol(symbol: LiquidSymbol, ink: Color, scratchPath: Path) {
    when (symbol) {
        LiquidSymbol.Sidebar -> {
            drawRoundRect(ink, Offset(2.5f, 4.5f), Size(19f, 15f), CornerRadius(2.7f), style = IconPen)
            val divider = if (layoutDirection == LayoutDirection.Ltr) 9f else 15f
            iconLine(ink, divider, 5f, divider, 19f)
        }
        LiquidSymbol.Download -> {
            iconLine(ink, 12f, 3.5f, 12f, 15.5f)
            iconOutline(ink, scratchPath) { moveTo(7f, 11f); lineTo(12f, 16f); lineTo(17f, 11f) }
            iconLine(ink, 5.5f, 20f, 18.5f, 20f)
        }
        LiquidSymbol.Bookmark -> iconOutline(ink, scratchPath) {
            moveTo(6f, 5f); quadraticTo(6f, 4f, 7.2f, 4f); lineTo(16.8f, 4f)
            quadraticTo(18f, 4f, 18f, 5f); lineTo(18f, 20f); lineTo(12f, 16f); lineTo(6f, 20f); close()
        }
        LiquidSymbol.Saved -> iconFilled(ink, scratchPath) {
            moveTo(5.3f, 3.1f); quadraticTo(5.3f, 2f, 6.6f, 2f); lineTo(17.4f, 2f)
            quadraticTo(18.7f, 2f, 18.7f, 3.1f); lineTo(18.7f, 22f); lineTo(12f, 17f)
            lineTo(5.3f, 22f); close()
        }
        LiquidSymbol.FilledFolder -> iconFilled(ink, scratchPath, evenOdd = true) {
            moveTo(1f, 5.4f); quadraticTo(1f, 3.2f, 3.3f, 3.2f); lineTo(9f, 3.2f)
            lineTo(11.3f, 5.6f); lineTo(20.8f, 5.6f); quadraticTo(23f, 5.6f, 23f, 8f)
            lineTo(23f, 18.8f); quadraticTo(23f, 21f, 20.8f, 21f); lineTo(3.2f, 21f)
            quadraticTo(1f, 21f, 1f, 18.8f); close()
            moveTo(3.5f, 8f); lineTo(20.8f, 8f); lineTo(20.8f, 9.3f); lineTo(3.5f, 9.3f); close()
        }
        LiquidSymbol.Folder -> iconOutline(ink, scratchPath) {
            moveTo(3f, 7.5f); quadraticTo(3f, 5f, 5.5f, 5f); lineTo(9.2f, 5f); lineTo(11.8f, 8f)
            lineTo(18.8f, 8f); quadraticTo(21f, 8f, 21f, 10.2f); lineTo(21f, 17.2f)
            quadraticTo(21f, 19.5f, 18.8f, 19.5f); lineTo(5.2f, 19.5f)
            quadraticTo(3f, 19.5f, 3f, 17f); close()
        }
        LiquidSymbol.NewFolder -> {
            iconOutline(ink, scratchPath) {
                moveTo(20.9f, 13f); lineTo(20.9f, 17.7f); quadraticTo(20.9f, 20f, 18.4f, 20f)
                lineTo(4.4f, 20f); quadraticTo(2.4f, 20f, 2.4f, 17.8f); lineTo(2.4f, 7.2f)
                quadraticTo(2.4f, 5.2f, 4.5f, 5.2f); lineTo(8.8f, 5.2f); lineTo(11.5f, 8.1f); lineTo(13.1f, 8.1f)
            }
            drawCircle(ink, 3.8f, Offset(18.7f, 6.5f), style = ThinPen)
            iconLine(ink, 16.9f, 6.5f, 20.5f, 6.5f); iconLine(ink, 18.7f, 4.7f, 18.7f, 8.3f)
        }
        LiquidSymbol.Document -> {
            iconOutline(ink, scratchPath) { moveTo(5.5f, 3f); lineTo(14.3f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(5.5f, 21f); close() }
            iconLine(ink, 9f, 12f, 15.5f, 12f); iconLine(ink, 9f, 16f, 13.5f, 16f)
        }
        LiquidSymbol.Link -> {
            iconOutline(ink, scratchPath) { moveTo(8.5f, 14f); lineTo(6.9f, 15.6f); cubicTo(3.6f, 19f, 8.1f, 23f, 11.1f, 20f); lineTo(15f, 16.1f); quadraticTo(17.3f, 13.6f, 15f, 11f) }
            iconOutline(ink, scratchPath) { moveTo(15.5f, 10f); lineTo(17.1f, 8.4f); cubicTo(20.4f, 5f, 15.9f, 1f, 12.9f, 4f); lineTo(9f, 7.9f); quadraticTo(6.7f, 10.4f, 9f, 13f) }
        }
        LiquidSymbol.Rename -> {
            iconOutline(ink, scratchPath) { moveTo(4f, 19.8f); lineTo(5.4f, 14.4f); lineTo(16.9f, 3.4f); lineTo(20.5f, 7.1f); lineTo(9.1f, 18.3f); close() }
            iconLine(ink, 14.2f, 6.3f, 17.5f, 9.6f)
        }
        LiquidSymbol.Trash -> {
            iconLine(ink, 3.8f, 6.8f, 20.2f, 6.8f); iconLine(ink, 9.5f, 3.7f, 14.5f, 3.7f)
            iconOutline(ink, scratchPath) { moveTo(6.3f, 7f); lineTo(7.4f, 20f); lineTo(16.6f, 20f); lineTo(17.7f, 7f) }
            iconLine(ink, 10f, 10.5f, 10.5f, 16.5f); iconLine(ink, 14f, 10.5f, 13.5f, 16.5f)
        }
        LiquidSymbol.Filter, LiquidSymbol.DownloadCircle -> {
            drawCircle(ink, 9.4f, Offset(12f, 12f), style = IconPen)
            if (symbol == LiquidSymbol.Filter) {
                iconLine(ink, 6.3f, 9f, 17.7f, 9f); iconLine(ink, 8.3f, 12.3f, 15.7f, 12.3f); iconLine(ink, 10.3f, 15.6f, 13.7f, 15.6f)
            } else {
                iconLine(ink, 12f, 6.3f, 12f, 16f)
                iconOutline(ink, scratchPath) { moveTo(8f, 12.2f); lineTo(12f, 16.2f); lineTo(16f, 12.2f) }
            }
        }
        LiquidSymbol.Share -> {
            iconOutline(ink, scratchPath) { moveTo(8f, 10f); lineTo(5f, 10f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 10f); lineTo(16f, 10f) }
            iconLine(ink, 12f, 3f, 12f, 14f); iconOutline(ink, scratchPath) { moveTo(8.3f, 6.8f); lineTo(12f, 3f); lineTo(15.7f, 6.8f) }
        }
        else -> Unit
    }
}

private fun DrawScope.drawSystemStatusSymbol(symbol: LiquidSymbol, ink: Color, scratchPath: Path) {
    when (symbol) {
        LiquidSymbol.Bell -> {
            iconOutline(ink, scratchPath) {
                moveTo(4.5f, 16.5f); quadraticTo(7f, 13.5f, 7f, 9.5f)
                cubicTo(7f, 2.5f, 17f, 2.5f, 17f, 9.5f)
                quadraticTo(17f, 13.5f, 19.5f, 16.5f); close()
            }
            iconLine(ink, 10.5f, 20f, 13.5f, 20f)
        }
        LiquidSymbol.Like -> {
            iconLine(ink, 4f, 11f, 4f, 19f)
            iconOutline(ink, scratchPath) {
                moveTo(8f, 19f); lineTo(8f, 10f); lineTo(12.5f, 4f)
                quadraticTo(15.2f, 3.4f, 14f, 10f); lineTo(18.5f, 10f)
                quadraticTo(21f, 10f, 20f, 13f); lineTo(18.6f, 18f)
                quadraticTo(18.2f, 20f, 15.6f, 20f); close()
            }
        }
        LiquidSymbol.Reload -> {
            drawArc(ink, -72f, 300f, false, Offset(3.5f, 4.5f), Size(16.5f, 16.5f), style = IconPen)
            iconOutline(ink, scratchPath) { moveTo(12.5f, 1.8f); lineTo(16.4f, 4.6f); lineTo(13.6f, 8.5f) }
        }
        LiquidSymbol.Microphone -> {
            drawRoundRect(ink, Offset(8.8f, 2.5f), Size(6.4f, 12f), CornerRadius(3.2f), style = IconPen)
            iconOutline(ink, scratchPath) { moveTo(5.4f, 10.3f); cubicTo(5.4f, 19.5f, 18.6f, 19.5f, 18.6f, 10.3f) }
            iconLine(ink, 12f, 17.2f, 12f, 21f); iconLine(ink, 8.4f, 21f, 15.6f, 21f)
        }
        LiquidSymbol.FilledClock -> iconFilled(ink, scratchPath, evenOdd = true) {
            addOval(Rect(2.3f, 2.3f, 21.7f, 21.7f))
            moveTo(11.1f, 6.2f); lineTo(12.9f, 6.2f); lineTo(12.9f, 11.5f)
            lineTo(16.8f, 13.7f); lineTo(15.9f, 15.3f); lineTo(11.1f, 12.5f); close()
        }
        LiquidSymbol.Clock, LiquidSymbol.CheckCircle, LiquidSymbol.Unchecked -> {
            drawCircle(ink, 8.7f, Offset(12f, 12f), style = IconPen)
            if (symbol == LiquidSymbol.Clock) { iconLine(ink, 12f, 6.8f, 12f, 12f); iconLine(ink, 12f, 12f, 16f, 14f) }
            if (symbol == LiquidSymbol.CheckCircle) iconOutline(ink, scratchPath) { moveTo(7.4f, 12f); lineTo(10.8f, 15.4f); lineTo(16.6f, 8.6f) }
        }
        LiquidSymbol.Drive -> {
            drawRoundRect(ink, Offset(3f, 6f), Size(18f, 12f), CornerRadius(3f), style = IconPen)
            iconLine(ink, 3.5f, 12.2f, 20.5f, 12.2f); drawCircle(ink, 1f, Offset(16.8f, 15.2f))
        }
        LiquidSymbol.Clipboard -> {
            drawRoundRect(ink, Offset(5.2f, 5.5f), Size(13.6f, 15.5f), CornerRadius(2.4f), style = IconPen)
            drawRoundRect(ink, Offset(8.5f, 2.8f), Size(7f, 3.4f), CornerRadius(1.4f), style = IconPen)
        }
        LiquidSymbol.Calendar -> {
            drawRoundRect(ink, Offset(3.5f, 5f), Size(17f, 15.5f), CornerRadius(2.8f), style = IconPen)
            iconLine(ink, 4f, 10f, 20f, 10f); iconLine(ink, 8f, 3.2f, 8f, 6.6f); iconLine(ink, 16f, 3.2f, 16f, 6.6f)
        }
        else -> Unit
    }
}

private fun DrawScope.drawControlGallerySymbol(symbol: LiquidSymbol, ink: Color, scratchPath: Path) {
    when (symbol) {
        LiquidSymbol.SquareOnSquare -> {
            drawRoundRect(ink, Offset(3.5f, 7.5f), Size(11.5f, 11.5f), CornerRadius(2.4f), style = IconPen)
            iconOutline(ink, scratchPath) { moveTo(9f, 7.5f); lineTo(9f, 5.4f); quadraticTo(9f, 3.8f, 10.6f, 3.8f); lineTo(18.6f, 3.8f); quadraticTo(20.2f, 3.8f, 20.2f, 5.4f); lineTo(20.2f, 13.4f); quadraticTo(20.2f, 15f, 18.6f, 15f); lineTo(15f, 15f) }
        }
        LiquidSymbol.SquareFillOnSquareFill -> {
            drawRoundRect(ink, Offset(9f, 3.8f), Size(11.2f, 11.2f), CornerRadius(2.4f))
            drawRoundRect(ink, Offset(3.8f, 8.8f), Size(11.2f, 11.2f), CornerRadius(2.4f), style = ThickPen)
            drawRoundRect(ink.copy(alpha = .75f), Offset(4.8f, 9.8f), Size(9.2f, 9.2f), CornerRadius(1.8f))
        }
        LiquidSymbol.SliderHorizontal -> {
            iconLine(ink, 4f, 6.5f, 20f, 6.5f); iconLine(ink, 4f, 12f, 20f, 12f); iconLine(ink, 4f, 17.5f, 20f, 17.5f)
            drawCircle(ink, 2f, Offset(9f, 6.5f)); drawCircle(ink, 2f, Offset(15f, 12f)); drawCircle(ink, 2f, Offset(11f, 17.5f))
        }
        LiquidSymbol.HandTap -> {
            iconLine(ink, 11.5f, 9f, 11.5f, 20f)
            iconOutline(ink, scratchPath) { moveTo(7.5f, 17.5f); lineTo(7.5f, 13f); lineTo(9.5f, 13f); lineTo(9.5f, 9f); quadraticTo(11.5f, 7f, 13.5f, 9f); lineTo(13.5f, 12.2f); lineTo(17f, 12.2f); quadraticTo(19f, 12.5f, 18.6f, 16f); lineTo(17.5f, 20f); lineTo(9.2f, 20f); close() }
            drawArc(ink, 205f, 130f, false, Offset(6.5f, 3.5f), Size(10f, 8f), style = IconPen)
        }
        LiquidSymbol.TextCursor -> {
            iconLine(ink, 12f, 4f, 12f, 20f)
            iconOutline(ink, scratchPath) { moveTo(8.5f, 3.8f); quadraticTo(12f, 4.5f, 15.5f, 3.8f) }
            iconOutline(ink, scratchPath) { moveTo(8.5f, 20.2f); quadraticTo(12f, 19.5f, 15.5f, 20.2f) }
            iconLine(ink, 9.5f, 12f, 14.5f, 12f)
        }
        LiquidSymbol.Gauge -> {
            drawArc(ink, 140f, 260f, false, Offset(3.2f, 3.8f), Size(17.6f, 17.6f), style = IconPen)
            iconLine(ink, 12f, 13f, 16.5f, 8.5f)
            drawCircle(ink, 1.8f, Offset(12f, 13f))
        }
        LiquidSymbol.CircleHalfFilled -> {
            drawCircle(ink, 8.8f, Offset(12f, 12f), style = IconPen)
            drawArc(ink, -90f, 180f, true, Offset(3.2f, 3.2f), Size(17.6f, 17.6f))
        }
        LiquidSymbol.SquareOnCircle -> {
            drawCircle(ink, 6.3f, Offset(9.5f, 9.5f), style = IconPen)
            drawRoundRect(ink, Offset(10.5f, 10.5f), Size(10.2f, 10.2f), CornerRadius(2.4f), style = IconPen)
        }
        LiquidSymbol.MenuCard -> {
            drawRoundRect(ink, Offset(4.5f, 3.5f), Size(15f, 17f), CornerRadius(2.8f), style = IconPen)
            iconLine(ink, 8f, 8.5f, 16f, 8.5f); iconLine(ink, 8f, 12f, 16f, 12f); iconLine(ink, 8f, 15.5f, 13.5f, 15.5f)
        }
        LiquidSymbol.HandDraw -> {
            iconOutline(ink, scratchPath) { moveTo(4.5f, 16.5f); cubicTo(7.5f, 12f, 11f, 20f, 14.5f, 14.5f) }
            iconOutline(ink, scratchPath) { moveTo(12.5f, 6.5f); lineTo(16.5f, 4f); lineTo(18.8f, 7.5f); lineTo(14.5f, 10.2f); close() }
        }
        LiquidSymbol.ListBulletRectangle -> {
            drawRoundRect(ink, Offset(3f, 4.5f), Size(18f, 15f), CornerRadius(2.6f), style = IconPen)
            for (y in BulletRowYs) { drawCircle(ink, 1.1f, Offset(7f, y)); iconLine(ink, 9.8f, y, 17.5f, y) }
        }
        else -> Unit
    }
}

private fun DrawScope.drawVisualSymbol(symbol: LiquidSymbol, ink: Color, scratchPath: Path) {
    when (symbol) {
        LiquidSymbol.Sparkles -> {
            iconFilled(ink, scratchPath) {
                moveTo(10.5f, 3.5f); quadraticTo(11f, 10f, 17f, 10.5f)
                quadraticTo(11f, 11f, 10.5f, 17.5f); quadraticTo(10f, 11f, 4f, 10.5f)
                quadraticTo(10f, 10f, 10.5f, 3.5f); close()
            }
            iconFilled(ink, scratchPath) {
                moveTo(18f, 13.5f); quadraticTo(18.3f, 16.5f, 21f, 16.8f)
                quadraticTo(18.3f, 17.1f, 18f, 20f); quadraticTo(17.7f, 17.1f, 15f, 16.8f)
                quadraticTo(17.7f, 16.5f, 18f, 13.5f); close()
            }
        }
        LiquidSymbol.Star, LiquidSymbol.StarFilled -> {
            if (symbol == LiquidSymbol.StarFilled) {
                iconFilled(ink, scratchPath) {
                    moveTo(12f, 3.5f); lineTo(14.6f, 8.8f); lineTo(20.4f, 9.6f)
                    lineTo(16.2f, 13.7f); lineTo(17.2f, 19.5f); lineTo(12f, 16.8f)
                    lineTo(6.8f, 19.5f); lineTo(7.8f, 13.7f); lineTo(3.6f, 9.6f)
                    lineTo(9.4f, 8.8f); close()
                }
            } else {
                iconOutline(ink, scratchPath) {
                    moveTo(12f, 3.5f); lineTo(14.6f, 8.8f); lineTo(20.4f, 9.6f)
                    lineTo(16.2f, 13.7f); lineTo(17.2f, 19.5f); lineTo(12f, 16.8f)
                    lineTo(6.8f, 19.5f); lineTo(7.8f, 13.7f); lineTo(3.6f, 9.6f)
                    lineTo(9.4f, 8.8f); close()
                }
            }
        }
        LiquidSymbol.Heart -> iconFilled(ink, scratchPath) {
            moveTo(12f, 20f)
            cubicTo(4f, 14.5f, 3.2f, 8.5f, 6.8f, 5.8f)
            cubicTo(9.1f, 4.1f, 11.4f, 5.4f, 12f, 7.2f)
            cubicTo(12.6f, 5.4f, 14.9f, 4.1f, 17.2f, 5.8f)
            cubicTo(20.8f, 8.5f, 20f, 14.5f, 12f, 20f); close()
        }
        LiquidSymbol.Sun -> {
            drawCircle(ink, 4.6f, Offset(12f, 12f))
            for (i in 0 until 8) {
                val c = SunRayCos[i]
                val s = SunRaySin[i]
                iconLine(ink, 12f + 7f * c, 12f + 7f * s, 12f + 9.5f * c, 12f + 9.5f * s)
            }
        }
        LiquidSymbol.Moon -> iconFilled(ink, scratchPath) {
            moveTo(15.5f, 4.2f)
            cubicTo(9.5f, 4.8f, 6.5f, 10.5f, 8.2f, 15.5f)
            cubicTo(9.8f, 20f, 15.5f, 21f, 19.5f, 17.8f)
            cubicTo(14.5f, 18.6f, 11.2f, 14.8f, 12.2f, 10.2f)
            cubicTo(12.8f, 7.6f, 14.2f, 5.6f, 15.5f, 4.2f); close()
        }
        LiquidSymbol.Play -> iconFilled(ink, scratchPath) { moveTo(7.5f, 5f); lineTo(19f, 12f); lineTo(7.5f, 19f); close() }
        LiquidSymbol.Globe -> {
            drawCircle(ink, 9f, Offset(12f, 12f), style = IconPen)
            iconLine(ink, 3.2f, 12f, 20.8f, 12f)
            drawOval(ink, Offset(7.5f, 3f), Size(9f, 18f), style = IconPen)
        }
        LiquidSymbol.Map -> {
            iconOutline(ink, scratchPath) {
                moveTo(3.5f, 6.5f); lineTo(9f, 4.5f); lineTo(15f, 6.8f); lineTo(20.5f, 4.8f)
                lineTo(20.5f, 17.5f); lineTo(15f, 19.5f); lineTo(9f, 17.2f); lineTo(3.5f, 19.2f); close()
            }
            iconLine(ink, 9f, 4.5f, 9f, 17.2f); iconLine(ink, 15f, 6.8f, 15f, 19.5f)
        }
        LiquidSymbol.Lightbulb -> {
            iconOutline(ink, scratchPath) {
                moveTo(9f, 15.5f)
                cubicTo(6f, 13.5f, 6f, 6.5f, 12f, 5f)
                cubicTo(18f, 6.5f, 18f, 13.5f, 15f, 15.5f)
            }
            iconLine(ink, 9.5f, 18f, 14.5f, 18f); iconLine(ink, 10.5f, 20.5f, 13.5f, 20.5f)
        }
        LiquidSymbol.Chart -> {
            iconLine(ink, 3.5f, 19.5f, 20.5f, 19.5f)
            drawRoundRect(ink, Offset(5f, 12f), Size(3.2f, 7.5f), CornerRadius(1f))
            drawRoundRect(ink, Offset(10.4f, 6.5f), Size(3.2f, 13f), CornerRadius(1f))
            drawRoundRect(ink, Offset(15.8f, 9.5f), Size(3.2f, 10f), CornerRadius(1f))
        }
        LiquidSymbol.Camera -> {
            drawRoundRect(ink, Offset(3.5f, 7f), Size(17f, 12f), CornerRadius(2.6f), style = IconPen)
            iconOutline(ink, scratchPath) { moveTo(8.2f, 7f); lineTo(9.8f, 4.8f); lineTo(14.2f, 4.8f); lineTo(15.8f, 7f) }
            drawCircle(ink, 3.2f, Offset(12f, 13f), style = IconPen)
        }
        LiquidSymbol.Send -> iconFilled(ink, scratchPath) {
            moveTo(20.5f, 4f); lineTo(3.5f, 11f); lineTo(10.5f, 13.5f)
            lineTo(13f, 20.5f); close()
        }
        LiquidSymbol.Code -> {
            iconOutline(ink, scratchPath) { moveTo(9f, 5f); cubicTo(6.5f, 5f, 7f, 9.5f, 5f, 12f); cubicTo(7f, 14.5f, 6.5f, 19f, 9f, 19f) }
            iconOutline(ink, scratchPath) { moveTo(15f, 5f); cubicTo(17.5f, 5f, 17f, 9.5f, 19f, 12f); cubicTo(17f, 14.5f, 17.5f, 19f, 15f, 19f) }
        }
        LiquidSymbol.Drop -> {
            iconOutline(ink, scratchPath) { moveTo(12f, 3.5f); cubicTo(17.5f, 10.5f, 18.5f, 15f, 16.2f, 18f); cubicTo(14f, 20.8f, 10f, 20.8f, 7.8f, 18f); cubicTo(5.5f, 15f, 6.5f, 10.5f, 12f, 3.5f); close() }
        }
        LiquidSymbol.Palette -> {
            drawCircle(ink, 8.8f, Offset(12f, 12f), style = IconPen)
            for (i in 0 until 4) {
                drawCircle(ink, 1.3f, Offset(PaletteDotXs[i], PaletteDotYs[i]))
            }
        }
        else -> Unit
    }
}
