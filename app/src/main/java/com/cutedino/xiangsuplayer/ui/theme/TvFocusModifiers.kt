package com.cutedino.xiangsuplayer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TV 遥控器 D-Pad 焦点缩放 + 高亮描边修饰符
 */
fun Modifier.tvFocusableCard(
    scaleFocused: Float = 1.05f,
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorderColor: Color? = null,
    unfocusedBorderColor: Color = Color.White.copy(alpha = 0.08f),
    focusedBackgroundColor: Color? = null,
    unfocusedBackgroundColor: Color? = null,
    borderWidth: Dp = 2.dp,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val primary = focusedBorderColor ?: MaterialTheme.colorScheme.primary

    LaunchedEffect(isFocused) {
        onFocusChanged?.invoke(isFocused)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleFocused else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tvCardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) primary else unfocusedBorderColor,
        animationSpec = tween(durationMillis = 180),
        label = "tvCardBorder"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && focusedBackgroundColor != null -> focusedBackgroundColor
            !isFocused && unfocusedBackgroundColor != null -> unfocusedBackgroundColor
            isFocused -> PixelPlaySurfaceVariant.copy(alpha = 0.95f)
            else -> PixelPlaySurface.copy(alpha = 0.82f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "tvCardBg"
    )

    var mod = this
        .scale(scale)
        .shadow(
            elevation = if (isFocused) 14.dp else 2.dp,
            shape = shape,
            spotColor = if (isFocused) primary else Color.Black
        )
        .clip(shape)
        .background(bgColor, shape)
        .border(
            BorderStroke(if (isFocused) borderWidth else 1.dp, borderColor),
            shape
        )

    mod = if (onClick != null) {
        mod.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        mod.focusable(interactionSource = interactionSource)
    }
    mod
}

/**
 * TV 胶囊按钮焦点修饰符（用于顶部导航栏、筛选标签、播放器控制按钮）
 */
@Composable
fun Modifier.tvPillFocusable(
    isSelected: Boolean = false,
    scaleFocused: Float = 1.07f,
    shape: Shape = RoundedCornerShape(50),
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(isFocused) {
        onFocusChanged?.invoke(isFocused)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleFocused else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> primary
            isSelected -> primary.copy(alpha = 0.24f)
            else -> PixelPlaySurfaceVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(160),
        label = "pillBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> primary.copy(alpha = 0.7f)
            else -> Color.White.copy(alpha = 0.1f)
        },
        animationSpec = tween(160),
        label = "pillBorder"
    )

    return this
        .scale(scale)
        .clip(shape)
        .background(bgColor, shape)
        .border(BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor), shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
