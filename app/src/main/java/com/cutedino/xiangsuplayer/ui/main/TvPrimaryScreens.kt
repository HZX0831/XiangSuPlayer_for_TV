package com.cutedino.xiangsuplayer.ui.main

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import com.cutedino.xiangsuplayer.core.toplist.ToplistCatalog
import com.cutedino.xiangsuplayer.core.toplist.ToplistRepository
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.components.SongPlatformBadge
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayGold
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayOrange
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPink
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurplePrimary
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurface
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
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
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var dailySongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var areaSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedAreaType by remember { mutableIntStateOf(7) } // 7:华语, 96:欧美, 8:日本, 16:韩国
    var isLoading by remember { mutableStateOf(true) }

    fun loadExploreData() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                @Suppress("UNCHECKED_CAST")
                if (NcmApi.isLogin) {
                    val recPl = NcmApi.recommendPlaylists().getOrNull()?.ncmList("recommend").orEmpty()
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

                    val recSongs = NcmApi.recommendSongs().getOrNull()?.ncmObj("data")?.ncmList("dailySongs").orEmpty()
                    val parsedSongs = recSongs.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
                    if (parsedSongs.isNotEmpty()) dailySongs = parsedSongs
                }

                @Suppress("UNCHECKED_CAST")
                if (playlists.isEmpty()) {
                    val perPl = NcmApi.personalizedPlaylists(limit = 14).getOrNull()?.ncmList("result").orEmpty()
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
                    val newSongs = NcmApi.full.personalizedNewsong(limit = 12).getOrNull()?.ncmList("result").orEmpty()
                    dailySongs = newSongs.mapNotNull { item ->
                        val map = item as? Map<String, Any?> ?: return@mapNotNull null
                        val songObj = map.ncmObj("song")
                        if (songObj.isNotEmpty()) songObj.toSong().copy(source = "wy") else null
                    }
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadExploreData()
    }

    LaunchedEffect(selectedAreaType) {
        withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            val dataList = NcmApi.full.topSong(selectedAreaType).getOrNull()?.ncmList("data").orEmpty()
            areaSongs = dataList.take(18).mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // 1. 顶部三大核心音乐电台卡片 (私人 FM / 心动模式 / 每日推荐)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ExploreHeroCard(
                    title = "私人 FM 漫游",
                    subtitle = "根据你的听歌口味无限畅听专属电台",
                    badgeText = "PERSONAL FM",
                    icon = Icons.Default.Radio,
                    gradientColors = listOf(Color(0xFF2855AE), Color(0xFF726CF8)),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        Toast.makeText(context, "正在开启私人 FM 漫游...", Toast.LENGTH_SHORT).show()
                        PlayerController.startPersonalFm { ok ->
                            if (ok) onOpenPlayer()
                        }
                    }
                )

                ExploreHeroCard(
                    title = "心动智能模式",
                    subtitle = "红心歌曲与相似旋律智能交织推荐",
                    badgeText = "HEART MODE",
                    icon = Icons.Default.Favorite,
                    gradientColors = listOf(Color(0xFFC83E74), Color(0xFFF06292)),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        Toast.makeText(context, "正在启动心动智能推荐...", Toast.LENGTH_SHORT).show()
                        PlayerController.startHeartMode { ok ->
                            if (ok) onOpenPlayer()
                        }
                    }
                )

                ExploreHeroCard(
                    title = if (NcmApi.isLogin) "每日专属推荐" else "热门新歌精选",
                    subtitle = "共 ${dailySongs.size.coerceAtLeast(12)} 首精选好歌 · 一键畅听",
                    badgeText = "DAILY MIX",
                    icon = Icons.Default.AutoAwesome,
                    gradientColors = listOf(Color(0xFFD96B43), Color(0xFFFF8A65)),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (dailySongs.isNotEmpty()) {
                            PlayerController.playSong(dailySongs.first(), dailySongs)
                            onOpenPlayer()
                        } else {
                            loadExploreData()
                        }
                    }
                )
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
                        text = if (NcmApi.isLogin) "为你甄选的专属歌单" else "热门精选歌单",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "按左右键浏览 · 确认键查看详情",
                        color = PixelPlayLightPurple.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading && playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
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
                                        shape = RoundedCornerShape(18.dp),
                                        onClick = { onOpenPlaylist(pl) }
                                    )
                                    .padding(10.dp)
                            ) {
                                AsyncImage(
                                    model = pl.coverUrl,
                                    contentDescription = pl.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(126.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PixelPlaySurfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pl.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.height(34.dp)
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
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "全球新歌速递",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        areaTabs.forEach { (type, label) ->
                            val isSelected = selectedAreaType == type
                            var focused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .tvPillFocusable(
                                        isSelected = isSelected,
                                        onFocusChanged = { focused = it },
                                        onClick = { selectedAreaType = type }
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (focused) PixelPlayPurpleDark else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    var playAllFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                onFocusChanged = { playAllFocused = it },
                                onClick = {
                                    if (areaSongs.isNotEmpty()) {
                                        PlayerController.playSong(areaSongs.first(), areaSongs)
                                    }
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "播放本区全部",
                            color = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val rows = areaSongs.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowSongs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            rowSongs.forEach { song ->
                                TvCompactSongCard(
                                    song = song,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        PlayerController.playSong(song, areaSongs)
                                    }
                                )
                            }
                            repeat(3 - rowSongs.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreHeroCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(118.dp)
            .tvFocusableCard(
                scaleFocused = 1.04f,
                shape = RoundedCornerShape(22.dp),
                onClick = onClick
            )
            .background(Brush.linearGradient(gradientColors))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TvCompactSongCard(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .tvFocusableCard(
                scaleFocused = 1.03f,
                shape = RoundedCornerShape(14.dp),
                onClick = onClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PixelPlaySurfaceVariant)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                SongPlatformBadge(source = song.source)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} · ${song.album.ifBlank { "单曲" }}",
                color = PixelPlayLightPurple,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. 多平台官方排行榜页 (TvToplistScreen) — 网易云 / QQ音乐 / 酷狗 30大榜单
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TvToplistScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPlatform by remember { mutableStateOf(ToplistCatalog.Platform.WY) }
    val boards = remember(selectedPlatform) { ToplistCatalog.boardsFor(selectedPlatform) }
    var selectedBoard by remember(selectedPlatform) { mutableStateOf(boards.first()) }
    var boardSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadBoard(entry: ToplistCatalog.Entry, force: Boolean = false) {
        scope.launch {
            isLoading = true
            val res = ToplistRepository.fetchBoardSongs(entry, forceRefresh = force)
            boardSongs = res.getOrDefault(emptyList())
            isLoading = false
        }
    }

    LaunchedEffect(selectedBoard) {
        loadBoard(selectedBoard)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 左侧：平台切换胶囊 + 10大榜单列表
        Column(
            modifier = Modifier
                .width(290.dp)
                .fillMaxHeight()
        ) {
            // 平台筛选器 (网易云 / QQ音乐 / 酷狗)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToplistCatalog.Platform.entries.forEach { platform ->
                    val isSelected = selectedPlatform == platform
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvPillFocusable(
                                isSelected = isSelected,
                                onFocusChanged = { focused = it },
                                onClick = { selectedPlatform = platform }
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = platform.label,
                            color = if (focused) PixelPlayPurpleDark else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(boards, key = { it.id }) { entry ->
                    val isSelected = selectedBoard.id == entry.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusableCard(
                                scaleFocused = 1.03f,
                                shape = RoundedCornerShape(14.dp),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBackgroundColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                } else {
                                    PixelPlaySurface.copy(alpha = 0.8f)
                                },
                                onClick = { selectedBoard = entry }
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = entry.name,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (entry.subtitle.isNotBlank()) {
                                Text(
                                    text = entry.subtitle,
                                    color = PixelPlayLightPurple,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        SongPlatformBadge(source = entry.platformKey)
                    }
                }
            }
        }

        // 右侧：当前选中榜单歌曲列表
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${selectedPlatform.label} · ${selectedBoard.name}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "(${boardSongs.size} 首)",
                        color = PixelPlayLightPurple,
                        fontSize = 14.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    var playAllFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                isSelected = true,
                                onFocusChanged = { playAllFocused = it },
                                onClick = {
                                    if (boardSongs.isNotEmpty()) {
                                        PlayerController.playSong(boardSongs.first(), boardSongs)
                                        Toast.makeText(context, "正在播放榜单全部歌曲", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "播放全部",
                            color = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var refreshFocused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .tvPillFocusable(
                                onFocusChanged = { refreshFocused = it },
                                onClick = { loadBoard(selectedBoard, force = true) }
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = if (refreshFocused) PixelPlayPurpleDark else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "刷新",
                            color = if (refreshFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (boardSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无榜单数据，请点击右上角刷新重试", color = PixelPlayLightPurple)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(boardSongs, key = { idx, s -> "${s.id}_$idx" }) { index, song ->
                        val rankColor = when (index) {
                            0 -> PixelPlayGold
                            1 -> PixelPlayPink
                            2 -> PixelPlayOrange
                            else -> PixelPlayLightPurple.copy(alpha = 0.65f)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusableCard(
                                    scaleFocused = 1.02f,
                                    shape = RoundedCornerShape(14.dp),
                                    onClick = {
                                        PlayerController.playSong(song, boardSongs)
                                    }
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = String.format("%02d", index + 1),
                                color = rankColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.width(26.dp)
                            )

                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PixelPlaySurfaceVariant)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    SongPlatformBadge(source = song.source)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${song.artist} · ${song.album.ifBlank { selectedBoard.name }}",
                                    color = PixelPlayLightPurple,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. 5大内置音源聚合搜索页 (TvSearchScreen) — ALL / WY / TX / KG / KW / MG
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TvSearchScreen(
    onOpenPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf(BuiltInSourceSearchApi.MusicPlatform.ALL) }
    var searchPlaylistsMode by remember { mutableStateOf(false) }

    var songResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var hotKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var historyKeywords by remember { mutableStateOf(SessionManager.getSearchHistory(context)) }
    var isSearching by remember { mutableStateOf(false) }

    fun executeSearch(targetQuery: String = query) {
        val trimmed = targetQuery.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        SessionManager.addSearchHistory(context, trimmed)
        historyKeywords = SessionManager.getSearchHistory(context)

        scope.launch {
            isSearching = true
            if (searchPlaylistsMode) {
                playlistResults = BuiltInSourceSearchApi.searchPlaylists(trimmed, limit = 24)
            } else {
                songResults = BuiltInSourceSearchApi.searchSongs(
                    platformKey = selectedPlatform.key,
                    keyword = trimmed,
                    page = 1,
                    limit = 36
                ).getOrDefault(emptyList())
            }
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            val hotList = NcmApi.full.searchHotDetail().getOrNull()?.ncmList("data").orEmpty()
            hotKeywords = hotList.take(12).mapNotNull {
                (it as? Map<String, Any?>)?.ncmString("searchWord")?.takeIf { w -> w.isNotBlank() }
            }
        }
    }

    LaunchedEffect(selectedPlatform, searchPlaylistsMode) {
        if (query.isNotBlank()) {
            executeSearch(query)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 6.dp)
    ) {
        // 顶部搜索输入栏 + 平台切换胶囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 搜索输入框
            Row(
                modifier = Modifier
                    .width(340.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PixelPlaySurfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = PixelPlayLightPurple,
                    modifier = Modifier.size(18.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索歌名、歌手、专辑...",
                            color = PixelPlayLightPurple.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            var searchBtnFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .tvPillFocusable(
                        isSelected = true,
                        onFocusChanged = { searchBtnFocused = it },
                        onClick = { executeSearch() }
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "搜索",
                    color = if (searchBtnFocused) PixelPlayPurpleDark else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 5大平台筛选胶囊
            BuiltInSourceSearchApi.MusicPlatform.entries.forEach { platform ->
                val isSelected = !searchPlaylistsMode && selectedPlatform == platform
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .tvPillFocusable(
                            isSelected = isSelected,
                            onFocusChanged = { focused = it },
                            onClick = {
                                searchPlaylistsMode = false
                                selectedPlatform = platform
                            }
                        )
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = platform.label,
                        color = if (focused) PixelPlayPurpleDark else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // 歌单搜索切换
            var plTabFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .tvPillFocusable(
                        isSelected = searchPlaylistsMode,
                        onFocusChanged = { plTabFocused = it },
                        onClick = { searchPlaylistsMode = true }
                    )
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "歌单",
                    color = if (plTabFocused) PixelPlayPurpleDark else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (searchPlaylistsMode || plTabFocused) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 搜索历史与热搜词快捷栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = if (historyKeywords.isNotEmpty()) historyKeywords else hotKeywords
            val prefixTitle = if (historyKeywords.isNotEmpty()) "搜索历史:" else "全网热搜:"
            Text(
                text = prefixTitle,
                color = PixelPlayGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { word ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .tvPillFocusable(
                                onFocusChanged = { focused = it },
                                onClick = { executeSearch(word) }
                            )
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = word,
                            color = if (focused) PixelPlayPurpleDark else PixelPlayLightPurple,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (historyKeywords.isNotEmpty()) {
                var clearFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .tvPillFocusable(
                            onFocusChanged = { clearFocused = it },
                            onClick = {
                                SessionManager.clearSearchHistory(context)
                                historyKeywords = emptyList()
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = if (clearFocused) PixelPlayPurpleDark else PixelPlayLightPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "清空历史",
                        color = if (clearFocused) PixelPlayPurpleDark else PixelPlayLightPurple,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 搜索结果展示区
        Box(modifier = Modifier.weight(1f)) {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (searchPlaylistsMode) {
                if (playlistResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("输入关键词搜索精选歌单", color = PixelPlayLightPurple)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(playlistResults) { _, pl ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusableCard(
                                        scaleFocused = 1.04f,
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { onOpenPlaylist(pl) }
                                    )
                                    .padding(10.dp)
                            ) {
                                AsyncImage(
                                    model = pl.coverUrl,
                                    contentDescription = pl.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pl.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                if (songResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "支持全网 5 大平台 (网易云 / QQ音乐 / 酷狗 / 酷我 / 咪咕) 聚合搜索",
                            color = PixelPlayLightPurple,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(songResults, key = { idx, s -> "${s.source}_${s.id}_$idx" }) { _, song ->
                            TvCompactSongCard(
                                song = song,
                                onClick = {
                                    PlayerController.playSong(song, songResults)
                                    Toast.makeText(context, "正在播放: ${song.title}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
