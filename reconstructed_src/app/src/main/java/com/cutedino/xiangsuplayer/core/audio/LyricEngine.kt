package com.cutedino.xiangsuplayer.core.audio

import com.cutedino.xiangsuplayer.core.model.LyricLine

/**
 * Kotlin version of LyricEngine originally decompiled from Java.
 */
object LyricEngine {
    /**
     * Find the index of the active lyric line based on the current playback position.
     *
     * @param lyrics List of lyric lines sorted by time.
     * @param currentPositionMs Current playback position in milliseconds.
     * @return Index of the active lyric, or -1 if the list is empty.
     */
    fun findActiveLyricIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var size = lyrics.size - 1
        if (size >= 0) {
            while (size >= 0) {
                if (currentPositionMs >= lyrics[size].timeMs) {
                    return size
                }
                size--
            }
            return 0
        }
        return 0
    }
}
