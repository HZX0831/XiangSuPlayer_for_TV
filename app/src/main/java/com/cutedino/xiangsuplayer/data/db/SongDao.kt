package com.cutedino.xiangsuplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    // ── 本地收藏 ──
    @Query("SELECT * FROM favorite_songs ORDER BY addedAtMs DESC")
    fun getAllFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM favorite_songs ORDER BY addedAtMs DESC")
    suspend fun getAllSongs(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: SongEntity)

    @Delete
    suspend fun deleteFavorite(song: SongEntity)

    @Query("DELETE FROM favorite_songs WHERE sourceId = :sourceId AND id = :songId")
    suspend fun deleteFavoriteById(sourceId: String, songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE sourceId = :sourceId AND id = :songId)")
    suspend fun isFavorite(sourceId: String, songId: String): Boolean

    @Transaction
    suspend fun toggleFavorite(song: SongEntity): Boolean {
        if (isFavorite(song.sourceId, song.id)) {
            deleteFavoriteById(song.sourceId, song.id)
            return false
        }
        insertFavorite(song)
        return true
    }

    // ── 播放历史持久化 ──
    @Query("SELECT * FROM play_history ORDER BY playedAtMs DESC LIMIT 200")
    fun getPlayHistoryFlow(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playedAtMs DESC LIMIT 200")
    suspend fun getPlayHistoryList(): List<PlayHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE sourceId = :sourceId AND id = :songId")
    suspend fun deleteHistoryById(sourceId: String, songId: String)

    @Query("DELETE FROM play_history")
    suspend fun clearAllHistory()
}
