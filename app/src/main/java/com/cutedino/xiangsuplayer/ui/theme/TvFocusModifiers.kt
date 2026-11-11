package com.cutedino.xiangsuplayer.ui.theme

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable geometry: focus changes the outline and tone, never the list layout. */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvFocusableCard(
    scaleFocused: Float = 1f,
    shape: Shape = RoundedCornerShape(4.dp),
    focusedBorderColor: Color? = null,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedBackgroundColor: Color? = null,
    unfocusedBackgroundColor: Color? = null,
    borderWidth: Dp = 2.dp,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusKey: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    status: String? = null
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val visible = remember { BringIntoViewRequester() }
    val processing = stringResource(R.string.tv_processing)
    val scheme = MaterialTheme.colorScheme
    val duration = (140 * LocalTvMotionScale.current).toInt()
    LaunchedEffect(focused) {
        onFocusChanged?.invoke(focused)
        if (focused) visible.bringIntoView()
    }
    val border by animateColorAsState(if (focused) focusedBorderColor ?: scheme.primary else unfocusedBorderColor,
        tween(duration), label = "contentFocusOutline")
    val fill by animateColorAsState(if (pressed && enabled && !busy) scheme.primaryContainer else if (focused) focusedBackgroundColor ?: scheme.surfaceVariant
        else unfocusedBackgroundColor ?: Color.Transparent, tween(duration), label = "contentFocusTone")
    val base = this.tvFocusTarget(focusKey).bringIntoViewRequester(visible).heightIn(min = 48.dp).clip(shape)
        .background(fill, shape).border(borderWidth, border, shape)
        .semantics {
            selected?.let { this.selected = it }
            if (!enabled || busy) disabled()
            (if (busy) processing else status)?.let { stateDescription = it }
        }
    if (onClick == null) base.focusable(interactionSource = source)
    else base.clickable(interactionSource = source, indication = null, role = Role.Button) {
        if (enabled && !busy) onClick()
    }
}

/** Compatibility entry point for existing call sites; the visual is a TV text action. */
@Suppress("UNUSED_PARAMETER")
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvPillFocusable(
    isSelected: Boolean? = null,
    scaleFocused: Float = 1f,
    shape: Shape = RoundedCornerShape(8.dp),
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusKey: String? = null,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    status: String? = null,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val visible = remember { BringIntoViewRequester() }
    val processing = stringResource(R.string.tv_processing)
    val scheme = MaterialTheme.colorScheme
    val duration = (140 * LocalTvMotionScale.current).toInt()
    LaunchedEffect(focused) {
        onFocusChanged?.invoke(focused)
        if (focused) visible.bringIntoView()
    }
    val fill by animateColorAsState(when {
        pressed && enabled && !busy -> scheme.primary.copy(alpha = 0.75f)
        focused -> scheme.primary
        isSelected == true || isPrimary -> scheme.surfaceVariant
        else -> Color.Transparent
    },
        tween(duration), label = "actionFocusTone")
    val outline by animateColorAsState(if (focused) scheme.primary else Color.Transparent,
        tween(duration), label = "actionFocusOutline")
    return this.tvFocusTarget(focusKey).bringIntoViewRequester(visible).heightIn(min = 48.dp).clip(shape).background(fill, shape)
        .border(2.dp, outline, shape).drawWithContent {
            drawContent()
            if (isSelected == true) drawLine(if (focused) scheme.onPrimary else scheme.primary,
                Offset(12.dp.toPx(), size.height - 5.dp.toPx()),
                Offset(size.width - 12.dp.toPx(), size.height - 5.dp.toPx()), 2.dp.toPx())
        }.semantics {
            isSelected?.let { selected = it }
            if (!enabled || busy) disabled()
            (if (busy) processing else status)?.let { stateDescription = it }
        }.clickable(interactionSource = source, indication = null, role = Role.Button) {
            if (enabled && !busy) onClick()
        }
}
