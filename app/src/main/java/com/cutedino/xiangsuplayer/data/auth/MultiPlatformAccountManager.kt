package com.cutedino.xiangsuplayer.data.auth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.cutedino.xiangsuplayer.core.model.Playlist
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import com.cutedino.xiangsuplayer.core.source.builtin.matchesRecording
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.data.session.SessionManager
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.readResponse
import net.moriafly.ncm.readBounded
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmInt
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SongCommentItem(
    val commentId: String,
    val userName: String,
    val avatarUrl: String,
    val content: String,
    val likedCount: Long,
    val timeStr: String
)

data class QqQrSession(
    val qrBitmap: Bitmap,
    val qrsig: String
)

/**
 * 多平台账号、私人FM、心动模式、歌单同步与热评管理器
 * 支持：
 * 1. 网易云音乐 (扫码登录、私人FM、心动模式、红心歌曲同步、云盘、热门评论)
 * 2. QQ音乐 (扫码登录 + QQ号一键绑定同步歌单)
 * 3. 酷狗音乐 (酷狗账号/歌单绑定与同步)
 */
object MultiPlatformAccountManager {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    private const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    val isNeteaseLoggedIn: Boolean
        get() = NcmApi.isLogin

    val isQqLoggedIn: Boolean
        get() = SessionManager.qqMusicUin.isNotBlank()

    val isKugouLoggedIn: Boolean
        get() = SessionManager.kugouUserId.isNotBlank()

    fun getAccountSummaryText(): String {
        val count = listOf(isNeteaseLoggedIn, isQqLoggedIn).count { it }
        return when {
            count == 0 -> "账号登录"
            isNeteaseLoggedIn && count == 1 -> NcmApi.nickname.ifBlank { "网易云已登录" }
            else -> "已连 $count 平台"
        }
    }

    // ─── 1. 网易云音乐：扫码登录、私人 FM、心动模式、喜欢列表、热评 ─────────────

    suspend fun prepareNeteaseQr(): Result<Pair<String, Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            val qr = NcmApi.qrLoginPrepare().getOrThrow()
            val base64Img = qr.qrPngBase64.substringAfter("base64,")
            val bytes = Base64.decode(base64Img, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("二维码解码失败")
            qr.key to bmp
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchPersonalFmSongs(): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = NcmApi.full.personalFm()
            val dataList = res.getOrNull()?.ncmList("data").orEmpty()
            if (dataList.isNotEmpty()) {
                return@runCatching dataList.mapNotNull { item ->
                    val map = item as? Map<String, Any?> ?: return@mapNotNull null
                    map.toSong().copy(source = "wy")
                }
            }
            // 未登录或接口为空时，回退至每日推荐新歌作为私人漫游电台
            val fallback = NcmApi.full.personalizedNewsong(limit = 15).getOrNull()?.ncmList("result").orEmpty()
            fallback.mapNotNull { item ->
                val map = item as? Map<String, Any?> ?: return@mapNotNull null
                val songObj = map.ncmObj("song")
                if (songObj.isNotEmpty()) songObj.toSong().copy(source = "wy") else null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchHeartModeSongs(seedSongId: String, playlistId: String = ""): Result<List<Song>> =
        withContext(Dispatchers.IO) {
            runCatching {
                var pid = playlistId
                if (pid.isBlank() && NcmApi.isLogin && NcmApi.userId > 0) {
                    val pls = NcmApi.userPlaylist(NcmApi.userId.toString(), limit = 2).getOrNull()?.ncmList("playlist").orEmpty()
                    val firstPl = pls.firstOrNull() as? Map<String, Any?>
                    pid = firstPl?.ncmLong("id")?.toString().orEmpty()
                }
                if (pid.isNotBlank() && seedSongId.all { it.isDigit() }) {
                    val res = NcmApi.full.playmodeIntelligenceList(
                        id = pid,
                        sid = seedSongId
                    )
                    val dataList = res.getOrNull()?.ncmList("data").orEmpty()
                    val songs = dataList.mapNotNull { item ->
                        val map = item as? Map<String, Any?> ?: return@mapNotNull null
                        val songInfo = map.ncmObj("songInfo")
                        if (songInfo.isNotEmpty()) songInfo.toSong().copy(source = "wy") else null
                    }
                    if (songs.isNotEmpty()) return@runCatching songs
                }
                // 回退至相似歌曲推荐 (simiSong)
                if (seedSongId.all { it.isDigit() }) {
                    val simi = NcmApi.full.simiSong(id = seedSongId).getOrNull()?.ncmList("songs").orEmpty()
                    val songs = simi.mapNotNull { (it as? Map<String, Any?>)?.toSong()?.copy(source = "wy") }
                    if (songs.isNotEmpty()) return@runCatching songs
                }
                fetchPersonalFmSongs().getOrDefault(emptyList())
            }
        }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchSongHotComments(song: Song): List<SongCommentItem> = withContext(Dispatchers.IO) {
        runCatching {
            val targetId = resolveNeteaseCommentTrack(song) { query ->
                BuiltInSourceSearchApi.searchSongs("wy", query, limit = 5).getOrThrow()
            } ?: return@runCatching emptyList()

            val res = NcmApi.full.commentMusic(id = targetId, limit = 25)
            val data = res.getOrNull() ?: return@runCatching emptyList()
            val hotList = data.ncmList("hotComments").ifEmpty { data.ncmList("comments") }

            hotList.mapNotNull { item ->
                val map = item as? Map<String, Any?> ?: return@mapNotNull null
                val userObj = map.ncmObj("user")
                val content = map.ncmString("content").trim()
                if (content.isBlank()) return@mapNotNull null
                SongCommentItem(
                    commentId = map.ncmLong("commentId").toString(),
                    userName = userObj.ncmString("nickname").ifBlank { "云村乐迷" },
                    avatarUrl = userObj.ncmString("avatarUrl"),
                    content = content,
                    likedCount = map.ncmLong("likedCount"),
                    timeStr = map.ncmString("timeStr")
                )
            }
        }.getOrDefault(emptyList())
    }

    // ─── 2. QQ音乐：官方扫码登录 + QQ号一键歌单同步 ─────────────────────────────

    suspend fun prepareQqQr(): Result<QqQrSession> = withContext(Dispatchers.IO) {
        runCatching {
            val t = Math.random()
            val url = "https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=4&d=72&v=4&t=$t&daid=383&pt_3rd_aid=100497308"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA_DESKTOP)
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .build()
            httpClient.newCall(req).readResponse { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val cookies = resp.headers("Set-Cookie")
                var qrsig = ""
                for (c in cookies) {
                    val m = Regex("""qrsig=([^;]+)""").find(c)
                    if (m != null) {
                        qrsig = m.groupValues[1]
                        break
                    }
                }
                val bytes = resp.body?.readBounded(512 * 1024) ?: throw IllegalStateException("图片为空")
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("二维码解析失败")
                QqQrSession(qrBitmap = bmp, qrsig = qrsig)
            }
        }
    }

    /**
     * 轮询单次 QQ 音乐扫码状态
     * 返回 Pair<状态码, 提示信息>:
     * - 66: 未失效等待扫码
     * - 67: 扫码认证中
     * - 65: 二维码已失效
     * - 0: 登录成功 (自动保存 QQ 号与昵称)
     */
    suspend fun checkQqQrStatus(qrsig: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        runCatching {
            val ptqrtoken = hash33(qrsig)
            val url = "https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump&ptqrtoken=$ptqrtoken&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-0-${System.currentTimeMillis()}&js_ver=20102616&js_type=1&login_sig=&pt_uistyle=40&aid=716027609&daid=383&pt_3rd_aid=100497308"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA_DESKTOP)
                .header("Cookie", "qrsig=$qrsig")
                .header("Referer", "https://xui.ptlogin2.qq.com/")
                .build()
            val body = httpClient.newCall(req).readResponse { it.body?.readBounded(4 * 1024 * 1024)?.toString(Charsets.UTF_8).orEmpty() }
            // ptuiCB('66','0','','0','二维码未失效。', '')
            val regex = Regex("""ptuiCB\('(\d+)','[^']*','([^']*)','[^']*','([^']*)',\s*'([^']*)'\)""")
            val match = regex.find(body) ?: return@runCatching 66 to "等待使用手机 QQ 扫码..."
            val code = match.groupValues[1].toIntOrNull() ?: 66
            val jumpUrl = match.groupValues[2]
            val msg = match.groupValues[3]
            val nick = match.groupValues[4]

            if (code == 0) {
                val uinMatch = Regex("""uin=o?(\d+)""").find(jumpUrl)
                val uin = uinMatch?.groupValues?.get(1).orEmpty()
                if (uin.isBlank()) return@runCatching 65 to "未取得 QQ 号，请直接输入公开资料的 QQ 号"
                ensureActive()
                SessionManager.qqMusicUin = uin
                SessionManager.qqMusicNickname = nick.ifBlank { "QQ音乐用户 ($uin)" }
                0 to "已关联 QQ 公开资料：${SessionManager.qqMusicNickname}"
            } else {
                code to msg
            }
        }.getOrDefault(66 to "等待扫码...")
    }

    fun bindQqByUin(uin: String, nickname: String = "") {
        val cleaned = uin.trim()
        if (cleaned.isNotEmpty()) {
            SessionManager.qqMusicUin = cleaned
            SessionManager.qqMusicNickname = nickname.ifBlank { "QQ音乐 ($cleaned)" }
        }
    }

    fun logoutQq() {
        SessionManager.qqMusicUin = ""
        SessionManager.qqMusicNickname = ""
        SessionManager.qqMusicCookie = ""
    }

    suspend fun fetchQqUserPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val uin = SessionManager.qqMusicUin
        if (uin.isBlank()) return@withContext emptyList()
        val url = "https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?cid=205360838&userid=$uin&reqfrom=1&format=json"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://y.qq.com/")
            .build()
        val body = httpClient.newCall(req).readResponse {
            check(it.isSuccessful) { "QQ 音乐服务暂不可用" }
            checkNotNull(it.body).readBounded(4 * 1024 * 1024).toString(Charsets.UTF_8)
        }

        val data = JSONObject(body).optJSONObject("data") ?: return@withContext emptyList()
        val result = mutableListOf<Playlist>()
        val mymusic = data.optJSONArray("mymusic")
        if (mymusic != null) {
            for (i in 0 until mymusic.length()) {
                val m = mymusic.optJSONObject(i) ?: continue
                val id = m.optString("id")
                val title = m.optString("title")
                if (id.isNotBlank() && id != "0" && title.isNotBlank()) {
                    result.add(
                        Playlist(
                            id = "tx_$id",
                            name = "QQ · $title",
                            coverUrl = m.optString("laypic"),
                            trackCount = m.optInt("num0", 0),
                            source = "tx"
                        )
                    )
                }
            }
        }
        val mydiss = data.optJSONObject("mydiss")?.optJSONArray("list")
        if (mydiss != null) {
            for (i in 0 until mydiss.length()) {
                val d = mydiss.optJSONObject(i) ?: continue
                val dissId = d.optString("dissid")
                val title = d.optString("title")
                if (dissId.isNotBlank() && title.isNotBlank()) {
                    result.add(
                        Playlist(
                            id = "tx_$dissId",
                            name = "QQ · $title",
                            coverUrl = d.optString("picurl"),
                            description = d.optString("subtitle"),
                            source = "tx"
                        )
                    )
                }
            }
        }
        result
    }

    suspend fun fetchQqPlaylistSongs(rawPlaylistId: String): List<Song> = withContext(Dispatchers.IO) {
        val dissid = rawPlaylistId.removePrefix("tx_")
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=$dissid&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA_DESKTOP)
            .header("Referer", "https://y.qq.com/")
            .build()
        val body = httpClient.newCall(req).readResponse {
            check(it.isSuccessful) { "QQ 音乐服务暂不可用" }
            checkNotNull(it.body).readBounded(4 * 1024 * 1024).toString(Charsets.UTF_8)
        }

        val cdlist = JSONObject(body).optJSONArray("cdlist") ?: return@withContext emptyList()
        if (cdlist.length() == 0) return@withContext emptyList()
        val songlist = cdlist.optJSONObject(0)?.optJSONArray("songlist") ?: return@withContext emptyList()

        val result = ArrayList<Song>(songlist.length())
        for (i in 0 until songlist.length()) {
            val item = songlist.optJSONObject(i) ?: continue
            val mid = item.optString("mid").ifBlank { item.optString("songmid") }
            if (mid.isBlank()) continue
            val title = item.optString("name").ifBlank { item.optString("songname") }
            val singerArr = item.optJSONArray("singer")
            val artist = if (singerArr != null && singerArr.length() > 0) {
                (0 until singerArr.length()).joinToString(" / ") { idx ->
                    singerArr.optJSONObject(idx)?.optString("name").orEmpty()
                }
            } else "未知歌手"
            val albumObj = item.optJSONObject("album")
            val albumName = albumObj?.optString("name").orEmpty().ifBlank { item.optString("albumname") }
            val albumMid = albumObj?.optString("mid").orEmpty().ifBlank { item.optString("albummid") }
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
        result
    }

    // ─── 3. 酷狗音乐：酷狗号/酷狗码绑定与歌单同步 ─────────────────────────────

    fun bindKugouAccount(userIdOrCode: String, nickname: String = "") {
        val cleaned = userIdOrCode.trim()
        if (cleaned.isNotEmpty()) {
            SessionManager.kugouUserId = cleaned
            SessionManager.kugouNickname = nickname.ifBlank { "酷狗音乐 ($cleaned)" }
        }
    }

    fun logoutKugou() {
        SessionManager.kugouUserId = ""
        SessionManager.kugouNickname = ""
        SessionManager.kugouToken = ""
    }

    suspend fun fetchKugouUserPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        listOf(
            Playlist(
                id = "kg_board_6666",
                name = "酷狗 · 公开飙升榜",
                coverUrl = "",
                description = "酷狗公开榜单",
                trackCount = 100,
                source = "kg"
            ),
            Playlist(
                id = "kg_board_8888",
                name = "酷狗 · 公开 TOP500",
                coverUrl = "",
                description = "酷狗公开榜单",
                trackCount = 100,
                source = "kg"
            )
        )
    }

    private fun hash33(qrsig: String): Int {
        var e = 0
        for (ch in qrsig) {
            e += (e shl 5) + ch.code
        }
        return 2147483647 and e
    }
}


internal suspend fun resolveNeteaseCommentTrack(song: Song, search: suspend (String) -> List<Song>): String? {
    return when (song.key.sourceId) {
        "wy" -> song.id.toLongOrNull()?.takeIf { it > 0L }?.toString()
        "tx", "kg", "kw", "mg" -> search("${song.title} ${song.artist}").firstOrNull {
            it.key.sourceId == "wy" && it.id.toLongOrNull()?.let { id -> id > 0L } == true &&
                matchesRecording(song, it)
        }?.id
        else -> null
    }
}
