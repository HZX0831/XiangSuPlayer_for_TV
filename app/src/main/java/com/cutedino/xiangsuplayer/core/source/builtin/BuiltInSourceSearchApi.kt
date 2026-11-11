package com.cutedino.xiangsuplayer.core.source.builtin

import android.util.Base64
import com.cutedino.xiangsuplayer.core.audio.LyricEngine
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import okhttp3.MediaType.Companion.toMediaType
import com.cutedino.xiangsuplayer.core.source.SourceNetwork
import kotlinx.coroutines.CancellationException
import net.moriafly.ncm.readResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 5大内置平台聚合搜索、音源直链与双语歌词引擎（移植自主项目 BuiltInSourceSearchApi.kt）
 * 支持平台：
 * - all: 聚合并发搜索
 * - wy: 网易云音乐
 * - tx: QQ音乐
 * - kg: 酷狗音乐
 * - kw: 酷我音乐
 * - mg: 咪咕音乐
 */
object BuiltInSourceSearchApi {

    enum class MusicPlatform(val key: String, val label: String) {
        ALL("all", "全网聚合"),
        WY("wy", "网易云"),
        TX("tx", "QQ音乐"),
        KG("kg", "酷狗"),
        KW("kw", "酷我"),
        MG("mg", "咪咕");

        companion object {
            fun fromKey(key: String): MusicPlatform =
                entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WY
        }
    }

    private val httpClient: OkHttpClient by lazy {
        SourceNetwork.configure(OkHttpClient.Builder())
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 按平台搜索歌曲（支持 all 全网并发聚合）
     */
    suspend fun searchSongs(
        platformKey: String,
        keyword: String,
        page: Int = 1,
        limit: Int = 30
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = keyword.trim()
            if (trimmed.isEmpty()) return@runCatching emptyList()

            when (platformKey.lowercase()) {
                "all" -> coroutineScope {
                    val perLimit = (limit / 3).coerceAtLeast(10)
                    val wyJob = async { runCatching { searchWy(trimmed, page, perLimit) } }
                    val txJob = async { runCatching { searchTx(trimmed, page, perLimit) } }
                    val kgJob = async { runCatching { searchKg(trimmed, page, perLimit) } }
                    val kwJob = async { runCatching { searchKw(trimmed, page, perLimit) } }
                    val results = awaitAll(wyJob, txJob, kgJob, kwJob)
                    if (results.all { it.isFailure }) results.first().getOrThrow()
                    val lists = results.map { it.getOrDefault(emptyList()) }
                    // 交错合并各平台结果，使前排展示包含各平台音源
                    val merged = mutableListOf<Song>()
                    val maxLen = lists.maxOfOrNull { it.size } ?: 0
                    for (i in 0 until maxLen) {
                        for (list in lists) {
                            if (i < list.size) merged.add(list[i])
                        }
                    }
                    merged
                }
                "wy" -> searchWy(trimmed, page, limit)
                "tx" -> searchTx(trimmed, page, limit)
                "kg" -> searchKg(trimmed, page, limit)
                "kw" -> searchKw(trimmed, page, limit)
                "mg" -> searchMg(trimmed, page, limit)
                else -> searchWy(trimmed, page, limit)
            }
        }
    }

    // ─── 1. 网易云 (wy) ────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private suspend fun searchWy(keyword: String, page: Int, limit: Int): List<Song> {
        val offset = (page - 1) * limit
        val res = NcmApi.search(keyword = keyword, offset = offset, limit = limit)
        val detailMap = res.getOrThrow()
        val resultObj = detailMap.ncmObj("result")
        val songsList = resultObj.ncmList("songs")
        val songIds = songsList.mapNotNull { (it as? Map<String, Any?>)?.ncmLong("id")?.toString() }
            .filter { it.isNotBlank() }

        if (songIds.isNotEmpty()) {
            val detailRes = NcmApi.songDetail(songIds)
            val detailSongs = detailRes.getOrNull()?.ncmList("songs").orEmpty()
            if (detailSongs.isNotEmpty()) {
                return detailSongs.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
            }
        }
        return songsList.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
    }

    // ─── 2. QQ音乐 (tx) ────────────────────────────────────────────────────

    private suspend fun searchTx(keyword: String, page: Int, limit: Int): List<Song> {
        val payload = JSONObject().apply {
            put("comm", JSONObject().apply {
                put("ct", "19")
                put("cv", "1859")
                put("uin", "0")
            })
            put("req", JSONObject().apply {
                put("method", "DoSearchForQQMusicDesktop")
                put("module", "music.search.SearchCgiService")
                put("param", JSONObject().apply {
                    put("grp", 1)
                    put("num_per_page", limit)
                    put("page_num", page)
                    put("query", keyword)
                    put("search_type", 0)
                })
            })
        }

        val req = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://y.qq.com/")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val body = executeString(req) ?: error("搜索服务不可用")
        val list = JSONObject(body)
            .optJSONObject("req")
            ?.optJSONObject("data")
            ?.optJSONObject("body")
            ?.optJSONObject("song")
            ?.optJSONArray("list") ?: return emptyList()

        val result = ArrayList<Song>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val mid = item.optString("mid").ifBlank { item.optString("songmid") }
            if (mid.isBlank()) continue
            val title = item.optString("name").ifBlank { item.optString("title") }
            if (title.isBlank()) continue
            val singers = item.optJSONArray("singer")
            val artist = if (singers != null && singers.length() > 0) {
                (0 until singers.length()).mapNotNull { idx ->
                    singers.optJSONObject(idx)?.optString("name")?.takeIf { it.isNotBlank() }
                }.joinToString(" / ")
            } else "未知歌手"
            val albumObj = item.optJSONObject("album")
            val albumName = albumObj?.optString("name").orEmpty()
            val albumMid = albumObj?.optString("mid").orEmpty()
            val coverUrl = if (albumMid.isNotBlank()) {
                "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg"
            } else ""
            val durationMs = item.optLong("interval", 0L) * 1000L

            result.add(
                Song(
                    id = mid,
                    title = title,
                    artist = artist,
                    album = albumName,
                    coverUrl = coverUrl,
                    durationMs = durationMs,
                    source = "tx",
                    songmid = mid
                )
            )
        }
        return result
    }

    // ─── 3. 酷狗音乐 (kg) ──────────────────────────────────────────────────

    private suspend fun searchKg(keyword: String, page: Int, limit: Int): List<Song> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=$page&pagesize=$limit&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://www.kugou.com/")
            .build()
        val body = executeString(req) ?: error("搜索服务不可用")
        val lists = JSONObject(body)
            .optJSONObject("data")
            ?.optJSONArray("lists") ?: return emptyList()

        val result = ArrayList<Song>(lists.length())
        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val hash = item.optString("FileHash").ifBlank { item.optString("HQFileHash") }
            if (hash.isBlank()) continue
            val songName = item.optString("SongName")
                .replace("<em>", "")
                .replace("</em>", "")
            val singerName = item.optString("SingerName")
                .replace("<em>", "")
                .replace("</em>", "")
                .ifBlank { "未知歌手" }
            val albumName = item.optString("AlbumName")
            val albumId = item.optString("AlbumID")
            val durationMs = item.optLong("Duration", 0L) * 1000L
            val img = item.optString("Image").replace("{size}", "400")

            result.add(
                Song(
                    id = hash,
                    title = songName,
                    artist = singerName,
                    album = albumName,
                    coverUrl = img,
                    durationMs = durationMs,
                    source = "kg",
                    songmid = albumId,
                    hash = hash
                )
            )
        }
        return result
    }

    // ─── 4. 酷我音乐 (kw) ──────────────────────────────────────────────────

    private suspend fun searchKw(keyword: String, page: Int, limit: Int): List<Song> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val pn = (page - 1).coerceAtLeast(0)
        val url = "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=$pn&rn=$limit&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .build()
        val body = executeString(req) ?: error("搜索服务不可用")
        val abslist = JSONObject(body).optJSONArray("abslist") ?: return emptyList()

        val result = ArrayList<Song>(abslist.length())
        for (i in 0 until abslist.length()) {
            val item = abslist.optJSONObject(i) ?: continue
            val rid = item.optString("MUSICRID").removePrefix("MUSIC_")
            if (rid.isBlank()) continue
            val title = decodeHtmlEntities(item.optString("SONGNAME").ifBlank { item.optString("NAME") })
            val artist = decodeHtmlEntities(item.optString("ARTIST")).ifBlank { "未知歌手" }
            val album = decodeHtmlEntities(item.optString("ALBUM"))
            val durationMs = item.optLong("DURATION", 0L) * 1000L
            val picPath = item.optString("web_albumpic_short")
            val coverUrl = if (picPath.isNotBlank()) {
                "https://img4.kuwo.cn/star/albumcover/${picPath.replace("120/", "500/")}"
            } else ""

            result.add(
                Song(
                    id = rid,
                    title = title,
                    artist = artist,
                    album = album,
                    coverUrl = coverUrl,
                    durationMs = durationMs,
                    source = "kw",
                    songmid = rid
                )
            )
        }
        return result
    }

    // ─── 5. 咪咕音乐 (mg) ──────────────────────────────────────────────────

    private suspend fun searchMg(keyword: String, page: Int, limit: Int): List<Song> {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://m.music.migu.cn/migu/remoting/scr_search_tag?rows=$limit&type=2&keyword=$encoded&pgc=$page"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://m.music.migu.cn/")
            .build()
        val body = executeString(req) ?: error("搜索服务不可用")
        val musics = JSONObject(body).optJSONArray("musics") ?: return emptyList()

        val result = ArrayList<Song>(musics.length())
        for (i in 0 until musics.length()) {
            val item = musics.optJSONObject(i) ?: continue
            val copyrightId = item.optString("copyrightId").ifBlank { item.optString("id") }
            if (copyrightId.isBlank()) continue
            val title = item.optString("songName").ifBlank { item.optString("title") }
            val artist = item.optString("singerName").ifBlank { "未知歌手" }
            val album = item.optString("albumName")
            val cover = item.optString("cover")
            val mp3 = item.optString("mp3")

            result.add(
                Song(
                    id = copyrightId,
                    title = title,
                    artist = artist,
                    album = album,
                    coverUrl = cover,
                    durationMs = 0L,
                    source = "mg",
                    songmid = mp3
                )
            )
        }
        return result
    }

    // ─── 跨平台音频直链解析 (用于非 wy 歌曲或 wy 灰歌跨平台匹配) ───────────────

    suspend fun resolvePlatformStream(song: Song, quality: AudioQuality): Result<StreamResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (song.source.lowercase()) {
                    "kw", "kuwo" -> resolveKwStream(song.id, quality)
                    "kg", "kugou" -> resolveKgStream(song.hash.ifBlank { song.id }, song.songmid)
                    "tx", "qq" -> resolveTxStream(song.songmid.ifBlank { song.id }, song, quality)
                    "mg", "migu" -> resolveMgStream(song, quality)
                    else -> {
                        // 对于网易云灰歌或未命中歌曲，通过「歌名 + 歌手」自动在酷我/酷狗跨平台匹配完整版音源
                        resolveByCrossPlatformMatch(song, quality)
                    }
                }
            }
        }

    suspend fun resolveByCrossPlatformMatch(song: Song, quality: AudioQuality): StreamResult {
        val query = "${song.title} ${song.artist.split("/").firstOrNull().orEmpty()}".trim()
        // 1. 优先尝试酷我直链
        val kwCandidates = runCatching { searchKw(query, 1, 5) }.getOrDefault(emptyList())
        for (candidate in kwCandidates.filter { matchesRecording(song, it) }) {
            val stream = runCatching { resolveKwStream(candidate.id, quality) }.getOrNull()
            if (stream != null) return stream
        }
        // 2. 备选酷狗直链
        val kgCandidates = runCatching { searchKg(query, 1, 5) }.getOrDefault(emptyList())
        for (candidate in kgCandidates.filter { matchesRecording(song, it) }) {
            val stream = runCatching { resolveKgStream(candidate.hash.ifBlank { candidate.id }, candidate.songmid) }.getOrNull()
            if (stream != null) return stream
        }
        throw IllegalStateException("跨平台音源未匹配到可用直链")
    }

    private suspend fun resolveKwStream(rid: String, quality: AudioQuality): StreamResult {
        val br = when (quality) {
            AudioQuality.HIRES, AudioQuality.LOSSLESS -> "2000kflac"
            AudioQuality.HIGH -> "320kmp3"
            AudioQuality.STANDARD -> "128kmp3"
        }
        // 酷我移动客户端直链解析接口
        val url1 = "http://antiserver.kuwo.cn/anti.s?type=convert_url3&br=$br&format=mp3&rid=MUSIC_$rid&response=url"
        val req1 = Request.Builder().url(url1).header("User-Agent", UA_DESKTOP).build()
        val body1 = executeString(req1)
        if (!body1.isNullOrBlank()) {
            if (body1.trim().startsWith("{")) {
                val playUrl = JSONObject(body1).optString("url")
                if (playUrl.startsWith("http")) {
                    return StreamResult.Full(url = playUrl, sourceName = "酷我直链")
                }
            } else if (body1.trim().startsWith("http")) {
                return StreamResult.Full(url = body1.trim(), sourceName = "酷我直链")
            }
        }
        val url2 = "http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MUSIC_$rid&format=mp3|aac&response=url"
        val req2 = Request.Builder().url(url2).header("User-Agent", UA_DESKTOP).build()
        val body2 = executeString(req2)?.trim()
        if (!body2.isNullOrBlank() && body2.startsWith("http")) {
            return StreamResult.Full(url = body2, sourceName = "酷我音源")
        }
        throw IllegalStateException("酷我直链解析为空")
    }

    private suspend fun resolveKgStream(hash: String, albumId: String): StreamResult {
        val url = "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&hash=$hash&album_id=$albumId&mid=1234567890"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Cookie", "kg_mid=1234567890")
            .build()
        val body = executeString(req) ?: throw IllegalStateException("酷狗接口无响应")
        val data = JSONObject(body).optJSONObject("data") ?: throw IllegalStateException("酷狗数据为空")
        val playUrl = data.optString("play_url").ifBlank { data.optString("play_backup_url") }
        if (playUrl.startsWith("http")) {
            return StreamResult.Full(url = playUrl, sourceName = "酷狗音源")
        }
        throw IllegalStateException("酷狗直链受限，回退跨平台")
    }

    private suspend fun resolveTxStream(songmid: String, song: Song, quality: AudioQuality): StreamResult {
        val payload = JSONObject().apply {
            put("req_0", JSONObject().apply {
                put("module", "vkey.GetVkeyServer")
                put("method", "CgiGetVkey")
                put("param", JSONObject().apply {
                    put("guid", "10000")
                    put("songmid", JSONArray().put(songmid))
                    put("songtype", JSONArray().put(0))
                    put("uin", "0")
                    put("loginflag", 1)
                    put("platform", "20")
                })
            })
        }
        val req = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://y.qq.com/")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = executeString(req)
        if (!body.isNullOrBlank()) {
            val data = JSONObject(body).optJSONObject("req_0")?.optJSONObject("data")
            val sip = data?.optJSONArray("sip")?.optString(0).orEmpty().ifBlank { "https://ws.stream.qqmusic.qq.com/" }
            val purl = data?.optJSONArray("midurlinfo")?.optJSONObject(0)?.optString("purl").orEmpty()
            if (purl.isNotBlank()) {
                return StreamResult.Full(url = "$sip$purl", sourceName = "QQ音乐直链")
            }
        }
        return resolveByCrossPlatformMatch(song, quality)
    }

    private suspend fun resolveMgStream(song: Song, quality: AudioQuality): StreamResult {
        if (song.songmid.startsWith("http")) {
            return StreamResult.Full(url = song.songmid, sourceName = "咪咕音源")
        }
        return resolveByCrossPlatformMatch(song, quality)
    }

    // ─── 多平台双语歌词抓取与兜底 ──────────────────────────────────────────

    suspend fun fetchMultiPlatformLyrics(song: Song): List<LyricLine> = withContext(Dispatchers.IO) {
        // 1. 按歌曲所属平台优先获取
        when (song.source.lowercase()) {
            "tx", "qq" -> {
                val txLyrics = runCatching { fetchTxLyrics(song.songmid.ifBlank { song.id }) }.getOrNull()
                if (!txLyrics.isNullOrEmpty()) return@withContext txLyrics
            }
            "kg", "kugou" -> {
                val kgLyrics = runCatching { fetchKgLyrics(song.hash.ifBlank { song.id }, song.durationMs) }.getOrNull()
                if (!kgLyrics.isNullOrEmpty()) return@withContext kgLyrics
            }
            "kw", "kuwo" -> {
                val kwLyrics = runCatching { fetchKwLyrics(song.id) }.getOrNull()
                if (!kwLyrics.isNullOrEmpty()) return@withContext kwLyrics
            }
        }

        // 2. 如果是网易云歌曲，直接通过 NcmApi 获取双语歌词 (lrc + tlyric)
        if (song.source.lowercase() in listOf("wy", "netease", "netease_native") && song.id.all { it.isDigit() }) {
            val wyLyrics = runCatching { fetchWyLyricsById(song.id) }.getOrNull()
            if (!wyLyrics.isNullOrEmpty()) return@withContext wyLyrics
        }

        // 3. 跨平台关键词搜索歌词兜底 (QQ音乐双语歌词 -> 网易云双语歌词 -> 酷狗歌词)
        val keyword = "${song.title} ${song.artist.split("/").firstOrNull().orEmpty()}".trim()
        val txCandidates = runCatching { searchTx(keyword, 1, 3) }.getOrDefault(emptyList())
        for (c in txCandidates.filter { matchesRecording(song, it) }) {
            val lrc = runCatching { fetchTxLyrics(c.songmid.ifBlank { c.id }) }.getOrNull()
            if (!lrc.isNullOrEmpty()) return@withContext lrc
        }

        val wyCandidates = runCatching { searchWy(keyword, 1, 3) }.getOrDefault(emptyList())
        for (c in wyCandidates.filter { matchesRecording(song, it) }) {
            val lrc = runCatching { fetchWyLyricsById(c.id) }.getOrNull()
            if (!lrc.isNullOrEmpty()) return@withContext lrc
        }

        emptyList()
    }

    private suspend fun fetchWyLyricsById(songId: String): List<LyricLine> {
        val timed = NcmApi.full.lyricNew(songId).getOrNull()
        if (timed != null) {
            val lines = LyricEngine.parseYrc(
                timed.ncmObj("yrc").ncmString("lyric"),
                timed.ncmObj("lrc").ncmString("lyric"),
                timed.ncmObj("tlyric").ncmString("lyric")
            )
            if (lines.isNotEmpty()) return lines
        }
        val res = NcmApi.songLyric(id = songId)
        val map = res.getOrNull() ?: return emptyList()
        val lrcText = map.ncmObj("lrc").ncmString("lyric")
        val tlyricText = map.ncmObj("tlyric").ncmString("lyric")
        return LyricEngine.parse(lrcText, tlyricText)
    }

    private suspend fun fetchTxLyrics(songmid: String): List<LyricLine> {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid&g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&nobase64=0"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://y.qq.com/")
            .build()
        val body = executeString(req) ?: error("搜索服务不可用")
        val json = JSONObject(body)
        val lrcBase64 = json.optString("lyric")
        val transBase64 = json.optString("trans")
        if (lrcBase64.isBlank()) return emptyList()

        val lrcText = String(Base64.decode(lrcBase64, Base64.DEFAULT), Charsets.UTF_8)
        val transText = if (transBase64.isNotBlank()) {
            runCatching { String(Base64.decode(transBase64, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
        } else null
        return LyricEngine.parse(lrcText, transText)
    }

    private suspend fun fetchKgLyrics(hash: String, durationMs: Long): List<LyricLine> {
        val dur = (durationMs).coerceAtLeast(180_000L)
        val searchUrl = "http://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=&duration=$dur&hash=$hash"
        val req1 = Request.Builder().url(searchUrl).header("User-Agent", UA_DESKTOP).build()
        val body1 = executeString(req1) ?: return emptyList()
        val candidates = JSONObject(body1).optJSONArray("candidates") ?: return emptyList()
        if (candidates.length() == 0) return emptyList()
        val first = candidates.optJSONObject(0) ?: return emptyList()
        val id = first.optString("id")
        val accesskey = first.optString("accesskey")
        if (id.isBlank() || accesskey.isBlank()) return emptyList()

        val dlUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8"
        val req2 = Request.Builder().url(dlUrl).header("User-Agent", UA_DESKTOP).build()
        val body2 = executeString(req2) ?: return emptyList()
        val contentBase64 = JSONObject(body2).optString("content")
        if (contentBase64.isBlank()) return emptyList()
        val lrcText = String(Base64.decode(contentBase64, Base64.DEFAULT), Charsets.UTF_8)
        return LyricEngine.parse(lrcText)
    }

    private suspend fun fetchKwLyrics(rid: String): List<LyricLine> {
        val url = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$rid"
        val req = Request.Builder().url(url).header("User-Agent", UA_DESKTOP).build()
        val body = executeString(req) ?: error("搜索服务不可用")
        val lrclist = JSONObject(body).optJSONObject("data")?.optJSONArray("lrclist") ?: return emptyList()
        val lines = ArrayList<LyricLine>(lrclist.length())
        for (i in 0 until lrclist.length()) {
            val item = lrclist.optJSONObject(i) ?: continue
            val timeSec = item.optString("time").toDoubleOrNull() ?: 0.0
            val text = item.optString("lineLyric").trim()
            if (text.isNotEmpty()) {
                lines.add(LyricLine((timeSec * 1000).toLong(), text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    // ─── 多平台歌单搜索与详情 ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    suspend fun searchPlaylists(keyword: String, limit: Int = 20): Result<List<Playlist>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = NcmApi.full.cloudSearch(
                    keywords = keyword,
                    type = 1000,
                    limit = limit
                )
                val playlists = res.getOrThrow().ncmObj("result")?.ncmList("playlists").orEmpty()
                playlists.mapNotNull { item ->
                    val map = item as? Map<String, Any?> ?: return@mapNotNull null
                    Playlist(
                        id = map.ncmLong("id").toString(),
                        name = map.ncmString("name"),
                        coverUrl = map.ncmString("coverImgUrl"),
                        description = map.ncmString("description"),
                        playCount = map.ncmLong("playCount"),
                        trackCount = map.ncmLong("trackCount").toInt(),
                        source = "wy"
                    )
                }
            }
        }

    private suspend fun executeString(request: Request): String? {
        return try {
            httpClient.newCall(request).readResponse { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
            } catch (_: Exception) {
            null
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}


internal fun matchesRecording(song: Song, candidate: Song): Boolean {
    fun normalized(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")
    val artist = normalized(song.artist.substringBefore('/'))
    return artist.isNotEmpty() && artist != "未知歌手" &&
        song.title.isNotBlank() && normalized(song.title) == normalized(candidate.title) &&
        artist == normalized(candidate.artist.substringBefore('/')) &&
        song.durationMs > 0L && candidate.durationMs > 0L &&
        kotlin.math.abs(song.durationMs - candidate.durationMs) <= maxOf(3_000L, song.durationMs / 40)
}
