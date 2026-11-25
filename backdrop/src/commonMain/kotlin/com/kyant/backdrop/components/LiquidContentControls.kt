package com.kyant.backdrop.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.interaction.InteractiveHighlight
import com.kyant.backdrop.interaction.LiquidPressMotion

enum class LiquidActionStyle { Plain, Tinted, Filled, Borderless, Glass, GlassProminent }

enum class LiquidControlSize { Mini, Small, Regular, Large, ExtraLarge }

enum class LiquidButtonShape { Capsule, Circle, RoundedRectangle }

internal val LocalActionGroupMotion = staticCompositionLocalOf<LiquidPressMotion?> { null }

/** An ink-only action uses the same contact response for text and symbol labels. */
@Composable
fun LiquidBorderlessButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit) {
    val motion = rememberControlPress()
    val menu = LocalLiquidGlassTokens.current.menu
    val inForm = LocalInLiquidForm.current
    Box(modifier.observeControlPress(motion, enabled)
        .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .graphicsLayer {
            scaleX = 1f + menu.borderlessExpansion * motion.progress
            scaleY = scaleX
            alpha = if (enabled) 1f - (1f - menu.borderlessPressedAlpha) * motion.highlightProgress else .45f
        }.defaultMinSize(minWidth = if (inForm) 0.dp else 44.dp, minHeight = if (inForm) 28.dp else 38.dp),
        contentAlignment = if (inForm) Alignment.CenterStart else Alignment.Center, content = content)
}

@Composable
fun LiquidActionButton(
    label: String, onClick: () -> Unit, tint: Color, modifier: Modifier = Modifier,
    style: LiquidActionStyle = LiquidActionStyle.Tinted, enabled: Boolean = true,
    backdrop: Backdrop? = null, leadingSymbol: LiquidSymbol? = null,
    controlSize: LiquidControlSize = LiquidControlSize.Regular,
    shape: LiquidButtonShape = LiquidButtonShape.Capsule,
    cornerRadius: Dp = 12.dp,
    destructive: Boolean = false,
) {
    val inForm = LocalInLiquidForm.current
    val group = LocalActionGroupMotion.current
    val dark = liquidGlassAppearance().dark
    val resolvedTint = when {
        destructive -> if (dark) Color(0xffff453a) else Color(0xffff3b30)
        tint.isSpecified -> tint
        else -> if (dark) Color(0xff0a84ff) else Color(0xff007aff)
    }
    val resolvedShape = when (shape) {
        LiquidButtonShape.Circle -> CircleShape
        LiquidButtonShape.RoundedRectangle -> RoundedCornerShape(cornerRadius)
        LiquidButtonShape.Capsule -> LiquidCapsule
    }
    val sizeMinH = when (controlSize) {
        LiquidControlSize.Mini -> 26.dp
        LiquidControlSize.Small -> 30.dp
        LiquidControlSize.Regular -> 36.dp
        LiquidControlSize.Large -> 44.dp
        LiquidControlSize.ExtraLarge -> 52.dp
    }
    val sizeHPad = when (controlSize) {
        LiquidControlSize.Mini -> 10.dp
        LiquidControlSize.Small -> 12.dp
        LiquidControlSize.Regular -> 16.dp
        LiquidControlSize.Large -> 20.dp
        LiquidControlSize.ExtraLarge -> 24.dp
    }
    val sizeVPad = when (controlSize) {
        LiquidControlSize.Mini -> 4.dp
        LiquidControlSize.Small -> 5.dp
        LiquidControlSize.Regular -> 7.dp
        LiquidControlSize.Large -> 10.dp
        LiquidControlSize.ExtraLarge -> 12.dp
    }
    val sizeFont = when (controlSize) {
        LiquidControlSize.Mini -> 12.sp
        LiquidControlSize.Small -> 14.sp
        LiquidControlSize.Regular -> 16.sp
        LiquidControlSize.Large -> 17.sp
        LiquidControlSize.ExtraLarge -> 19.sp
    }
    val sizeIcon = when (controlSize) {
        LiquidControlSize.Mini -> 13.dp
        LiquidControlSize.Small -> 15.dp
        LiquidControlSize.Regular -> 18.dp
        LiquidControlSize.Large -> 20.dp
        LiquidControlSize.ExtraLarge -> 22.dp
    }
    val isCircleIconOnly = shape == LiquidButtonShape.Circle && label.isEmpty()
    if (style == LiquidActionStyle.Borderless) {
        LiquidBorderlessButton(onClick, modifier, enabled) {
            Row(
                Modifier.padding(horizontal = if (inForm) 0.dp else sizeHPad * .75f, vertical = if (inForm) 2.dp else sizeVPad),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingSymbol != null) LiquidIcon(leadingSymbol, resolvedTint.copy(alpha = if (enabled) 1f else .45f), Modifier.size(sizeIcon))
                if (label.isNotEmpty()) {
                    BasicText(label, style = TextStyle(resolvedTint.copy(alpha = if (enabled) 1f else .45f), if (inForm) 17.sp else sizeFont, FontWeight.Normal, textAlign = if (inForm) TextAlign.Start else TextAlign.Center))
                }
            }
        }
        return
    }
    val appearance = liquidGlassAppearance()
    val glassStyle = style == LiquidActionStyle.Glass || style == LiquidActionStyle.GlassProminent
    val resolvedBackdrop = LocalLiquidSurfaceBackdrop.current ?: backdrop
    if (glassStyle && resolvedBackdrop != null) {
        val scope = rememberCoroutineScope()
        val tokens = LocalLiquidGlassTokens.current
        val prominent = style == LiquidActionStyle.GlassProminent
        val interactive = remember(scope, tokens, prominent) {
            InteractiveHighlight(
                scope,
                tokens.press,
                intensity = tokens.highlightIntensity * (if (prominent) 1.85f else 1.25f),
            )
        }
        val motion = group ?: interactive.motion
        val rim = rememberLiquidRimHighlight(dark)
        val accentTint = resolvedTint
        val chromaticTint = (maxOf(accentTint.red, accentTint.green, accentTint.blue) - minOf(accentTint.red, accentTint.green, accentTint.blue)) > 0.05f
        val translucentProminent = prominent && accentTint.alpha < 0.85f
        val whiteLabeledGlass = !prominent && tint == Color.White
        val surfaceFill = when {
            translucentProminent -> accentTint
            prominent -> appearance.overlay(accentTint.copy(alpha = if (dark) .66f else .76f))
            whiteLabeledGlass -> Color.White.copy(alpha = if (dark) .14f else .22f)
            else -> appearance.overlay(LiquidButtonDefaults.SurfaceColor)
        }
        val ink = when {
            translucentProminent -> if (dark || accentTint.alpha < 0.55f) Color.White else liquidContentColorFor(accentTint)
            prominent -> liquidContentColorFor(accentTint)
            destructive -> resolvedTint
            tint.isSpecified -> tint
            else -> appearance.contentColor
        }
        val contentAlpha = if (enabled) 1f else .45f
        val scale = if (group == null && enabled) Modifier.graphicsLayer { motion.apply(this) } else Modifier
        val sizeMod = if (isCircleIconOnly) Modifier.size(sizeMinH)
        else Modifier.defaultMinSize(minWidth = 44.dp, minHeight = sizeMinH).padding(horizontal = sizeHPad, vertical = sizeVPad)
        val hasShader = com.kyant.backdrop.isRuntimeShaderSupported()
        val pressMod = when {
            !enabled -> Modifier
            group == null -> interactive.gestureModifier
            else -> Modifier.observeControlPress(motion, enabled = true)
        }
        Box(modifier.then(pressMod)
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .then(scale)
            .drawBackdrop(
                backdrop = resolvedBackdrop,
                shape = { resolvedShape },
                effects = { liquidSurfaceLens(appearance.controlBlurRadius, appearance, LiquidGlassRole.Control) },
                highlight = { rim },
                shadow = { LiquidSurfaceShadow },
                onDrawSurface = {
                    if (prominent && !translucentProminent) {
                        if (chromaticTint) drawRect(accentTint, blendMode = BlendMode.Hue)
                        drawRect(accentTint.copy(alpha = (if (dark) .56f else .64f) * if (enabled) 1f else .52f))
                    } else if (!hasShader || translucentProminent || whiteLabeledGlass) {
                        drawRect(surfaceFill.copy(alpha = surfaceFill.alpha * if (enabled) 1f else .52f))
                    }
                    if (enabled && group == null) interactive.drawLight(this, brightenBackdrop = !dark && !prominent)
                },
            )
            .then(sizeMod),
            contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leadingSymbol != null) LiquidIcon(leadingSymbol, ink.copy(alpha = contentAlpha), Modifier.size(sizeIcon))
                if (label.isNotEmpty()) {
                    BasicText(label, style = TextStyle(ink.copy(alpha = contentAlpha), sizeFont, FontWeight.Medium, textAlign = TextAlign.Center))
                }
            }
        }
        return
    }
    val motion = group ?: rememberControlPress()
    val localHighlightMotion = if (group != null) rememberControlPress() else motion
    val highlight = LocalLiquidGlassTokens.current.press.actionHighlightAlpha
    val accentTint = resolvedTint
    val ink = when (style) {
        LiquidActionStyle.Filled, LiquidActionStyle.GlassProminent -> liquidContentColorFor(accentTint)
        LiquidActionStyle.Glass -> if (destructive) resolvedTint else if (tint.isSpecified) tint else appearance.contentColor
        else -> accentTint
    }
    val pressTint = if (style == LiquidActionStyle.Filled || style == LiquidActionStyle.GlassProminent || (dark && style == LiquidActionStyle.Tinted)) Color.White else ink
    val base = when (style) {
        LiquidActionStyle.Plain, LiquidActionStyle.Borderless -> Color.Transparent
        LiquidActionStyle.Tinted -> accentTint.copy(alpha = .05f).compositeOver(if (dark) Color(0xff36363a) else Color(0xffebecef))
        LiquidActionStyle.Filled -> accentTint
        LiquidActionStyle.Glass -> appearance.surface
        LiquidActionStyle.GlassProminent -> accentTint.copy(alpha = if (dark) .74f else .84f)
    }
    val glassBorder = if (glassStyle) Modifier.border(.6.dp, Color.White.copy(alpha = if (dark) .22f else .48f), resolvedShape) else Modifier
    val scale = if (group == null && enabled) Modifier.graphicsLayer { motion.apply(this) } else Modifier
    val contentAlpha = if (enabled) 1f else .45f
    val flushPlain = inForm && style == LiquidActionStyle.Plain && group == null
    val hPad = when {
        isCircleIconOnly -> 0.dp
        flushPlain -> 0.dp
        style == LiquidActionStyle.Plain -> if (group != null) 14.dp else 10.dp
        else -> sizeHPad
    }
    val vPad = when {
        isCircleIconOnly -> 0.dp
        flushPlain -> 2.dp
        else -> sizeVPad
    }
    val minH = when {
        flushPlain -> 28.dp
        group != null -> 32.dp
        else -> sizeMinH
    }
    var drawGroupDivider by remember { mutableStateOf(false) }
    val groupSeparator = (LocalListColors.current ?: liquidListColors()).separator.copy(alpha = .68f)
    val groupDividerModifier = if (group != null) {
        Modifier.onGloballyPositioned { drawGroupDivider = it.positionInParent().x > 4f }
            .drawBehind {
                if (drawGroupDivider) {
                    val insetY = size.height * 0.22f
                    drawLine(groupSeparator, Offset(0f, insetY), Offset(0f, size.height - insetY), strokeWidth = .75.dp.toPx())
                }
            }
    } else Modifier
    val innerClipShape = if (group != null) RectangleShape else resolvedShape
    val sizeMod = if (isCircleIconOnly) Modifier.size(minH)
    else Modifier.defaultMinSize(minWidth = if (flushPlain) 0.dp else 44.dp, minHeight = minH).padding(horizontal = hPad, vertical = vPad)
    val observeLocalPress = if (group != null) Modifier.observeControlPress(localHighlightMotion, enabled) else Modifier
    Box(modifier.then(groupDividerModifier).observeControlPress(motion, enabled).then(observeLocalPress)
        .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .then(scale).clip(innerClipShape).background(base.copy(alpha = base.alpha * if (enabled) 1f else .42f))
        .then(glassBorder)
        .drawBehind { if (enabled && localHighlightMotion.progress > 0f) drawRect(pressTint.copy(alpha = highlight * localHighlightMotion.highlightProgress)) }
        .then(sizeMod),
        contentAlignment = if (flushPlain) Alignment.CenterStart else Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingSymbol != null) LiquidIcon(leadingSymbol, ink.copy(alpha = contentAlpha), Modifier.size(sizeIcon))
            if (label.isNotEmpty()) {
                BasicText(
                    label,
                    style = TextStyle(
                        color = ink.copy(alpha = contentAlpha),
                        fontSize = if (flushPlain) 17.sp else sizeFont,
                        fontWeight = if (flushPlain) FontWeight.Normal else FontWeight.Medium,
                        textAlign = if (flushPlain) TextAlign.Start else TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/** SwiftUI `Toggle(..., isOn:).toggleStyle(.button)` pill toggle button. */
@Composable
fun LiquidToggleButton(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    leadingSymbol: LiquidSymbol? = null,
    enabled: Boolean = true,
    controlSize: LiquidControlSize = LiquidControlSize.Regular,
) {
    LiquidActionButton(
        label = label,
        onClick = { onCheckedChange(!checked) },
        tint = tint,
        modifier = modifier.semantics { this.selected = checked },
        style = if (checked) LiquidActionStyle.Filled else LiquidActionStyle.Tinted,
        enabled = enabled,
        leadingSymbol = leadingSymbol,
        controlSize = controlSize,
    )
}

@Composable
fun LiquidUnavailableView(title: String, symbol: LiquidSymbol, modifier: Modifier = Modifier,
    description: String? = null, colors: LiquidListColors = liquidListColors(), action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 380.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LiquidIcon(symbol, colors.secondary, Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            BasicText(title, Modifier.semantics { heading() }, style = TextStyle(colors.ink, 21.sp, FontWeight.SemiBold, textAlign = TextAlign.Center))
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                BasicText(description, style = TextStyle(colors.secondary, 15.sp, textAlign = TextAlign.Center))
            }
            if (action != null) { Spacer(Modifier.height(16.dp)); action() }
        }
    }
}

@Composable
fun LiquidSidebarRow(title: String, symbol: LiquidSymbol, tint: Color, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, colors: LiquidListColors = liquidListColors()) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val controls = LocalLiquidGlassTokens.current.controls
    val fill by animateColorAsState(if (selected || pressed) colors.selected else Color.Transparent, tween(controls.listSelectMillis), label = "Sidebar selection")
    Row(modifier.fillMaxWidth().clip(LiquidCapsule).background(fill)
        .selectable(selected, interactionSource = interaction, indication = null, onClick = onClick)
        .heightIn(min = 44.dp).padding(horizontal = 15.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        LiquidIcon(symbol, tint, Modifier.size(23.dp))
        BasicText(title, style = TextStyle(colors.ink, 16.sp, if (selected) FontWeight.Medium else FontWeight.Normal),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LiquidDisclosureIcon(expanded: Boolean, color: Color, modifier: Modifier = Modifier) {
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val navigation = LocalLiquidGlassTokens.current.navigation
    val angle by animateFloatAsState(if (expanded) 90f * sign else 0f, tween(navigation.disclosureRotateMillis), label = "Disclosure angle")
    LiquidIcon(LiquidSymbol.Forward, color, modifier.rotate(angle))
}

@Composable
fun LiquidSidebarSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit, toggleLabel: String,
    modifier: Modifier = Modifier, colors: LiquidListColors = liquidListColors()) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(modifier.fillMaxWidth().clip(LiquidCapsule)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = toggleLabel, onClick = onToggle)
        .graphicsLayer { alpha = if (pressed) .55f else 1f }
        .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically) {
        BasicText(title, Modifier.weight(1f), style = TextStyle(colors.secondary, 14.sp, FontWeight.SemiBold))
        LiquidDisclosureIcon(expanded, colors.secondary, Modifier.size(16.dp))
    }
}
