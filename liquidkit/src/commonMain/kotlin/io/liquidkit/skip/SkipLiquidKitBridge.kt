package io.liquidkit.skip

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.components.LiquidAnchoredMenuHost
import com.kyant.backdrop.components.LiquidMenuItem
import com.kyant.backdrop.components.LiquidMenuPresentation
import com.kyant.backdrop.components.LiquidSymbol
import com.kyant.backdrop.components.liquidBorderlessMenuTrigger
import com.kyant.backdrop.components.liquidMenuTrigger
import com.kyant.backdrop.components.rememberLiquidMenuState
import io.liquidkit.*

/**
 * Skip / SkipUI bridge entry points (`io.liquidkit.skip`).
 *
 * Every composable here accepts primitive/String/standard Kotlin types so SwiftUI wrappers
 * transpiled by Skip (`#if SKIP` -> `ComposeView { context in ... }`) map cleanly to LiquidKit
 * without manually threading `Backdrop` or Compose-specific value types.
 */

fun skipLiquidKitColor(red: Double, green: Double, blue: Double, alpha: Double = 1.0): Color =
    Color(
        red = red.toFloat().coerceIn(0f, 1f),
        green = green.toFloat().coerceIn(0f, 1f),
        blue = blue.toFloat().coerceIn(0f, 1f),
        alpha = alpha.toFloat().coerceIn(0f, 1f),
    )

fun skipLiquidKitSymbol(systemName: String?): LiquidKitSymbol? =
    systemName?.takeIf { it.isNotBlank() }?.let { LiquidSymbol.fromSystemName(it) }

fun skipLiquidKitActionStyle(raw: String): LiquidKitActionStyle = when (raw.lowercase()) {
    "plain" -> LiquidKitActionStyle.Plain
    "borderless" -> LiquidKitActionStyle.Borderless
    "bordered", "tinted" -> LiquidKitActionStyle.Tinted
    "borderedprominent", "filled" -> LiquidKitActionStyle.Filled
    "glass" -> LiquidKitActionStyle.Glass
    "glassprominent" -> LiquidKitActionStyle.GlassProminent
    else -> LiquidKitActionStyle.Tinted
}

fun skipLiquidKitVariant(raw: String): LiquidKitVariant = when (raw.lowercase()) {
    "clear" -> LiquidKitVariant.Clear
    "identity" -> LiquidKitVariant.Identity
    else -> LiquidKitVariant.Regular
}

fun skipLiquidKitShape(cornerRadius: Double?): Shape = when {
    cornerRadius == null || cornerRadius < 0.0 -> LiquidKitCapsule
    else -> RoundedCornerShape(cornerRadius.dp)
}

fun skipLiquidKitAlertRole(raw: String): LiquidKitAlertRole = when (raw.lowercase()) {
    "primary" -> LiquidKitAlertRole.Primary
    "cancel" -> LiquidKitAlertRole.Cancel
    "destructive" -> LiquidKitAlertRole.Destructive
    else -> LiquidKitAlertRole.Default
}

fun skipLiquidKitTitleMode(raw: String): LiquidKitNavigationTitleMode = when (raw.lowercase()) {
    "large" -> LiquidKitNavigationTitleMode.Large
    "inline" -> LiquidKitNavigationTitleMode.Inline
    else -> LiquidKitNavigationTitleMode.Automatic
}

fun skipLiquidKitTransitionStyle(raw: String): LiquidKitViewTransitionStyle = when (raw.lowercase()) {
    "zoom" -> LiquidKitViewTransitionStyle.Zoom
    "slide" -> LiquidKitViewTransitionStyle.Slide
    else -> LiquidKitViewTransitionStyle.CrossFade
}

fun skipLiquidKitControlSize(raw: String): LiquidKitControlSize = when (raw.lowercase()) {
    "mini" -> LiquidKitControlSize.Mini
    "small" -> LiquidKitControlSize.Small
    "large" -> LiquidKitControlSize.Large
    "extralarge", "xl" -> LiquidKitControlSize.ExtraLarge
    else -> LiquidKitControlSize.Regular
}

fun skipLiquidKitButtonShape(raw: String): LiquidKitButtonShape = when (raw.lowercase()) {
    "circle" -> LiquidKitButtonShape.Circle
    "roundedrectangle", "rounded" -> LiquidKitButtonShape.RoundedRectangle
    else -> LiquidKitButtonShape.Capsule
}

@Composable
fun SkipLiquidKitRoot(
    modifier: Modifier = Modifier,
    opacity: Double = 0.5,
    darkOverride: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    LiquidKitRoot(
        modifier = modifier,
        opacity = opacity.toFloat().coerceIn(0f, 1f),
        dark = dark,
        content = content,
    )
}

@Composable
fun SkipLiquidKitGlassSurface(
    modifier: Modifier = Modifier,
    variant: String = "regular",
    cornerRadius: Double? = null,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidKitGlassSurface(
        modifier = modifier,
        shape = skipLiquidKitShape(cornerRadius),
        variant = skipLiquidKitVariant(variant),
        tint = tint,
        interactive = interactive,
        onClick = onClick,
        content = content,
    )
}

@Composable
fun SkipLiquidKitGlassContainer(
    modifier: Modifier = Modifier,
    spacing: Double = 8.0,
    content: @Composable () -> Unit,
) {
    LiquidKitGlassEffectContainer(
        modifier = modifier,
        spacing = spacing.dp,
        content = content,
    )
}

@Composable
fun SkipLiquidKitButton(
    label: String,
    style: String = "glass",
    systemImage: String? = null,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    controlSize: String = "regular",
    shape: String = "capsule",
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    LiquidKitActionButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        tint = tint,
        style = skipLiquidKitActionStyle(style),
        enabled = enabled,
        leadingSymbol = skipLiquidKitSymbol(systemImage),
        controlSize = skipLiquidKitControlSize(controlSize),
        shape = skipLiquidKitButtonShape(shape),
        destructive = destructive,
    )
}

@Composable
fun SkipLiquidKitToggleButton(
    label: String,
    checked: Boolean,
    systemImage: String? = null,
    tint: Color = Color(0xff007aff),
    enabled: Boolean = true,
    controlSize: String = "regular",
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    LiquidKitToggleButton(
        label = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        tint = tint,
        leadingSymbol = skipLiquidKitSymbol(systemImage),
        enabled = enabled,
        controlSize = skipLiquidKitControlSize(controlSize),
    )
}

@Composable
fun SkipLiquidKitToggle(
    title: String?,
    isOn: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    val colors = liquidKitColors()
    if (title.isNullOrEmpty()) {
        LiquidKitToggle(selected = { isOn }, onSelect = onToggle, modifier = modifier)
    } else {
        Row(
            modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(title, Modifier.weight(1f), style = TextStyle(colors.ink, 17.sp))
            LiquidKitToggle(selected = { isOn }, onSelect = onToggle)
        }
    }
}

@Composable
fun SkipLiquidKitSlider(
    value: Double,
    minValue: Double = 0.0,
    maxValue: Double = 1.0,
    tapToSeek: Boolean = true,
    ticks: List<Double> = emptyList(),
    neutralValue: Double? = null,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit,
) {
    val start = minValue.toFloat()
    val end = maxOf(maxValue.toFloat(), start + 1e-4f)
    val mappedTicks = ticks.map { it.toFloat() }
    LiquidKitSlider(
        value = { value.toFloat().coerceIn(start, end) },
        onValueChange = { onValueChange(it.toDouble()) },
        valueRange = start..end,
        modifier = modifier,
        tapToSeek = tapToSeek,
        ticks = mappedTicks,
        snapToTicks = mappedTicks.isNotEmpty(),
        neutralValue = neutralValue?.toFloat(),
    )
}

@Composable
fun SkipLiquidKitOpacitySlider(
    value: Double,
    label: String = "玻璃不透明度",
    showIcons: Boolean = true,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit,
    onFinished: () -> Unit = {},
) {
    LiquidKitOpacitySlider(
        value = value.toFloat().coerceIn(0f, 1f),
        onValueChange = { onValueChange(it.toDouble()) },
        label = label,
        modifier = modifier,
        onValueChangeFinished = onFinished,
        showIcons = showIcons,
    )
}

@Composable
fun SkipLiquidKitStepper(
    label: String,
    value: Int,
    minValue: Int = 0,
    maxValue: Int = 100,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    LiquidKitStepper(
        label = label,
        value = value,
        onChange = onChange,
        range = minValue..maxOf(maxValue, minValue),
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun SkipLiquidKitSegmentedPicker(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LiquidKitSegmentedPicker(options = options, selected = selected, select = onSelect, modifier = modifier)
}

@Composable
fun SkipLiquidKitWheelPicker(
    options: List<String>,
    selected: String,
    showSelectionBar: Boolean = true,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LiquidKitWheelPicker(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        showSelectionBar = showSelectionBar,
    )
}

@Composable
fun SkipLiquidKitMenuPicker(
    label: String,
    options: List<String>,
    selected: String,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LiquidKitMenuPicker(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun SkipLiquidKitInlinePicker(
    options: List<String>,
    selected: String,
    title: String? = null,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LiquidKitInlinePicker(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        title = title,
        tint = tint,
    )
}

data class SkipLiquidKitPaletteOptionSpec(
    val id: String,
    val label: String,
    val color: Color = Color.Unspecified,
    val systemImage: String? = null,
)

@Composable
fun SkipLiquidKitPalettePicker(
    options: List<SkipLiquidKitPaletteOptionSpec>,
    selectedId: String,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LiquidKitPalettePicker(
        options = options.map { LiquidKitPaletteOption(it.id, it.label, it.color, skipLiquidKitSymbol(it.systemImage)) },
        selectedId = selectedId,
        onSelect = onSelect,
        modifier = modifier,
        tint = tint,
    )
}

data class SkipLiquidKitTabOptionSpec(
    val id: String,
    val label: String,
    val systemImage: String? = null,
)

@Composable
fun SkipLiquidKitTabsPicker(
    options: List<SkipLiquidKitTabOptionSpec>,
    selectedId: String,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LiquidKitTabsPicker(
        options = options.map { LiquidKitTabOption(it.id, it.label, skipLiquidKitSymbol(it.systemImage)) },
        selectedId = selectedId,
        onSelect = onSelect,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun SkipLiquidKitSafeAreaBar(
    edge: String = "bottom",
    blurRadius: Double = 16.0,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedEdge = if (edge.equals("top", ignoreCase = true)) {
        LiquidKitSafeAreaEdge.Top
    } else {
        LiquidKitSafeAreaEdge.Bottom
    }
    LiquidKitSafeAreaBar(
        modifier = modifier,
        edge = resolvedEdge,
        blurRadius = blurRadius.dp,
        content = content,
    )
}

@Composable
fun SkipLiquidKitDatePicker(
    label: String,
    year: Int,
    month: Int,
    day: Int,
    style: String = "compact",
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onDateChange: (Int, Int, Int) -> Unit,
) {
    val resolvedStyle = when (style.lowercase()) {
        "graphical" -> LiquidKitDatePickerStyle.Graphical
        "wheel" -> LiquidKitDatePickerStyle.Wheel
        else -> LiquidKitDatePickerStyle.Compact
    }
    LiquidKitDatePicker(
        label = label,
        date = LiquidKitCalendarDate(year, month, day),
        onDateChange = { d -> onDateChange(d.year, d.month, d.day) },
        modifier = modifier,
        tint = tint,
        style = resolvedStyle,
    )
}

@Composable
fun SkipLiquidKitColorPicker(
    label: String,
    color: Color,
    supportsOpacity: Boolean = true,
    modifier: Modifier = Modifier,
    onColorChange: (Color) -> Unit,
) {
    LiquidKitColorPicker(
        label = label,
        color = color,
        onColorChange = onColorChange,
        modifier = modifier,
        supportsOpacity = supportsOpacity,
    )
}

@Composable
fun SkipLiquidKitProgressView(
    value: Double?,
    label: String? = null,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
) {
    if (value == null) {
        LiquidKitActivityIndicator(modifier = modifier, label = label)
    } else {
        LiquidKitProgressIndicator(value = value.toFloat().coerceIn(0f, 1f), tint = tint, modifier = modifier)
    }
}

@Composable
fun SkipLiquidKitGauge(
    value: Double,
    label: String? = null,
    style: String = "circular",
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
) {
    val clamped = value.toFloat().coerceIn(0f, 1f)
    when (style.lowercase()) {
        "linear" -> LiquidKitLinearGauge(value = clamped, tint = tint, modifier = modifier, label = label)
        "open", "circularopen" -> LiquidKitGauge(value = clamped, tint = tint, modifier = modifier, style = LiquidKitGaugeStyle.CircularOpen)
        else -> LiquidKitGauge(value = clamped, tint = tint, modifier = modifier, style = LiquidKitGaugeStyle.CircularCapacity)
    }
}

@Composable
fun SkipLiquidKitTextField(
    value: String,
    placeholder: String,
    secret: Boolean = false,
    multiline: Boolean = false,
    roundedBorder: Boolean = false,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onDone: (() -> Unit)? = null,
) {
    LiquidKitTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        secret = secret,
        multiline = multiline,
        tint = tint,
        onDone = onDone,
        style = if (roundedBorder) LiquidKitTextFieldStyle.RoundedBorder else LiquidKitTextFieldStyle.Plain,
    )
}

@Composable
fun SkipLiquidKitSearchField(
    query: String,
    placeholder: String = "搜索",
    cancelLabel: String? = "取消",
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
) {
    LiquidKitSearchField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = placeholder,
        modifier = modifier,
        cursorColor = tint,
        cancelLabel = cancelLabel,
    )
}

@Composable
fun SkipLiquidKitLabeledContent(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    LiquidKitLabeledContent(label = label, value = value, modifier = modifier)
}

@Composable
fun SkipLiquidKitGroupBox(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidKitGroupBox(title = title, modifier = modifier, content = content)
}

@Composable
fun SkipLiquidKitControlGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidKitControlGroup(modifier = modifier, content = content)
}

@Composable
fun SkipLiquidKitDisclosureGroup(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidKitDisclosureGroup(
        title = title,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun SkipLiquidKitUnavailableView(
    title: String,
    systemImage: String = "folder",
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    LiquidKitUnavailableView(
        title = title,
        symbol = skipLiquidKitSymbol(systemImage) ?: LiquidKitSymbol.Folder,
        modifier = modifier,
        description = description,
    )
}

data class SkipLiquidKitTabSpec(
    val id: String,
    val title: String,
    val systemImage: String,
)

@Composable
fun SkipLiquidKitTabBar(
    tabs: List<SkipLiquidKitTabSpec>,
    selectedId: String,
    tint: Color = Color(0xff007aff),
    minimized: Boolean = false,
    onExpandFromMinimized: (() -> Unit)? = null,
    searchAction: (() -> Unit)? = null,
    searchLabel: String = "搜索",
    searchSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val mapped = tabs.map { spec ->
        LiquidKitTab(spec.id, spec.title, skipLiquidKitSymbol(spec.systemImage) ?: LiquidKitSymbol.Folder)
    }
    LiquidKitTabBar(
        tabs = mapped,
        selected = selectedId,
        onSelect = onSelect,
        modifier = modifier,
        tint = tint,
        minimized = minimized,
        onExpandFromMinimized = onExpandFromMinimized,
        searchAction = searchAction,
        searchLabel = searchLabel,
        searchSelected = searchSelected,
    )
}

data class SkipLiquidKitMenuItemSpec(
    val id: String,
    val title: String,
    val systemImage: String? = null,
    val checked: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun SkipLiquidKitMenu(
    title: String,
    items: List<SkipLiquidKitMenuItemSpec>,
    borderless: Boolean = false,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val backdrop = LocalLiquidKitBackdrop.current
    val state = rememberLiquidMenuState { onSelect(it.key) }
    val mapped = items.map { spec ->
        LiquidMenuItem(
            key = spec.id,
            title = spec.title,
            symbol = skipLiquidKitSymbol(spec.systemImage),
            checked = spec.checked,
            enabled = spec.enabled,
            destructive = spec.destructive,
        )
    }
    LiquidAnchoredMenuHost(
        state = state,
        backdrop = backdrop,
        items = mapped,
        modifier = modifier,
        presentation = if (borderless) LiquidMenuPresentation.Popup else LiquidMenuPresentation.ButtonTransform,
    ) {
        if (borderless) {
            Box(Modifier.liquidBorderlessMenuTrigger(state).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(title, style = TextStyle(tint, 17.sp))
            }
        } else {
            LiquidKitToolbarGroup(Modifier.heightIn(min = 44.dp)) {
                LiquidKitToolbarButton(title, state::show, Modifier.liquidMenuTrigger(state).padding(horizontal = 16.dp)) {
                    BasicText(title, style = TextStyle(tint, 16.sp))
                }
            }
        }
    }
}

data class SkipLiquidKitAlertActionSpec(
    val label: String,
    val role: String = "default",
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun SkipLiquidKitAlert(
    title: String,
    message: String? = null,
    actions: List<SkipLiquidKitAlertActionSpec>,
    tint: Color = Color(0xff007aff),
    onDismiss: () -> Unit,
) {
    LiquidKitAlert(
        title = title,
        actions = actions.map { LiquidKitAlertAction(it.label, skipLiquidKitAlertRole(it.role), it.enabled, it.onClick) },
        onDismissRequest = onDismiss,
        tint = tint,
        message = message,
    )
}

@Composable
fun SkipLiquidKitActionSheet(
    title: String? = null,
    message: String? = null,
    cancelLabel: String = "取消",
    actions: List<SkipLiquidKitAlertActionSpec>,
    tint: Color = Color(0xff007aff),
    onDismiss: () -> Unit,
) {
    LiquidKitActionSheet(
        actions = actions.map { LiquidKitAlertAction(it.label, skipLiquidKitAlertRole(it.role), it.enabled, it.onClick) },
        onDismissRequest = onDismiss,
        tint = tint,
        title = title,
        message = message,
        cancelLabel = cancelLabel,
    )
}

@Composable
fun SkipLiquidKitSheet(
    title: String? = null,
    doneLabel: String? = "完成",
    mediumDetent: Boolean = true,
    largeDetent: Boolean = true,
    tint: Color = Color(0xff007aff),
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val detents = buildList {
        if (mediumDetent) add(LiquidKitSheetDetent.Medium)
        if (largeDetent || isEmpty()) add(LiquidKitSheetDetent.Large)
    }
    LiquidKitSheet(
        onDismissRequest = onDismiss,
        title = title,
        doneLabel = doneLabel,
        tint = tint,
        detents = detents,
        content = content,
    )
}

@Composable
fun SkipLiquidKitNavigationScaffold(
    title: String,
    titleMode: String = "automatic",
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    LiquidKitNavigationScaffold(
        title = title,
        modifier = modifier.fillMaxSize(),
        titleMode = skipLiquidKitTitleMode(titleMode),
        leading = { leading() },
        trailing = { trailing() },
        contentWindowInsets = LiquidKitSafeAreaDefaults.windowInsets,
        content = content,
    )
}

@Composable
fun SkipLiquidKitViewTransition(
    key: String,
    style: String = "crossFade",
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    LiquidKitViewTransition(
        targetState = key,
        modifier = modifier,
        style = skipLiquidKitTransitionStyle(style),
        content = content,
    )
}

@Composable
fun SkipLiquidKitBadge(
    text: String,
    tint: Color = Color(0xffff3b30),
    modifier: Modifier = Modifier,
) = LiquidKitBadge(text = text, modifier = modifier, tint = tint)

@Composable
fun SkipLiquidKitTipCard(
    title: String,
    message: String,
    systemImage: String = "sparkles",
    tint: Color = Color(0xff007aff),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) = LiquidKitTipCard(
    title = title,
    message = message,
    modifier = modifier,
    symbol = skipLiquidKitSymbol(systemImage) ?: LiquidKitSymbol.Sparkles,
    tint = tint,
    onDismiss = onDismiss,
    actionLabel = actionLabel,
    onAction = onAction,
)

@Composable
fun SkipLiquidKitFlowLayout(
    horizontalSpacing: Double = 8.0,
    verticalSpacing: Double = 8.0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = LiquidKitFlowLayout(modifier, horizontalSpacing.dp, verticalSpacing.dp, content)

@Composable
fun SkipLiquidKitTabViewBottomAccessory(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) = LiquidKitTabViewBottomAccessory(modifier = modifier, onClick = onClick, content = content)

@Composable
fun SkipLiquidKitInspector(
    isPresented: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    inspectorContent: @Composable ColumnScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = LiquidKitInspector(
    isPresented = isPresented,
    onDismiss = onDismiss,
    title = title,
    modifier = modifier,
    inspectorContent = inspectorContent,
    content = content,
)

internal fun skipParseCalendarDate(iso: String): LiquidKitCalendarDate? {
    val parts = iso.trim().split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull()?.coerceIn(1, 12) ?: return null
    val d = parts[2].toIntOrNull()?.coerceIn(1, 31) ?: return null
    return LiquidKitCalendarDate(y, m, d)
}

internal fun skipFormatCalendarDate(date: LiquidKitCalendarDate): String =
    "${date.year.toString().padStart(4, '0')}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"

@Composable
fun SkipLiquidKitMultiDatePicker(
    selectedIsoDates: List<String>,
    onSelectedIsoDatesChange: (List<String>) -> Unit,
    tint: Color = Color(0xff007aff),
    initialYear: Int = 2026,
    initialMonth: Int = 11,
    modifier: Modifier = Modifier,
) {
    val parsed = selectedIsoDates.mapNotNull(::skipParseCalendarDate).toSet()
    LiquidKitMultiDatePicker(
        dates = parsed,
        onDatesChange = { next ->
            onSelectedIsoDatesChange(
                next.sortedWith(compareBy<LiquidKitCalendarDate> { it.year }.thenBy { it.month }.thenBy { it.day })
                    .map(::skipFormatCalendarDate),
            )
        },
        modifier = modifier,
        tint = tint,
        initialYear = initialYear,
        initialMonth = initialMonth,
    )
}

@Composable
fun SkipLiquidKitPopover(
    onDismiss: () -> Unit,
    maxWidth: Double = 360.0,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = LiquidKitPopover(
    onDismissRequest = onDismiss,
    modifier = modifier,
    maxWidth = maxWidth.dp,
    content = content,
)

@Composable
fun SkipLiquidKitMeshGradient(
    width: Int,
    height: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    subdivisions: Int = 36,
) = LiquidKitMeshGradient(
    width = width,
    height = height,
    colors = colors,
    modifier = modifier,
    subdivisions = subdivisions,
)

@Composable
fun SkipLiquidKitRedacted(
    active: Boolean,
    cornerRadius: Double = 8.0,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liquidKitRedacted(active = active, cornerRadius = cornerRadius.dp), content = content)
}

@Composable
fun SkipLiquidKitReorderableGrid(
    ids: List<String>,
    onReorder: (List<String>) -> Unit,
    columns: Int = 3,
    tileHeight: Double = 74.0,
    spacing: Double = 10.0,
    modifier: Modifier = Modifier,
    tileContent: @Composable BoxScope.(String, Boolean) -> Unit,
) = LiquidKitReorderableGrid(
    items = ids,
    key = { it },
    onReorder = onReorder,
    modifier = modifier,
    columns = columns,
    tileHeight = tileHeight.dp,
    spacing = spacing.dp,
    tileContent = tileContent,
)

@Composable
fun SkipLiquidKitTable(
    headers: List<String>,
    rows: List<List<String>>,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
) {
    val columns = headers.mapIndexed { idx, title ->
        LiquidKitTableColumn<List<String>>(
            id = "col_$idx",
            title = title,
            weight = 1f,
            comparator = compareBy { it.getOrElse(idx) { "" } },
            valueText = { it.getOrElse(idx) { "" } },
        )
    }
    LiquidKitTable(rows = rows, columns = columns, modifier = modifier, tint = tint)
}

@Composable
fun SkipLiquidKitOutlineGroup(
    ids: List<String>,
    titles: List<String>,
    parentIds: List<String?>,
    systemImages: List<String?> = emptyList(),
    tint: Color = Color(0xff007aff),
    onSelectLeafId: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val count = minOf(ids.size, titles.size, parentIds.size)
    val childrenByParent = HashMap<String?, MutableList<Int>>()
    for (i in 0 until count) {
        childrenByParent.getOrPut(parentIds[i]) { mutableListOf() }.add(i)
    }
    fun build(parent: String?): List<LiquidKitOutlineNode<String>> =
        childrenByParent[parent].orEmpty().map { idx ->
            val id = ids[idx]
            LiquidKitOutlineNode(
                id = id,
                title = titles[idx],
                symbol = skipLiquidKitSymbol(systemImages.getOrNull(idx)),
                payload = id,
                children = build(id),
            )
        }
    LiquidKitOutlineGroup(
        nodes = build(null),
        modifier = modifier,
        tint = tint,
        onSelectLeaf = onSelectLeafId?.let { cb -> { node -> cb(node.id) } },
    )
}

@Composable
fun SkipLiquidKitBarChart(
    labels: List<String>,
    values: List<Double>,
    colors: List<Color>,
    series: List<String> = emptyList(),
    horizontal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val count = minOf(labels.size, values.size)
    val points = List(count) { i ->
        LiquidKitChartSeriesPoint(
            label = labels[i],
            series = series.getOrElse(i) { "default" },
            value = values[i].toFloat(),
            color = colors.getOrElse(i) { Color(0xff007aff) },
        )
    }
    LiquidKitBarChart(points = points, modifier = modifier, horizontal = horizontal)
}

@Composable
fun SkipLiquidKitLineAreaChart(
    labels: List<String>,
    values: List<Double>,
    lineColor: Color = Color(0xff00c7be),
    ruleValue: Double? = null,
    ruleColor: Color = Color(0xffff9500),
    selectedIndex: Int? = null,
    onSelectIndex: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) = LiquidKitLineAreaChart(
    labels = labels,
    values = values.map { it.toFloat() },
    modifier = modifier,
    lineColor = lineColor,
    ruleValue = ruleValue?.toFloat(),
    ruleColor = ruleColor,
    selectedIndex = selectedIndex,
    onSelectIndex = onSelectIndex,
)

@Composable
fun SkipLiquidKitPieDonutChart(
    labels: List<String>,
    values: List<Double>,
    colors: List<Color>,
    innerRadiusRatio: Double = 0.58,
    angularInsetDegrees: Double = 2.0,
    modifier: Modifier = Modifier,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val count = minOf(labels.size, values.size)
    val slices = List(count) { i ->
        LiquidKitPieSlice(
            label = labels[i],
            value = values[i].toFloat(),
            color = colors.getOrElse(i) { Color(0xff007aff) },
        )
    }
    LiquidKitPieDonutChart(
        slices = slices,
        modifier = modifier,
        innerRadiusRatio = innerRadiusRatio.toFloat(),
        angularInsetDegrees = angularInsetDegrees.toFloat(),
        centerContent = centerContent,
    )
}

@Composable
fun SkipLiquidKitMathPlotChart(
    frequency: Double = 1.0,
    modifier: Modifier = Modifier,
) = LiquidKitMathPlotChart(modifier = modifier, frequency = frequency.toFloat())
