package com.cutedino.xiangsuplayer.core.toplist

import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmObj
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 多平台排行榜数据拉取与内存缓存仓库（支持网易云、QQ音乐、酷狗音乐）
 */
object ToplistRepository {

    private const val TX_LIST_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    private const val TX_PERIOD_URL = "https://c.y.qq.com/node/pc/wk_v15/top.html"
    private const val KG_LIST_URL =
        "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=100&area_code=1&page=1&rankid=%s&with_res_tag=0&show_portrait_mv=1"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val boardSongsCache = ConcurrentHashMap<String, List<Song>>()

    suspend fun fetchBoardSongs(
        entry: ToplistCatalog.Entry,
        forceRefresh: Boolean = false
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            boardSongsCache[entry.id]?.takeIf { it.isNotEmpty() }?.let {
                return@withContext Result.success(it)
            }
        }

        runCatching {
            val songs = when (entry.platformKey.lowercase()) {
                "wy" -> fetchWyBoard(entry.bangid)
                "tx" -> fetchTxBoard(entry)
                "kg" -> fetchKgBoard(entry)
                else -> emptyList()
            }
            if (songs.isNotEmpty()) {
                boardSongsCache[entry.id] = songs
            }
            songs
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchWyBoard(bangid: String): List<Song> {
        val res = NcmApi.playlistDetail(bangid)
        val playlistObj = res.getOrNull()?.ncmObj("playlist") ?: return emptyList()
        val tracks = playlistObj.ncmList("tracks")
        return tracks.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
    }

    private fun fetchTxBoard(entry: ToplistCatalog.Entry): List<Song> {
        val period = fetchTxPeriod(entry.bangid)
        val payload = JSONObject()
            .put(
                "toplist",
                JSONObject()
                    .put("module", "musicToplist.ToplistInfoServer")
                    .put("method", "GetDetail")
                    .put(
                        "param",
                        JSONObject()
                            .put("topid", entry.bangid.toIntOrNull() ?: 0)
                            .put("num", 100)
                            .put("period", period)
                    )
            )
            .put(
                "comm",
                JSONObject().put("uin", 0).put("format", "json").put("ct", 20).put("cv", 1859)
            )

        val req = Request.Builder()
            .url(TX_LIST_URL)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header(
                "User-Agent",
                "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)"
            )
            .build()

        val body = execute(req) ?: return emptyList()
        val root = JSONObject(body)
        val songList = root.optJSONObject("toplist")
            ?.optJSONObject("data")
            ?.optJSONArray("songInfoList") ?: return emptyList()

        val result = ArrayList<Song>(songList.length())
        for (i in 0 until songList.length()) {
            val item = songList.optJSONObject(i) ?: continue
            val mid = item.optString("mid", "")
            if (mid.isBlank()) continue
            val title = item.optString("title", "")
            if (title.isBlank()) continue
            val singerArr = item.optJSONArray("singer")
            val singer = if (singerArr != null && singerArr.length() > 0) {
                (0 until singerArr.length()).joinToString(" / ") { idx ->
                    singerArr.optJSONObject(idx)?.optString("name", "") ?: ""
                }
            } else "未知歌手"
            val albumObj = item.optJSONObject("album")
            val albumName = albumObj?.optString("name", "") ?: ""
            val albumMid = albumObj?.optString("mid", "") ?: ""
            val pic = if (albumMid.isNotBlank()) {
                "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg"
            } else ""
            val durationMs = item.optLong("interval", 0L) * 1000L

            result.add(
                Song(
                    id = mid,
                    title = title,
                    artist = singer,
                    album = albumName,
                    coverUrl = pic,
                    durationMs = durationMs,
                    source = "tx",
                    songmid = mid
                )
            )
        }
        return result
    }

    private fun fetchTxPeriod(bangid: String): String {
        return try {
            val req = Request.Builder().url(TX_PERIOD_URL).build()
            val html = execute(req) ?: return ""
            val pattern = Regex("""data-listname="(.+?)" data-tid=".*?\/(.+?)" data-date="(.+?)".*?<\/i>""")
            for (m in pattern.findAll(html)) {
                if (m.groupValues[2] == bangid) {
                    return m.groupValues[3]
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun fetchKgBoard(entry: ToplistCatalog.Entry): List<Song> {
        val req = Request.Builder().url(String.format(KG_LIST_URL, entry.bangid)).build()
        val body = execute(req) ?: return emptyList()
        val info = JSONObject(body).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()

        val result = ArrayList<Song>(info.length())
        for (i in 0 until info.length()) {
            val item = info.optJSONObject(i) ?: continue
            val hash = item.optString("hash", "")
            if (hash.isBlank()) continue
            val title = item.optString("songname", "")
            if (title.isBlank()) continue
            val authors = item.optJSONArray("authors")
            val singer = if (authors != null && authors.length() > 0) {
                (0 until authors.length()).joinToString(" / ") { idx ->
                    authors.optJSONObject(idx)?.optString("author_name", "") ?: ""
                }
            } else "未知歌手"
            val album = item.optString("remark", "").ifBlank { item.optString("album_name", "") }
            val pic = item.optString("album_sizable_cover", "")
                .replace("{size}", "400")
            val durationSec = item.optLong("duration", 0L)

            result.add(
                Song(
                    id = hash,
                    title = title,
                    artist = singer,
                    album = album,
                    coverUrl = pic,
                    durationMs = durationSec * 1000L,
                    source = "kg",
                    songmid = item.optString("album_id", ""),
                    hash = hash
                )
            )
        }
        return result
    }

    private fun execute(request: Request): String? {
        return try {
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
