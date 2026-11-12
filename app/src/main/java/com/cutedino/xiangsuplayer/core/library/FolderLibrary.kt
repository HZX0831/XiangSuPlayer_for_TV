package com.cutedino.xiangsuplayer.core.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.ResolvedStream
import com.cutedino.xiangsuplayer.core.source.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

internal data class LibraryFolder(val id: String, val name: String, val location: String, val network: Boolean)
internal data class FolderEntry(val id: String, val name: String, val song: Song? = null)
internal data class FolderPage(val entries: List<FolderEntry>, val more: Boolean, val incomplete: Boolean = false)
private data class DirectoryItem(val id: String, val name: String, val directory: Boolean)
private data class DirectoryListing(val items: List<DirectoryItem>, val incomplete: Boolean = false)

internal object FolderLibrary {
    const val PAGE_SIZE = 24
    private val changes = Mutex()
    private val supported = setOf("mp3", "flac", "m4a")
    private const val LOCAL = "local:"
    private const val DAV = "webdav:"
    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )

    private fun prefs(context: Context) = context.getSharedPreferences("folder_libraries", Context.MODE_PRIVATE)
    fun isFolderSong(song: Song) = song.source.startsWith(LOCAL) || song.source.startsWith(DAV)

    fun folders(context: Context): List<LibraryFolder> = prefs(context).all.mapNotNull { (id, stored) ->
        val value = (stored as? String)?.split('\n', limit = 3) ?: return@mapNotNull null
        if (value.size != 3) return@mapNotNull null
        LibraryFolder(id, value[1], value[2], value[0] == "dav")
    }.sortedBy { it.name }

    suspend fun addLocal(context: Context, tree: Uri): LibraryFolder = withContext(Dispatchers.IO) {
        changes.withLock {
            folders(context).firstOrNull { !it.network && it.location == tree.toString() }?.let { existing ->
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                return@withLock existing
            }
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val label = context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }.orEmpty().ifBlank { "本地目录" }
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val folder = LibraryFolder(UUID.randomUUID().toString(), label, tree.toString(), false)
            try {
                save(context, folder)
                folder
            } catch (failure: Exception) {
                context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                throw failure
            }
        }
    }

    suspend fun addDav(context: Context, name: String, url: String, user: String, password: String): LibraryFolder =
        withContext(Dispatchers.IO) {
            val root = requireNotNull(WebDavClient.root(url)) { "请输入有效的 HTTPS WebDAV 根目录" }
            require(':' !in user) { "用户名不能包含冒号" }
            val auth = if (user.isBlank()) null else okhttp3.Credentials.basic(user, password, Charsets.UTF_8)
            WebDavClient.list(root, root, auth)
            changes.withLock {
                val existing = folders(context).firstOrNull { it.network && it.location == root.toString() }
                val folder = LibraryFolder(existing?.id ?: UUID.randomUUID().toString(),
                    name.ifBlank { root.host }, root.toString(), true)
                val previous = runCatching { FolderCredentials.get(context, folder.id) }.getOrNull()
                FolderCredentials.save(context, folder.id, user, password)
                try {
                    save(context, folder)
                } catch (failure: Exception) {
                    FolderCredentials.saveAuthorization(context, folder.id, previous)
                    throw failure
                }

                folder
            }
        }

    suspend fun remove(context: Context, folder: LibraryFolder) {
        withContext(Dispatchers.IO) {
            changes.withLock {
                check(prefs(context).edit().remove(folder.id).commit()) { "无法移除目录" }

                if (folder.network) FolderCredentials.remove(context, folder.id)
                else runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(folder.location), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }
    }

    private fun save(context: Context, folder: LibraryFolder) {
        val type = if (folder.network) "dav" else "local"
        val name = folder.name.replace('\n', ' ').replace('\r', ' ').take(160)
        check(prefs(context).edit().putString(folder.id, "$type\n$name\n${folder.location}").commit()) { "无法保存目录" }
    }

    class Browse(private val folder: LibraryFolder, private val directory: String?) {
        private val listing = FolderListingCache<DirectoryListing>()

        suspend fun page(context: Context, page: Int): FolderPage =
            FolderLibrary.page(context, folder, directory, page, listing)
    }

    private suspend fun page(
        context: Context,
        folder: LibraryFolder,
        directory: String?,
        page: Int,
        listing: FolderListingCache<DirectoryListing>
    ): FolderPage = withContext(Dispatchers.IO) {
        require(page in 0..Int.MAX_VALUE / PAGE_SIZE)
        val offset = page * PAGE_SIZE
        val result = listing.get {
            val raw = if (folder.network) davListing(context, folder, directory) else localListing(context, folder, directory)
            raw.copy(items = raw.items.distinctBy { it.id }.sortedWith(
                compareBy<DirectoryItem> { !it.directory }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.id }
            ))
        }
        val entries = result.items.drop(offset).take(PAGE_SIZE).map { item ->
            ensureActive()
            if (item.directory) return@map FolderEntry(item.id, item.name)
            val source = (if (folder.network) DAV else LOCAL) + folder.id
            val fallback = Song(item.id, item.name.substringBeforeLast('.'), "", "", source = source)
            val song = if (folder.network) fallback else {
                val document = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(folder.location), item.id)
                tags(context, document, fallback)
            }
            FolderEntry(item.id, item.name, song)
        }
        FolderPage(entries, offset.toLong() + PAGE_SIZE < result.items.size, result.incomplete)
    }

    private suspend fun davListing(context: Context, folder: LibraryFolder, directory: String?): DirectoryListing {
        val root = requireNotNull(WebDavClient.root(folder.location))
        val target = directory?.let { requireNotNull(WebDavClient.child(root, root, it)) } ?: root
        val targetDirectory = requireNotNull(WebDavClient.root(target.toString()))
        val listing = WebDavClient.list(root, targetDirectory, FolderCredentials.get(context, folder.id))
        return DirectoryListing(listing.items.filter { it.directory || supported(it.name) || supported(it.url.encodedPath) }
            .map { DirectoryItem(it.url.encodedPath, it.name, it.directory) }, listing.incomplete)
    }

    private suspend fun localListing(context: Context, folder: LibraryFolder, directory: String?): DirectoryListing {
        val tree = Uri.parse(folder.location)
        val treeId = directory ?: DocumentsContract.getTreeDocumentId(tree)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
            ?: error("目录不可访问，请检查 U 盘或目录授权")
        val items = mutableListOf<DirectoryItem>()
        cursor.use {
            while (cursor.moveToNext()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1).orEmpty().ifBlank { id.substringAfterLast('/') }
                val isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDirectory || supported(name)) {
                    if (items.size >= 16_000) return DirectoryListing(items, incomplete = true)
                    items.add(DirectoryItem(id, name, isDirectory))
                }
            }
        }
        return DirectoryListing(items)
    }

    suspend fun resolve(context: Context, song: Song, quality: AudioQuality): ResolvedStream = withContext(Dispatchers.IO) {
        val folder = folders(context).firstOrNull { it.id == song.source.substringAfter(':') }
            ?: error("目录已移除")
        if (folder.network) {
            require(song.source.startsWith(DAV))
            val root = requireNotNull(WebDavClient.root(folder.location))
            val url = requireNotNull(WebDavClient.child(root, root, song.id))
            val auth = FolderCredentials.get(context, folder.id)
            val seekable = WebDavClient.canSeek(url, auth)
            ResolvedStream(song.source, quality, StreamResult.Full(url.toString(), sourceName = folder.name),
                requestHeaders = if (auth == null) emptyMap() else mapOf("Authorization" to auth), seekable = seekable)
        } else {
            require(song.source.startsWith(LOCAL))
            val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(folder.location), song.id)
            ResolvedStream(song.source, quality, StreamResult.Full(uri.toString(), sourceName = folder.name))
        }
    }

    private fun supported(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in supported

    private fun tags(context: Context, uri: Uri, fallback: Song): Song {
        val reader = MediaMetadataRetriever()
        return try {
            reader.setDataSource(context, uri)
            fallback.copy(
                title = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank) ?: fallback.title,
                artist = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            )
        } catch (_: Exception) {
            fallback
        } finally {
            runCatching { reader.release() }
        }
    }
}
