package com.cutedino.xiangsuplayer.core.source.market

import android.content.Context
import android.util.AtomicFile
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.ISoundSourceAdapter
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.SourceEnablement
import com.cutedino.xiangsuplayer.core.source.SourceHttpException
import com.cutedino.xiangsuplayer.core.source.SourceNetwork
import com.cutedino.xiangsuplayer.data.session.SessionManager
import com.cutedino.xiangsuplayer.core.source.StreamResult
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import net.moriafly.ncm.readResponse
import net.moriafly.ncm.readBounded
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class MarketRelease(
    val id: Long,
    val tagName: String,
    val name: String,
    val publishedAt: String,
    val body: String,
    val assets: List<MarketAsset>
)

data class MarketAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String
)

data class InstalledLxSource(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val fileName: String,
    val apiBaseUrl: String = "",
    val apiKey: String = ""
)

/**
 * 落雪 (LX Music) 音源市场与脚本解析引擎（移植自主项目 SourceMarketRepository.kt）
 * 支持：
 * 1. 从 GitHub 音源仓库 guoyue2010/lxmusic- 获取最新音源包 Release
 * 2. 下载并解析 .zip / .js 落雪音源脚本头注释 (@name, @version, @author, @description)
 * 3. 提取脚本内 API 端点或结合跨平台直链，注册为动态音源适配器参与并发竞速
 */
class SourceAlreadyInstalledException : IllegalArgumentException("同名音源已安装")

object SourceMarketRepository {

    private const val RELEASES_API = "https://api.github.com/repos/guoyue2010/lxmusic-/releases?per_page=10"
    private val GITHUB_MIRRORS = listOf(
        "",
        "https://ghfast.top/",
        "https://gh-proxy.com/"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }

    private val headerBlockRegex = Regex("""/\*!([\s\S]*?)\*/""")
    private val metaLineRegex = Regex("""^\s*\*?\s*@(\w+)\s+(.+?)\s*$""", RegexOption.MULTILINE)


    private const val MAX_DOWNLOAD = 4 * 1024 * 1024
    private const val MAX_SCRIPT = 256 * 1024
    private const val MAX_EXPANDED = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 64
    private var sourcesDir: File? = null
    @Volatile private var installedSources: List<InstalledLxSource> = emptyList()

    fun init(context: Context) {
        val dir = File(context.filesDir, "lx_sources")
        dir.mkdirs()
        sourcesDir = dir
        reloadInstalledSources()
    }

    fun getInstalledSources(): List<InstalledLxSource> = installedSources

    @Synchronized
    fun reloadInstalledSources() {
        val dir = sourcesDir ?: return
        installedSources = sourceFiles(dir).map { (name, file) ->
            val text = runCatching {
                file.inputStream().use { it.readBounded(MAX_SCRIPT).toString(Charsets.UTF_8) }
            }.getOrDefault("")
            parseLxScriptContent(name, text)
        }.sortedBy { it.fileName }
        val disabled = SessionManager.disabledLxSources
        val migrated = installedSources.filter { legacyId(it.fileName) in disabled }.map { it.id }
        val enabledState = disabled.filterTo(mutableSetOf()) { it.startsWith("lx_file-") } + migrated
        if (enabledState != disabled) SessionManager.disabledLxSources = enabledState
        SoundSourceRepository.disabledLxSourceIds = enabledState
        SoundSourceRepository.registerDynamicAdapters(installedSources.map(::LxScriptSourceAdapter))
    }

    private fun sourceFiles(dir: File): Map<String, File> {
        val primary = AtomicFile(File(dir, "installed.json"))
        val fallback = AtomicFile(File(dir, "installed.last-good.json"))
        fun exists(file: AtomicFile) = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        if (!exists(primary) && !exists(fallback)) {
            return dir.listFiles { file -> file.isFile && file.name.endsWith(".js", true) }
                .orEmpty().associateBy { it.name }
        }
        return readIndex(dir, primary) ?: readIndex(dir, fallback).orEmpty()
    }

    private fun readIndex(dir: File, index: AtomicFile): Map<String, File>? = try {
        val json = index.openRead().use { JSONObject(it.readBounded(1024 * 1024).toString(Charsets.UTF_8)) }
        json.keys().asSequence().associateWith { name ->
            scriptName(name)
            val path = json.getString(name)
            require(path.matches(Regex("""(?:payloads/[a-f0-9-]+|[^/\\]+\.js)""", RegexOption.IGNORE_CASE)))
            File(dir, path).also { require(it.isFile) }
        }
    } catch (_: java.io.FileNotFoundException) {
        null
    } catch (failure: Exception) {
        android.util.Log.w("SourceMarket", "Invalid installed source index: ${index.baseFile.name}", failure)
        null
    }

    private fun writeIndex(dir: File, sources: Map<String, File>) {
        val data = JSONObject()
        sources.forEach { (name, file) -> data.put(name, file.relativeTo(dir).invariantSeparatorsPath) }
        val bytes = data.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1024 * 1024) { "音源索引超过大小上限" }
        writeAtomically(AtomicFile(File(dir, "installed.json")), bytes)
        try {
            writeAtomically(AtomicFile(File(dir, "installed.last-good.json")), bytes)
            val retained = sources.values.toSet()
            File(dir, "payloads").listFiles()?.forEach {
                if (it !in retained && it.isFile && it.name.matches(Regex("[a-f0-9-]+"))) it.delete()
            }
        } catch (failure: Exception) {
            // Keep the previous payloads while an older recovery index may still refer to them.
            android.util.Log.w("SourceMarket", "Could not update the recovery index", failure)
        }
    }

    private fun writeAtomically(index: AtomicFile, bytes: ByteArray) {
        val out = index.startWrite()
        try {
            out.write(bytes)
            index.finishWrite(out)
        } catch (failure: Exception) {
            index.failWrite(out)
            throw failure
        }
    }

    @Synchronized
    private fun publish(scripts: Map<String, String>, replace: Boolean): List<InstalledLxSource> {
        require(scripts.isNotEmpty()) { "音源包没有可导入的 JS 文件" }
        val dir = checkNotNull(sourcesDir)
        val previous = sourceFiles(dir)
        if (!replace && scripts.keys.any(previous::containsKey)) throw SourceAlreadyInstalledException()
        val staged = mutableMapOf<String, File>()
        try {
            val payloads = File(dir, "payloads").apply { mkdirs() }
            for ((name, content) in scripts) {
                val file = File(payloads, java.util.UUID.randomUUID().toString())
                staged[name] = file
                file.outputStream().use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
            }
            writeIndex(dir, previous + staged)
        } catch (failure: Exception) {
            staged.values.forEach { it.delete() }
            throw failure
        }
        reloadInstalledSources()
        return installedSources.filter { it.fileName in scripts }
    }

    suspend fun fetchMarketReleases(): Result<List<MarketRelease>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PixelPlay-TV/2.1")
                .build()
            val body = httpClient.newCall(req).readResponse { resp ->
                if (resp.isSuccessful) resp.body?.readBounded(2 * 1024 * 1024)?.toString(Charsets.UTF_8) else null
            }

            if (!body.isNullOrBlank()) {
                val arr = JSONArray(body)
                val list = ArrayList<MarketRelease>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
                    val assets = ArrayList<MarketAsset>()
                    for (j in 0 until assetsArr.length()) {
                        val a = assetsArr.optJSONObject(j) ?: continue
                        val name = a.optString("name")
                        if (name.endsWith(".zip", true) || name.endsWith(".js", true)) {
                            assets.add(
                                MarketAsset(
                                    id = a.optLong("id", j.toLong()),
                                    name = name,
                                    size = a.optLong("size", 0L),
                                    downloadUrl = a.optString("browser_download_url")
                                )
                            )
                        }
                    }
                    if (assets.isNotEmpty()) {
                        list.add(
                            MarketRelease(
                                id = obj.optLong("id", i.toLong()),
                                tagName = obj.optString("tag_name", "v1.0"),
                                name = obj.optString("name").ifBlank { obj.optString("tag_name") },
                                publishedAt = obj.optString("published_at").take(10),
                                body = obj.optString("body"),
                                assets = assets
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return@runCatching list
            }
            emptyList()
        }.recover {
            emptyList()
        }
    }


    suspend fun installAsset(asset: MarketAsset, replace: Boolean = false): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(asset.size <= MAX_DOWNLOAD) { "音源包超过大小上限" }
                val bytes = GITHUB_MIRRORS.firstNotNullOfOrNull { mirror ->
                    try {
                        download(mirror + asset.downloadUrl, MAX_DOWNLOAD)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: java.io.IOException) {
                        null
                    }
                } ?: error("音源包下载失败")
                val scripts = if (asset.name.endsWith(".zip", true)) {
                    unzipScripts(bytes)
                } else {
                    require(bytes.size <= MAX_SCRIPT) { "脚本超过大小上限" }
                    mapOf(scriptName(asset.name) to bytes.toString(Charsets.UTF_8))
                }
                publish(scripts, replace).size
            }
        }

    suspend fun importFromUrl(url: String, replace: Boolean = false): Result<InstalledLxSource> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = requireNotNull(SourceNetwork.httpsUrl(url.trim())) { "仅支持 HTTPS 音源" }
                val name = endpoint.pathSegments.last().let {
                    if (it.endsWith(".js", true)) scriptName(it) else "custom_${System.currentTimeMillis()}.js"
                }
                val bytes = download(endpoint.toString(), MAX_SCRIPT) ?: error("音源下载失败")
                publish(mapOf(name to bytes.toString(Charsets.UTF_8)), replace).single()
            }
        }

    private suspend fun download(url: String, limit: Int): ByteArray? {
        val endpoint = requireNotNull(SourceNetwork.httpsUrl(url)) { "仅支持 HTTPS 音源" }
        val request = Request.Builder().url(endpoint).build()
        return httpClient.newCall(request).readResponse { response ->
            response.body?.takeIf { response.isSuccessful }?.let { body ->
                require(body.contentLength() <= limit) { "音源包超过大小上限" }
                body.byteStream().readBounded(limit)
            }
        }
    }

    private fun unzipScripts(bytes: ByteArray): Map<String, String> {
        val scripts = linkedMapOf<String, String>()
        var count = 0
        var remaining = MAX_EXPANDED
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= MAX_ENTRIES) { "音源包文件过多" }
                val body = zip.readBounded(minOf(remaining, MAX_SCRIPT))
                remaining -= body.size
                if (!entry.isDirectory && entry.name.endsWith(".js", true)) {
                    val name = scriptName(entry.name.substringAfterLast('/'))
                    require(name !in scripts) { "音源包内存在同名文件" }
                    scripts[name] = body.toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        return scripts
    }

    private fun scriptName(name: String): String {
        require(name.length in 4..200 && name.endsWith(".js", true) &&
            name.none { it == '/' || it == '\\' || it.isISOControl() }) { "无效的脚本文件名" }
        return name
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= limit) { "音源包超过大小上限" }
            output.write(buffer, 0, count)
        }
    }

    @Synchronized
    fun removeInstalledSource(sourceId: String) {
        val dir = sourcesDir ?: return
        val target = installedSources.firstOrNull { it.id == sourceId } ?: return
        val files = sourceFiles(dir)
        writeIndex(dir, files - target.fileName)
        reloadInstalledSources()
    }

    private fun legacyId(fileName: String): String =
        SourceEnablement.LX_PREFIX + fileName.removeSuffix(".js").replace(Regex("[^a-zA-Z0-9_]"), "_")

    internal fun stableId(fileName: String): String = "lx_file-" +
        MessageDigest.getInstance("SHA-256").digest(fileName.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun parseLxScriptContent(fileName: String, content: String): InstalledLxSource {
        val meta = mutableMapOf<String, String>()
        val headerMatch = headerBlockRegex.find(content)
        if (headerMatch != null) {
            val block = headerMatch.groupValues[1]
            for (m in metaLineRegex.findAll(block)) {
                meta[m.groupValues[1].lowercase()] = m.groupValues[2].trim()
            }
        }
        val name = meta["name"] ?: fileName.removeSuffix(".js")
        val version = meta["version"] ?: "1.0.0"
        val author = meta["author"] ?: "LX Community"
        val desc = meta["description"] ?: "落雪自定义音源脚本"

        val apiBase = meta["apibaseurl"]?.let(SourceNetwork::httpsUrl)
            ?.takeIf { it.query == null && it.fragment == null }?.toString().orEmpty()
        val id = stableId(fileName)
        return InstalledLxSource(
            id = id,
            name = name,
            version = version,
            author = author,
            description = desc,
            fileName = fileName,
            apiBaseUrl = apiBase
        )
    }

}

class LxScriptSourceAdapter(
    private val lxSource: InstalledLxSource
) : ISoundSourceAdapter {
    override val sourceId: String = lxSource.id
    val compatible: Boolean get() = lxSource.apiBaseUrl.isNotBlank()
    override fun supports(song: Song): Boolean = compatible
    override val sourceName: String = lxSource.name + if (compatible) "" else " · 不兼容"
    override val priority: Int = 88

    private val client = OkHttpClient.Builder().followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> {
        return BuiltInSourceSearchApi.searchSongs("all", query, page, limit)
    }

    override suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {

                val base = requireNotNull(SourceNetwork.httpsUrl(lxSource.apiBaseUrl)) { "缺少有效的 @apiBaseUrl 配置" }
                val qualityName = when (quality) {
                    AudioQuality.HIRES -> "flac24bit"
                    AudioQuality.LOSSLESS -> "flac"
                    AudioQuality.HIGH -> "320k"
                    AudioQuality.STANDARD -> "128k"
                }
                val endpoint = base.newBuilder()
                    .addPathSegment("url").addPathSegment(song.key.sourceId)
                    .addPathSegment(song.stableProviderId).addPathSegment(qualityName)
                    .build()
                val request = Request.Builder().url(endpoint).build()
                val body = client.newCall(request).readResponse { response ->
                    if (!response.isSuccessful) throw SourceHttpException(response.code)
                    response.body?.readBounded(256 * 1024)?.toString(Charsets.UTF_8).orEmpty()
                }
                val json = JSONObject(body)
                val url = json.optString("url").ifBlank { json.optJSONObject("data")?.optString("url").orEmpty() }
                checkNotNull(SourceNetwork.httpsUrl(url)) { "音源未返回 HTTPS 播放地址" }
                StreamResult.Full(url, sourceName = sourceName)
            }
        }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> {
        return SoundSourceRepository.neteaseAdapter.getLyrics(songId)
    }
}
