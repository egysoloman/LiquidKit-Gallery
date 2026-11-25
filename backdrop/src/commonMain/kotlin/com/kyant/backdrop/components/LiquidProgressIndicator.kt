package com.kyant.backdrop.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Determinate, read-only progress. The slider is the adjustable control. */
@Composable
fun LiquidProgressIndicator(value: Float, tint: Color, modifier: Modifier = Modifier) {
    LiquidProgressIndicator(value, SolidColor(tint), modifier)
}

/** Determinate, read-only progress supporting solid or gradient [Brush] fills. */
@Composable
fun LiquidProgressIndicator(
    value: Float,
    brush: Brush,
    modifier: Modifier = Modifier,
    trackColor: Color = liquidGlassAppearance().contentColor.copy(alpha = .14f),
) {
    val progress = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    val fill by animateFloatAsState(progress, label = "Progress")
    Box(modifier.fillMaxWidth().progressSemantics(progress).height(4.dp).clip(CircleShape)
        .background(trackColor)) {
        Spacer(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).background(brush, CircleShape))
    }
}

/** 12-spoke Apple-style indeterminate spinner replacing platform Material rings. */
@Composable
fun LiquidActivityIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
    colors: LiquidListColors = liquidListColors(),
    color: Color = colors.secondary,
    size: Dp = 22.dp,
) {
    val transition = rememberInfiniteTransition(label = "ActivityIndicator")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(840, easing = LinearEasing), RepeatMode.Restart),
        label = "ActivityIndicatorPhase",
    )
    val activeStep = phase.toInt() % 12
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            val inner = radius * .42f
            val outer = radius * .92f
            val stroke = (this.size.minDimension / 8.8f).coerceAtLeast(1.8f)
            for (i in 0 until 12) {
                val age = (i - activeStep + 12) % 12
                val alpha = .18f + .76f * (age / 11f)
                val angle = (i * 30f - 90f) * PI.toFloat() / 180f
                val dx = cos(angle)
                val dy = sin(angle)
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = Offset(center.x + dx * inner, center.y + dy * inner),
                    end = Offset(center.x + dx * outer, center.y + dy * outer),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        if (!label.isNullOrEmpty()) {
            BasicText(label, style = TextStyle(colors.secondary, 15.sp))
        }
    }
}
