package com.cutedino.xiangsuplayer.ui.player

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.StreamResult
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.auth.SongCommentItem
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.components.SongPlatformBadge
import com.cutedino.xiangsuplayer.ui.components.TvImmersiveBackdrop
import com.cutedino.xiangsuplayer.ui.components.TvWavyMusicSlider
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayGold
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayOrange
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPink
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurface
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val currentSong by PlayerController.currentSong.observeAsState()
            PixelPlayTvTheme(coverUrl = currentSong?.coverUrl) {
                TvFullPlayerScreen(
                    onFinish = { finish() }
                )
            }
        }
    }
}

private enum class PlayerDrawerType {
    NONE,
    QUEUE,
    COMMENTS
}

@Composable
fun TvFullPlayerScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val currentSong by PlayerController.currentSong.observeAsState()
    val isPlaying by PlayerController.isPlaying.observeAsState(false)
    val playMode by PlayerController.playMode.observeAsState(PlayMode.SEQUENCE)
    val lyrics by PlayerController.lyrics.observeAsState(emptyList())
    val activeLyricIndex by PlayerController.activeLyricIndex.observeAsState(-1)
    val queueList by PlayerController.playlistLiveData.observeAsState(emptyList())
    val quality by PlayerController.quality.observeAsState(AudioQuality.LOSSLESS)

    val currentPosition by PlayerController.currentPosition.collectAsState()
    val duration by PlayerController.duration.collectAsState()
    val streamResult by PlayerController.activeStreamResult.collectAsState()
    val isFavorite by PlayerController.isCurrentFavorite.collectAsState()
    val isPersonalFm by PlayerController.isPersonalFmMode.collectAsState()

    var activeDrawer by remember { mutableStateOf(PlayerDrawerType.NONE) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var isPureLyricsMode by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val playPauseFocusRequester = remember { FocusRequester() }

    // 90秒无操作自动进入 TV 防烧屏纯享歌词模式
    LaunchedEffect(lastInteractionMs, isPlaying, activeDrawer, showSourceDialog) {
        if (isPlaying && activeDrawer == PlayerDrawerType.NONE && !showSourceDialog && !isPureLyricsMode) {
            delay(90_000L)
            isPureLyricsMode = true
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { playPauseFocusRequester.requestFocus() }
    }

    BackHandler(enabled = activeDrawer != PlayerDrawerType.NONE || isPureLyricsMode) {
        when {
            activeDrawer != PlayerDrawerType.NONE -> activeDrawer = PlayerDrawerType.NONE
            isPureLyricsMode -> isPureLyricsMode = false
        }
    }

    // OLED 像素微移防烧屏动画
    val infiniteTransition = rememberInfiniteTransition(label = "oledShift")
    val shiftX by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shiftX"
    )
    val shiftY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shiftY"
    )

    val progress = if (duration > 0L) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val sourceName = when (val sr = streamResult) {
        is StreamResult.Full -> sr.sourceName.ifBlank { "极速直链" }
        is StreamResult.Trial -> sr.sourceName.ifBlank { "试听音源" }
        null -> "解析中..."
    }
    val isTrial = streamResult is StreamResult.Trial

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    lastInteractionMs = System.currentTimeMillis()
                    if (isPureLyricsMode) {
                        isPureLyricsMode = false
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        // 1. 沉浸式动态流体背景
        TvImmersiveBackdrop(
            coverUrl = currentSong?.coverUrl,
            useWallpaper = SessionManager.useWallpaperBackground,
            wallpaperUrl = SessionManager.customWallpaperUrl,
            wallpaperOpacityPercent = SessionManager.wallpaperOpacityPercent
        )

        // 2. 主体内容区
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    if (isPureLyricsMode) {
                        IntOffset(shiftX.roundToInt(), shiftY.roundToInt())
                    } else {
                        IntOffset.Zero
                    }
                }
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 中部：左专辑封面 + 右双语同步歌词
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                // 左侧：封面与元数据卡片
                Column(
                    modifier = Modifier.width(if (isPureLyricsMode) 280.dp else 330.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isPureLyricsMode) 230.dp else 276.dp)
                            .shadow(
                                elevation = 28.dp,
                                shape = RoundedCornerShape(28.dp),
                                spotColor = MaterialTheme.colorScheme.primary
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .background(PixelPlaySurfaceVariant)
                            .border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                    ) {
                        AsyncImage(
                            model = currentSong?.coverUrl,
                            contentDescription = currentSong?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = currentSong?.title ?: "未在播放",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${currentSong?.artist ?: "未知歌手"} · ${currentSong?.album?.ifBlank { "精选单曲" } ?: ""}",
                        color = PixelPlayLightPurple,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongPlatformBadge(
                            source = currentSong?.source ?: "wy",
                            qualityText = quality.displayName,
                            sourceNameText = sourceName,
                            isTrial = isTrial
                        )
                        if (isPersonalFm) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PixelPlayPink.copy(alpha = 0.25f))
                                    .border(1.dp, PixelPlayPink, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "私人FM",
                                    color = PixelPlayPink,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 右侧：居中平滑滚动双语歌词面版
                TvSyncedLyricsPanel(
                    lyrics = lyrics,
                    activeIndex = activeLyricIndex,
                    showTranslation = showTranslation,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // 底部：波浪进度条与全功能控制栏（纯享防烧屏模式下自动隐藏）
            AnimatedVisibility(
                visible = !isPureLyricsMode,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(400))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // 波浪进度条行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = formatDurationMs(currentPosition),
                            color = PixelPlayLightPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        TvWavyMusicSlider(
                            progress = progress,
                            isPlaying = isPlaying,
                            onSeekRelative = { delta -> PlayerController.seekRelative(delta) },
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = formatDurationMs(duration),
                            color = PixelPlayLightPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 控制按钮胶囊栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 左组：播放模式、喜欢、译文开关
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerControlPill(
                                icon = when (playMode) {
                                    PlayMode.SEQUENCE -> Icons.Default.Repeat
                                    PlayMode.SHUFFLE -> Icons.Default.Shuffle
                                    PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                    PlayMode.HEART_MODE -> Icons.Default.Favorite
                                },
                                label = playMode.displayName,
                                isSelected = playMode == PlayMode.HEART_MODE,
                                onClick = { PlayerController.togglePlayMode() }
                            )

                            PlayerControlPill(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                label = if (isFavorite) "已收藏" else "收藏",
                                isSelected = isFavorite,
                                onClick = {
                                    PlayerController.toggleFavorite()
                                    val msg = if (!isFavorite) "已加入我的收藏" else "已取消收藏"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )

                            val hasAnyTranslation = remember(lyrics) {
                                lyrics.any { !it.translation.isNullOrBlank() }
                            }
                            if (hasAnyTranslation) {
                                PlayerControlPill(
                                    icon = Icons.Default.AutoAwesome,
                                    label = if (showTranslation) "双语译文: 开" else "双语译文: 关",
                                    isSelected = showTranslation,
                                    onClick = { showTranslation = !showTranslation }
                                )
                            }
                        }

                        // 中组：上一曲、播放/暂停、下一曲
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerControlPill(
                                icon = Icons.Default.SkipPrevious,
                                label = "上一曲",
                                onClick = { PlayerController.playPrevious() }
                            )

                            PlayerControlPill(
                                icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                label = if (isPlaying) "暂停" else "播放",
                                isPrimary = true,
                                modifier = Modifier.focusRequester(playPauseFocusRequester),
                                onClick = { PlayerController.togglePlayPause() }
                            )

                            PlayerControlPill(
                                icon = Icons.Default.SkipNext,
                                label = "下一曲",
                                onClick = { PlayerController.playNext() }
                            )
                        }

                        // 右组：切换音源/音质、歌曲热评、纯享防烧屏、播放队列
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerControlPill(
                                icon = Icons.Default.Equalizer,
                                label = "换源/音质",
                                onClick = { showSourceDialog = true }
                            )

                            PlayerControlPill(
                                icon = Icons.Default.Comment,
                                label = "热评",
                                isSelected = activeDrawer == PlayerDrawerType.COMMENTS,
                                onClick = {
                                    activeDrawer = if (activeDrawer == PlayerDrawerType.COMMENTS) {
                                        PlayerDrawerType.NONE
                                    } else {
                                        PlayerDrawerType.COMMENTS
                                    }
                                }
                            )

                            PlayerControlPill(
                                icon = Icons.Default.AutoAwesome,
                                label = "防烧屏纯享",
                                onClick = { isPureLyricsMode = true }
                            )

                            PlayerControlPill(
                                icon = Icons.Default.QueueMusic,
                                label = "队列 (${queueList.size})",
                                isSelected = activeDrawer == PlayerDrawerType.QUEUE,
                                onClick = {
                                    activeDrawer = if (activeDrawer == PlayerDrawerType.QUEUE) {
                                        PlayerDrawerType.NONE
                                    } else {
                                        PlayerDrawerType.QUEUE
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. 右侧抽屉：播放队列或歌曲热评
        AnimatedVisibility(
            visible = activeDrawer != PlayerDrawerType.NONE,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            when (activeDrawer) {
                PlayerDrawerType.QUEUE -> TvPlayQueueDrawer(
                    queueList = queueList,
                    currentSongId = currentSong?.id,
                    onClose = { activeDrawer = PlayerDrawerType.NONE }
                )
                PlayerDrawerType.COMMENTS -> TvSongCommentsDrawer(
                    song = currentSong,
                    onClose = { activeDrawer = PlayerDrawerType.NONE }
                )
                PlayerDrawerType.NONE -> {}
            }
        }

        // 4. 手动切换音源与音质弹窗
        if (showSourceDialog) {
            TvSourceAndQualityDialog(
                currentQuality = quality,
                currentStreamResult = streamResult,
                onSelectSource = { adapterId ->
                    PlayerController.switchSource(adapterId)
                    Toast.makeText(context, "正在为您切换音源重新解析...", Toast.LENGTH_SHORT).show()
                    showSourceDialog = false
                },
                onSelectQuality = { q ->
                    SessionManager.saveAudioQuality(context, q)
                    PlayerController.setQuality(q)
                    Toast.makeText(context, "已切换音质为：${q.displayName}", Toast.LENGTH_SHORT).show()
                    showSourceDialog = false
                },
                onDismiss = { showSourceDialog = false }
            )
        }
    }
}

@Composable
private fun TvSyncedLyricsPanel(
    lyrics: List<LyricLine>,
    activeIndex: Int,
    showTranslation: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, lyrics.size) {
        if (activeIndex >= 0 && activeIndex < lyrics.size) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            runCatching {
                listState.animateScrollToItem(target)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(PixelPlaySurface.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (lyrics.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "♪ 纯音乐或正在匹配多平台双语歌词...",
                    color = PixelPlayLightPurple,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "支持网易云 / QQ音乐 / 酷狗 / 酷我四端歌词自动兜底",
                    color = PixelPlayLightPurple.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 110.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex
                    val distance = if (activeIndex >= 0) abs(index - activeIndex) else 2
                    val targetAlpha = when {
                        isActive -> 1f
                        distance == 1 -> 0.68f
                        distance == 2 -> 0.45f
                        else -> 0.25f
                    }
                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(250),
                        label = "lyricAlpha"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = if (isActive) 10.dp else 4.dp)
                    ) {
                        Text(
                            text = line.text,
                            color = if (isActive) Color.White else PixelPlayLightPurple,
                            fontSize = if (isActive) 24.sp else 17.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            lineHeight = if (isActive) 32.sp else 24.sp
                        )
                        if (showTranslation && !line.translation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = line.translation,
                                color = if (isActive) MaterialTheme.colorScheme.primary else PixelPlayLightPurple.copy(alpha = 0.8f),
                                fontSize = if (isActive) 16.sp else 13.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlPill(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .tvPillFocusable(
                isSelected = isPrimary || isSelected,
                scaleFocused = 1.08f,
                onFocusChanged = { isFocused = it },
                onClick = onClick
            )
            .padding(
                horizontal = if (isPrimary) 22.dp else 14.dp,
                vertical = if (isPrimary) 12.dp else 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) PixelPlayPurpleDark else Color.White,
            modifier = Modifier.size(if (isPrimary) 22.dp else 18.dp)
        )
        Text(
            text = label,
            color = if (isFocused) PixelPlayPurpleDark else Color.White,
            fontSize = if (isPrimary) 15.sp else 13.sp,
            fontWeight = if (isFocused || isPrimary || isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TvPlayQueueDrawer(
    queueList: List<Song>,
    currentSongId: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .width(420.dp)
            .fillMaxHeight(),
        color = PixelPlaySurface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "当前播放队列 (${queueList.size} 首)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "支持立即播放、插队下一首或移出队列",
                        color = PixelPlayLightPurple,
                        fontSize = 12.sp
                    )
                }
                PlayerControlPill(
                    icon = Icons.Default.Close,
                    label = "关闭",
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(queueList, key = { idx, s -> "${s.id}_$idx" }) { _, song ->
                    val isCurrent = song.id == currentSongId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else PixelPlaySurfaceVariant.copy(alpha = 0.6f)
                            )
                            .border(
                                1.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.06f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.artist} · ${song.album}",
                                color = PixelPlayLightPurple,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallQueueActionBtn(Icons.Default.PlayArrow, "播放") {
                                PlayerController.playSong(song)
                            }
                            SmallQueueActionBtn(Icons.Default.PlaylistAdd, "下一首") {
                                PlayerController.insertAsNext(song)
                                Toast.makeText(context, "已设为下一首播放: ${song.title}", Toast.LENGTH_SHORT).show()
                            }
                            SmallQueueActionBtn(Icons.Default.Delete, "移除") {
                                PlayerController.removeFromPlaylist(song)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallQueueActionBtn(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(32.dp)
            .tvPillFocusable(
                shape = CircleShape,
                onFocusChanged = { focused = it },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (focused) PixelPlayPurpleDark else Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun TvSongCommentsDrawer(
    song: Song?,
    onClose: () -> Unit
) {
    var comments by remember { mutableStateOf<List<SongCommentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(song?.id) {
        if (song == null) {
            comments = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        comments = MultiPlatformAccountManager.fetchSongHotComments(song)
        isLoading = false
    }

    Surface(
        modifier = Modifier
            .width(440.dp)
            .fillMaxHeight(),
        color = PixelPlaySurface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "精彩乐评 (${comments.size})",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = song?.title ?: "",
                        color = PixelPlayLightPurple,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayerControlPill(
                    icon = Icons.Default.Close,
                    label = "关闭",
                    onClick = onClose
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (comments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无热门评论", color = PixelPlayLightPurple, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(comments) { _, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableCard(
                                    scaleFocused = 1.02f,
                                    shape = RoundedCornerShape(16.dp),
                                    onClick = {}
                                )
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AsyncImage(
                                        model = item.avatarUrl,
                                        contentDescription = item.userName,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(PixelPlaySurfaceVariant)
                                    )
                                    Text(
                                        text = item.userName,
                                        color = PixelPlayLightPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = null,
                                        tint = PixelPlayPink,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = item.likedCount.toString(),
                                        color = PixelPlayPink,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.content,
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSourceAndQualityDialog(
    currentQuality: AudioQuality,
    currentStreamResult: StreamResult?,
    onSelectSource: (String) -> Unit,
    onSelectQuality: (AudioQuality) -> Unit,
    onDismiss: () -> Unit
) {
    val adapters = remember { SoundSourceRepository.getAllAdapters() }
    val activeSourceName = when (currentStreamResult) {
        is StreamResult.Full -> currentStreamResult.sourceName
        is StreamResult.Trial -> currentStreamResult.sourceName
        null -> ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = PixelPlaySurface,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = "音源热切换与输出音质",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "支持手动指定单一音源重新解析当前曲目，或切换无损/Hi-Res 音质档位",
                    color = PixelPlayLightPurple,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "输出音质档位",
                    color = PixelPlayGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AudioQuality.entries.forEach { q ->
                        PlayerControlPill(
                            icon = Icons.Default.Equalizer,
                            label = q.displayName,
                            isSelected = q == currentQuality,
                            onClick = { onSelectQuality(q) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "可用音源通道 (点击立即切换)",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.height(210.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(adapters) { _, adapter ->
                        val isCurrent = adapter.sourceName == activeSourceName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableCard(
                                    scaleFocused = 1.02f,
                                    shape = RoundedCornerShape(12.dp),
                                    onClick = { onSelectSource(adapter.sourceId) }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = adapter.sourceName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: ${adapter.sourceId} · 优先级 ${adapter.priority}",
                                    color = PixelPlayLightPurple,
                                    fontSize = 11.sp
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = "当前生效",
                                    color = PixelPlayOrange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    PlayerControlPill(
                        icon = Icons.Default.Close,
                        label = "关闭",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format("%02d:%02d", min, sec)
}
