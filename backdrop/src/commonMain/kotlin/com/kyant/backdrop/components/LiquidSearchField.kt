package com.kyant.backdrop.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop

/** An inline search field. Supply a backdrop when the field floats over scrolling content. */
@Composable
fun LiquidSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    colors: LiquidListColors = liquidListColors(),
    cursorColor: Color = colors.ink,
    backdrop: Backdrop? = null,
    onSearch: (String) -> Unit = {},
    cancelLabel: String? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val shape = LiquidCapsule
    val appearance = liquidGlassAppearance()
    val rim = rememberLiquidRimHighlight(appearance.dark)
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val hasShader = com.kyant.backdrop.isRuntimeShaderSupported()
    val fieldFill = if (appearance.dark) Color(0xff24262a) else Color(0xffeeeff1)
    val surface = if (backdrop == null) Modifier.background(fieldFill, shape) else Modifier.drawBackdrop(
        backdrop = backdrop, shape = { shape }, effects = { liquidSurfaceLens(appearance.controlBlurRadius, appearance, LiquidGlassRole.Bar) },
        highlight = { rim }, shadow = { LiquidSurfaceShadow },
        onDrawSurface = { if (!hasShader) drawRect(appearance.surface) },
    )
    val text = TextStyle(colors.ink, 17.sp)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).liquidControlPress(backdrop != null)) {
            Spacer(Modifier.matchParentSize().padding(vertical = 1.dp).then(surface))
            BasicTextField(value, onValueChange, Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label }.onPreviewKeyEvent {
                    if (it.key != Key.Escape || it.type != KeyEventType.KeyDown) false else {
                        if (value.isNotEmpty()) onValueChange("") else focus.clearFocus()
                        true
                    }
                },
                singleLine = true, textStyle = text, cursorBrush = SolidColor(cursorColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(value); focus.clearFocus() }),
                decorationBox = { editor ->
                    Row(Modifier.fillMaxWidth().clip(shape).heightIn(min = 44.dp).padding(start = 14.dp, end = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        LiquidIcon(LiquidSymbol.Search, colors.secondary, Modifier.size(20.dp))
                        Box(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                            contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) BasicText(placeholder, style = text.copy(color = colors.secondary),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            editor()
                        }
                        if (value.isNotEmpty()) Box(Modifier.size(44.dp).clip(CircleShape)
                            .clickable(interactionSource = null, indication = null, role = Role.Button) { onValueChange("") }
                            .semantics { contentDescription = clearLabel },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(18.dp).background(colors.secondary, CircleShape), contentAlignment = Alignment.Center) {
                                LiquidIcon(LiquidSymbol.Close, if (backdrop == null) fieldFill else colors.background, Modifier.size(12.dp))
                            }
                        } else trailingAction?.invoke()
                    }
                })
        }
        if (cancelLabel != null) AnimatedVisibility(focused, enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()) {
            LiquidActionButton(cancelLabel, { onValueChange(""); focus.clearFocus() }, cursorColor,
                style = LiquidActionStyle.Plain)
        }
    }
}
