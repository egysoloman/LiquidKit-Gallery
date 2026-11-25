package io.liquidkit

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.components.*

typealias LiquidKitActionStyle = LiquidActionStyle
typealias LiquidKitSymbol = LiquidSymbol
typealias LiquidKitGaugeStyle = LiquidGaugeStyle
typealias LiquidKitDatePickerStyle = LiquidDatePickerStyle
typealias LiquidKitCalendarDate = LiquidCalendarDate
typealias LiquidKitTextFieldStyle = LiquidTextFieldStyle
typealias LiquidKitListStyle = LiquidListStyle
typealias LiquidKitSwipeAction = LiquidSwipeAction
typealias LiquidKitFormScope = LiquidFormScope
typealias LiquidKitNavigationTitleMode = LiquidNavigationTitleMode
typealias LiquidKitSplitColumn = LiquidSplitColumn
typealias LiquidKitSplitVisibility = LiquidSplitVisibility
typealias LiquidKitNavigationSplitState = LiquidNavigationSplitState
typealias LiquidKitViewTransitionStyle = LiquidViewTransitionStyle
typealias LiquidKitTab<Key> = LiquidTab<Key>
typealias LiquidKitMenuItem = LiquidMenuItem
typealias LiquidKitMenuState = LiquidMenuState
typealias LiquidKitMenuPresentation = LiquidMenuPresentation
typealias LiquidKitAlertRole = LiquidAlertRole
typealias LiquidKitAlertAction = LiquidAlertAction
typealias LiquidKitSheetDetent = LiquidSheetDetent
typealias LiquidKitBackEvent = LiquidBackEvent
typealias LiquidKitToolbarItem<Icon> = LiquidToolbarItem<Icon>
typealias LiquidKitToolbarSpacing = LiquidToolbarSpacing
typealias LiquidKitNavBarAction = LiquidNavBarAction
typealias LiquidKitNavBarGroup = LiquidNavBarGroup
typealias LiquidKitPinnedNavChrome = LiquidPinnedNavChrome
typealias LiquidKitPaletteOption = LiquidPaletteOption
typealias LiquidKitTabOption = LiquidTabOption
typealias LiquidKitSafeAreaEdge = LiquidSafeAreaEdge
typealias LiquidKitControlSize = LiquidControlSize
typealias LiquidKitButtonShape = LiquidButtonShape
typealias LiquidKitOutlineNode<T> = LiquidOutlineNode<T>
typealias LiquidKitTableColumn<T> = LiquidTableColumn<T>
typealias LiquidKitChartSeriesPoint = LiquidChartSeriesPoint
typealias LiquidKitPieSlice = LiquidPieSlice

val LiquidKitCapsule: Shape = LiquidCapsule

@Composable
fun LiquidKitIcon(symbol: LiquidKitSymbol, tint: Color = LocalLiquidContentColor.current, modifier: Modifier = Modifier) =
    LiquidIcon(symbol, tint, modifier)

@Composable
fun LiquidKitGlassSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    shape: Shape = LiquidKitCapsule,
    variant: LiquidKitVariant = LiquidKitVariant.Regular,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = LiquidGlassSurface(backdrop, modifier, shape, variant, tint, interactive, onClick, content)

@Composable
fun LiquidKitGlassEffectContainer(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    spacing: Dp = LocalLiquidKitTokens.current.fusionDistance,
    surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    content: @Composable () -> Unit,
) = LiquidGlassEffectContainer(backdrop, modifier, spacing, surfaceColor, content)

@Composable
fun LiquidKitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) = LiquidButton(onClick, backdrop, modifier, isInteractive, tint, surfaceColor, content)

@Composable
fun LiquidKitActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    style: LiquidKitActionStyle = LiquidKitActionStyle.Tinted,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    leadingSymbol: LiquidKitSymbol? = null,
    controlSize: LiquidKitControlSize = LiquidKitControlSize.Regular,
    shape: LiquidKitButtonShape = LiquidKitButtonShape.Capsule,
    cornerRadius: Dp = 12.dp,
    destructive: Boolean = false,
) = LiquidActionButton(
    label = label,
    onClick = onClick,
    tint = tint,
    modifier = modifier,
    style = style,
    enabled = enabled,
    backdrop = backdrop,
    leadingSymbol = leadingSymbol,
    controlSize = controlSize,
    shape = shape,
    cornerRadius = cornerRadius,
    destructive = destructive,
)

@Composable
fun LiquidKitToggleButton(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    leadingSymbol: LiquidKitSymbol? = null,
    enabled: Boolean = true,
    controlSize: LiquidKitControlSize = LiquidKitControlSize.Regular,
) = LiquidToggleButton(label, checked, onCheckedChange, modifier, tint, leadingSymbol, enabled, controlSize)

@Composable
fun LiquidKitBorderlessButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) = LiquidBorderlessButton(onClick, modifier, enabled, content)

@Composable
fun LiquidKitToolbarGroup(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidKitBackdrop.current,
    surfaceColor: Color = LiquidButtonDefaults.SurfaceColor,
    content: @Composable BoxScope.() -> Unit,
) = LiquidToolbarGroup(modifier, backdrop, surfaceColor, content)

@Composable
fun LiquidKitToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop? = LocalLiquidKitBackdrop.current,
    content: @Composable BoxScope.() -> Unit,
) = LiquidToolbarButton(label, onClick, modifier, enabled, backdrop, content)

@Composable
fun LiquidKitToolbarPair(
    spacing: Dp,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    firstVisible: Boolean = true,
    fusionDistance: Dp = LocalLiquidKitTokens.current.fusionDistance,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) = LiquidToolbarPair(backdrop, spacing, firstVisible, modifier, fusionDistance, first, second)

@Composable
fun LiquidKitToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    tint: Color = Color.Unspecified,
) = LiquidToggle(selected, onSelect, backdrop, modifier, tint)

@Composable
fun LiquidKitSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    visibilityThreshold: Float = .001f,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    tapToSeek: Boolean = true,
    ticks: List<Float> = emptyList(),
    snapToTicks: Boolean = ticks.isNotEmpty(),
    neutralValue: Float? = null,
    tint: Color = Color.Unspecified,
) = LiquidSlider(value, onValueChange, valueRange, visibilityThreshold, backdrop, modifier, tapToSeek, ticks, snapToTicks, neutralValue, tint)

@Composable
fun LiquidKitOpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String = "玻璃不透明度",
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    showIcons: Boolean = true,
    backdrop: Backdrop? = LocalLiquidKitBackdrop.current,
) = LiquidOpacitySlider(value, onValueChange, label, modifier, onValueChangeFinished, showIcons, backdrop)

@Composable
fun LiquidKitStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
    showValue: Boolean = true,
) = LiquidStepper(label, value, onChange, range, tint, modifier, colors, showValue)

@Composable
fun LiquidKitSegmentedPicker(
    options: List<String>,
    selected: String,
    select: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    backdrop: Backdrop? = LocalLiquidKitBackdrop.current,
) = LiquidSegmentedPicker(options, selected, select, modifier, colors, backdrop)

@Composable
fun LiquidKitWheelPicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    showSelectionBar: Boolean = true,
) = LiquidWheelPicker(options, selected, onSelect, modifier, colors, showSelectionBar)

@Composable
fun LiquidKitMenuPicker(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidMenuPicker(label, options, selected, onSelect, tint, modifier, backdrop, colors)

@Composable
fun LiquidKitInlinePicker(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidInlinePicker(options, selected, onSelect, modifier, title, tint, colors)

@Composable
fun LiquidKitPalettePicker(
    options: List<LiquidKitPaletteOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidPalettePicker(options, selectedId, onSelect, modifier, tint, colors)

@Composable
fun LiquidKitTabsPicker(
    options: List<LiquidKitTabOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidKitBackdrop.current,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidTabsPicker(options, selectedId, onSelect, modifier, backdrop, tint, colors)

@Composable
fun LiquidKitSafeAreaBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    edge: LiquidKitSafeAreaEdge = LiquidKitSafeAreaEdge.Bottom,
    colors: LiquidKitColors = liquidKitColors(),
    blurRadius: Dp = 16.dp,
    respectSystemBars: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = LiquidSafeAreaBar(backdrop, modifier, edge, colors, blurRadius, respectSystemBars, content)

@Composable
fun LiquidKitDatePicker(
    label: String,
    date: LiquidKitCalendarDate,
    onDateChange: (LiquidKitCalendarDate) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    style: LiquidKitDatePickerStyle = LiquidKitDatePickerStyle.Compact,
    minDate: LiquidKitCalendarDate? = null,
    maxDate: LiquidKitCalendarDate? = null,
) = LiquidDatePicker(label, date, onDateChange, tint, modifier, backdrop, colors, style, minDate, maxDate)

@Composable
fun LiquidKitMultiDatePicker(
    dates: Set<LiquidKitCalendarDate>,
    onDatesChange: (Set<LiquidKitCalendarDate>) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    initialYear: Int = 2026,
    initialMonth: Int = 11,
    colors: LiquidKitColors = liquidKitColors(),
    minDate: LiquidKitCalendarDate? = null,
    maxDate: LiquidKitCalendarDate? = null,
) = LiquidMultiDatePicker(dates, onDatesChange, tint, modifier, initialYear, initialMonth, colors, minDate, maxDate)

@Composable
fun LiquidKitColorPicker(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = color,
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    supportsOpacity: Boolean = true,
) = LiquidColorPicker(label, color, onColorChange, modifier, tint, backdrop, colors, supportsOpacity)

@Composable
fun LiquidKitProgressIndicator(value: Float, tint: Color, modifier: Modifier = Modifier) =
    LiquidProgressIndicator(value, tint, modifier)

@Composable
fun LiquidKitProgressIndicator(value: Float, brush: Brush, modifier: Modifier = Modifier) =
    LiquidProgressIndicator(value, brush, modifier)

@Composable
fun LiquidKitActivityIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
    colors: LiquidKitColors = liquidKitColors(),
    color: Color = colors.secondary,
    size: Dp = 22.dp,
) = LiquidActivityIndicator(modifier, label, colors, color, size)

@Composable
fun LiquidKitGauge(
    value: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    style: LiquidKitGaugeStyle = LiquidKitGaugeStyle.CircularCapacity,
    brush: Brush? = null,
    trackColor: Color = Color.Unspecified,
) = LiquidGauge(value, tint, modifier, colors, style, brush, trackColor)

@Composable
fun LiquidKitLinearGauge(
    value: Float,
    tint: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueLabel: String? = null,
    colors: LiquidKitColors = liquidKitColors(),
    brush: Brush? = null,
    trackHeight: Dp = 8.dp,
    trackColor: Color = Color.Unspecified,
) = LiquidLinearGauge(value, tint, modifier, label, valueLabel, colors, brush, trackHeight, trackColor)

@Composable
fun LiquidKitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    multiline: Boolean = false,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
    onDone: (() -> Unit)? = null,
    style: LiquidKitTextFieldStyle = LiquidKitTextFieldStyle.Plain,
) = LiquidTextField(value, onValueChange, placeholder, modifier, secret, multiline, tint, colors, onDone, style)

@Composable
fun LiquidKitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String = "清除",
    modifier: Modifier = Modifier,
    label: String = placeholder,
    colors: LiquidKitColors = liquidKitColors(),
    cursorColor: Color = Color(0xff007aff),
    backdrop: Backdrop? = LocalLiquidKitBackdrop.current,
    cancelLabel: String? = "取消",
    onSearch: (String) -> Unit = {},
    trailingAction: (@Composable () -> Unit)? = null,
) = LiquidSearchField(
    value = value,
    onValueChange = onValueChange,
    placeholder = placeholder,
    clearLabel = clearLabel,
    modifier = modifier,
    label = label,
    colors = colors,
    cursorColor = cursorColor,
    backdrop = backdrop,
    onSearch = onSearch,
    cancelLabel = cancelLabel,
    trailingAction = trailingAction,
)

@Composable
fun LiquidKitLabeledContent(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidLabeledContent(label, value, modifier, colors)

@Composable
fun LiquidKitLabeledContent(
    label: String,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    value: @Composable () -> Unit,
) = LiquidLabeledContent(label, modifier, colors, value)

@Composable
fun LiquidKitGroupBox(
    title: String? = null,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    content: @Composable ColumnScope.() -> Unit,
) = LiquidGroupBox(title, modifier, colors, content)

@Composable
fun LiquidKitControlGroup(
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    content: @Composable RowScope.() -> Unit,
) = LiquidControlGroup(modifier, colors, content)

@Composable
fun LiquidKitDisclosureGroup(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    content: @Composable ColumnScope.() -> Unit,
) = LiquidDisclosureGroup(title, expanded, onExpandedChange, modifier, colors, content)

@Composable
fun LiquidKitFormSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    colors: LiquidKitColors = liquidKitColors(),
    content: LiquidKitFormScope.() -> Unit,
) = LiquidFormSection(title, footer, modifier, colors, content)

@Composable
fun LiquidKitList(
    style: LiquidKitListStyle = LiquidKitListStyle.Grouped,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    colors: LiquidKitColors = liquidKitColors(),
    contentPadding: PaddingValues = PaddingValues(
        horizontal = when (style) {
            LiquidKitListStyle.Grouped -> 20.dp
            LiquidKitListStyle.Plain -> 0.dp
            LiquidKitListStyle.Cards -> 16.dp
        },
        vertical = 8.dp,
    ),
    content: LazyListScope.() -> Unit,
) = LiquidList(style, modifier, state, colors, contentPadding, content)

@Composable
fun LiquidKitListCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) = LiquidListCell(title, modifier, subtitle, selected, enabled, onClick, leading, trailing)

@Composable
fun LiquidKitUnavailableView(
    title: String,
    symbol: LiquidKitSymbol,
    modifier: Modifier = Modifier,
    description: String? = null,
    colors: LiquidKitColors = liquidKitColors(),
    action: (@Composable () -> Unit)? = null,
) = LiquidUnavailableView(title, symbol, modifier, description, colors, action)

@Composable
fun <Key> LiquidKitTabBar(
    tabs: List<LiquidKitTab<Key>>,
    selected: Key,
    onSelect: (Key) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    tint: Color = Color(0xff007aff),
    minimized: Boolean = false,
    onExpandFromMinimized: (() -> Unit)? = null,
    searchAction: (() -> Unit)? = null,
    searchLabel: String = "搜索",
    searchSelected: Boolean = false,
) = LiquidTabBar(
    tabs = tabs,
    selected = selected,
    onSelect = onSelect,
    backdrop = backdrop,
    tint = tint,
    modifier = modifier,
    minimized = minimized,
    onExpandFromMinimized = onExpandFromMinimized,
    searchAction = searchAction,
    searchLabel = searchLabel,
    searchSelected = searchSelected,
)

@Composable
fun LiquidKitNavigationScaffold(
    title: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    scrollState: LazyListState? = null,
    titleMode: LiquidKitNavigationTitleMode = LiquidKitNavigationTitleMode.Automatic,
    colors: LiquidKitColors = liquidKitColors(),
    leading: @Composable (Backdrop) -> Unit = {},
    trailing: @Composable (Backdrop) -> Unit = {},
    bottomBar: (@Composable (Backdrop) -> Unit)? = null,
    contentWindowInsets: WindowInsets = LiquidKitSafeAreaDefaults.windowInsets,
    scrollOffsetPx: (() -> Float)? = null,
    pageBackdrop: LiquidKitLayerBackdrop? = null,
    recordPageBackdrop: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) = LiquidNavigationScaffold(
    title = title,
    backdrop = backdrop,
    modifier = modifier,
    scrollState = scrollState,
    titleMode = titleMode,
    colors = colors,
    leading = leading,
    trailing = trailing,
    bottomBar = bottomBar,
    contentWindowInsets = contentWindowInsets,
    scrollOffsetPx = scrollOffsetPx,
    pageBackdrop = pageBackdrop,
    recordPageBackdrop = recordPageBackdrop,
    content = content,
)

@Composable
fun rememberLiquidKitSplitState(initialColumn: LiquidKitSplitColumn = LiquidKitSplitColumn.Sidebar): LiquidKitNavigationSplitState =
    rememberLiquidNavigationSplitState(initialColumn)

@Composable
fun LiquidKitNavigationSplitView(
    state: LiquidKitNavigationSplitState,
    modifier: Modifier = Modifier,
    visibility: LiquidKitSplitVisibility = LiquidKitSplitVisibility.Automatic,
    twoColumnWidth: Dp = 600.dp,
    threeColumnWidth: Dp = 1000.dp,
    dividerColor: Color = Color.Gray.copy(alpha = .24f),
    swipeBackFromBlank: Boolean = true,
    sidebar: @Composable () -> Unit,
    content: (@Composable () -> Unit)? = null,
    detail: @Composable () -> Unit,
) = LiquidNavigationSplitView(state, modifier, visibility, twoColumnWidth, threeColumnWidth, dividerColor, swipeBackFromBlank, sidebar, content, detail)

@Composable
fun <T : Any> LiquidKitSwipeBackNavigation(
    page: T,
    previousPage: T?,
    depth: (T) -> Int,
    contentKey: (T) -> Any = { it },
    onNavigateBack: (T) -> Unit,
    modifier: Modifier = Modifier,
    predictiveBackEnabled: Boolean = true,
    onVisualTargetChange: ((T) -> Unit)? = null,
    content: @Composable (T) -> Unit,
) = LiquidSwipeBackNavigation(page, previousPage, depth, contentKey, onNavigateBack, modifier, predictiveBackEnabled, onVisualTargetChange, content)

@Composable
fun LiquidKitNavBarSlot(
    group: LiquidKitNavBarGroup?,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    modifier: Modifier = Modifier,
) = LiquidNavBarSlot(group, backdrop, modifier)

val LocalLiquidKitPinnedNavChromeReporter = LocalPinnedNavChromeReporter

@Composable
fun LiquidKitProgressiveNavHeader(
    backdrop: Backdrop,
    edgeLayer: LiquidKitLayerBackdrop,
    barHeight: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
) = LiquidProgressiveNavHeader(backdrop, edgeLayer, barHeight, alpha, modifier)

@Composable
fun LiquidKitNavigationBarStyle(content: @Composable () -> Unit) =
    LiquidNavigationBarStyle(content)

@Composable
fun <T> LiquidKitViewTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    style: LiquidKitViewTransitionStyle = LiquidKitViewTransitionStyle.CrossFade,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) = LiquidViewTransition(targetState, modifier, style, contentKey, content)

@Composable
fun LiquidKitAlert(
    title: String,
    actions: List<LiquidKitAlertAction>,
    onDismissRequest: () -> Unit,
    tint: Color = Color(0xff007aff),
    message: String? = null,
    colors: LiquidKitColors = liquidKitColors(),
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    field: (@Composable () -> Unit)? = null,
) = LiquidAlert(title, actions, onDismissRequest, tint, message, colors, backdrop, field)

@Composable
fun LiquidKitActionSheet(
    actions: List<LiquidKitAlertAction>,
    onDismissRequest: () -> Unit,
    tint: Color = Color(0xff007aff),
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    cancelLabel: String = "取消",
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidActionSheet(actions, onDismissRequest, tint, modifier, title, message, cancelLabel, backdrop, colors)

@Composable
fun LiquidKitSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    title: String? = null,
    doneLabel: String? = "完成",
    tint: Color = Color(0xff007aff),
    detents: List<LiquidKitSheetDetent> = listOf(LiquidKitSheetDetent.Medium, LiquidKitSheetDetent.Large),
    initialDetent: LiquidKitSheetDetent = detents.firstOrNull() ?: LiquidKitSheetDetent.Large,
    colors: LiquidKitColors = liquidKitColors(),
    content: @Composable ColumnScope.() -> Unit,
) = LiquidSheet(onDismissRequest, modifier, backdrop, title, doneLabel, tint, detents, initialDetent, colors, content)

@Composable
fun LiquidKitPopover(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorBoundsInRoot: Rect? = null,
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    maxWidth: Dp = 360.dp,
    blurRadius: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) = LiquidPopover(onDismissRequest, modifier, anchorBoundsInRoot, backdrop, colors, maxWidth, blurRadius, content)

@Composable
fun LiquidKitBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xffff3b30),
    contentColor: Color = Color.White,
) = LiquidBadge(text, modifier, tint, contentColor)

fun Modifier.liquidKitRedacted(active: Boolean, cornerRadius: Dp = 8.dp): Modifier =
    liquidRedacted(active, cornerRadius)

@Composable
fun <T> LiquidKitReorderableGrid(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    tileHeight: Dp = 74.dp,
    spacing: Dp = 10.dp,
    tileContent: @Composable BoxScope.(T, Boolean) -> Unit,
) = LiquidReorderableGrid(items, key, onReorder, modifier, columns, tileHeight, spacing, tileContent)

@Composable
fun LiquidKitInspector(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidKitModalBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    inspectorWidth: Dp = 300.dp,
    inspectorContent: @Composable ColumnScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = LiquidInspector(isPresented, onDismiss, title, modifier, backdrop, colors, inspectorWidth, inspectorContent, content)

@Composable
fun LiquidKitTabViewBottomAccessory(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) = LiquidTabViewBottomAccessory(backdrop, modifier, onClick, content)

@Composable
fun LiquidKitMeshGradient(
    width: Int,
    height: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    subdivisions: Int = 18,
) = LiquidMeshGradient(width, height, colors, modifier, subdivisions)

@Composable
fun LiquidKitFlowLayout(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) = LiquidFlowLayout(modifier, horizontalSpacing, verticalSpacing, content)

@Composable
fun <T> LiquidKitOutlineGroup(
    nodes: List<LiquidKitOutlineNode<T>>,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
    onSelectLeaf: ((LiquidKitOutlineNode<T>) -> Unit)? = null,
) = LiquidOutlineGroup(nodes, modifier, tint, colors, onSelectLeaf)

@Composable
fun <T> LiquidKitTable(
    rows: List<T>,
    columns: List<LiquidKitTableColumn<T>>,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidTable(rows, columns, modifier, tint, colors)

@Composable
fun LiquidKitTipCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    symbol: LiquidKitSymbol = LiquidKitSymbol.Sparkles,
    tint: Color = Color(0xff007aff),
    colors: LiquidKitColors = liquidKitColors(),
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = LiquidTipCard(title, message, modifier, symbol, tint, colors, onDismiss, actionLabel, onAction)

@Composable
fun LiquidKitBarChart(
    points: List<LiquidKitChartSeriesPoint>,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidBarChart(points, modifier, horizontal, colors)

@Composable
fun LiquidKitLineAreaChart(
    labels: List<String>,
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xff00c7be),
    ruleValue: Float? = null,
    ruleColor: Color = Color(0xffff9500),
    selectedIndex: Int? = null,
    onSelectIndex: ((Int?) -> Unit)? = null,
    colors: LiquidKitColors = liquidKitColors(),
    chartHeight: Dp = 180.dp,
    showPoints: Boolean = true,
    showGrid: Boolean = true,
    labelColor: Color = colors.secondary,
    yDomain: ClosedFloatingPointRange<Float>? = null,
) = LiquidLineAreaChart(labels, values, modifier, lineColor, ruleValue, ruleColor, selectedIndex, onSelectIndex, colors, chartHeight, showPoints, showGrid, labelColor, yDomain)

@Composable
fun LiquidKitPieDonutChart(
    slices: List<LiquidKitPieSlice>,
    modifier: Modifier = Modifier,
    innerRadiusRatio: Float = 0.58f,
    angularInsetDegrees: Float = 2f,
    centerContent: @Composable BoxScope.() -> Unit = {},
) = LiquidPieDonutChart(slices, modifier, innerRadiusRatio, angularInsetDegrees, centerContent)

@Composable
fun LiquidKitMathPlotChart(
    modifier: Modifier = Modifier,
    frequency: Float = 1f,
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidMathPlotChart(modifier, frequency, colors)

@Composable
fun rememberLiquidKitMenuState(onSelect: (LiquidKitMenuItem) -> Unit): LiquidKitMenuState =
    rememberLiquidMenuState(onSelect)

fun Modifier.liquidKitMenuTrigger(
    state: LiquidKitMenuState,
    activation: LiquidMenuActivation = LiquidMenuActivation.Press,
    beforeOpen: () -> Unit = {},
    onShortTap: () -> Unit = {},
    onHoldChange: (Boolean) -> Unit = {},
): Modifier = liquidMenuTrigger(state, activation, beforeOpen, onShortTap, onHoldChange)

fun Modifier.liquidKitContextMenu(
    state: LiquidKitMenuState,
    enabled: Boolean = true,
    prepare: () -> Unit = {},
): Modifier = liquidListContextMenu(state, enabled, prepare)

@Composable
fun LiquidKitAnchoredMenuHost(
    state: LiquidKitMenuState,
    items: List<LiquidKitMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    presentation: LiquidKitMenuPresentation = LiquidKitMenuPresentation.ButtonTransform,
    actions: List<LiquidKitMenuItem> = emptyList(),
    content: @Composable BoxScope.() -> Unit,
) = LiquidAnchoredMenuHost(state, backdrop, items, modifier, presentation, actions, content = content)

@Composable
fun LiquidKitContextMenuHost(
    state: LiquidKitMenuState,
    items: List<LiquidKitMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    content: @Composable BoxScope.() -> Unit,
) = LiquidListContextMenuHost(state, backdrop, items, modifier, colors, content)

@Composable
fun LiquidKitContextMenuHost(
    state: LiquidKitMenuState,
    items: List<LiquidKitMenuItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalLiquidKitBackdrop.current,
    colors: LiquidKitColors = liquidKitColors(),
    preview: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) = LiquidListContextMenuHost(state, backdrop, items, modifier, colors, preview, content)

@Composable
fun LiquidKitAlertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tint: Color = Color(0xff007aff),
    onConfirm: () -> Unit = {},
    colors: LiquidKitColors = liquidKitColors(),
) = LiquidAlertTextField(value, onValueChange, label, tint, onConfirm, colors)

@Composable
fun <T> LiquidKitContentReplacement(
    value: T,
    modifier: Modifier = Modifier,
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.CenterStart,
    content: @Composable (T) -> Unit,
) = LiquidContentReplacement(value, modifier, alignment, content)

@Composable
fun LiquidKitProvideSurfaceBackdrop(backdrop: Backdrop, content: @Composable () -> Unit) =
    ProvideLiquidSurfaceBackdrop(backdrop, content)
