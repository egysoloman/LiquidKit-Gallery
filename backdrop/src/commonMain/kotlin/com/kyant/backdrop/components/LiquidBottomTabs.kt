package com.kyant.backdrop.components

import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.layer.drawLayer
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SharedRenderEffectShaderCache
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.internal.CircularRefractionProfile
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.drawEpoch
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.isLayerBacked
import com.kyant.backdrop.effects.runtimeShaderEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh
import kotlin.math.roundToInt

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) = LiquidBottomTabsLayout(selectedTabIndex, onTabSelected, backdrop, tabsCount, modifier, tint, 64.dp, content)

@Composable
internal fun LiquidBottomTabsLayout(
    selectedTabIndex: () -> Int, onTabSelected: (Int) -> Unit, backdrop: Backdrop, tabsCount: Int,
    modifier: Modifier, tint: Color, height: Dp, content: @Composable RowScope.() -> Unit,
) {
    if (tabsCount <= 0) return
    val appearance = liquidGlassAppearance()
    val plateRim = rememberLiquidRimHighlight(appearance.dark, layeredEdge = true, reflectionInSurface = true)
    val lensRim = rememberLiquidRimHighlight(appearance.dark, layeredEdge = true)
    val accent = if (tint.isSpecified) tint else if (appearance.dark) Color(0xff0091ff) else Color(0xff0088ff)
    val selected = selectedTabIndex().coerceIn(0, tabsCount - 1)
    val currentSelection by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onTabSelected)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tokens = LocalLiquidGlassTokens.current
    val tab = tokens.tabs
    var finger by remember(tabsCount, rtl) { mutableStateOf<Float?>(null) }
    var contact by remember { mutableStateOf(Offset.Zero) }
    var tracking by remember(tabsCount, rtl) { mutableStateOf(false) }
    var pull by remember(tabsCount, rtl) { mutableStateOf(Offset.Zero) }
    var dragCommit by remember { mutableStateOf<Int?>(null) }
    val destination = finger ?: selected.toFloat()
    // These are the two physical ends in logical tab order. On reversal they keep their
    // positions and velocities, and exchange which spring leads the new movement.
    val travel = updateTransition(destination, label = "Tab edges")
    val startEdge by travel.animateFloat(transitionSpec = {
        tab.edgeSpring(leading = targetState < initialState, tracking = tracking).spec
    }, label = "Tab start edge") { it }
    val endEdge by travel.animateFloat(transitionSpec = {
        tab.edgeSpring(leading = targetState > initialState, tracking = tracking).spec
    }, label = "Tab end edge") { it }
    val held by animateFloatAsState(if (finger != null) 1f else 0f,
        if (finger != null) tokens.press.engage.spec else tokens.press.centerRelease.spec, label = "Tab contact")
    val yOffset by animateFloatAsState(pull.y, if (tracking) tab.hoverPullSpring.spec else tokens.press.centerRelease.spec,
        label = "Tab vertical pull")
    // A signed strain is sprung independently from translation, so the outline rebounds on release.
    val aspect by animateFloatAsState(abs(pull.y) - abs(pull.x),
        if (tracking) tab.hoverPullSpring.spec else tokens.press.release.spec, label = "Tab aspect")
    val tapLift = remember { Animatable(0f) }
    var previousTab by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) {
        val tapped = selected != previousTab && dragCommit != selected
        previousTab = selected
        dragCommit = null
        if (tapped) {
            tapLift.animateTo(1f, tween(tab.tapLiftMillis))
            delay(80)
            tapLift.animateTo(0f, tokens.press.centerRelease.spec)
        } else tapLift.snapTo(0f)
    }
    fun travelStrain() = tabEdgeStrain(startEdge, endEdge)
    fun strength() = maxOf(held, tapLift.value, abs(travelStrain()) * 3f).coerceIn(0f, 1f)
    fun plateScale() = 1f + tab.plateLift * strength()
    val supportsOptics = remember { isRuntimeShaderSupported() }
    val backing = tab.backingFraction(appearance)
    val blurRadius = with(LocalDensity.current) {
        (if (appearance.dark) tab.darkDenseDiffusionRadius else tab.denseDiffusionRadius).toPx()
    }
    val blurDownscale = 4f
    val invBlurDownscale = 0.25f
    val diffuseEffect = remember(blurRadius, supportsOptics) {
        if (supportsOptics && blurRadius > 0f) {
            val r = blurRadius * invBlurDownscale
            BlurEffect(r, r, TileMode.Clamp)
        } else null
    }
    val blurHaloSmallPx = remember(blurRadius, diffuseEffect) {
        if (diffuseEffect != null) kotlin.math.ceil(blurRadius * invBlurDownscale * 3f).toInt().coerceAtLeast(8) else 0
    }
    val transmissionHolder = remember { arrayOfNulls<RuntimeShader>(1) }
    fun obtainTransmission(): RuntimeShader? {
        if (!supportsOptics) return null
        return transmissionHolder[0] ?: SharedRenderEffectShaderCache.obtainRuntimeShader("TabDiffusionMask", TabDiffusionMask).also { transmissionHolder[0] = it }
    }
    val clear = 1f - appearance.opacity
    val plateMix = if (appearance.dark) {
        lerp(0.14f, 0.94f, appearance.opacity.pow(0.75f))
    } else {
        appearance.opacity.pow(tab.diffusionResponse.coerceAtLeast(.01f))
    }
    val clearMix = tab.clearLensDiffusionMix.coerceIn(0f, 1f) * clear * clear
    val lensMix = if (appearance.dark) {
        lerp(0.03f, 0.22f, appearance.opacity)
    } else {
        clearMix + appearance.opacity
    }
    val baseAlpha = lerp(.22f, 1f, backing)
    // On a neutral page the native plate barely changes. Vary how much of the scene passes through it.
    val base = if (appearance.dark) {
        val channel = (32f / 255f) / baseAlpha
        Color(channel, channel, channel, baseAlpha)
    } else Color(0xfffdfdfd).copy(alpha = lerp(.24f, 1f, backing))
    val selectionInk = if (appearance.dark) Color.White.copy(alpha = .13f) else Color.Black.copy(alpha = .058f)
    val haptics = rememberLiquidHaptics()

    BoxWithConstraints(modifier.height(height).pointerInput(tabsCount, rtl) {
        val inset = 8.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(false, PointerEventPass.Initial)
            if (down.isConsumed) return@awaitEachGesture
            val slot = ((size.width - 2 * inset) / tabsCount).coerceAtLeast(1f)
            val first = (((if (rtl) size.width - down.position.x else down.position.x) - inset) / slot)
                .toInt().coerceIn(0, tabsCount - 1)
            val direction = if (rtl) -1f else 1f
            val slop = viewConfiguration.touchSlop
            var claimed = false
            var lastHoverTab = first
            try {
                // A distant touch starts moving the glass immediately; the page still belongs to the old tab.
                down.consume()
                contact = down.position
                finger = first.toFloat()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull()
                    if (change == null) { event.changes.forEach { it.consume() }; break }
                    if (change.id != down.id || change.isConsumed) break
                    contact = change.position
                    val delta = change.position - down.position
                    val longPress = change.uptimeMillis - down.uptimeMillis >= 175L
                    claimed = claimed || longPress || delta.getDistance() > slop
                    if (claimed) {
                        val raw = first + delta.x / slot * direction
                        val stop = raw.coerceIn(0f, (tabsCount - 1).toFloat())
                        val hoverTab = stop.roundToInt()
                        if (hoverTab != lastHoverTab) {
                            lastHoverTab = hoverTab
                            haptics(LiquidHaptic.SelectionTick)
                        }
                        val limit = minOf(7.dp.toPx() / slot, .16f)
                        finger = stop + limit * tanh((raw - stop) / limit)
                        if (delta.getDistance() > slop) tracking = true
                        pull = Offset(tanh(raw - stop), tanh(delta.y / size.height.coerceAtLeast(1)))
                        change.consume()
                        if (!change.pressed) {
                            // Release commits the item under the lens, including diagonal releases outside
                            // the visual plate. Only cancellation or loss of the pointer abandons a drag.
                            val next = stop.roundToInt()
                            if (next != currentSelection) { dragCommit = next; select(next) }
                        }
                    }
                    if (!change.pressed) {
                        if (!claimed && change.position.x in 0f..size.width.toFloat() &&
                            change.position.y in 0f..size.height.toFloat()) {
                            // Commit before removing the finger target. Semantics and keyboard
                            // activation still use the individual child's click action.
                            change.consume()
                            if (first != currentSelection) haptics(LiquidHaptic.SelectionTick)
                            select(first)
                        }
                        break
                    }
                }
            } finally {
                finger = null
                tracking = false
                pull = Offset.Zero
            }
        }
    }, contentAlignment = AbsoluteAlignment.CenterLeft) {
        val inset = 4.dp
        val horizontalInset = 8.dp
        val barMaxWidth = maxWidth
        val width = ((barMaxWidth - horizontalInset * 2) / tabsCount).coerceAtLeast(0.dp)
        val lensWidth = width + minOf(8.dp, width * .10f)
        val innerHeight = (height - inset * 2).coerceAtLeast(0.dp)
        val bevelHolder = remember { arrayOfNulls<RuntimeShader>(1) }
        val bevelBrushHolder = remember { arrayOfNulls<ShaderBrush>(1) }
        fun tapAmount() = tapLift.value * (1f - held.coerceIn(0f, 1f))
        fun lensWidthScale() = 1f + tab.heldWidthGain * held + .17f * tapAmount() +
            tab.travelStretch * travelStrain() - .35f * tab.pullStretch * aspect
        val heightGain = tab.heldHeightGain + if (appearance.dark) 0f else tab.lightHeldHeightGain
        fun lensHeightScale() = 1f + heightGain * held + .04f * tapAmount() -
            .21f * travelStrain() + tab.pullStretch * aspect
        fun physicalIndex(): Float {
            val center = (startEdge + endEdge) * .5f
            return if (rtl) tabsCount - 1f - center else center
        }
        fun androidx.compose.ui.unit.Density.lensCenterX(barWidth: Float): Float {
            val slotCenter = horizontalInset.toPx() + (physicalIndex() + .5f) * width.toPx()
            return barWidth / 2f + (slotCenter - barWidth / 2f) * plateScale()
        }
        fun DrawScope.lensRect(): Rect {
            val center = Offset(lensCenterX(size.width), size.height * .5f + yOffset * 3.dp.toPx())
            val half = Size(lensWidth.toPx() * lensWidthScale(), innerHeight.toPx() * lensHeightScale()) * (plateScale() / 2f)
            return Rect(center.x - half.width, center.y - half.height, center.x + half.width, center.y + half.height)
        }
        fun DrawScope.bevel(amount: Float) {
            if (!supportsOptics || amount <= .001f) return
            val bevel = bevelHolder[0] ?: SharedRenderEffectShaderCache.obtainRuntimeShader("TabBevelShader", TabBevelShader).also { bevelHolder[0] = it }
            val bevelBrush = bevelBrushHolder[0] ?: ShaderBrush(bevel.asComposeShader()).also { bevelBrushHolder[0] = it }
            val fingerX = contact.x - lensCenterX(barMaxWidth.toPx())
            val fingerY = contact.y - height.toPx() * .5f - yOffset * 3.dp.toPx()
            bevel.setFloatUniform("contact", (2f * fingerX / size.width).coerceIn(-1f, 1f) * held,
                (2f * fingerY / size.height).coerceIn(-1f, 1f) * held)
            bevel.setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
            bevel.setFloatUniform("density", density)
            bevel.setFloatUniform("bevelWidth", tab.hoverBevelWidth.toPx().coerceAtLeast(1f))
            bevel.setFloatUniform("dark", if (appearance.dark) 1f else 0f)
            bevel.setFloatUniform("amount", amount)
            drawRect(bevelBrush)
        }
        // Record the small clipped slice of the backing scene ONCE per frame and share it across
        // plate diffusion, lens refraction, and ink vibrancy instead of replaying the full screen 3 times.
        var plateCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
        var platePosVersion by remember { mutableLongStateOf(0L) }
        var lastObservedPlatePos by remember { mutableStateOf(Offset.Unspecified) }
        val backdropSlice = rememberGraphicsLayer()
        val blurredSlice = rememberGraphicsLayer()
        val maskLayer = rememberGraphicsLayer()
        val plateGeom = remember { PlateGeometryHolder() }
        val sliceHaloPx = with(LocalDensity.current) { 28.dp.roundToPx() }
        val sliceBackdrop = remember(backdrop, backdropSlice, sliceHaloPx) {
            object : Backdrop {
                override val isCoordinatesDependent: Boolean = true
                override fun DrawScope.drawBackdrop(
                    density: Density,
                    coordinates: LayoutCoordinates?,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                ) {
                    @Suppress("UNUSED_VARIABLE")
                    val posVer = platePosVersion
                    val root = plateCoordinates?.takeIf { it.isAttached } ?: return
                    val target = coordinates?.takeIf { it.isAttached } ?: return
                    val offset = try { root.localPositionOf(target) } catch (_: Exception) { Offset.Zero }
                    translate(-offset.x - sliceHaloPx, -offset.y - sliceHaloPx) {
                        drawLayer(backdropSlice)
                    }
                }
            }
        }
        val plateImage = rememberGraphicsLayer()
        val lensScene = rememberGraphicsLayer()
        val plateShaderHolder = remember { arrayOfNulls<RuntimeShader>(1) }
        Box(Modifier.matchParentSize().onGloballyPositioned {
            plateCoordinates = it
            if (it.isAttached) {
                val p = it.positionInWindow()
                if (p != lastObservedPlatePos) {
                    lastObservedPlatePos = p
                    platePosVersion++
                }
            }
        }.drawWithCache {
            val halo = sliceHaloPx
            val w = size.width.toInt().coerceAtLeast(1)
            val h = size.height.toInt().coerceAtLeast(1)
            val padded = IntSize(w + 2 * halo, h + 2 * halo)
            val lensMargin = kotlin.math.ceil((tab.lensDiffusion + tab.dispersion + 6.dp).toPx()).toInt()
            val lensCropW = kotlin.math.ceil(lensWidth.toPx() * 1.80f + 2 * lensMargin).toInt().coerceIn(1, padded.width)
            val lensCropH = kotlin.math.ceil(innerHeight.toPx() * 1.85f + 2 * lensMargin).toInt().coerceIn(1, padded.height)
            val lensCropSize = IntSize(lensCropW, lensCropH)
            val path = Path()
            var cachedEpoch = Long.MIN_VALUE
            var cachedBackdrop: Backdrop? = null
            var cachedDark = !appearance.dark
            var cachedPlatePos = Offset.Unspecified
            var cachedSize = IntSize.Zero
            var cachedBlurRadius = -1f
            var cachedBlurHalo = -1
            var blurRecorded = false
            onDrawWithContent {
                @Suppress("UNUSED_VARIABLE")
                val posVer = platePosVersion
                val active = strength()
                val coordinates = plateCoordinates
                if (coordinates?.isAttached == true && size.width >= 1f && size.height >= 1f) {
                    val canCache = backdrop.isLayerBacked()
                    val platePos = coordinates.positionInWindow()
                    val epoch = backdrop.drawEpoch()
                    val sliceDirty = !canCache ||
                        cachedBackdrop !== backdrop ||
                        cachedEpoch != epoch ||
                        cachedDark != appearance.dark ||
                        cachedPlatePos != platePos ||
                        cachedSize != IntSize(w, h)
                    if (sliceDirty) {
                        backdropSlice.record(size = padded) {
                            drawRect(if (appearance.dark) Color.Black else Color.White)
                            translate(halo.toFloat(), halo.toFloat()) {
                                with(backdrop) { drawBackdrop(this@onDrawWithContent, coordinates) }
                            }
                        }
                        cachedBackdrop = backdrop
                        cachedEpoch = epoch
                        cachedDark = appearance.dark
                        cachedPlatePos = platePos
                        cachedSize = IntSize(w, h)
                        blurRecorded = false
                    }
                    val curBlurHalo = blurHaloSmallPx
                    if (diffuseEffect != null && (!blurRecorded || cachedBlurRadius != blurRadius || cachedBlurHalo != curBlurHalo)) {
                        val smallW = kotlin.math.ceil(w * invBlurDownscale).toInt().coerceAtLeast(1) + 2 * curBlurHalo
                        val smallH = kotlin.math.ceil(h * invBlurDownscale).toInt().coerceAtLeast(1) + 2 * curBlurHalo
                        val blurSize = IntSize(smallW, smallH)
                        plateGeom.blurWidth = smallW
                        plateGeom.blurHeight = smallH
                        blurredSlice.renderEffect = diffuseEffect
                        blurredSlice.alpha = 1f
                        blurredSlice.record(size = blurSize) {
                            drawRect(if (appearance.dark) Color.Black else Color.White)
                            translate(curBlurHalo - halo * invBlurDownscale, curBlurHalo - halo * invBlurDownscale) {
                                withTransform({ scale(invBlurDownscale, invBlurDownscale, pivot = Offset.Zero) }) {
                                    drawLayer(backdropSlice)
                                }
                            }
                        }
                        cachedBlurRadius = blurRadius
                        cachedBlurHalo = curBlurHalo
                        blurRecorded = true
                    }
                }
                if (!supportsOptics || active <= .001f || coordinates?.isAttached != true) {
                    drawContent()
                    return@onDrawWithContent
                }
                val plateShader = plateShaderHolder[0] ?: SharedRenderEffectShaderCache.obtainRuntimeShader("TabPlateShader", TabPlateShader).also { plateShaderHolder[0] = it }
                plateImage.record(size = padded) {
                    translate(halo.toFloat(), halo.toFloat()) { this@onDrawWithContent.drawContent() }
                }
                translate(-halo.toFloat(), -halo.toFloat()) { drawLayer(plateImage) }
                val rect = lensRect()
                val cropLeft = (rect.center.x - lensCropW * 0.5f).roundToInt().toFloat()
                val cropTop = (rect.center.y - lensCropH * 0.5f).roundToInt().toFloat()
                plateShader.setFloatUniform("rect", rect.center.x - cropLeft, rect.center.y - cropTop, rect.width / 2, rect.height / 2)
                val scale = plateScale()
                plateShader.setFloatUniform("plate", center.x - cropLeft, center.y - cropTop, center.x * scale, center.y * scale)
                plateShader.setFloatUniform("strength", active)
                plateShader.setFloatUniform("dark", if (appearance.dark) 1f else 0f)
                plateShader.setFloatUniform("density", density)
                plateShader.setFloatUniform("reduction", tab.plateMinification)
                plateShader.setFloatUniform("joinWidth", tab.rimJoinDepth.toPx())
                plateShader.setFloatUniform("interfaceShade", tab.lightInterfaceShade.coerceIn(0f, 1f))
                plateShader.setFloatUniform("dispersion", tab.dispersion.toPx())
                plateShader.setFloatUniform("bevelWidth", tab.hoverBevelWidth.toPx().coerceAtLeast(1f))
                val diffusion = tab.lensDiffusion * if (appearance.dark) tab.darkLensDiffusionScale else 1f
                plateShader.setFloatUniform("diffusion", diffusion.toPx())
                lensScene.renderEffect = RuntimeShaderEffect(plateShader, "content")
                lensScene.record(size = lensCropSize) {
                    translate(-cropLeft - halo, -cropTop - halo) {
                        drawLayer(backdropSlice)
                        drawLayer(plateImage)
                    }
                }
                path.reset()
                path.addLiquidCapsule(rect)
                clipPath(path) { translate(cropLeft, cropTop) { drawLayer(lensScene) } }
            }
        }) {
            // Grow the geometry in layout space. Scaling a recorded backdrop would move
            // the stripe twice, leaving a vertical step at the edge of the plate.
            Spacer(Modifier.matchParentSize().layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val enlarged = measurable.measure(Constraints.fixed((w * plateScale()).roundToInt(), (h * plateScale()).roundToInt()))
                val dx = (w - enlarged.width) / 2
                val dy = (h - enlarged.height) / 2
                plateGeom.dx = dx
                plateGeom.dy = dy
                plateGeom.width = enlarged.width
                plateGeom.height = enlarged.height
                layout(w, h) { enlarged.place(dx, dy) }
            }.drawBackdrop(sliceBackdrop, { LiquidCapsule }, effects = {
                if (supportsOptics) {
                    val spread = (if (appearance.dark) tab.darkDiffusionRadius else tab.diffusionRadius).toPx()
                    padding = kotlin.math.ceil(spread * 2.5f + 3.dp.toPx())
                    runtimeShaderEffect("Tab backing material", TabMaterialShader, "content") {
                        setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
                        setFloatUniform("inset", padding)
                        setFloatUniform("density", density)
                        setFloatUniform("spread", spread)
                        setFloatUniform("softMix", tab.diffusionMix)
                        setFloatUniform("reflection", tab.rimReflection * tokens.highlightIntensity)
                        setFloatUniform("hairline", lerp(tab.topHairlineOpacity, tab.raisedHairlineOpacity, strength()) *
                            tokens.highlightIntensity.coerceIn(0f, 1f))
                        setFloatUniform("dark", if (appearance.dark) 1f else 0f)
                        setFloatUniform("backing", backing)
                        setFloatUniform("active", strength())
                    }
                } else liquidSurfaceLens(appearance.controlBlurRadius)
            }, highlight = {
                val rimStrength = lerp(tab.restingPlateRimStrength.coerceIn(0f, 1f), if (appearance.dark) 0.58f else 1f, strength())
                plateRim.copy(alpha = rimStrength * tokens.highlightIntensity.coerceIn(0f, 1f))
            }, shadow = { LiquidSurfaceShadow }, onDrawBackdrop = { drawSource ->
                val lift = strength()
                val lensTransmission = if (lift > .001f && abs(plateMix - lensMix) > .001f) obtainTransmission() else null
                val maskLens = lensTransmission != null
                if (plateMix < 1f || maskLens || diffuseEffect == null) drawSource()
                if ((plateMix > 0f || maskLens) && diffuseEffect != null) {
                    // Reuse the 4x-downsampled pre-blurred backdrop slice across animation frames;
                    // use a fixed-size mask FBO when active to avoid GPU texture reallocation.
                    val blurHaloFull = blurHaloSmallPx * blurDownscale
                    val tx = -plateGeom.dx.toFloat() - blurHaloFull
                    val ty = -plateGeom.dy.toFloat() - blurHaloFull
                    if (lensTransmission != null) {
                        val scale = plateScale()
                        val cx = (horizontalInset + width * (physicalIndex() + .5f)).toPx() * scale
                        val cy = height.toPx() * scale / 2f + yOffset * 3.dp.toPx()
                        val bx = (cx + plateGeom.dx) * invBlurDownscale + blurHaloSmallPx
                        val by = (cy + plateGeom.dy) * invBlurDownscale + blurHaloSmallPx
                        lensTransmission.setFloatUniform(
                            "rect",
                            bx,
                            by,
                            lensWidth.toPx() * lensWidthScale() * scale * 0.5f * invBlurDownscale,
                            innerHeight.toPx() * lensHeightScale() * scale * 0.5f * invBlurDownscale,
                        )
                        lensTransmission.setFloatUniform("coverage", plateMix, lerp(plateMix, lensMix, lift))
                        lensTransmission.setFloatUniform("feather", 7.dp.toPx() * invBlurDownscale)
                        maskLayer.renderEffect = RuntimeShaderEffect(lensTransmission, "content")
                        blurredSlice.alpha = 1f
                        maskLayer.record(size = IntSize(plateGeom.blurWidth.coerceAtLeast(1), plateGeom.blurHeight.coerceAtLeast(1))) {
                            drawLayer(blurredSlice)
                        }
                        translate(tx, ty) {
                            withTransform({ scale(blurDownscale, blurDownscale, pivot = Offset.Zero) }) {
                                drawLayer(maskLayer)
                            }
                        }
                    } else {
                        blurredSlice.alpha = plateMix
                        translate(tx, ty) {
                            withTransform({ scale(blurDownscale, blurDownscale, pivot = Offset.Zero) }) {
                                drawLayer(blurredSlice)
                            }
                        }
                    }
                }
            }, onDrawSurface = {
                if (!supportsOptics) drawRect(base)
                bevel(tab.platePressSheen * (if (appearance.dark) 0.08f else 1f) * tokens.highlightIntensity.coerceIn(0f, 1f) * strength())
            }))
        }
        // Build the optical mask, rim and shadow at the same deformed capsule size.
        // Stretching an already rounded stroke leaves a second, elliptical end at impact.
        Spacer(Modifier.matchParentSize().layout { measurable, constraints ->
            val barWidth = constraints.maxWidth
            val barHeight = constraints.maxHeight
            val scale = plateScale()
            val w = (lensWidth.toPx() * lensWidthScale() * scale).roundToInt().coerceAtLeast(1)
            val h = (innerHeight.toPx() * lensHeightScale() * scale).roundToInt().coerceAtLeast(1)
            val rim = measurable.measure(Constraints.fixed(w, h))
            layout(barWidth, barHeight) {
                val left = lensCenterX(barWidth.toFloat()) - w * .5f
                val top = (barHeight - h) * .5f + yOffset * 3.dp.toPx()
                rim.placeWithLayer(left.roundToInt(), top.roundToInt()) {
                    translationX = left - left.roundToInt()
                    translationY = top - top.roundToInt()
                }
            }
        }.drawBackdrop(emptyBackdrop(), { LiquidCapsule },
            effects = {}, highlight = { lensRim.copy(alpha = (tokens.highlightIntensity * strength()).coerceIn(0f, 1f)) },
            shadow = { LiquidSurfaceShadow.copy(alpha = strength(), color = Color.Black.copy(alpha = .065f)) },
            onDrawSurface = {
                drawRect(selectionInk, alpha = 1f - strength())
                bevel(tab.hoverMaterialStrength.coerceIn(0f, 1f) * tokens.highlightIntensity * strength())
            }))
        // Both paints use the same live row. The tinted lens has no second set of buttons or semantics.
        val inkLayer = rememberGraphicsLayer()
        val lensInk = rememberGraphicsLayer()
        val shader = remember { if (isRuntimeShaderSupported()) SharedRenderEffectShaderCache.obtainRuntimeShader("TabInkShader", TabInkShader) else null }
        Row(Modifier.fillMaxSize().drawWithCache {
            val path = Path()
            val paint = Paint()
            val inkMargin = kotlin.math.ceil((tab.inkRefractionAmount + tab.dispersion + 6.dp).toPx())
                .toInt().coerceAtLeast(12.dp.roundToPx())
            val inkCropW = kotlin.math.ceil(lensWidth.toPx() * 1.80f + 2 * inkMargin).toInt().coerceAtLeast(1)
            val inkCropH = kotlin.math.ceil(innerHeight.toPx() * 1.85f + 2 * inkMargin).toInt().coerceAtLeast(1)
            val inkCropSize = IntSize(inkCropW, inkCropH)
            val inkAndBackdropCropSize = IntSize(inkCropW, inkCropH * 2)
            var cachedInkEffect: RenderEffect? = null
            var lastRectCx = Float.NaN
            var lastRectCy = Float.NaN
            var lastRectHalfW = Float.NaN
            var lastRectHalfH = Float.NaN
            var lastActive = Float.NaN
            var lastFirstSlot = Float.NaN
            var lastGridY = Float.NaN
            var lastSlotScaled = Float.NaN
            var lastDark = !appearance.dark
            var lastSampleBackdrop = false
            var lastInkBacking = Float.NaN
            var lastInkColor = Color.Unspecified
            onDrawWithContent {
                val rect = lensRect()
                path.reset(); path.addLiquidCapsule(rect)
                inkLayer.record {
                    withTransform({ scale(plateScale(), plateScale(), center) }) { this@onDrawWithContent.drawContent() }
                }
                clipPath(path, ClipOp.Difference) { drawLayer(inkLayer) }
                clipPath(path) {
                    val active = strength()
                    val color = accent
                    val coordinates = plateCoordinates
                    val sampleBackdrop = tab.inkVibrancy > 0f && coordinates?.isAttached == true
                    if (shader != null && (active > .001f || sampleBackdrop)) {
                        val cropLeft = (rect.center.x - inkCropW * 0.5f).roundToInt().toFloat()
                        val cropTop = (rect.center.y - inkCropH * 0.5f).roundToInt().toFloat()
                        val rectCx = rect.center.x - cropLeft
                        val rectCy = rect.center.y - cropTop
                        val rectHalfW = rect.width / 2f
                        val rectHalfH = rect.height / 2f
                        val slotWidth = width.toPx()
                        val scale = plateScale()
                        val firstSlot = center.x + (horizontalInset.toPx() + slotWidth / 2f - center.x) * scale - cropLeft
                        val gridY = center.y - cropTop
                        val slotScaled = slotWidth * scale
                        val inkBacking = if (appearance.dark) (backing * 1.14f).coerceAtMost(1f) else backing
                        val uniformsChanged = cachedInkEffect == null ||
                            lastRectCx != rectCx || lastRectCy != rectCy ||
                            lastRectHalfW != rectHalfW || lastRectHalfH != rectHalfH ||
                            lastActive != active || lastFirstSlot != firstSlot ||
                            lastGridY != gridY || lastSlotScaled != slotScaled ||
                            lastDark != appearance.dark || lastSampleBackdrop != sampleBackdrop ||
                            lastInkBacking != inkBacking || lastInkColor != color
                        if (uniformsChanged) {
                            shader.setFloatUniform("rect", rectCx, rectCy, rectHalfW, rectHalfH)
                            shader.setFloatUniform("strength", active)
                            shader.setFloatUniform("density", density)
                            // The flat face magnifies each glyph around its own slot. Refraction
                            // then bends that image only in the narrow, curved optical shoulder.
                            shader.setFloatUniform("grid", firstSlot, gridY, slotScaled,
                                tab.heldInkMagnification.coerceIn(0f, .5f) * active)
                            shader.setFloatUniform("dispersionPx", tab.dispersion.toPx())
                            shader.setFloatUniform("bevelWidth", tab.inkRefractionWidth.toPx().coerceAtLeast(0f))
                            shader.setFloatUniform("refractionPx", tab.inkRefractionAmount.toPx().coerceAtLeast(0f))
                            shader.setFloatUniform("darkSurface", if (appearance.dark) 1f else 0f)
                            shader.setFloatUniform("sceneOffset", if (sampleBackdrop) inkCropH.toFloat() else 0f)
                            shader.setFloatUniform("vibrancy", tab.inkVibrancy.coerceIn(0f, 1f))
                            shader.setFloatUniform("backing", inkBacking)
                            shader.setColorUniform("ink", color)
                            cachedInkEffect = RuntimeShaderEffect(shader, "content")
                            lensInk.renderEffect = cachedInkEffect
                            lastRectCx = rectCx
                            lastRectCy = rectCy
                            lastRectHalfW = rectHalfW
                            lastRectHalfH = rectHalfH
                            lastActive = active
                            lastFirstSlot = firstSlot
                            lastGridY = gridY
                            lastSlotScaled = slotScaled
                            lastDark = appearance.dark
                            lastSampleBackdrop = sampleBackdrop
                            lastInkBacking = inkBacking
                            lastInkColor = color
                        }
                        // Crop lensInk to a fixed window around the moving lens so TabInkShader runs
                        // on ~1/3 the pixels and never reallocates FBOs across animation frames.
                        lensInk.record(size = if (sampleBackdrop) inkAndBackdropCropSize else inkCropSize) {
                            clipRect(bottom = inkCropH.toFloat()) {
                                translate(-cropLeft, -cropTop) { drawLayer(inkLayer) }
                            }
                            if (sampleBackdrop) clipRect(top = inkCropH.toFloat()) {
                                translate(-cropLeft - sliceHaloPx, inkCropH - cropTop - sliceHaloPx) {
                                    drawLayer(backdropSlice)
                                }
                            }
                        }
                        translate(cropLeft, cropTop) { drawLayer(lensInk) }
                    } else {
                        paint.colorFilter = ColorFilter.tint(color)
                        drawContext.canvas.saveLayer(rect, paint)
                        try {
                            withTransform({ scale(1f + .16f * active, 1f + .16f * active, rect.center) }) {
                                drawLayer(inkLayer)
                            }
                        } finally { drawContext.canvas.restore() }
                    }
                }
            }
        }.padding(horizontal = horizontalInset, vertical = inset), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

internal fun tabEdgeStrain(startEdge: Float, endEdge: Float) = (endEdge - startEdge).coerceIn(-.65f, .95f)

// The physical lens magnifies the part of each glyph underneath it as it crosses a tab.
internal const val TabInkShader = """
uniform shader content;
uniform float4 rect;
uniform float strength;
uniform float density;
uniform float dispersionPx, bevelWidth, refractionPx;
uniform float4 grid;
uniform float darkSurface;
uniform float sceneOffset, vibrancy, backing;
layout(color) uniform half4 ink;
$GlassCapsuleSDF
$CircularRefractionProfile
half coverageAt(float2 p) {
    if (grid.w <= 0.0 || grid.z <= 0.0) return content.eval(p).a;
    float index = floor((p.x - grid.x) / grid.z + 0.5);
    float2 axis = grid.xy + float2(index * grid.z, 0.0);
    // Neighboring cells agree in the gutter, including long accessibility labels.
    float gain = grid.w * (1.0 - smoothstep(0.34, 0.50, abs(p.x-axis.x) / grid.z));
    return content.eval(axis + (p - axis) / (1.0 + gain)).a;
}
half3 tabInkColor(float2 p) {
    if (sceneOffset <= 0.0 || vibrancy <= 0.0) return ink.rgb;
    float2 at = p + float2(0.0, sceneOffset);
    float2 tap = float2(2.5 * density);
    half4 sampleColor = 0.25 * (content.eval(at+tap) + content.eval(at-tap)
                      + content.eval(at+float2(tap.x,-tap.y)) + content.eval(at+float2(-tap.x,tap.y)));
    half3 neutral = half3(1.0 - darkSurface);
    half3 scene = mix(sampleColor.rgb + (1.0 - sampleColor.a) * neutral, neutral, backing);
    half luma = dot(scene, half3(0.2126, 0.7152, 0.0722));
    half low = min(min(ink.r, ink.g), ink.b);
    half chroma = max(max(ink.r, ink.g), ink.b) - low;
    half3 hue = clamp((ink.rgb - low) / max(chroma, 0.001), 0.0, 1.0);
    half3 like = pow(hue, half3(6.0));
    half3 opposite = pow(1.0 - hue, half3(6.0));
    half transmitted = dot(like, scene) / max(dot(like, half3(1.0)), 0.001);
    half complementary = dot(opposite, scene) / max(dot(opposite, half3(1.0)), 0.001);
    half absorbed = 1.0 - complementary + 0.35 * max(luma - complementary, 0.0);
    // Preserve the accent hue. The raised dark lens transmits more of the matching
    // background color; a light lens over a deep background darkens the accent.
    half lift = mix(0.031, 0.142, strength) + mix(0.29, 0.512, strength) * transmitted
              + mix(0.09, 0.17, strength) * (transmitted - luma);
    half3 shade = mix(-0.012, 0.045, strength) - absorbed * (0.204 + 0.045 * smoothstep(0.67, 1.0, ink.rgb));
    half3 delta = mix(shade, half3(lift), darkSurface * (1.0 - smoothstep(0.79, 0.97, luma)));
    return clamp(ink.rgb + delta * vibrancy * smoothstep(0.0, 0.20, chroma), 0.0, 1.0);
}
half4 main(float2 p) {
    if (sceneOffset > 0.0 && p.y >= sceneOffset) return half4(0.0);
    if (strength <= 0.0) {
        half alpha = coverageAt(p) * ink.a;
        if (alpha <= 0.0) return half4(0.0);
        return half4(tabInkColor(p) * alpha, alpha);
    }
    float2 local = p - rect.xy;
    float depth = -sdGlassCap(local, rect.zw);
    float2 dx = float2(0.6, 0.0);
    float2 g = float2(sdGlassCap(local+dx, rect.zw) - sdGlassCap(local-dx, rect.zw),
                      sdGlassCap(local+dx.yx, rect.zw) - sdGlassCap(local-dx.yx, rect.zw));
    float2 normal = g / max(length(g), 0.0001);
    float2 tangent = float2(normal.y, -normal.x);
    // The optical flat transmits the original glyph coverage. Ray bending and
    // the finite sampling footprint start at the curved shoulder of the glass.
    float band = min(bevelWidth * strength, 0.47 * min(rect.z, rect.w));
    float flank = smoothstep(0.15, 0.82, abs(normal.x));
    if (depth >= band || band <= 0.0 || flank <= 0.0) {
        half alpha = coverageAt(p) * ink.a;
        return half4(tabInkColor(p) * alpha, alpha);
    }
    float edge = (1.0 - smoothstep(0.0, band, depth)) * flank;
    // An output pixel on the cap looks towards the inside of the glass.
    // This inverse map spreads the source glyph out to the rim on either side.
    float bend = circleMap(clamp(1.0 - depth / band, 0.0, 1.0));
    float2 sampleAt = p - normal * (refractionPx * band / bevelWidth * bend * flank);
    float rim = edge * strength;
    float2 spread = normal * (1.35 * density * rim);
    half mask = 0.50 * coverageAt(sampleAt)
              + 0.25 * (coverageAt(sampleAt + spread) + coverageAt(sampleAt - spread));
    // Refraction moves the image along the normal. Integrate overlapping color
    // footprints along the tangent so a bent stroke has one soft, connected edge.
    float2 side = tangent * (dispersionPx * (edge + bend * flank) * strength);
    half nearRed = coverageAt(sampleAt + 0.5 * side);
    half nearBlue = coverageAt(sampleAt - 0.5 * side);
    half3 spectral = half3(0.35 * mask + 0.45 * nearRed + 0.20 * coverageAt(sampleAt + side),
                           0.50 * mask + 0.25 * (nearRed + nearBlue),
                           0.35 * mask + 0.45 * nearBlue + 0.20 * coverageAt(sampleAt - side));
    half3 coverage = mix(half3(mask), spectral, rim);
    half peak = max(max(coverage.r, coverage.g), coverage.b);
    if (peak <= 0.0) return half4(0.0);
    half3 tint = tabInkColor(p);
    half3 lightInk = half3(peak) - (1.0 - tint) * coverage;
    half3 darkInk = tint * coverage;
    return half4(mix(lightInk, darkInk, darkSurface) * ink.a, peak * ink.a);
}
"""

// A sharp image and a wider diffuse image share the same optical axis. The backing
// stays readable while high-contrast artwork has a soft shoulder on either side.
internal const val TabDiffusionMask = """
uniform shader content;
uniform float4 rect;
uniform float2 coverage;
uniform float feather;
$GlassCapsuleSDF
half4 main(float2 p) {
    float depth = -sdGlassCap(p - rect.xy, rect.zw);
    return content.eval(p) * mix(coverage.x, coverage.y, smoothstep(0.0, feather, depth));
}
"""

internal const val TabMaterialShader = """
uniform shader content;
uniform float2 halfSize;
uniform float inset, density, spread, softMix, dark, backing, active, reflection, hairline;
$TabEdgeResponse
$GlassCapsuleSDF
half3 scene(float2 p) {
    half4 c = content.eval(p);
    return c.rgb + (1.0 - c.a) * (1.0 - dark);
}
half4 main(float2 p) {
    float2 q = p - inset - halfSize;
    float depth = -sdGlassCap(q, halfSize) / density;
    float2 e = float2(0.7, 0.0);
    float2 g = float2(sdGlassCap(q+e, halfSize)-sdGlassCap(q-e, halfSize),
                      sdGlassCap(q+e.yx, halfSize)-sdGlassCap(q-e.yx, halfSize));
    float shoulder = smoothstep(0.0, 2.0, depth) * (1.0 - smoothstep(3.5, 13.0, depth));
    float2 ray = p - (g / max(length(g), 0.001)) * 5.2 * density * shoulder;
    // Single 6-tap Gaussian ring. Stagger the angles so a vertical edge doesn't divide taps into bands.
    half3 center = scene(ray);
    half3 color = center;
    if (softMix > 0.001) {
        half3 soft = half3(0.0);
        for (int n = 0; n < 6; ++n) {
            float a = (float(n) + 0.25) * 1.04719755;
            soft += scene(ray + 1.35 * spread * float2(cos(a), sin(a)));
        }
        color = mix(center, soft * (1.0 / 6.0), clamp(softMix, 0.0, 1.0));
    }
    half luma = dot(color, half3(0.2126, 0.7152, 0.0722));
    half3 chroma = mix(half3(luma), color, mix(1.17, 0.88, dark));
    half3 deepRaw = chroma * (0.58 - 0.10 * smoothstep(0.20, 0.72, luma))
                  + 0.125 * (1.0 - smoothstep(0.02, 0.68, luma));
    half3 deep = mix(deepRaw, half3(0.145, 0.148, 0.155), 0.36);
    half3 pale = chroma * 1.04 + 0.16 * pow(max(1.0 - luma, 0.0), 1.35);
    half3 material = mix(pale, deep, dark);
    material = mix(material, half3(mix(0.99, 0.126, dark)), backing);
    material = material * (1.0 + 0.10 * (1.0 - dark) * active) + 0.019 * (1.0 - dark) * active;
    // Lifting over saturated artwork increases chroma while compressing the
    // brightest colors. A neutral backing progressively masks that response.
    half bright = smoothstep(0.20, 0.71, luma);
    half chromatic = smoothstep(0.02, 0.22, max(max(color.r, color.g), color.b) - min(min(color.r, color.g), color.b));
    half3 deviation = material - dot(material, half3(0.2126, 0.7152, 0.0722));
    material += active * chromatic * (1.0 - backing) * mix(0.45, 0.35, dark)
              * (deviation * mix(0.24, 0.095, bright) + mix(0.023, -0.039, bright));
    half3 base = clamp(material, 0.0, 1.0);
    half level = dot(base, half3(0.2126, 0.7152, 0.0722));
    float2 normal = g / max(length(g), 0.001);
    float edge = tabEdgeResponse(depth, normal).x;
    float headroom = max(1.0 - level, 0.001);
    float darkReflect = mix(1.0, 0.42, dark);
    float light = mix(reflection * darkReflect, 0.12 * reflection * darkReflect, smoothstep(0.43, 0.99, level));
    // The resting plate picks up a subtle contour reflection along its top and bottom.
    // Finger-directed light belongs to the contact layer, which fades in as the tab lifts.
    float upright = abs(normal.y);
    float falloff = exp(-max(depth, 0.0) / 0.85);
    float softEdge = (1.0 - active) * dark * 0.035 * upright * upright * falloff;
    float brightness = (edge + softEdge) * clamp(light / headroom, 0.0, 1.0);
    half3 lit = mix(base, half3(1.0), min(brightness, 1.0));
    float distanceToLine = (depth - 0.38) / 0.39;
    float facing = mix(upright, max(-normal.y, 0.0), active);
    float line = (1.0 - dark) * facing * exp(-distanceToLine * distanceToLine);
    return half4(lit * (1.0 - clamp(hairline, 0.0, 1.0) * line), 1.0);
}
"""

// The outer ray meets the real scene at the contour. Slightly inside, the glass compresses
// the plate vertically; its straight edge curves continuously back into the uncovered plate.
internal const val TabPlateShader = """
uniform shader content;
uniform float4 rect, plate;
uniform float strength, density, reduction, dispersion, diffusion, dark, joinWidth, bevelWidth, interfaceShade;
$GlassCapsuleSDF
float halfChord(float x, float2 halfSize) {
    float r = halfSize.y;
    float axial = sdGlassCap(float2(x, 0.0), halfSize) + r;
    return sqrt(max(0.0, r*r - axial*axial));
}
half4 main(float2 p) {
    float2 q = p - rect.xy;
    float depth = -sdGlassCap(q, rect.zw) / density;
    float2 e = float2(0.65, 0.0);
    float2 g = float2(sdGlassCap(q+e, rect.zw)-sdGlassCap(q-e, rect.zw),
                      sdGlassCap(q+e.yx, rect.zw)-sdGlassCap(q-e.yx, rect.zw));
    float2 normal = g / max(length(g), 0.001);
    // Distribute the change of ray angle over enough glass that adjacent rays
    // stay in order as the lower plate's straight rim enters the rounded lens.
    float band = max(bevelWidth, density);
    float shoulder = 1.0 - smoothstep(0.0, band, depth * density);
    float2 sampleAt = float2(
        p.x + normal.x * (1.15 * band * (reduction + 0.14) * shoulder * strength),
        p.y + normal.y * (0.52 * band * reduction * shoulder * strength)
    );
    float interfaceDepth = 0.0;
    if (rect.z >= rect.w && plate.z >= plate.w) {
        // Map three points on each vertical ray: lens centre, image of the plate
        // rim, and the outer glass surface. Parallel rims stay parallel throughout
        // the interior. Only the narrow junction at either end bends into the scene.
        float lensEdge = halfChord(q.x, rect.zw);
        float plateEdge = halfChord(sampleAt.x - plate.x, plate.zw) + sign(q.y) * (plate.y - rect.y);
        float clearance = lensEdge - plateEdge;
        if (clearance > 0.0 && plateEdge > 0.0) {
            // The refracted edge rolls into the flat face. Normalize for the actual
            // gap on this side, including a vertically displaced or stretched hover.
            float span = max(0.65 * density, min(joinWidth, rect.w - plateEdge));
            float u = clamp(clearance / span, 0.0, 1.0);
            float tail = 1.0 - u;
            float join = 1.0 - tail * tail * tail * (1.0 + 3.0 * u);
            float imageEdge = plateEdge * (1.0 - clamp(reduction * mix(0.84, 0.77, dark), 0.0, 0.45) * strength * join);
            float v = abs(q.y);
            float innerStart = max(0.0, imageEdge - band);
            // On a pale backdrop the gap is visible as a shallow neutral ramp.
            // It ends at the refracted image of the lower plate, even on white.
            float inGap = smoothstep(imageEdge - 0.42 * density, imageEdge + 0.55 * density, v);
            interfaceDepth = inGap * smoothstep(0.0, max(lensEdge - imageEdge, density), lensEdge - v) * join;
            float interior = v + (plateEdge - imageEdge) * smoothstep(innerStart, max(innerStart + 0.001, imageEdge), v);
            float outside = plateEdge + (v-imageEdge) * clearance / max(lensEdge-imageEdge, 0.001);
            sampleAt.y = rect.y + sign(q.y) * (v <= imageEdge ? interior : outside);
        } else {
            sampleAt.y = p.y;
        }
    }
    // A sharp refracted image carries the lower plate's rim. The broad diffuse
    // lobe describes the artwork behind it and tapers off at that physical boundary.
    float2 plateAt = sampleAt - plate.xy;
    float rimDistance = abs(sdGlassCap(plateAt, plate.zw)) / density;
    float clearOfRim = smoothstep(0.4, 6.0, rimDistance);
    float innerRim = 1.0 - smoothstep(0.0, 3.2, rimDistance);
    float incidence = mix(0.82, 1.0, smoothstep(0.22, 0.90, abs(q.x) / max(rect.z, 1.0)));
    float2 split = normal * dispersion * strength * incidence * max(shoulder, 0.85 * innerRim);
    float footprint = diffusion * strength * max(shoulder, 0.32 * innerRim);
    float2 plateGradient = float2(sdGlassCap(plateAt+e, plate.zw)-sdGlassCap(plateAt-e, plate.zw),
                                  sdGlassCap(plateAt+e.yx, plate.zw)-sdGlassCap(plateAt-e.yx, plate.zw));
    float2 plateNormal = length(plateGradient) > 0.001 ? normalize(plateGradient) : float2(0.0, 1.0);
    half4 centralRay = content.eval(sampleAt);
    half4 mid = centralRay;
    if (footprint > 0.35) {
        // Keep the color spread parallel to the rim. Only the normal component
        // narrows, so a colored stripe passing through the highlight stays connected.
        float2 along = float2(plateNormal.y, -plateNormal.x) * footprint;
        float2 across = plateNormal * footprint * mix(0.15, 1.0, clearOfRim);
        float2 diag = 0.70 * (along + across);
        float2 otherDiag = 0.70 * (along - across);
        mid = 0.40 * centralRay
            + 0.10 * (content.eval(sampleAt + along) + content.eval(sampleAt - along))
            + 0.10 * (content.eval(sampleAt + across) + content.eval(sampleAt - across))
            + 0.05 * (content.eval(sampleAt + diag) + content.eval(sampleAt - diag)
                    + content.eval(sampleAt + otherDiag) + content.eval(sampleAt - otherDiag));
    }
    half3 color = mid.rgb;
    if (dot(split, split) > 0.04) {
        float2 s1 = split * 1.35;
        float2 s0 = split * 0.65;
        half4 pR = content.eval(sampleAt + s1);
        half4 pO = content.eval(sampleAt + s0);
        half4 pC = content.eval(sampleAt - s0);
        half4 pB = content.eval(sampleAt - s1);
        half weight = min(min(mid.a, centralRay.a), min(min(pR.a, pB.a), min(pO.a, pC.a)));
        half3 spec = half3(
            0.42 * pR.r / max(pR.a, 0.001) + 0.36 * pO.r / max(pO.a, 0.001) + 0.22 * centralRay.r / max(centralRay.a, 0.001),
            0.20 * pO.g / max(pO.a, 0.001) + 0.60 * centralRay.g / max(centralRay.a, 0.001) + 0.20 * pC.g / max(pC.a, 0.001),
            0.22 * centralRay.b / max(centralRay.a, 0.001) + 0.36 * pC.b / max(pC.a, 0.001) + 0.42 * pB.b / max(pB.a, 0.001)
        );
        half3 baseRgb = centralRay.rgb / max(centralRay.a, 0.001);
        color = clamp(mid.rgb + mid.a * weight * 1.15 * (spec - baseRgb), half3(0.0), half3(mid.a));
    }
    float opticalDepth = interfaceShade * interfaceDepth * strength * (1.0 - dark);
    half level = dot(color, half3(0.2126,0.7152,0.0722)) / max(mid.a, 0.001);
    // Where the source already supplies a hairline, retain the stronger shade.
    color *= min(1.0 - 0.28 * opticalDepth, (1.0 - opticalDepth) / max(level, 0.001));
    // A neutral dark plate sinks towards black; colored artwork keeps its hue.
    half3 selected = max(color * 1.08 - half3(.096) * mid.a, half3(0.0));
    return half4(mix(color, selected, dark * (1.0-strength)), mid.a);
}
"""

internal const val TabBevelShader = """
uniform float2 halfSize, contact;
uniform float density, dark, amount, bevelWidth;
$GlassCapsuleSDF
half4 main(float2 p) {
    float2 q = p - halfSize;
    float d = -sdGlassCap(q, halfSize);
    float2 e = float2(0.7, 0.0);
    float2 grad = float2(sdGlassCap(q+e, halfSize)-sdGlassCap(q-e, halfSize),
                         sdGlassCap(q+e.yx, halfSize)-sdGlassCap(q-e.yx, halfSize));
    float2 n = grad / max(length(grad), 0.001);
    float rim = 1.0 - smoothstep(0.0, bevelWidth, d);
    float facing = 0.5 + 0.5 * dot(n, normalize(float2(-0.5,-1.0) + 0.48 * contact));
    float pressure = 1.0 - smoothstep(0.0, 1.7, length(q / halfSize - contact));
    float crest = (d / density - 1.05) / 0.90;
    float glint = exp(-crest * crest);
    float shine = clamp(amount * (glint * (0.25 + 0.68 * facing) + 0.18 * rim * pressure), 0.0, 1.0);
    float film = clamp(amount * (0.04 * pressure + 0.04 * rim) * mix(1.0, 0.16, dark), 0.0, 1.0);
    half3 reflected = mix(half3(1.0,0.88,0.72), half3(0.76,0.91,1.0), 0.5 + 0.5 * n.x);
    half3 body = mix(half3(0.97), half3(0.16,0.18,0.20), dark);
    return half4(reflected * shine + body * film * (1.0-shine), shine + film * (1.0-shine));
}
"""

private class PlateGeometryHolder {
    var dx = 0
    var dy = 0
    var width = 0
    var height = 0
    var blurWidth = 1
    var blurHeight = 1
}
