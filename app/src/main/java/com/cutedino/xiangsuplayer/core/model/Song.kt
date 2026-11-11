package com.cutedino.xiangsuplayer.core.model

import net.moriafly.ncm.ncmList
import net.moriafly.ncm.ncmLong
import net.moriafly.ncm.ncmObj
import net.moriafly.ncm.ncmString
import java.util.Locale

data class SongKey(val sourceId: String, val trackId: String) {
    companion object {
        fun canonicalSource(source: String): String = when (val id = source.trim().lowercase(Locale.ROOT)) {
            "netease", "netease_native" -> "wy"
            "qq" -> "tx"
            "kugou" -> "kg"
            "kuwo" -> "kw"
            "migu" -> "mg"
            else -> id
        }
    }
}

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val source: String = "wy",
    val songmid: String = "",
    val hash: String = "",
    val isFavorite: Boolean = false
) {
    val key: SongKey get() = SongKey(SongKey.canonicalSource(source), id)
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.toSong(): Song {
    val id = ncmLong("id").toString()
    val name = ncmString("name")

    // Try "ar" first (playlist/recommend API), then fallback to "artists" (search API)
    val arList = ncmList("ar").ifEmpty { ncmList("artists") }
    val artistNames = arList.mapNotNull { (it as? Map<String, Any?>)?.ncmString("name") }
        .filter { it.isNotBlank() }
    val artist = if (artistNames.isNotEmpty()) artistNames.joinToString(" / ") else "未知歌手"

    // Try "al" first (playlist/recommend API), then fallback to "album" (search API)
    val alObj = ncmObj("al").ifEmpty { ncmObj("album") }
    val album = alObj.ncmString("name")

    // Extract cover image URL: check picUrl first, then fallback to artist img1v1Url/picUrl
    var picUrl = alObj.ncmString("picUrl")
    if (picUrl.isBlank()) {
        val artistObj = alObj.ncmObj("artist")
        picUrl = artistObj.ncmString("img1v1Url").ifBlank { artistObj.ncmString("picUrl") }
    }

    val duration = ncmLong("dt").takeIf { it > 0 } ?: ncmLong("duration")

    return Song(
        id = id,
        title = name,
        artist = artist,
        album = album,
        coverUrl = picUrl,
        durationMs = duration
    )
}

