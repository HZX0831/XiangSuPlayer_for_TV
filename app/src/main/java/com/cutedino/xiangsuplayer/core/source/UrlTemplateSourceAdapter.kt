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

class UrlTemplateSourceAdapter(
    override val sourceId: String,
    override val sourceName: String,
    private val urlTemplate: String,
    override val priority: Int = 70,
    okHttpClient: OkHttpClient = SourceNetwork.configure(OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : ISoundSourceAdapter {

    private val okHttpClient = okHttpClient.newBuilder().followRedirects(false).build()

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> {
        return Result.failure(UnsupportedOperationException("$sourceName 仅提供音源解析"))
    }

    override suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> = withContext(Dispatchers.IO) {
        runCatching {
            val levelStr = mapQuality(quality)
            val requestUrl = urlTemplate
                .replace("{id}", song.id)
                .replace("{level}", levelStr)

            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            okHttpClient.newCall(request).readResponse { response ->
                if (response.isRedirect) {
                    val url = playbackLocation(response) ?: throw SourcePolicyException("无效的音源重定向")
                    return@readResponse StreamResult.Full(url, sourceName = sourceName)
                }
                if (!response.isSuccessful) throw SourceHttpException(response.code)

                val bodyStr = response.body?.readBounded(256 * 1024)?.toString(Charsets.UTF_8) ?: throw IllegalStateException("响应体为空")
                
                // 处理可能返回的 JSON 格式或直接 Location/URL 字符串
                val extractedUrl = parseUrlFromBodyOrRedirect(bodyStr, response)
                if (extractedUrl.isNotBlank()) {
                    StreamResult.Full(url = extractedUrl, sourceName = sourceName)
                } else {
                    throw IllegalStateException("$sourceName 未找到播放 URL")
                }
            }
        }
    }

    private fun parseUrlFromBodyOrRedirect(bodyStr: String, response: okhttp3.Response): String {
        val trimmed = bodyStr.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.startsWith("{")) {
            try {
                val json = JsonParser.parseString(trimmed).asJsonObject
                if (json.has("url")) return json.get("url").asString
                if (json.has("data") && json.get("data").isJsonObject) {
                    val data = json.getAsJsonObject("data")
                    if (data.has("url")) return data.get("url").asString
                }
            } catch (_: Exception) {}
        }
        return playbackLocation(response).orEmpty()
    }

    private fun playbackLocation(response: okhttp3.Response): String? =
        response.header("Location")?.takeIf(String::isNotBlank)?.let(response.request.url::resolve)
            ?.toString()?.takeIf { SourceNetwork.allowsPlayback(it, allowLegacy = true) }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> {
        return Result.failure(UnsupportedOperationException("$sourceName 不提供歌词"))
    }

    private fun mapQuality(quality: AudioQuality): String {
        return when (quality) {
            AudioQuality.STANDARD -> "standard"
            AudioQuality.HIGH -> "exhigh"
            AudioQuality.LOSSLESS -> "lossless"
            AudioQuality.HIRES -> "hires"
        }
    }
}
