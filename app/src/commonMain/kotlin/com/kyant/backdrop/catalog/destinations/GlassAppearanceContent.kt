package com.kyant.backdrop.catalog.destinations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.catalog.BackdropDemoScaffold
import com.kyant.backdrop.components.LiquidOpacitySlider
import com.kyant.backdrop.components.liquidGlassAppearance
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GlassAppearanceContent(opacity: Float, onOpacityChange: (Float) -> Unit) {
    val appearance = liquidGlassAppearance()
    val animationScope = rememberCoroutineScope()
    val offsetAnimation = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val zoomAnimation = remember { Animatable(1f) }
    val rotationAnimation = remember { Animatable(0f) }

    BackdropDemoScaffold(content = { backdrop ->
        Box(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(24f.dp) },
                    effects = {
                        blur(appearance.blurRadius.toPx())
                        lens(24f.dp.toPx(), size.minDimension / 2f, depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    layerBlock = {
                        val offset = offsetAnimation.value
                        val zoom = zoomAnimation.value
                        val rotation = rotationAnimation.value
                        translationX = offset.x
                        translationY = offset.y
                        scaleX = zoom
                        scaleY = zoom
                        rotationZ = rotation
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    },
                    onDrawSurface = { drawRect(appearance.surface) }
                )
                .pointerInput(animationScope) {
                    fun Offset.rotateBy(angle: Float): Offset {
                        val angleInRadians = angle * (PI / 180)
                        val cos = cos(angleInRadians)
                        val sin = sin(angleInRadians)
                        return Offset((x * cos - y * sin).toFloat(), (x * sin + y * cos).toFloat())
                    }

                    detectTransformGestures { _, pan, gestureZoom, gestureRotate ->
                        val offset = offsetAnimation.value
                        val zoom = zoomAnimation.value
                        val rotation = rotationAnimation.value

                        val targetZoom = zoom * gestureZoom
                        val targetRotation = rotation + gestureRotate
                        val targetOffset = offset + pan.rotateBy(targetRotation) * targetZoom

                        animationScope.launch {
                            offsetAnimation.snapTo(targetOffset)
                            zoomAnimation.snapTo(targetZoom)
                            rotationAnimation.snapTo(targetRotation)
                        }
                    }
                }
                .size(160f.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicText("Glass", style = TextStyle(appearance.contentColor, 24.sp))
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 98.dp)
            .widthIn(max = 420.dp).fillMaxWidth().background(
                if (appearance.dark) Color(0xff242426) else Color(0xfff7f6f4), RoundedCornerShape(22.dp)
            ).padding(horizontal = 18.dp, vertical = 10.dp)) {
            BasicText("Material opacity  " + (opacity * 100).fastRoundToInt() + "%",
                style = TextStyle(appearance.contentColor, 16.sp, textAlign = TextAlign.Start))
            LiquidOpacitySlider(opacity, onOpacityChange, "Material opacity")
        }
    })
}
