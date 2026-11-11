package com.cutedino.xiangsuplayer.ui.main

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory
import com.cutedino.xiangsuplayer.ui.theme.TvFocusMemory
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvFocusGroup
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.components.TvMiniPlayer
import com.cutedino.xiangsuplayer.ui.components.TvImmersiveBackdrop
import com.cutedino.xiangsuplayer.ui.components.TvMainTab
import com.cutedino.xiangsuplayer.ui.components.TvNavigationRail
import com.cutedino.xiangsuplayer.ui.library.PlaylistDetailActivity
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelPlayTvTheme {
                TvMainAppScreen(
                    onOpenPlayer = {
                        if (PlayerController.currentSong.value != null) {
                            startActivity(Intent(this, PlayerActivity::class.java))
                        } else {
                            Toast.makeText(this, "当前暂无正在播放的歌曲，先点首歌吧", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenPlaylist = { playlist ->
                        val intent = Intent(this, PlaylistDetailActivity::class.java).apply {
                            putExtra("EXTRA_PLAYLIST_ID", playlist.id)
                            putExtra("EXTRA_PLAYLIST_NAME", playlist.name)
                            putExtra("EXTRA_PLAYLIST_COVER", playlist.coverUrl)
                            putExtra("EXTRA_PLAYLIST_SOURCE", playlist.source)
                        }
                        startActivity(intent)
                    },
                    onExitApp = { finish() }
                )
            }
        }
    }

}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvMainAppScreen(
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onExitApp: () -> Unit
) {
    val error by PlayerController.playbackError.collectAsState()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableStateOf(TvMainTab.EXPLORE) }
    var showAccountModal by rememberSaveable { mutableStateOf(false) }
    var accountRefreshTick by remember { mutableIntStateOf(0) }
    var themeRefreshTick by remember { mutableIntStateOf(0) }
    var navigationFocusRequest by remember { mutableIntStateOf(0) }
    var focusPageOnEntry by remember { mutableStateOf(false) }
    var childParent by rememberSaveable { mutableStateOf(TvMainTab.MORE) }

    val selectedNavigation = remember { FocusRequester() }
    val navigation = remember { TvFocusMemory() }
    val mini = remember { TvFocusMemory() }
    val focusScope = rememberCoroutineScope()
    val pages = remember { TvMainTab.entries.associateWith { TvFocusMemory() } }
    val page = pages.getValue(selectedTab)
    val pageStates = rememberSaveableStateHolder()
    val accountSummary = remember(accountRefreshTick) { MultiPlatformAccountManager.getAccountSummaryText() }
    val useWallpaper = remember(themeRefreshTick) { SessionManager.useWallpaperBackground }
    val wallpaperUrl = remember(themeRefreshTick) { SessionManager.customWallpaperUrl }
    val wallpaperOpacity = remember(themeRefreshTick) { SessionManager.wallpaperOpacityPercent }
    val moreChildren = setOf(TvMainTab.HISTORY, TvMainTab.SOURCE_MARKET, TvMainTab.SETTINGS)

    LaunchedEffect(navigationFocusRequest) {
        if (!showAccountModal) selectedNavigation.tryRequestFocus()
    }
    LaunchedEffect(selectedTab) {
        if (focusPageOnEntry) {
            focusPageOnEntry = false
            if (!page.restore()) selectedNavigation.tryRequestFocus()
        }
    }
    LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    BackHandler {
        when {
            selectedTab == TvMainTab.DISCOVER -> {
                focusPageOnEntry = true
                selectedTab = TvMainTab.EXPLORE
            }
            selectedTab in moreChildren -> {
                focusPageOnEntry = true
                selectedTab = childParent
            }
            !navigation.hasFocus -> navigationFocusRequest++
            else -> onExitApp()
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvImmersiveBackdrop(null, useWallpaper, wallpaperUrl, wallpaperOpacity)
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CompositionLocalProvider(LocalTvFocusMemory provides navigation) {
                    TvNavigationRail(selectedTab, { selectedTab = it }, selectedNavigation,
                        Modifier.tvFocusGroup { direction -> if (direction == FocusDirection.Right) page.preferred() else null })
                }
                Box(Modifier.weight(1f).fillMaxHeight().tvFocusGroup { direction -> when (direction) {
                    FocusDirection.Left -> selectedNavigation
                    FocusDirection.Down -> mini.preferred() ?: FocusRequester.Cancel
                    else -> null
                } }) {
                    pageStates.SaveableStateProvider(selectedTab.name) {
                        CompositionLocalProvider(LocalTvFocusMemory provides page) {
                            when (selectedTab) {
                                TvMainTab.EXPLORE -> TvHomeScreen(onOpenPlayer = onOpenPlayer, onNavigate = {
                                    childParent = selectedTab; focusPageOnEntry = true; selectedTab = it
                                })
                                TvMainTab.DISCOVER -> TvExploreScreen(onOpenPlaylist, onOpenPlayer)
                                TvMainTab.TOPLIST -> TvToplistScreen()
                                TvMainTab.SEARCH -> TvSearchScreen(onOpenPlaylist = onOpenPlaylist)
                                TvMainTab.LIBRARY -> TvLibraryScreen(onOpenPlaylist, { showAccountModal = true }, accountRevision = accountRefreshTick)
                                TvMainTab.MORE -> TvMoreScreen(accountSummary, { showAccountModal = true }, {
                                    childParent = selectedTab; focusPageOnEntry = true; selectedTab = it
                                })
                                TvMainTab.SOURCE_MARKET -> TvSourceMarketScreen()
                                TvMainTab.HISTORY -> TvHistoryScreen(onNavigate = { navigationFocusRequest++ })
                                TvMainTab.SETTINGS -> TvSettingsScreen(onThemeModeChanged = { themeRefreshTick++ })
                            }
                        }
                    }
                }
            }
            CompositionLocalProvider(LocalTvFocusMemory provides mini) {
                TvConnectedMiniPlayer(onOpenPlayer,
                        Modifier.onPreviewKeyEvent { event ->
                            val key = event.nativeKeyEvent
                            if (key.action == android.view.KeyEvent.ACTION_DOWN && key.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                if (key.repeatCount == 0) focusScope.launch { if (!page.restore()) selectedNavigation.tryRequestFocus() }
                                true
                            } else false
                        })
            }
        }
    }

    if (showAccountModal) {
        TvMultiAccountDialog(
            onAccountChanged = { accountRefreshTick++ },
            onDismiss = {
                showAccountModal = false
                focusScope.launch { if (!page.restore()) navigationFocusRequest++ }
            }
        )
    }

}

@Composable
private fun TvMoreScreen(
    accountSummary: String,
    onOpenAccounts: () -> Unit,
    onOpenTab: (TvMainTab) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item("karaoke") {
            TvMoreRow(stringResource(R.string.tv_karaoke), Icons.Rounded.PlayArrow, {
                context.startActivity(Intent(context, com.cutedino.xiangsuplayer.ui.player.KaraokeActivity::class.java))
            })
        }
        item("accounts") { TvMoreRow("账号", Icons.Rounded.AccountCircle, onOpenAccounts, accountSummary) }
        items(listOf(TvMainTab.HISTORY, TvMainTab.SOURCE_MARKET, TvMainTab.SETTINGS), key = { it.name }) { tab ->
            TvMoreRow(tab.title, tab.icon, { onOpenTab(tab) })
        }
    }
}

@Composable
private fun TvMoreRow(title: String, icon: ImageVector, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().tvFocusableCard(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The progress clock is collected here so browsing pages do not recompose on every tick. */
@Composable
private fun TvConnectedMiniPlayer(onOpenPlayer: () -> Unit, modifier: Modifier = Modifier) {
    val song by PlayerController.currentSong.observeAsState()
    val playing by PlayerController.isPlaying.observeAsState(false)
    val mode by PlayerController.playMode.observeAsState(PlayMode.SEQUENCE)
    val position by PlayerController.currentPosition.collectAsState()
    val duration by PlayerController.duration.collectAsState()
    val stream by PlayerController.activeStreamResult.collectAsState()
    val failure by PlayerController.playbackError.collectAsState()
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    PixelPlayTvTheme(coverUrl = song?.coverUrl) {
        TvMiniPlayer(song, playing, mode, progress, stream, PlayerController::togglePlayPause,
            PlayerController::playNext, PlayerController::playPrevious, PlayerController::togglePlayMode,
            onOpenPlayer, modifier, failure)
    }
}
