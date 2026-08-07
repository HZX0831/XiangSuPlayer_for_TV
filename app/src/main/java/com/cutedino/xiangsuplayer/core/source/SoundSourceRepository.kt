package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song

object SoundSourceRepository {

    val neteaseAdapter = NeteaseSourceAdapter()

    suspend fun search(query: String, page: Int = 1): Result<List<Song>> {
        return neteaseAdapter.search(query, page)
    }

    suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<String> {
        return neteaseAdapter.getStreamUrl(song, quality)
    }

    suspend fun getLyrics(song: Song): Result<List<LyricLine>> {
        return neteaseAdapter.getLyrics(song.id)
    }
}
