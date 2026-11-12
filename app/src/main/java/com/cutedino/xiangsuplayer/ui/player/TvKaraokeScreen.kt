package com.cutedino.xiangsuplayer.ui.player

import com.cutedino.xiangsuplayer.ui.components.TvReadingText

import com.cutedino.xiangsuplayer.ui.theme.rememberTvWindowSpec
import com.cutedino.xiangsuplayer.ui.components.TvDialogPanel
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.phone.PhoneChoice
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import com.cutedino.xiangsuplayer.ui.components.TvPhoneInputDialog
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.components.TvStatusText
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme

class KaraokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PixelPlayTvTheme { TvKaraokeScreen { finish() } } }
    }
}

@Composable
internal fun TvKaraokeScreen(onClose: () -> Unit) {
    var entered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (!entered) {
        val cancel = remember { FocusRequester() }
        com.cutedino.xiangsuplayer.ui.components.TvDialogPanel(stringResource(R.string.tv_karaoke), onClose,
            footer = {
                TvTextAction(stringResource(R.string.tv_close), Modifier.focusRequester(cancel), onClick = onClose)
                TvTextAction(stringResource(R.string.tv_point_enter)) { entered = true }
            }) { Text(stringResource(R.string.tv_point_entry_note)) }
        LaunchedEffect(Unit) { cancel.tryRequestFocus() }
        return
    }
    DisposableEffect(Unit) {
        PlayerController.beginKaraoke()
        onDispose { PlayerController.endKaraoke() }
    }
    val playback by PlayerController.uiState.collectAsState()
    val lyrics by PlayerController.lyrics.observeAsState(emptyList())
    val active by PlayerController.activeLyricIndex.observeAsState(-1)
    val phoneFocus = remember { FocusRequester() }
    var pair by remember { mutableStateOf(false) }
    var readLyrics by remember { mutableStateOf(false) }
    val spec = rememberTvWindowSpec()
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(pair, removing) { if (!pair) phoneFocus.tryRequestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.tv_karaoke), style = MaterialTheme.typography.titleLarge)
            Text(playback.song?.title ?: "请用手机扫码点歌", maxLines = 1, overflow = TextOverflow.Ellipsis)
            val current = lyrics.getOrNull(active)
            val next = lyrics.getOrNull(active + 1)
            val accent = MaterialTheme.colorScheme.primary
            val normal = MaterialTheme.colorScheme.onSurface
            val text = buildAnnotatedString {
                if (current == null || current.words.isEmpty()) {
                    append(current?.text ?: if (next == null) "暂无歌词" else "前奏")
                } else current.words.forEach { word ->
                    withStyle(SpanStyle(color = if (playback.positionMs >= word.startMs) accent else normal)) {
                        append(word.text)
                    }
                }
            }
            Column(Modifier.weight(0.55f).fillMaxWidth().tvFocusableCard(onClick = { readLyrics = true })
                .verticalScroll(rememberScrollState())) {
            Text(
                text, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center, color = if (current?.words.isNullOrEmpty()) accent else normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                next?.text ?: " ", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                item { TvTextAction(stringResource(R.string.tv_phone_order), Modifier.focusRequester(phoneFocus)) { pair = true } }
                if (playback.song != null) {
                    item { TvTextAction(
                        if (playback.error != null) "重试" else if (playback.playing) "暂停" else "播放",
                        onClick = PlayerController::togglePlayPause
                    ) }
                    item { TvTextAction("跳过", onClick = PlayerController::playNext) }
                }
                item { TvTextAction(stringResource(R.string.tv_karaoke_exit), onClick = onClose) }
            }
            playback.error?.let { TvStatusText(it, error = true) }
            LazyColumn(Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (playback.queue.isEmpty()) item { TvStatusText(stringResource(R.string.tv_karaoke_queue_empty)) }
                items(playback.queue, key = { it.id }) { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val prefix = if (entry.id == playback.currentEntryId) "当前 · " else ""
                        Text(prefix + entry.song.title + " · " + entry.song.artist, Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TvTextAction(stringResource(R.string.tv_remove)) {
                            removing = !removing
                            PlayerController.removeQueueEntry(entry.id)
                        }
                    }
                }
            }
        }
    }
    if (readLyrics) TvDialogPanel(stringResource(R.string.tv_karaoke), { readLyrics = false }, footer = {
        TvTextAction(stringResource(R.string.tv_close)) { readLyrics = false }
    }) {
        TvReadingText(lyrics.drop(active.coerceAtLeast(0)).joinToString("\n\n") { it.text })
    }
    if (pair) TvPhoneInputDialog(
        onClose = { pair = false },
        search = { query ->
            BuiltInSourceSearchApi.searchSongs("all", query, page = 1, limit = 18)
                .getOrThrow().distinctBy { it.key }.map { PhoneChoice("${it.title} · ${it.artist}", it) }
        },
        order = { PlayerController.enqueueKaraoke(it.song) }
    )
}
