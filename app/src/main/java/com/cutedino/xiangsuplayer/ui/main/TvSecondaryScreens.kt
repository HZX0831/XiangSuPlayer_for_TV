package com.cutedino.xiangsuplayer.ui.main

import androidx.compose.foundation.layout.heightIn

import com.cutedino.xiangsuplayer.ui.components.TvReadingText

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.focusable
import androidx.compose.runtime.key

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import com.cutedino.xiangsuplayer.ui.components.TvTextInput
import com.cutedino.xiangsuplayer.core.source.market.MarketAsset
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.HorizontalDivider



import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.cutedino.xiangsuplayer.ui.components.TvDialogPanel
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.SourceEnablement
import com.cutedino.xiangsuplayer.core.source.market.MarketRelease
import com.cutedino.xiangsuplayer.core.source.market.SourceMarketRepository
import com.cutedino.xiangsuplayer.core.source.market.SourceAlreadyInstalledException
import com.cutedino.xiangsuplayer.core.source.market.LxScriptSourceAdapter
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayGold
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayGreen
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayLightPurple
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayPurpleDark
import com.cutedino.xiangsuplayer.ui.theme.PixelPlayRed
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurface
import com.cutedino.xiangsuplayer.ui.theme.PixelPlaySurfaceVariant
import com.cutedino.xiangsuplayer.ui.theme.tvFocusableCard
import com.cutedino.xiangsuplayer.ui.theme.tvPillFocusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString

// ═════════════════════════════════════════════════════════════════════════════
// 4. 多平台我的曲库页 (TvLibraryScreen) — 本地收藏、网易云/QQ/酷狗公开榜单、网易云盘
// ═════════════════════════════════════════════════════════════════════════════

private enum class LibrarySubTab(val label: String) {
    FAVORITES("我的收藏"),
    WY_PLAYLISTS("网易云歌单"),
    TX_PLAYLISTS("QQ音乐歌单"),
    KG_PLAYLISTS("酷狗音乐歌单"),
    WY_CLOUD("网易云音乐云盘"),
    LOCAL_DIRECTORIES("本地目录"),
    WEBDAV("WebDAV")
}

@Composable
fun TvLibraryScreen(
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenAccountModal: () -> Unit,
    modifier: Modifier = Modifier,
    accountRevision: Int = 0
) {
    val currentSong by PlayerController.currentSong.observeAsState()
    val context = LocalContext.current
    val favoriteSongs by PlayerController.favoriteSongs.collectAsState()
    var selectedSubTab by rememberSaveable { mutableStateOf(LibrarySubTab.FAVORITES) }

    var wyPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var txPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var kgPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var cloudSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var libraryError by remember { mutableStateOf(false) }
    var libraryRetry by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedSubTab, accountRevision, libraryRetry) {
        wyPlaylists = emptyList()
        txPlaylists = emptyList()
        kgPlaylists = emptyList()
        cloudSongs = emptyList()
        isLoading = true
        libraryError = false
        try {
            when (selectedSubTab) {
                LibrarySubTab.FAVORITES, LibrarySubTab.LOCAL_DIRECTORIES, LibrarySubTab.WEBDAV -> {}
                LibrarySubTab.WY_PLAYLISTS -> {
                    if (NcmApi.isLogin && NcmApi.userId > 0) {
                        @Suppress("UNCHECKED_CAST")
                        val rawList = NcmApi.userPlaylist(NcmApi.userId.toString(), limit = 40).getOrThrow().ncmList("playlist")
                        wyPlaylists = rawList.mapNotNull { item ->
                            val map = item as? Map<String, Any?> ?: return@mapNotNull null
                            Playlist(
                                id = map.ncmLong("id").toString(),
                                name = map.ncmString("name"),
                                coverUrl = map.ncmString("coverImgUrl"),
                                trackCount = map.ncmLong("trackCount").toInt(),
                                source = "wy"
                            )
                        }
                    }
                }
                LibrarySubTab.TX_PLAYLISTS -> {
                    txPlaylists = MultiPlatformAccountManager.fetchQqUserPlaylists()
                }
                LibrarySubTab.KG_PLAYLISTS -> {
                    kgPlaylists = MultiPlatformAccountManager.fetchKugouUserPlaylists()
                }
                LibrarySubTab.WY_CLOUD -> {
                    if (NcmApi.isLogin) {
                        @Suppress("UNCHECKED_CAST")
                        val rawData = NcmApi.full.userCloud(limit = 100).getOrThrow().ncmList("data")
                        cloudSongs = rawData.mapNotNull { item ->
                            val map = item as? Map<String, Any?> ?: return@mapNotNull null
                            val simpleSong = map.ncmObj("simpleSong")
                            if (simpleSong.isNotEmpty()) {
                                simpleSong.toSong().copy(source = "wy")
                            } else {
                                Song(
                                    id = map.ncmLong("songId").toString(),
                                    title = map.ncmString("songName"),
                                    artist = map.ncmString("artist").ifBlank { "未知歌手" },
                                    album = map.ncmString("album"),
                                    source = "wy"
                                )
                            }
                        }
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            libraryError = true
        } finally {
            isLoading = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 0.dp, vertical = 6.dp)
    ) {
        LazyColumn(
            modifier = Modifier.width(156.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(LibrarySubTab.entries, key = { it.name }) { tab ->
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth().tvPillFocusable(isSelected = selectedSubTab == tab,
                    focusKey = "library/category/" + tab.name, onFocusChanged = { focused = it }, onClick = { selectedSubTab = tab })
                    .padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(tab.label, style = MaterialTheme.typography.titleSmall,
                        color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            item("accounts") { TvTextAction("管理账号", onClick = onOpenAccountModal) }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (libraryError) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TvTextAction("曲库加载失败，重试") { libraryRetry++ }
                }
            } else {
                when (selectedSubTab) {
                    LibrarySubTab.LOCAL_DIRECTORIES -> TvFolderLibrary(network = false)
                    LibrarySubTab.WEBDAV -> TvFolderLibrary(network = true)
                    LibrarySubTab.FAVORITES -> {
                        if (favoriteSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "暂无本地收藏歌曲 · 在播放界面点击「收藏」即可持久化保存",
                                    color = PixelPlayLightPurple,
                                    fontSize = 16.sp
                                )
                            }
                        } else {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "我的收藏曲库 (${favoriteSongs.size} 首)",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    var playAllFocused by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .tvPillFocusable(
                                                isPrimary = true,
                                                onFocusChanged = { playAllFocused = it },
                                                onClick = {
                                                    PlayerController.playSong(favoriteSongs.first(), favoriteSongs)
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "播放全部收藏",
                                            color = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(320.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(favoriteSongs, key = { _, s -> "${s.key.sourceId}:${s.id}" }) { _, song ->
                                        TvCompactSongCard(
                                            song = song,
                                            isCurrent = currentSong?.key == song.key,
                                            onClick = {
                                                PlayerController.playSong(song, favoriteSongs)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LibrarySubTab.WY_PLAYLISTS -> {
                        PlaylistGridSection(
                            playlists = wyPlaylists,
                            emptyHint = if (NcmApi.isLogin) "暂无网易云歌单" else "请先点击右上角登录网易云账号同步歌单",
                            onOpenPlaylist = onOpenPlaylist
                        )
                    }

                    LibrarySubTab.TX_PLAYLISTS -> {
                        PlaylistGridSection(
                            playlists = txPlaylists,
                            emptyHint = if (MultiPlatformAccountManager.isQqLoggedIn) "该 QQ 账号没有可读取的公开歌单" else "绑定 QQ 公开资料后可浏览公开歌单",
                            onOpenPlaylist = onOpenPlaylist
                        )
                    }

                    LibrarySubTab.KG_PLAYLISTS -> {
                        PlaylistGridSection(
                            playlists = kgPlaylists,
                            emptyHint = "暂无酷狗公开榜单",
                            onOpenPlaylist = onOpenPlaylist
                        )
                    }

                    LibrarySubTab.WY_CLOUD -> {
                        if (cloudSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (NcmApi.isLogin) "你的网易云音乐云盘暂无曲目" else "请先登录网易云账号以访问音乐云盘",
                                    color = PixelPlayLightPurple
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(320.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(cloudSongs, key = { idx, s -> "${s.id}_$idx" }) { _, song ->
                                    TvCompactSongCard(
                                        song = song,
                                            isCurrent = currentSong?.key == song.key,
                                        onClick = {
                                            PlayerController.playSong(song, cloudSongs)
                                            Toast.makeText(context, "正在播放云盘歌曲: ${song.title}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridSection(
    playlists: List<Playlist>,
    emptyHint: String,
    onOpenPlaylist: (Playlist) -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = emptyHint, color = PixelPlayLightPurple, fontSize = 16.sp)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(144.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(playlists, key = { idx, p -> "${p.id}_$idx" }) { _, pl ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusableCard(
                            scaleFocused = 1.04f,
                            shape = RoundedCornerShape(6.dp),
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
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PixelPlaySurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = pl.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(34.dp)
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. 落雪 (LX) 音源市场与实时测速页 (TvSourceMarketScreen)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TvSourceMarketScreen(modifier: Modifier = Modifier,
    fetchReleases: suspend () -> Result<List<MarketRelease>> = { SourceMarketRepository.fetchMarketReleases() },
    installAsset: suspend (MarketAsset, Boolean) -> Result<Int> = { asset, replace -> SourceMarketRepository.installAsset(asset, replace) }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<MarketRelease>>(emptyList()) }
    var adapters by remember { mutableStateOf(SoundSourceRepository.getAllAdapters()) }
    var enabledSet by remember { mutableStateOf(SessionManager.enabledSoundSources) }
    var disabledLxSet by remember { mutableStateOf(SessionManager.disabledLxSources) }
    val latency = remember { mutableStateMapOf<String, Long>() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf("") }
    var installed by rememberSaveable { mutableStateOf(false) }
    var replace by remember { mutableStateOf<(() -> Unit)?>(null) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    val entryFocus = remember { FocusRequester() }
    val marketFocus = remember { FocusRequester() }
    val installedFocus = remember { FocusRequester() }
    var restoreInfo by remember { mutableStateOf(false) }
    val memory = com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory.current
        ?: remember { com.cutedino.xiangsuplayer.ui.theme.TvFocusMemory() }
    fun refreshAdapters() {
        adapters = SoundSourceRepository.getAllAdapters()
        enabledSet = SessionManager.enabledSoundSources
        disabledLxSet = SessionManager.disabledLxSources
    }
    fun importSource(url: String, replacing: Boolean = false) {
        if (operation) return
        operation = true
        scope.launch { try {
            SourceMarketRepository.importFromUrl(url, replacing).onSuccess {
                address = ""; refreshAdapters()
                Toast.makeText(context, context.getString(R.string.tv_source_imported), Toast.LENGTH_SHORT).show()
            }.onFailure {
                if (it is CancellationException) throw it
                if (it is SourceAlreadyInstalledException) replace = { importSource(url, true) }
                else info = context.getString(R.string.tv_source_import_failed) to (it.message ?: context.getString(R.string.tv_source_import_failed))
            }
        } finally { operation = false } }
    }
    fun install(asset: MarketAsset, replacing: Boolean = false) {
        if (operation) return
        operation = true
        scope.launch { try {
            installAsset(asset, replacing).onSuccess {
                refreshAdapters()
                Toast.makeText(context, context.getString(R.string.tv_source_installed, it), Toast.LENGTH_SHORT).show()
            }.onFailure {
                if (it is CancellationException) throw it
                if (it is SourceAlreadyInstalledException) replace = { install(asset, true) }
                else info = context.getString(R.string.tv_source_install_failed) to (it.message ?: context.getString(R.string.tv_source_install_failed))
            }
        } finally { operation = false } }
    }
    LaunchedEffect(revision) {
        loading = true; failed = false
        try {
            val result = fetchReleases()
            ensureActive()
            result.onSuccess { releases = it }.onFailure { if (it is CancellationException) throw it; failed = true }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(restoreInfo) {
        if (restoreInfo) { withFrameNanos { }; memory.restore(); restoreInfo = false }
    }
    androidx.compose.runtime.CompositionLocalProvider(com.cutedino.xiangsuplayer.ui.theme.LocalTvFocusMemory provides memory) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { TvTextAction(stringResource(R.string.tv_market_title), Modifier.focusRequester(marketFocus), selected = !installed) { installed = false } }
                item { TvTextAction(stringResource(R.string.tv_installed_sources), Modifier.focusRequester(installedFocus), selected = installed) { installed = true } }
                item { TvTextAction(stringResource(R.string.tv_source_compatibility)) {
                    info = context.getString(R.string.tv_source_compatibility) to context.getString(R.string.tv_source_compatibility_body)
                } }
            }
            if (!installed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TvTextInput(address, { address = it }, stringResource(R.string.tv_source_url), Modifier.weight(1f))
                    TvTextAction(stringResource(R.string.tv_import), enabled = address.isNotBlank(), busy = operation) { importSource(address) }
                }
                TvTextAction(stringResource(R.string.tv_retry), Modifier.focusRequester(entryFocus), busy = loading) { revision++ }
                if (failed) Text(stringResource(if (releases.isEmpty()) R.string.tv_load_failed else R.string.tv_refresh_failed), color = MaterialTheme.colorScheme.error)
                if (loading && releases.isEmpty()) CircularProgressIndicator()
                else if (releases.isEmpty() && !failed) Text(stringResource(R.string.tv_market_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(releases, key = { it.id }) { release ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(release.name, style = MaterialTheme.typography.titleMedium)
                            Text(release.tagName + " · " + release.publishedAt.take(10), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (release.body.isNotBlank()) TvTextAction(stringResource(R.string.tv_release_notes), Modifier.fillMaxWidth()) { info = release.name to release.body }
                            release.assets.forEach { asset -> key(asset.id) {
                                TvTextAction(asset.name + " · " + context.getString(R.string.tv_install) + " (" + asset.size / 1024 + " KB)",
                                    Modifier.fillMaxWidth(), busy = operation) { install(asset) }
                            } }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            } else {
                TvTextAction(stringResource(if (testing) R.string.tv_latency_running else R.string.tv_latency_test), busy = testing) {
                    testing = true
                    scope.launch { try {
                        for (adapter in adapters) { latency[adapter.sourceId] = -2; latency[adapter.sourceId] = SoundSourceRepository.testAdapterLatency(adapter) }
                    } finally { testing = false } }
                }
                if (adapters.isEmpty()) Text(stringResource(R.string.tv_source_none))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(adapters, key = { it.sourceId }) { adapter ->
                        val isLx = SourceEnablement.isLx(adapter.sourceId)
                        val compatible = (adapter as? LxScriptSourceAdapter)?.compatible != false
                        val active = compatible && SourceEnablement.isEnabled(adapter.sourceId, enabledSet, disabledLxSet)
                        val status = context.getString(if (!compatible) R.string.tv_source_incompatible else if (active) R.string.tv_source_enabled else R.string.tv_source_disabled)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(adapter.sourceName, style = MaterialTheme.typography.titleMedium)
                            val measured = latency[adapter.sourceId]
                            Text(status + " · " + when {
                                measured == null -> context.getString(R.string.tv_no_latency)
                                measured == -2L -> context.getString(R.string.tv_latency_running)
                                measured < 0 -> context.getString(R.string.tv_latency_failed)
                                else -> measured.toString() + " ms"
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { TvTextAction(stringResource(if (active) R.string.tv_disable else R.string.tv_enable), selected = active,
                                    enabled = compatible && (isLx || !active || enabledSet.size > 1)) {
                                    if (isLx) {
                                        disabledLxSet = if (adapter.sourceId in disabledLxSet) disabledLxSet - adapter.sourceId else disabledLxSet + adapter.sourceId
                                        SessionManager.disabledLxSources = disabledLxSet; SoundSourceRepository.disabledLxSourceIds = disabledLxSet
                                    } else {
                                        enabledSet = if (active) enabledSet - adapter.sourceId else enabledSet + adapter.sourceId
                                        SessionManager.enabledSoundSources = enabledSet; SoundSourceRepository.enabledSourceIds = enabledSet
                                    }
                                } }
                                item { TvTextAction(stringResource(R.string.tv_source_details)) {
                                    info = adapter.sourceName to context.getString(R.string.tv_current_source_details, adapter.sourceId, adapter.priority, status)
                                } }
                                if (isLx) item { TvTextAction(stringResource(R.string.tv_uninstall)) {
                                    SourceMarketRepository.removeInstalledSource(adapter.sourceId)
                                    disabledLxSet = disabledLxSet - adapter.sourceId
                                    SessionManager.disabledLxSources = disabledLxSet; SoundSourceRepository.disabledLxSourceIds = disabledLxSet
                                    refreshAdapters(); scope.launch { installedFocus.tryRequestFocus() }
                                } }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
    info?.let { (title, body) ->
        val closeFocus = remember { FocusRequester() }
        val dismiss = { info = null; restoreInfo = true }
        TvDialogPanel(title, dismiss, footer = { TvTextAction(stringResource(R.string.tv_close), Modifier.focusRequester(closeFocus), onClick = dismiss) }) {
            LaunchedEffect(Unit) { closeFocus.tryRequestFocus() }
            TvReadingText(body)
        }
    }
    replace?.let { action ->
        val cancelFocus = remember { FocusRequester() }
        val dismiss = { replace = null; restoreInfo = true }
        TvDialogPanel(stringResource(R.string.tv_source_replaced), dismiss, footer = {
            TvTextAction(stringResource(R.string.tv_cancel), Modifier.focusRequester(cancelFocus), onClick = dismiss)
            TvTextAction(stringResource(R.string.tv_replace)) { dismiss(); action() }
        }) { LaunchedEffect(Unit) { cancelFocus.tryRequestFocus() }; Text(stringResource(R.string.tv_source_replace_note)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 6. 播放历史页 (TvHistoryScreen) — Room 数据库持久化
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TvHistoryScreen(
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit = {}
) {
    val currentSong by PlayerController.currentSong.observeAsState()
    val historySongs by PlayerController.playHistory.observeAsState(emptyList())

    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val fallback = remember { FocusRequester() }
    val entryFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    var pendingRemoval by remember { mutableStateOf<Pair<com.cutedino.xiangsuplayer.core.model.SongKey, Int>?>(null) }
    var target by remember { mutableIntStateOf(-1) }
    var cleared by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(historySongs, pendingRemoval, cleared) {
        val pending = pendingRemoval
        if (pending != null && historySongs.none { it.key == pending.first }) {
            if (historySongs.isNotEmpty()) {
                target = pending.second.coerceIn(historySongs.indices)
                grid.scrollToItem(target)
                withFrameNanos { }
                entryFocus.tryRequestFocus()
            } else fallback.tryRequestFocus()
            pendingRemoval = null
        }
        if (cleared && historySongs.isEmpty()) {
            cleared = false
            fallback.tryRequestFocus()
        }
    }
    if (confirmClear) {
        val cancel = remember { FocusRequester() }
        val dismiss = { confirmClear = false; clearFocus.tryRequestFocus(); Unit }
        TvDialogPanel("清空播放记录？", onDismiss = dismiss, footer = {
            TvTextAction("取消", Modifier.focusRequester(cancel), onClick = dismiss)
            TvTextAction("清空") {
                confirmClear = false
                cleared = true
                PlayerController.clearHistory()
            }
        }) {
            Text("此操作会删除本机的全部播放记录。")
            LaunchedEffect(Unit) { cancel.tryRequestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "最近播放历史 (${historySongs.size} 首)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "已启用 Room 本地数据库跨重启持久化保存",
                    color = PixelPlayLightPurple,
                    fontSize = 16.sp
                )
            }

            if (historySongs.isEmpty()) TvTextAction("返回导航", Modifier.focusRequester(fallback), onClick = onNavigate)
            if (historySongs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    var playAllFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .tvPillFocusable(
                                isPrimary = true,
                                onFocusChanged = { playAllFocused = it },
                                onClick = {
                                    PlayerController.playSong(historySongs.first(), historySongs)
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "播放全部历史",
                            color = if (playAllFocused) PixelPlayPurpleDark else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var clearFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier.focusRequester(clearFocus)
                            .tvPillFocusable(
                                onFocusChanged = { clearFocused = it },
                                onClick = {
                                    confirmClear = true
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "清空记录",
                            color = if (clearFocused) PixelPlayPurpleDark else PixelPlayRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (historySongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无播放记录，去探索或搜索想听的音乐吧", color = PixelPlayLightPurple, fontSize = 16.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(historySongs, key = { _, song -> "history/${song.key.sourceId}/${song.key.trackId}" }) { index, song ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TvCompactSongCard(song, Modifier.weight(1f), isCurrent = currentSong?.key == song.key) { PlayerController.playSong(song, historySongs) }
                        TvTextAction(
                            "移除",
                            if (index == target) Modifier.focusRequester(entryFocus) else Modifier
                        ) {
                            pendingRemoval = song.key to index
                            PlayerController.removeFromHistory(song)
                        }
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 7. 设置与关于页 (TvSettingsScreen) — 对齐 XiangsuPlayerHQ 主题、壁纸、音质与贡献者致谢
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvSettingsScreen(
    onThemeModeChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentQuality by remember { mutableStateOf(SessionManager.getAudioQuality(context)) }
    var useWallpaper by remember { mutableStateOf(SessionManager.useWallpaperBackground) }
    var opacityPercent by remember { mutableIntStateOf(SessionManager.wallpaperOpacityPercent) }
    var customWallUrl by remember { mutableStateOf(SessionManager.customWallpaperUrl.orEmpty()) }
    var legacyHttp by remember { mutableStateOf(SessionManager.legacyHttpSourcesEnabled) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            SettingsCard(
                title = "默认流媒体输出音质",
                subtitle = "优先请求指定无损/Hi-Res档位，未命中时自动向下兼容"
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { q ->
                        val isSelected = currentQuality == q
                        var focused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .tvPillFocusable(
                                    isSelected = isSelected,
                                    onFocusChanged = { focused = it },
                                    onClick = {
                                        currentQuality = q
                                        SessionManager.saveAudioQuality(context, q)
                                        PlayerController.setQuality(q)
                                    }
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = q.displayName,
                                color = if (focused) PixelPlayPurpleDark else Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 2. 视觉主题与背景风格 (默认 PixelPlay 流体深蓝紫 + 专辑动态取色，可选必应/自定义壁纸)
        item {
            SettingsCard(
                title = "背景与封面配色",
                subtitle = "品牌深色基底与专辑封面配色"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var mode1Focused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .tvPillFocusable(
                                    isSelected = !useWallpaper,
                                    onFocusChanged = { mode1Focused = it },
                                    onClick = {
                                        useWallpaper = false
                                        SessionManager.useWallpaperBackground = false
                                        onThemeModeChanged()
                                    }
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "封面配色",
                                color = if (mode1Focused) PixelPlayPurpleDark else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        var mode2Focused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .tvPillFocusable(
                                    isSelected = useWallpaper,
                                    onFocusChanged = { mode2Focused = it },
                                    onClick = {
                                        useWallpaper = true
                                        SessionManager.useWallpaperBackground = true
                                        onThemeModeChanged()
                                    }
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "必应每日壁纸 / 自定义壁纸",
                                color = if (mode2Focused) PixelPlayPurpleDark else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (useWallpaper) {
                        TvTextInput(customWallUrl, { customWallUrl = it }, stringResource(R.string.tv_wallpaper_url), Modifier.fillMaxWidth())
                        var previewError by remember(customWallUrl) { mutableStateOf(false) }
                        val validWallUrl = com.cutedino.xiangsuplayer.core.source.SourceNetwork.httpsUrl(customWallUrl.trim())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvTextAction("应用壁纸") {
                                if (validWallUrl == null || previewError) {
                                    Toast.makeText(context, "请输入可加载的 HTTPS 图片", Toast.LENGTH_SHORT).show()
                                } else {
                                    SessionManager.customWallpaperUrl = validWallUrl.toString()
                                    onThemeModeChanged()
                                }
                            }
                            TvTextAction("恢复默认") {
                                customWallUrl = ""
                                SessionManager.customWallpaperUrl = null
                                onThemeModeChanged()
                            }
                        }
                        if (validWallUrl != null) {
                            AsyncImage(
                                model = validWallUrl.toString(), contentDescription = "壁纸预览",
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentScale = ContentScale.Crop, onError = { previewError = true }
                            )
                            if (previewError) Text("预览加载失败", color = MaterialTheme.colorScheme.error)
                        }
                        FlowRow(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("壁纸暗角遮罩浓度:", color = PixelPlayLightPurple, fontSize = 16.sp)
                            listOf(30, 50, 70, 85).forEach { pct ->
                                val isSel = opacityPercent == pct
                                var f by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .tvPillFocusable(
                                            isSelected = isSel,
                                            onFocusChanged = { f = it },
                                            onClick = {
                                                opacityPercent = pct
                                                SessionManager.wallpaperOpacityPercent = pct
                                                onThemeModeChanged()
                                            }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$pct%",
                                        color = if (f) PixelPlayPurpleDark else Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Advanced compatibility remains available after daily settings.
        item {
            SettingsCard(stringResource(R.string.tv_advanced_compatibility), "仅对预设的无认证音源启用明文 HTTP。") {
                TvTextAction(if (legacyHttp) "关闭旧版 HTTP 音源" else "启用旧版 HTTP 音源", selected = legacyHttp) {
                    legacyHttp = !legacyHttp
                    SessionManager.legacyHttpSourcesEnabled = legacyHttp
                }
            }
        }

        // 3. 关于像素播放器 TV & 主项目致谢 (对齐 XiangsuPlayerHQ AboutScreen.kt)
        item {
            SettingsCard(
                title = stringResource(R.string.tv_about_title, com.cutedino.xiangsuplayer.BuildConfig.VERSION_NAME),
                subtitle = stringResource(R.string.tv_about_brand)
            ) {
                TvReadingText(listOf(
                    stringResource(R.string.tv_about_android_author),
                    stringResource(R.string.tv_about_tv_original),
                    stringResource(R.string.tv_about_tv_maintainer),
                    stringResource(R.string.tv_about_acknowledgments),
                    "• 官方交流群：907852088  |  官方网站：https://pixel.grammx.asia/"
                ).joinToString("\n\n"), Modifier.fillMaxWidth().heightIn(max = 200.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = PixelPlayLightPurple,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 8. 多平台账号中心弹窗 (TvMultiAccountDialog) — 网易云扫码、QQ音乐扫码/QQ号绑定、酷狗绑定
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TvMultiAccountDialog(
    onAccountChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qrSize = if (LocalConfiguration.current.screenHeightDp < 440) 132.dp else 168.dp

    var selectedPlatformTab by remember { mutableIntStateOf(0) } // 0:网易云, 1:QQ音乐, 2:酷狗

    // 网易云扫码状态
    var wyQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var wyQrStatus by remember { mutableStateOf("点击下方按钮生成网易云二维码") }

    // QQ音乐扫码与QQ号绑定状态
    var qqQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qqQrsig by remember { mutableStateOf("") }
    var qqQrStatus by remember { mutableStateOf("用手机 QQ 扫码或输入 QQ 号关联公开歌单") }
    var qqInputUin by remember { mutableStateOf(SessionManager.qqMusicUin) }

    // 酷狗绑定状态
    var wyQrJob by remember { mutableStateOf<Job?>(null) }
    var qqQrJob by remember { mutableStateOf<Job?>(null) }

    // 自动启动网易云二维码与轮询
    fun startWyQrLogin() {
        val previous = wyQrJob
        wyQrJob = scope.launch {
            previous?.cancelAndJoin()
            wyQrBitmap = null
            wyQrStatus = "正在获取网易云登录二维码..."
            val prep = MultiPlatformAccountManager.prepareNeteaseQr()
            val pair = prep.getOrNull()
            if (pair == null) {
                wyQrStatus = "二维码获取失败，请点击刷新"
                return@launch
            }
            wyQrBitmap = pair.second
            wyQrStatus = "请使用手机「网易云音乐 App」扫码授权"

            val awaitRes = NcmApi.qrLoginAwait(pair.first) { code, _ ->
                wyQrStatus = when (code) {
                    801 -> "等待手机扫码..."
                    802 -> "已扫码，请在手机上点击确认授权"
                    803 -> "登录成功！"
                    800 -> { wyQrBitmap = null; "二维码已过期，请点击刷新" }
                    else -> "状态码: $code"
                }
            }
            ensureActive()
            if (awaitRes.isSuccess && NcmApi.isLogin && NcmApi.fetchAndSaveAccountInfo().isSuccess) {
                Toast.makeText(context, "网易云音乐登录成功！", Toast.LENGTH_SHORT).show()
                onAccountChanged()
            }
        }
    }

    // 启动 QQ 音乐官方扫码与轮询
    fun startQqQrLogin() {
        val previous = qqQrJob
        qqQrJob = scope.launch {
            previous?.cancelAndJoin()
            qqQrBitmap = null
            qqQrsig = ""
            qqQrStatus = "正在生成 QQ 音乐安全二维码..."
            val prep = MultiPlatformAccountManager.prepareQqQr()
            val session = prep.getOrNull()
            if (session == null) {
                qqQrStatus = "二维码生成受限，推荐直接使用下方 QQ 号一键绑定"
                return@launch
            }
            qqQrBitmap = session.qrBitmap
            qqQrsig = session.qrsig
            qqQrStatus = "请使用手机 QQ 扫描二维码登录"

            while (qqQrsig.isNotBlank()) {
                delay(2200)
                val (code, msg) = MultiPlatformAccountManager.checkQqQrStatus(qqQrsig)
                ensureActive()
                qqQrStatus = msg
                if (code == 0) {
                    qqInputUin = SessionManager.qqMusicUin
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    onAccountChanged()
                    break
                } else if (code == 65) {
                    qqQrBitmap = null
                    break
                }
            }
        }
    }

    LaunchedEffect(selectedPlatformTab) {
        if (selectedPlatformTab == 0) {
            qqQrJob?.cancelAndJoin()
            qqQrBitmap = null
            if (!NcmApi.isLogin) startWyQrLogin()
        } else {
            wyQrJob?.cancelAndJoin()
            wyQrBitmap = null
            if (!MultiPlatformAccountManager.isQqLoggedIn) startQqQrLogin()
        }
    }

    val initialTab = remember { selectedPlatformTab }
    val tabFocus = remember { FocusRequester() }
    TvDialogPanel(
        title = "音乐账号",
        onDismiss = onDismiss,
        footer = { TvTextAction("关闭", onClick = onDismiss) }
    ) {
        LaunchedEffect(Unit) { tabFocus.requestFocus() }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tabs = listOf("网易云音乐", "QQ 公开资料")
                itemsIndexed(tabs, key = { _, name -> name }) { index, name ->
                    TvTextAction(
                        name,
                        modifier = if (initialTab == index) Modifier.focusRequester(tabFocus) else Modifier,
                        selected = selectedPlatformTab == index
                    ) { selectedPlatformTab = index }
                }
            }

            when (selectedPlatformTab) {
                // 0. 网易云音乐
                0 -> {
                    if (NcmApi.isLogin) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(PixelPlaySurfaceVariant)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "已登录：${NcmApi.nickname.ifBlank { "网易云黑胶用户" }}",
                                color = PixelPlayGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "UID: ${NcmApi.userId} · 已开启私人FM、心动模式、红心与云盘同步",
                                color = PixelPlayLightPurple,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            var logoutFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .tvPillFocusable(
                                        onFocusChanged = { logoutFocused = it },
                                        onClick = {
                                            scope.launch {
                                                wyQrJob?.cancelAndJoin()
                                                NcmApi.logout()
                                                onAccountChanged()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "退出网易云账号",
                                    color = if (logoutFocused) PixelPlayPurpleDark else PixelPlayRed,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(qrSize)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = wyQrBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "网易云二维码",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    if (wyQrJob?.isActive == true && wyQrStatus.contains("正在获取")) CircularProgressIndicator(color = PixelPlayPurpleDark)
                                    else Text(stringResource(R.string.tv_qr_unavailable), color = PixelPlayPurpleDark, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "手机网易云扫码登录",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = wyQrStatus,
                                    color = PixelPlayGold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                var refFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .tvPillFocusable(
                                            onFocusChanged = { refFocused = it },
                                            onClick = { startWyQrLogin() }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "刷新二维码",
                                        color = if (refFocused) PixelPlayPurpleDark else Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. QQ音乐
                1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(minOf(qrSize, 144.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = qqQrBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "QQ扫码",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCode,
                                        contentDescription = null,
                                        tint = PixelPlayPurpleDark,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (MultiPlatformAccountManager.isQqLoggedIn) {
                                        "已绑定：${SessionManager.qqMusicNickname}"
                                    } else {
                                        "手机 QQ 扫码或输入 QQ 号绑定"
                                    },
                                    color = if (MultiPlatformAccountManager.isQqLoggedIn) PixelPlayGreen else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = qqQrStatus,
                                    color = PixelPlayGold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var refF by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .tvPillFocusable(
                                                onFocusChanged = { refF = it },
                                                onClick = { startQqQrLogin() }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "刷新二维码",
                                            color = if (refF) PixelPlayPurpleDark else Color.White,
                                            fontSize = 16.sp
                                        )
                                    }
                                    if (MultiPlatformAccountManager.isQqLoggedIn) {
                                        var outF by remember { mutableStateOf(false) }
                                        Box(
                                            modifier = Modifier
                                                .tvPillFocusable(
                                                    onFocusChanged = { outF = it },
                                                    onClick = {
                                                        qqQrJob?.cancel()
                                                        MultiPlatformAccountManager.logoutQq()
                                                        qqInputUin = ""
                                                        onAccountChanged()
                                                    }
                                                )
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "解绑",
                                                color = if (outF) PixelPlayPurpleDark else PixelPlayRed,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // QQ号直连输入栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TvTextInput(qqInputUin, { qqInputUin = it }, stringResource(R.string.tv_qq_number), Modifier.weight(1f),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))

                            var bindF by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .tvPillFocusable(
                                        isPrimary = true,
                                        onFocusChanged = { bindF = it },
                                        onClick = {
                                            if (qqInputUin.isNotBlank()) {
                                                MultiPlatformAccountManager.bindQqByUin(qqInputUin)
                                                Toast.makeText(context, "已绑定 QQ 公开资料： $qqInputUin", Toast.LENGTH_SHORT).show()
                                                onAccountChanged()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = "绑定QQ号",
                                    color = if (bindF) PixelPlayPurpleDark else Color.White,
                                    fontSize = 16.sp,
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
