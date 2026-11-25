package com.kyant.backdrop.components

import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.boundsInRoot
import com.kyant.backdrop.morph.LiquidMorphBody

data class LiquidMenuItem(
    val key: String,
    val title: String,
    val symbol: LiquidSymbol? = null,
    val children: List<LiquidMenuItem> = emptyList(),
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val startsSection: Boolean = false,
    val subtitle: String? = null,
    val destructive: Boolean = false,
) {
    @Deprecated("For precompiled menu clients", level = DeprecationLevel.HIDDEN)
    constructor(key: String, title: String, symbol: LiquidSymbol? = null, children: List<LiquidMenuItem> = emptyList(),
        enabled: Boolean = true, checked: Boolean = false, startsSection: Boolean = false, subtitle: String? = null) :
        this(key, title, symbol, children, enabled, checked, startsSection, subtitle, false)

    @Deprecated("For precompiled menu clients", level = DeprecationLevel.HIDDEN)
    fun copy(key: String = this.key, title: String = this.title, symbol: LiquidSymbol? = this.symbol,
        children: List<LiquidMenuItem> = this.children, enabled: Boolean = this.enabled, checked: Boolean = this.checked,
        startsSection: Boolean = this.startsSection, subtitle: String? = this.subtitle): LiquidMenuItem =
        LiquidMenuItem(key, title, symbol, children, enabled, checked, startsSection, subtitle, destructive)
}

enum class LiquidMenuActivation { Press, LongPress, ListContext }

internal class MenuTrigger(val activation: LiquidMenuActivation, val beforeOpen: () -> Unit, val onTap: () -> Unit, val onHold: (Boolean) -> Unit) {
    var layout: LayoutCoordinates? = null
    var borderlessHoldMillis: Long? = null
    var previewShape by mutableStateOf<RoundedCornerShape?>(null)
    var borderlessReboundScale by mutableFloatStateOf(1f)
    var toolbar: ToolbarTouchGroup? = null
    fun fullBounds(): Rect? = layout?.takeIf { it.isAttached }?.let { Rect(it.positionInRoot(), it.size.toSize()) }
}

internal class MenuRow(val level: List<String>, val header: Boolean, var item: LiquidMenuItem) {
    var ink = Color.Unspecified
    var layout: LayoutCoordinates? = null
    var leadingCheck = false
    val label = movableContentOf<MenuLabelStyle> { MenuRowLabel(it) }
}

internal class MenuDwell(val row: MenuRow, val origin: Offset)

internal class MenuBranch(
    val level: List<String>,
    val item: LiquidMenuItem,
    val anchor: Rect,
    val parentPanel: Rect,
    val sourceInk: Color,
    val source: MenuRow,
) {
    var open by mutableStateOf(true)
    val reveal = Animatable(0f)
}

internal class MenuStackReturn(val initial: Rect, val stack: Rect, val trigger: Rect, val body: LiquidMorphBody?) {
    val fallback = Animatable(1f)

    fun bounds(): Rect? = if (body != null) body.boundsInRoot()
        else androidx.compose.ui.geometry.lerp(trigger, initial, fallback.value)
}

internal fun LayoutCoordinates?.visibleBounds(): Rect? =
    this?.takeIf { it.isAttached }?.boundsInRoot()?.takeUnless { it.isEmpty }

@Stable
class LiquidMenuState internal constructor(private val onSelect: (LiquidMenuItem) -> Unit) {
    var expanded by mutableStateOf(false)
        private set
    internal var stackReturn by mutableStateOf<MenuStackReturn?>(null)
        private set
    internal val dismissing get() = stackReturn != null
    internal var restoringSource by mutableStateOf(false)
    internal var overlayActive by mutableStateOf(false)
    internal var borderlessCover by mutableFloatStateOf(0f)
    internal var rootBody: LiquidMorphBody? = null
    internal val triggers = mutableSetOf<MenuTrigger>()
    internal var hostLayout: LayoutCoordinates? = null
    internal val descendants = mutableSetOf<LiquidMenuState>()
    internal val rows = mutableSetOf<MenuRow>()
    internal val branches = mutableStateListOf<MenuBranch>()
    internal val panels = mutableMapOf<List<String>, LayoutCoordinates>()
    internal val restPanelBounds = mutableMapOf<List<String>, Rect>()
    internal val viewports = mutableStateMapOf<List<String>, MenuViewport>()
    internal val activeLevel get() = branches.lastOrNull { it.open }?.level.orEmpty()
    internal var highlighted by mutableStateOf<MenuRow?>(null)
        private set
    internal var dwell by mutableStateOf<MenuDwell?>(null)
        private set
    internal var pointer by mutableStateOf<Offset?>(null)
        private set
    var openingBounds by mutableStateOf<Rect?>(null)
        private set
    internal var sourceTrigger by mutableStateOf<MenuTrigger?>(null)
        private set
    internal var openingGroupBounds: Rect? = null
    internal var openingPull by mutableStateOf(Offset.Zero)
        private set
    private var holdOrigin: Offset? = null
    internal var heldTrigger by mutableStateOf<MenuTrigger?>(null)
        private set
    internal var previewTrigger by mutableStateOf<MenuTrigger?>(null)
        private set
    internal var openingPoint: Offset? = null
        private set

    internal fun retirePreview(trigger: MenuTrigger) {
        if (previewTrigger === trigger && heldTrigger !== trigger && !expanded) previewTrigger = null
    }

    internal fun removeTrigger(trigger: MenuTrigger) {
        triggers.remove(trigger)
        if (heldTrigger === trigger) finishHold()
        if (previewTrigger === trigger) {
            previewTrigger = null
            if (expanded && sourceTrigger === trigger) dismiss()
        }
    }

    internal fun beginHold(at: Offset, trigger: MenuTrigger? = null) {
        heldTrigger = trigger
        if (trigger?.activation == LiquidMenuActivation.ListContext) previewTrigger = trigger
        holdOrigin = at
        openingPull = Offset.Zero
    }

    internal fun moveHold(at: Offset) {
        holdOrigin?.let { openingPull = at - it }
    }

    internal fun finishHold() {
        heldTrigger = null
        holdOrigin = null
        openingPull = Offset.Zero
    }

    private var enteredAt: Offset? = null
    private var disclosureRow: Rect? = null
    private var touchSlop = 0f
    internal var trackingFromTrigger by mutableStateOf(false)

    internal fun openFrom(trigger: MenuTrigger) {
        openingBounds = trigger.layout.visibleBounds()
        openingPoint = if (heldTrigger === trigger) holdOrigin else null
        if (trigger.activation == LiquidMenuActivation.ListContext) previewTrigger = trigger
        sourceTrigger = trigger
        openingGroupBounds = trigger.toolbar?.layout.visibleBounds()
        trigger.beforeOpen()
        show()
    }

    fun show() {
        if (expanded) return
        if (sourceTrigger?.activation == LiquidMenuActivation.ListContext && previewTrigger == null) {
            previewTrigger = sourceTrigger?.takeIf { it in triggers }
        }
        restoringSource = false
        stackReturn = null
        branches.clear()
        endTracking()
        expanded = true
    }

    fun dismiss() {
        if (!expanded) return
        finishHold()
        endTracking()
        if (branches.isNotEmpty()) {
            val live = rootBody?.boundsInRoot()
            val initial = live ?: branches.first().parentPanel
            val destination = openingBounds ?: Rect(initial.topRight, initial.topRight)
            val stack = branches.fold(initial) { union, branch ->
                val panel = panels[branch.level].visibleBounds() ?: branch.anchor
                Rect(minOf(union.left, panel.left), minOf(union.top, panel.top),
                    maxOf(union.right, panel.right), maxOf(union.bottom, panel.bottom))
            }
            stackReturn = MenuStackReturn(initial, stack, destination, rootBody.takeIf { live != null })
        }
        expanded = false
    }

    internal fun retire(branch: MenuBranch) {
        if (branch.open || dismissing) return
        branches.remove(branch)
    }

    internal fun finishReturn(exit: MenuStackReturn) {
        if (stackReturn !== exit) return
        branches.clear()
        restPanelBounds.clear()
        stackReturn = null
    }

    internal fun detachHost() {
        previewTrigger = null
        borderlessCover = 0f
        openingPoint = null
        restoringSource = false
        overlayActive = false
        sourceTrigger = null
        branches.clear()
        restPanelBounds.clear()
        stackReturn = null
        rootBody = null
        expanded = false
        endTracking()
        finishHold()
    }

    fun back() {
        if (dismissing) return
        val active = branches.lastOrNull { it.open }
        if (active == null) dismiss() else {
            active.open = false
            enteredAt = pointer
            disclosureRow = active.anchor
            highlighted = null
            dwell = null
        }
    }

    internal fun triggerAt(point: Offset) = triggers.firstOrNull { it.layout.visibleBounds()?.contains(point) == true }

    internal fun containsPanel(point: Offset): Boolean =
        panels.any { (level, layout) -> (level.isEmpty() || branches.any { it.open && it.level == level }) &&
            layout.visibleBounds()?.contains(point) == true }

    internal fun point(at: Offset, rearmDistance: Float) {
        if (!expanded || dismissing) return
        pointer = at
        touchSlop = rearmDistance
        val previous = enteredAt
        if (previous != null) {
            if ((previous - at).getDistance() < rearmDistance || disclosureRow?.contains(at) == true) return
            enteredAt = null
            disclosureRow = null
        }
        hitTest(at)
    }

    // Layout may catch up with an early drag. Only a real pointer event may rearm a new level.
    internal fun refreshHit() {
        if (!expanded || enteredAt != null) return
        pointer?.let(::hitTest)
    }

    private fun hitTest(at: Offset) {
        val level = activeLevel
        val row = rows.firstOrNull { row ->
            row.level == level && row.item.enabled && row.layout.visibleBounds()?.contains(at) == true
        }
        highlighted = row
        if (row != null) finishHold()
        val pending = dwell
        if (!trackingFromTrigger || row == null || (!row.header && row.item.children.isEmpty())) {
            dwell = null
        } else if (pending == null || pending.row !== row || (at - pending.origin).getDistance() > touchSlop) {
            dwell = MenuDwell(row, at)
        }
    }

    internal fun finishDwell(pending: MenuDwell) {
        val at = pointer ?: return
        if (dwell === pending && pending.row === highlighted && pending.row in rows &&
            pending.row.layout.visibleBounds()?.contains(at) == true) activate(pending.row)
    }

    internal fun clearHoldHighlight() {
        pointer = null
        highlighted = null
        dwell = null
        enteredAt = null
        disclosureRow = null
    }

    internal fun endTracking() {
        trackingFromTrigger = false
        highlighted = null
        dwell = null
        pointer = null
        enteredAt = null
        disclosureRow = null
    }

    internal fun release(at: Offset, rearmDistance: Float, tappedRow: MenuRow?) {
        point(at, rearmDistance)
        val target = highlighted
        finishHold()
        if (target != null) {
            // A deliberate tap can disclose on release. Sliding across a branch must finish its dwell.
            if (target === tappedRow || (!target.header && target.item.children.isEmpty())) activate(target)
        } else if (!containsPanel(at)) dismiss()
        endTracking()
    }

    internal fun activate(row: MenuRow) {
        if (!expanded || dismissing || row.level != activeLevel || !row.item.enabled) return
        if (row.header) {
            back()
        } else if (row.item.children.isNotEmpty()) {
            val anchor = row.layout.visibleBounds() ?: return
            val panel = panels[row.level].visibleBounds() ?: return
            val level = row.level + row.item.key
            val returning = branches.firstOrNull { it.level == level }
            if (returning == null) branches.add(MenuBranch(level, row.item, anchor, panel, row.ink, row))
            else returning.open = true
            disclosureRow = anchor
            enteredAt = pointer
            highlighted = null
            dwell = null
        } else {
            dismiss()
            onSelect(row.item)
        }
    }
}

@Composable
fun rememberLiquidMenuState(onSelect: (LiquidMenuItem) -> Unit): LiquidMenuState {
    val latest by rememberUpdatedState(onSelect)
    return remember { LiquidMenuState { latest(it) } }
}
