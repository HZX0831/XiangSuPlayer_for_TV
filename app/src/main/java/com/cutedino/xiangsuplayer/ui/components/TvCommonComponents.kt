package com.cutedino.xiangsuplayer.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory

import androidx.compose.ui.text.style.TextDecoration


import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import com.cutedino.xiangsuplayer.ui.theme.LocalTvMotionScale
import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec
import androidx.compose.foundation.layout.widthIn

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Favorite
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.selected
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
    EXPLORE("首页", Icons.Rounded.Explore),
    DISCOVER("发现音乐", Icons.Rounded.Explore),
    TOPLIST("榜单", Icons.Rounded.Leaderboard),
    SEARCH("搜索", Icons.Rounded.Search),
    LIBRARY("曲库", Icons.Rounded.LibraryMusic),
    SOURCE_MARKET("音源市场", Icons.Rounded.Extension),
    MORE("更多", Icons.Rounded.MoreHoriz),
    HISTORY("历史", Icons.Rounded.History),
    SETTINGS("设置", Icons.Rounded.Settings)
}

/**
 * 平台与音质徽标组件（对齐主项目 WY / TX / KG / KW / MG / LX 标签）
 */
@Composable
fun SongPlatformBadge(source: String, qualityText: String? = null, sourceNameText: String? = null,
    isTrial: Boolean = false, modifier: Modifier = Modifier) {
    val label = when {
        source.startsWith("local:") || source.contains("local", true) -> "本地目录"
        source.startsWith("dav:") || source.contains("webdav", true) -> "WebDAV"
        source in listOf("wy", "netease", "netease_native") -> "网易云"
        source in listOf("tx", "qq") -> "QQ音乐"
        source in listOf("kg", "kugou") -> "酷狗"
        source in listOf("kw", "kuwo") -> "酷我"
        source in listOf("mg", "migu") -> "咪咕"
        else -> sourceNameText?.takeIf { it.isNotBlank() } ?: "扩展音源"
    }
    val parts = listOfNotNull(label, qualityText?.takeIf { it.isNotBlank() }, if (isTrial) "试听" else null)
    Text(parts.joinToString(" · "), modifier, color = if (isTrial) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * 主项目标志性波浪进度条 (WavyMusicSlider) TV 版 — 支持遥控器左右键快进/快退
 */
@Composable
fun TvWavyMusicSlider(
    progress: Float,
    isPlaying: Boolean,
    canSeek: Boolean,
    isAdjusting: Boolean,
    onToggleAdjust: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onExitDown: () -> Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    onSetProgress: ((Float) -> Unit)? = null
) {
    val progressDescription = stringResource(if (isAdjusting) R.string.tv_progress_adjust else R.string.tv_progress)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val phase = 0f

    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !isAdjusting) 1f else 0f,
        animationSpec = tween(durationMillis = (350 * LocalTvMotionScale.current).toInt()),
        label = "waveAmplitude"
    )

    val strokeColor by animateColorAsState(
        targetValue = activeColor,
        animationSpec = tween((200 * LocalTvMotionScale.current).toInt()),
        label = "sliderStrokeColor"
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent
            )
            .border(
                width = 2.dp,
                color = if (isFocused) strokeColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .onKeyEvent { event ->
                val key = event.nativeKeyEvent
                if (!canSeek || key.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (key.repeatCount == 0) onToggleAdjust()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isAdjusting) onSeekRelative(if (key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -5000L else 5000L)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (isAdjusting) true else onExitDown()
                    KeyEvent.KEYCODE_DPAD_UP -> isAdjusting
                    else -> false
                }
            }
            .semantics {
                contentDescription = progressDescription
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                if (!canSeek) disabled()
                if (canSeek && onSetProgress != null) {
                    setProgress { requested ->
                        onSetProgress(requested.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            // Always focusable: toggling focusability with canSeek (duration resets to 0 on every
            // track change) would clear focus from a focused slider. Keys are ignored while !canSeek.
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val centerY = size.height / 2f
            val clampedProgress = progress.coerceIn(0f, 1f)
            val activeEndX = width * clampedProgress
            val waveLength = 20.dp.toPx()
            val maxAmplitude = 1.8.dp.toPx() * amplitudeFactor

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
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
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

        }
    }
}

/** Shared browse geometry; screenshot fixtures use the same frame as MainActivity. */
@Composable
internal fun TvBrowseFrame(navigation: @Composable () -> Unit, content: @Composable () -> Unit,
    miniPlayer: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val spec = rememberTvWindowSpec()
    Column(modifier.fillMaxSize().padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spec.gutter)) {
            navigation()
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
        miniPlayer()
    }
}

@Composable
fun TvNavigationRail(selectedTab: TvMainTab, onSelectTab: (TvMainTab) -> Unit,
    selectedRequester: FocusRequester, modifier: Modifier = Modifier) {
    val spec = rememberTvWindowSpec()
    val tabs = listOf(TvMainTab.EXPLORE, TvMainTab.SEARCH, TvMainTab.LIBRARY, TvMainTab.TOPLIST, TvMainTab.MORE)
    val active = when (selectedTab) { TvMainTab.DISCOVER -> TvMainTab.EXPLORE; in tabs -> selectedTab; else -> TvMainTab.MORE }
    Column(modifier.width(spec.railWidth).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("像素", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        if (!spec.compact) Text("播放器 TV", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEach { tab ->
                key(tab) {
                    val chosen = tab == active
                    Row(Modifier.fillMaxWidth().then(if (chosen) Modifier.focusRequester(selectedRequester) else Modifier)
                        .tvFocusableCard(focusKey = "nav/" + tab.name, selected = chosen, onClick = { onSelectTab(tab) })
                        .heightIn(min = 52.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(tab.icon, null, Modifier.size(20.dp), tint = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tab.title, style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (chosen) TextDecoration.Underline else null,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun TvMiniPlayer(currentSong: Song?, isPlaying: Boolean, playMode: PlayMode, progress: Float,
    streamResult: StreamResult?, onTogglePlayPause: () -> Unit, onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit, onTogglePlayMode: () -> Unit, onOpenFullPlayer: () -> Unit,
    modifier: Modifier = Modifier, failure: String? = null, showArtwork: Boolean = true) {
    val song = currentSong ?: return
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(MaterialTheme.colorScheme.primary))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).tvFocusableCard(focusKey = "mini/info", onClick = onOpenFullPlayer)
                .padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showArtwork) TvArtwork(song.coverUrl, Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(failure ?: listOf(song.artist, if (isPlaying) "正在播放" else "已暂停").filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MiniControlCircle(when (playMode) { PlayMode.SEQUENCE -> Icons.Rounded.Repeat; PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
                PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne; PlayMode.HEART_MODE -> Icons.Rounded.Favorite }, playMode.displayName, onClick = onTogglePlayMode)
            MiniControlCircle(Icons.Rounded.SkipPrevious, "上一首", onClick = onPlayPrev)
            MiniControlCircle(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (failure != null) "重试" else if (isPlaying) "暂停" else "播放", isPrimary = true, onClick = onTogglePlayPause)
            MiniControlCircle(Icons.Rounded.SkipNext, "下一首", onClick = onPlayNext)
        }
    }
}

@Composable
internal fun TvArtwork(
    coverUrl: String?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxSize(0.42f)
        )
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun MiniControlCircle(icon: ImageVector, contentDescription: String, isPrimary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val compact = rememberTvWindowSpec().compact
    Column(Modifier.width(if (compact) 52.dp else 78.dp).tvPillFocusable(isPrimary = isPrimary, onFocusChanged = { focused = it }, onClick = onClick)
        .padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, if (compact) contentDescription else null, Modifier.size(24.dp), tint = foreground)
        if (!compact) Text(contentDescription, style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1)
    }
}

@Composable
fun TvImmersiveBackdrop(coverUrl: String?, useWallpaper: Boolean, wallpaperUrl: String?,
    wallpaperOpacityPercent: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (useWallpaper) {
            AsyncImage(model = wallpaperUrl?.takeIf { it.isNotBlank() }
                ?: "https://bing.biturl.top/?resolution=1920&format=image&index=0&mkt=zh-CN",
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(
                alpha = 0.82f + (wallpaperOpacityPercent / 100f).coerceIn(0f, 1f) * 0.16f)))
        }
    }
}

@Composable
internal fun TvDialogPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable RowScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Modal actions must not replace the caller page's remembered target.
        CompositionLocalProvider(LocalTvFocusMemory provides null) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = modifier
                    .width(minOf(maxWidth - 32.dp, 640.dp))
                    .heightIn(max = maxHeight - 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth(), content = body)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        content = footer
                    )
                }
            }
        }
        }
    }
}

@Composable
internal fun TvStatusText(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text,
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
internal fun TvTextAction(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    focusKey: String? = null,
    contentAlignment: Alignment = Alignment.Center,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.tvPillFocusable(isSelected = selected, isPrimary = primary, enabled = enabled, busy = busy, focusKey = focusKey, onFocusChanged = { focused = it }, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = contentAlignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (busy) label + " · " + stringResource(R.string.tv_processing) else label, style = MaterialTheme.typography.labelLarge,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
