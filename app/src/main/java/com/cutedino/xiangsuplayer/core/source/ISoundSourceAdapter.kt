package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song

interface ISoundSourceAdapter {
    val sourceId: String
    val sourceName: String

    suspend fun search(query: String, page: Int = 1, limit: Int = 30): Result<List<Song>>
    suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<String>
    suspend fun getLyrics(songId: String): Result<List<LyricLine>>
}
