package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import net.moriafly.ncm.ResponseLimitException
import kotlinx.coroutines.delay
import net.moriafly.ncm.NcmHttpException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/**
 * 跨平台直链换源适配器（酷我/酷狗/QQ/咪咕自动匹配）
 */
class CrossPlatformMatchAdapter : ISoundSourceAdapter {
    override val sourceId: String = "builtin_cross_platform"
    override val sourceName: String = "全网跨平台音源"
    override val priority: Int = 92

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> {
        return BuiltInSourceSearchApi.searchSongs("all", query, page, limit)
    }

    override suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> {
        return BuiltInSourceSearchApi.resolvePlatformStream(song, quality)
    }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> {
        return SoundSourceRepository.neteaseAdapter.getLyrics(songId)
    }
}

object SoundSourceRepository {

    val neteaseAdapter = NeteaseSourceAdapter()
    val crossPlatformAdapter = CrossPlatformMatchAdapter()
    val ikunAdapter = IKunSourceAdapter()
    val xinghaiAdapter = XingHaiSourceAdapter()
    val changqingAdapter = UrlTemplateSourceAdapter(
        sourceId = "changqing",
        sourceName = "长青音源",
        urlTemplate = "http://175.27.166.236/wy/wy.php?type=mp3&id={id}&level={level}",
        priority = 75
    )
    val nianxinAdapter = UrlTemplateSourceAdapter(
        sourceId = "nianxin",
        sourceName = "念心音源",
        urlTemplate = "http://music.nxinxz.com/wy.php?id={id}&level={level}&type=mp3",
        priority = 70
    )

    private val builtInAdapters: List<ISoundSourceAdapter> = listOf(
        neteaseAdapter,
        crossPlatformAdapter,
        ikunAdapter,
        xinghaiAdapter,
        changqingAdapter,
        nianxinAdapter
    )

    @Volatile private var dynamicAdapters = emptyList<ISoundSourceAdapter>()

    fun registerDynamicAdapters(adapters: List<ISoundSourceAdapter>) {
        dynamicAdapters = adapters.toList()
    }

    fun getAllAdapters(): List<ISoundSourceAdapter> = builtInAdapters + dynamicAdapters

    private val health = SourceHealth()

    var activeMode: String = "auto_race"
    @Volatile var enabledSourceIds: Set<String> = setOf(
        "netease_native",
        "builtin_cross_platform",
        "ikun",
        "xinghai",
        "changqing",
        "nianxin"
    )

    /** Installed LX scripts the user switched off (LX sources default to enabled). */
    @Volatile var disabledLxSourceIds: Set<String> = emptySet()

    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = 30,
        platformKey: String = "wy"
    ): Result<List<Song>> {
        return BuiltInSourceSearchApi.searchSongs(platformKey, query, page, limit)
    }

    /**
     * 指定单一音源解析直链（用于播放页手动切换音源）
     */
    suspend fun getStreamUrlFromSource(
        sourceId: String,
        song: Song,
        quality: AudioQuality,
        rejectedMiguUrl: String? = null
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        val adapter = getAllAdapters().firstOrNull { it.sourceId == sourceId && isEnabled(it, song) }
            ?: return@withContext Result.failure(IllegalArgumentException("此曲目无法使用该音源"))
        withTimeoutOrNull(RESOLVE_BUDGET_MS) { resolve(adapter, song, quality, rejectedMiguUrl) }
            ?: Result.failure(IllegalStateException("音源解析超时"))
    }

    /**
     * 测试单个音源适配器的响应延迟（ms），返回负值代表不可用
     */
    suspend fun testAdapterLatency(adapter: ISoundSourceAdapter): Long = withContext(Dispatchers.IO) {
        val testSong = Song(
            id = "1901371647",
            title = "孤勇者",
            artist = "陈奕迅",
            album = "孤勇者",
            source = "wy"
        )
        val start = System.currentTimeMillis()
        val res = runCatching { adapter.getStreamUrl(testSong, AudioQuality.STANDARD) }.getOrNull()
        val stream = res?.getOrNull()
        if (stream != null && stream.streamUrl.isNotBlank()) {
            (System.currentTimeMillis() - start).coerceAtLeast(12L)
        } else {
            -1L
        }
    }

    /**
     * 多音源智能并行竞速与跨平台换源回退引擎
     */
    internal const val RESOLVE_BUDGET_MS = 4_500L

    suspend fun getStreamUrl(
        song: Song,
        quality: AudioQuality,
        preferredSourceId: String? = null,
        excludedSources: Set<String> = emptySet(),
        budgetMs: Long = RESOLVE_BUDGET_MS,
        rejectedMiguUrl: String? = null
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        val started = TimeSource.Monotonic.markNow()
        val enabled = getAllAdapters().filter { it.sourceId !in excludedSources && isEnabled(it, song) }
        val preferred = enabled.firstOrNull { it.sourceId == preferredSourceId }
        val first = preferred?.let { adapter ->
            withTimeoutOrNull(budgetMs.coerceIn(0L, 1_500L)) { resolveAndRecord(adapter, song, quality, rejectedMiguUrl) }
        }
        if (first?.result is StreamResult.Full) return@withContext Result.success(first)
        val candidates = enabled.filter { it != preferred }.sortedByDescending { it.priority }
        val healthy = candidates.filter { !health.isCoolingDown(it.sourceId) }
        val remaining = (budgetMs - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L)
        val winner = raceStreams(candidates, remaining, first) { adapter ->
            if (healthy.isNotEmpty() && adapter !in healthy) delay(minOf(500L, remaining / 3))
            resolveAndRecord(adapter, song, quality, rejectedMiguUrl)
        }
        winner?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("没有可靠的可播放音源"))
    }

    private suspend fun resolveAndRecord(
        adapter: ISoundSourceAdapter, song: Song, quality: AudioQuality, rejectedMiguUrl: String?
    ): ResolvedStream? {
        val result = resolve(adapter, song, quality, rejectedMiguUrl)
        currentCoroutineContext().ensureActive()
        val error = result.exceptionOrNull()
        when {
            error == null -> health.recordSuccess(adapter.sourceId)
            isTransportFailure(error) -> health.recordTransportFailure(adapter.sourceId)
        }
        return result.getOrNull()
    }

    internal fun hasUntriedSource(song: Song, excluded: Set<String>): Boolean =
        getAllAdapters().any { it.sourceId !in excluded && isEnabled(it, song) }

    private fun isEnabled(adapter: ISoundSourceAdapter, song: Song): Boolean =
        adapter.supports(song) &&
            SourceEnablement.isEnabled(adapter.sourceId, enabledSourceIds, disabledLxSourceIds) &&
            (song.key.sourceId == "wy" || adapter == crossPlatformAdapter || SourceEnablement.isLx(adapter.sourceId))

    private suspend fun resolve(
        adapter: ISoundSourceAdapter, song: Song, quality: AudioQuality, rejectedMiguUrl: String?
    ): Result<ResolvedStream> =
        try {
            val result = if (adapter === crossPlatformAdapter && rejectedMiguUrl != null) {
                BuiltInSourceSearchApi.resolvePlatformStream(song, quality, rejectedMiguUrl).getOrThrow()
            } else adapter.getStreamUrl(song, quality).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (!SourceNetwork.allowsPlayback(result.streamUrl, allowLegacy = !SourceEnablement.isLx(adapter.sourceId))) {
                throw SourcePolicyException("播放地址不符合音源安全设置")
            }
            Result.success(ResolvedStream(adapter.sourceId, quality, result))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }

    /**
     * 获取双语歌词（内置跨平台自动回退：歌曲源平台 -> 网易云双语 -> QQ音乐双语 -> 酷狗 -> 酷我）
     */
    suspend fun getLyrics(song: Song): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val multi = BuiltInSourceSearchApi.fetchMultiPlatformLyrics(song)
            if (multi.isNotEmpty()) {
                multi
            } else if (song.key.sourceId == "wy") {
                neteaseAdapter.getLyrics(song.id).getOrDefault(emptyList())
            } else emptyList()
        }
    }

}

internal fun isTransportFailure(error: Throwable): Boolean = when (error) {
    is NcmHttpException -> error.status in 500..599
    is SourceHttpException -> error.status in 500..599
    is SourcePolicyException, is ResponseLimitException -> false
    is IOException -> true
    else -> false
}

internal class SourceHealth(private val nanoTime: () -> Long = System::nanoTime) {
    private data class Failure(val count: Int, val until: Long?)
    private val failures = mutableMapOf<String, Failure>()

    @Synchronized
    fun isCoolingDown(id: String): Boolean = failures[id]?.until?.let { nanoTime() - it < 0 } ?: false

    @Synchronized
    fun recordSuccess(id: String) {
        failures.remove(id)
    }

    @Synchronized
    fun recordTransportFailure(id: String) {
        val previous = failures[id]
        val now = nanoTime()
        val count = if (previous == null || previous.until?.let { now - it >= 0 } == true) 1 else previous.count + 1
        failures[id] = Failure(count, if (count >= 3) now + 60_000_000_000L else null)
    }
}

internal class SourceFallback {
    private var failure: Exception? = null

    suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (failure == null || isTransportFailure(error)) failure = error
        null
    }

    fun exhausted(message: String): Nothing = throw (failure ?: IllegalStateException(message))
}

internal class SourceHttpException(val status: Int) : IOException("Audio source HTTP $status")
internal class SourcePolicyException(message: String) : IOException(message)

internal suspend fun <T> raceStreams(
    candidates: List<T>,
    budgetMs: Long,
    initialTrial: ResolvedStream? = null,
    resolve: suspend (T) -> ResolvedStream?
): ResolvedStream? = coroutineScope {
    if (candidates.isEmpty() || budgetMs <= 0) return@coroutineScope initialTrial
    val replies = Channel<ResolvedStream?>(candidates.size)
    val jobs = candidates.map { candidate -> launch { replies.send(resolve(candidate)) } }
    var trial = initialTrial
    try {
        withTimeoutOrNull(budgetMs) {
            repeat(candidates.size) {
                val result = replies.receive()
                when (result?.result) {
                    is StreamResult.Full -> return@withTimeoutOrNull result
                    is StreamResult.Trial -> if (trial == null) trial = result
                    null -> Unit
                }
            }
            null
        } ?: trial
    } finally {
        jobs.forEach { it.cancel() }
    }
}
