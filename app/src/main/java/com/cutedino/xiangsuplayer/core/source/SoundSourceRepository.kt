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
import java.util.concurrent.ConcurrentHashMap
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

    // 音源健康度与熔断管理 (失败计数与冷却时间)
    private val failureCounts = ConcurrentHashMap<String, Int>()
    private val cooldownTimestamps = ConcurrentHashMap<String, Long>()
    private const val MAX_FAILURE_THRESHOLD = 3
    private const val COOLDOWN_DURATION_MS = 60_000L // 熔断 60 秒

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
        quality: AudioQuality
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        val adapter = getAllAdapters().firstOrNull { it.sourceId == sourceId && isEnabled(it, song) }
            ?: return@withContext Result.failure(IllegalArgumentException("此曲目无法使用该音源"))
        withTimeoutOrNull(RESOLVE_BUDGET_MS) { resolve(adapter, song, quality) }
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
    private const val RESOLVE_BUDGET_MS = 4_500L

    suspend fun getStreamUrl(
        song: Song,
        quality: AudioQuality,
        preferredSourceId: String? = null
    ): Result<ResolvedStream> = withContext(Dispatchers.IO) {
        val started = TimeSource.Monotonic.markNow()
        val enabled = getAllAdapters().filter { isEnabled(it, song) }
        val preferred = enabled.firstOrNull { it.sourceId == preferredSourceId }
        val first = preferred?.let { adapter ->
            withTimeoutOrNull(1_500L) { resolve(adapter, song, quality).getOrNull() }
        }
        if (first?.result is StreamResult.Full) return@withContext Result.success(first)
        val candidates = enabled.filter { it != preferred }
        val healthy = candidates.filter { isAdapterHealthy(it.sourceId) }
        val racers = healthy.ifEmpty { candidates }.sortedByDescending { it.priority }
        val remaining = (RESOLVE_BUDGET_MS - started.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L)
        val winner = raceStreams(racers, remaining, first) { adapter ->
            val result = resolve(adapter, song, quality).getOrNull()
            currentCoroutineContext().ensureActive()
            if (result == null) markAdapterFailure(adapter.sourceId)
            else markAdapterSuccess(adapter.sourceId)
            result
        }
        winner?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("没有可靠的可播放音源"))
    }

    private fun isEnabled(adapter: ISoundSourceAdapter, song: Song): Boolean =
        adapter.supports(song) &&
            SourceEnablement.isEnabled(adapter.sourceId, enabledSourceIds, disabledLxSourceIds) &&
            (song.key.sourceId == "wy" || adapter == crossPlatformAdapter || SourceEnablement.isLx(adapter.sourceId))

    private suspend fun resolve(adapter: ISoundSourceAdapter, song: Song, quality: AudioQuality): Result<ResolvedStream> =
        try {
            val result = adapter.getStreamUrl(song, quality).getOrThrow()
            currentCoroutineContext().ensureActive()
            require(SourceNetwork.allowsPlayback(result.streamUrl, allowLegacy = !SourceEnablement.isLx(adapter.sourceId))) {
                "播放地址不符合音源安全设置"
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

    private fun isAdapterHealthy(sourceId: String): Boolean {
        val cooldownUntil = cooldownTimestamps[sourceId] ?: 0L
        return System.currentTimeMillis() >= cooldownUntil
    }

    private fun markAdapterSuccess(sourceId: String) {
        failureCounts[sourceId] = 0
        cooldownTimestamps.remove(sourceId)
    }

    private fun markAdapterFailure(sourceId: String) {
        val currentFailures = (failureCounts[sourceId] ?: 0) + 1
        failureCounts[sourceId] = currentFailures
        if (currentFailures >= MAX_FAILURE_THRESHOLD) {
            cooldownTimestamps[sourceId] = System.currentTimeMillis() + COOLDOWN_DURATION_MS
        }
    }
}


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
