package com.cutedino.xiangsuplayer.ui.main

import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import androidx.compose.material.icons.rounded.MusicNote



import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import com.cutedino.xiangsuplayer.ui.theme.rememberTvFocusScroller
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import androidx.compose.foundation.layout.heightIn
import android.view.KeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onPreInterceptKeyBeforeSoftKeyboard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.cutedino.xiangsuplayer.ui.components.TvPhoneInputDialog
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.components.TvStatusText
import com.cutedino.xiangsuplayer.ui.components.TvArtwork
import kotlinx.coroutines.ensureActive
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import com.cutedino.xiangsuplayer.core.toplist.ToplistCatalog
import com.cutedino.xiangsuplayer.core.toplist.ToplistRepository
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.components.SongPlatformBadge
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString

// ═════════════════════════════════════════════════════════════════════════════
// 1. 探索页 (TvExploreScreen) — 私人FM、心动模式、日推、甄选歌单、多地区新歌速递
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TvExploreScreen(
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSong by PlayerController.currentSong.observeAsState()
    val scope = rememberCoroutineScope()

    val ncmAccount by NcmApi.accountState.collectAsState()
    var playlists by remember(ncmAccount) { mutableStateOf<List<Playlist>>(emptyList()) }
    var dailySongs by remember(ncmAccount) { mutableStateOf<List<Song>>(emptyList()) }
    var areaSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedAreaType by remember { mutableIntStateOf(7) } // 7:华语, 96:欧美, 8:日本, 16:韩国
    var isLoading by remember { mutableStateOf(true) }
    var exploreFailed by remember { mutableStateOf(false) }
    var areaFailed by remember { mutableStateOf(false) }
    var areaLoading by remember { mutableStateOf(true) }
    var areaRevision by remember { mutableIntStateOf(0) }
    var areaFor by remember { mutableIntStateOf(-1) }
    var exploreJob by remember { mutableStateOf<Job?>(null) }

    fun loadExploreData() {
        exploreJob?.cancel()
        exploreJob = scope.launch {
            isLoading = true
            exploreFailed = false
            try { withContext(Dispatchers.IO) {
                @Suppress("UNCHECKED_CAST")
                if (ncmAccount.isReady) {
                    val recPl = NcmApi.recommendPlaylists().valueOrReport { exploreFailed = true }?.ncmList("recommend").orEmpty()
                    val parsedPl = recPl.mapNotNull { item ->
                        val map = item as? Map<String, Any?> ?: return@mapNotNull null
                        Playlist(
                            id = map.ncmLong("id").toString(),
                            name = map.ncmString("name"),
                            coverUrl = map.ncmString("picUrl"),
                            playCount = map.ncmLong("playcount"),
                            trackCount = map.ncmLong("trackCount").toInt(),
                            source = "wy"
                        )
                    }
                    if (parsedPl.isNotEmpty()) playlists = parsedPl

                    val recSongs = NcmApi.recommendSongs().valueOrReport { exploreFailed = true }?.ncmObj("data")?.ncmList("dailySongs").orEmpty()
                    val parsedSongs = recSongs.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
                    if (parsedSongs.isNotEmpty()) dailySongs = parsedSongs
                }

                @Suppress("UNCHECKED_CAST")
                if (playlists.isEmpty()) {
                    val perPl = NcmApi.personalizedPlaylists(limit = 14).valueOrReport { exploreFailed = true }?.ncmList("result").orEmpty()
                    playlists = perPl.mapNotNull { item ->
                        val map = item as? Map<String, Any?> ?: return@mapNotNull null
                        Playlist(
                            id = map.ncmLong("id").toString(),
                            name = map.ncmString("name"),
                            coverUrl = map.ncmString("picUrl"),
                            playCount = map.ncmLong("playCount"),
                            trackCount = map.ncmLong("trackCount").toInt(),
                            source = "wy"
                        )
                    }
                }

                @Suppress("UNCHECKED_CAST")
                if (dailySongs.isEmpty()) {
                    val newSongs = NcmApi.full.personalizedNewsong(limit = 12).valueOrReport { exploreFailed = true }?.ncmList("result").orEmpty()
                    dailySongs = newSongs.mapNotNull { item ->
                        val map = item as? Map<String, Any?> ?: return@mapNotNull null
                        val songObj = map.ncmObj("song")
                        if (songObj.isNotEmpty()) songObj.toSong().copy(source = "wy") else null
                    }
                }
            }
            } finally { if (currentCoroutineContext().isActive) isLoading = false }
        }
    }

    LaunchedEffect(ncmAccount) {
        loadExploreData()
    }

    LaunchedEffect(selectedAreaType, areaRevision) {
        areaLoading = true
        areaFailed = false
        if (areaFor != selectedAreaType) areaSongs = emptyList()
        areaFor = selectedAreaType
        val result = NcmApi.full.topSong(selectedAreaType)
        ensureActive()
        result.onSuccess { payload ->
            @Suppress("UNCHECKED_CAST")
            val parsed = payload.ncmList("data").take(18).mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
            areaSongs = parsed
        }.onFailure { if (it is CancellationException) throw it; areaFailed = true }
        areaLoading = false
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (isLoading || exploreFailed) {
            item("load-state") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvStatusText(
                        stringResource(if (isLoading) R.string.tv_loading else if (dailySongs.isEmpty() && playlists.isEmpty())
                            R.string.tv_load_failed else R.string.tv_refresh_failed),
                        error = exploreFailed && !isLoading
                    )
                    if (exploreFailed && !isLoading) TvTextAction(stringResource(R.string.tv_retry)) { loadExploreData() }
                }
            }
        }
        item("daily-feature") {
            ExploreHeroCard(
                title = if (ncmAccount.isReady) "每日专属推荐" else "热门新歌精选",
                subtitle = if (dailySongs.isEmpty()) stringResource(if (isLoading) R.string.tv_loading else R.string.tv_empty_result) else "共 ${dailySongs.size} 首曲目",
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (dailySongs.isEmpty()) {
                        if (!isLoading) loadExploreData()
                    } else {
                        PlayerController.playSong(dailySongs.first(), dailySongs)
                        onOpenPlayer()
                    }
                }
            )
        }
        item("radio-actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvTextAction("私人 FM") {
                    PlayerController.startPersonalFm { ok ->
                        if (ok) onOpenPlayer() else Toast.makeText(context, "暂时无法加载私人 FM", Toast.LENGTH_SHORT).show()
                    }
                }
                TvTextAction("心动模式") {
                    PlayerController.startHeartMode { ok ->
                        if (ok) onOpenPlayer() else Toast.makeText(context, "暂时无法加载心动模式", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 2. 甄选推荐歌单横向画廊
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (ncmAccount.isReady) "为你甄选的专属歌单" else "热门精选歌单",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "按左右键浏览 · 确认键查看详情",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isLoading && !exploreFailed && playlists.isEmpty()) {
                    TvStatusText(stringResource(R.string.tv_empty_result))
                }
                if (playlists.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            Column(
                                modifier = Modifier
                                    .width(156.dp)
                                    .tvFocusableCard(
                                        scaleFocused = 1.05f,
                                        shape = RoundedCornerShape(6.dp),
                                        onClick = { onOpenPlaylist(pl) }
                                    )
                                    .padding(10.dp)
                            ) {
                                TvArtwork(pl.coverUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 多地区新歌速递 (华语 / 欧美 / 日本 / 韩国)
        item {
            val areaTabs = listOf(
                7 to "华语热歌",
                96 to "欧美流行",
                8 to "日本新声",
                16 to "韩国K-Pop"
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tv_discover_new_songs), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(areaTabs, key = { it.first }) { (type, label) ->
                        TvTextAction(label, selected = selectedAreaType == type) {
                            if (selectedAreaType != type) {
                                selectedAreaType = type
                                areaSongs = emptyList()
                                areaLoading = true
                                areaFailed = false
                            }
                        }
                    }
                }
                when {
                    areaLoading -> TvStatusText(stringResource(R.string.tv_loading))
                    areaFailed -> {
                        TvStatusText(stringResource(if (areaSongs.isEmpty()) R.string.tv_load_failed else R.string.tv_refresh_failed), error = true)
                        TvTextAction(stringResource(R.string.tv_retry)) { areaRevision++ }
                    }
                    areaSongs.isEmpty() -> TvStatusText(stringResource(R.string.tv_empty_result))
                }
                if (areaSongs.isNotEmpty()) {
                    TvTextAction(stringResource(R.string.tv_play_all)) { PlayerController.playSong(areaSongs.first(), areaSongs) }
                }

                val columns = if (rememberTvWindowSpec().compact) 1 else 2
                val rows = areaSongs.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEachIndexed { rowIndex, rowSongs -> key(rowIndex) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            rowSongs.forEachIndexed { column, song ->
                                // Keyed so every card gets its own tvFocusTarget id (unkeyed loop
                                // iterations share a compound key hash, breaking focus restore).
                                key(song.source, song.id, column) {
                                    TvCompactSongCard(
                                        song = song,
                                        isCurrent = currentSong?.key == song.key,
                                        rank = rowIndex * columns + column + 1,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            PlayerController.playSong(song, areaSongs)
                                        }
                                    )
                                }
                            }
                            repeat(columns - rowSongs.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun ExploreHeroCard(title: String, subtitle: String, icon: ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.tvFocusableCard(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun <T> Result<T>.valueOrReport(failure: () -> Unit): T? {
    currentCoroutineContext().ensureActive()
    exceptionOrNull()?.let { if (it is CancellationException) throw it; failure() }
    return getOrNull()
}

@Composable
fun TvCompactSongCard(
    song: Song,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    focusKey: String = "song/${song.key.sourceId}/${song.key.trackId}",
    isCurrent: Boolean = false,
    onClick: () -> Unit
) {
    val active = isCurrent
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.tvFocusableCard(
            shape = RoundedCornerShape(4.dp),
            status = if (active) stringResource(R.string.tv_current_track) else null,
            unfocusedBackgroundColor = if (active) scheme.surfaceVariant else Color.Transparent,
            focusKey = focusKey,
            onClick = onClick
        ).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rank?.let {
            Text(it.toString(), Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
        }
        TvArtwork(song.coverUrl, Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(song.artist, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SongPlatformBadge(song.source, modifier = Modifier.widthIn(max = 96.dp))
            }
        }
        if (active) {
            Icon(Icons.Rounded.Equalizer, contentDescription = stringResource(R.string.tv_current_track),
                tint = scheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvToplistScreen(
    modifier: Modifier = Modifier,
    fetchBoardSongs: suspend (ToplistCatalog.Entry, Boolean) -> Result<List<Song>> = { entry, forceRefresh ->
        ToplistRepository.fetchBoardSongs(entry, forceRefresh = forceRefresh)
    }
) {
    var platform by rememberSaveable { mutableStateOf(ToplistCatalog.Platform.WY) }
    val boards = remember(platform) { ToplistCatalog.boardsFor(platform) }
    var boardId by rememberSaveable { mutableStateOf("") }
    val board = boards.firstOrNull { it.id == boardId } ?: boards.first()
    var compactDetail by rememberSaveable { mutableStateOf(false) }
    var restoreBoard by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var requestedBoard by remember { mutableStateOf<String?>(null) }
    val currentSong by PlayerController.currentSong.observeAsState()
    val boardList = rememberLazyListState()
    // Requesters of the board items that are composed right now, keyed by board id. Remembering the
    // last board by id (not by FocusRequester) means a platform switch or a board scrolled out of
    // view can never leave a detached requester behind, which Compose 1.6 throws on when focused.
    val boardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastBoardId by remember { mutableStateOf<String?>(null) }
    val detailEntry = remember { FocusRequester() }

    LaunchedEffect(board, refresh) {
        loading = true
        failed = false
        if (requestedBoard != board.id) songs = emptyList()
        requestedBoard = board.id
        try {
            val result = fetchBoardSongs(board, refresh > 0)
            ensureActive()
            result.onSuccess { songs = it }.onFailure { if (it is CancellationException) throw it; failed = true }
        } finally { loading = false }
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp)) {
        val compact = maxWidth < 648.dp
        BackHandler(enabled = compact && compactDetail) {
            compactDetail = false
            restoreBoard = true
        }
        LaunchedEffect(compact, compactDetail, restoreBoard) {
            if (compact && compactDetail) {
                detailEntry.tryRequestFocus()
            } else if (restoreBoard) {
                boardList.scrollToItem(boards.indexOf(board))
                withFrameNanos { }
                boardRequesters[board.id]?.tryRequestFocus()
                restoreBoard = false
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!compact || !compactDetail) {
                Column(
                    (if (compact) Modifier.fillMaxWidth() else Modifier.width(232.dp))
                        .fillMaxHeight().focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToplistCatalog.Platform.entries, key = { it.name }) { option ->
                            TvTextAction(option.label, selected = option == platform) {
                                platform = option
                                boardId = ""
                                lastBoardId = null
                            }
                        }
                    }
                    LazyColumn(
                        state = boardList,
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            if (!compact && key.action == KeyEvent.ACTION_DOWN && key.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                detailEntry.tryRequestFocus()
                                true
                            } else false
                        },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(boards, key = { it.id }) { entry ->
                            val entryFocus = remember { FocusRequester() }
                            DisposableEffect(entry.id, entryFocus) {
                                boardRequesters[entry.id] = entryFocus
                                onDispose {
                                    if (boardRequesters[entry.id] === entryFocus) boardRequesters.remove(entry.id)
                                }
                            }
                            TvTextAction(
                                entry.name,
                                Modifier.fillMaxWidth().focusRequester(entryFocus)
                                    .onFocusChanged { if (it.isFocused) lastBoardId = entry.id },
                                selected = board.id == entry.id,
                                contentAlignment = Alignment.CenterStart
                            ) {
                                boardId = entry.id
                                if (compact) compactDetail = true
                            }
                        }
                    }
                }
            }
            if (!compact || compactDetail) {
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .focusProperties {
                            exit = { direction ->
                                if (!compact && direction == FocusDirection.Left) {
                                    // Only requesters still in the map are attached; otherwise let the
                                    // default search pick a visible board.
                                    lastBoardId?.let(boardRequesters::get)
                                        ?: boardRequesters[board.id]
                                        ?: FocusRequester.Default
                                } else FocusRequester.Default
                            }
                        }
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(board.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TvTextAction("刷新", Modifier.focusRequester(detailEntry), busy = loading) { refresh++ }
                        if (songs.isNotEmpty()) {
                            TvTextAction("播放全部") { PlayerController.playSong(songs.first(), songs) }
                        }
                    }
                    if (failed) TvStatusText(stringResource(if (songs.isEmpty()) R.string.tv_load_failed else R.string.tv_refresh_failed), error = true)
                    when {
                        loading && songs.isEmpty() -> TvStatusText(stringResource(R.string.tv_loading))
                        songs.isEmpty() && !failed -> TvStatusText(stringResource(R.string.tv_empty_result))
                        songs.isEmpty() -> Unit
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(320.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(songs, key = { index, song -> "${song.source}:${song.id}:$index" }) { index, song ->
                                TvCompactSongCard(song, rank = index + 1, isCurrent = currentSong?.key == song.key) { PlayerController.playSong(song, songs) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun TvSearchScreen(
    onOpenPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSong by PlayerController.currentSong.observeAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val editorFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    var phoneInput by remember { mutableStateOf(false) }
    var receivedPhoneInput by remember { mutableStateOf(false) }
    val resultFocus = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible

    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var platform by rememberSaveable { mutableStateOf(BuiltInSourceSearchApi.MusicPlatform.ALL) }
    var playlists by rememberSaveable { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var editorHadFocus by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    var restoreSearchFocus by remember { mutableStateOf(false) }
    var focusResults by remember { mutableStateOf(false) }
    // Whether the search card / 搜索 button holds focus. Results only steal focus from these, so a
    // user who navigates elsewhere while a request runs keeps their position.
    var searchCardFocused by remember { mutableStateOf(false) }
    var submitFocused by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val requestKey = Triple(submittedQuery, platform, playlists)
    val gridState = key(requestKey) { rememberLazyGridState() }
    var resultsKey by remember { mutableStateOf<Triple<String, BuiltInSourceSearchApi.MusicPlatform, Boolean>?>(null) }
    var hotKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var history by remember { mutableStateOf(SessionManager.getSearchHistory(context)) }

    fun submit(word: String = query) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val refreshingResults = resultsKey == Triple(trimmed, platform, playlists) &&
            (songs.isNotEmpty() || playlistResults.isNotEmpty())
        keyboard?.hide()
        editing = false
        imeWasVisible = false
        query = trimmed
        submittedQuery = trimmed
        SessionManager.addSearchHistory(context, trimmed)
        history = SessionManager.getSearchHistory(context)
        loading = true
        focusResults = !refreshingResults
        // The editor or the tapped suggestion chip leaves composition; park focus on the search
        // card so the remote never has nothing focused while the request runs.
        if (!submitFocused) restoreSearchFocus = true
        revision++
    }

    fun openEditor() {
        focusResults = false
        searchCardFocused = false
        submitFocused = false
        editing = true
    }

    fun closeEditor() {
        keyboard?.hide()
        editing = false
        restoreSearchFocus = true
    }
    BackHandler(enabled = editing) { closeEditor() }
    LaunchedEffect(editing) {
        if (editing) {
            editorFocus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(editing, imeVisible) {
        if (editing && imeVisible) imeWasVisible = true
        if (!imeVisible && imeWasVisible) {
            imeWasVisible = false
            if (editing) {
                editing = false
                restoreSearchFocus = true
            }
        }
    }
    LaunchedEffect(restoreSearchFocus) {
        if (restoreSearchFocus) {
            restoreSearchFocus = false
            searchFocus.tryRequestFocus()
        }
    }

    LaunchedEffect(Unit) {
        val items = NcmApi.full.searchHotDetail().getOrNull()?.ncmList("data").orEmpty()
        hotKeywords = items.mapNotNull { (it as? Map<*, *>)?.get("searchWord") as? String }
            .filter(String::isNotBlank).distinct().take(12)
    }
    LaunchedEffect(requestKey, revision) {
        if (submittedQuery.isBlank()) return@LaunchedEffect
        loading = true
        failed = false
        if (resultsKey != requestKey) {
            songs = emptyList()
            playlistResults = emptyList()
        }
        resultsKey = requestKey
        if (playlists) {
            val response = BuiltInSourceSearchApi.searchPlaylists(submittedQuery, limit = 24)
            ensureActive()
            response.onSuccess { playlistResults = it.distinctBy { item -> item.source to item.id } }
                .onFailure { if (it is CancellationException) throw it }
            songs = emptyList()
            failed = response.isFailure
        } else {
            val response = BuiltInSourceSearchApi.searchSongs(platform.key, submittedQuery, page = 1, limit = 36)
            ensureActive()
            response.onSuccess { songs = it.distinctBy { item -> item.source to item.id } }
                .onFailure { if (it is CancellationException) throw it }
            playlistResults = emptyList()
            failed = response.isFailure
        }
        loading = false
    }

    val hasResults = resultsKey == requestKey && (if (playlists) playlistResults.isNotEmpty() else songs.isNotEmpty())
    val searching = submittedQuery.isNotBlank() && (loading || resultsKey != requestKey)
    val searchFailed = resultsKey == requestKey && failed

    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editing) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    decorationBox = { field ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.tv_search_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            field()
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(editorFocus)
                        .onPreInterceptKeyBeforeSoftKeyboard { event ->
                            val key = event.nativeKeyEvent
                            if (key.keyCode == KeyEvent.KEYCODE_BACK) {
                                if (key.action == KeyEvent.ACTION_UP) closeEditor()
                                true
                            } else false
                        }

                        .onFocusChanged { focus ->
                            if (focus.isFocused) {
                                editorHadFocus = true
                            } else if (editorHadFocus) {
                                editorHadFocus = false
                                editing = false
                                keyboard?.hide()
                            }
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                Row(
                    Modifier.weight(1f).focusRequester(searchFocus)
                        .onFocusChanged { searchCardFocused = it.isFocused }
                        .tvFocusableCard(shape = RoundedCornerShape(6.dp), onClick = { openEditor() })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(query.ifBlank { stringResource(R.string.tv_search_hint) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TvTextAction(
                stringResource(R.string.tv_search_action),
                Modifier.focusRequester(submitFocus).onFocusChanged { submitFocused = it.isFocused }
            ) {
                if (query.isBlank()) openEditor() else submit()
            }
            if (!editing) TvTextAction(stringResource(R.string.tv_phone_input), Modifier.focusRequester(phoneFocus)) {
                receivedPhoneInput = false
                phoneInput = true
            }
        }
        if (phoneInput) {
            TvPhoneInputDialog(
                onClose = {
                    phoneInput = false
                    if (receivedPhoneInput) restoreSearchFocus = true else phoneFocus.tryRequestFocus()
                },
                onInput = { value -> query = value; receivedPhoneInput = true },
                inputReceived = receivedPhoneInput
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(BuiltInSourceSearchApi.MusicPlatform.entries, key = { it.name }) { option ->
                TvTextAction(option.label, selected = !playlists && platform == option) {
                    playlists = false
                    platform = option
                }
            }
            item("playlists") { TvTextAction(stringResource(R.string.tv_search_playlists), selected = playlists) { playlists = true } }
        }
        val suggestions = history.ifEmpty { hotKeywords }
        if (suggestions.isNotEmpty() && !editing && (submittedQuery.isBlank() || query.isBlank())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (history.isEmpty()) "热搜" else "搜索历史", modifier = Modifier.padding(end = 8.dp))
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions.distinct(), key = { it }) { word ->
                        TvTextAction(word, modifier = Modifier.widthIn(max = 200.dp)) { submit(word) }
                    }
                }
                if (history.isNotEmpty()) {
                    TvTextAction("清空历史") {
                        SessionManager.clearSearchHistory(context)
                        history = emptyList()
                        searchFocus.requestFocus()
                    }
                }
            }
        }

        if (hasResults) rememberTvFocusScroller { key ->
            val index = if (playlists) playlistResults.indexOfFirst { "playlist/${it.source}/${it.id}" == key }
            else songs.indexOfFirst { "song/${it.key.sourceId}/${it.key.trackId}" == key }
            if (index >= 0) gridState.scrollToItem(index)
            index >= 0
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                submittedQuery.isBlank() -> TvStatusText(stringResource(R.string.tv_search_idle))
                searching -> TvStatusText(stringResource(R.string.tv_search_loading, submittedQuery))
                searchFailed -> {
                    TvStatusText(stringResource(if (hasResults) R.string.tv_refresh_failed else R.string.tv_search_failed), error = true)
                    TvTextAction(stringResource(R.string.tv_retry)) { submit(submittedQuery) }
                }
                !hasResults -> TvStatusText(stringResource(R.string.tv_search_empty, submittedQuery))
            }
            if (hasResults) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (playlists) 144.dp else 240.dp),
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (playlists) {
                        itemsIndexed(playlistResults, key = { _, item -> "${item.source}:${item.id}" }) { index, item ->
                            val first = if (index == 0) Modifier.focusRequester(resultFocus) else Modifier
                            Column(
                                first.fillMaxWidth().tvFocusableCard(focusKey = "playlist/${item.source}/${item.id}", onClick = { onOpenPlaylist(item) }).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TvArtwork(item.coverUrl, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        itemsIndexed(songs, key = { _, item -> "${item.source}:${item.id}" }) { index, song ->
                            TvCompactSongCard(
                                song,
                                if (index == 0) Modifier.focusRequester(resultFocus) else Modifier,
                                isCurrent = currentSong?.key == song.key
                            ) {
                                PlayerController.playSong(song, songs)
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(focusResults, searching, searchFailed, hasResults, requestKey, revision, editing) {
        if (!focusResults || searching) return@LaunchedEffect
        if (!editing && !searchFailed && hasResults && (searchCardFocused || submitFocused)) {
            withFrameNanos { }
            if (searchCardFocused || submitFocused) resultFocus.tryRequestFocus()
        }
        focusResults = false
    }
}
