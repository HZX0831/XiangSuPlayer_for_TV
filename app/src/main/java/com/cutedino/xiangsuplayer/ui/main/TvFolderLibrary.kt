package com.cutedino.xiangsuplayer.ui.main

import com.cutedino.xiangsuplayer.ui.components.TvTextInput

import androidx.compose.runtime.livedata.observeAsState

import com.cutedino.xiangsuplayer.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.library.FolderEntry
import com.cutedino.xiangsuplayer.core.library.FolderLibrary
import com.cutedino.xiangsuplayer.core.library.FolderPage
import com.cutedino.xiangsuplayer.core.library.LibraryFolder
import com.cutedino.xiangsuplayer.ui.components.TvTextAction
import com.cutedino.xiangsuplayer.ui.components.TvStatusText
import com.cutedino.xiangsuplayer.ui.theme.tryRequestFocus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TvFolderLibrary(network: Boolean) {
    val currentSong by PlayerController.currentSong.observeAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folders by remember(network) { mutableStateOf(FolderLibrary.folders(context).filter { it.network == network }) }
    var folder by remember(network) { mutableStateOf<LibraryFolder?>(null) }
    var path by remember(network) { mutableStateOf(emptyList<FolderEntry>()) }
    var page by remember(network) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var listing by remember { mutableStateOf<FolderPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var configure by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<LibraryFolder?>(null) }
    var focusEntry by remember(network) { mutableStateOf(true) }
    var returnFolderId by remember(network) { mutableStateOf<String?>(null) }
    val returnFolderFocus = remember { FocusRequester() }
    val addFocus = remember { FocusRequester() }
    val upFocus = remember { FocusRequester() }
    val openFolder: (LibraryFolder) -> Unit = {
        folders = FolderLibrary.folders(context).filter { it.network == network }
        returnFolderId = it.id
        folder = it
        path = emptyList()
        page = 0
        retry++
        focusEntry = true
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                openFolder(FolderLibrary.addLocal(context, uri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "无法取得目录授权，请重新选择目录"
            }
        }
    }
    fun up() {
        page = 0
        error = null
        if (path.isNotEmpty()) path = path.dropLast(1) else folder = null
        focusEntry = true
    }
    BackHandler(folder != null) { up() }

    val browse = remember(folder, path, retry) {
        folder?.let { FolderLibrary.Browse(it, path.lastOrNull()?.id) }
    }
    LaunchedEffect(browse, page) {
        listing = null
        val session = browse ?: return@LaunchedEffect
        error = null
        try {
            listing = session.page(context, page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = if (network) "无法读取 WebDAV，请检查网络和账号" else "无法读取目录，请检查目录授权或 U 盘"
        }
    }
    LaunchedEffect(folder, path, configure, remove, focusEntry) {
        if (focusEntry && !configure && remove == null) {
            withFrameNanos { }
            if (folder == null) {
                if (returnFolderId == null || !returnFolderFocus.tryRequestFocus()) addFocus.tryRequestFocus()
            } else upFocus.tryRequestFocus()
            focusEntry = false
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val selected = folder
        if (selected == null) {
            TvTextAction(if (network) stringResource(R.string.tv_webdav_add) else stringResource(R.string.tv_local_authorize), Modifier.focusRequester(addFocus)) {
                error = null
                if (network) configure = true
                else try { picker.launch(null) } catch (_: android.content.ActivityNotFoundException) {
                    error = "此设备没有可用的系统目录选择器"
                }
            }
            error?.let { TvStatusText(it, error = true) }
            if (folders.isEmpty() && error == null) Text(if (network) "尚未添加网络目录" else "请选择内置存储或 U 盘中的音乐目录")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(folders, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvTextAction(item.name, Modifier.weight(1f).then(if (item.id == returnFolderId) Modifier.focusRequester(returnFolderFocus) else Modifier)) { openFolder(item) }
                        TvTextAction(stringResource(R.string.tv_remove)) { returnFolderId = item.id; remove = item }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TvTextAction(stringResource(R.string.tv_directory_back), Modifier.focusRequester(upFocus)) { up() }
                Text(path.lastOrNull()?.name ?: selected.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (page > 0) TvTextAction("上一页") { page--; focusEntry = true }
                if (listing?.more == true) TvTextAction("下一页") { page++; focusEntry = true }
            }
            val result = listing
            if (result?.incomplete == true) {
                TvStatusText(stringResource(R.string.tv_directory_incomplete))
                TvTextAction("重试") { page = 0; retry++; focusEntry = true }
            }
            when {
                error != null -> {
                    TvStatusText(error.orEmpty(), error = true)
                    TvTextAction("重试") { page = 0; retry++; focusEntry = true }
                }
                result == null -> TvStatusText(stringResource(R.string.tv_directory_loading))
                result.entries.isEmpty() -> Text("此目录没有 MP3、FLAC 或 M4A")
                else -> {
                    val songs = remember(result) { result.entries.mapNotNull { it.song } }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(result.entries, key = { it.id }) { entry ->
                            val song = entry.song
                            if (song != null) TvCompactSongCard(song, Modifier.fillMaxWidth(), isCurrent = currentSong?.key == song.key) {
                                PlayerController.playSong(song, songs)
                            }
                            else TvTextAction("目录 · ${entry.name}", Modifier.fillMaxWidth()) {
                                path = path + entry
                                page = 0
                                focusEntry = true
                            }
                        }
                    }
                }
            }
        }
    }
    if (configure) WebDavConfigDialog(
        close = { configure = false; focusEntry = true },
        save = { name, address, user, password ->
            val result = FolderLibrary.addDav(context, name.trim(), address, user.trim(), password)
            configure = false
            openFolder(result)
        }
    )
    remove?.let { item ->
        val cancelFocus = remember { FocusRequester() }
        val dismiss = { remove = null; focusEntry = true }
        Dialog(onDismissRequest = dismiss) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("移除目录「${item.name}」？")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvTextAction("取消", Modifier.focusRequester(cancelFocus), onClick = dismiss)
                        TvTextAction(stringResource(R.string.tv_remove)) {
                            dismiss()
                            scope.launch {
                                try {
                                    val index = folders.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                                    FolderLibrary.remove(context, item)
                                    folders = FolderLibrary.folders(context).filter { it.network == network }
                                    returnFolderId = folders.getOrNull(index.coerceAtMost((folders.size - 1).coerceAtLeast(0)))?.id
                                    focusEntry = true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = "无法移除目录"
                                }
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { cancelFocus.tryRequestFocus() }
        }
    }
}

@Composable
private fun WebDavConfigDialog(
    close: () -> Unit,
    save: suspend (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val addressFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = close) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.widthIn(max = 450.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.tv_webdav_configure), style = MaterialTheme.typography.titleLarge)
                TvTextInput(address, { address = it.take(1024) }, stringResource(R.string.tv_dav_address), Modifier.fillMaxWidth().focusRequester(addressFocus))
                TvTextInput(name, { name = it.take(100) }, stringResource(R.string.tv_dav_name), Modifier.fillMaxWidth())
                TvTextInput(user, { user = it.take(160) }, stringResource(R.string.tv_dav_user), Modifier.fillMaxWidth())
                TvTextInput(password, { password = it.take(256) }, stringResource(R.string.tv_dav_password), Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvTextAction("取消", onClick = close)
                    TvTextAction(if (saving) "正在验证…" else "验证并保存", busy = saving) {
                        if (!saving) scope.launch {
                            saving = true
                            error = null
                            try {
                                save(name, address, user, password)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "无法连接，请检查 HTTPS 地址、账号和服务器证书"
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { addressFocus.tryRequestFocus() }
    }
}
