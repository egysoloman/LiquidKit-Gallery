package com.kyant.backdrop.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

class LiquidSwipeAction(
    val label: String,
    val symbol: LiquidSymbol,
    val color: Color,
    val onClick: () -> Unit,
)

@Stable
internal class LiquidSwipeGroup { var active by mutableStateOf<Any?>(null) }

/** The first action sits at the outside edge and runs once on a committed full swipe. */
@Composable
fun LiquidSwipeActions(
    rowKey: Any,
    modifier: Modifier = Modifier,
    leading: List<LiquidSwipeAction> = emptyList(),
    trailing: List<LiquidSwipeAction> = emptyList(),
    allowsFullSwipeLeading: Boolean = true,
    allowsFullSwipeTrailing: Boolean = true,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = key(rowKey) {
    val group = LocalSwipeGroup.current ?: remember { LiquidSwipeGroup() }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val buttonWidth = with(density) { 72.dp.toPx() }
    val minFling = with(density) { 650.dp.toPx() }
    var width by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberLiquidHaptics()
    val settleSpec = LocalLiquidGlassTokens.current.controls.swipeSettle.spec
    var settling by remember { mutableStateOf<Job?>(null) }
    var inFullSwipeZone by remember { mutableStateOf(false) }
    fun settle(to: Float) {
        settling?.cancel()
        settling = scope.launch { animate(offset, to, animationSpec = settleSpec) { value, _ -> offset = value } }
    }
    val leadWidth = (leading.size * buttonWidth).coerceAtMost(width * .8f)
    val trailWidth = (trailing.size * buttonWidth).coerceAtMost(width * .8f)
    val leadLimit = if (leading.isNotEmpty() && allowsFullSwipeLeading) width.toFloat() else leadWidth
    val trailLimit = if (trailing.isNotEmpty() && allowsFullSwipeTrailing) width.toFloat() else trailWidth
    LaunchedEffect(group.active, enabled, leading.size, trailing.size, width) {
        when {
            group.active != rowKey && offset != 0f -> settle(0f)
            !enabled || (offset > 0 && leading.isEmpty()) || (offset < 0 && trailing.isEmpty()) -> {
                if (group.active == rowKey) group.active = null
            }
            offset > leadLimit || offset < -trailLimit -> settle(offset.coerceIn(-trailLimit, leadLimit))
        }
    }
    DisposableEffect(group, rowKey) { onDispose { if (group.active == rowKey) group.active = null } }
    fun perform(action: LiquidSwipeAction) {
        group.active = null
        settle(0f)
        action.onClick()
    }
    val overhang = with(density) { 18.dp.toPx() }
    val drag = rememberDraggableState { delta ->
        val raw = offset + delta
        val nextOffset = when {
            raw > leadLimit -> leadLimit + overhang * tanh((raw - leadLimit) / overhang)
            raw < -trailLimit -> -trailLimit + overhang * tanh((raw + trailLimit) / overhang)
            else -> raw
        }
        offset = nextOffset
        val nextLeading = nextOffset > 0f
        val nextAllowFull = if (nextLeading) allowsFullSwipeLeading else allowsFullSwipeTrailing
        val nextActions = if (nextLeading) leading else trailing
        val nextAnchor = if (nextLeading) leadWidth else trailWidth
        val nextThreshold = maxOf(width * .68f, nextAnchor + buttonWidth * .5f).coerceAtMost(width * .94f)
        val crossed = nextAllowFull && nextActions.isNotEmpty() && abs(nextOffset) >= nextThreshold
        if (crossed != inFullSwipeZone) {
            inFullSwipeZone = crossed
            haptics(LiquidHaptic.ThresholdDetent)
        }
    }
    val isLeading = offset > 0f
    val actions = if (isLeading) leading else trailing
    val revealed = abs(offset)
    val anchor = if (isLeading) leadWidth else trailWidth
    val full = if (isLeading) allowsFullSwipeLeading else allowsFullSwipeTrailing
    val fullDistance = maxOf(width * .68f, anchor + buttonWidth * .5f).coerceAtMost(width * .94f)
    val colors = LocalListColors.current ?: liquidListColors()
    Box(modifier.fillMaxWidth().clipToBounds().onSizeChanged { width = it.width }
        .semantics { if (enabled) customActions = (leading + trailing).map { action ->
            CustomAccessibilityAction(action.label) { perform(action); true }
        } }.draggable(state = drag, orientation = Orientation.Horizontal, reverseDirection = rtl,
            enabled = enabled && (leading.isNotEmpty() || trailing.isNotEmpty()),
            onDragStarted = { settling?.cancel(); inFullSwipeZone = false; group.active = rowKey },
            onDragStopped = { velocity ->
                inFullSwipeZone = false
                val liveLeading = offset > 0f
                val liveActions = if (liveLeading) leading else trailing
                val openWidth = if (liveLeading) leadWidth else trailWidth
                val allowFull = if (liveLeading) allowsFullSwipeLeading else allowsFullSwipeTrailing
                val threshold = maxOf(width * .68f, openWidth + buttonWidth * .5f).coerceAtMost(width * .94f)
                val speed = velocity * sign(offset)
                when {
                    allowFull && abs(offset) >= threshold && liveActions.isNotEmpty() -> perform(liveActions.first())
                    liveActions.isNotEmpty() && speed > -minFling && (abs(offset) > openWidth * .43f || speed > minFling) -> settle(sign(offset) * openWidth)
                    else -> { group.active = null; settle(0f) }
                }
            })) {
        if (revealed > 1f && actions.isNotEmpty()) {
            val displayed = if (full && revealed >= fullDistance) actions.take(1) else actions
            Row(Modifier.matchParentSize(), horizontalArrangement = if (isLeading) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(with(density) { revealed.toDp() }).padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    (if (isLeading) displayed else displayed.asReversed()).forEach { action ->
                        Column(
                            Modifier.weight(1f).clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = action.label) { perform(action) }
                                .padding(horizontal = 3.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                    .background(action.color, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    LiquidIcon(action.symbol, Color.White, Modifier.size(18.dp))
                                    BasicText(action.label, style = TextStyle(Color.White, 11.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().offset { IntOffset(offset.roundToInt(), 0) }) {
            content()
            if (revealed > 1f) Spacer(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { group.active = null; settle(0f) })
        }
    }
}
