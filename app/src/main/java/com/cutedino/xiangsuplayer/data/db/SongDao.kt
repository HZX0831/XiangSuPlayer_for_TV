package com.cutedino.xiangsuplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM favorite_songs ORDER BY addedAtMs DESC")
    fun getAllFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM favorite_songs ORDER BY addedAtMs DESC")
    suspend fun getAllSongs(): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: SongEntity)

    @Delete
    suspend fun deleteFavorite(song: SongEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE id = :songId)")
    suspend fun isFavorite(songId: String): Boolean
}
