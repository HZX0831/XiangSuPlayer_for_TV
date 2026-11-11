package com.cutedino.xiangsuplayer.ui.player

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.key

import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec
import androidx.compose.animation.core.animateDpAsState
import com.cutedino.xiangsuplayer.ui.theme.LocalTvMotionScale

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

private data class PlayerLayoutState(val queue: List<PlaybackQueueEntry>, val currentEntryId: String?, val seekable: Boolean)

@Composable
fun TvFullPlayerScreen(onFinish: () -> Unit) {
    val song by PlayerController.currentSong.observeAsState()
    val isPlaying by PlayerController.isPlaying.observeAsState(false)
    val playMode by PlayerController.playMode.observeAsState(PlayMode.SEQUENCE)
    val lyrics by PlayerController.lyrics.observeAsState(emptyList())
    val activeLyricIndex by PlayerController.activeLyricIndex.observeAsState(-1)
    val layoutState = remember {
        PlayerController.uiState.map { PlayerLayoutState(it.queue, it.currentEntryId, it.seekable) }.distinctUntilChanged()
    }
    val initial = PlayerController.uiState.value
    val playback by layoutState.collectAsState(initial = PlayerLayoutState(initial.queue, initial.currentEntryId, initial.seekable))
    val context = androidx.compose.ui.platform.LocalContext.current
    val quality by PlayerController.quality.observeAsState(AudioQuality.LOSSLESS)
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
            previewPosition = PlayerController.currentPosition.value
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

    Box(
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
        TvImmersiveBackdrop(
            coverUrl = song?.coverUrl,
            useWallpaper = SessionManager.useWallpaperBackground,
            wallpaperUrl = SessionManager.customWallpaperUrl,
            wallpaperOpacityPercent = SessionManager.wallpaperOpacityPercent
        )
        TvPlayerLayout(
            modifier = if (pureLyrics) Modifier.focusRequester(lyricsFocus).focusable() else Modifier,
            stage = { TvPlayerStage(song, quality, stream, lyrics, activeLyricIndex, showTranslation, pureLyrics, it) },
            controls = {
            if (!pureLyrics) {
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TvPlayerProgress(isPlaying, playback.seekable, previewPosition, progressFocus, toggleSeek,
                    onPreview = { previewPosition = it }, onExitDown = { controls.request() })
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
        })
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
                    Text(listOfNotNull(song?.title, song?.artist, song?.album).filter(String::isNotBlank).joinToString("\n"),
                        Modifier.tvFocusableCard(onClick = null).padding(8.dp), style = MaterialTheme.typography.bodyMedium)
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

/** Production layout shared by the Activity and native screenshot fixtures. */
@Composable
internal fun TvPlayerLayout(modifier: Modifier = Modifier, stage: @Composable (Modifier) -> Unit,
    controls: @Composable ColumnScope.() -> Unit) {
    val spec = rememberTvWindowSpec()
    Column(modifier.fillMaxSize().padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stage(Modifier.weight(1f).fillMaxWidth())
        controls()
    }
}

@Composable
private fun TvPlayerProgress(playing: Boolean, seekable: Boolean, preview: Long?, focus: FocusRequester,
    toggle: () -> Unit, onPreview: (Long?) -> Unit, onExitDown: () -> Boolean) {
    val position by PlayerController.currentPosition.collectAsState()
    val duration by PlayerController.duration.collectAsState()
    val displayed = preview ?: position
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(formatDurationMs(displayed), style = MaterialTheme.typography.labelMedium)
        TvWavyMusicSlider(if (duration > 0) displayed.toFloat() / duration else 0f,
            playing && preview == null, seekable, preview != null, toggle,
            onSeekRelative = { onPreview(((preview ?: position) + it).coerceIn(0L, duration)) },
            onExitDown = onExitDown, modifier = Modifier.weight(1f).focusRequester(focus),
            onSetProgress = { onPreview(null); PlayerController.seekTo((it * duration).toLong()) })
        Text(formatDurationMs(duration), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TvPlayerDrawer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val spec = rememberTvWindowSpec()
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding), contentAlignment = Alignment.CenterEnd) {
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
            val compact = rememberTvWindowSpec().compact
            if (compact) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvArtwork(song?.coverUrl, Modifier.size(48.dp))
                    Text(listOfNotNull(song?.title, song?.artist).joinToString(" · "), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TvSyncedLyricsPanel(lyrics, activeIndex, showTranslation, Modifier.weight(1f).fillMaxWidth(), compact = true)
            } else {
                val stageWidth = maxWidth
                val coverSize = minOf(maxHeight * 0.70f, stageWidth * 0.31f, 280.dp)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.width(stageWidth * 0.36f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TvArtwork(song?.coverUrl, Modifier.size(coverSize))
                        PlayerTrackInfo(song, quality, stream, compact = false)
                    }
                    TvSyncedLyricsPanel(lyrics, activeIndex, showTranslation, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun TvSyncedLyricsPanel(lyrics: List<LyricLine>, activeIndex: Int, showTranslation: Boolean,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    val listState = rememberLazyListState()
    val motion = com.cutedino.xiangsuplayer.ui.theme.LocalTvMotionScale.current
    LaunchedEffect(activeIndex, lyrics.size) {
        if (activeIndex in lyrics.indices) {
            val target = (activeIndex - if (compact) 0 else 1).coerceAtLeast(0)
            if (motion == 0f) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }
    Box(modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        if (lyrics.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tv_player_no_lyrics), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.tv_player_no_lyrics_hint), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        } else LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = if (compact) 0.dp else 40.dp, bottom = if (compact) 16.dp else 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
    val compact = rememberTvWindowSpec().compact
    val actions = listOf(
        Triple(Icons.Rounded.SkipPrevious, stringResource(R.string.tv_previous), onPrevious),
        Triple(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (failed) "重试" else if (isPlaying) "暂停" else "播放", onPlayPause),
        Triple(Icons.Rounded.SkipNext, stringResource(R.string.tv_next), onNext),
        Triple(Icons.Rounded.QueueMusic, stringResource(R.string.tv_queue), onQueue),
        Triple(Icons.Rounded.MoreHoriz, stringResource(R.string.tv_more), onMore)
    )
    val keys = listOf("previous", "play-pause", "next", "queue", "more")
    Row(modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { index, (icon, label, click) -> key(keys[index]) {
            PlayerControlPill(icon, label, isPrimary = index == 1, compact = compact, playing = isPlaying,
                focusKey = keys[index], modifier = (if (compact) Modifier.weight(1f) else Modifier)
                    .then(if (index == 1) Modifier.focusRequester(playbackFocus) else Modifier), onClick = click)
        } }
    }
}

@Composable
private fun PlayerControlPill(icon: ImageVector, label: String, isPrimary: Boolean = false,
    compact: Boolean = false, playing: Boolean = false, focusKey: String? = null,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val radius by animateDpAsState(if (isPrimary) (if (playing) 16.dp else 20.dp) else 8.dp,
        tween((160 * LocalTvMotionScale.current).toInt()), label = "playButtonShape")
    val foreground = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val base = modifier.tvPillFocusable(isPrimary = isPrimary, shape = RoundedCornerShape(radius),
        onFocusChanged = { focused = it }, focusKey = focusKey, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = if (compact) 4.dp else if (isPrimary) 22.dp else 14.dp, vertical = 8.dp)
    if (compact) Column(base, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = foreground)
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground, maxLines = 1)
    } else Row(base, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(if (isPrimary) 28.dp else 24.dp), tint = foreground)
        Text(label, style = MaterialTheme.typography.labelLarge, color = foreground, maxLines = 1)
    }
}

@Composable
private fun TvPlayQueueDrawer(queueList: List<PlaybackQueueEntry>, currentId: String?, onClose: () -> Unit) {
    val currentIndex = queueList.indexOfFirst { it.id == currentId }
    val closeFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    var returnToAction by remember { mutableStateOf(false) }
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
            if (returnToAction) actionFocus.requestFocus() else rowFocus.requestFocus()
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
                                isCurrent = entry.id == currentId,
                                focusKey = "queue/" + entry.id,
                                modifier = Modifier.weight(1f)
                                    .then(if (index == targetIndex) Modifier.focusRequester(rowFocus) else Modifier)
                            ) { PlayerController.playQueueEntry(entry.id) }
                            TvTextAction("更多", modifier = if (index == targetIndex) Modifier.focusRequester(actionFocus) else Modifier) {
                                targetIndex = index; returnToAction = true; actionId = entry.id
                            }
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
                            returnToAction = false
                            PlayerController.moveQueueEntryNext(activeAction)
                            targetIndex = (currentIndex + if (actionIndex < currentIndex) 0 else 1).coerceAtLeast(0)
                        }
                    }
                    TvTextAction("移除") {
                        dismiss()
                        returnToAction = false
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
                                lineHeight = 24.sp
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
