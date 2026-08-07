package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class XingHaiSourceAdapter(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
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
            // 优先尝试 VIP 接口
            try {
                val vipUrl = fetchVipUrl(song.id, quality)
                if (vipUrl.isNotBlank()) {
                    return@runCatching StreamResult.Full(url = vipUrl, sourceName = sourceName)
                }
            } catch (_: Exception) {
                // VIP 接口失败，降级尝试 GD 主接口
            }

            val gdUrl = fetchGdUrl(song.id, quality)
            if (gdUrl.isNotBlank()) {
                StreamResult.Full(url = gdUrl, sourceName = sourceName)
            } else {
                throw IllegalStateException("星海音源解析未获取到有效的音频链接")
            }
        }
    }

    private fun fetchVipUrl(songId: String, quality: AudioQuality): String {
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

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val body = response.body?.string() ?: return ""
            val json = JsonParser.parseString(body).asJsonObject
            if (json.get("code")?.asInt == 200 && json.has("data")) {
                val dataObj = json.getAsJsonObject("data")
                return dataObj.get("url")?.asString ?: ""
            }
        }
        return ""
    }

    private fun fetchGdUrl(songId: String, quality: AudioQuality): String {
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

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val body = response.body?.string() ?: return ""
            val json = JsonParser.parseString(body).asJsonObject
            return json.get("url")?.asString ?: ""
        }
    }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> {
        return Result.failure(UnsupportedOperationException("星海源不提供歌词"))
    }
}
