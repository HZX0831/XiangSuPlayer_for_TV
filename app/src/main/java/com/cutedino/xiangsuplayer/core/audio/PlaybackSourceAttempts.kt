package com.cutedino.xiangsuplayer.core.audio

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository

internal class PlaybackSourceAttempts(private val nanoTime: () -> Long = System::nanoTime) {
    private val rejected = mutableMapOf<AudioQuality, MutableSet<String>>()
    private var start = nanoTime()

    fun reset() {
        rejected.clear()
        startBudget()
    }

    fun startBudget() { start = nanoTime() }

    fun remaining(): Long = (SoundSourceRepository.RESOLVE_BUDGET_MS - (nanoTime() - start) / 1_000_000L)
        .coerceAtLeast(0L)

    fun reject(quality: AudioQuality, id: String) { rejected.getOrPut(quality, ::mutableSetOf).add(id) }

    fun exclusions(quality: AudioQuality): Set<String> = rejected[quality]?.toSet().orEmpty()

    fun clear(quality: AudioQuality) { rejected.remove(quality) }
}
