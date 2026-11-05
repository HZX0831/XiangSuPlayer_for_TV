package com.cutedino.xiangsuplayer.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.components.TvFloatingMiniPlayer
import com.cutedino.xiangsuplayer.ui.components.TvImmersiveBackdrop
import com.cutedino.xiangsuplayer.ui.components.TvMainTab
import com.cutedino.xiangsuplayer.ui.components.TvTopPillNavigationBar
import com.cutedino.xiangsuplayer.ui.library.PlaylistDetailActivity
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayRed
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurface
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val currentSong by PlayerController.currentSong.observeAsState()
            PixelPlayTvTheme(coverUrl = currentSong?.coverUrl) {
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

    fun loadWallpaper() {}
    fun updateWallpaperOverlayOpacity(percent: Int = SessionManager.wallpaperOpacityPercent) {}
}

@Composable
private fun TvMainAppScreen(
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onExitApp: () -> Unit
) {
    val currentSong by PlayerController.currentSong.observeAsState()
    val isPlaying by PlayerController.isPlaying.observeAsState(false)
    val playMode by PlayerController.playMode.observeAsState(PlayMode.SEQUENCE)
    val pos by PlayerController.currentPosition.collectAsState()
    val dur by PlayerController.duration.collectAsState()
    val streamResult by PlayerController.activeStreamResult.collectAsState()

    var selectedTab by remember { mutableStateOf(TvMainTab.EXPLORE) }
    var showAccountModal by remember { mutableStateOf(false) }
    var showExitModal by remember { mutableStateOf(false) }
    var accountRefreshTick by remember { mutableIntStateOf(0) }
    var themeRefreshTick by remember { mutableIntStateOf(0) }

    val accountSummary = remember(accountRefreshTick) {
        MultiPlatformAccountManager.getAccountSummaryText()
    }

    val useWallpaper = remember(themeRefreshTick) { SessionManager.useWallpaperBackground }
    val wallpaperUrl = remember(themeRefreshTick) { SessionManager.customWallpaperUrl }
    val wallpaperOpacity = remember(themeRefreshTick) { SessionManager.wallpaperOpacityPercent }

    BackHandler {
        if (selectedTab != TvMainTab.EXPLORE) {
            selectedTab = TvMainTab.EXPLORE
        } else {
            showExitModal = true
        }
    }

    val progress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 全局沉浸式动态流体背景 (或必应/自定义壁纸)
        TvImmersiveBackdrop(
            coverUrl = currentSong?.coverUrl,
            useWallpaper = useWallpaper,
            wallpaperUrl = wallpaperUrl,
            wallpaperOpacityPercent = wallpaperOpacity
        )

        // 2. 主界面纵向布局：顶部胶囊导航栏 + 中部内容视图 + 底部悬浮胶囊 MiniPlayer
        Column(modifier = Modifier.fillMaxSize()) {
            TvTopPillNavigationBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onOpenPlayer = onOpenPlayer,
                onOpenAccounts = { showAccountModal = true },
                accountSummary = accountSummary
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(durationMillis = 220),
                    label = "mainTabCrossfade"
                ) { tab ->
                    when (tab) {
                        TvMainTab.EXPLORE -> TvExploreScreen(
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenPlayer = onOpenPlayer
                        )
                        TvMainTab.TOPLIST -> TvToplistScreen()
                        TvMainTab.SEARCH -> TvSearchScreen(
                            onOpenPlaylist = onOpenPlaylist
                        )
                        TvMainTab.LIBRARY -> TvLibraryScreen(
                            onOpenPlaylist = onOpenPlaylist,
                            onOpenAccountModal = { showAccountModal = true }
                        )
                        TvMainTab.SOURCE_MARKET -> TvSourceMarketScreen()
                        TvMainTab.HISTORY -> TvHistoryScreen()
                        TvMainTab.SETTINGS -> TvSettingsScreen(
                            onThemeModeChanged = { themeRefreshTick++ }
                        )
                    }
                }
            }

            TvFloatingMiniPlayer(
                currentSong = currentSong,
                isPlaying = isPlaying,
                playMode = playMode,
                progress = progress,
                streamResult = streamResult,
                onTogglePlayPause = { PlayerController.togglePlayPause() },
                onPlayNext = { PlayerController.playNext() },
                onPlayPrev = { PlayerController.playPrevious() },
                onTogglePlayMode = { PlayerController.togglePlayMode() },
                onOpenFullPlayer = onOpenPlayer
            )
        }

        // 3. 多平台账号中心弹窗
        if (showAccountModal) {
            TvMultiAccountDialog(
                onAccountChanged = { accountRefreshTick++ },
                onDismiss = { showAccountModal = false }
            )
        }

        // 4. 退出确认弹窗
        if (showExitModal) {
            Dialog(onDismissRequest = { showExitModal = false }) {
                Surface(
                    modifier = Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    color = PixelPlaySurface,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(26.dp)) {
                        Text(
                            text = "退出像素播放器 TV",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "确定要退出像素播放器吗？",
                            color = PixelPlayLightPurple,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            var cancelFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .tvPillFocusable(
                                        isSelected = true,
                                        onFocusChanged = { cancelFocused = it },
                                        onClick = { showExitModal = false }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "继续听歌",
                                    color = if (cancelFocused) PixelPlayPurpleDark else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            var exitFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .tvPillFocusable(
                                        onFocusChanged = { exitFocused = it },
                                        onClick = {
                                            showExitModal = false
                                            onExitApp()
                                        }
                                    )
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "确定退出",
                                    color = if (exitFocused) PixelPlayPurpleDark else PixelPlayRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
