package com.cutedino.xiangsuplayer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable geometry: focus changes the outline and tone, never the list layout. */
@Suppress("UNUSED_PARAMETER")
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
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val duration = (140 * LocalTvMotionScale.current).toInt()
    LaunchedEffect(focused) { onFocusChanged?.invoke(focused) }
    val border by animateColorAsState(if (focused) focusedBorderColor ?: scheme.primary else unfocusedBorderColor,
        tween(duration), label = "contentFocusOutline")
    val fill by animateColorAsState(if (focused) focusedBackgroundColor ?: scheme.surfaceVariant
        else unfocusedBackgroundColor ?: Color.Transparent, tween(duration), label = "contentFocusTone")
    val base = this.tvFocusTarget(focusKey).heightIn(min = 48.dp).clip(shape)
        .background(fill, shape).border(borderWidth, border, shape)
    if (onClick == null) base.focusable(interactionSource = source)
    else base.clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Compatibility entry point for existing call sites; the visual is a TV text action. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.tvPillFocusable(
    isSelected: Boolean = false,
    scaleFocused: Float = 1f,
    shape: Shape = RoundedCornerShape(6.dp),
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusKey: String? = null,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val duration = (140 * LocalTvMotionScale.current).toInt()
    LaunchedEffect(focused) { onFocusChanged?.invoke(focused) }
    val fill by animateColorAsState(when { focused -> scheme.primary; isSelected -> scheme.surfaceVariant; else -> Color.Transparent },
        tween(duration), label = "actionFocusTone")
    val outline by animateColorAsState(when { focused -> scheme.primary; isSelected -> scheme.outline; else -> Color.Transparent },
        tween(duration), label = "actionFocusOutline")
    return this.tvFocusTarget(focusKey).heightIn(min = 48.dp).clip(shape).background(fill, shape)
        .border(2.dp, outline, shape).clickable(interactionSource = source, indication = null, onClick = onClick)
}
