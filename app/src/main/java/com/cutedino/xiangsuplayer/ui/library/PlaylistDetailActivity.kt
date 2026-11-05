package com.cutedino.xiangsuplayer.ui.library

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayMode
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.toplist.ToplistCatalog
import com.cutedino.xiangsuplayer.core.toplist.ToplistRepository
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.components.SongPlatformBadge
import com.cutedino.xiangsuplayer.ui.components.TvFloatingMiniPlayer
import com.cutedino.xiangsuplayer.ui.components.TvImmersiveBackdrop
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayTvTheme
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmObj

class PlaylistDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playlistId = intent.getStringExtra("EXTRA_PLAYLIST_ID") ?: ""
        val playlistName = intent.getStringExtra("EXTRA_PLAYLIST_NAME") ?: "歌单详情"
        val playlistCover = intent.getStringExtra("EXTRA_PLAYLIST_COVER") ?: ""
        val playlistSource = intent.getStringExtra("EXTRA_PLAYLIST_SOURCE") ?: "wy"

        setContent {
            val currentSong by PlayerController.currentSong.observeAsState()
            PixelPlayTvTheme(coverUrl = currentSong?.coverUrl ?: playlistCover) {
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
    val context = LocalContext.current
    val currentSong by PlayerController.currentSong.observeAsState()
    val isPlaying by PlayerController.isPlaying.observeAsState(false)
    val playMode by PlayerController.playMode.observeAsState(PlayMode.SEQUENCE)
    val pos by PlayerController.currentPosition.collectAsState()
    val dur by PlayerController.duration.collectAsState()
    val streamResult by PlayerController.activeStreamResult.collectAsState()

    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        isLoading = true
        songs = withContext(Dispatchers.IO) {
            when {
                playlistId.startsWith("tx_") -> {
                    MultiPlatformAccountManager.fetchQqPlaylistSongs(playlistId)
                }
                playlistId.startsWith("kg_board_") -> {
                    val bangid = playlistId.removePrefix("kg_board_")
                    val entry = ToplistCatalog.Entry(playlistId, playlistName, bangid, "kg")
                    ToplistRepository.fetchBoardSongs(entry).getOrDefault(emptyList())
                }
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    val trackList = NcmApi.playlistDetail(playlistId.removePrefix("wy_"))
                        .getOrNull()
                        ?.ncmObj("playlist")
                        ?.ncmList("tracks")
                        .orEmpty()
                    trackList.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
                }
            }
        }
        isLoading = false
    }

    val progress = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        TvImmersiveBackdrop(
            coverUrl = currentSong?.coverUrl ?: playlistCover,
            useWallpaper = SessionManager.useWallpaperBackground,
            wallpaperUrl = SessionManager.customWallpaperUrl,
            wallpaperOpacityPercent = SessionManager.wallpaperOpacityPercent
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 20.dp)
        ) {
            // 顶部歌单信息横幅
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    var backFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                onFocusChanged = { backFocused = it },
                                onClick = onBack
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = if (backFocused) PixelPlayPurpleDark else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "返回",
                            color = if (backFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (playlistCover.isNotBlank()) {
                        AsyncImage(
                            model = playlistCover,
                            contentDescription = playlistName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(PixelPlaySurfaceVariant)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = playlistName,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            SongPlatformBadge(source = playlistSource)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "共 ${songs.size} 首曲目 · 按遥控器确认键即刻播放",
                            color = PixelPlayLightPurple,
                            fontSize = 13.sp
                        )
                    }
                }

                // 右侧：播放全部 & 随机播放
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var playAllFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                isSelected = true,
                                onFocusChanged = { playAllFocused = it },
                                onClick = {
                                    if (songs.isNotEmpty()) {
                                        PlayerController.playSong(songs.first(), songs)
                                        Toast.makeText(context, "开始播放全部 (${songs.size} 首)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "播放全部",
                            color = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var shuffleFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                onFocusChanged = { shuffleFocused = it },
                                onClick = {
                                    if (songs.isNotEmpty()) {
                                        val shuffled = songs.shuffled()
                                        PlayerController.playSong(shuffled.first(), shuffled)
                                        Toast.makeText(context, "已开启随机播放", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (shuffleFocused) PixelPlayPurpleDark else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "随机漫游",
                            color = if (shuffleFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 歌曲网格列表
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("歌单暂无歌曲", color = PixelPlayLightPurple, fontSize = 16.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(songs, key = { idx, s -> "${s.id}_$idx" }) { index, song ->
                            val isCurrent = currentSong?.id == song.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusableCard(
                                        scaleFocused = 1.03f,
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = {
                                            PlayerController.playSong(song, songs)
                                        }
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = String.format("%02d", index + 1),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else PixelPlayLightPurple.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )

                                AsyncImage(
                                    model = song.coverUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PixelPlaySurfaceVariant)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${song.artist} · ${song.album}",
                                        color = PixelPlayLightPurple,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 底部悬浮胶囊 MiniPlayer
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
    }
}
