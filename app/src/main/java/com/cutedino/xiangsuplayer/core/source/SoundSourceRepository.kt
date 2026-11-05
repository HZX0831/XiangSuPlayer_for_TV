package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.builtin.BuiltInSourceSearchApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

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

    private val dynamicAdapters = CopyOnWriteArrayList<ISoundSourceAdapter>()

    fun registerDynamicAdapters(adapters: List<ISoundSourceAdapter>) {
        dynamicAdapters.clear()
        dynamicAdapters.addAll(adapters)
    }

    fun getAllAdapters(): List<ISoundSourceAdapter> = builtInAdapters + dynamicAdapters

    // 音源健康度与熔断管理 (失败计数与冷却时间)
    private val failureCounts = ConcurrentHashMap<String, Int>()
    private val cooldownTimestamps = ConcurrentHashMap<String, Long>()
    private const val MAX_FAILURE_THRESHOLD = 3
    private const val COOLDOWN_DURATION_MS = 60_000L // 熔断 60 秒

    var activeMode: String = "auto_race"
    var enabledSourceIds: Set<String> = setOf(
        "netease_native",
        "builtin_cross_platform",
        "ikun",
        "xinghai",
        "changqing",
        "nianxin"
    )

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
    ): Result<StreamResult> = withContext(Dispatchers.IO) {
        val adapter = getAllAdapters().firstOrNull { it.sourceId == sourceId }
            ?: return@withContext getStreamUrl(song, quality)
        adapter.getStreamUrl(song, quality)
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
    suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> = withContext(Dispatchers.IO) {
        // 如果歌曲本身来自非网易云平台 (tx/kg/kw/mg)，优先使用对应平台直链 + 落雪动态音源竞速
        if (song.source.lowercase() !in listOf("wy", "netease", "netease_native")) {
            val directRes = BuiltInSourceSearchApi.resolvePlatformStream(song, quality)
            if (directRes.isSuccess) {
                return@withContext directRes
            }
        }

        coroutineScope {
            val all = getAllAdapters()
            val selectedAdapters = all.filter { it.sourceId in enabledSourceIds || it.sourceId.startsWith("lx_") }
            val activeAdapters = if (selectedAdapters.isNotEmpty()) selectedAdapters else all

            val healthyAdapters = activeAdapters
                .filter { isAdapterHealthy(it.sourceId) }
                .sortedByDescending { it.priority }

            val candidateAdapters = if (healthyAdapters.isNotEmpty()) healthyAdapters else activeAdapters

            val resultDeferred = CompletableDeferred<StreamResult>()
            val trialCandidateRef = AtomicReference<StreamResult.Trial?>(null)
            val jobs = mutableListOf<Job>()

            candidateAdapters.forEach { adapter ->
                val job = launch {
                    try {
                        val res = adapter.getStreamUrl(song, quality)
                        val streamRes = res.getOrNull()
                        if (streamRes != null) {
                            when (streamRes) {
                                is StreamResult.Full -> {
                                    if (resultDeferred.complete(streamRes)) {
                                        markAdapterSuccess(adapter.sourceId)
                                    }
                                }
                                is StreamResult.Trial -> {
                                    trialCandidateRef.compareAndSet(null, streamRes)
                                }
                            }
                        } else {
                            markAdapterFailure(adapter.sourceId)
                        }
                    } catch (_: Exception) {
                        markAdapterFailure(adapter.sourceId)
                    }
                }
                jobs.add(job)
            }

            // 4500ms 超时保底机制
            val timeoutJob = launch {
                delay(4500)
                val trial = trialCandidateRef.get()
                if (trial != null) {
                    resultDeferred.complete(trial)
                } else {
                    resultDeferred.completeExceptionally(IllegalStateException("多音源解析全数超时 (4500ms)"))
                }
            }

            try {
                val finalResult = resultDeferred.await()
                jobs.forEach { it.cancel() }
                timeoutJob.cancel()
                Result.success(finalResult)
            } catch (e: Exception) {
                jobs.forEach { it.cancel() }
                timeoutJob.cancel()
                val fallbackTrial = trialCandidateRef.get()
                if (fallbackTrial != null) {
                    Result.success(fallbackTrial)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * 获取双语歌词（内置跨平台自动回退：歌曲源平台 -> 网易云双语 -> QQ音乐双语 -> 酷狗 -> 酷我）
     */
    suspend fun getLyrics(song: Song): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val multi = BuiltInSourceSearchApi.fetchMultiPlatformLyrics(song)
            if (multi.isNotEmpty()) {
                multi
            } else {
                neteaseAdapter.getLyrics(song.id).getOrDefault(emptyList())
            }
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
