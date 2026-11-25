package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberSolidColorBackdrop
import kotlinx.coroutines.delay

internal val LocalLiquidSurfaceBackdrop = compositionLocalOf<Backdrop?> { null }
internal val LocalInLiquidForm = staticCompositionLocalOf { false }
internal val LocalFormRowHighlight = compositionLocalOf<((Boolean) -> Unit)?> { null }

/** Scopes child glass surfaces inside a container or modal so controls refract this surface instead of underlying pages. */
@Composable
fun ProvideLiquidSurfaceBackdrop(backdrop: Backdrop, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLiquidSurfaceBackdrop provides backdrop, content = content)
}

enum class LiquidTextFieldStyle { Plain, RoundedBorder }

/** Key-value row mirroring SwiftUI `LabeledContent`. */
@Composable
fun LiquidLabeledContent(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
) {
    LiquidLabeledContent(label, modifier, colors) {
        BasicText(value, style = TextStyle(colors.secondary, 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LiquidLabeledContent(
    label: String,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    value: @Composable () -> Unit,
) {
    val minRowHeight = if (LocalInLiquidForm.current) 28.dp else 44.dp
    Row(
        modifier.fillMaxWidth().heightIn(min = minRowHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        value()
    }
}

/** Tinted inset container matching SwiftUI `GroupBox`. */
@Composable
fun LiquidGroupBox(
    title: String? = null,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = liquidGlassAppearance().dark
    val boxFill = if (dark) Color(0xff2c2c2e) else Color(0xfff2f2f7)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(boxFill).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!title.isNullOrEmpty()) {
            BasicText(title, Modifier.semantics { heading() }, style = TextStyle(colors.ink, 16.sp, FontWeight.SemiBold))
        }
        CompositionLocalProvider(LocalInLiquidForm provides true) {
            content()
        }
    }
}

/** Coordinates grouped actions inside one shared pill and shared press motion (`ControlGroup`). */
@Composable
fun LiquidControlGroup(
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val motion = rememberControlPress()
    val dark = liquidGlassAppearance().dark
    val groupFill = if (dark) Color(0xff2c2c30) else Color(0xffececf0)
    CompositionLocalProvider(
        LocalActionGroupMotion provides motion,
        LocalListColors provides colors,
        LocalInLiquidForm provides false,
    ) {
        Row(
            modifier.graphicsLayer { motion.apply(this) }.clip(LiquidCapsule)
                .background(groupFill).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Expandable disclosure section with animated chevron mirroring SwiftUI `DisclosureGroup`. */
@Composable
fun LiquidDisclosureGroup(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val rowMinHeight = if (LocalInLiquidForm.current) 30.dp else 44.dp
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var tapFlash by remember { mutableStateOf(false) }
    LaunchedEffect(tapFlash) { if (tapFlash) { delay(110); tapFlash = false } }
    val highlighted = pressed || tapFlash
    val notifyFormRow = LocalFormRowHighlight.current
    SideEffect { notifyFormRow?.invoke(highlighted) }
    val controls = LocalLiquidGlassTokens.current.controls
    val standaloneFill by animateColorAsState(
        if (notifyFormRow == null && highlighted) colors.selected else Color.Transparent,
        tween(if (highlighted) controls.listPressMillis else controls.listReleaseMillis),
        label = "Disclosure row highlight",
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(standaloneFill)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    if (!pressed) tapFlash = true
                    onExpandedChange(!expanded)
                }
                .heightIn(min = rowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(title, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
            LiquidDisclosureIcon(expanded, colors.ink, Modifier.size(15.dp))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.88f, stiffness = 460f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.92f, stiffness = 520f)) + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 18.dp, top = 6.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
        }
    }
}

interface LiquidFormScope {
    fun row(content: @Composable ColumnScope.() -> Unit) =
        row(inset = true, separatorStart = 16.dp, onClick = null, enabled = true, selected = false, content = content)

    fun row(
        inset: Boolean = true,
        separatorStart: Dp = 16.dp,
        onClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        selected: Boolean = false,
        content: @Composable ColumnScope.() -> Unit,
    )

    /** SwiftUI-style inset-grouped navigation/action row with full-bleed iOS press highlight and aligned separator. */
    fun navigationRow(
        title: String,
        onClick: () -> Unit,
        leading: LiquidSymbol? = null,
        tint: Color = Color.Unspecified,
        subtitle: String? = null,
        trailingText: String? = null,
        enabled: Boolean = true,
        selected: Boolean = false,
    ) {
        val dividerStart = if (leading != null) 49.dp else 16.dp
        row(inset = true, separatorStart = dividerStart, onClick = onClick, enabled = enabled, selected = selected) {
            val colors = LocalListColors.current ?: liquidListColors()
            val iconColor = if (tint.isSpecified) tint else colors.ink
            val ink = if (enabled) colors.ink else colors.secondary.copy(alpha = .55f)
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (subtitle != null) 42.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (leading != null) {
                    LiquidIcon(leading, iconColor.copy(alpha = if (enabled) 1f else .45f), Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(title, style = TextStyle(ink, 17.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrEmpty()) {
                        BasicText(subtitle, style = TextStyle(colors.secondary, 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!trailingText.isNullOrEmpty()) {
                    BasicText(trailingText, style = TextStyle(colors.secondary, 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                LiquidIcon(LiquidSymbol.Forward, colors.secondary.copy(alpha = if (enabled) .85f else .4f), Modifier.size(14.dp))
            }
        }
    }
}

private class LiquidFormRowSpec(
    val inset: Boolean,
    val separatorStart: Dp,
    val onClick: (() -> Unit)?,
    val enabled: Boolean,
    val selected: Boolean,
    val content: @Composable ColumnScope.() -> Unit,
)

private class LiquidFormCollector : LiquidFormScope {
    val rows = mutableListOf<LiquidFormRowSpec>()
    override fun row(
        inset: Boolean,
        separatorStart: Dp,
        onClick: (() -> Unit)?,
        enabled: Boolean,
        selected: Boolean,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        rows += LiquidFormRowSpec(inset, separatorStart, onClick, enabled, selected, content)
    }
}

/** SwiftUI-style InsetGrouped Form card with native full-bleed cell highlights and auto-hiding hairline dividers. */
@Composable
fun LiquidFormSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    colors: LiquidListColors = liquidListColors(),
    content: LiquidFormScope.() -> Unit,
) {
    val collector = LiquidFormCollector().apply(content)
    val controls = LocalLiquidGlassTokens.current.controls
    val highlightedRows = remember { mutableStateMapOf<Int, Boolean>() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrEmpty()) {
            BasicText(
                title,
                Modifier.padding(horizontal = 16.dp).semantics { heading() },
                style = TextStyle(colors.secondary, 14.sp, FontWeight.Medium),
            )
        }
        val cardBackdrop = rememberSolidColorBackdrop(colors.surface)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.surface)) {
            CompositionLocalProvider(
                LocalLiquidSurfaceBackdrop provides cardBackdrop,
                LocalInLiquidForm provides true,
                LocalListColors provides colors,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    collector.rows.forEachIndexed { index, spec ->
                        val interaction = remember { MutableInteractionSource() }
                        val pressHighlighted by rememberSinglePressHighlight(interaction)
                        var childHighlighted by remember { mutableStateOf(false) }
                        val highlighted = spec.selected || pressHighlighted || childHighlighted
                        SideEffect { highlightedRows[index] = highlighted }
                        DisposableEffect(index) { onDispose { highlightedRows.remove(index) } }
                        val rowFill by animateColorAsState(
                            if (highlighted) colors.selected else Color.Transparent,
                            tween(if (highlighted) controls.listPressMillis else controls.listReleaseMillis),
                            label = "Form row highlight",
                        )
                        val clickModifier = if (spec.onClick != null) {
                            Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                                enabled = spec.enabled,
                                role = Role.Button,
                            ) {
                                spec.onClick.invoke()
                            }
                        } else {
                            Modifier
                        }
                        val innerPadding = if (spec.inset) Modifier.padding(horizontal = 16.dp, vertical = 10.dp) else Modifier
                        CompositionLocalProvider(
                            LocalFormRowHighlight provides { active -> childHighlighted = active },
                        ) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(rowFill)
                                    .then(clickModifier)
                                    .then(innerPadding),
                                content = spec.content,
                            )
                        }
                        if (index < collector.rows.lastIndex) {
                            val hideDivider = highlightedRows[index] == true || highlightedRows[index + 1] == true
                            val dividerAlpha by animateFloatAsState(
                                if (hideDivider) 0f else .55f,
                                tween(if (hideDivider) 35 else 160),
                                label = "Form divider alpha",
                            )
                            Spacer(
                                Modifier.fillMaxWidth()
                                    .padding(start = spec.separatorStart)
                                    .height(.55.dp)
                                    .background(colors.separator.copy(alpha = dividerAlpha)),
                            )
                        }
                    }
                }
            }
        }
        if (!footer.isNullOrEmpty()) {
            BasicText(footer, Modifier.padding(horizontal = 16.dp), style = TextStyle(colors.secondary, 13.sp))
        }
    }
}

/** Form text input field supporting single-line, secure password, clear action, and multi-line editing. */
@Composable
fun LiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    multiline: Boolean = false,
    tint: Color = Color(0xff007aff),
    colors: LiquidListColors = liquidListColors(),
    onDone: (() -> Unit)? = null,
    style: LiquidTextFieldStyle = LiquidTextFieldStyle.Plain,
) {
    val dark = liquidGlassAppearance().dark
    val inForm = LocalInLiquidForm.current
    var focused by remember { mutableStateOf(false) }
    val rounded = style == LiquidTextFieldStyle.RoundedBorder
    val shape = RoundedCornerShape(10.dp)
    val boxModifier = when {
        rounded -> {
            val fill = if (dark) colors.selected.copy(alpha = .40f) else colors.surface
            val stroke = if (focused) 1.5.dp else .75.dp
            val strokeColor = if (focused) tint else colors.separator.copy(alpha = .75f)
            modifier.fillMaxWidth()
                .heightIn(min = if (multiline) 100.dp else 40.dp)
                .clip(shape)
                .background(fill)
                .border(stroke, strokeColor, shape)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        }
        inForm -> modifier.fillMaxWidth()
            .heightIn(min = if (multiline) 100.dp else 26.dp)
            .padding(vertical = if (multiline) 2.dp else 0.dp)
        else -> modifier.fillMaxWidth()
            .heightIn(min = if (multiline) 100.dp else 38.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    }
    Box(
        boxModifier,
        contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = placeholder },
            singleLine = !multiline,
            textStyle = TextStyle(color = colors.ink, fontSize = 17.sp),
            cursorBrush = SolidColor(tint),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            decorationBox = { editor ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            BasicText(
                                placeholder,
                                style = TextStyle(colors.secondary.copy(alpha = .58f), 17.sp),
                                maxLines = if (multiline) 3 else 1,
                            )
                        }
                        editor()
                    }
                    if (!multiline && value.isNotEmpty()) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(colors.secondary.copy(alpha = .55f))
                                .clickable(interactionSource = null, indication = null, role = Role.Button) { onValueChange("") }
                                .semantics { contentDescription = "清除 $placeholder" },
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidIcon(LiquidSymbol.Close, colors.surface, Modifier.size(11.dp))
                        }
                    }
                }
            },
        )
    }
}
