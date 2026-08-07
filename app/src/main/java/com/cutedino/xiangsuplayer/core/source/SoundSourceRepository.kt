package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object SoundSourceRepository {

    val neteaseAdapter = NeteaseSourceAdapter()
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

    private val adapters: List<ISoundSourceAdapter> = listOf(
        neteaseAdapter,
        ikunAdapter,
        xinghaiAdapter,
        changqingAdapter,
        nianxinAdapter
    )

    // 音源健康度与熔断管理 (失败计数与冷却时间)
    private val failureCounts = ConcurrentHashMap<String, Int>()
    private val cooldownTimestamps = ConcurrentHashMap<String, Long>()
    private const val MAX_FAILURE_THRESHOLD = 3
    private const val COOLDOWN_DURATION_MS = 60_000L // 熔断 60 秒

    var activeMode: String = "auto_race"
    var enabledSourceIds: Set<String> = setOf("netease_native", "ikun", "xinghai", "changqing", "nianxin")

    suspend fun search(query: String, page: Int = 1, limit: Int = 30): Result<List<Song>> {
        return neteaseAdapter.search(query, page, limit)
    }

    /**
     * 多音源智能并行竞速与试听回退引擎
     */
    suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            val selectedAdapters = adapters.filter { it.sourceId in enabledSourceIds }
            val activeAdapters = if (selectedAdapters.isNotEmpty()) selectedAdapters else adapters

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

            // 4000ms 超时保底机制
            val timeoutJob = launch {
                delay(4000)
                val trial = trialCandidateRef.get()
                if (trial != null) {
                    resultDeferred.complete(trial)
                } else {
                    resultDeferred.completeExceptionally(IllegalStateException("多音源解析全数超时 (4000ms)"))
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

    suspend fun getLyrics(song: Song): Result<List<LyricLine>> {
        return neteaseAdapter.getLyrics(song.id)
    }

    private fun isAdapterHealthy(sourceId: String): Boolean {
        val cooldownUntil = cooldownTimestamps[sourceId] ?: 0L
        if (System.currentTimeMillis() < cooldownUntil) {
            return false // 处于熔断冷却中
        }
        return true
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
