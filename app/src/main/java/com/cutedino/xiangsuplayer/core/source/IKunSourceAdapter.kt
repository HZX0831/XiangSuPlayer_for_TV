package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class IKunSourceAdapter(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : ISoundSourceAdapter {

    override val sourceId: String = "ikun"
    override val sourceName: String = "ikun 音源"
    override val priority: Int = 90

    private val apiUrl = "https://c.wwwweb.top/music/url"
    private val apiKey = "IKM-P06700001-pXTmoEGzywrXIOOR-04"

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> {
        return Result.failure(UnsupportedOperationException("ikun 源仅提供音源解析"))
    }

    override suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonBody = JsonObject().apply {
                addProperty("source", "wy")
                addProperty("musicId", song.id)
                addProperty("quality", mapQuality(quality))
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Api-Key", apiKey)
                .addHeader("User-Agent", "lx-music-request/v26")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("ikun API 请求失败 HTTP ${response.code}")
                }

                val bodyString = response.body?.string() ?: throw IllegalStateException("响应体为空")
                val json = JsonParser.parseString(bodyString).asJsonObject

                val code = json.get("code")?.asInt ?: -1
                if (code == 200) {
                    val url = json.get("url")?.asString ?: ""
                    if (url.isNotBlank()) {
                        StreamResult.Full(url = url, sourceName = sourceName)
                    } else {
                        throw IllegalStateException("ikun API 未返回有效的播放 URL")
                    }
                } else {
                    val msg = json.get("message")?.asString ?: "未知错误"
                    throw IllegalStateException("ikun API 返回错误 code=$code: $msg")
                }
            }
        }
    }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> {
        return Result.failure(UnsupportedOperationException("ikun 源不提供歌词"))
    }

    private fun mapQuality(quality: AudioQuality): String {
        return when (quality) {
            AudioQuality.STANDARD -> "128k"
            AudioQuality.HIGH -> "320k"
            AudioQuality.LOSSLESS -> "flac"
            AudioQuality.HIRES -> "flac"
        }
    }
}
