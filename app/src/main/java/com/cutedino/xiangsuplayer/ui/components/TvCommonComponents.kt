package com.cutedino.xiangsuplayer.ui.components

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.StreamResult
import com.cutedino.xiangsuplayer.ui.theme.BadgeKugou
import com.cutedino.xiangsuplayer.ui.theme.BadgeKuwo
import com.cutedino.xiangsuplayer.ui.theme.BadgeLx
import com.cutedino.xiangsuplayer.ui.theme.BadgeMigu
import com.cutedino.xiangsuplayer.ui.theme.BadgeNetease
import com.cutedino.xiangsuplayer.ui.theme.BadgeQqMusic
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayGold
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayOrange
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPink
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurplePrimary
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurface
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
import kotlin.math.PI
import kotlin.math.sin

enum class TvMainTab(val title: String, val icon: ImageVector) {
    EXPLORE("探索", Icons.Default.Explore),
    TOPLIST("榜单", Icons.Default.Leaderboard),
    SEARCH("搜索", Icons.Default.Search),
    LIBRARY("曲库", Icons.Default.LibraryMusic),
    SOURCE_MARKET("音源市场", Icons.Default.Extension),
    HISTORY("历史", Icons.Default.History),
    SETTINGS("设置", Icons.Default.Settings)
}

/**
 * 平台与音质徽标组件（对齐主项目 WY / TX / KG / KW / MG / LX 标签）
 */
@Composable
fun SongPlatformBadge(
    source: String,
    qualityText: String? = null,
    sourceNameText: String? = null,
    isTrial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (label, bgColor) = when (source.lowercase()) {
        "wy", "netease", "netease_native" -> "网易云" to BadgeNetease
        "tx", "qq" -> "QQ音乐" to BadgeQqMusic
        "kg", "kugou" -> "酷狗" to BadgeKugou
        "kw", "kuwo" -> "酷我" to BadgeKuwo
        "mg", "migu" -> "咪咕" to BadgeMigu
        else -> "LX音源" to BadgeLx
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor.copy(alpha = 0.25f))
                .border(1.dp, bgColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!sourceNameText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PixelPlayPurplePrimary.copy(alpha = 0.2f))
                    .border(1.dp, PixelPlayPurplePrimary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = sourceNameText,
                    color = PixelPlayLightPurple,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!qualityText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PixelPlayGold.copy(alpha = 0.18f))
                    .border(1.dp, PixelPlayGold.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = qualityText,
                    color = PixelPlayGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (isTrial) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PixelPlayOrange.copy(alpha = 0.25f))
                    .border(1.dp, PixelPlayOrange, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "试听片段",
                    color = PixelPlayOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 主项目标志性波浪进度条 (WavyMusicSlider) TV 版 — 支持遥控器左右键快进/快退
 */
@Composable
fun TvWavyMusicSlider(
    progress: Float,
    isPlaying: Boolean,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "waveAmplitude"
    )

    val strokeColor by animateColorAsState(
        targetValue = if (isFocused) PixelPlayPink else activeColor,
        animationSpec = tween(200),
        label = "sliderStrokeColor"
    )

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent
            )
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) strokeColor.copy(alpha = 0.85f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSeekRelative(-5000L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekRelative(5000L)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val clampedProgress = progress.coerceIn(0f, 1f)
            val activeEndX = width * clampedProgress
            val waveLength = 28.dp.toPx()
            val maxAmplitude = 5.dp.toPx() * amplitudeFactor

            // 1. 绘制已播放部分波浪线
            if (activeEndX > 0f) {
                val wavePath = Path()
                val step = 3f
                var x = 0f
                var first = true
                while (x <= activeEndX) {
                    val angle = (x / waveLength) * (2f * PI.toFloat()) - phase
                    val y = centerY + sin(angle) * maxAmplitude
                    if (first) {
                        wavePath.moveTo(x, y)
                        first = false
                    } else {
                        wavePath.lineTo(x, y)
                    }
                    x += step
                }
                drawPath(
                    path = wavePath,
                    color = strokeColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 2. 绘制未播放部分平直线
            if (activeEndX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(activeEndX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. 绘制进度指示圆点
            val thumbRadius = if (isFocused) 8.5.dp.toPx() else 6.dp.toPx()
            val thumbY = if (activeEndX > 0f) {
                val angle = (activeEndX / waveLength) * (2f * PI.toFloat()) - phase
                centerY + sin(angle) * maxAmplitude
            } else {
                centerY
            }
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(activeEndX, thumbY)
            )
            if (isFocused) {
                drawCircle(
                    color = strokeColor.copy(alpha = 0.45f),
                    radius = thumbRadius * 1.7f,
                    center = Offset(activeEndX, thumbY)
                )
            }
        }
    }
}

/**
 * TV 顶部胶囊导航栏 (Top Pill Navigation Bar)
 */
@Composable
fun TvTopPillNavigationBar(
    selectedTab: TvMainTab,
    onSelectTab: (TvMainTab) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAccounts: () -> Unit,
    accountSummary: String,
    modifier: Modifier = Modifier
) {
    val mainTabs = listOf(
        TvMainTab.EXPLORE,
        TvMainTab.TOPLIST,
        TvMainTab.SEARCH,
        TvMainTab.LIBRARY,
        TvMainTab.SOURCE_MARKET
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 左侧品牌 Logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(PixelPlayPurplePrimary, PixelPlayPink)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = "像素播放器 TV",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "PixelPlay Expressive",
                    color = PixelPlayLightPurple.copy(alpha = 0.75f),
                    fontSize = 10.sp
                )
            }
        }

        // 中部主导航胶囊群
        Surface(
            color = PixelPlaySurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                mainTabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    var isFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                isSelected = isSelected,
                                onFocusChanged = { focused ->
                                    isFocused = focused
                                },
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = when {
                                isFocused -> PixelPlayPurpleDark
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> PixelPlayLightPurple
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = tab.title,
                            color = when {
                                isFocused -> PixelPlayPurpleDark
                                isSelected -> Color.White
                                else -> PixelPlayLightPurple
                            },
                            fontSize = 14.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 右侧快捷操作区 (全屏播放、历史、多平台账号、设置)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavActionPill(
                icon = Icons.Default.MusicNote,
                label = "播放页",
                isSelected = false,
                onClick = onOpenPlayer
            )
            NavActionPill(
                icon = Icons.Default.History,
                label = "历史",
                isSelected = selectedTab == TvMainTab.HISTORY,
                onClick = { onSelectTab(TvMainTab.HISTORY) }
            )
            NavActionPill(
                icon = Icons.Default.AccountCircle,
                label = accountSummary,
                isSelected = false,
                onClick = onOpenAccounts
            )
            NavActionPill(
                icon = Icons.Default.Settings,
                label = "设置",
                isSelected = selectedTab == TvMainTab.SETTINGS,
                onClick = { onSelectTab(TvMainTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavActionPill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .tvPillFocusable(
                isSelected = isSelected,
                onFocusChanged = { isFocused = it },
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) PixelPlayPurpleDark else Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (isFocused) PixelPlayPurpleDark else PixelPlayLightPurple,
            fontSize = 13.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * TV 底部悬浮胶囊 MiniPlayer (Floating Capsule MiniPlayer)
 */
@Composable
fun TvFloatingMiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    playMode: PlayMode,
    progress: Float,
    streamResult: StreamResult?,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = currentSong ?: return
    val sourceName = when (streamResult) {
        is StreamResult.Full -> streamResult.sourceName
        is StreamResult.Trial -> streamResult.sourceName
        null -> null
    }
    val isTrial = streamResult is StreamResult.Trial

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 10.dp),
        color = PixelPlaySurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 10.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶部极细进度条指示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary, PixelPlayPink)
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：封面 + 歌曲信息 + 音源徽标 (点击进入全屏播放器)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusableCard(
                            scaleFocused = 1.02f,
                            shape = RoundedCornerShape(18.dp),
                            unfocusedBackgroundColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            onClick = onOpenFullPlayer
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PixelPlaySurfaceVariant)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SongPlatformBadge(
                                source = song.source,
                                sourceNameText = sourceName,
                                isTrial = isTrial
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${song.artist} · ${song.album.ifBlank { "精选单曲" }}",
                            color = PixelPlayLightPurple,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 右侧：播放控制胶囊按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniControlCircle(
                        icon = when (playMode) {
                            PlayMode.SEQUENCE -> Icons.Default.Repeat
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                            PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                            PlayMode.HEART_MODE -> Icons.Default.Favorite
                        },
                        contentDescription = playMode.displayName,
                        onClick = onTogglePlayMode
                    )
                    MiniControlCircle(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = "上一曲",
                        onClick = onPlayPrev
                    )
                    MiniControlCircle(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        isPrimary = true,
                        onClick = onTogglePlayPause
                    )
                    MiniControlCircle(
                        icon = Icons.Default.SkipNext,
                        contentDescription = "下一曲",
                        onClick = onPlayNext
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniControlCircle(
    icon: ImageVector,
    contentDescription: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(if (isPrimary) 44.dp else 38.dp)
            .tvPillFocusable(
                isSelected = isPrimary,
                scaleFocused = 1.12f,
                shape = CircleShape,
                onFocusChanged = { isFocused = it },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) PixelPlayPurpleDark else Color.White,
            modifier = Modifier.size(if (isPrimary) 22.dp else 18.dp)
        )
    }
}

/**
 * TV 全局沉浸式动态背景层 (支持主项目深蓝紫流体光晕 + 专辑封面高斯模糊 + 可选必应/自定义壁纸)
 */
@Composable
fun TvImmersiveBackdrop(
    coverUrl: String?,
    useWallpaper: Boolean,
    wallpaperUrl: String?,
    wallpaperOpacityPercent: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPlayPurpleDark)
    ) {
        if (useWallpaper) {
            val resolvedUrl = if (!wallpaperUrl.isNullOrBlank()) {
                wallpaperUrl
            } else {
                "https://bing.biturl.top/?resolution=1920&format=image&index=0&mkt=zh-CN"
            }
            AsyncImage(
                model = resolvedUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val overlayAlpha = (wallpaperOpacityPercent / 100f).coerceIn(0.15f, 0.92f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PixelPlayPurpleDark.copy(alpha = overlayAlpha))
            )
        } else {
            // 主项目 PixelPlay 流体弥散背景 + 专辑封面柔光层
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(90.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.25f),
                                secondary.copy(alpha = 0.16f),
                                PixelPlayPurpleDark.copy(alpha = 0.92f)
                            ),
                            radius = 1200f
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PixelPlayPurpleDark.copy(alpha = 0.72f),
                                PixelPlayPurpleDark.copy(alpha = 0.85f),
                                PixelPlayPurpleDark.copy(alpha = 0.96f)
                            )
                        )
                    )
            )
        }
    }
}
