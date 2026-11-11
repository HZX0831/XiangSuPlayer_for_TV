package com.cutedino.xiangsuplayer.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.R
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.ui.components.*
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
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
internal fun TvHomeContent(song: Song?, playing: Boolean, history: List<Song>, onResume: () -> Unit,
    onPlaySong: (Song) -> Unit, onNavigate: (TvMainTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tv_home_title), style = MaterialTheme.typography.displayMedium)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val artSize = minOf(maxHeight * 0.82f, maxWidth * 0.37f, 280.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(song?.title ?: stringResource(R.string.tv_home_start), style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (song != null) Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    var focused by remember { mutableStateOf(false) }
                    Row(Modifier.tvPillFocusable(isSelected = true, focusKey = "home/primary", onFocusChanged = { focused = it },
                        onClick = { if (song == null) onNavigate(TvMainTab.SEARCH) else onResume() })
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val foreground = if (focused) scheme.onPrimary else scheme.onSurface
                        Icon(Icons.Rounded.PlayArrow, null, tint = foreground)
                        Text(stringResource(if (song == null) R.string.tv_home_find else if (playing) R.string.tv_home_open_player else R.string.tv_home_resume),
                            color = foreground, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.tv_home_recent), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                    val recent = history.filter { it.key != song?.key }.take(2)
                    if (recent.isEmpty()) Text(stringResource(R.string.tv_home_no_recent), style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                    recent.forEach { item ->
                        Row(Modifier.fillMaxWidth().tvFocusableCard(focusKey = "home/recent/" + item.key.sourceId + "/" + item.id,
                            onClick = { onPlaySong(item) }).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvArtwork(item.coverUrl, Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.artist, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Column(Modifier.width(artSize), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (song != null) {
                        TvArtwork(song.coverUrl, Modifier.size(artSize))
                        SongPlatformBadge(song.source)
                    } else {
                        Icon(Icons.Rounded.MusicNote, null, Modifier.size(88.dp), tint = scheme.primary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvTextAction(stringResource(R.string.tv_home_discover), Modifier.weight(1f)) { onNavigate(TvMainTab.DISCOVER) }
            TvTextAction(stringResource(R.string.tv_home_history), Modifier.weight(1f)) { onNavigate(TvMainTab.HISTORY) }
        }
    }
}
