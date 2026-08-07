package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString

class NeteaseSourceAdapter {

    @Suppress("UNCHECKED_CAST")
    suspend fun search(query: String, page: Int = 1): Result<List<Song>> = runCatching {
        val offset = (page - 1) * 30
        val res = NcmApi.search(keyword = query, offset = offset, limit = 30)
        val detailMap = res.getOrNull() ?: return@runCatching emptyList()

        val resultObj = detailMap.ncmObj("result")
        val songsList = resultObj.ncmList("songs")
        val resultSongs = mutableListOf<Song>()

        for (item in songsList) {
            val map = item as? Map<String, Any?> ?: continue
            val id = map.ncmLong("id").toString()
            val name = map.ncmString("name")

            val arList = map.ncmList("ar")
            val artist = if (arList.isNotEmpty()) (arList[0] as? Map<String, Any?>)?.ncmString("name") ?: "未知歌手" else "未知歌手"
            val alObj = map.ncmObj("al")
            val album = alObj.ncmString("name")
            val picUrl = alObj.ncmString("picUrl")

            resultSongs.add(Song(id = id, title = name, artist = artist, album = album, coverUrl = picUrl, durationMs = 0L))
        }

        resultSongs
    }

    suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<String> = runCatching {
        val res = NcmApi.songUrlV1(ids = listOf(song.id), level = quality.level)
        val detailMap = res.getOrNull() ?: return@runCatching ""
        val dataList = detailMap.ncmList("data")
        if (dataList.isNotEmpty()) {
            val map = dataList[0] as? Map<*, *> ?: return@runCatching ""
            @Suppress("UNCHECKED_CAST")
            val songMap = map as Map<String, Any?>
            val url = songMap.ncmString("url")
            url
        } else ""
    }

    suspend fun getLyrics(songId: String): Result<List<LyricLine>> = runCatching {
        val res = NcmApi.songLyric(id = songId)
        val map = res.getOrNull() ?: return@runCatching emptyList()
        val lrcObj = map.ncmObj("lrc")
        val lrcText = lrcObj.ncmString("lyric")
        com.cutedino.xiangsuplayer.core.audio.LyricEngine.parse(lrcText)
    }
}
