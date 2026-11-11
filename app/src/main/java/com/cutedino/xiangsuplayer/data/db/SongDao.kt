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
    suspend fun toggleFavorite(song: SongEntity, accountId: Long? = null): Boolean {
        val liked = !isFavorite(song.sourceId, song.id)
        if (liked) insertFavorite(song) else deleteFavoriteById(song.sourceId, song.id)
        val trackId = song.id.toLongOrNull()
        if (accountId != null && song.sourceId == "wy" && trackId != null && trackId > 0L) {
            upsertFavoriteSync(FavoriteSyncEntry(accountId, trackId, liked))
        }
        return liked
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoriteSync(entry: FavoriteSyncEntry)

    @Query("SELECT * FROM favorite_sync WHERE accountId = :accountId AND retryAtMs <= :now ORDER BY retryAtMs, trackId LIMIT 1")
    suspend fun nextFavoriteSync(accountId: Long, now: Long): FavoriteSyncEntry?

    @Query("DELETE FROM favorite_sync WHERE accountId = :accountId AND trackId = :trackId AND token = :token")
    suspend fun finishFavoriteSync(accountId: Long, trackId: Long, token: String)

    @Query("UPDATE favorite_sync SET attempts = :attempts, retryAtMs = :retryAt WHERE accountId = :accountId AND trackId = :trackId AND token = :token")
    suspend fun deferFavoriteSync(accountId: Long, trackId: Long, token: String, attempts: Int, retryAt: Long)

    // ── 播放历史持久化 ──
    @Query("SELECT * FROM play_history ORDER BY playedAtMs DESC, rowid DESC LIMIT 200")
    fun getPlayHistoryFlow(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playedAtMs DESC, rowid DESC LIMIT 200")
    suspend fun getPlayHistoryList(): List<PlayHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(item: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE rowid NOT IN (SELECT rowid FROM play_history ORDER BY playedAtMs DESC, rowid DESC LIMIT 200)")
    suspend fun trimHistory()

    @Transaction
    suspend fun insertHistory(item: PlayHistoryEntity) {
        upsertHistory(item)
        trimHistory()
    }

    @Query("DELETE FROM play_history WHERE sourceId = :sourceId AND id = :songId")
    suspend fun deleteHistoryById(sourceId: String, songId: String)

    @Query("DELETE FROM play_history")
    suspend fun clearAllHistory()
}
