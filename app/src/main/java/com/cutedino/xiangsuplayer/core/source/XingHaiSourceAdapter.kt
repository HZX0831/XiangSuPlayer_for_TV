package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.moriafly.ncm.readResponse
import net.moriafly.ncm.readBounded
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class XingHaiSourceAdapter(
    private val okHttpClient: OkHttpClient = SourceNetwork.configure(OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : ISoundSourceAdapter {

    override val sourceId: String = "xinghai"
    override val sourceName: String = "星海音乐源"
    override val priority: Int = 85

    private val chkszVipApi = "https://api.chksz.top/api/163_music"
    private val gdMainApi = "https://music-api.gdstudio.xyz/api.php?use_xbridge3=true&loader_name=forest&need_sec_link=1&sec_link_scene=im&theme=light"

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> {
        return Result.failure(UnsupportedOperationException("星海源仅提供音源解析"))
    }

    override suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> = withContext(Dispatchers.IO) {
        runCatching {
            val fallback = SourceFallback()
            val url = fallback.attempt { checkedUrl(fetchVipUrl(song.id, quality)) }
                ?: fallback.attempt { checkedUrl(fetchGdUrl(song.id, quality)) }
                ?: fallback.exhausted("星海音源解析未获取到有效的音频链接")
            StreamResult.Full(url, sourceName = sourceName)
        }
    }

    private fun checkedUrl(url: String): String? {
        if (url.isBlank()) return null
        if (!SourceNetwork.allowsPlayback(url, allowLegacy = true)) {
            throw SourcePolicyException("播放地址不符合音源安全设置")
        }
        return url
    }

    private suspend fun fetchVipUrl(songId: String, quality: AudioQuality): String {
        val level = when (quality) {
            AudioQuality.STANDARD -> "standard"
            AudioQuality.HIGH -> "exhigh"
            AudioQuality.LOSSLESS -> "lossless"
            AudioQuality.HIRES -> "jymaster"
        }
        val requestUrl = "$chkszVipApi?id=$songId&level=$level"
        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("User-Agent", "LX-Music-Mobile")
            .build()

        return okHttpClient.newCall(request).readResponse { response ->
            if (!response.isSuccessful) throw SourceHttpException(response.code)
            val body = response.body?.readBounded(256 * 1024)?.toString(Charsets.UTF_8) ?: return@readResponse ""
            val json = JsonParser.parseString(body).asJsonObject
            if (json.get("code")?.asInt == 200 && json.has("data")) {
                val dataObj = json.getAsJsonObject("data")
                return@readResponse dataObj.get("url")?.asString ?: ""
            }
            ""
        }
    }

    private suspend fun fetchGdUrl(songId: String, quality: AudioQuality): String {
        val br = when (quality) {
            AudioQuality.STANDARD -> "128"
            AudioQuality.HIGH -> "320"
            AudioQuality.LOSSLESS -> "740"
            AudioQuality.HIRES -> "999"
        }
        val requestUrl = "$gdMainApi&types=url&source=netease&id=$songId&br=$br"
        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("User-Agent", "LX-Music-Mobile")
            .build()

        return okHttpClient.newCall(request).readResponse { response ->
            if (!response.isSuccessful) throw SourceHttpException(response.code)
            val body = response.body?.readBounded(256 * 1024)?.toString(Charsets.UTF_8) ?: return@readResponse ""
            val json = JsonParser.parseString(body).asJsonObject
            json.get("url")?.asString.orEmpty()
        }
    }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> {
        return Result.failure(UnsupportedOperationException("星海源不提供歌词"))
    }
}
