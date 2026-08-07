package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song

sealed interface StreamResult {
    data class Full(val url: String, val bitRate: Int = 0, val sourceName: String = "") : StreamResult
    data class Trial(val url: String, val trialSeconds: Int = 30, val sourceName: String = "") : StreamResult

    val streamUrl: String
        get() = when (this) {
            is Full -> url
            is Trial -> url
        }
}

interface ISoundSourceAdapter {
    val sourceId: String
    val sourceName: String
    val priority: Int get() = 50

    suspend fun search(query: String, page: Int = 1, limit: Int = 30): Result<List<Song>>
    suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult>
    suspend fun getLyrics(songId: String): Result<List<LyricLine>>
}
