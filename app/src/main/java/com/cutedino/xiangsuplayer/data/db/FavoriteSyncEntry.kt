package com.cutedino.xiangsuplayer.data.db

import androidx.room.Entity
import java.util.UUID

@Entity(tableName = "favorite_sync", primaryKeys = ["accountId", "trackId"])
data class FavoriteSyncEntry(
    val accountId: Long,
    val trackId: Long,
    val liked: Boolean,
    val token: String = UUID.randomUUID().toString(),
    val attempts: Int = 0,
    val retryAtMs: Long = 0L
)
