package com.kyant.backdrop.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.interaction.InteractiveHighlight
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil
import kotlin.math.roundToInt

data class LiquidTab<Key>(val key: Key, val label: String, val symbol: LiquidSymbol)

/**
 * A floating Liquid Glass tab bar mirroring iOS 26/27 `TabView`, supporting:
 * - continuous dragged lens refraction across tabs (`LiquidBottomTabsLayout`)
 * - optional detached circular Liquid Glass search orb (`searchAction`, matching `Tab(role: .search)`)
 * - smooth spring morphing into a compact active orb on scroll (`minimized`, matching `.tabBarMinimizeBehavior(.onScrollDown)`)
 */
@Composable
fun <Key> LiquidTabBar(
    tabs: List<LiquidTab<Key>>,
    selected: Key,
    onSelect: (Key) -> Unit,
    backdrop: Backdrop,
    tint: Color,
    modifier: Modifier = Modifier,
) = LiquidTabBar(
    tabs = tabs,
    selected = selected,
    onSelect = onSelect,
    backdrop = backdrop,
    tint = tint,
    modifier = modifier,
    minimized = false,
    onExpandFromMinimized = null,
    searchAction = null,
)

@Composable
fun <Key> LiquidTabBar(
    tabs: List<LiquidTab<Key>>,
    selected: Key,
    onSelect: (Key) -> Unit,
    backdrop: Backdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    minimized: Boolean = false,
    onExpandFromMinimized: (() -> Unit)? = null,
    searchAction: (() -> Unit)? = null,
    searchLabel: String = "搜索",
    searchSelected: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val appearance = liquidGlassAppearance()
    val haptics = rememberLiquidHaptics()
    val activeTab = tabs.firstOrNull { it.key == selected } ?: tabs.first()
    val hasSearch = searchAction != null
    val minimizeProgress by animateFloatAsState(
        targetValue = if (minimized) 1f else 0f,
        animationSpec = GlassSpring.swiftUI(response = 0.36f, dampingFraction = 0.84f).spec,
        label = "LiquidTabBar minimize",
    )
    val p = minimizeProgress.coerceIn(0f, 1f)
    val tabsFadeProgress = ((p - 0.15f) / 0.55f).coerceIn(0f, 1f)
    val showTabs = p < 0.85f
    val showMinimizedOrb = p > 0.15f

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compactHorizontal = maxWidth >= 560.dp && !minimized
        if (compactHorizontal) {
            val slotCount = tabs.size + if (hasSearch) 1 else 0
            val barHeight = 48.dp
            val slotWidth = minOf(116.dp, (maxWidth - 16.dp).coerceAtLeast(1.dp) / slotCount)
            val barWidth = slotWidth * slotCount + 8.dp
            val selectedIndex = if (searchSelected && hasSearch) tabs.size else tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)
            LiquidBottomTabsLayout(
                selectedTabIndex = { selectedIndex },
                onTabSelected = { idx ->
                    if (hasSearch && idx == tabs.size) {
                        haptics(LiquidHaptic.SelectionTick)
                        searchAction?.invoke()
                    } else {
                        onSelect(tabs[idx].key)
                    }
                },
                backdrop = backdrop,
                tabsCount = slotCount,
                modifier = Modifier.size(barWidth, barHeight).selectableGroup(),
                tint = tint,
                height = barHeight,
            ) {
                for (tab in tabs) {
                    val chosen = !searchSelected && tab.key == selected
                    LiquidBottomTab(
                        onClick = { onSelect(tab.key) },
                        modifier = Modifier.semantics { this.selected = chosen },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiquidIcon(tab.symbol, appearance.contentColor, Modifier.size(18.dp))
                            BasicText(
                                tab.label,
                                style = TextStyle(appearance.contentColor, 13.sp, FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (hasSearch && searchAction != null) {
                    LiquidBottomTab(
                        onClick = {
                            haptics(LiquidHaptic.SelectionTick)
                            searchAction()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = searchLabel
                            this.selected = searchSelected
                        },
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiquidIcon(LiquidSymbol.Search, appearance.contentColor, Modifier.size(18.dp))
                            BasicText(
                                searchLabel,
                                style = TextStyle(appearance.contentColor, 13.sp, FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val expandedHeight = 56.dp
        val minimizedOrbSize = 52.dp
        val gap = 10.dp
        val reservedForSearch = if (hasSearch) expandedHeight + gap else 0.dp
        val availableBarWidth = (maxWidth - reservedForSearch).coerceAtLeast(expandedHeight)
        val slot = minOf(89.dp, (availableBarWidth - 8.dp).coerceAtLeast(1.dp) / tabs.size)
        val expandedBarWidth = slot * tabs.size + 8.dp
        val currentHeight = lerp(expandedHeight, minimizedOrbSize, p)
        val currentBarWidth = lerp(expandedBarWidth, minimizedOrbSize, p)
        val index = tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)

        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Box(Modifier.size(currentBarWidth, currentHeight)) {
                    if (showTabs) {
                        LiquidBottomTabsLayout(
                            selectedTabIndex = { index },
                            onTabSelected = { if (!minimized) onSelect(tabs[it].key) },
                            backdrop = backdrop,
                            tabsCount = tabs.size,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - tabsFadeProgress }
                                .selectableGroup(),
                            tint = tint,
                            height = currentHeight,
                        ) {
                            for (tab in tabs) {
                                val chosen = !searchSelected && tab.key == selected
                                LiquidBottomTab(
                                    onClick = { if (!minimized) onSelect(tab.key) },
                                    modifier = Modifier.semantics { this.selected = chosen },
                                ) {
                                    LiquidIcon(tab.symbol, appearance.contentColor, Modifier.size(23.dp))
                                    BasicText(
                                        tab.label,
                                        style = TextStyle(appearance.contentColor, 10.sp, FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (showMinimizedOrb) {
                        val iconColor = if (tint.isSpecified) tint else appearance.contentColor
                        LiquidTabOrbSurface(
                            backdrop = backdrop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = tabsFadeProgress }
                                .semantics { contentDescription = activeTab.label },
                            shape = if (p > 0.9f) CircleShape else LiquidCapsule,
                            interactive = minimized,
                            onClick = if (minimized) {
                                {
                                    haptics(LiquidHaptic.SelectionTick)
                                    onExpandFromMinimized?.invoke() ?: onSelect(activeTab.key)
                                }
                            } else {
                                null
                            },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LiquidIcon(activeTab.symbol, iconColor, Modifier.size(22.dp))
                            }
                        }
                    }
                }
                if (searchAction != null) {
                    val activeSearchColor = if (searchSelected && tint.isSpecified) tint else appearance.contentColor
                    val searchOrbTint = if (searchSelected && tint.isSpecified && appearance.dark) {
                        tint.copy(alpha = .20f)
                    } else {
                        Color.Unspecified
                    }
                    LiquidTabOrbSurface(
                        backdrop = backdrop,
                        modifier = Modifier.size(currentHeight).semantics {
                            contentDescription = searchLabel
                            this.selected = searchSelected
                        },
                        shape = CircleShape,
                        tint = searchOrbTint,
                        interactive = true,
                        onClick = {
                            haptics(LiquidHaptic.SelectionTick)
                            searchAction()
                        },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LiquidIcon(
                                LiquidSymbol.Search,
                                activeSearchColor,
                                Modifier.size(22.dp),
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(loose) }
            val bar = placeables[0]
            val search = placeables.getOrNull(1)
            val gapPx = gap.roundToPx()
            val layoutWidth = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                bar.width + if (search != null) gapPx + search.width else 0
            }
            val layoutHeight = maxOf(bar.height, search?.height ?: 0)
            val expandedGroupWidth = bar.width + if (search != null) gapPx + search.width else 0
            val centeredBarX = (layoutWidth - expandedGroupWidth) / 2f
            val minimizedBarX = if (search != null) 0f else (layoutWidth - bar.width) / 2f
            val barX = lerp(centeredBarX, minimizedBarX, p).roundToInt()
            layout(layoutWidth, layoutHeight) {
                bar.placeRelative(barX, (layoutHeight - bar.height) / 2)
                if (search != null) {
                    val centeredSearchX = centeredBarX + bar.width + gapPx
                    val minimizedSearchX = (layoutWidth - search.width).toFloat()
                    val searchX = lerp(centeredSearchX, minimizedSearchX, p).roundToInt()
                    search.placeRelative(searchX, (layoutHeight - search.height) / 2)
                }
            }
        }
    }
}

/** Shares the exact `TabMaterialShader` glass response so the search & minimized orbs match the adjacent tab bar. */
@Composable
private fun LiquidTabOrbSurface(
    backdrop: Backdrop,
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isRuntimeShaderSupported()) {
        LiquidGlassSurface(
            backdrop = backdrop,
            modifier = modifier,
            shape = shape,
            variant = LiquidGlassVariant.Regular,
            tint = tint,
            interactive = interactive,
            onClick = onClick,
            content = content,
        )
        return
    }
    val tokens = LocalLiquidGlassTokens.current
    val tab = tokens.tabs
    val appearance = liquidGlassAppearance()
    val backing = tab.backingFraction(appearance)
    val plateRim = rememberLiquidRimHighlight(appearance.dark, layeredEdge = true, reflectionInSurface = true)
    val scope = rememberCoroutineScope()
    val highlight = if (interactive) {
        remember(scope, tokens) {
            InteractiveHighlight(scope, tokens.press, intensity = tokens.highlightIntensity)
        }
    } else {
        null
    }
    val interactionMod = when {
        highlight != null && onClick != null ->
            highlight.gestureModifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        highlight != null -> highlight.gestureModifier
        onClick != null -> Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
        else -> Modifier
    }
    val motionMod = if (highlight != null) Modifier.graphicsLayer { highlight.motion.apply(this) } else Modifier
    val hasTint = tint.isSpecified && tint.alpha > 0f
    Box(
        modifier
            .then(interactionMod)
            .then(motionMod)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    val spread = (if (appearance.dark) tab.darkDiffusionRadius else tab.diffusionRadius).toPx()
                    padding = ceil(spread * 2.5f + 3.dp.toPx())
                    runtimeShaderEffect("Tab backing material", TabMaterialShader, "content") {
                        setFloatUniform("halfSize", size.width / 2f, size.height / 2f)
                        setFloatUniform("inset", padding)
                        setFloatUniform("density", density)
                        setFloatUniform("spread", spread)
                        setFloatUniform("softMix", tab.diffusionMix)
                        setFloatUniform("reflection", tab.rimReflection * tokens.highlightIntensity)
                        setFloatUniform("hairline", tab.topHairlineOpacity * tokens.highlightIntensity.coerceIn(0f, 1f))
                        setFloatUniform("dark", if (appearance.dark) 1f else 0f)
                        setFloatUniform("backing", backing)
                        setFloatUniform("active", 0f)
                    }
                },
                highlight = { plateRim.copy(alpha = (tab.restingPlateRimStrength * tokens.highlightIntensity).coerceIn(0f, 1f)) },
                shadow = { LiquidSurfaceShadow },
                onDrawSurface = {
                    if (hasTint) drawRect(tint)
                    highlight?.drawLight(this, brightenBackdrop = !appearance.dark)
                },
            ),
        propagateMinConstraints = true,
        content = content,
    )
}
