package com.cutedino.xiangsuplayer.ui.player

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import com.cutedino.xiangsuplayer.ui.main.TvCompactSongCard
import kotlinx.coroutines.launch
import android.os.SystemClock
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.window.DialogProperties
import com.cutedino.xiangsuplayer.ui.components.TvDialogPanel
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.components.TvArtwork
import com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory
import com.cutedino.xiangsuplayer.ui.theme.TvFocusMemory
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlaybackQueueEntry
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
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPink
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurface
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
import kotlinx.coroutines.delay
import kotlin.math.abs

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

private enum class PlayerPanel { MORE, QUEUE, COMMENTS, SOURCE }

@Composable
fun TvFullPlayerScreen(onFinish: () -> Unit) {
    val song by PlayerController.currentSong.observeAsState()
    val isPlaying by PlayerController.isPlaying.observeAsState(false)
    val playMode by PlayerController.playMode.observeAsState(PlayMode.SEQUENCE)
    val lyrics by PlayerController.lyrics.observeAsState(emptyList())
    val activeLyricIndex by PlayerController.activeLyricIndex.observeAsState(-1)
    val playback by PlayerController.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val quality by PlayerController.quality.observeAsState(AudioQuality.LOSSLESS)
    val position by PlayerController.currentPosition.collectAsState()
    val duration by PlayerController.duration.collectAsState()
    val stream by PlayerController.activeStreamResult.collectAsState()
    val isFavorite by PlayerController.isCurrentFavorite.collectAsState()
    val error by PlayerController.playbackError.collectAsState()

    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var moreReturnTarget by remember { mutableStateOf<PlayerPanel?>(null) }
    var pureLyrics by remember { mutableStateOf(false) }
    var showTranslation by rememberSaveable { mutableStateOf(true) }
    var previewPosition by remember { mutableStateOf<Long?>(null) }
    var lastInteraction by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var wakeKey by remember { mutableStateOf<Int?>(null) }

    val playbackFocus = remember { FocusRequester() }
    val progressFocus = remember { FocusRequester() }
    val lyricsFocus = remember { FocusRequester() }
    val controls = remember { TvFocusMemory() }
    val closePanel = {
        panel = null
        lastInteraction = SystemClock.elapsedRealtime()
    }
    val toggleSeek = {
        val preview = previewPosition
        if (preview == null) {
            previewPosition = position
        } else {
            PlayerController.seekTo(preview)
            previewPosition = null
        }
    }

    LaunchedEffect(panel, pureLyrics) {
        if (panel == null) {
            if (pureLyrics) lyricsFocus.tryRequestFocus()
            else if (controls.lastFocused == null || !controls.restore()) playbackFocus.tryRequestFocus()
        }
    }
    LaunchedEffect(song?.source, song?.id) {
        previewPosition = null
    }
    LaunchedEffect(lastInteraction, isPlaying, panel, pureLyrics, previewPosition != null) {
        if (isPlaying && panel == null && !pureLyrics && previewPosition == null) {
            delay(90_000L)
            pureLyrics = true
        }
    }
    BackHandler {
        when {
            previewPosition != null -> previewPosition = null
            pureLyrics -> {
                pureLyrics = false
                lastInteraction = SystemClock.elapsedRealtime()
            }
            else -> onFinish()
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val isNavigation = key.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
                key.keyCode == KeyEvent.KEYCODE_ENTER || key.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            if (!isNavigation) return@onPreviewKeyEvent false
            if (wakeKey == key.keyCode) {
                if (key.action == KeyEvent.ACTION_UP) wakeKey = null
                return@onPreviewKeyEvent true
            }
            if (key.action == KeyEvent.ACTION_DOWN) {
                lastInteraction = SystemClock.elapsedRealtime()
                if (pureLyrics) {
                    wakeKey = key.keyCode
                    pureLyrics = false
                    return@onPreviewKeyEvent true
                }
            }
            false
        }
    ) {
        val compact = maxWidth < 880.dp || maxHeight < 470.dp
        TvImmersiveBackdrop(
            coverUrl = song?.coverUrl,
            useWallpaper = SessionManager.useWallpaperBackground,
            wallpaperUrl = SessionManager.customWallpaperUrl,
            wallpaperOpacityPercent = SessionManager.wallpaperOpacityPercent
        )
        val displayedPosition = previewPosition ?: position
        val lyricsOffset = Modifier
        Column(
            Modifier.fillMaxSize().then(lyricsOffset)
                .padding(horizontal = if (compact) 32.dp else 48.dp, vertical = 27.dp)
                .then(if (pureLyrics) Modifier.focusRequester(lyricsFocus).focusable() else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val lyricsContent: @Composable (Modifier) -> Unit = { modifier ->
                TvSyncedLyricsPanel(lyrics, activeLyricIndex, showTranslation, modifier)
            }
            TvPlayerStage(song, quality, stream, lyrics, activeLyricIndex, showTranslation, pureLyrics,
                Modifier.weight(1f).fillMaxWidth())

            if (!pureLyrics) {
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(formatDurationMs(displayedPosition), style = MaterialTheme.typography.labelMedium)
                    TvWavyMusicSlider(
                        progress = if (duration > 0) displayedPosition.toFloat() / duration else 0f,
                        isPlaying = isPlaying && previewPosition == null,
                        canSeek = playback.seekable,
                        isAdjusting = previewPosition != null,
                        onToggleAdjust = toggleSeek,
                        onSeekRelative = { delta ->
                            previewPosition = ((previewPosition ?: position) + delta).coerceIn(0L, duration)
                        },
                        onExitDown = { controls.request() },
                        onSetProgress = { fraction ->
                            previewPosition = null
                            PlayerController.seekTo((fraction * duration).toLong())
                        },
                        modifier = Modifier.weight(1f).focusRequester(progressFocus)
                    )
                    Text(formatDurationMs(duration), style = MaterialTheme.typography.labelMedium)
                }
                if (!playback.seekable) Text(stringResource(R.string.tv_no_seek), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (previewPosition != null) {
                    Text("左右调整 · 确定跳转 · 返回取消", style = MaterialTheme.typography.labelMedium)
                }
                CompositionLocalProvider(LocalTvFocusMemory provides controls) {
                    TvPlayerControlRow(isPlaying, error != null,
                        Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            if (key.action == KeyEvent.ACTION_DOWN && key.keyCode == KeyEvent.KEYCODE_DPAD_UP && playback.seekable) {
                                progressFocus.tryRequestFocus(); true
                            } else false
                        }, playbackFocus,
                        onPrevious = PlayerController::playPrevious,
                        onPlayPause = PlayerController::togglePlayPause,
                        onNext = PlayerController::playNext,
                        onQueue = { panel = PlayerPanel.QUEUE },
                        onMore = { moreReturnTarget = null; panel = PlayerPanel.MORE })
                }
            }
        }
    }

    when (panel) {
        PlayerPanel.QUEUE -> TvPlayerDrawer(closePanel) {
            TvPlayQueueDrawer(playback.queue, playback.currentEntryId, closePanel)
        }
        PlayerPanel.COMMENTS -> TvPlayerDrawer({ panel = PlayerPanel.MORE }) {
            TvSongCommentsDrawer(song) { panel = PlayerPanel.MORE }
        }
        PlayerPanel.SOURCE -> TvSourceAndQualityDialog(
            currentQuality = quality,
            currentStreamResult = stream,
            onSelectSource = { PlayerController.switchSource(it); closePanel() },
            onSelectQuality = { PlayerController.setQuality(it); closePanel() },
            onDismiss = { panel = PlayerPanel.MORE }
        )
        PlayerPanel.MORE -> {
            val returnFocus = remember { FocusRequester() }
            TvDialogPanel(stringResource(R.string.tv_player_options), closePanel, footer = { TvTextAction("关闭", onClick = closePanel) }) {
                LaunchedEffect(Unit) { returnFocus.requestFocus() }
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TvTextAction(
                        "播放模式 · ${playMode.displayName}",
                        if (moreReturnTarget == null) Modifier.focusRequester(returnFocus) else Modifier,
                        onClick = PlayerController::togglePlayMode
                    )
                    TvTextAction(if (isFavorite) "取消收藏" else "收藏歌曲") { PlayerController.toggleFavorite() }
                    if (lyrics.any { !it.translation.isNullOrBlank() }) {
                        TvTextAction(if (showTranslation) "隐藏译文" else "显示译文") { showTranslation = !showTranslation }
                    }
                    if (song?.let(com.cutedino.xiangsuplayer.core.library.FolderLibrary::isFolderSong) != true) TvTextAction(
                        "换源与音质",
                        if (moreReturnTarget == PlayerPanel.SOURCE) Modifier.focusRequester(returnFocus) else Modifier
                    ) {
                        moreReturnTarget = PlayerPanel.SOURCE
                        panel = PlayerPanel.SOURCE
                    }
                    TvTextAction(
                        "热门评论",
                        if (moreReturnTarget == PlayerPanel.COMMENTS) Modifier.focusRequester(returnFocus) else Modifier
                    ) {
                        moreReturnTarget = PlayerPanel.COMMENTS
                        panel = PlayerPanel.COMMENTS
                    }
                    TvTextAction(stringResource(R.string.tv_karaoke)) {
                        closePanel()
                        context.startActivity(android.content.Intent(context, KaraokeActivity::class.java))
                    }
                    TvTextAction("纯歌词模式") {
                        closePanel()
                        pureLyrics = true
                    }
                }
            }
        }
        null -> Unit
    }
}

@Composable
private fun PlayerTrackInfo(song: Song?, quality: AudioQuality, stream: StreamResult?, compact: Boolean) {
    val sourceName = when (stream) {
        is StreamResult.Full -> stream.sourceName
        is StreamResult.Trial -> stream.sourceName
        null -> null
    }
    val metadata: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier,
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                song?.title ?: "未在播放",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (song != null) {
                Text(
                    listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val bitrate = (stream as? StreamResult.Full)?.bitRate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
                SongPlatformBadge(song.source, bitrate, sourceName, stream is StreamResult.Trial)
            }
        }
    }
    if (compact) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvArtwork(song?.coverUrl, Modifier.size(60.dp))
            metadata(Modifier.weight(1f))
        }
    } else {
        metadata(Modifier.fillMaxWidth())
    }
}

@Composable
private fun TvPlayerDrawer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.width(minOf(maxWidth * 0.9f, 460.dp)).fillMaxHeight(), content = { content() })
        }
    }
}

@Composable
internal fun TvPlayerStage(song: Song?, quality: AudioQuality, stream: StreamResult?, lyrics: List<LyricLine>,
    activeIndex: Int, showTranslation: Boolean, pureLyrics: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (pureLyrics) TvSyncedLyricsPanel(lyrics, activeIndex, showTranslation, Modifier.fillMaxSize())
        else {
            val stageWidth = maxWidth
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val coverSize = minOf(maxHeight * (if (fontScale > 1.2f) 0.50f else 0.70f), stageWidth * 0.31f, 280.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.width(stageWidth * 0.36f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvArtwork(song?.coverUrl, Modifier.size(coverSize), RoundedCornerShape(8.dp))
                    PlayerTrackInfo(song, quality, stream, compact = false)
                }
                TvSyncedLyricsPanel(lyrics, activeIndex, showTranslation, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TvSyncedLyricsPanel(lyrics: List<LyricLine>, activeIndex: Int, showTranslation: Boolean,
    modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val motion = com.cutedino.xiangsuplayer.ui.theme.LocalTvMotionScale.current
    LaunchedEffect(activeIndex, lyrics.size) {
        if (activeIndex in lyrics.indices) {
            val target = (activeIndex - 1).coerceAtLeast(0)
            if (motion == 0f) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }
    Box(modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        if (lyrics.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tv_player_no_lyrics), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tv_player_no_lyrics_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        } else LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(lyrics) { index, line ->
                val active = index == activeIndex
                Column(Modifier.fillMaxWidth()) {
                    Text(line.text, color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (active) 32.sp else 24.sp, lineHeight = if (active) 44.sp else 34.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    if (showTranslation && !line.translation.isNullOrBlank()) Text(line.translation,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, lineHeight = 28.sp)
                }
            }
        }
    }
}

@Composable
internal fun TvPlayerControlRow(isPlaying: Boolean, failed: Boolean, modifier: Modifier = Modifier,
    playbackFocus: FocusRequester = remember { FocusRequester() }, onPrevious: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, onQueue: () -> Unit, onMore: () -> Unit) {
    Row(modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        PlayerControlPill(Icons.Rounded.SkipPrevious, stringResource(R.string.tv_previous), focusKey = "previous", onClick = onPrevious)
        PlayerControlPill(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (failed) "重试" else if (isPlaying) "暂停" else "播放", isPrimary = true, focusKey = "play-pause",
            modifier = Modifier.focusRequester(playbackFocus), onClick = onPlayPause)
        PlayerControlPill(Icons.Rounded.SkipNext, stringResource(R.string.tv_next), focusKey = "next", onClick = onNext)
        PlayerControlPill(Icons.Rounded.QueueMusic, stringResource(R.string.tv_queue), focusKey = "queue", onClick = onQueue)
        PlayerControlPill(Icons.Rounded.MoreHoriz, "更多", focusKey = "more", onClick = onMore)
    }
}

@Composable
private fun PlayerControlPill(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean = false,
    isSelected: Boolean = false,
    showLabel: Boolean = true,
    focusKey: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .tvPillFocusable(
                isSelected = isPrimary || isSelected,
                shape = RoundedCornerShape(if (isPrimary) 16.dp else 6.dp),
                onFocusChanged = { isFocused = it },
                focusKey = focusKey,
                onClick = onClick
            )
            .heightIn(min = 56.dp)
            .padding(
                horizontal = if (isPrimary) 22.dp else 14.dp,
                vertical = if (!showLabel) 6.dp else if (isPrimary) 12.dp else 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label.takeUnless { showLabel },
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(if (isPrimary) 28.dp else 24.dp)
        )
        if (showLabel) {
            Text(label, color = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TvPlayQueueDrawer(queueList: List<PlaybackQueueEntry>, currentId: String?, onClose: () -> Unit) {
    val currentIndex = queueList.indexOfFirst { it.id == currentId }
    val closeFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    var targetIndex by remember { mutableStateOf(currentIndex.coerceAtLeast(0)) }
    var requestFocus by remember { mutableStateOf(true) }
    var actionId by remember { mutableStateOf<String?>(null) }
    val activeAction = actionId

    LaunchedEffect(requestFocus, queueList) {
        if (!requestFocus) return@LaunchedEffect
        if (queueList.isEmpty()) {
            closeFocus.requestFocus()
        } else {
            val index = targetIndex.coerceIn(queueList.indices)
            targetIndex = index
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
            withFrameNanos { }
            rowFocus.requestFocus()
        }
        requestFocus = false
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("播放队列 · ${queueList.size} 首", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TvTextAction("关闭", Modifier.focusRequester(closeFocus), onClick = onClose)
            }
            if (queueList.isEmpty()) {
                Text("队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(queueList, key = { _, entry -> entry.id }) { index, entry ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TvCompactSongCard(
                                song = entry.song,
                                modifier = Modifier.weight(1f)
                                    .then(if (index == targetIndex) Modifier.focusRequester(rowFocus) else Modifier)
                            ) { PlayerController.playQueueEntry(entry.id) }
                            TvTextAction("更多", selected = entry.id == currentId) { actionId = entry.id }
                        }
                    }
                }
            }
        }
    }
    if (activeAction != null) {
        val actionIndex = queueList.indexOfFirst { it.id == activeAction }
        val song = queueList.getOrNull(actionIndex)?.song
        val cancel = remember { FocusRequester() }
        val dismiss = { actionId = null; targetIndex = actionIndex.coerceAtLeast(0); requestFocus = true }
        if (song != null) {
            TvDialogPanel(
                song.title,
                dismiss,
                footer = { TvTextAction("取消", Modifier.focusRequester(cancel), onClick = dismiss) }
            ) {
                LaunchedEffect(Unit) { cancel.requestFocus() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeAction != currentId) {
                        TvTextAction("设为下一首") {
                            dismiss()
                            PlayerController.moveQueueEntryNext(activeAction)
                            targetIndex = (currentIndex + if (actionIndex < currentIndex) 0 else 1).coerceAtLeast(0)
                        }
                    }
                    TvTextAction("移除") {
                        dismiss()
                        PlayerController.removeQueueEntry(activeAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSongCommentsDrawer(
    song: Song?,
    onClose: () -> Unit
) {
    var comments by remember { mutableStateOf<List<SongCommentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val closeFocus = remember { FocusRequester() }
    val commentsState = rememberLazyListState()
    val scrollDistance = with(LocalDensity.current) { 100.dp.toPx() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    LaunchedEffect(song?.source, song?.id) {
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
        modifier = Modifier.fillMaxSize(),
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
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PlayerControlPill(
                    icon = Icons.Rounded.Close,
                    label = "关闭",
                    modifier = Modifier.focusRequester(closeFocus),
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
                    Text("暂无热门评论", color = PixelPlayLightPurple, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    state = commentsState,
                    modifier = Modifier.fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            if (key.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                            when (key.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (commentsState.canScrollBackward) {
                                        scope.launch { commentsState.animateScrollBy(-scrollDistance) }
                                    } else closeFocus.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    scope.launch { commentsState.animateScrollBy(scrollDistance) }
                                    true
                                }
                                else -> false
                            }
                        }
                        .tvFocusableCard(unfocusedBackgroundColor = Color.Transparent),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(comments) { _, item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
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
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ThumbUp,
                                        contentDescription = null,
                                        tint = PixelPlayPink,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = item.likedCount.toString(),
                                        color = PixelPlayPink,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.content,
                                color = Color.White,
                                fontSize = 16.sp,
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
    val initialFocus = remember { FocusRequester() }
    val activeSource = when (currentStreamResult) {
        is StreamResult.Full -> currentStreamResult.sourceName
        is StreamResult.Trial -> currentStreamResult.sourceName
        null -> null
    }
    val listState = rememberLazyListState()
    val closeFocus = remember { FocusRequester() }
    TvDialogPanel("换源与音质", onDismiss, footer = {
        TvTextAction("关闭", Modifier.focusRequester(closeFocus), onClick = onDismiss)
    }) {
        LaunchedEffect(Unit) {
            listState.scrollToItem(AudioQuality.entries.indexOf(currentQuality) + 1)
            withFrameNanos { }
            if (!initialFocus.tryRequestFocus()) closeFocus.tryRequestFocus()
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("quality-title") { Text("音质", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(AudioQuality.entries, key = { _, quality -> "quality:${quality.name}" }) { _, quality ->
                TvTextAction(
                    quality.displayName,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (quality == currentQuality) Modifier.focusRequester(initialFocus) else Modifier),
                    selected = quality == currentQuality
                ) { onSelectQuality(quality) }
            }
            item("sources-title") { Text("可用音源", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(adapters, key = { _, adapter -> "source:${adapter.sourceId}" }) { _, adapter ->
                TvTextAction(
                    adapter.sourceName,
                    modifier = Modifier.fillMaxWidth(),
                    selected = adapter.sourceName == activeSource
                ) { onSelectSource(adapter.sourceId) }
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
