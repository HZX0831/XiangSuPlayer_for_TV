package com.cutedino.xiangsuplayer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cutedino.xiangsuplayer.core.model.Song

@Entity(tableName = "favorite_songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
    val sourceId: String = "netease",
    val addedAtMs: Long = System.currentTimeMillis(),
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationMs = durationMs
    )
}
