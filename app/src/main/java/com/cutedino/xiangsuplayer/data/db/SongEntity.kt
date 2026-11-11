package com.cutedino.xiangsuplayer.data.db

import androidx.room.Entity
import com.cutedino.xiangsuplayer.core.model.Song

@Entity(tableName = "favorite_songs", primaryKeys = ["sourceId", "id"])
data class SongEntity(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
    val sourceId: String = "wy",
    val songmid: String = "",
    val hash: String = "",
    val addedAtMs: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationMs = durationMs,
        source = sourceId,
        songmid = songmid,
        hash = hash,
        isFavorite = true
    )
}

@Entity(tableName = "play_history", primaryKeys = ["sourceId", "id"])
data class PlayHistoryEntity(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val durationMs: Long,
    val sourceId: String = "wy",
    val songmid: String = "",
    val hash: String = "",
    val playedAtMs: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        coverUrl = coverUrl,
        durationMs = durationMs,
        source = sourceId,
        songmid = songmid,
        hash = hash
    )
}

fun Song.toFavoriteEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs,
    sourceId = key.sourceId,
    songmid = songmid,
    hash = hash,
    addedAtMs = System.currentTimeMillis()
)

fun Song.toHistoryEntity(): PlayHistoryEntity = PlayHistoryEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    coverUrl = coverUrl,
    durationMs = durationMs,
    sourceId = key.sourceId,
    songmid = songmid,
    hash = hash,
    playedAtMs = System.currentTimeMillis()
)
