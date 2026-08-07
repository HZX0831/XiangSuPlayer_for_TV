package com.cutedino.xiangsuplayer.core.source

import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmInt
import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString

class NeteaseSourceAdapter : ISoundSourceAdapter {
    override val sourceId: String = "netease_native"
    override val sourceName: String = "网易云原生"
    override val priority: Int = 100

    @Suppress("UNCHECKED_CAST")
    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> = runCatching {
        val offset = (page - 1) * limit
        val res = NcmApi.search(keyword = query, offset = offset, limit = limit)
        val detailMap = res.getOrNull() ?: return@runCatching emptyList()

        val resultObj = detailMap.ncmObj("result")
        val songsList = resultObj.ncmList("songs")
        val songIds = songsList.mapNotNull { (it as? Map<String, Any?>)?.ncmLong("id")?.toString() }
            .filter { it.isNotBlank() }

        if (songIds.isNotEmpty()) {
            val detailRes = NcmApi.songDetail(songIds)
            val detailData = detailRes.getOrNull()
            if (detailData != null) {
                val detailSongs = detailData.ncmList("songs")
                if (detailSongs.isNotEmpty()) {
                    return@runCatching detailSongs.mapNotNull { (it as? Map<String, Any?>)?.toSong() }
                }
            }
        }

        val resultSongs = mutableListOf<Song>()
        for (item in songsList) {
            val map = item as? Map<String, Any?> ?: continue
            resultSongs.add(map.toSong())
        }

        resultSongs
    }

    override suspend fun getStreamUrl(song: Song, quality: AudioQuality): Result<StreamResult> = runCatching {
        val res = NcmApi.songUrlV1(ids = listOf(song.id), level = quality.level)
        val detailMap = res.getOrNull() ?: throw IllegalStateException("网易云 API 无响应")
        val dataList = detailMap.ncmList("data")
        if (dataList.isNotEmpty()) {
            val map = dataList[0] as? Map<*, *> ?: throw IllegalStateException("无效的数据对象")
            @Suppress("UNCHECKED_CAST")
            val songMap = map as Map<String, Any?>
            val url = songMap.ncmString("url")
            if (url.isBlank()) throw IllegalStateException("音频直链为空")

            // 检测试听/付费限制 (freeTrialInfo, freeTime, freeTrialPrivilege 等)
            val freeTrialInfo = songMap.ncmObj("freeTrialInfo")
            val freeTime = songMap.ncmInt("freeTime")
            val freeTrialPrivilege = songMap.ncmObj("freeTrialPrivilege")
            val isTrial = freeTrialInfo.isNotEmpty() || freeTime > 0 || (freeTrialPrivilege.isNotEmpty() && freeTrialPrivilege.ncmInt("userFeeLevel") == 0)

            if (isTrial) {
                StreamResult.Trial(url = url, trialSeconds = if (freeTime > 0) freeTime else 30, sourceName = sourceName)
            } else {
                StreamResult.Full(url = url, sourceName = sourceName)
            }
        } else {
            throw IllegalStateException("未查找到音频直链")
        }
    }

    override suspend fun getLyrics(songId: String): Result<List<LyricLine>> = runCatching {
        val res = NcmApi.songLyric(id = songId)
        val map = res.getOrNull() ?: return@runCatching emptyList()
        val lrcObj = map.ncmObj("lrc")
        val lrcText = lrcObj.ncmString("lyric")
        com.cutedino.xiangsuplayer.core.audio.LyricEngine.parse(lrcText)
    }
}
