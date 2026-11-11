package com.cutedino.xiangsuplayer.ui.library

import com.cutedino.xiangsuplayer.ui.main.TvConnectedMiniPlayer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.main.TvCompactSongCard
import com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory
import com.cutedino.xiangsuplayer.ui.theme.TvFocusMemory
import com.cutedino.xiangsuplayer.ui.theme.tvFocusGroup
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.toplist.ToplistCatalog
import com.cutedino.xiangsuplayer.core.toplist.ToplistRepository
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.ui.components.SongPlatformBadge
import com.cutedino.xiangsuplayer.ui.components.TvMiniPlayer
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj

class PlaylistDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playlistId = intent.getStringExtra("EXTRA_PLAYLIST_ID") ?: ""
        val playlistName = intent.getStringExtra("EXTRA_PLAYLIST_NAME") ?: "歌单详情"
        val playlistCover = intent.getStringExtra("EXTRA_PLAYLIST_COVER") ?: ""
        val playlistSource = intent.getStringExtra("EXTRA_PLAYLIST_SOURCE") ?: "wy"

        setContent {
            PixelPlayTvTheme {
                TvPlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    playlistCover = playlistCover,
                    playlistSource = playlistSource,
                    onBack = { finish() },
                    onOpenPlayer = {
                        startActivity(Intent(this, PlayerActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun TvPlaylistDetailScreen(
    playlistId: String,
    playlistName: String,
    playlistCover: String,
    playlistSource: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val currentSong by PlayerController.currentSong.observeAsState()

    var songs by remember(playlistId) { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var total by remember(playlistId) { mutableStateOf(0) }

    LaunchedEffect(playlistId, retry) {
        isLoading = true
        error = null
        try {
            when {
                playlistId.startsWith("tx_") -> {
                    songs = MultiPlatformAccountManager.fetchQqPlaylistSongs(playlistId)
                    total = songs.size
                }
                playlistId.startsWith("kg_board_") -> {
                    val bangid = playlistId.removePrefix("kg_board_")
                    val entry = ToplistCatalog.Entry(playlistId, playlistName, bangid, "kg")
                    songs = ToplistRepository.fetchBoardSongs(entry).getOrThrow()
                    total = songs.size
                }
                else -> {
                    val detail = NcmApi.playlistDetail(playlistId.removePrefix("wy_")).getOrThrow()
                    val playlist = detail.ncmObj("playlist")
                    check(playlist.isNotEmpty()) { "未收到歌单数据" }
                    val known = (songs + parsePlaylistSongs(playlist.ncmList("tracks"))).associateBy { it.id }.toMutableMap()
                    parsePlaylistSongs(detail.ncmObj("_songs_detail").ncmList("songs")).forEach { known[it.id] = it }
                    @Suppress("UNCHECKED_CAST")
                    val ids = playlist.ncmList("trackIds").mapNotNull {
                        (it as? Map<String, Any?>)?.ncmLong("id")?.takeIf { id -> id > 0 }?.toString()
                    }.ifEmpty { known.keys.toList() }
                    total = maxOf(ids.size, playlist.ncmLong("trackCount").toInt())
                    songs = ids.mapNotNull(known::get)
                    for (page in ids.filterNot(known::containsKey).distinct().chunked(100)) {
                        parsePlaylistSongs(NcmApi.songDetail(page).getOrThrow().ncmList("songs"))
                            .forEach { known[it.id] = it }
                        songs = ids.mapNotNull(known::get)
                    }
                    if (songs.size < total) error = "部分曲目不可用，可重试"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "歌单加载失败，请重试"
        } finally {
            isLoading = false
        }
    }

    val spec = rememberTvWindowSpec()
    val header = remember { TvFocusMemory() }
    val tracks = remember { TvFocusMemory() }
    val mini = remember { TvFocusMemory() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompositionLocalProvider(LocalTvFocusMemory provides header) {
            Column(
                Modifier.fillMaxWidth().tvFocusGroup { direction ->
                    if (direction == FocusDirection.Down) tracks.preferred() ?: mini.preferred() else null
                },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TvTextAction("返回", Modifier.focusRequester(backFocus), onClick = onBack)
                    if (playlistCover.isNotBlank()) {
                        AsyncImage(
                            playlistCover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(playlistName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("已加载 ${songs.size} / $total 首", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SongPlatformBadge(source = playlistSource)
                    if (error != null && songs.isNotEmpty()) TvTextAction(stringResource(R.string.tv_playlist_retry_missing)) { retry++ }
                }
                TvPlaylistBatchActions(songs, total, onPlay = { snapshot -> PlayerController.playSong(snapshot.first(), snapshot) })

            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().tvFocusGroup { direction ->
                when (direction) {
                    FocusDirection.Up -> header.preferred()
                    FocusDirection.Down -> mini.preferred()
                    else -> null
                }
            },
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalTvFocusMemory provides tracks) {
                when {
                    isLoading && songs.isEmpty() -> CircularProgressIndicator()
                    error != null && songs.isEmpty() -> TvTextAction(error.orEmpty()) { retry++ }
                    songs.isEmpty() -> Text("歌单暂无歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(320.dp),
                        modifier = Modifier.fillMaxSize(),
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

        CompositionLocalProvider(LocalTvFocusMemory provides mini) {
            TvConnectedMiniPlayer(onOpenPlayer, Modifier.tvFocusGroup { direction ->
                if (direction == FocusDirection.Up) tracks.preferred() ?: header.preferred() else null
            })
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvPlaylistBatchActions(songs: List<Song>, total: Int, onPlay: (List<Song>) -> Unit) {
    if (songs.isEmpty()) return
    val partial = total > songs.size
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TvTextAction(if (partial) stringResource(R.string.tv_play_loaded, songs.size) else stringResource(R.string.tv_play_all)) {
            onPlay(songs.toList())
        }
        TvTextAction(stringResource(if (partial) R.string.tv_shuffle_loaded else R.string.tv_shuffle_all)) {
            onPlay(songs.shuffled())
        }
    }
}


@Suppress("UNCHECKED_CAST")
private fun parsePlaylistSongs(raw: List<*>): List<Song> = raw.mapNotNull {
    (it as? Map<String, Any?>)?.toSong()?.takeIf { song -> song.id != "0" }
}
