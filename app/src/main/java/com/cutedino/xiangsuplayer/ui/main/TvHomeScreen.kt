package com.cutedino.xiangsuplayer.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.ui.components.SongPlatformBadge
import com.cutedino.xiangsuplayer.ui.components.TvArtwork
import com.cutedino.xiangsuplayer.ui.components.TvMainTab
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.theme.rememberTvFocusScroller
import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable

@Composable
internal fun TvHomeScreen(onOpenPlayer: () -> Unit, onNavigate: (TvMainTab) -> Unit) {
    val song by PlayerController.currentSong.observeAsState()
    val playing by PlayerController.isPlaying.observeAsState(false)
    val history by PlayerController.playHistory.observeAsState(emptyList())
    TvHomeContent(song, playing, history,
        onResume = { if (!playing) PlayerController.play(); onOpenPlayer() },
        onPlaySong = { PlayerController.playSong(it, history); onOpenPlayer() }, onNavigate = onNavigate)
}

@Composable
internal fun TvHomeContent(
    song: Song?,
    playing: Boolean,
    history: List<Song>,
    onResume: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onNavigate: (TvMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = rememberTvWindowSpec().compact
    val recent = remember(history, song?.key) {
        history.asSequence().filter { it.key != song?.key }.distinctBy { it.key }.take(3).toList()
    }
    val listState = rememberLazyListState()
    rememberTvFocusScroller { key ->
        val index = when (key) {
            "home/primary", "home/discover" -> 0
            "home/history" -> 2 + recent.size
            else -> recent.indexOfFirst { it.homeFocusKey() == key }.takeIf { it >= 0 }?.plus(2) ?: -1
        }
        if (index >= 0) listState.scrollToItem(index)
        index >= 0
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        item("home-stage") {
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("home-stage")) {
                val coverSize = minOf(maxWidth * (if (compact) 0.26f else 0.36f), if (compact) 128.dp else 224.dp)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            song?.title ?: stringResource(R.string.tv_home_start),
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (song != null) {
                            Text(song.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            Modifier.tvPillFocusable(isPrimary = true, focusKey = "home/primary", onFocusChanged = { focused = it }) {
                                if (song == null) onNavigate(TvMainTab.SEARCH) else onResume()
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val foreground = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Icon(if (song == null) Icons.Rounded.Search else Icons.Rounded.PlayArrow, null, tint = foreground)
                            Text(
                                stringResource(when {
                                    song == null -> R.string.tv_home_find
                                    playing -> R.string.tv_home_open_player
                                    else -> R.string.tv_home_resume
                                }),
                                color = foreground,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        TvTextAction(stringResource(R.string.tv_home_discover), focusKey = "home/discover") {
                            onNavigate(TvMainTab.DISCOVER)
                        }
                    }
                    if (song != null) {
                        Column(Modifier.width(coverSize), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvArtwork(song.coverUrl, Modifier.size(coverSize))
                            SongPlatformBadge(song.source, modifier = Modifier.testTag("home-cover-source"))
                        }
                    }
                }
            }
        }
        item("home/recent-title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.tv_home_recent), style = MaterialTheme.typography.titleSmall)
                if (recent.isEmpty()) {
                    Text(stringResource(R.string.tv_home_no_recent), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(recent, key = { it.homeFocusKey() }) { item ->
            TvCompactSongCard(item, Modifier.fillMaxWidth(), focusKey = item.homeFocusKey()) { onPlaySong(item) }
        }
        item("home/history") {
            TvTextAction(stringResource(R.string.tv_home_history), focusKey = "home/history") {
                onNavigate(TvMainTab.HISTORY)
            }
        }
    }
}

private fun Song.homeFocusKey() = "home/recent/" + key.sourceId + "/" + key.trackId
