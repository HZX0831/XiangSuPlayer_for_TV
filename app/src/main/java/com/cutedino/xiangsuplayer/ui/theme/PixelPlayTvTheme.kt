package com.cutedino.xiangsuplayer.ui.theme

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── PixelPlay 主项目核心深蓝紫调色板 (对齐 XiangsuPlayerHQ Color.kt) ──────────────
val PixelPlayPurpleDark = Color(0xFF0F1827)      // 主背景：深海蓝黑
val PixelPlaySurface = Color(0xFF162236)         // 卡片/容器底色：深邃岩石蓝
val PixelPlaySurfaceVariant = Color(0xFF1E2D47)  // 次级容器底色
val PixelPlayPurplePrimary = Color(0xFF5C9CFF)   // 主强调色：清透雾霾蓝
val PixelPlayPink = Color(0xFFF06292)            // 次强调色：珊瑚粉
val PixelPlayOrange = Color(0xFFFF8A65)          // 第三强调色：暖橘
val PixelPlayLightPurple = Color(0xFFB8D4F5)     // 冰晶浅蓝（副标题/次要文字）
val PixelPlayWhite = Color(0xFFFFFFFF)
val PixelPlayRed = Color(0xFFEF5350)
val PixelPlayGreen = Color(0xFF66BB6A)
val PixelPlayGold = Color(0xFFFFCA28)

// ── 平台徽标配色 ──────────────────────────────────────────────────────────────
val BadgeNetease = Color(0xFFE60026)
val BadgeQqMusic = Color(0xFF31C27C)
val BadgeKugou = Color(0xFF2C7FF9)
val BadgeKuwo = Color(0xFFFF9800)
val BadgeMigu = Color(0xFFE91E63)
val BadgeLx = Color(0xFF8E24AA)

@Composable
fun PixelPlayTvTheme(
    coverUrl: String? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var extractedPrimary by remember { mutableStateOf(PixelPlayPurplePrimary) }
    var extractedSecondary by remember { mutableStateOf(PixelPlayPink) }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) {
            extractedPrimary = PixelPlayPurplePrimary
            extractedSecondary = PixelPlayPink
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .size(256, 256)
                    .build()
                val drawable = loader.execute(req).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val vibrant = palette.getVibrantColor(0xFF5C9CFF.toInt())
                    val lightVibrant = palette.getLightVibrantColor(vibrant)
                    val muted = palette.getMutedColor(0xFFF06292.toInt())

                    val candidatePrimary = Color(lightVibrant)
                    // 保证 TV 深色背景下足够高对比度
                    extractedPrimary = if (candidatePrimary.luminance() < 0.22f) {
                        PixelPlayPurplePrimary
                    } else {
                        candidatePrimary
                    }
                    extractedSecondary = Color(muted)
                }
            } catch (_: Exception) {
                extractedPrimary = PixelPlayPurplePrimary
                extractedSecondary = PixelPlayPink
            }
        }
    }

    val animatedPrimary by animateColorAsState(
        targetValue = extractedPrimary,
        animationSpec = tween(durationMillis = 650),
        label = "primaryColor"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = extractedSecondary,
        animationSpec = tween(durationMillis = 650),
        label = "secondaryColor"
    )

    val colorScheme = darkColorScheme(
        primary = animatedPrimary,
        onPrimary = PixelPlayPurpleDark,
        primaryContainer = PixelPlaySurfaceVariant,
        onPrimaryContainer = PixelPlayWhite,
        secondary = animatedSecondary,
        onSecondary = PixelPlayPurpleDark,
        secondaryContainer = PixelPlaySurfaceVariant,
        onSecondaryContainer = PixelPlayLightPurple,
        tertiary = PixelPlayOrange,
        background = PixelPlayPurpleDark,
        onBackground = PixelPlayWhite,
        surface = PixelPlaySurface,
        onSurface = PixelPlayWhite,
        surfaceVariant = PixelPlaySurfaceVariant,
        onSurfaceVariant = PixelPlayLightPurple,
        error = PixelPlayRed
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
